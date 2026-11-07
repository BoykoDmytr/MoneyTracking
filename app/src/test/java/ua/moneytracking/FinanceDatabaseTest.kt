package ua.moneytracking

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class FinanceDatabaseTest {
    private lateinit var database: FinanceDatabase
    private lateinit var repository: FinanceRepository
    private lateinit var app: Application

    @Before fun setUp() {
        app = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(app, FinanceDatabase::class.java).allowMainThreadQueries().build()
        repository = FinanceRepository(database)
    }
    @After fun tearDown() { database.close() }

    private suspend fun accounts(): Pair<Account, Account> {
        repository.saveAccount(Account(name = "Картка", opening = 100000, color = palette[0]))
        repository.saveAccount(Account(name = "Готівка", opening = 50000, color = palette[1], cash = true))
        val accounts = repository.snapshot().accounts
        return accounts[0] to accounts[1]
    }
    private suspend fun reject(block: suspend () -> Unit) {
        try { block(); fail("Invalid write must fail") } catch (_: IllegalArgumentException) { }
    }

    @Test fun accountCorrectionsAreHistoricalSignedAndNotMonthlyIncome() = runBlocking {
        val (a, _) = accounts()
        val now = System.currentTimeMillis()
        repository.saveEntry(Entry(kind = "expense", amount = 35000, accountId = a.id, time = now))
        repository.saveEntry(Entry(kind = "income", amount = 20000, accountId = a.id, time = now, incomeSource = "salary", sender = "Компанія"))
        repository.saveAccount(a.copy(name = "Нова назва", opening = 1), desiredBalance = 200000)
        var state = repository.snapshot()
        assertEquals(100000L, state.accounts.first().opening)
        assertEquals("Нова назва", state.accounts.first().name)
        assertEquals(200000L, balance(state.accounts.first(), state.entries))
        assertEquals(115000L, state.entries.single { it.kind == "correction" }.amount)
        repository.saveAccount(state.accounts.first(), desiredBalance = 200000)
        assertEquals(1, repository.snapshot().entries.count { it.kind == "correction" })
        repository.saveAccount(state.accounts.first(), desiredBalance = -25000)
        state = repository.snapshot()
        assertEquals(-25000L, balance(state.accounts.first(), state.entries))
        assertEquals(listOf(-225000L, 115000L), state.entries.filter { it.kind == "correction" }.map { it.amount })
        assertTrue(state.entries.filter { it.kind == "correction" }.all { it.note == "Корекція балансу" && it.targetId == null && it.categoryId == null })
        assertEquals(MonthlyTotals(income = 20000, expense = 35000), monthlyTotals(state.entries, YearMonth.now(), accountId = a.id))
    }

    @Test fun metadataEditDoesNotUndoNewerTransactionsOrChangeOpening() = runBlocking {
        val (a, _) = accounts()
        repository.saveEntry(Entry(kind = "income", amount = 5000, accountId = a.id, time = 1))
        repository.saveAccount(a.copy(name = "Перейменовано", opening = -999))
        val state = repository.snapshot()
        assertEquals(100000L, state.accounts.first().opening)
        assertEquals(105000L, balance(state.accounts.first(), state.entries))
        assertEquals(0, state.entries.count { it.kind == "correction" })
    }

    @Test fun editingAndDeletingTransfersPreservesCombinedBalance() = runBlocking {
        val (a, b) = accounts()
        repository.saveEntry(Entry(kind = "transfer", amount = 20000, accountId = a.id, targetId = b.id, time = 1))
        var state = repository.snapshot()
        assertEquals(80000L, balance(a, state.entries)); assertEquals(70000L, balance(b, state.entries))
        repository.saveEntry(state.entries.single().copy(amount = 5000))
        state = repository.snapshot()
        assertEquals(95000L, balance(a, state.entries)); assertEquals(55000L, balance(b, state.entries))
        assertEquals(150000L, state.accounts.sumOf { balance(it, state.entries) })
        repository.deleteEntry(state.entries.single())
        state = repository.snapshot()
        assertEquals(100000L, balance(a, state.entries)); assertEquals(50000L, balance(b, state.entries))
    }

    @Test fun deletingCategoriesAndAccountsUsesRealRoomForeignKeys() = runBlocking {
        val (a, b) = accounts()
        repository.saveCategory(Category(name = "Їжа", color = palette[0], icon = 0))
        val category = repository.snapshot().categories.single()
        repository.saveEntry(Entry(kind = "expense", amount = 20000, accountId = a.id, categoryId = category.id, time = 1))
        repository.saveEntry(Entry(kind = "income", amount = 5000, accountId = b.id, time = 2))
        repository.saveEntry(Entry(kind = "transfer", amount = 10000, accountId = a.id, targetId = b.id, time = 3))
        repository.deleteCategory(category)
        var state = repository.snapshot()
        assertEquals(3, state.entries.size)
        assertNull(state.entries.single { it.kind == "expense" }.categoryId)
        repository.deleteAccount(b)
        state = repository.snapshot()
        assertEquals(1, state.entries.size)
        assertEquals("expense", state.entries.single().kind)
        assertEquals(80000L, balance(a, state.entries))
        repository.deleteAccount(a)
        assertTrue(repository.snapshot().entries.isEmpty())
    }

    @Test fun invalidWritesRollbackWithoutPartialMetadataOrBalances() = runBlocking {
        val (a, b) = accounts()
        reject { repository.saveEntry(Entry(kind = "transfer", amount = 100, accountId = a.id, targetId = a.id, time = 1)) }
        reject { repository.saveEntry(Entry(kind = "transfer", amount = 100, accountId = a.id, targetId = 999, time = 1)) }
        reject { repository.saveEntry(Entry(kind = "expense", amount = 0, accountId = a.id, time = 1)) }
        reject { repository.saveEntry(Entry(kind = "income", amount = -1, accountId = b.id, time = 1)) }
        reject { repository.saveEntry(Entry(kind = "income", amount = MAX_MONEY + 1, accountId = a.id, time = 1)) }
        reject { repository.saveEntry(Entry(kind = "income", amount = 100, accountId = a.id, time = 1, incomeSource = "person")) }
        reject { repository.saveEntry(Entry(kind = "expense", amount = 100, accountId = a.id, categoryId = 999, time = 1)) }
        reject { repository.saveEntry(Entry(kind = "correction", amount = 100, accountId = a.id, time = 1)) }
        reject { repository.saveAccount(a.copy(name = "Must rollback"), desiredBalance = MAX_MONEY + 1) }
        val state = repository.snapshot()
        assertEquals("Картка", state.accounts.first().name)
        assertEquals(100000L, balance(a, state.entries))
        assertTrue(state.entries.isEmpty())
    }

    @Test fun salaryAndPersonOriginsPersistAcrossDatabaseReopen() = runBlocking {
        val name = "income-origins-test.db"
        app.deleteDatabase(name)
        val disk = Room.databaseBuilder(app, FinanceDatabase::class.java, name).allowMainThreadQueries().addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
        try {
            val repo = FinanceRepository(disk)
            repo.saveAccount(Account(name = "Картка", opening = 0, color = palette[0]))
            val id = repo.snapshot().accounts.single().id
            repo.saveEntry(Entry(kind = "income", amount = 3000000, accountId = id, time = 1, incomeSource = "salary", sender = "  Роботодавець  "))
            repo.saveEntry(Entry(kind = "income", amount = 35050, accountId = id, time = 2, incomeSource = "person", sender = "  Олена  ", note = "Повернення боргу"))
        } finally { disk.close() }
        val reopened = Room.databaseBuilder(app, FinanceDatabase::class.java, name).allowMainThreadQueries().addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
        try {
            val state = FinanceRepository(reopened).snapshot()
            assertEquals(listOf("person", "salary"), state.entries.map { it.incomeSource })
            assertEquals(listOf("Олена", "Роботодавець"), state.entries.map { it.sender })
            assertEquals("Повернення боргу", state.entries.first().note)
            assertEquals(3035050L, balance(state.accounts.single(), state.entries))
        } finally { reopened.close(); app.deleteDatabase(name) }
    }

    @Test fun correctionCanBeEditedAndDeletedButNotConvertedOrMoved() = runBlocking {
        val (a, b) = accounts()
        repository.saveAccount(a, desiredBalance = 200000)
        val correction = repository.snapshot().entries.single()
        reject { repository.saveEntry(correction.copy(kind = "income")) }
        reject { repository.saveEntry(correction.copy(accountId = b.id)) }
        reject { repository.saveEntry(correction.copy(amount = 0)) }
        repository.saveEntry(correction.copy(amount = 50000, note = "Уточнено", time = 123))
        var state = repository.snapshot()
        assertEquals(150000L, balance(a, state.entries))
        assertEquals("Уточнено", state.entries.single().note)
        repository.deleteEntry(state.entries.single())
        state = repository.snapshot()
        assertEquals(100000L, balance(a, state.entries))
    }

    @Test fun concurrentCorrectionsSerializeAndDoNotLoseBalanceChanges() = runBlocking {
        val (a, _) = accounts()
        coroutineScope {
            listOf(async { repository.saveAccount(a, 200000) }, async { repository.saveAccount(a, 300000) }).awaitAll()
        }
        val state = repository.snapshot()
        assertEquals(2, state.entries.size)
        assertTrue(balance(a, state.entries) in setOf(200000L, 300000L))
        assertEquals(balance(a, state.entries) - a.opening, state.entries.sumOf { it.amount })
        val observed = repository.observeState().first()
        assertEquals(state, observed)
    }

    @Test fun observationRefreshesSameRowCountAndNeverExposesHalfOfACorrection() = runBlocking {
        val (a, _) = accounts()
        val updates = Channel<FinanceState>(Channel.UNLIMITED)
        val observer = launch { repository.observeState().collect { updates.send(it) } }
        try {
            val initial = withTimeout(10000) { updates.receive() }
            assertEquals("Картка", initial.accounts.first().name)
            repository.saveAccount(a.copy(name = "Перейменовано"))
            val renamed = withTimeout(10000) { updates.receive() }
            assertEquals("Перейменовано", renamed.accounts.first().name)
            assertEquals(initial.accounts.size, renamed.accounts.size)
            assertEquals(initial.entries.size, renamed.entries.size)
            repository.saveAccount(renamed.accounts.first().copy(name = "Уточнено"), desiredBalance = 120000)
            val corrected = withTimeout(10000) { updates.receive() }
            assertEquals("Уточнено", corrected.accounts.first().name)
            assertEquals(20000L, corrected.entries.single().amount)
            assertEquals("correction", corrected.entries.single().kind)
            assertEquals(120000L, balance(corrected.accounts.first(), corrected.entries))
        } finally {
            observer.cancelAndJoin()
            updates.close()
        }
    }

    @Test fun versionOneMigrationRetainsAccountsCategoriesAndEveryOperation() = runBlocking {
        val name = "migration-one-two-test.db"
        app.deleteDatabase(name)
        app.openOrCreateDatabase(name, 0, null).use { old ->
            val schemaFile = File("schemas/ua.moneytracking.FinanceDatabase/1.json")
            assertTrue("Version1 exported schema must be retained", schemaFile.exists())
            val schema = JSONObject(schemaFile.readText()).getJSONObject("database")
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.getJSONArray("indices")
                for (j in 0 until indices.length()) old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
            old.execSQL("INSERT INTO accounts VALUES (1,'Стара картка',100000,0,0,'1234')")
            old.execSQL("INSERT INTO accounts VALUES (2,'Готівка',50000,0,1,'')")
            old.execSQL("INSERT INTO categories VALUES (1,'Їжа',0,0)")
            old.execSQL("INSERT INTO entries VALUES (1,'income',20000,1,NULL,NULL,1000,'Старий дохід')")
            old.execSQL("INSERT INTO entries VALUES (2,'expense',35000,1,NULL,1,2000,'Продукти')")
            old.execSQL("INSERT INTO entries VALUES (3,'transfer',10000,1,2,NULL,3000,'Переказ')")
            old.version = 1
        }
        val migrated = Room.databaseBuilder(app, FinanceDatabase::class.java, name).allowMainThreadQueries().addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
        try {
            val repo = FinanceRepository(migrated)
            val state = repo.snapshot()
            assertEquals(2, state.accounts.size); assertEquals(1, state.categories.size); assertEquals(3, state.entries.size)
            assertEquals("1234", state.accounts.first().lastFour)
            assertEquals(75000L, balance(state.accounts[0], state.entries))
            assertEquals(60000L, balance(state.accounts[1], state.entries))
            assertTrue(state.entries.all { it.incomeSource == "other" && it.sender.isEmpty() })
            assertEquals(listOf("Переказ", "Продукти", "Старий дохід"), state.entries.map { it.note })
            repo.saveAccount(state.accounts[0], desiredBalance = 90000)
            assertEquals(90000L, balance(state.accounts[0], repo.snapshot().entries))
            assertEquals(3, migrated.openHelper.readableDatabase.version)
        } finally { migrated.close(); app.deleteDatabase(name) }
    }
}
