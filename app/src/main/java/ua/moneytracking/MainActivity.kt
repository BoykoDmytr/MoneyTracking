package ua.moneytracking

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.graphics.graphicsLayer
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

internal val Bg = Color(0xFF0C1215)
internal val Panel = Color(0xFF162025)
internal val Muted = Color(0xFF91A3A8)
internal val Accent = Color(0xFFB4F0D2)
internal val Red = Color(0xFFFF8896)
internal val Green = Color(0xFF70D9AD)
internal val uk = Locale.forLanguageTag("uk-UA")
internal fun Entry.date() = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).toLocalDateTime()
internal fun Entry.inMonth(month: YearMonth) = YearMonth.from(date()) == month
internal fun kindName(kind: String) = when (kind) { "income" -> "Дохід"; "transfer" -> "Переказ"; "correction" -> "Корекція"; else -> "Витрата" }
internal fun categoryIcon(i: Int): ImageVector = when (i % 12) {
    0 -> Icons.Default.Restaurant
    1 -> Icons.Default.LocalCafe
    2 -> Icons.Default.SportsEsports
    3 -> Icons.Default.DirectionsBus
    4 -> Icons.Default.Favorite
    5 -> Icons.Default.Pets
    6 -> Icons.Default.People
    7 -> Icons.Default.Checkroom
    8 -> Icons.Default.Home
    9 -> Icons.Default.Subscriptions
    10 -> Icons.Default.ShoppingBag
    else -> Icons.Default.MoreHoriz
}

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(base: Context) {
        val locale = Locale.forLanguageTag("uk-UA")
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply { setLocale(locale) }
        super.attachBaseContext(base.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.rgb(12, 18, 21))
        )
        setContent { FinanceTheme { FinanceApp() } }
    }
}

@Composable internal fun FinanceTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent, onPrimary = Bg, background = Bg, surface = Panel,
            onBackground = Color(0xFFECF4F1), onSurface = Color(0xFFECF4F1),
            secondary = Green, onSecondary = Bg,
            primaryContainer = Color(0xFF263C35), onPrimaryContainer = Accent,
            secondaryContainer = Color(0xFF263C35), onSecondaryContainer = Accent,
            surfaceVariant = Color(0xFF223136), onSurfaceVariant = Muted,
            error = Red, outline = Color(0xFF35474C)
        ),
        content = content
    )
}

