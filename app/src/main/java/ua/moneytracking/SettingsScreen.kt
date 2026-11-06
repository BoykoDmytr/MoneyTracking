package ua.moneytracking

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun SettingsScreen(
    state: FinanceState,
    preferences: UserPreferences,
    onPreferencesChange: (UserPreferences) -> Unit,
    onAddAccount: () -> Unit,
    onEditAccount: (Account) -> Unit,
    onDeleteAccount: (Account) -> Unit,
    onAddCategory: () -> Unit,
    onEditCategory: (Category) -> Unit,
    onDeleteCategory: (Category) -> Unit
) {
    var section by rememberSaveable { mutableStateOf("general") }
    BackHandler(enabled = section != "general") { section = "general" }

    AnimatedContent(
        targetState = section,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            val direction = if (targetState == "general") -1 else 1
            (fadeIn(tween(160)) + slideInHorizontally(tween(180)) { direction * it / 16 }) togetherWith fadeOut(tween(80))
        },
        label = "settingsSection"
    ) { selectedSection ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when (selectedSection) {
                "accounts" -> {
                    item {
                        ManagementHeading("Рахунки", { section = "general" }, onAddAccount)
                    }
                    item {
                        Text("Створюйте рахунки та змінюйте їхній баланс тут. Щоденний облік залишається на вкладці «Огляд».", color = Muted, fontSize = 13.sp, lineHeight = 20.sp)
                    }
                    if (state.accounts.isEmpty()) item {
                        SettingsCard {
                            Icon(Icons.Default.AccountBalanceWallet, null, tint = Accent, modifier = Modifier.size(30.dp))
                            Text("Почніть із першого рахунку", fontWeight = FontWeight.SemiBold)
                            Text("Картка або готівка — достатньо назви та початкового залишку.", color = Muted, fontSize = 13.sp)
                            Button(onClick = onAddAccount) { Text("Додати рахунок") }
                        }
                    }
                    items(state.accounts, key = { it.id }) { account ->
                        SettingsCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SettingIcon(if (account.cash) Icons.Default.Payments else Icons.Default.CreditCard, Color(account.color))
                                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                    Text(account.name, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                                    Text(if (account.cash) "Готівка" else "Банківська картка" + if (account.lastFour.isBlank()) "" else " · ${account.lastFour}", color = Muted, fontSize = 12.sp)
                                }
                            }
                            Text(money(balance(account, state.entries)), fontSize = 24.sp, fontWeight = FontWeight.Medium)
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { onEditAccount(account) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 0.dp)) {
                                    Icon(Icons.Default.Edit, null, Modifier.size(17.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Змінити назву або баланс", maxLines = 2, fontSize = 13.sp)
                                }
                                Spacer(Modifier.width(4.dp))
                                IconButton(onClick = { onDeleteAccount(account) }) {
                                    Icon(Icons.Default.DeleteOutline, "Видалити рахунок ${account.name}", tint = Red)
                                }
                            }
                        }
                    }
                }
                "categories" -> {
                    item { ManagementHeading("Категорії", { section = "general" }, onAddCategory) }
                    item {
                        Text("Назви, іконки та кольори категорій. Видалення категорії збереже її операції без категорії.", color = Muted, fontSize = 13.sp, lineHeight = 20.sp)
                    }
                    if (state.categories.isEmpty()) item {
                        SettingsCard {
                            Text("Створіть свою першу категорію", fontWeight = FontWeight.SemiBold)
                            Button(onClick = onAddCategory) { Text("Додати категорію") }
                        }
                    }
                    items(state.categories, key = { it.id }) { category ->
                        Surface(color = Panel, shape = RoundedCornerShape(20.dp)) {
                            Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                CategoryBadge(category, Modifier.size(42.dp))
                                Text(category.name, Modifier.weight(1f).padding(horizontal = 12.dp), fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                IconButton(onClick = { onEditCategory(category) }) { Icon(Icons.Default.Edit, "Редагувати категорію ${category.name}", tint = Accent, modifier = Modifier.size(21.dp)) }
                                IconButton(onClick = { onDeleteCategory(category) }) { Icon(Icons.Default.DeleteOutline, "Видалити категорію ${category.name}", tint = Red, modifier = Modifier.size(21.dp)) }
                            }
                        }
                    }
                }
                else -> {
                    item {
                        Text("Усе керування зібране тут", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text("Налаштуйте один раз — далі просто ведіть облік.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 5.dp))
                    }
                    item {
                        SettingsCard(spacing = 0) {
                            SettingsLink(Icons.Default.AccountBalanceWallet, "Рахунки", "${state.accounts.size} · назви, залишки та оформлення") { section = "accounts" }
                            HorizontalDivider(color = Muted.copy(alpha = .12f))
                            SettingsLink(Icons.Default.Category, "Категорії", "${state.categories.size} · іконки, кольори та назви") { section = "categories" }
                        }
                    }
                    item { SettingsSectionLabel("ШВИДКЕ ДОДАВАННЯ") }
                    item {
                        SettingsCard {
                            Text("Рахунок за замовчуванням", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            AccountPreferencePicker(state.accounts, preferences.defaultAccountId) {
                                onPreferencesChange(preferences.copy(defaultAccountId = it))
                            }
                            Spacer(Modifier.height(2.dp))
                            Text("Тип нової операції", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("expense" to "Витрата", "income" to "Дохід", "transfer" to "Переказ").forEach { (kind, label) ->
                                    FilterChip(
                                        selected = preferences.defaultKind == kind,
                                        onClick = { onPreferencesChange(preferences.copy(defaultKind = kind)) },
                                        label = { Text(label, fontSize = 12.sp) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                    item { SettingsSectionLabel("ПРИВАТНІСТЬ І ВІДГУК") }
                    item {
                        SettingsCard(spacing = 0) {
                            PreferenceToggle("Приховувати загальний баланс", "На головному екрані показуватимуться крапки замість суми.", preferences.hideBalance) { onPreferencesChange(preferences.copy(hideBalance = it)) }
                            HorizontalDivider(color = Muted.copy(alpha = .12f))
                            PreferenceToggle("Вібровідгук клавіатури", "Легке підтвердження натискання під час введення суми.", preferences.haptics) { onPreferencesChange(preferences.copy(haptics = it)) }
                        }
                    }
                    item { SettingsSectionLabel("ПРО ЗАСТОСУНОК") }
                    item {
                        SettingsCard {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Мова", color = Muted, fontSize = 13.sp); Text("Українська", fontSize = 13.sp) }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Валюта", color = Muted, fontSize = 13.sp); Text("Гривня · UAH", fontSize = 13.sp) }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Оформлення", color = Muted, fontSize = 13.sp); Text("Темна тема", fontSize = 13.sp) }
                            HorizontalDivider(color = Muted.copy(alpha = .12f), modifier = Modifier.padding(vertical = 4.dp))
                            Text("Ваші дані залишаються на телефоні", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            Text("Без сервера, реклами та банківських підключень. Очищення даних або видалення застосунку видалить фінансову історію. Автоматичного резервного копіювання немає.", color = Muted, fontSize = 12.sp, lineHeight = 19.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(spacing: Int = 12, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Panel, shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(spacing.dp), content = content)
    }
}

@Composable
private fun ManagementHeading(title: String, back: () -> Unit, add: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад до налаштувань") }
        Text(title, Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        FilledTonalButton(onClick = add) { Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text("Додати") }
    }
}

@Composable
private fun SettingsSectionLabel(label: String) {
    Text(label, fontSize = 10.sp, color = Muted, letterSpacing = 1.4.sp, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
}

@Composable
private fun SettingIcon(icon: ImageVector, color: Color = Accent) {
    Surface(color = color.copy(alpha = .1f), shape = RoundedCornerShape(14.dp)) {
        Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) { Icon(icon, null, tint = color, modifier = Modifier.size(23.dp)) }
    }
}

@Composable
private fun SettingsLink(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent, shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
            SettingIcon(icon)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(subtitle, fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 4.dp))
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Muted, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun PreferenceToggle(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, fontSize = 12.sp, color = Muted, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp))
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun AccountPreferencePicker(accounts: List<Account>, selectedId: Long?, select: (Long?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
            Icon(Icons.Default.CreditCard, null, modifier = Modifier.size(19.dp))
            Text(accounts.find { it.id == selectedId }?.name ?: "Перший доступний рахунок", Modifier.weight(1f).padding(horizontal = 10.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
            Icon(Icons.Default.ExpandMore, null, modifier = Modifier.size(20.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 300.dp)) {
            DropdownMenuItem(text = { Text("Перший доступний рахунок") }, onClick = { select(null); expanded = false })
            accounts.forEach { account -> DropdownMenuItem(text = { Text(account.name) }, onClick = { select(account.id); expanded = false }) }
        }
    }
}
