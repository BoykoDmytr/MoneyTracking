package ua.moneytracking

import android.content.Context
import android.content.res.Configuration
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

internal val Bg = Color(0xFF101419)
internal val Panel = Color(0xFF191F27)
internal val Muted = Color(0xFFA8ADB7)
internal val Accent = Color(0xFFA5CFFF)
internal val Red = Color(0xFFEE607C)
internal val Green = Color(0xFF44B794)
internal val uk = Locale.forLanguageTag("uk-UA")
internal fun Entry.date() = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).toLocalDateTime()
internal fun Entry.inMonth(month: YearMonth) = YearMonth.from(date()) == month
internal fun kindName(kind: String) = when (kind) { "income" -> "Дохід"; "transfer" -> "Переказ"; else -> "Витрата" }
internal fun categoryIcon(i: Int): ImageVector = when (i % 12) {
    0 -> Icons.Default.Restaurant; 1 -> Icons.Default.LocalCafe; 2 -> Icons.Default.SportsEsports
    3 -> Icons.Default.DirectionsBus; 4 -> Icons.Default.Favorite; 5 -> Icons.Default.Pets
    6 -> Icons.Default.People; 7 -> Icons.Default.Checkroom; 8 -> Icons.Default.Home
    9 -> Icons.Default.Subscriptions; 10 -> Icons.Default.ShoppingBag; else -> Icons.Default.MoreHoriz
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
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.rgb(16, 20, 25))
        )
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Bg, surface = Panel, onSurface = Color(0xFFF4F2FF), secondary = Green, error = Red)) {
                FinanceApp()
            }
        }
    }
}

