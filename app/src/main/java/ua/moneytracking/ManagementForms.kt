package ua.moneytracking

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import java.time.*
import java.time.format.DateTimeFormatter
import java.math.BigDecimal
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable private fun <T> Choice(label: String, value: T, options: List<Pair<T, String>>, onChange: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Text("$label: ${options.firstOrNull { it.first == value }?.second ?: "Оберіть"}", Modifier.weight(1f), maxLines = 2); Icon(Icons.Default.ExpandMore, null) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 300.dp)) { options.forEach { (id, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { onChange(id); expanded = false }) } }
    }
}
/** A full-window host avoids wrap-content window/field remeasurement feedback. */
@Composable internal fun FormDialog(
    title: String,
    dismiss: () -> Unit,
    save: () -> Unit,
    saveLabel: String = "Зберегти",
    maxPanelHeight: androidx.compose.ui.unit.Dp = 650.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BoxWithConstraints(
            Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.matchParentSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = dismiss))
            Surface(
                modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().height(maxHeight.coerceAtMost(maxPanelHeight)),
                shape = RoundedCornerShape(24.dp), color = Panel
            ) {
                Column(Modifier.fillMaxSize().padding(20.dp)) {
                    Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = dismiss) { Text("Скасувати") }
                        Button(onClick = save) { Text(saveLabel) }
                    }
                }
            }
        }
    }
}
@Composable internal fun Field(label: String, value: String, money: Boolean = false, onChange: (String) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontSize = 12.sp, lineHeight = 16.sp, color = Muted)
        OutlinedTextField(
            value = value, onValueChange = onChange,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            singleLine = true, shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(keyboardType = if (money) KeyboardType.Decimal else KeyboardType.Text)
        )
    }
}
@Composable private fun Colors(value: Long, select: (Long) -> Unit) {
    Text("Колір", color = Muted, fontSize = 12.sp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        palette.forEach { c -> Box(Modifier.size(30.dp).clip(CircleShape).background(Color(c)).clickable { select(c) }, contentAlignment = Alignment.Center) { if (value == c) Icon(Icons.Default.Check, "Обраний колір", Modifier.size(20.dp), tint = Bg) } }
    }
}
@Composable internal fun AccountForm(original: Account?, state: FinanceState, dismiss: () -> Unit, save: (Account, Long?) -> Unit) {
    val initialBalance = remember(original?.id) { original?.let { balance(it, state.entries) } ?: 0L }
    var name by remember { mutableStateOf(original?.name.orEmpty()) }
    var amount by remember { mutableStateOf(if (original == null) "0" else editableMoney(initialBalance)) }
    var color by remember { mutableLongStateOf(original?.color ?: palette[0]) }
    var cash by remember { mutableStateOf(original?.cash ?: false) }
    var currency by remember { mutableStateOf(original?.currency ?: "UAH") }
    var digits by remember { mutableStateOf(original?.lastFour.orEmpty()) }
    var error by remember { mutableStateOf("") }
    FormDialog(if (original == null) "Новий рахунок" else "Редагувати рахунок", dismiss, {
        val value = parseMoney(amount)
        if (name.isBlank() || value == null || (!cash && digits.isNotBlank() && !Regex("\\d{4}").matches(digits))) error = "Вкажіть назву, коректну суму та рівно 4 цифри картки (або залиште поле порожнім)."
        else save(
            Account(original?.id ?: 0, name.trim(), original?.opening ?: value, color, cash, if (cash) "" else digits, currency),
            if (original != null && value != initialBalance) value else null
        )
    }) {
        Field("Назва рахунку", name) { name = it.take(60) }
        Choice("Тип", cash, listOf(false to "Банківська картка", true to "Готівка")) { cash = it }
        if (original == null) Choice("Валюта", currency, supportedCurrencies.map { it to currencyName(it) }) { currency = it }
        else Text("${currencyName(currency)} · Для іншої валюти створіть новий рахунок.", color = Muted, fontSize = 12.sp)
        val balanceLabel = (if (original == null) "Початковий баланс, " else "Баланс зараз, ") + currencyUnit(currency)
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(balanceLabel, color = Muted, fontSize = 12.sp, lineHeight = 16.sp)
            OutlinedTextField(
                value = amount, onValueChange = { amount = it },
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = balanceLabel }, singleLine = true, shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                trailingIcon = {
                    TextButton(onClick = {
                        val trimmed = amount.trimStart()
                        amount = if (trimmed.startsWith("-")) trimmed.removePrefix("-") else "-" + trimmed.ifBlank { "0" }
                    }, modifier = Modifier.semantics { contentDescription = "Змінити знак балансу" }) { Text("±") }
                }
            )
        }
        if (original != null) Text("Зміна суми створить у історії операцію «Корекція». Доходи й витрати за місяць не зміняться.", color = Muted, fontSize = 12.sp)
        if (!cash) Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Останні 4 цифри (необов’язково)", color = Muted, fontSize = 12.sp, lineHeight = 16.sp)
            OutlinedTextField(value = digits, onValueChange = { digits = it.filter(Char::isDigit).take(4) }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Останні 4 цифри (необов’язково)" }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
        }
        Colors(color) { color = it }
        if (error.isNotBlank()) Text(error, color = Red, fontSize = 12.sp)
    }
}
@Composable internal fun CategoryForm(original: Category?, dismiss: () -> Unit, onDelete: (() -> Unit)?, save: (Category) -> Unit) {
    var name by remember { mutableStateOf(original?.name.orEmpty()) }
    var color by remember { mutableLongStateOf(original?.color ?: palette[0]) }
    var icon by remember { mutableIntStateOf(original?.icon ?: 0) }
    var error by remember { mutableStateOf(false) }
    FormDialog(if (original == null) "Нова категорія" else "Категорія", dismiss, { if (name.isBlank()) error = true else save(Category(original?.id ?: 0, name.trim(), color, icon)) }) {
        Field("Назва", name) { name = it.take(50) }
        Colors(color) { color = it }
        Text("Іконка", color = Muted, fontSize = 12.sp)
        (0..11).chunked(4).forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { row.forEach { i -> IconButton(onClick = { icon = i }, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (i == icon) Color(color).copy(alpha = .25f) else Color.Transparent)) { Icon(categoryIcon(i), defaultNames[i], tint = if (i == icon) Color(color) else Muted) } } } }
        if (error) Text("Введіть назву категорії", color = Red)
        if (onDelete != null) TextButton(onClick = onDelete) { Text("Видалити категорію", color = Red) }
    }
}


