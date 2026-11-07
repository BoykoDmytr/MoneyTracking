package ua.moneytracking

import android.content.ContentResolver
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

const val MAX_BACKUP_BYTES = 32 * 1024 * 1024
private const val BACKUP_FORMAT = "ua.moneytracking.backup"
data class FinanceBackup(val state: FinanceState, val preferences: UserPreferences, val exportedAt: Long = System.currentTimeMillis())
class BackupException(message: String = "Файл не є коректною резервною копією «Мої фінанси» або має непідтримувану версію.") : IllegalArgumentException(message)
private fun checkBackup(valid: Boolean) { if (!valid) throw BackupException() }

/** The same strict validation is used before preview, export and database replacement. */
fun validateBackup(backup: FinanceBackup) {
    val (accounts, categories, entries) = backup.state
    checkBackup(accounts.size <= 1000 && categories.size <= 5000 && entries.size <= 100000)
    fun unique(ids: List<Long>) = ids.all { it > 0 && it < Long.MAX_VALUE } && ids.toSet().size == ids.size
    checkBackup(unique(accounts.map { it.id }) && unique(categories.map { it.id }) && unique(entries.map { it.id }))
    val byId = accounts.associateBy { it.id }
    val categoryIds = categories.map { it.id }.toSet()
    accounts.forEach {
        checkBackup(it.name.isNotBlank() && it.name.length <= 60 && it.currency in supportedCurrencies && it.opening in -MAX_MONEY..MAX_MONEY)
        checkBackup(it.lastFour.isEmpty() || (!it.cash && Regex("[0-9]{4}").matches(it.lastFour)))
    }
    categories.forEach { checkBackup(it.name.isNotBlank() && it.name.length <= 50 && it.icon in 0..11) }
    entries.forEach { e ->
        val source = byId[e.accountId] ?: throw BackupException()
        val target = e.targetId?.let { byId[it] ?: throw BackupException() }
        checkBackup(e.kind in setOf("expense", "income", "transfer", "correction") && e.note.length <= 500 && e.sender.length <= 120)
        checkBackup(e.categoryId == null || e.categoryId in categoryIds)
        checkBackup(if (e.kind == "correction") e.amount != 0L && e.targetId == null && e.categoryId == null else e.amount in 1..MAX_MONEY)
        checkBackup((e.kind == "transfer") == (target != null) && e.targetId != e.accountId)
        checkBackup(e.kind != "transfer" || e.categoryId == null)
        checkBackup(if (target != null && target.currency != source.currency) e.targetAmount != null && e.targetAmount in 1..MAX_MONEY else e.targetAmount == null)
        checkBackup(e.incomeSource in setOf("salary", "person", "other"))
        checkBackup(e.kind != "income" || e.incomeSource != "person" || e.sender.isNotBlank())
    }
    checkBackup(backup.preferences.defaultKind in setOf("expense", "income", "transfer"))
    checkBackup(backup.preferences.defaultAccountId == null || backup.preferences.defaultAccountId in byId)
    try {
        // Bound absolute exposure too: every date/account filter must remain safe to sum.
        val exposure = mutableMapOf<String, Long>()
        fun add(currency: String, value: Long) {
            val magnitude = if (value < 0) Math.negateExact(value) else value
            exposure[currency] = Math.addExact(exposure[currency] ?: 0, magnitude)
        }
        accounts.forEach { add(it.currency, it.opening) }
        entries.forEach { e ->
            add(byId.getValue(e.accountId).currency, e.amount)
            if (e.targetId != null) add(byId.getValue(e.targetId).currency, e.targetAmount ?: e.amount)
        }
        val ordered = entries.sortedWith(compareByDescending<Entry> { it.time }.thenByDescending { it.id })
        accounts.forEach { balance(it, ordered) }
    } catch (_: ArithmeticException) { throw BackupException("У копії є суми, що перевищують допустимі межі.") }
}