@Composable
private fun FinanceApp(model: FinanceModel = viewModel()) {
    val state by model.state.collectAsState()
    val error by model.error.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(1) }
    var monthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val month = YearMonth.parse(monthText)
    var hidden by rememberSaveable { mutableStateOf(false) }
    var accountForm by remember { mutableStateOf<Account?>(null) }
    var categoryForm by remember { mutableStateOf<Category?>(null) }
    var entryForm by remember { mutableStateOf<Entry?>(null) }
    var accountOpen by remember { mutableStateOf(false) }
    var categoryOpen by remember { mutableStateOf(false) }
    var entryOpen by remember { mutableStateOf(false) }
    var draftCategory by remember { mutableStateOf<Long?>(null) }
    var draftKind by remember { mutableStateOf("expense") }
    var selectedCategory by rememberSaveable { mutableStateOf<Long?>(null) }
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    val names = listOf("Рахунки", "Категорії", "Операції", "Статистика", "Більше")
    val icons = listOf(Icons.Default.AccountBalanceWallet, Icons.Default.Category, Icons.AutoMirrored.Filled.ReceiptLong, Icons.Default.BarChart, Icons.Default.Apps)
    val snack = remember { SnackbarHostState() }
    fun newEntry(category: Long? = null, kind: String = "expense") {
        entryForm = null; draftCategory = category; draftKind = kind; entryOpen = true
    }
    LaunchedEffect(error) { error?.let { snack.showSnackbar(it); model.error.value = null } }
    Scaffold(containerColor = Bg, snackbarHost = { SnackbarHost(snack) }, bottomBar = {
        NavigationBar(containerColor = Color(0xFF242E3B), tonalElevation = 0.dp) {
            names.forEachIndexed { i, title -> NavigationBarItem(
                selected = tab == i, onClick = { tab = i; selectedCategory = null },
                icon = { Icon(icons[i], title, Modifier.size(27.dp)) },
                label = { Text(title, fontSize = 11.sp, maxLines = 1, fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Normal) },
                colors = NavigationBarItemDefaults.colors(selectedIconColor = Accent, selectedTextColor = Accent, indicatorColor = Color(0xFF3B4A5D), unselectedIconColor = Muted, unselectedTextColor = Muted)
            ) }
        }
    }, floatingActionButton = {
        if (tab != 4 && state.ready) ExtendedFloatingActionButton(
            onClick = { if (tab == 0) { accountForm = null; accountOpen = true } else newEntry() },
            icon = { Icon(Icons.Default.Add, null) }, text = { Text("Додати", fontWeight = FontWeight.SemiBold) },
            containerColor = Color(0xFF12577F), contentColor = Color(0xFFC6E6FF), shape = RoundedCornerShape(22.dp)
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            if (tab != 4) {
                Box(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp)) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 38.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Загальний баланс", color = Muted, fontSize = 14.sp)
                            IconButton(onClick = { hidden = !hidden }, modifier = Modifier.size(40.dp)) { Icon(if (hidden) Icons.Default.VisibilityOff else Icons.Default.Visibility, if (hidden) "Показати баланс" else "Приховати баланс", Modifier.size(19.dp), tint = Muted) }
                        }
                        Text(if (hidden) "•••• грн" else compactMoney(state.accounts.sumOf { balance(it, state.entries) }), fontSize = 32.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                    }
                    Box(Modifier.align(Alignment.CenterEnd)) {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "Дії", tint = Muted) }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("Додати рахунок") }, onClick = { menuOpen = false; accountForm = null; accountOpen = true })
                            DropdownMenuItem(text = { Text("Додати категорію") }, onClick = { menuOpen = false; categoryForm = null; categoryOpen = true })
                            DropdownMenuItem(text = { Text("Поточний місяць") }, onClick = { menuOpen = false; monthText = YearMonth.now().toString() })
                        }
                    }
                }
                if (tab in 1..3) MonthPicker(month) { monthText = it.toString() }
                else Text("Мої рахунки", color = Muted, modifier = Modifier.padding(vertical = 16.dp))
            } else Text("Налаштування", fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 22.dp))
            if (!state.ready) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else when (tab) {
                0 -> AccountScreen(state, onEdit = { accountForm = it; accountOpen = true }, onDelete = { a ->
                    confirm = "Видалити «${a.name}» та всі пов’язані операції? Перекази теж буде видалено; баланси інших рахунків перерахуються." to { model.delete(a) }
                }, onAdd = { accountForm = null; accountOpen = true })
                1 -> CategoryScreen(state, month, onOpen = { selectedCategory = it.id; tab = 2 }, onQuickAdd = { c, kind -> newEntry(c.id, kind) }, onEdit = { categoryForm = it; categoryOpen = true }, onAdd = { categoryForm = null; categoryOpen = true })
                2 -> HistoryScreen(state, month, selectedCategory, onClearCategory = { selectedCategory = null }, onEdit = { entryForm = it; entryOpen = true }, onDelete = { e -> confirm = "Видалити операцію? Баланси будуть перераховані." to { model.delete(e) } })
                3 -> StatisticsScreen(state, month)
                4 -> SettingsScreen()
            }
        }
    }
    if (accountOpen) AccountForm(accountForm, state, { accountOpen = false }) { model.save(it); accountOpen = false }
    if (categoryOpen) CategoryForm(categoryForm, { categoryOpen = false }, onDelete = categoryForm?.let { c -> { confirm = "Видалити категорію «${c.name}»? Операції залишаться без категорії." to { model.delete(c); categoryOpen = false } } }) { model.save(it); categoryOpen = false }
    if (entryOpen) {
        if (state.accounts.isEmpty()) AlertDialog(onDismissRequest = { entryOpen = false }, title = { Text("Спочатку додайте рахунок") }, text = { Text("Створіть картку або готівковий рахунок для першої операції.") }, confirmButton = { TextButton(onClick = { entryOpen = false; accountForm = null; accountOpen = true }) { Text("Створити рахунок") } }, dismissButton = { TextButton(onClick = { entryOpen = false }) { Text("Закрити") } })
        else EntrySheet(entryForm, state, draftCategory, draftKind, { entryOpen = false }, onDelete = entryForm?.let { e -> { confirm = "Видалити операцію? Баланси будуть перераховані." to { model.delete(e); entryOpen = false } } }) { model.save(it); entryOpen = false }
    }
    confirm?.let { (message, action) -> AlertDialog(onDismissRequest = { confirm = null }, title = { Text("Підтвердіть видалення") }, text = { Text(message) }, confirmButton = { TextButton(onClick = { action(); confirm = null }) { Text("Видалити", color = Red) } }, dismissButton = { TextButton(onClick = { confirm = null }) { Text("Скасувати") } }) }
}