/** Corrections stay signed adjustments and cannot turn into a bank income or expense. */
@Composable internal fun CorrectionForm(original: Entry, state: FinanceState, dismiss: () -> Unit, save: (Entry) -> Unit) {
    val originalMagnitude = remember(original.id) { BigDecimal.valueOf(original.amount, 2).abs().toPlainString() }
    var amount by remember(original.id) { mutableStateOf(originalMagnitude) }
    var increase by remember(original.id) { mutableStateOf(original.amount > 0L) }
    var note by remember(original.id) { mutableStateOf(original.note) }
    var timestamp by remember(original.id) { mutableLongStateOf(original.time) }
    var error by remember { mutableStateOf("") }
    val context = LocalContext.current
    val date = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDateTime()
    fun updateDate(value: LocalDateTime) { timestamp = value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }
    FormDialog("Корекція балансу", dismiss, {
        val signedAmount = if (amount == originalMagnitude) {
            if (increase == (original.amount > 0L)) original.amount
            else try { Math.negateExact(original.amount) } catch (_: ArithmeticException) { null }
        } else parseMoney(amount)?.takeIf { it > 0L }?.let { if (increase) it else -it }
        if (signedAmount == null || signedAmount == 0L) error = "Введіть додатну суму корекції в допустимих межах."
        else save(original.copy(amount = signedAmount, note = note.trim(), time = timestamp))
    }) {
        Text(state.accounts.find { it.id == original.accountId }?.name ?: "Рахунок", fontWeight = FontWeight.SemiBold)
        Text("Корекція змінює залишок на рахунку, але не входить у доходи чи витрати.", color = Muted, fontSize = 13.sp)
        Choice("Напрям", increase, listOf(true to "Збільшити баланс", false to "Зменшити баланс")) { increase = it }
        Field("Сума корекції, ${currencyUnit(state.accounts.find { it.id == original.accountId }?.currency ?: "UAH")}", amount, true) { amount = it }
        OutlinedButton(onClick = { DatePickerDialog(context, { _, y, m, d -> updateDate(LocalDate.of(y, m + 1, d).atTime(date.toLocalTime())) }, date.year, date.monthValue - 1, date.dayOfMonth).show() }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.CalendarMonth, null); Spacer(Modifier.width(8.dp)); Text(date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")))
        }
        OutlinedButton(onClick = { TimePickerDialog(context, { _, h, m -> updateDate(date.withHour(h).withMinute(m)) }, date.hour, date.minute, true).show() }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Schedule, null); Spacer(Modifier.width(8.dp)); Text(date.format(DateTimeFormatter.ofPattern("HH:mm")))
        }
        Field("Коментар", note) { note = it.take(500) }
        if (error.isNotBlank()) Text(error, color = Red)
    }
}
