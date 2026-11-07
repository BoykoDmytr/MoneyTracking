package ua.moneytracking

import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun ExchangeRateDialog(source: String, target: String, amount: Long?, initial: String, dismiss: () -> Unit, confirm: (String) -> Unit) {
    var rate by rememberSaveable { mutableStateOf(initial) }
    var error by remember { mutableStateOf(false) }
    val result = exchangeAmount(amount, rate)
    FormDialog("Курс обміну", dismiss, {
        // Permit configuring a rate before entering the transfer amount.
        if (!validExchangeRate(rate) || (amount != null && amount > 0 && result == null)) error = true
        else confirm(rate.trim().replace(',', '.'))
    }, saveLabel = "Готово", maxPanelHeight = 420.dp) {
        Text("Скільки $target ви отримуєте за 1 $source?", fontSize = 15.sp)
        Field("Курс $source → $target", rate, true) { rate = it.take(40); error = false }
        Text("Вкажіть фактичний курс вашого обміну. Сума зарахування округлюється до 2 знаків.", color = Muted, fontSize = 12.sp)
        if (result != null) Text("${money(amount!!, source)} → ${money(result, target)}")
        if (error) Text("Введіть додатний курс і перевірте суму зарахування.", color = Red, fontSize = 12.sp)
    }
}
