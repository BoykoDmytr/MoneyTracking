package ua.moneytracking

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowDialog
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.YearMonth

/** User-visible workflows rendered in a small phone viewport, without an emulator. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DailyUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val fixture = FinanceState(
        accounts = listOf(Account(1, "Картка", 500000, palette[0]), Account(2, "Готівка", 100000, palette[1], cash = true)),
        categories = listOf(Category(1, "Їжа", palette[0], 0)),
        ready = true
    )

    @Test fun managementLivesInSettingsAndCallsTheSelectedActions() {
        var added = false
        var editedCategory: Category? = null
        compose.setContent {
            TestScreen {
                SettingsScreen(fixture, UserPreferences(), {}, { added = true }, {}, {}, {}, { editedCategory = it }, {})
            }
        }
        capture("settings")
        compose.onNodeWithText("Рахунки").performClick()
        compose.onAllNodesWithText("Змінити назву або баланс").onFirst().assertExists()
        compose.onNodeWithText("Додати").performClick()
        compose.runOnIdle { assertTrue(added) }
        compose.onNodeWithContentDescription("Назад до налаштувань").performClick()
        compose.onNodeWithText("Категорії").performClick()
        compose.onNodeWithContentDescription("Редагувати категорію Їжа").performClick()
        compose.runOnIdle { assertEquals(1L, editedCategory?.id) }
    }

    @Test fun overviewStartsAnOperationWithoutManagementControls() {
        var request: Pair<String, Long?>? = null
        compose.setContent {
            TestScreen {
                OverviewScreen(fixture, YearMonth.now(), UserPreferences(), { kind, category -> request = kind to category }, {}, {})
            }
        }
        compose.onNodeWithText("Змінити назву або баланс").assertDoesNotExist()
        compose.onNodeWithContentDescription("Редагувати категорію Їжа").assertDoesNotExist()
        compose.onNodeWithText("Додати рахунок").assertDoesNotExist()
        compose.onNodeWithText("Додати категорію").assertDoesNotExist()
        capture("overview")
        compose.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText("Їжа"))
        compose.onNodeWithText("Їжа").performClick()
        compose.runOnIdle { assertEquals("expense" to 1L, request) }
    }

    @Test fun expenseEntryUsesTheConfiguredAccountAndExactKopecks() {
        var saved: Entry? = null
        compose.setContent {
            TestScreen {
                EntrySheet(null, fixture, 1, "expense", UserPreferences(defaultAccountId = 2, haptics = false), {}, null) { saved = it }
            }
        }
        listOf("3", "5", "0", "Десяткова кома", "5", "0").forEach {
            compose.onNodeWithContentDescription(it).assertIsDisplayed().performClick()
        }
        compose.onNodeWithContentDescription("Стерти цифру").assertIsDisplayed()
        compose.onNodeWithText("Додати витрату").assertIsDisplayed()
        capture("entry")
        compose.onNodeWithText("Додати витрату").performClick()
        compose.runOnIdle {
            assertNotNull(saved)
            assertEquals(35050L, saved?.amount)
            assertEquals(2L, saved?.accountId)
            assertEquals(1L, saved?.categoryId)
            assertEquals("expense", saved?.kind)
        }
    }
    @Composable private fun TestScreen(content: @Composable () -> Unit) {
        FinanceTheme {
            Surface(Modifier.fillMaxSize(), color = Bg) {
                Box(Modifier.padding(16.dp)) { content() }
            }
        }
    }

    private fun capture(name: String) {
        try {
            compose.runOnIdle {
                val activityView = compose.activity.window.decorView
                val view = if (name == "entry") ShadowDialog.getLatestDialog()?.window?.decorView ?: activityView else activityView
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                val file = File("build/reports/ui-screenshots/$name.png")
                file.parentFile?.mkdirs()
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        } catch (e: Exception) {
            println("Optional screenshot unavailable ($name): ${e.message}")
        }
    }
}
