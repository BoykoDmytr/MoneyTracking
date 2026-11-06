package ua.moneytracking

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.*
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EntrySheet(original: Entry?, state: FinanceState, initialCategory: Long?, initialKind: String, dismiss: () -> Unit, onDelete: (() -> Unit)?, save: (Entry) -> Unit) {
    var kind by rememberSaveable { mutableStateOf(original?.kind ?: initialKind) }
    var expression by rememberSaveable { mutableStateOf(original?.let { editableMoney(it.amount) }.orEmpty()) }
    var account by rememberSaveable { mutableLongStateOf(original?.accountId ?: state.accounts.first().id) }
    var target by rememberSaveable { mutableStateOf(original?.targetId ?: state.accounts.firstOrNull { it.id != account }?.id) }
    var category by rememberSaveable { mutableStateOf(if (original != null) original.categoryId else initialCategory ?: state.categories.firstOrNull()?.id) }
    var timestamp by rememberSaveable { mutableLongStateOf(original?.time ?: System.currentTimeMillis()) }
    val date = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDateTime()
    fun setDate(value: LocalDateTime) { timestamp = value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }
    var note by rememberSaveable { mutableStateOf(original?.note.orEmpty()) }
    var error by rememberSaveable { mutableStateOf("") }
    var accountPicker by remember { mutableStateOf(false) }
    var categoryPicker by remember { mutableStateOf(false) }
    var notePicker by remember { mutableStateOf(false) }
    var noteDraft by rememberSaveable { mutableStateOf(note) }
    val context = LocalContext.current
    val screenHeight = LocalConfiguration.current.screenHeightDp
    val keyHeight = ((screenHeight - 560) / 4).coerceIn(48, 78).dp
    val selectedAccount = state.accounts.find { it.id == account }
    val selectedCategory = state.categories.find { it.id == category }
    val transfer = kind == "transfer"
    fun timePicker() { TimePickerDialog(context, { _, h, m -> setDate(date.withHour(h).withMinute(m)) }, date.hour, date.minute, true).show() }
    fun submit() {
        val value = AmountInput.evaluate(expression)
        if (value == null || value <= 0) error = "Введіть суму більше нуля. Перевірте обчислення."
        else if (transfer && (target == null || target == account)) error = "Для переказу потрібні два різні рахунки."
        else save(Entry(original?.id ?: 0, kind, value, account, if (transfer) target else null, if (transfer) null else category, timestamp, note.trim()))
    }
    ModalBottomSheet(onDismissRequest = dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Panel, contentColor = MaterialTheme.colorScheme.onSurface, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(Modifier.fillMaxWidth().heightIn(max = (screenHeight * .92).dp).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                listOf("expense", "income", "transfer").forEach { type ->
                    val color = when (type) { "expense" -> Red; "income" -> Green; else -> Accent }
                    TextButton(onClick = { kind = type; error = "" }, modifier = Modifier.weight(1f), colors = ButtonDefaults.textButtonColors(contentColor = if (kind == type) color else Muted), contentPadding = PaddingValues(horizontal = 4.dp)) {
                        Text(kindName(type), fontWeight = if (kind == type) FontWeight.Bold else FontWeight.Normal, fontSize = 14.sp)
                    }
                }
                IconButton(onClick = dismiss, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.Close, "Закрити введення") }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { setDate(date.minusDays(1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Попередній день") }
                TextButton(onClick = { DatePickerDialog(context, { _, y, m, d -> setDate(LocalDate.of(y, m + 1, d).atTime(date.toLocalTime())) }, date.year, date.monthValue - 1, date.dayOfMonth).show() }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(0.dp)) {
                    Icon(Icons.Default.CalendarMonth, null, Modifier.size(20.dp), tint = Muted)
                    Spacer(Modifier.width(8.dp))
                    Text((if (date.toLocalDate() == LocalDate.now()) "Сьогодні, " else "") + date.format(DateTimeFormatter.ofPattern(if (date.year == LocalDate.now().year) "d MMMM" else "d MMMM yyyy", uk)), fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                }
                IconButton(onClick = { setDate(date.plusDays(1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Наступний день") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SelectionTile(if (transfer) "З рахунку" else "Рахунок", selectedAccount?.name ?: "Оберіть", if (selectedAccount?.cash == true) Icons.Default.Payments else Icons.Default.CreditCard, Color(0xFF5D93F2), Modifier.weight(1f)) { accountPicker = true }
                SelectionTile(if (transfer) "На рахунок" else "Категорія", if (transfer) state.accounts.find { it.id == target }?.name ?: "Оберіть" else selectedCategory?.name ?: "Без категорії", if (transfer) Icons.Default.CreditCard else selectedCategory?.let { categoryIcon(it.icon) } ?: Icons.Default.Category, if (transfer) Green else selectedCategory?.let { Color(it.color) } ?: Color(0xFFA577FF), Modifier.weight(1f)) { categoryPicker = true }
            }
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (expression.isEmpty()) "0 грн" else expression.replace('.', ',') + " грн", fontSize = if (expression.length > 14) 27.sp else if (expression.length > 9) 34.sp else 44.sp, fontWeight = FontWeight.Normal, textAlign = TextAlign.Center, modifier = Modifier.semantics { contentDescription = "Сума: ${expression.ifEmpty { "0" }} гривень" })
                if (expression.any { it in "+−×÷" }) Text(AmountInput.evaluate(expression)?.let { "= ${compactMoney(it)}" } ?: "Завершіть обчислення", fontSize = 13.sp, color = Muted)
                if (error.isNotBlank()) Text(error, fontSize = 12.sp, color = Red, textAlign = TextAlign.Center)
            }
            HorizontalDivider(color = Color(0xFF343B45))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { noteDraft = note; notePicker = true }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Notes, null, Modifier.size(21.dp), tint = if (note.isBlank()) Muted else Accent)
                    Text(if (note.isBlank()) "Коментар" else note, Modifier.padding(start = 6.dp), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Muted)
                }
                TextButton(onClick = { timePicker() }) { Icon(Icons.Default.Schedule, null, Modifier.size(19.dp), tint = Muted); Text(date.format(DateTimeFormatter.ofPattern("HH:mm")), Modifier.padding(start = 5.dp), fontSize = 12.sp, color = Muted) }
                if (onDelete != null) IconButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, "Видалити операцію", tint = Red) }
            }
            // Five columns, with one tall save key, keep the primary action within thumb reach.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(4f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(listOf("÷", "7", "8", "9"), listOf("×", "4", "5", "6"), listOf("−", "1", "2", "3"), listOf("+", "C", "0", ",")).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { row.forEach { key ->
                            KeypadKey(key, Modifier.weight(1f).height(keyHeight), description = when (key) { "C" -> "Очистити суму"; "÷" -> "Поділити"; "×" -> "Помножити"; "−" -> "Відняти"; "+" -> "Додати до суми"; "," -> "Десяткова кома"; else -> key }) {
                                expression = AmountInput.key(expression, key); error = ""
                            }
                        } }
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    KeypadKey("", Modifier.fillMaxWidth().height(keyHeight), Icons.Default.Backspace, "Стерти цифру") { expression = AmountInput.key(expression, "⌫"); error = "" }
                    KeypadKey("=", Modifier.fillMaxWidth().height(keyHeight), description = "Обчислити") {
                        val value = AmountInput.evaluate(expression)
                        if (value == null || value < 0) error = "Перевірте вираз. Сума не може бути від’ємною." else { expression = editableMoney(value); error = "" }
                    }
                    KeypadKey("", Modifier.fillMaxWidth().height(keyHeight * 2 + 8.dp), Icons.Default.Check, "Зберегти операцію", primary = true) { submit() }
                }
            }
        }
    }
    if (accountPicker) AccountPicker(state.accounts, "Оберіть рахунок", { accountPicker = false }) {
        account = it.id
        if (target == account) target = state.accounts.firstOrNull { a -> a.id != account }?.id
        accountPicker = false
    }
    if (categoryPicker) {
        if (transfer) AccountPicker(state.accounts.filter { it.id != account }, "Куди переказати", { categoryPicker = false }) { target = it.id; categoryPicker = false }
        else AlertDialog(onDismissRequest = { categoryPicker = false }, title = { Text("Оберіть категорію") }, text = {
            LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.categories, key = { it.id }) { c ->
                    Surface(onClick = { category = c.id; categoryPicker = false }, color = if (category == c.id) Color(c.color).copy(alpha = .15f) else Color.Transparent, shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.padding(vertical = 10.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) { CategoryBadge(c, Modifier.size(42.dp)); Text(c.name, fontSize = 11.sp, maxLines = 2, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 5.dp)) }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { category = null; categoryPicker = false }) { Text("Без категорії") } }, dismissButton = { TextButton(onClick = { categoryPicker = false }) { Text("Закрити") } })
    }
    if (notePicker) AlertDialog(onDismissRequest = { notePicker = false }, title = { Text("Коментар") }, text = { OutlinedTextField(value = noteDraft, onValueChange = { noteDraft = it.take(500) }, placeholder = { Text("Наприклад, продукти на вечерю") }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4) }, confirmButton = { TextButton(onClick = { note = noteDraft; notePicker = false }) { Text("Готово") } }, dismissButton = { TextButton(onClick = { notePicker = false }) { Text("Скасувати") } })
}

