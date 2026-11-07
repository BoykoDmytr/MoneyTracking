package ua.moneytracking

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

@Entity(tableName = "accounts")
data class Account(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val opening: Long, val color: Long, val cash: Boolean = false, val lastFour: String = "")
@Entity(tableName = "categories")
data class Category(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val color: Long, val icon: Int)
@Entity(tableName = "entries", foreignKeys = [
    ForeignKey(entity = Account::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = Account::class, parentColumns = ["id"], childColumns = ["targetId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = Category::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.SET_NULL)
], indices = [Index("accountId"), Index("targetId"), Index("categoryId"), Index("time")])
data class Entry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val amount: Long,
    val accountId: Long,
    val targetId: Long? = null,
    val categoryId: Long? = null,
    val time: Long,
    val note: String = "",
    @ColumnInfo(defaultValue = "'other'") val incomeSource: String = "other",
    @ColumnInfo(defaultValue = "''") val sender: String = ""
)

val palette = listOf(0xFF927BFA, 0xFF588EF3, 0xFF40BC99, 0xFFEE728E, 0xFFECAF58, 0xFFB678DA, 0xFF57BCCA, 0xFF737C93)
val defaultNames = listOf("Їжа", "Кафе", "Розваги", "Транспорт", "Здоров’я", "Домашні тварини", "Сім’я", "Одяг", "Комунальні послуги", "Підписки", "Покупки", "Інше")
const val MAX_MONEY = 999_999_999_999L

@Dao
interface FinanceDao {
    @Query("SELECT (SELECT COUNT(*) FROM accounts) + (SELECT COUNT(*) FROM categories) + (SELECT COUNT(*) FROM entries)")
    fun observeChanges(): Flow<Long>
    @Query("SELECT * FROM accounts ORDER BY id") fun accounts(): Flow<List<Account>>
    @Query("SELECT * FROM categories ORDER BY id") fun categories(): Flow<List<Category>>
    @Query("SELECT * FROM entries ORDER BY time DESC, id DESC") fun entries(): Flow<List<Entry>>
    @Query("SELECT * FROM accounts ORDER BY id") suspend fun allAccounts(): List<Account>
    @Query("SELECT * FROM categories ORDER BY id") suspend fun allCategories(): List<Category>
    @Query("SELECT * FROM entries ORDER BY time DESC, id DESC") suspend fun allEntries(): List<Entry>
    @Query("SELECT * FROM accounts WHERE id = :id") suspend fun account(id: Long): Account?
    @Query("SELECT * FROM categories WHERE id = :id") suspend fun category(id: Long): Category?
    @Query("SELECT * FROM entries WHERE id = :id") suspend fun entry(id: Long): Entry?
    @Upsert suspend fun save(a: Account)
    @Upsert suspend fun save(c: Category)
    @Upsert suspend fun save(e: Entry)
    @Delete suspend fun delete(a: Account)
    @Delete suspend fun delete(c: Category)
    @Delete suspend fun delete(e: Entry)
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE entries ADD COLUMN incomeSource TEXT NOT NULL DEFAULT 'other'")
        db.execSQL("ALTER TABLE entries ADD COLUMN sender TEXT NOT NULL DEFAULT ''")
    }
}

@Database(entities = [Account::class, Category::class, Entry::class], version = 2, exportSchema = true)
abstract class FinanceDatabase : RoomDatabase() {
    abstract fun dao(): FinanceDao
    companion object {
        fun open(app: Application): FinanceDatabase = Room.databaseBuilder(app, FinanceDatabase::class.java, "finance.db")
            .addMigrations(MIGRATION_1_2)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    defaultNames.forEachIndexed { i, name ->
                        db.execSQL("INSERT INTO categories (name,color,icon) VALUES (?,?,?)", arrayOf(name, palette[i % palette.size], i))
                    }
                }
            }).build()
    }
}

data class FinanceState(val accounts: List<Account> = emptyList(), val categories: List<Category> = emptyList(), val entries: List<Entry> = emptyList(), val ready: Boolean = false)

