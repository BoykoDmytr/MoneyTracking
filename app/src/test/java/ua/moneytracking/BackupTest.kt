package ua.moneytracking

import android.app.Application
import android.net.Uri
import androidx.room.Room
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BackupTest {
    private lateinit var app: Application
    private lateinit var db: FinanceDatabase
    private lateinit var repository: FinanceRepository
    private val fixture = FinanceBackup(FinanceState(
        listOf(Account(1, "Картка", 100000, palette[0], lastFour = "1234"), Account(2, "Доларова", 20000, palette[1], currency = "USD"), Account(3, "Євро", 0, palette[2], cash = true, currency = "EUR")),
        listOf(Category(1, "Їжа", palette[0], 0)),
        listOf(
            Entry(5, "correction", -1000, 1, time = 5, note = "Корекція балансу"),
            Entry(4, "transfer", 10000, 2, 3, time = 4, targetAmount = 9000),
            Entry(3, "income", 100000, 2, time = 3, incomeSource = "salary", sender = "Роботодавець"),
            Entry(2, "income", 50000, 1, time = 2, incomeSource = "person", sender = "Олена"),
            Entry(1, "expense", 2500, 1, categoryId = 1, time = 1, note = "Хліб 🥖")
        ), true
    ), UserPreferences(true, false, 2, "income"), 1794000000000)

    @Before fun setup() {
        app = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(app, FinanceDatabase::class.java).allowMainThreadQueries().build()
        repository = FinanceRepository(db)
    }
    @After fun close() { db.close() }

    @Test fun roundTripPreservesAllRecordsCurrenciesOriginsCorrectionsAndPreferences() {
        assertEquals(fixture, BackupCodec.decode(BackupCodec.encode(fixture)))
        assertEquals(fixture, BackupCodec.decode("\uFEFF" + BackupCodec.encode(fixture)))
    }

    @Test fun invalidTypesVersionsAmountsLinksAndDuplicateIdsAreRejected() {
        val mutations: List<(JSONObject) -> Unit> = listOf(
            { it.put("version", 2) }, { it.put("format", "other-app") }, { it.put("minorUnitScale", 100) },
            { it.getJSONArray("accounts").getJSONObject(0).put("currency", "BTC") },
            { it.getJSONArray("accounts").put(it.getJSONArray("accounts").getJSONObject(0)) },
            { it.getJSONArray("entries").getJSONObject(0).put("id", -1) },
            { it.getJSONArray("entries").getJSONObject(0).put("amount", Long.MIN_VALUE) },
            { it.getJSONArray("entries").getJSONObject(0).put("amount", Long.MAX_VALUE) },
            { it.getJSONArray("entries").getJSONObject(4).put("amount", 1.5) },
            { it.getJSONArray("entries").getJSONObject(4).put("amount", "100") },
            { it.getJSONArray("entries").getJSONObject(4).put("amount", MAX_MONEY + 1) },
            { it.getJSONArray("entries").getJSONObject(4).put("accountId", 999) },
            { it.getJSONArray("entries").getJSONObject(4).put("categoryId", 999) },
            { it.getJSONArray("entries").getJSONObject(1).put("targetAmount", JSONObject.NULL) },
            { it.getJSONArray("entries").getJSONObject(1).put("targetId", 2) },
            { it.getJSONArray("entries").getJSONObject(3).put("sender", "") },
            { it.getJSONObject("preferences").put("defaultAccountId", 999) },
            { it.getJSONObject("preferences").put("haptics", "true") },
            { it.remove("entries") }
        )
        mutations.forEachIndexed { index, mutate ->
            val json = JSONObject(BackupCodec.encode(fixture)); mutate(json)
            assertThrows("Mutation $index", BackupException::class.java) { BackupCodec.decode(json.toString()) }
        }
        listOf("", "[]", "{}", "null", BackupCodec.encode(fixture) + "{}").forEach {
            assertThrows(BackupException::class.java) { BackupCodec.decode(it) }
        }
        assertThrows(BackupException::class.java) { BackupCodec.decode(BackupCodec.encode(fixture).replace("\"amount\": 2500", "\"amount\": 2500.0")) }
    }

    @Test fun replacementIsCompleteAndReimportingDoesNotDuplicateAnything() = runBlocking {
        repository.saveAccount(Account(name = "Стара картка", opening = 1, color = 0))
        repository.restore(BackupCodec.decode(BackupCodec.encode(fixture)))
        assertEquals(fixture.state, repository.snapshot())
        repository.restore(fixture)
        val state = repository.snapshot()
        assertEquals(fixture.state, state)
        assertEquals(146500L, balance(state.accounts[0], state.entries))
        assertEquals(110000L, balance(state.accounts[1], state.entries))
        assertEquals(9000L, balance(state.accounts[2], state.entries))
        repository.saveAccount(Account(name = "Новий рахунок", opening = 0, color = 0, currency = "USD"))
        assertEquals(4, repository.snapshot().accounts.size)
    }

    @Test fun invalidRestoreLeavesExistingHistoryUntouched() = runBlocking {
        repository.restore(fixture)
        val invalid = fixture.copy(state = fixture.state.copy(entries = fixture.state.entries + fixture.state.entries.first()))
        try { repository.restore(invalid); fail() } catch (_: BackupException) { }
        assertEquals(fixture.state, repository.snapshot())
    }

    @Test fun sqlFailureAfterDeletesRollsBackAccountsCategoriesAndEntriesTogether() = runBlocking {
        repository.restore(fixture)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_restore BEFORE INSERT ON entries WHEN NEW.note = 'abort' BEGIN SELECT RAISE(ABORT, 'simulated storage failure'); END")
        val replacement = fixture.copy(state = fixture.state.copy(entries = fixture.state.entries.map { it.copy(note = "abort") }))
        var failed = false
        try { repository.restore(replacement) } catch (_: android.database.sqlite.SQLiteException) { failed = true }
        assertTrue("The simulated write must fail", failed)
        assertEquals(fixture.state, repository.snapshot())
    }

    @Test fun documentStreamsRoundTripAndRejectBadUtf8AndOversizedFiles() {
        val file = File(app.cacheDir, "finance-backup-test.json")
        try {
            val uri = Uri.fromFile(file)
            BackupFiles.write(app.contentResolver, uri, BackupCodec.encode(fixture))
            assertEquals(fixture, BackupFiles.read(app.contentResolver, uri))
            file.writeBytes(byteArrayOf(0xC3.toByte(), 0x28))
            assertThrows(BackupException::class.java) { BackupFiles.read(app.contentResolver, uri) }
            file.outputStream().use { output -> repeat(33) { output.write(ByteArray(1024 * 1024)) } }
            assertThrows(BackupException::class.java) { BackupFiles.read(app.contentResolver, uri) }
        } finally { file.delete() }
    }

    @Test fun differentCurrencyTransfersRequireCreditAndEditDeleteExactlyBothBalances() = runBlocking {
        repository.restore(fixture)
        val state = repository.snapshot()
        val transfer = state.entries.single { it.kind == "transfer" }
        try { repository.saveEntry(transfer.copy(targetAmount = null)); fail() } catch (_: IllegalArgumentException) { }
        try { repository.saveEntry(transfer.copy(targetAmount = 0)); fail() } catch (_: IllegalArgumentException) { }
        try { repository.saveAccount(state.accounts[1].copy(currency = "EUR")); fail() } catch (_: IllegalArgumentException) { }
        assertEquals(fixture.state, repository.snapshot())
        repository.saveEntry(transfer.copy(amount = 5000, targetAmount = 4500))
        val edited = repository.snapshot()
        assertEquals(115000L, balance(state.accounts[1], edited.entries))
        assertEquals(4500L, balance(state.accounts[2], edited.entries))
        repository.deleteEntry(edited.entries.single { it.kind == "transfer" })
        assertEquals(120000L, balance(state.accounts[1], repository.snapshot().entries))
        assertEquals(0L, balance(state.accounts[2], repository.snapshot().entries))
    }

    @Test fun migrationFromVersionTwoPreservesSalaryAndCorrectionAndDefaultsToUah() = runBlocking {
        val name = "migration-two-three-test.db"
        app.deleteDatabase(name)
        app.openOrCreateDatabase(name, 0, null).use { old ->
            val schema = JSONObject(File("schemas/ua.moneytracking.FinanceDatabase/2.json").readText()).getJSONObject("database")
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.getJSONArray("indices")
                for (j in 0 until indices.length()) old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
            old.execSQL("INSERT INTO accounts VALUES (1,'Стара картка',100000,0,0,'1234')")
            old.execSQL("INSERT INTO entries VALUES (1,'income',20000,1,NULL,NULL,1000,'Аванс','salary','Компанія')")
            old.execSQL("INSERT INTO entries VALUES (2,'correction',-5000,1,NULL,NULL,2000,'Корекція балансу','other','')")
            old.version = 2
        }
        val migrated = Room.databaseBuilder(app, FinanceDatabase::class.java, name).allowMainThreadQueries().addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
        try {
            val state = FinanceRepository(migrated).snapshot()
            assertEquals("UAH", state.accounts.single().currency)
            assertEquals(115000L, balance(state.accounts.single(), state.entries))
            assertEquals("salary", state.entries.last().incomeSource)
            assertEquals("Компанія", state.entries.last().sender)
            assertTrue(state.entries.all { it.targetAmount == null })
            assertEquals(3, migrated.openHelper.readableDatabase.version)
            assertEquals(state, BackupCodec.decode(BackupCodec.encode(FinanceBackup(state, UserPreferences()))).state)
        } finally { migrated.close(); app.deleteDatabase(name) }
    }
}
