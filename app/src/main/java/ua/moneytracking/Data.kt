package ua.moneytracking

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
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
data class Entry(@PrimaryKey(autoGenerate = true) val id: Long = 0, val kind: String, val amount: Long, val accountId: Long, val targetId: Long? = null, val categoryId: Long? = null, val time: Long, val note: String = "")

val palette = listOf(0xFF927BFA, 0xFF588EF3, 0xFF40BC99, 0xFFEE728E, 0xFFECAF58, 0xFFB678DA, 0xFF57BCCA, 0xFF737C93)
val defaultNames = listOf("Їжа", "Кафе", "Розваги", "Транспорт", "Здоров’я", "Домашні тварини", "Сім’я", "Одяг", "Комунальні послуги", "Підписки", "Покупки", "Інше")

@Dao
interface FinanceDao {
    @Query("SELECT * FROM accounts ORDER BY id") fun accounts(): Flow<List<Account>>
    @Query("SELECT * FROM categories ORDER BY id") fun categories(): Flow<List<Category>>
    @Query("SELECT * FROM entries ORDER BY time DESC, id DESC") fun entries(): Flow<List<Entry>>
    @Upsert suspend fun save(a: Account)
    @Upsert suspend fun save(c: Category)
    @Upsert suspend fun save(e: Entry)
    @Delete suspend fun delete(a: Account)
    @Delete suspend fun delete(c: Category)
    @Delete suspend fun delete(e: Entry)
}
@Database(entities = [Account::class, Category::class, Entry::class], version = 1, exportSchema = true)
abstract class FinanceDatabase : RoomDatabase() { abstract fun dao(): FinanceDao }

data class FinanceState(val accounts: List<Account> = emptyList(), val categories: List<Category> = emptyList(), val entries: List<Entry> = emptyList(), val ready: Boolean = false)
class FinanceModel(app: Application) : AndroidViewModel(app) {
    private val database = Room.databaseBuilder(app, FinanceDatabase::class.java, "finance.db")
        .addCallback(object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                defaultNames.forEachIndexed { i, name -> db.execSQL("INSERT INTO categories (name,color,icon) VALUES (?,?,?)", arrayOf(name, palette[i % palette.size], i)) }
            }
        }).build()
    private val dao = database.dao()
    val state = combine(dao.accounts(), dao.categories(), dao.entries()) { a, c, e -> FinanceState(a, c, e, true) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FinanceState())
    val error = MutableStateFlow<String?>(null)
    private fun run(action: suspend () -> Unit) { viewModelScope.launch { try { action() } catch (e: Exception) { error.value = "Не вдалося зберегти зміни. Спробуйте ще раз." } } }
    fun save(a: Account) = run { dao.save(a) }
    fun save(c: Category) = run { dao.save(c) }
    fun save(e: Entry) = run {
        require(e.amount > 0 && e.kind in listOf("expense", "income", "transfer"))
        require((e.kind == "transfer") == (e.targetId != null))
        require(e.targetId != e.accountId)
        dao.save(e)
    }
    fun delete(a: Account) = run { dao.delete(a) }
    fun delete(c: Category) = run { dao.delete(c) }
    fun delete(e: Entry) = run { dao.delete(e) }
    override fun onCleared() { database.close() }
}

// Every amount is an integer number of kopecks. Limit input to avoid Long overflow.
fun parseMoney(raw: String): Long? = try {
    val clean = raw.trim().replace(" ", "").replace("\u00a0", "").replace(',', '.')
    if (!Regex("-?\\d{1,10}(\\.\\d{1,2})?").matches(clean)) null
    else BigDecimal(clean).movePointRight(2).longValueExact()
} catch (_: Exception) { null }
fun money(value: Long): String {
    val symbols = DecimalFormatSymbols(Locale.forLanguageTag("uk-UA")).apply { groupingSeparator = ' '; decimalSeparator = ',' }
    return DecimalFormat("#,##0.00", symbols).format(BigDecimal.valueOf(value, 2)) + " грн"
}
fun editableMoney(value: Long): String = BigDecimal.valueOf(value, 2).toPlainString()
fun delta(entry: Entry, account: Long): Long = when (entry.kind) {
    "income" -> if (entry.accountId == account) entry.amount else 0
    "expense" -> if (entry.accountId == account) -entry.amount else 0
    "transfer" -> when (account) { entry.accountId -> -entry.amount; entry.targetId -> entry.amount; else -> 0 }
    else -> 0
}
fun balance(account: Account, entries: List<Entry>): Long = account.opening + entries.sumOf { delta(it, account.id) }