@Composable private fun SelectionTile(title: String, name: String, icon: ImageVector, color: Color, modifier: Modifier, click: () -> Unit) {
    Surface(onClick = click, modifier = modifier, color = color.copy(alpha = .08f), border = BorderStroke(1.dp, color.copy(alpha = .8f)), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Muted)
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = color, modifier = Modifier.size(25.dp)); Text(name, modifier = Modifier.padding(start = 8.dp).weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 19.sp) }
        }
    }
}
@Composable private fun KeypadKey(label: String, modifier: Modifier, icon: ImageVector? = null, description: String, primary: Boolean = false, click: () -> Unit) {
    Surface(onClick = click, modifier = modifier.semantics { contentDescription = description }, shape = RoundedCornerShape(18.dp), color = if (primary) Color(0xFF12577F) else Bg, contentColor = if (primary) Color(0xFFCEE9FF) else MaterialTheme.colorScheme.onSurface) {
        Box(contentAlignment = Alignment.Center) { if (icon == null) Text(label, fontSize = 29.sp) else Icon(icon, null, Modifier.size(28.dp)) }
    }
}
@Composable private fun AccountPicker(accounts: List<Account>, title: String, dismiss: () -> Unit, select: (Account) -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = {
        LazyColumn(Modifier.heightIn(max = 340.dp)) {
            if (accounts.isEmpty()) item { Text("Додайте ще один рахунок у вкладці «Рахунки», щоб переказувати між ними.", color = Muted) }
            items(accounts, key = { it.id }) { account ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { select(account) }.padding(vertical = 16.dp, horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (account.cash) Icons.Default.Payments else Icons.Default.CreditCard, null, tint = Color(account.color))
                    Text(account.name, Modifier.padding(start = 12.dp), fontSize = 17.sp)
                }
            }
        }
    }, confirmButton = { TextButton(onClick = dismiss) { Text("Закрити") } })
}