@Composable private fun MonthPicker(month: YearMonth, change: (YearMonth) -> Unit) {
    var picker by remember { mutableStateOf(false) }
    var year by remember(month) { mutableIntStateOf(month.year) }
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        IconButton(onClick = { change(month.minusMonths(1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Попередній місяць") }
        TextButton(onClick = { year = month.year; picker = true }) {
            Text(month.format(DateTimeFormatter.ofPattern("LLLL yyyy", uk)).replaceFirstChar { it.titlecase(uk) }, fontWeight = FontWeight.Medium, fontSize = 19.sp, color = MaterialTheme.colorScheme.onSurface)
            Icon(Icons.Default.ArrowDropDown, null, tint = Muted)
        }
        IconButton(onClick = { change(month.plusMonths(1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Наступний місяць") }
    }
    if (picker) AlertDialog(onDismissRequest = { picker = false }, title = { Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { year-- }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Попередній рік") }
        Text("$year", Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        IconButton(onClick = { year++ }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Наступний рік") }
    } }, text = { Column { (1..12).chunked(3).forEach { row -> Row {
        row.forEach { m -> TextButton(onClick = { change(YearMonth.of(year, m)); picker = false }, modifier = Modifier.weight(1f)) { Text(YearMonth.of(year, m).format(DateTimeFormatter.ofPattern("LLL", uk))) } }
    } } } }, confirmButton = { TextButton(onClick = { change(YearMonth.now()); picker = false }) { Text("Поточний місяць") } })
}
@Composable private fun Empty(text: String) { Text(text, color = Muted, modifier = Modifier.padding(vertical = 28.dp), lineHeight = 24.sp) }
@Composable private fun Block(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) { Column(modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Panel).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content) }
@Composable private fun Total(label: String, value: Long, color: Color = Color.White) { Column { Text(label, color = Muted, fontSize = 12.sp); Text(money(value), color = color, fontWeight = FontWeight.Bold, fontSize = 18.sp) } }
@Composable private fun MonthlyTotals(entries: List<Entry>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Block(Modifier.weight(1f)) { Total("↓ Витрати", entries.filter { it.kind == "expense" }.sumOf { it.amount }, Red) }
        Block(Modifier.weight(1f)) { Total("↑ Доходи", entries.filter { it.kind == "income" }.sumOf { it.amount }, Green) }
    }
}
@Composable private fun AccountScreen(state: FinanceState, onEdit: (Account) -> Unit, onDelete: (Account) -> Unit, onAdd: () -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 100.dp)) {
        if (state.accounts.isEmpty()) item { Empty("Додайте перший рахунок — картку або готівку. Початковий баланс не враховується як дохід."); Button(onClick = onAdd) { Text("Створити рахунок") } }
        items(state.accounts, key = { it.id }) { a ->
            Block {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Color(a.color).copy(alpha = .18f)), contentAlignment = Alignment.Center) { Icon(if (a.cash) Icons.Default.Payments else Icons.Default.CreditCard, null, tint = Color(a.color)) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) { Text(a.name, fontWeight = FontWeight.Bold); Text(if (a.cash) "Готівка" else "Картка" + if (a.lastFour.isBlank()) "" else " •••• ${a.lastFour}", color = Muted, fontSize = 12.sp) }
                    IconButton(onClick = { onEdit(a) }) { Icon(Icons.Default.Edit, "Редагувати рахунок") }
                    IconButton(onClick = { onDelete(a) }) { Icon(Icons.Default.DeleteOutline, "Видалити рахунок", tint = Muted) }
                }
                Text(money(balance(a, state.entries)), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("Натисніть олівець, щоб скоригувати баланс", color = Muted, fontSize = 11.sp)
            }
        }
    }
}
@Composable private fun CategoryScreen(state: FinanceState, month: YearMonth, onOpen: (Category) -> Unit, onQuickAdd: (Category, String) -> Unit, onEdit: (Category) -> Unit, onAdd: () -> Unit) {
    val entries = state.entries.filter { it.inMonth(month) }
    var kind by rememberSaveable { mutableStateOf("expense") }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 100.dp)) {
        item {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Panel).padding(8.dp)) {
                listOf("expense", "income").forEach { type ->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if (kind == type) Bg else Color.Transparent).clickable { kind = type }.padding(vertical = 17.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (type == "expense") "Витрати" else "Доходи", color = if (kind == type) MaterialTheme.colorScheme.onSurface else Muted, fontSize = 14.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(compactMoney(entries.filter { it.kind == type }.sumOf { it.amount }), color = if (type == "expense") Red else Green, fontSize = 24.sp)
                    }
                }
            }
        }
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Категорії · торкніться, щоб додати", color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f)); IconButton(onClick = onAdd) { Icon(Icons.Default.Add, "Додати категорію", tint = Accent) } } }
        items(state.categories.chunked(2)) { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { c ->
                    var menu by remember { mutableStateOf(false) }
                    Surface(onClick = { onQuickAdd(c, kind) }, modifier = Modifier.weight(1f), color = Bg, border = BorderStroke(1.dp, Color(0xFF30353C)), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CategoryBadge(c, Modifier.size(40.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(c.name, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                                Text(compactMoney(entries.filter { it.kind == kind && it.categoryId == c.id }.sumOf { it.amount }), modifier = Modifier.weight(1f), fontSize = 16.sp, maxLines = 1)
                                Box {
                                    IconButton(onClick = { menu = true }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.MoreHoriz, "Дії: ${c.name}", Modifier.size(18.dp), tint = Muted) }
                                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                        DropdownMenuItem(text = { Text("Додати операцію") }, onClick = { menu = false; onQuickAdd(c, kind) })
                                        DropdownMenuItem(text = { Text("Історія категорії") }, onClick = { menu = false; onOpen(c) })
                                        DropdownMenuItem(text = { Text("Редагувати категорію") }, onClick = { menu = false; onEdit(c) })
                                    }
                                }
                            }
                        }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (entries.isEmpty()) item { Empty("Ще немає операцій. Оберіть категорію або натисніть «Додати».") }
        val uncategorized = entries.filter { it.kind == kind && it.categoryId == null }.sumOf { it.amount }
        if (uncategorized != 0L) item { Block { Total("Без категорії", uncategorized, if (kind == "expense") Red else Green) } }
    }
}
@Composable internal fun CategoryBadge(category: Category?, modifier: Modifier = Modifier) {
    val color = category?.let { Color(it.color) } ?: Accent
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = .13f)), contentAlignment = Alignment.Center) {
        Icon(category?.let { categoryIcon(it.icon) } ?: Icons.Default.SwapHoriz, null, tint = color, modifier = Modifier.size(26.dp))
    }
}

