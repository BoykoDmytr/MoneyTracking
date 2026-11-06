package ua.moneytracking

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable private fun <T> Choice(label: String, value: T, options: List<Pair<T, String>>, onChange: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Text("$label: ${options.firstOrNull { it.first == value }?.second ?: "Оберіть"}", Modifier.weight(1f), maxLines = 2); Icon(Icons.Default.ExpandMore, null) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 300.dp)) { options.forEach { (id, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { onChange(id); expanded = false }) } }
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
@Composable internal fun AccountForm(original: Account?, state: FinanceState, dismiss: () -> Unit, save: (Account) -> Unit) {
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
@Composable internal fun CategoryForm(original: Category?, dismiss: () -> Unit, onDelete: (() -> Unit)?, save: (Category) -> Unit) {
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