@Composable
internal fun FinanceApp(model: FinanceModel = viewModel()) {
    val context = LocalContext.current
    val state by model.state.collectAsState()
    val error by model.error.collectAsState()
    val backupBusy by model.backupBusy.collectAsState()
    val importPreview by model.importPreview.collectAsState()
    var writePending by remember { mutableStateOf(false) }
    var preferences by remember { mutableStateOf(loadPreferences(context)) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var monthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val month = YearMonth.parse(monthText)
    var accountForm by remember { mutableStateOf<Account?>(null) }
    var categoryForm by remember { mutableStateOf<Category?>(null) }
    var accountOpen by remember { mutableStateOf(false) }
    var returnToOverviewAfterAccount by rememberSaveable { mutableStateOf(false) }
    var categoryOpen by remember { mutableStateOf(false) }
    var entryOpen by rememberSaveable { mutableStateOf(false) }
    var entryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var draftCategory by rememberSaveable { mutableStateOf<Long?>(null) }
    var draftKind by rememberSaveable { mutableStateOf(preferences.defaultKind) }
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    val titles = listOf("Огляд", "Історія", "Аналітика", "Налаштування")
    val icons = listOf(Icons.Default.SpaceDashboard, Icons.AutoMirrored.Filled.ReceiptLong, Icons.Default.BarChart, Icons.Default.Settings)
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val screenStates = rememberSaveableStateHolder()
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) model.exportTo(uri, preferences) { scope.launch { snack.showSnackbar("Резервну копію збережено") } }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) model.prepareImport(uri)
    }

    fun startAccount() {
        returnToOverviewAfterAccount = true
        tab = 3
        accountForm = null
        accountOpen = true
    }
    fun newEntry(kind: String, category: Long? = null) {
        if (state.accounts.isEmpty()) { startAccount(); return }
        entryId = null
        draftCategory = category
        draftKind = kind
        entryOpen = true
    }
    fun deleteAccount(account: Account) {
        confirm = "Видалити «${account.name}» та всі пов’язані операції? Власні перекази теж буде видалено, а баланси інших рахунків перераховано." to {
            if (!writePending) {
                writePending = true
                model.delete(account) {
                    writePending = false
                    if (preferences.defaultAccountId == account.id) {
                        preferences = preferences.copy(defaultAccountId = null)
                        savePreferences(context, preferences)
                    }
                }
            }
        }
    }
    fun deleteCategory(category: Category) {
        confirm = "Видалити категорію «${category.name}»? Її операції залишаться в історії без категорії." to {
            if (!writePending) {
                writePending = true
                model.delete(category) { writePending = false; categoryOpen = false }
            }
        }
    }
    fun deleteEntry(entry: Entry) {
        confirm = "Видалити цю операцію? Баланси й статистику буде перераховано." to {
            if (!writePending) {
                writePending = true
                model.delete(entry) { writePending = false; entryOpen = false }
            }
        }
    }

    LaunchedEffect(state.accounts.size, returnToOverviewAfterAccount) {
        if (returnToOverviewAfterAccount && state.accounts.isNotEmpty()) {
            tab = 0
            returnToOverviewAfterAccount = false
        }
    }
    LaunchedEffect(error) { if (error != null) writePending = false }
    Scaffold(
        containerColor = Bg,
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            NavigationBar(containerColor = Panel, tonalElevation = 0.dp) {
                titles.forEachIndexed { index, title ->
                    val iconScale by animateFloatAsState(if (tab == index) 1.08f else 1f, tween(150), label = "navigationIcon")
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Icon(icons[index], null, Modifier.size(24.dp).graphicsLayer { scaleX = iconScale; scaleY = iconScale }) },
                        label = { Text(title, fontSize = 11.sp, maxLines = 1, fontWeight = if (tab == index) FontWeight.SemiBold else FontWeight.Normal) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Accent, selectedTextColor = Accent,
                            indicatorColor = Color(0xFF263C35), unselectedIconColor = Muted, unselectedTextColor = Muted
                        )
                    )
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Мої фінанси", fontSize = 12.sp, color = Muted)
                    AnimatedContent(
                        targetState = titles[tab],
                        transitionSpec = { (fadeIn(tween(160)) togetherWith fadeOut(tween(80))).using(SizeTransform(clip = false)) },
                        label = "screenTitle"
                    ) { title ->
                        Text(title, fontSize = 29.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 3.dp))
                    }
                }
                if (tab == 0) Box(Modifier.size(44.dp).clip(RoundedCornerShape(15.dp)).background(Panel), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.AccountBalanceWallet, null, tint = Accent, modifier = Modifier.size(23.dp))
                }
                if (tab == 1 && state.accounts.isNotEmpty()) FilledTonalButton(
                    onClick = { newEntry(preferences.defaultKind) },
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = Accent, contentColor = Bg),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp), shape = RoundedCornerShape(14.dp)
                ) { Icon(Icons.Default.Add, null, Modifier.size(19.dp)); Spacer(Modifier.width(5.dp)); Text("Операція", fontSize = 12.sp) }
            }
            if (tab != 3) PeriodSelector(month) { monthText = it.toString() }
            if (!state.ready) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Accent) }
            else AnimatedContent(
                targetState = tab,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    val direction = if (targetState > initialState) 1 else -1
                    (fadeIn(tween(180)) + slideInHorizontally(tween(200, easing = FastOutSlowInEasing)) { direction * it / 18 }) togetherWith
                        (fadeOut(tween(90)) + slideOutHorizontally(tween(160)) { -direction * it / 24 }) using SizeTransform(clip = false)
                },
                label = "tabTransition"
            ) { screen ->
                screenStates.SaveableStateProvider(screen) {
                    when (screen) {
                        0 -> OverviewScreen(state, month, preferences, onNewEntry = ::newEntry, onHistory = { tab = 1 }, onStart = ::startAccount)
                        1 -> TransactionsScreen(state, month, onEdit = { entryId = it.id; entryOpen = true }, onDelete = ::deleteEntry)
                        2 -> InsightsScreen(state, month)
                        3 -> SettingsScreen(
                            state, preferences,
                            onPreferencesChange = { preferences = it; savePreferences(context, it) },
                            onAddAccount = { returnToOverviewAfterAccount = false; accountForm = null; accountOpen = true },
                            onEditAccount = { accountForm = it; accountOpen = true },
                            onDeleteAccount = ::deleteAccount,
                            onAddCategory = { categoryForm = null; categoryOpen = true },
                            onEditCategory = { categoryForm = it; categoryOpen = true },
                            onDeleteCategory = ::deleteCategory,
                            backupBusy = backupBusy,
                            onExport = { exportLauncher.launch("Moi-Finansy-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss"))}.json") },
                            onImport = { importLauncher.launch(arrayOf("*/*")) }
                        )
                    }
                }
            }
        }
    }
    // Configuration dialogs are reachable only from Settings or explicit first-run onboarding.
    if (tab == 3 && accountOpen) AccountForm(accountForm, state, { accountOpen = false; returnToOverviewAfterAccount = false }) { account, desiredBalance ->
        if (!writePending) {
            writePending = true
            model.save(account, desiredBalance) { writePending = false; accountOpen = false }
        }
    }
    if (tab == 3 && categoryOpen) CategoryForm(categoryForm, { categoryOpen = false }, categoryForm?.let { c -> { deleteCategory(c) } }) { category ->
        if (!writePending) {
            writePending = true
            model.save(category) { writePending = false; categoryOpen = false }
        }
    }
    fun saveEntry(entry: Entry) {
        if (!writePending) {
            writePending = true
            model.save(entry) {
                monthText = YearMonth.from(entry.date()).toString()
                writePending = false
                entryOpen = false
            }
        }
    }
    val original = state.entries.find { it.id == entryId }
    if (entryOpen && state.ready && state.accounts.isNotEmpty() && (entryId == null || original != null)) {
        if (original?.kind == "correction") CorrectionForm(original, state, { entryOpen = false }, ::saveEntry)
        else EntrySheet(original, state, draftCategory, draftKind, preferences, { entryOpen = false }, original?.let { e -> { deleteEntry(e) } }, ::saveEntry)
    }
    confirm?.let { (message, action) ->
        AlertDialog(
            onDismissRequest = { confirm = null }, title = { Text("Підтвердьте видалення") }, text = { Text(message) },
            confirmButton = { TextButton(onClick = { action(); confirm = null }) { Text("Видалити", color = Red) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Скасувати") } }
        )
    }
    importPreview?.let { backup ->
        ImportPreviewDialog(backup, backupBusy, model::dismissImport) {
            model.confirmImport { restored ->
                preferences = restored
                accountOpen = false; categoryOpen = false; entryOpen = false
                returnToOverviewAfterAccount = false
                monthText = YearMonth.now().toString()
                scope.launch { snack.showSnackbar("Дані відновлено з копії") }
            }
        }
    }
    if (error != null) AlertDialog(
        onDismissRequest = { model.error.value = null },
        title = { Text("Не вдалося виконати дію") },
        text = { Text(error.orEmpty()) },
        confirmButton = { TextButton(onClick = { model.error.value = null }) { Text("Гаразд") } }
    )
}

@Composable
private fun PeriodSelector(month: YearMonth, change: (YearMonth) -> Unit) {
    var picker by remember { mutableStateOf(false) }
    var year by remember(month) { mutableIntStateOf(month.year) }
    Row(Modifier.fillMaxWidth().padding(bottom = 16.dp).clip(RoundedCornerShape(16.dp)).background(Panel), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { change(month.minusMonths(1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Попередній місяць", tint = Muted) }
        TextButton(onClick = { year = month.year; picker = true }, modifier = Modifier.weight(1f)) {
            AnimatedContent(
                targetState = month,
                transitionSpec = {
                    val direction = if (targetState > initialState) 1 else -1
                    (fadeIn(tween(160)) + slideInVertically(tween(160)) { direction * it / 3 }) togetherWith
                        (fadeOut(tween(80)) + slideOutVertically(tween(120)) { -direction * it / 3 })
                },
                label = "monthTransition"
            ) { selectedMonth ->
                Text(selectedMonth.format(DateTimeFormatter.ofPattern("LLLL yyyy", uk)).replaceFirstChar { it.titlecase(uk) }, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
            }
            Icon(Icons.Default.ExpandMore, null, Modifier.size(17.dp).padding(start = 4.dp), tint = Muted)
        }
        IconButton(onClick = { change(month.plusMonths(1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Наступний місяць", tint = Muted) }
    }
    if (picker) AlertDialog(
        onDismissRequest = { picker = false },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { year-- }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Попередній рік") }
                Text("$year", Modifier.weight(1f), textAlign = TextAlign.Center)
                IconButton(onClick = { year++ }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Наступний рік") }
            }
        },
        text = {
            Column {
                (1..12).chunked(3).forEach { row ->
                    Row {
                        row.forEach { number ->
                            TextButton(onClick = { change(YearMonth.of(year, number)); picker = false }, modifier = Modifier.weight(1f)) {
                                Text(YearMonth.of(year, number).format(DateTimeFormatter.ofPattern("LLL", uk)))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { change(YearMonth.now()); picker = false }) { Text("Цей місяць") } },
        dismissButton = { TextButton(onClick = { picker = false }) { Text("Закрити") } }
    )
}

@Composable internal fun CategoryBadge(category: Category?, modifier: Modifier = Modifier) {
    val color = category?.let { Color(it.color) } ?: Accent
    Box(modifier.clip(RoundedCornerShape(13.dp)).background(color.copy(alpha = .13f)), contentAlignment = Alignment.Center) {
        Icon(category?.let { categoryIcon(it.icon) } ?: Icons.Default.SwapHoriz, null, tint = color, modifier = Modifier.size(24.dp))
    }
}
