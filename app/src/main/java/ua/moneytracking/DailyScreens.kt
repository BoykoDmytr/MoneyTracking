package ua.moneytracking

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max

private val DailyBorder = Color(0xFF26363C)
private val DailyInk = Color(0xFF10291F)

/** Daily work deliberately contains no account/category configuration actions. */
@Composable
internal fun OverviewScreen(
    state: FinanceState,
    month: YearMonth,
    preferences: UserPreferences,
    onNewEntry: (String, Long?) -> Unit,
    onHistory: () -> Unit,
    onStart: () -> Unit
) {
    val monthEntries = state.entries.filter { it.inMonth(month) }
    val spending = monthEntries.filter { it.kind == "expense" }.sumOf { it.amount }
    val income = monthEntries.filter { it.kind == "income" }.sumOf { it.amount }
    val recent = monthEntries.sortedWith(compareByDescending<Entry> { it.time }.thenByDescending { it.id }).take(3)
    val categoryTotals = state.categories.associate { c ->
        c.id to monthEntries.filter { it.kind == "expense" && it.categoryId == c.id }.sumOf { it.amount }
    }
    val quickCategories = state.categories.sortedByDescending { categoryTotals[it.id] ?: 0L }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(22.dp),
        contentPadding = PaddingValues(bottom = 28.dp)
    ) {
        // Entry actions come first, within easy reach even on compact screens.
        if (state.accounts.isEmpty()) {
            item {
                DailySurface {
                    Icon(Icons.Default.WavingHand, null, tint = Accent, modifier = Modifier.size(32.dp))
                    Text("Почнімо з першого рахунку", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    Text("Додайте картку або готівку та вкажіть залишок. Після цього записуйте витрати за кілька дотиків.", color = Muted, lineHeight = 22.sp)
                    Button(onClick = onStart, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = DailyInk), shape = RoundedCornerShape(16.dp)) {
                        Text("Почати облік", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(19.dp))
                    }
                }
            }
        } else {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { onNewEntry("expense", null) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 62.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = DailyInk),
                        shape = RoundedCornerShape(19.dp)
                    ) {
                        Icon(Icons.Default.Add, null, Modifier.size(24.dp))
                        Text("Записати витрату", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 10.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DailySecondaryAction("Дохід", Icons.Default.SouthWest, Modifier.weight(1f)) { onNewEntry("income", null) }
                        DailySecondaryAction("Переказ", Icons.Default.SwapHoriz, Modifier.weight(1f)) { onNewEntry("transfer", null) }
                    }
                }
            }
        }
        item { DailyBalanceHero(state, preferences, spending, income) }
        if (state.accounts.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DailySectionHeader("Мої гроші")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(state.accounts, key = { it.id }) { account ->
                            Surface(color = Panel, shape = RoundedCornerShape(19.dp), border = BorderStroke(1.dp, DailyBorder)) {
                                Column(Modifier.widthIn(min = 160.dp, max = 240.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(if (account.cash) Icons.Default.Payments else Icons.Default.CreditCard, null, tint = Color(account.color), modifier = Modifier.size(20.dp))
                                        Text(account.name, Modifier.padding(start = 8.dp), color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Text(if (preferences.hideBalance) "•••• грн" else compactMoney(balance(account, state.entries)), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                                    if (!account.cash && account.lastFour.isNotBlank()) Text("•••• ${account.lastFour}", color = Muted, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
            if (quickCategories.isNotEmpty()) item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DailySectionHeader("Швидка витрата", subtitle = "Оберіть категорію й введіть суму")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(quickCategories, key = { it.id }) { category ->
                            Surface(onClick = { onNewEntry("expense", category.id) }, color = Panel, shape = RoundedCornerShape(18.dp)) {
                                Column(Modifier.width(108.dp).padding(horizontal = 10.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                    CategoryBadge(category, Modifier.size(40.dp))
                                    Text(category.name, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(compactMoney(categoryTotals[category.id] ?: 0L), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Muted, textAlign = TextAlign.Center, maxLines = 2)
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DailySectionHeader("Останні операції", actionText = "Усі", action = onHistory)
                if (recent.isEmpty()) DailyEmpty("Історія починається тут", "Ваші операції за вибраний місяць з’являться після першого запису.", Icons.AutoMirrored.Filled.ReceiptLong)
                else DailySurface(padding = 4) {
                    recent.forEachIndexed { index, entry ->
                        DailyEntryRow(entry, state, onClick = onHistory)
                        if (index != recent.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 14.dp), color = DailyBorder)
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyBalanceHero(state: FinanceState, preferences: UserPreferences, spending: Long, income: Long) {
    Surface(shape = RoundedCornerShape(28.dp), color = Color.Transparent) {
        Column(
            Modifier.fillMaxWidth()
                .background(Brush.linearGradient(listOf(Color(0xFF1E3E34), Color(0xFF172D2B))))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AccountBalanceWallet, null, tint = Accent, modifier = Modifier.size(20.dp))
                Text("Усього на рахунках", Modifier.weight(1f).padding(start = 9.dp), color = Color(0xFFC2DCD0), fontSize = 14.sp)
                Surface(color = Accent.copy(alpha = .12f), shape = CircleShape) {
                    Text("UAH", Modifier.padding(horizontal = 10.dp, vertical = 5.dp), color = Accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            val total = state.accounts.sumOf { balance(it, state.entries) }
            Text(
                if (preferences.hideBalance) "•••• грн" else compactMoney(total),
                color = Color(0xFFF1FFF6), fontWeight = FontWeight.SemiBold,
                fontSize = if (compactMoney(total).length > 18) 27.sp else 36.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            HorizontalDivider(color = Accent.copy(alpha = .15f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                DailyMetric("Витрачено за місяць", spending, Red, Modifier.weight(1f))
                DailyMetric("Отримано за місяць", income, Green, Modifier.weight(1f))
            }
        }
    }
}

@Composable
internal fun TransactionsScreen(
    state: FinanceState,
    month: YearMonth,
    onEdit: (Entry) -> Unit,
    onDelete: (Entry) -> Unit
) {
    var accountId by rememberSaveable { mutableStateOf<Long?>(null) }
    var categoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var kind by rememberSaveable { mutableStateOf("all") }
    var search by rememberSaveable { mutableStateOf("") }
    var expandedFilters by rememberSaveable { mutableStateOf(false) }
    val activeAccount = accountId?.takeIf { id -> state.accounts.any { it.id == id } }
    val activeCategory = categoryId?.takeIf { id -> state.categories.any { it.id == id } }
    val accountNames = state.accounts.associate { it.id to it.name }
    val categoryNames = state.categories.associate { it.id to it.name }
    val entries = state.entries.filter { e ->
        e.inMonth(month) &&
            (activeAccount == null || e.accountId == activeAccount || e.targetId == activeAccount) &&
            (kind == "all" || e.kind == kind) &&
            (activeCategory == null || e.categoryId == activeCategory) &&
            (search.isBlank() || listOf(e.note, accountNames[e.accountId].orEmpty(), accountNames[e.targetId].orEmpty(), categoryNames[e.categoryId].orEmpty(), compactMoney(e.amount), kindName(e.kind)).any { it.contains(search.trim(), ignoreCase = true) })
    }.sortedWith(compareByDescending<Entry> { it.time }.thenByDescending { it.id })
    val monthStart = month.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val monthEnd = month.plusMonths(1).atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val accounts = state.accounts.filter { activeAccount == null || it.id == activeAccount }
    val opening = accounts.sumOf { balance(it, state.entries.filter { e -> e.time < monthStart }) }
    val closing = accounts.sumOf { balance(it, state.entries.filter { e -> e.time < monthEnd }) }
    val hasFilters = activeAccount != null || activeCategory != null || kind != "all" || search.isNotBlank()
    LazyColumn(verticalArrangement = Arrangement.spacedBy(15.dp), contentPadding = PaddingValues(bottom = 28.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                DailySelect("Рахунок", activeAccount, listOf(null to "Усі рахунки") + state.accounts.map { it.id to it.name }, Modifier.weight(1f)) { accountId = it }
                Surface(onClick = { expandedFilters = !expandedFilters }, color = if (hasFilters || expandedFilters) Accent.copy(alpha = .12f) else Panel, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, if (hasFilters || expandedFilters) Accent.copy(alpha = .3f) else DailyBorder)) {
                    Row(Modifier.heightIn(min = 52.dp).padding(horizontal = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Tune, "Фільтри історії", Modifier.size(20.dp), tint = if (hasFilters) Accent else Muted)
                        Text(if (hasFilters) "Активні" else "Фільтри", Modifier.padding(start = 7.dp), fontSize = 13.sp, color = if (hasFilters) Accent else Muted)
                    }
                }
            }
        }
        if (expandedFilters) {
            item {
                OutlinedTextField(value = search, onValueChange = { search = it.take(100) }, placeholder = { Text("Коментар, категорія або сума") }, leadingIcon = { Icon(Icons.Default.Search, null) }, trailingIcon = if (search.isNotBlank()) ({ IconButton(onClick = { search = "" }) { Icon(Icons.Default.Close, "Очистити пошук") } }) else null, singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DailySelect("Тип", kind, listOf("all" to "Усі типи", "expense" to "Витрати", "income" to "Доходи", "transfer" to "Перекази"), Modifier.weight(1f)) { kind = it }
                    DailySelect("Категорія", activeCategory, listOf(null to "Усі категорії") + state.categories.map { it.id to it.name }, Modifier.weight(1f)) { categoryId = it }
                }
            }
            if (hasFilters) item {
                TextButton(onClick = { accountId = null; categoryId = null; kind = "all"; search = "" }) { Icon(Icons.Default.Close, null, Modifier.size(17.dp)); Text("Скинути фільтри", Modifier.padding(start = 6.dp)) }
            }
        }
        item {
            DailySurface {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DailyMetric("На початок місяця", opening, MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                    DailyMetric("На кінець місяця", closing, Accent, Modifier.weight(1f))
                }
                if (hasFilters && (activeCategory != null || kind != "all" || search.isNotBlank())) Text("Залишки показано за всіма операціями вибраних рахунків.", fontSize = 11.sp, lineHeight = 16.sp, color = Muted)
            }
        }
        if (entries.isEmpty()) item {
            DailyEmpty(if (hasFilters) "Нічого не знайшлося" else "Поки без операцій", if (hasFilters) "Змініть фільтри або пошуковий запит." else "Запишіть першу витрату чи дохід на вкладці «Огляд».", Icons.AutoMirrored.Filled.ReceiptLong)
        }
        entries.groupBy { it.date().toLocalDate() }.forEach { (date, dayEntries) ->
            item(key = "day-$date") {
                val net = dayEntries.sumOf { entry ->
                    if (activeAccount != null) delta(entry, activeAccount)
                    else when (entry.kind) { "expense" -> -entry.amount; "income" -> entry.amount; else -> 0L }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(date.format(DateTimeFormatter.ofPattern("d MMMM", uk)), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Text(date.format(DateTimeFormatter.ofPattern("EEEE", uk)), fontSize = 12.sp, color = Muted)
                    }
                    Text((if (net > 0) "+" else "") + compactMoney(net), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (net > 0) Green else if (net < 0) Red else Muted)
                }
            }
            items(dayEntries, key = { it.id }) { entry ->
                Surface(color = Panel, shape = RoundedCornerShape(18.dp)) {
                    DailyEntryRow(entry, state, onClick = { onEdit(entry) }, onDelete = { onDelete(entry) })
                }
            }
        }
    }
}

@Composable
internal fun InsightsScreen(state: FinanceState, month: YearMonth) {
    var accountId by rememberSaveable { mutableStateOf<Long?>(null) }
    val activeAccount = accountId?.takeIf { id -> state.accounts.any { it.id == id } }
    val scoped = state.entries.filter { activeAccount == null || it.accountId == activeAccount || it.targetId == activeAccount }
    val selected = scoped.filter { it.inMonth(month) }
    val spending = selected.filter { it.kind == "expense" }.sumOf { it.amount }
    val income = selected.filter { it.kind == "income" }.sumOf { it.amount }
    val difference = income - spending
    val groups = selected.filter { it.kind == "expense" }.groupBy { it.categoryId }.map { (id, entries) ->
        val category = state.categories.find { it.id == id }
        DailySpendingGroup(category?.name ?: "Без категорії", entries.sumOf { it.amount }, category?.let { Color(it.color) } ?: Muted)
    }.sortedByDescending { it.amount }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(22.dp), contentPadding = PaddingValues(bottom = 28.dp)) {
        item { DailySelect("Рахунок", activeAccount, listOf(null to "Усі рахунки") + state.accounts.map { it.id to it.name }) { accountId = it } }
        item {
            DailySurface {
                Text("Результат місяця", color = Muted, fontSize = 14.sp)
                Text((if (difference > 0) "+" else "") + compactMoney(difference), fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = if (difference < 0) Red else Green)
                HorizontalDivider(color = DailyBorder)
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    DailyMetric("Доходи", income, Green, Modifier.weight(1f))
                    DailyMetric("Витрати", spending, Red, Modifier.weight(1f))
                }
                Text("Перекази між власними рахунками не враховуються.", color = Muted, fontSize = 11.sp, lineHeight = 17.sp)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DailySectionHeader("Куди йдуть гроші")
                DailySurface {
                    if (groups.isEmpty()) DailyEmpty("Ще немає витрат", "Діаграма з’явиться після першої витрати за цей місяць.", Icons.Default.PieChartOutline)
                    else {
                        Box(Modifier.fillMaxWidth().height(206.dp), contentAlignment = Alignment.Center) {
                            Canvas(Modifier.size(174.dp).padding(13.dp)) {
                                var start = -90f
                                groups.forEach { group ->
                                    val sweep = (group.amount.toDouble() / spending * 360).toFloat()
                                    drawArc(group.color, start, sweep, false, style = Stroke(width = 24.dp.toPx(), cap = StrokeCap.Butt))
                                    start += sweep
                                }
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Усього витрат", color = Muted, fontSize = 11.sp)
                                Text(compactMoney(spending), fontWeight = FontWeight.SemiBold, fontSize = if (compactMoney(spending).length > 13) 13.sp else 17.sp, modifier = Modifier.widthIn(max = 132.dp), textAlign = TextAlign.Center)
                            }
                        }
                        groups.forEach { group ->
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(8.dp).clip(CircleShape).background(group.color))
                                    Text(group.name, Modifier.weight(1f).padding(start = 9.dp), fontSize = 13.sp, maxLines = 2)
                                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 8.dp)) {
                                        Text(compactMoney(group.amount), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                        Text("${(group.amount.toDouble() / spending * 100).toInt()}%", color = Muted, fontSize = 11.sp)
                                    }
                                }
                                LinearProgressIndicator(progress = { (group.amount.toDouble() / spending).toFloat() }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape), color = group.color, trackColor = DailyBorder, drawStopIndicator = {})
                            }
                        }
                    }
                }
            }
        }
        item {
            val months = (5 downTo 0).map { month.minusMonths(it.toLong()) }
            val amounts = months.map { m -> scoped.filter { it.kind == "expense" && it.inMonth(m) }.sumOf { it.amount } }
            val peak = max(1L, amounts.maxOrNull() ?: 0L)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DailySectionHeader("Витрати в динаміці", subtitle = "Останні 6 місяців")
                DailySurface {
                    Row(Modifier.fillMaxWidth().height(156.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                        months.forEachIndexed { index, m ->
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                                Box(Modifier.fillMaxWidth(.65f).height((amounts[index].toDouble() / peak * 110 + 3).dp).clip(RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp)).background(if (index == months.lastIndex) Accent else Color(0xFF355248)))
                                Text(m.format(DateTimeFormatter.ofPattern("LLL", uk)), color = if (index == months.lastIndex) Accent else Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 9.dp))
                            }
                        }
                    }
                    HorizontalDivider(color = DailyBorder)
                    months.forEachIndexed { index, m ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(m.format(DateTimeFormatter.ofPattern("LLLL yyyy", uk)), color = Muted, fontSize = 12.sp)
                            Text(compactMoney(amounts[index]), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

private data class DailySpendingGroup(val name: String, val amount: Long, val color: Color)

@Composable
private fun DailySurface(padding: Int = 18, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(23.dp), color = Panel) {
        Column(Modifier.fillMaxWidth().padding(padding.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

@Composable
private fun DailyMetric(label: String, value: Long, color: Color, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, fontSize = 11.sp, lineHeight = 16.sp, color = Muted)
        Text(compactMoney(value), fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = color, maxLines = 2)
    }
}

@Composable
private fun DailySectionHeader(title: String, subtitle: String? = null, actionText: String? = null, action: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) Text(subtitle, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
        if (actionText != null && action != null) TextButton(onClick = action) {
            Text(actionText, fontSize = 13.sp)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(18.dp))
        }
    }
}

@Composable
private fun DailySecondaryAction(label: String, icon: ImageVector, modifier: Modifier, action: () -> Unit) {
    Surface(onClick = action, modifier = modifier, color = Panel, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, DailyBorder)) {
        Row(Modifier.heightIn(min = 52.dp).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(20.dp), tint = Accent)
            Text(label, Modifier.padding(start = 9.dp), fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun DailyEntryRow(entry: Entry, state: FinanceState, onClick: () -> Unit, onDelete: (() -> Unit)? = null) {
    val category = state.categories.find { it.id == entry.categoryId }
    val account = state.accounts.find { it.id == entry.accountId }
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(start = 14.dp, end = if (onDelete == null) 14.dp else 2.dp, top = 15.dp, bottom = 15.dp), verticalAlignment = Alignment.CenterVertically) {
        CategoryBadge(if (entry.kind == "transfer") null else category, Modifier.size(43.dp))
        Column(Modifier.weight(1f).padding(horizontal = 11.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(if (entry.kind == "transfer") "Переказ" else category?.name ?: kindName(entry.kind), fontWeight = FontWeight.Medium, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text((account?.name ?: "Рахунок") + if (entry.targetId != null) " → ${state.accounts.find { it.id == entry.targetId }?.name.orEmpty()}" else "", color = Muted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (entry.note.isNotBlank()) Text(entry.note, color = Muted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(max = 135.dp)) {
            Text((if (entry.kind == "expense") "−" else if (entry.kind == "income") "+" else "") + compactMoney(entry.amount), color = when (entry.kind) { "expense" -> Red; "income" -> Green; else -> Accent }, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, textAlign = TextAlign.End)
            Text(entry.date().format(DateTimeFormatter.ofPattern("HH:mm")), color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 5.dp))
        }
        if (onDelete != null) Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.width(34.dp)) { Icon(Icons.Default.MoreVert, "Дії з операцією", Modifier.size(18.dp), tint = Muted) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Редагувати") }, onClick = { menu = false; onClick() })
                DropdownMenuItem(text = { Text("Видалити", color = Red) }, onClick = { menu = false; onDelete() })
            }
        }
    }
}

@Composable
private fun DailyEmpty(title: String, message: String, icon: ImageVector) {
    Column(Modifier.fillMaxWidth().padding(vertical = 22.dp, horizontal = 15.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(54.dp).clip(CircleShape).background(Accent.copy(alpha = .08f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Accent.copy(alpha = .8f), modifier = Modifier.size(26.dp)) }
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
        Text(message, color = Muted, lineHeight = 20.sp, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun <T> DailySelect(label: String, value: T, options: List<Pair<T, String>>, modifier: Modifier = Modifier, change: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        Surface(onClick = { expanded = true }, color = Panel, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, DailyBorder), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.heightIn(min = 52.dp).padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(label, color = Muted, fontSize = 10.sp)
                    Text(options.firstOrNull { it.first == value }?.second ?: "Оберіть", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.Default.ExpandMore, null, Modifier.size(18.dp), tint = Muted)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 320.dp)) {
            options.forEach { (id, title) -> DropdownMenuItem(text = { Text(title) }, onClick = { change(id); expanded = false }) }
        }
    }
}