@Composable private fun <T> Choice(label: String, value: T, options: List<Pair<T, String>>, onChange: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Text("$label: ${options.firstOrNull { it.first == value }?.second ?: "Оберіть"}", Modifier.weight(1f), maxLines = 2); Icon(Icons.Default.ExpandMore, null) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 300.dp)) { options.forEach { (id, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { onChange(id); expanded = false }) } }
    }
}
@Composable private fun HistoryScreen(state: FinanceState, month: YearMonth, category: Long?, onClearCategory: () -> Unit, onEdit: (Entry) -> Unit, onDelete: (Entry) -> Unit) {
    var account by rememberSaveable { mutableStateOf<Long?>(null) }
    var kind by rememberSaveable { mutableStateOf("all") }
    var filters by rememberSaveable { mutableStateOf(false) }
    val list = state.entries.filter { it.inMonth(month) && (account == null || it.accountId == account || it.targetId == account) && (kind == "all" || it.kind == kind) && (category == null || it.categoryId == category) }
    val start = month.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val end = month.plusMonths(1).atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val accounts = state.accounts.filter { account == null || it.id == account }
    val opening = accounts.sumOf { balance(it, state.entries.filter { e -> e.time < start }) }
    val closing = accounts.sumOf { balance(it, state.entries.filter { e -> e.time < end }) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 110.dp)) {
        item {
            Surface(color = Bg, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Color(0xFF42474F))) {
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    listOf("Початковий баланс" to opening, "Кінцевий баланс" to closing).forEach { (label, value) ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) { Text(label, color = Muted, fontSize = 12.sp); Text(compactMoney(value), color = Green, fontSize = 19.sp, modifier = Modifier.padding(top = 4.dp)) }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (account == null) "Усі рахунки" else state.accounts.find { it.id == account }?.name.orEmpty(), color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { filters = !filters }) { Icon(Icons.Default.FilterList, null, Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text(if (filters) "Згорнути" else "Фільтри" + if (account != null || kind != "all") " •" else "", fontSize = 12.sp) }
            }
        }
        if (filters) {
            item { Choice("Рахунок", account, listOf(null to "Усі") + state.accounts.map { it.id to it.name }) { account = it } }
            item { Choice("Тип", kind, listOf("all" to "Усі", "expense" to "Витрати", "income" to "Доходи", "transfer" to "Перекази")) { kind = it } }
        }
        if (category != null) item { TextButton(onClick = onClearCategory) { Text("${state.categories.find { it.id == category }?.name ?: "Категорія"} × Скинути") } }
        if (list.isEmpty()) item { Empty("Операцій за цей місяць немає. Додайте першу або змініть фільтри.") }
        list.groupBy { it.date().toLocalDate() }.forEach { (date, entries) ->
            val dayNet = entries.sumOf { if (account != null) delta(it, account!!) else when (it.kind) { "income" -> it.amount; "expense" -> -it.amount; else -> 0L } }
            item {
                Column(Modifier.padding(top = 18.dp, bottom = 4.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(date.dayOfMonth.toString(), fontSize = 28.sp)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) { Text(date.format(DateTimeFormatter.ofPattern("EEEE", uk)), fontSize = 14.sp); Text(date.format(DateTimeFormatter.ofPattern("LLLL yyyy", uk)), fontSize = 13.sp, color = Muted) }
                        Text((if (dayNet > 0) "+" else "") + compactMoney(dayNet), color = if (dayNet < 0) Red else if (dayNet > 0) Green else Muted, fontSize = 18.sp)
                    }
                    HorizontalDivider(Modifier.padding(top = 14.dp), color = Color(0xFF353C44))
                }
            }
            items(entries, key = { it.id }) { e ->
                val c = state.categories.find { it.id == e.categoryId }
                var actions by remember { mutableStateOf(false) }
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onEdit(e) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    CategoryBadge(if (e.kind == "transfer") null else c, Modifier.size(52.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(if (e.kind == "transfer") "Переказ" else c?.name ?: kindName(e.kind), fontSize = 17.sp)
                        Row(Modifier.padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CreditCard, null, Modifier.size(19.dp), tint = Muted)
                            Text((state.accounts.find { it.id == e.accountId }?.name ?: "Рахунок") + if (e.targetId != null) " → ${state.accounts.find { it.id == e.targetId }?.name.orEmpty()}" else "", modifier = Modifier.padding(start = 5.dp), color = Muted, fontSize = 13.sp, maxLines = 2)
                        }
                        if (e.note.isNotBlank()) Text(e.note, color = Muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
                    }
                    Text(compactMoney(e.amount), color = when (e.kind) { "expense" -> Red; "income" -> Green; else -> Accent }, fontSize = 17.sp)
                    Box {
                        IconButton(onClick = { actions = true }, modifier = Modifier.width(32.dp)) { Icon(Icons.Default.MoreVert, "Дії з операцією", tint = Muted, modifier = Modifier.size(18.dp)) }
                        DropdownMenu(expanded = actions, onDismissRequest = { actions = false }) {
                            DropdownMenuItem(text = { Text("Редагувати") }, onClick = { actions = false; onEdit(e) })
                            DropdownMenuItem(text = { Text("Видалити", color = Red) }, onClick = { actions = false; onDelete(e) })
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun StatisticsScreen(state: FinanceState, month: YearMonth) {
    var account by rememberSaveable { mutableStateOf<Long?>(null) }
    val scoped = state.entries.filter { account == null || it.accountId == account || it.targetId == account }
    val entries = scoped.filter { it.inMonth(month) }
    val expenses = entries.filter { it.kind == "expense" }
    val total = expenses.sumOf { it.amount }
    val groups = expenses.groupBy { it.categoryId }.map { (id, es) -> Triple(state.categories.find { it.id == id }?.name ?: "Без категорії", es.sumOf { it.amount }, state.categories.find { it.id == id }?.let { Color(it.color) } ?: Muted) }.sortedByDescending { it.second }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 100.dp)) {
        item { Choice("Рахунок", account, listOf(null to "Усі") + state.accounts.map { it.id to it.name }) { account = it } }
        item { MonthlyTotals(entries) }
        item { Block { Total("Різниця доходів і витрат", entries.filter { it.kind == "income" }.sumOf { it.amount } - total, Accent); Text("Власні перекази не входять у статистику.", color = Muted, fontSize = 12.sp) } }
        item {
            Block {
                Text("Структура витрат", fontWeight = FontWeight.Bold)
                if (total == 0L) Empty("Ще немає витрат за цей місяць.")
                else {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.size(190.dp).padding(12.dp)) {
                            var start = -90f
                            groups.forEach { (_, sum, color) -> val sweep = (sum.toDouble() / total * 360).toFloat(); drawArc(color, start, sweep, false, style = Stroke(width = 27.dp.toPx(), cap = StrokeCap.Butt)); start += sweep }
                        }
                        Text("100%", fontWeight = FontWeight.Bold, fontSize = 24.sp)
                    }
                    groups.forEach { (name, value, color) -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(8.dp).clip(CircleShape).background(color)); Text(name, Modifier.weight(1f).padding(start = 8.dp), fontSize = 13.sp); Text("${money(value)} · ${value * 100 / total}%", fontSize = 12.sp, color = Muted) } }
                }
            }
        }
        item {
            val months = (5 downTo 0).map { month.minusMonths(it.toLong()) }
            val sums = months.map { m -> scoped.filter { it.kind == "expense" && it.inMonth(m) }.sumOf { it.amount } }
            val peak = max(1L, sums.maxOrNull() ?: 0L)
            Block {
                Text("Витрати за 6 місяців", fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth().height(145.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                    months.forEachIndexed { i, m -> Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                        Box(Modifier.width(22.dp).height((sums[i].toDouble() / peak * 105 + 2).dp).clip(RoundedCornerShape(6.dp)).background(if (i == 5) Accent else Color(0xFF4F476E)))
                        Text(m.format(DateTimeFormatter.ofPattern("LLL", uk)), fontSize = 10.sp, color = Muted)
                    } }
                }
                months.forEachIndexed { i, m -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(m.format(DateTimeFormatter.ofPattern("LLL yyyy", uk)), color = Muted, fontSize = 12.sp); Text(money(sums[i]), fontSize = 12.sp) } }
            }
        }
    }
}
@Composable private fun SettingsScreen() {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Block { Icon(Icons.Default.AccountBalanceWallet, null, tint = Accent); Text("Мої Фінанси", fontWeight = FontWeight.Bold, fontSize = 22.sp); Text("Версія 1.1 · Українська · UAH (грн)", color = Muted) } }
        item { Block { Text("Ваші дані — на вашому пристрої", fontWeight = FontWeight.Bold); Text("Застосунок працює повністю офлайн. Немає сервера, реклами, реєстрації чи банківських інтеграцій. Не вводьте повний номер картки, CVV або паролі.", color = Muted, lineHeight = 23.sp) } }
        item { Block { Text("Як рахується баланс", fontWeight = FontWeight.Bold); Text("Початковий залишок + доходи − витрати + вхідні перекази − вихідні перекази. Зміна або видалення операції одразу оновлює підсумки. Коригування балансу змінює початковий залишок, а не статистику доходів.", color = Muted, lineHeight = 23.sp) } }
        item { Block { Text("Локальне зберігання", fontWeight = FontWeight.Bold); Text("Room зберігає записи після закриття програми та перезапуску телефона. Видалення застосунку або очищення його даних видаляє всю історію. Автоматичного резервного копіювання немає.", color = Muted, lineHeight = 23.sp) } }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable private fun EditDialog(title: String, dismiss: () -> Unit, save: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = dismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = Panel) {
            Column(Modifier.fillMaxWidth().heightIn(max = 650.dp).imePadding().padding(20.dp)) {
                Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = dismiss) { Text("Скасувати") }
                    Button(onClick = save) { Text("Зберегти") }
                }
            }
        }
    }
}
@Composable internal fun Field(label: String, value: String, money: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp), keyboardOptions = KeyboardOptions(keyboardType = if (money) KeyboardType.Decimal else KeyboardType.Text))
}
@Composable private fun Colors(value: Long, select: (Long) -> Unit) {
    Text("Колір", color = Muted, fontSize = 12.sp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        palette.forEach { c -> Box(Modifier.size(30.dp).clip(CircleShape).background(Color(c)).clickable { select(c) }, contentAlignment = Alignment.Center) { if (value == c) Icon(Icons.Default.Check, "Обраний колір", Modifier.size(20.dp), tint = Bg) } }
    }
}
@Composable private fun AccountForm(original: Account?, state: FinanceState, dismiss: () -> Unit, save: (Account) -> Unit) {
    var name by remember { mutableStateOf(original?.name.orEmpty()) }
    var amount by remember { mutableStateOf(original?.let { editableMoney(balance(it, state.entries)) } ?: "0") }
    var color by remember { mutableLongStateOf(original?.color ?: palette[0]) }
    var cash by remember { mutableStateOf(original?.cash ?: false) }
    var digits by remember { mutableStateOf(original?.lastFour.orEmpty()) }
    var error by remember { mutableStateOf("") }
    EditDialog(if (original == null) "Новий рахунок" else "Редагувати рахунок", dismiss, {
        val value = parseMoney(amount)
        if (name.isBlank() || value == null || (!cash && digits.isNotBlank() && !Regex("\\d{4}").matches(digits))) error = "Вкажіть назву, коректну суму та рівно 4 цифри картки (або залиште поле порожнім)."
        else save(Account(original?.id ?: 0, name.trim(), value - (original?.let { state.entries.sumOf { e -> delta(e, it.id) } } ?: 0), color, cash, if (cash) "" else digits))
    }) {
        Field("Назва рахунку", name) { name = it.take(60) }
        Choice("Тип", cash, listOf(false to "Банківська картка", true to "Готівка")) { cash = it }
        Field(if (original == null) "Початковий баланс, грн" else "Баланс зараз, грн", amount, true) { amount = it }
        if (original != null) Text("Зміна балансу коригує початковий залишок. Історія операцій збережеться.", color = Muted, fontSize = 12.sp)
        if (!cash) OutlinedTextField(value = digits, onValueChange = { digits = it.filter(Char::isDigit).take(4) }, label = { Text("Останні 4 цифри (необов’язково)") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
        Colors(color) { color = it }
        if (error.isNotBlank()) Text(error, color = Red, fontSize = 12.sp)
    }
}
@Composable private fun CategoryForm(original: Category?, dismiss: () -> Unit, onDelete: (() -> Unit)?, save: (Category) -> Unit) {
    var name by remember { mutableStateOf(original?.name.orEmpty()) }
    var color by remember { mutableLongStateOf(original?.color ?: palette[0]) }
    var icon by remember { mutableIntStateOf(original?.icon ?: 0) }
    var error by remember { mutableStateOf(false) }
    EditDialog(if (original == null) "Нова категорія" else "Категорія", dismiss, { if (name.isBlank()) error = true else save(Category(original?.id ?: 0, name.trim(), color, icon)) }) {
        Field("Назва", name) { name = it.take(50) }
        Colors(color) { color = it }
        Text("Іконка", color = Muted, fontSize = 12.sp)
        (0..11).chunked(4).forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { row.forEach { i -> IconButton(onClick = { icon = i }, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (i == icon) Color(color).copy(alpha = .25f) else Color.Transparent)) { Icon(categoryIcon(i), defaultNames[i], tint = if (i == icon) Color(color) else Muted) } } } }
        if (error) Text("Введіть назву категорії", color = Red)
        if (onDelete != null) TextButton(onClick = onDelete) { Text("Видалити категорію", color = Red) }
    }
}
