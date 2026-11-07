package ua.moneytracking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable internal fun ImportPreviewDialog(backup: FinanceBackup, busy: Boolean, dismiss: () -> Unit, confirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!busy) dismiss() },
        title = { Text("Відновити резервну копію?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Копія від ${Instant.ofEpochMilli(backup.exportedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))}")
                Text("Рахунків: ${backup.state.accounts.size}\nКатегорій: ${backup.state.categories.size}\nОперацій: ${backup.state.entries.size}\nВалюти: ${backup.state.currencies().joinToString()}")
                Text("Усі поточні рахунки, категорії, операції й налаштування буде замінено даними з цього файлу. Спочатку експортуйте поточні дані, якщо хочете зберегти їх.", color = Red)
                if (busy) LinearProgressIndicator()
            }
        },
        confirmButton = { TextButton(onClick = confirm, enabled = !busy) { Text("Замінити дані") } },
        dismissButton = { TextButton(onClick = dismiss, enabled = !busy) { Text("Скасувати") } }
    )
}
