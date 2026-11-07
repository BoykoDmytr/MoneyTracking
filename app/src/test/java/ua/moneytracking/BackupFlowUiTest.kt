package ua.moneytracking

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/** Exercises real Activity Result contracts, document streams, ViewModel and Room together. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BackupFlowUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun exportAndImportUseSystemDocumentsAndRestoreOnlyAfterConfirmation() {
        val app = RuntimeEnvironment.getApplication()
        app.deleteDatabase("finance.db")
        savePreferences(app, UserPreferences())
        val store = ViewModelStore()
        val model = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory.getInstance(app))[FinanceModel::class.java]
        val exported = File(app.cacheDir, "export-flow.json")
        val imported = File(app.cacheDir, "import-flow.json")
        try {
            compose.setContent { FinanceTheme { FinanceApp(model) } }
            val saved = AtomicBoolean(false)
            compose.runOnIdle { model.save(Account(name = "Стара картка", opening = 50000, color = palette[0])) { saved.set(true) } }
            await { assertNull(model.error.value); saved.get() && model.state.value.accounts.size == 1 }
            compose.onNodeWithText("Налаштування").performClick()
            scrollTo("Експортувати копію")
            compose.onNodeWithText("Експортувати копію").performClick()
            compose.runOnIdle {
                val launched = shadowOf(compose.activity).nextStartedActivityForResult
                assertEquals(Intent.ACTION_CREATE_DOCUMENT, launched.intent.action)
                assertEquals("application/json", launched.intent.type)
                shadowOf(compose.activity).receiveResult(launched.intent, Activity.RESULT_OK, Intent().setData(Uri.fromFile(exported)))
            }
            await { assertNull(model.error.value); exported.length() > 0 && !model.backupBusy.value }
            val backup = BackupCodec.decode(exported.readText())
            assertEquals("Стара картка", backup.state.accounts.single().name)
            assertEquals(12, backup.state.categories.size)
            val replacement = FinanceBackup(FinanceState(
                accounts = listOf(Account(42, "Доларова картка", 10000, palette[1], currency = "USD")),
                entries = listOf(Entry(90, "income", 25000, 42, time = System.currentTimeMillis(), incomeSource = "salary", sender = "Компанія")),
                ready = true
            ), UserPreferences(true, false, 42, "income"))
            imported.writeText(BackupCodec.encode(replacement))
            scrollTo("Імпортувати копію")
            compose.onNodeWithText("Імпортувати копію").performClick()
            compose.runOnIdle {
                val launched = shadowOf(compose.activity).nextStartedActivityForResult
                assertEquals(Intent.ACTION_OPEN_DOCUMENT, launched.intent.action)
                shadowOf(compose.activity).receiveResult(launched.intent, Activity.RESULT_OK, Intent().setData(Uri.fromFile(imported)))
            }
            await { assertNull(model.error.value); model.importPreview.value != null }
            compose.onNodeWithText("Відновити резервну копію?").assertExists()
            assertEquals("Стара картка", model.state.value.accounts.single().name)
            assertEquals(UserPreferences(), loadPreferences(app))
            compose.onNodeWithText("Замінити дані").performClick()
            await { assertNull(model.error.value); !model.backupBusy.value && model.state.value.accounts.singleOrNull()?.id == 42L }
            assertEquals(replacement.state, model.state.value)
            assertEquals(replacement.preferences, loadPreferences(app))
            assertNull(model.importPreview.value)
            assertEquals(35000L, balance(model.state.value.accounts.single(), model.state.value.entries))
            assertNull(model.error.value)
        } finally {
            compose.runOnIdle { store.clear() }
            app.deleteDatabase("finance.db")
            exported.delete(); imported.delete()
        }
    }

    private fun scrollTo(text: String) = compose.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText(text))
    private fun await(condition: () -> Boolean) = compose.waitUntil(15000) {
        ShadowLooper.idleMainLooper()
        condition()
    }
}
