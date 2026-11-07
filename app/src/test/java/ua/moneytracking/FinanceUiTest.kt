package ua.moneytracking

import android.graphics.Bitmap
import android.graphics.Canvas
import java.io.File
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

/** End-user regressions: provenance, corrections, calendar scope and accessible totals. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FinanceUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val accounts = listOf(Account(1, "Картка", 500000, palette[0]), Account(2, "Готівка", 100000, palette[1], cash = true))
    private val category = Category(1, "Їжа", palette[0], 0)
    private val fixture = FinanceState(accounts, listOf(category), ready = true)

    @Test fun salaryStoresEmployerAndDoesNotBecomeAnExpenseCategory() {
        var saved: Entry? = null
        entry { saved = it }
        amount("1", "2", "5", "0", "Десяткова кома", "5", "0")
        compose.onNodeWithText("Інше надходження").performClick()
        compose.onNodeWithText("Зарплата").performClick()
        field("Роботодавець (необов’язково)").performTextReplacement("  Компанія  ")
        compose.onNodeWithText("Готово").performClick()
        compose.onNodeWithText("Додати дохід").assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals("income", saved?.kind)
            assertEquals(125050L, saved?.amount)
            assertEquals("salary", saved?.incomeSource)
            assertEquals("Компанія", saved?.sender)
            assertNull(saved?.categoryId)
        }
    }

    @Test fun personIncomeRequiresTheSenderAndThenSavesIt() {
        var saved: Entry? = null
        entry { saved = it }
        amount("5", "0", "0")
        compose.onNodeWithText("Інше надходження").performClick()
        compose.onNodeWithText("Від людини").performClick()
        compose.onNodeWithText("Готово").performClick()
        compose.onNodeWithText("Вкажіть ім’я або назву відправника.").assertExists()
        compose.runOnIdle { assertNull(saved) }
        field("Від кого").performTextReplacement("  Олена  ")
        compose.onNodeWithText("Готово").performClick()
        compose.onNodeWithText("Додати дохід").assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals("person", saved?.incomeSource)
            assertEquals("Олена", saved?.sender)
            assertEquals(50000L, saved?.amount)
        }
    }

    @Test fun editingIncomeKeepsOriginTimeNoteAndLegacyCategory() {
        val original = Entry(7, "income", 100000, 2, categoryId = 1, time = at(2026, 10, 15), note = "Повернення боргу", incomeSource = "person", sender = "Андрій")
        var saved: Entry? = null
        compose.setContent { Screen { EntrySheet(original, fixture, null, "expense", UserPreferences(haptics = false), {}, null) { saved = it } } }
        amount("5", "0") // First digit replaces the old amount rather than appending to it.
        compose.onNodeWithText("Зберегти зміни").performClick()
        compose.runOnIdle {
            assertEquals(original.copy(amount = 5000), saved)
        }
    }

    @Test fun editingCommentPreservesTheIncomeOriginAmountAndDate() {
        val original = Entry(8, "income", 125050, 2, time = at(2026, 11, 6), incomeSource = "salary", sender = "Компанія")
        var saved: Entry? = null
        compose.setContent { Screen { EntrySheet(original, fixture, null, "expense", UserPreferences(haptics = false), {}, null) { saved = it } } }
        compose.onNodeWithText("деталі", substring = true).performClick()
        compose.onNodeWithText("Додати коментар").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("  Аванс за листопад  ")
        compose.onNodeWithText("Готово").performClick()
        compose.onNodeWithText("Зберегти зміни").performClick()
        compose.runOnIdle { assertEquals(original.copy(note = "Аванс за листопад"), saved) }
    }

    @Test fun selectedMonthUsesActualEntryDatesAndExcludesTransfersAndCorrections() {
        var selected by mutableStateOf(YearMonth.of(2026, 12))
        val entries = listOf(
            Entry(1, "income", 123400, 1, time = at(2026, 12, 1), incomeSource = "salary"),
            Entry(2, "expense", 45600, 1, categoryId = 1, time = at(2026, 12, 31, 23, 59)),
            Entry(3, "transfer", 222200, 1, 2, time = at(2026, 12, 15)),
            Entry(4, "correction", -11100, 1, time = at(2026, 12, 20)),
            Entry(5, "expense", 33300, 1, time = at(2026, 11, 30, 23, 59)),
            Entry(6, "income", 987600, 1, time = at(2027, 1, 1))
        )
        compose.setContent { Screen { OverviewScreen(fixture.copy(entries = entries), selected, UserPreferences(), { _, _ -> }, {}, {}) } }
        scrollTo("Витрачено за місяць")
        compose.onNodeWithText("456 грн").assertExists()
        compose.onNodeWithText("1 234 грн").assertExists()
        compose.onNodeWithText("333 грн").assertDoesNotExist()
        compose.onNodeWithText("9 876 грн").assertDoesNotExist()
        compose.runOnIdle { selected = selected.plusMonths(1) }
        scrollTo("Витрачено за місяць")
        compose.onNodeWithText("9 876 грн").assertExists()
        compose.onNodeWithText("456 грн").assertDoesNotExist()
        compose.onNodeWithText("1 234 грн").assertDoesNotExist()
    }

    @Test fun historyShowsCorrectionAndCanFindIncomeBySender() {
        val correction = Entry(1, "correction", -2500, 1, time = at(2026, 11, 6), note = "Корекція балансу")
        val salary = Entry(2, "income", 300000, 1, time = at(2026, 11, 5), incomeSource = "salary", sender = "Роботодавець")
        val received = Entry(3, "income", 50000, 1, time = at(2026, 11, 4), incomeSource = "person", sender = "Олена")
        var edited: Entry? = null
        compose.setContent { Screen { TransactionsScreen(fixture.copy(entries = listOf(correction, salary, received)), YearMonth.of(2026, 11), { edited = it }, {}) } }
        scrollTo("Корекція")
        compose.onNodeWithText("Корекція").performClick()
        compose.runOnIdle { assertEquals(correction, edited) }
        scrollTo("Усі рахунки")
        compose.onNodeWithContentDescription("Фільтри історії").performClick()
        compose.onNodeWithText("Усі типи").performClick()
        compose.onNodeWithText("Корекції").performClick()
        scrollTo("Корекція")
        compose.onNodeWithText("Корекція").assertExists()
        compose.onNodeWithText("Зарплата").assertDoesNotExist()
        scrollTo("Скинути фільтри")
        compose.onNodeWithText("Скинути фільтри").performClick()
        field("Коментар, джерело, категорія або сума").performTextReplacement("Олена")
        scrollTo("Від: Олена")
        compose.onNodeWithText("Від: Олена").assertExists()
        compose.onNodeWithText("Корекція").assertDoesNotExist()
        compose.onNodeWithText("Зарплата").assertDoesNotExist()
    }

    @Test fun accountRenameDoesNotSilentlyCorrectANewerBalance() {
        val original = Account(1, "Картка", 100000, palette[0])
        var state by mutableStateOf(fixture.copy(accounts = listOf(original)))
        var saved: Pair<Account, Long?>? = null
        compose.setContent { Screen { AccountForm(original, state, {}, { account, desired -> saved = account to desired }) } }
        // A record arrived after the editor opened; metadata-only save must not undo it.
        compose.runOnIdle { state = state.copy(entries = listOf(Entry(1, "expense", 25000, 1, time = at(2026, 11, 6)))) }
        field("Назва рахунку").performTextReplacement("Основна картка")
        compose.onNodeWithText("Зберегти").performClick()
        compose.runOnIdle {
            assertEquals("Основна картка", saved?.first?.name)
            assertEquals(original.opening, saved?.first?.opening)
            assertNull(saved?.second)
        }
    }

    @Test fun negativeDesiredBalanceKeepsTheHistoricalOpening() {
        val original = Account(1, "Картка", 100000, palette[0])
        var saved: Pair<Account, Long?>? = null
        compose.setContent { Screen { AccountForm(original, fixture.copy(accounts = listOf(original)), {}, { account, desired -> saved = account to desired }) } }
        field("Баланс зараз, грн").performTextReplacement("250,50")
        compose.onNodeWithContentDescription("Змінити знак балансу").performClick()
        compose.onNodeWithText("Зберегти").performClick()
        compose.runOnIdle {
            assertEquals(-25050L, saved?.second)
            assertEquals(original.opening, saved?.first?.opening)
        }
    }

    @Test fun correctionEditorKeepsItsKindAndAccountAndSupportsBothDirections() {
        val original = Entry(9, "correction", -2500, 1, time = at(2026, 11, 6), note = "Корекція балансу")
        var saved: Entry? = null
        compose.setContent { Screen { CorrectionForm(original, fixture, {}, { saved = it }) } }
        field("Сума корекції, грн").performTextReplacement("75,25")
        compose.onNodeWithText("Напрям: Зменшити баланс").performClick()
        compose.onNodeWithText("Збільшити баланс").performClick()
        compose.onNodeWithText("Зберегти").performClick()
        compose.runOnIdle { assertEquals(original.copy(amount = 7525), saved) }
    }

    @Test fun largeFontMonthlyAmountsRemainUnclipped() {
        val spending = 123456789099L
        val income = 999999999999L
        val entries = listOf(Entry(1, "expense", spending, 1, categoryId = 1, time = at(2026, 11, 1)), Entry(2, "income", income, 1, time = at(2026, 11, 2)))
        compose.setContent { Screen(fontScale = 1.3f) { OverviewScreen(fixture.copy(entries = entries), YearMonth.of(2026, 11), UserPreferences(), { _, _ -> }, {}, {}) } }
        listOf(spending, income).forEach { amount ->
            val text = compactMoney(amount)
            val tag = if (amount == spending) "overview-monthly-expense" else "overview-monthly-income"
            compose.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasTestTag(tag))
            val node = compose.onNodeWithTag(tag).assertIsDisplayed().assertTextEquals(text)
            val layouts = mutableListOf<TextLayoutResult>()
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue("Text layout should be available for $text", layouts.isNotEmpty())
            if (layouts.any { it.hasVisualOverflow }) {
                layouts.forEach { result -> println("MONTHLY_LAYOUT text=$text size=${result.size} constraints=${result.layoutInput.constraints} lines=${result.lineCount} widthOverflow=${result.didOverflowWidth} heightOverflow=${result.didOverflowHeight} textHeight=${result.multiParagraph.height} maxLines=${result.layoutInput.maxLines}") }
                compose.runOnIdle {
                    val view = compose.activity.window.decorView
                    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                    view.draw(Canvas(bitmap))
                    val file = File("build/reports/ui-screenshots/monthly-overflow.png")
                    file.parentFile?.mkdirs()
                    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
            }
            assertFalse("Monthly amount is clipped: $text", layouts.any { it.hasVisualOverflow })
        }
    }

    @Test fun preferencesSurviveReloadAndInvalidDefaultTypeFallsBack() {
        val context = compose.activity
        val expected = UserPreferences(hideBalance = true, haptics = false, defaultAccountId = 2, defaultKind = "income")
        savePreferences(context, expected)
        assertEquals(expected, loadPreferences(context))
        context.getSharedPreferences("finance_preferences", 0).edit().putString("default_kind", "invalid").commit()
        assertEquals("expense", loadPreferences(context).defaultKind)
    }

    private fun entry(save: (Entry) -> Unit) {
        compose.setContent { Screen { EntrySheet(null, fixture, null, "income", UserPreferences(haptics = false), {}, null, save) } }
    }
    private fun amount(vararg keys: String) = keys.forEach { compose.onNodeWithContentDescription(it).performClick() }
    private fun field(label: String) = compose.onNode(hasSetTextAction() and (hasText(label) or hasContentDescription(label)))
    private fun scrollTo(text: String) { compose.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText(text)) }
    private fun at(year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0): Long = LocalDateTime.of(year, month, day, hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Composable private fun Screen(fontScale: Float? = null, content: @Composable () -> Unit) {
        FinanceTheme {
            Surface(Modifier.fillMaxSize(), color = Bg) {
                Box(Modifier.padding(16.dp)) {
                    if (fontScale == null) content()
                    else {
                        val density = LocalDensity.current
                        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) { content() }
                    }
                }
            }
        }
    }
}
