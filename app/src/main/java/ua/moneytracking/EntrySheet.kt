package ua.moneytracking

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
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
internal fun EntrySheet(
    original: Entry?,
    state: FinanceState,
    initialCategory: Long?,
    initialKind: String,
    preferences: UserPreferences,
    dismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    save: (Entry) -> Unit
) {
    if (state.accounts.isEmpty()) return
    val defaultAccount = state.accounts.find { it.id == preferences.defaultAccountId } ?: state.accounts.first()
    var kind by rememberSaveable { mutableStateOf(original?.kind ?: initialKind.takeIf { it in listOf("expense", "income", "transfer") } ?: "expense") }
    var expression by rememberSaveable { mutableStateOf(original?.let { editableMoney(it.amount) }.orEmpty()) }
    var replaceAmount by rememberSaveable { mutableStateOf(original != null) }
    var account by rememberSaveable { mutableLongStateOf(original?.accountId ?: defaultAccount.id) }
    var target by rememberSaveable { mutableStateOf(original?.targetId ?: state.accounts.firstOrNull { it.id != account }?.id) }
    var category by rememberSaveable { mutableStateOf(if (original != null) original.categoryId else initialCategory ?: state.categories.firstOrNull()?.id) }
    var timestamp by rememberSaveable { mutableLongStateOf(original?.time ?: System.currentTimeMillis()) }
    var note by rememberSaveable { mutableStateOf(original?.note.orEmpty()) }
    var error by rememberSaveable { mutableStateOf("") }
    var details by rememberSaveable { mutableStateOf(false) }
    var calculator by rememberSaveable { mutableStateOf(false) }
    var accountPicker by remember { mutableStateOf(false) }
    var categoryPicker by remember { mutableStateOf(false) }
    var notePicker by remember { mutableStateOf(false) }
    var noteDraft by rememberSaveable { mutableStateOf(note) }
    val date = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDateTime()
    val selectedAccount = state.accounts.find { it.id == account }
    val selectedCategory = state.categories.find { it.id == category }
    val transfer = kind == "transfer"
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val screenHeight = LocalConfiguration.current.screenHeightDp
    val keyHeight = if (screenHeight < 720) 48.dp else if (screenHeight < 860) 56.dp else 64.dp
    val evaluated = AmountInput.evaluate(expression)
    val validTransfer = !transfer || (target != null && target != account)
    val canSave = evaluated != null && evaluated > 0 && validTransfer && selectedAccount != null
    val saveLabel = if (original != null) "Зберегти зміни" else when (kind) {
        "income" -> "Додати дохід"
        "transfer" -> "Переказати"
        else -> "Додати витрату"
    }
    fun setDate(value: LocalDateTime) {
        timestamp = value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
    fun press(key: String) {
        if (preferences.haptics) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        val base = if (replaceAmount && (key.firstOrNull()?.isDigit() == true || key == ",")) "" else expression
        replaceAmount = false
        expression = AmountInput.key(base, key)
        error = ""
    }
    fun submit() {
        val amount = AmountInput.evaluate(expression)
        if (amount == null || amount <= 0) error = "Введіть суму більше нуля."
        else if (!validTransfer) error = "Оберіть два різні рахунки для переказу."
        else if (selectedAccount != null) save(Entry(original?.id ?: 0, kind, amount, account, if (transfer) target else null, if (transfer) null else category, timestamp, note.trim()))
    }
    fun pickDate() {
        DatePickerDialog(context, { _, y, m, d -> setDate(LocalDate.of(y, m + 1, d).atTime(date.toLocalTime())) }, date.year, date.monthValue - 1, date.dayOfMonth).show()
    }
    fun pickTime() {
        TimePickerDialog(context, { _, h, m -> setDate(date.withHour(h).withMinute(m)) }, date.hour, date.minute, true).show()
    }
    ModalBottomSheet(
        onDismissRequest = dismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Panel,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = null
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = (screenHeight * .96).dp).padding(horizontal = 20.dp)) {
            Box(Modifier.fillMaxWidth().height(8.dp), contentAlignment = Alignment.Center) {
                Surface(color = Muted.copy(alpha = .4f), shape = RoundedCornerShape(4.dp), modifier = Modifier.width(32.dp).height(3.dp)) {}
            }
            Row(Modifier.fillMaxWidth().height(38.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (original == null) "Нова операція" else "Редагувати операцію", fontSize = 19.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton(onClick = dismiss, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.Close, "Закрити введення", tint = Muted, modifier = Modifier.size(20.dp)) }
            }
            // Only the body scrolls. The labelled save action stays visible on short screens.
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 2.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf("expense", "income", "transfer").forEach { type ->
                        val selected = kind == type
                        Surface(onClick = { kind = type; error = "" }, modifier = Modifier.weight(1f).height(38.dp), shape = RoundedCornerShape(12.dp), color = if (selected) Accent else Bg, contentColor = if (selected) Bg else Muted) {
                            Box(contentAlignment = Alignment.Center) { Text(kindName(type), fontSize = 13.sp, lineHeight = 18.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) }
                        }
                    }
                }
                Column(Modifier.fillMaxWidth().padding(vertical = 1.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Сума у гривнях", color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                        Text(expression.ifEmpty { "0" }.replace('.', ','), fontSize = if (expression.length > 14) 24.sp else if (expression.length > 9) 32.sp else 42.sp, lineHeight = if (expression.length > 14) 30.sp else if (expression.length > 9) 38.sp else 50.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.weight(1f, fill = false).semantics { contentDescription = "Сума: ${expression.ifEmpty { "0" }} гривень" })
                        Text("грн", color = Muted, fontSize = 18.sp, modifier = Modifier.padding(start = 8.dp))
                    }
                    if (expression.any { it in "+−×÷" }) Text(evaluated?.let { "= ${compactMoney(it)}" } ?: "Завершіть обчислення", fontSize = 12.sp, color = Muted)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EntrySelection(if (transfer) "З рахунку" else "Рахунок", selectedAccount?.name ?: "Оберіть", if (selectedAccount?.cash == true) Icons.Default.Payments else Icons.Default.CreditCard, Accent, Modifier.weight(1f)) { accountPicker = true }
                    EntrySelection(if (transfer) "На рахунок" else "Категорія", if (transfer) state.accounts.find { it.id == target }?.name ?: "Оберіть" else selectedCategory?.name ?: "Без категорії", if (transfer) Icons.Default.SwapHoriz else selectedCategory?.let { categoryIcon(it.icon) } ?: Icons.Default.Category, if (transfer) Accent else selectedCategory?.let { Color(it.color) } ?: Muted, Modifier.weight(1f)) { categoryPicker = true }
                }
                Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { details = !details }, contentPadding = PaddingValues(horizontal = 0.dp), modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.CalendarToday, null, Modifier.size(16.dp), tint = Muted)
                        Spacer(Modifier.width(6.dp))
                        Text(if (details) "Згорнути деталі" else (if (date.toLocalDate() == LocalDate.now()) "Сьогодні" else date.format(DateTimeFormatter.ofPattern("d MMM", uk))) + if (note.isNotBlank()) " · коментар" else " · деталі", fontSize = 12.sp, color = Muted, maxLines = 1)
                        Icon(if (details) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, Modifier.size(17.dp), tint = Muted)
                    }
                    IconButton(onClick = { calculator = !calculator }, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.Calculate, if (calculator) "Приховати калькулятор" else "Калькулятор", Modifier.size(20.dp), tint = if (calculator) Accent else Muted) }
                    TextButton(onClick = { press("C") }, contentPadding = PaddingValues(horizontal = 4.dp)) { Text("Очистити", fontSize = 12.sp, color = Muted) }
                }
                if (details) {
                    Surface(color = Bg, shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.fillMaxWidth().padding(10.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TextButton(onClick = { pickDate() }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Text(date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")), color = Accent, fontSize = 13.sp) }
                                TextButton(onClick = { pickTime() }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Icon(Icons.Default.Schedule, null, Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text(date.format(DateTimeFormatter.ofPattern("HH:mm")), fontSize = 13.sp) }
                            }
                            TextButton(onClick = { noteDraft = note; notePicker = true }, contentPadding = PaddingValues(4.dp), modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.AutoMirrored.Filled.Notes, null, Modifier.size(18.dp), tint = Muted)
                                Text(note.ifBlank { "Додати коментар" }, modifier = Modifier.weight(1f).padding(start = 8.dp), textAlign = TextAlign.Start, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, color = if (note.isBlank()) Muted else MaterialTheme.colorScheme.onSurface)
                            }
                            if (onDelete != null) TextButton(onClick = onDelete, contentPadding = PaddingValues(4.dp)) { Icon(Icons.Default.DeleteOutline, null, Modifier.size(18.dp), tint = Red); Text("Видалити операцію", Modifier.padding(start = 6.dp), color = Red, fontSize = 12.sp) }
                        }
                    }
                }
                if (!validTransfer) Text("Для переказу оберіть два різні рахунки.", color = Muted, fontSize = 12.sp)
                if (error.isNotBlank()) Text(error, fontSize = 12.sp, color = Red)
                if (calculator) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("+", "−", "×", "÷", "=").forEach { key ->
                        EntryKey(key, Modifier.weight(1f).height(44.dp), description = when (key) { "+" -> "Додати до суми"; "−" -> "Відняти"; "×" -> "Помножити"; "÷" -> "Поділити"; else -> "Обчислити" }, toned = true) {
                            if (key != "=") press(key) else {
                                val amount = AmountInput.evaluate(expression)
                                if (amount == null || amount < 0) error = "Перевірте обчислення." else { expression = editableMoney(amount); replaceAmount = false; error = "" }
                            }
                        }
                    }
                }
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf(",", "0", "⌫")).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { key -> EntryKey(if (key == "⌫") "" else key, Modifier.weight(1f).height(keyHeight), icon = if (key == "⌫") Icons.AutoMirrored.Filled.Backspace else null, description = when (key) { "⌫" -> "Стерти цифру"; "," -> "Десяткова кома"; else -> key }) { press(key) } }
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
            }
            Column(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp)) {
                Button(onClick = { submit() }, enabled = canSave, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Bg)) {
                    Icon(if (original == null) Icons.Default.Add else Icons.Default.Check, null, Modifier.size(19.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(saveLabel, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
    if (accountPicker) EntryAccountPicker(state, state.accounts, "Оберіть рахунок", account, { accountPicker = false }) {
        account = it.id
        if (target == account) target = state.accounts.firstOrNull { a -> a.id != account }?.id
        accountPicker = false
    }
    if (categoryPicker) {
        if (transfer) EntryAccountPicker(state, state.accounts.filter { it.id != account }, "Куди переказати", target, { categoryPicker = false }) { target = it.id; categoryPicker = false }
        else AlertDialog(onDismissRequest = { categoryPicker = false }, title = { Text("Категорія") }, text = {
            LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.heightIn(max = 340.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.categories, key = { it.id }) { c ->
                    Surface(onClick = { category = c.id; categoryPicker = false }, color = if (category == c.id) Color(c.color).copy(alpha = .16f) else Color.Transparent, shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.padding(vertical = 10.dp, horizontal = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            CategoryBadge(c, Modifier.size(40.dp))
                            Text(c.name, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 2, minLines = 2, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 5.dp))
                        }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { category = null; categoryPicker = false }) { Text("Без категорії") } }, dismissButton = { TextButton(onClick = { categoryPicker = false }) { Text("Закрити") } })
    }
    if (notePicker) AlertDialog(onDismissRequest = { notePicker = false }, title = { Text("Коментар") }, text = {
        OutlinedTextField(value = noteDraft, onValueChange = { noteDraft = it.take(500) }, placeholder = { Text("Наприклад, продукти на вечерю") }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4, shape = RoundedCornerShape(14.dp))
    }, confirmButton = { TextButton(onClick = { note = noteDraft; notePicker = false }) { Text("Готово") } }, dismissButton = { TextButton(onClick = { notePicker = false }) { Text("Скасувати") } })
}

@Composable private fun EntrySelection(label: String, value: String, icon: ImageVector, color: Color, modifier: Modifier, click: () -> Unit) {
    Surface(onClick = click, modifier = modifier.heightIn(min = 56.dp), shape = RoundedCornerShape(14.dp), color = Bg, border = BorderStroke(1.dp, Muted.copy(alpha = .12f))) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text(label, fontSize = 10.sp, lineHeight = 14.sp, color = Muted)
                Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
            }
            Icon(Icons.Default.ExpandMore, null, tint = Muted, modifier = Modifier.size(15.dp))
        }
    }
}
@Composable private fun EntryKey(label: String, modifier: Modifier, description: String, icon: ImageVector? = null, toned: Boolean = false, click: () -> Unit) {
    Surface(onClick = click, modifier = modifier.semantics { contentDescription = description }, shape = RoundedCornerShape(14.dp), color = if (toned) Accent.copy(alpha = .12f) else Bg, contentColor = if (toned) Accent else MaterialTheme.colorScheme.onSurface) {
        Box(contentAlignment = Alignment.Center) { if (icon == null) Text(label, fontSize = if (toned) 24.sp else 27.sp, lineHeight = 32.sp) else Icon(icon, null, Modifier.size(23.dp)) }
    }
}
@Composable private fun EntryAccountPicker(state: FinanceState, accounts: List<Account>, title: String, selected: Long?, dismiss: () -> Unit, select: (Account) -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = {
        LazyColumn(Modifier.heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (accounts.isEmpty()) item { Text("Для переказу потрібні щонайменше два рахунки.", color = Muted) }
            items(accounts, key = { it.id }) { account ->
                Surface(onClick = { select(account) }, color = if (account.id == selected) Accent.copy(alpha = .1f) else Color.Transparent, shape = RoundedCornerShape(14.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (account.cash) Icons.Default.Payments else Icons.Default.CreditCard, null, tint = Color(account.color))
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(account.name, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
                            Text(compactMoney(balance(account, state.entries)), color = Muted, fontSize = 12.sp, lineHeight = 16.sp)
                        }
                        if (account.id == selected) Icon(Icons.Default.Check, null, tint = Accent, modifier = Modifier.size(19.dp))
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = dismiss) { Text("Закрити") } })
}