private class FinanceValidationException(message: String) : IllegalArgumentException(message)
private inline fun financeRequire(condition: Boolean, message: () -> String = { "Перевірте введені дані та суму операції." }) {
    if (!condition) throw FinanceValidationException(message())
}
private inline fun <T : Any> financeNotNull(value: T?, message: () -> String): T = value ?: throw FinanceValidationException(message())

/** All balance-changing writes and reads use one transaction, including corrections. */
class FinanceRepository(private val database: FinanceDatabase) {
    private val dao = database.dao()
    suspend fun snapshot(): FinanceState = database.withTransaction {
        FinanceState(dao.allAccounts(), dao.allCategories(), dao.allEntries(), ready = true)
    }
    fun observeState(): Flow<FinanceState> = dao.observeChanges()
        .map { snapshot() }.distinctUntilChanged()

    suspend fun saveAccount(account: Account, desiredBalance: Long? = null) = database.withTransaction {
        financeRequire(account.id >= 0 && account.name.isNotBlank() && account.name.trim().length <= 60)
        financeRequire(account.cash || account.lastFour.isBlank() || Regex("\\d{4}").matches(account.lastFour))
        val clean = account.copy(name = account.name.trim(), lastFour = if (account.cash) "" else account.lastFour)
        if (account.id == 0L) {
            financeRequire(account.opening in -MAX_MONEY..MAX_MONEY)
            financeRequire(desiredBalance == null || desiredBalance == account.opening)
            dao.save(clean)
        } else {
            val persisted = financeNotNull(dao.account(account.id)) { "Рахунок уже видалено." }
            dao.save(clean.copy(opening = persisted.opening))
            if (desiredBalance != null) {
                financeRequire(desiredBalance in -MAX_MONEY..MAX_MONEY)
                val current = balance(persisted, dao.allEntries())
                val adjustment = Math.subtractExact(desiredBalance, current)
                if (adjustment != 0L) {
                    dao.save(Entry(kind = "correction", amount = adjustment, accountId = account.id, time = System.currentTimeMillis(), note = "Корекція балансу"))
                }
            }
        }
    }

    suspend fun saveCategory(category: Category) = database.withTransaction {
        financeRequire(category.id >= 0 && category.name.isNotBlank() && category.name.trim().length <= 50)
        financeRequire(category.icon in 0..11)
        if (category.id != 0L) financeNotNull(dao.category(category.id)) { "Категорію вже видалено." }
        dao.save(category.copy(name = category.name.trim()))
    }

    suspend fun saveEntry(entry: Entry) = database.withTransaction {
        financeRequire(entry.id >= 0)
        val previous = if (entry.id == 0L) null else financeNotNull(dao.entry(entry.id)) { "Операцію вже видалено." }
        financeNotNull(dao.account(entry.accountId)) { "Рахунок уже видалено." }
        financeRequire(entry.note.length <= 500 && entry.sender.length <= 120)
        financeRequire(entry.kind in setOf("expense", "income", "transfer", "correction"))
        if (entry.kind == "correction") {
            financeRequire(previous?.kind == "correction" && previous.accountId == entry.accountId)
            financeRequire(entry.amount != 0L)
            financeRequire(entry.targetId == null && entry.categoryId == null)
        } else {
            financeRequire(previous?.kind != "correction")
            financeRequire(entry.amount in 1..MAX_MONEY)
            financeRequire((entry.kind == "transfer") == (entry.targetId != null))
            financeRequire(entry.targetId != entry.accountId)
        }
        if (entry.targetId != null) financeNotNull(dao.account(entry.targetId)) { "Рахунок отримувача вже видалено." }
        if (entry.categoryId != null) financeNotNull(dao.category(entry.categoryId)) { "Категорію вже видалено." }
        if (entry.kind == "income") {
            financeRequire(entry.incomeSource in setOf("salary", "person", "other"))
            financeRequire(entry.incomeSource != "person" || entry.sender.isNotBlank()) { "Вкажіть, від кого надійшли гроші." }
        }
        val clean = entry.copy(
            note = entry.note.trim(),
            incomeSource = if (entry.kind == "income") entry.incomeSource else "other",
            sender = if (entry.kind == "income") entry.sender.trim() else ""
        )
        // Detect any overflow before committing rather than storing a broken account balance.
        val futureEntries = dao.allEntries().filterNot { entry.id != 0L && it.id == entry.id } + clean
        dao.allAccounts().forEach { balance(it, futureEntries) }
        dao.save(clean)
    }