object BackupCodec {
    fun encode(backup: FinanceBackup): String {
        validateBackup(backup)
        fun <T> array(values: List<T>, encode: (T) -> JSONObject) = JSONArray().apply { values.forEach { put(encode(it)) } }
        return JSONObject().apply {
            put("format", BACKUP_FORMAT); put("version", 1); put("minorUnitScale", 2); put("exportedAt", backup.exportedAt)
            put("accounts", array(backup.state.accounts) { a -> JSONObject().apply {
                put("id", a.id); put("name", a.name); put("opening", a.opening); put("color", a.color)
                put("cash", a.cash); put("lastFour", a.lastFour); put("currency", a.currency)
            } })
            put("categories", array(backup.state.categories) { c -> JSONObject().apply {
                put("id", c.id); put("name", c.name); put("color", c.color); put("icon", c.icon)
            } })
            put("entries", array(backup.state.entries) { e -> JSONObject().apply {
                put("id", e.id); put("kind", e.kind); put("amount", e.amount); put("accountId", e.accountId)
                put("targetId", e.targetId ?: JSONObject.NULL); put("categoryId", e.categoryId ?: JSONObject.NULL)
                put("time", e.time); put("note", e.note); put("incomeSource", e.incomeSource); put("sender", e.sender)
                put("targetAmount", e.targetAmount ?: JSONObject.NULL)
            } })
            put("preferences", JSONObject().apply {
                put("hideBalance", backup.preferences.hideBalance); put("haptics", backup.preferences.haptics)
                put("defaultAccountId", backup.preferences.defaultAccountId ?: JSONObject.NULL); put("defaultKind", backup.preferences.defaultKind)
            })
        }.toString(2).also { if (it.toByteArray(Charsets.UTF_8).size > MAX_BACKUP_BYTES) throw BackupException("Копія перевищує ліміт 32 МБ.") }
    }

    fun decode(text: String): FinanceBackup = try {
        checkBackup(text.toByteArray(Charsets.UTF_8).size <= MAX_BACKUP_BYTES)
        val parser = JSONTokener(text.removePrefix("\uFEFF"))
        val root = parser.nextValue() as? JSONObject ?: throw BackupException()
        checkBackup(parser.nextClean() == '\u0000')
        checkBackup(root.string("format") == BACKUP_FORMAT && root.long("version") == 1L && root.long("minorUnitScale") == 2L)
        fun <T> list(name: String, max: Int, read: (JSONObject) -> T): List<T> {
            val array = root.getJSONArray(name)
            checkBackup(array.length() <= max)
            return (0 until array.length()).map { read(array.getJSONObject(it)) }
        }
        val accounts = list("accounts", 1000) { a -> Account(a.long("id"), a.string("name"), a.long("opening"), a.long("color"), a.bool("cash"), a.string("lastFour"), a.string("currency")) }
        val categories = list("categories", 5000) { c ->
            val icon = c.long("icon"); checkBackup(icon in 0..11)
            Category(c.long("id"), c.string("name"), c.long("color"), icon.toInt())
        }
        val entries = list("entries", 100000) { e -> Entry(e.long("id"), e.string("kind"), e.long("amount"), e.long("accountId"), e.nullableLong("targetId"), e.nullableLong("categoryId"), e.long("time"), e.string("note"), e.string("incomeSource"), e.string("sender"), e.nullableLong("targetAmount")) }
        val p = root.getJSONObject("preferences")
        FinanceBackup(FinanceState(accounts, categories, entries, true), UserPreferences(p.bool("hideBalance"), p.bool("haptics"), p.nullableLong("defaultAccountId"), p.string("defaultKind")), root.long("exportedAt")).also(::validateBackup)
    } catch (e: BackupException) { throw e }
    catch (_: Exception) { throw BackupException() }

    // JSONObject.getLong accepts fractions and numeric strings. Financial files must not.
    private fun JSONObject.long(key: String): Long = when (val value = get(key)) {
        is Long -> value
        is Int -> value.toLong()
        else -> throw BackupException()
    }
    private fun JSONObject.nullableLong(key: String): Long? { checkBackup(has(key)); return if (isNull(key)) null else long(key) }
    private fun JSONObject.string(key: String) = get(key) as? String ?: throw BackupException()
    private fun JSONObject.bool(key: String) = get(key) as? Boolean ?: throw BackupException()
}

/** Storage Access Framework grants access only to the document the user chooses. */
object BackupFiles {
    fun read(resolver: ContentResolver, uri: Uri): FinanceBackup {
        val bytes = resolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var size = 0
            while (true) {
                val count = input.read(buffer)
                if (count == -1) break
                size += count
                if (size > MAX_BACKUP_BYTES) throw BackupException("Копія перевищує ліміт 32 МБ.")
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: throw BackupException("Не вдалося відкрити файл.")
        val text = try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: Exception) { throw BackupException() }
        return BackupCodec.decode(text)
    }

    fun write(resolver: ContentResolver, uri: Uri, text: String) {
        resolver.openOutputStream(uri, "wt")?.use { output ->
            output.write(text.toByteArray(Charsets.UTF_8)); output.flush()
        } ?: throw BackupException("Не вдалося створити файл. Оберіть інше місце для копії.")
    }
}
