package ua.moneytracking

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import java.io.File
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CurrencyUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val fixture = FinanceState(accounts = listOf(
        Account(1, "Картка", 100000, palette[0]),
        Account(2, "Доларова", 20000, palette[1], currency = "USD"),
        Account(3, "Євро", 30000, palette[2], currency = "EUR")
    ), ready = true)

    @Test fun creatingADollarCardStoresSelectedCurrencyAndExactInitialCents() {
        var saved: Account? = null
        compose.setContent { Screen { AccountForm(null, fixture, {}) { account, _ -> saved = account } } }
        field("Назва рахунку").performTextReplacement("Доларова картка")
        compose.onNodeWithText("Валюта:", substring = true).performScrollTo().performClick()
        compose.onNodeWithText(currencyName("USD")).performClick()
        field("Початковий баланс, $").performTextReplacement("125,50")
        capture("new-usd-account", dialog = true)
        compose.onNodeWithText("Зберегти").performClick()
        compose.runOnIdle {
            assertEquals("USD", saved?.currency)
            assertEquals("Доларова картка", saved?.name)
            assertEquals(12550L, saved?.opening)
        }
    }

    @Test fun dollarIncomeUsesDollarAccountAndCurrencyLabel() {
        var saved: Entry? = null
        compose.setContent { Screen { EntrySheet(null, fixture, null, "income", UserPreferences(defaultAccountId = 2, haptics = false), {}, null) { saved = it } } }
        compose.onNodeWithText("Сума · USD").assertExists()
        listOf("2", "0", "Десяткова кома", "5").forEach { compose.onNodeWithContentDescription(it).performClick() }
        compose.onNodeWithText("Додати дохід").performClick()
        compose.runOnIdle { assertEquals(2L, saved?.accountId); assertEquals(2050L, saved?.amount) }
    }

    @Test fun foreignExchangeRequiresRateAndPreviewsBothExactAmounts() {
        var saved: Entry? = null
        compose.setContent { Screen { EntrySheet(null, fixture, null, "transfer", UserPreferences(defaultAccountId = 2, haptics = false), {}, null) { saved = it } } }
        listOf("1", "0", "0").forEach { compose.onNodeWithContentDescription(it).performClick() }
        compose.onNodeWithText("Переказати").assertIsNotEnabled()
        compose.onNodeWithText("Вказати курс обміну").performClick()
        field("Курс USD → UAH").performTextReplacement("41,25")
        compose.onNodeWithText("100,00 $ → 4 125,00 грн").assertExists()
        compose.onNodeWithText("Готово").performClick()
        compose.onNodeWithText("Зарахується: 4 125,00 грн").assertExists()
        capture("exchange-entry", dialog = true)
        compose.onNodeWithText("Переказати").assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(2L, saved?.accountId); assertEquals(1L, saved?.targetId)
            assertEquals(10000L, saved?.amount); assertEquals(412500L, saved?.targetAmount)
        }
    }

    @Test fun editingCrossCurrencyTransferPreservesRecipientCents() {
        val entry = Entry(1, "transfer", 35001, 2, 3, time = 1234, targetAmount = 32376)
        var saved: Entry? = null
        compose.setContent { Screen { EntrySheet(entry, fixture, null, "expense", UserPreferences(haptics = false), {}, null) { saved = it } } }
        compose.onNodeWithText("Зберегти зміни").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(entry, saved) }
    }

    @Test fun overviewShowsCurrencyBalancesSeparatelyAndScopesMonthlyTotals() {
        val now = System.currentTimeMillis()
        val state = fixture.copy(entries = listOf(Entry(1, "income", 10000, 1, time = now), Entry(2, "income", 20000, 2, time = now), Entry(3, "income", 30000, 3, time = now)))
        compose.setContent { Screen { OverviewScreen(state, YearMonth.now(), UserPreferences(), { _, _ -> }, {}, {}) } }
        scrollTo("Витрачено за місяць")
        compose.onNodeWithTag("overview-monthly-income").assertTextEquals("100 грн")
        compose.onNodeWithText("Валюта звіту").performClick()
        compose.onNodeWithText(currencyName("USD")).performClick()
        compose.onNodeWithTag("overview-monthly-income").assertTextEquals("200 $")
        compose.onNodeWithText("Валюта звіту").performClick()
        compose.onNodeWithText(currencyName("EUR")).performClick()
        compose.onNodeWithTag("overview-monthly-income").assertTextEquals("300 €")
        scrollTo("Усього на рахунках")
        capture("multicurrency-overview")
        compose.onNodeWithText("1 100 грн").assertExists()
        compose.onNodeWithText("400 $").assertExists()
        compose.onNodeWithText("600 €").assertExists()
    }

    @Test fun insightsScopesAllChartsAndMonthlyTotalsToSelectedCurrency() {
        val now = System.currentTimeMillis()
        val state = fixture.copy(entries = listOf(Entry(1, "expense", 10000, 1, time = now), Entry(2, "expense", 20000, 2, time = now), Entry(3, "expense", 30000, 3, time = now)))
        compose.setContent { Screen { InsightsScreen(state, YearMonth.now()) } }
        compose.onNodeWithTag("insights-monthly-expense").assertTextEquals("100 грн")
        compose.onNodeWithText("Валюта звіту").performClick()
        compose.onNodeWithText(currencyName("EUR")).performClick()
        compose.onNodeWithTag("insights-monthly-expense").assertTextEquals("300 €")
        compose.onNodeWithTag("insights-monthly-income").assertTextEquals("0 €")
        capture("eur-insights")
    }

    @Test fun settingsExposeBackupActionsAndDisableThemWhileBusy() {
        var exported = false
        var imported = false
        var busy by mutableStateOf(false)
        compose.setContent { Screen { SettingsScreen(fixture, UserPreferences(), {}, {}, {}, {}, {}, {}, {}, backupBusy = busy, onExport = { exported = true }, onImport = { imported = true }) } }
        scrollTo("Експортувати копію")
        compose.onNodeWithText("Експортувати копію").performClick()
        compose.onNodeWithText("Імпортувати копію").performClick()
        compose.runOnIdle { assertTrue(exported); assertTrue(imported) }
        capture("backup-settings")
        compose.runOnIdle { busy = true }
        compose.onNodeWithText("Експортувати копію").assertIsNotEnabled()
        compose.onNodeWithText("Імпортувати копію").assertIsNotEnabled()
    }

    @Test fun importPreviewRequiresExplicitReplacementAndCanBeCancelled() {
        var confirmed = false
        var cancelled = false
        compose.setContent { Screen { ImportPreviewDialog(FinanceBackup(fixture, UserPreferences()), false, { cancelled = true }, { confirmed = true }) } }
        compose.onNodeWithText("Рахунків: 3", substring = true).assertExists()
        compose.onNodeWithText("буде замінено", substring = true).assertExists()
        compose.runOnIdle { assertFalse(confirmed) }
        compose.onNodeWithText("Скасувати").performClick()
        compose.runOnIdle { assertTrue(cancelled); assertFalse(confirmed) }
    }

    private fun field(label: String) = compose.onNode(hasSetTextAction() and hasContentDescription(label))
    private fun scrollTo(text: String) = compose.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText(text))
    @Composable private fun Screen(content: @Composable () -> Unit) { FinanceTheme { Surface(Modifier.fillMaxSize(), color = Bg) { Box(Modifier.padding(16.dp)) { content() } } } }
    private fun capture(name: String, dialog: Boolean = false) {
        compose.runOnIdle {
            val view = (if (dialog) ShadowDialog.getShownDialogs().lastOrNull { it.isShowing }?.window?.decorView else null) ?: compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val file = File("build/reports/ui-screenshots/$name.png")
            file.parentFile?.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