    suspend fun deleteAccount(account: Account) = database.withTransaction { dao.delete(account) }
    suspend fun deleteCategory(category: Category) = database.withTransaction { dao.delete(category) }
    suspend fun deleteEntry(entry: Entry) = database.withTransaction { dao.delete(entry) }
}

class FinanceModel(app: Application) : AndroidViewModel(app) {
    private val database = FinanceDatabase.open(app)
    private val repository = FinanceRepository(database)
    val error = MutableStateFlow<String?>(null)
    val state = repository.observeState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FinanceState())
    private fun run(action: suspend () -> Unit, onSuccess: () -> Unit) = viewModelScope.launch {
        try { action(); onSuccess() }
        catch (e: CancellationException) { throw e }
        catch (e: FinanceValidationException) { error.value = e.message }
        catch (e: Exception) { error.value = "Не вдалося зберегти зміни. Спробуйте ще раз." }
    }
    fun save(a: Account, desiredBalance: Long? = null, onSuccess: () -> Unit = {}) = run({ repository.saveAccount(a, desiredBalance) }, onSuccess)
    fun save(c: Category, onSuccess: () -> Unit = {}) = run({ repository.saveCategory(c) }, onSuccess)
    fun save(e: Entry, onSuccess: () -> Unit = {}) = run({ repository.saveEntry(e) }, onSuccess)
    fun delete(a: Account, onSuccess: () -> Unit = {}) = run({ repository.deleteAccount(a) }, onSuccess)
    fun delete(c: Category, onSuccess: () -> Unit = {}) = run({ repository.deleteCategory(c) }, onSuccess)
    fun delete(e: Entry, onSuccess: () -> Unit = {}) = run({ repository.deleteEntry(e) }, onSuccess)
    override fun onCleared() { database.close() }
}

fun incomeSourceLabel(source: String): String = when (source) {
    "salary" -> "Зарплата"
    "person" -> "Від іншої людини"
    else -> "Інше надходження"
}

data class MonthlyTotals(val income: Long = 0, val expense: Long = 0)
fun monthlyTotals(entries: List<Entry>, month: YearMonth, zone: ZoneId = ZoneId.systemDefault(), accountId: Long? = null): MonthlyTotals {
    val start = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    var income = 0L
    var expense = 0L
    entries.forEach { entry ->
        if (entry.time >= start && entry.time < end && (accountId == null || entry.accountId == accountId)) {
            when (entry.kind) {
                "income" -> income = Math.addExact(income, entry.amount)
                "expense" -> expense = Math.addExact(expense, entry.amount)
            }
        }
    }
    return MonthlyTotals(income, expense)
}

// Every amount is an integer number of kopecks. Limit input to avoid Long overflow.
fun parseMoney(raw: String): Long? = try {
    val clean = raw.trim().replace(" ", "").replace("\u00a0", "").replace("\u202f", "").replace('−', '-').replace(',', '.')
    if (!Regex("-?\\d{1,10}(\\.\\d{1,2})?").matches(clean)) null
    else BigDecimal(clean).movePointRight(2).longValueExact()
} catch (_: Exception) { null }
fun money(value: Long): String {
    val symbols = DecimalFormatSymbols(Locale.forLanguageTag("uk-UA")).apply { groupingSeparator = ' '; decimalSeparator = ',' }
    return DecimalFormat("#,##0.00", symbols).format(BigDecimal.valueOf(value, 2)) + " грн"
}
fun editableMoney(value: Long): String = BigDecimal.valueOf(value, 2).toPlainString()
fun delta(entry: Entry, account: Long): Long = when (entry.kind) {
    "income", "correction" -> if (entry.accountId == account) entry.amount else 0
    "expense" -> if (entry.accountId == account) Math.negateExact(entry.amount) else 0
    "transfer" -> when (account) { entry.accountId -> Math.negateExact(entry.amount); entry.targetId -> entry.amount; else -> 0 }
    else -> 0
}
fun balance(account: Account, entries: List<Entry>): Long = entries.fold(account.opening) { total, entry -> Math.addExact(total, delta(entry, account.id)) }
