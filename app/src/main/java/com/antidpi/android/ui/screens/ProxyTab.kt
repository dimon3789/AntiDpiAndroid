package com.antidpi.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antidpi.android.ui.MainViewModel

@Composable
fun ProxyTab(viewModel: MainViewModel) {
    val state by viewModel.proxyState.collectAsState()
    val selected by viewModel.selectedStrategy.collectAsState()
    val logs by viewModel.proxyLogs.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (state.isRunning) {
                        "Статус: АКТИВЕН (${state.host}:${state.port})"
                    } else {
                        "Статус: ОСТАНОВЛЕН"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (state.isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(6.dp))
                Text("Стратегия: ${selected.name}", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = selected.commandLinePreview(state.host, state.port),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.secondary
                )
                state.lastError?.let { err ->
                    Spacer(Modifier.height(6.dp))
                    Text("Ошибка: $err", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { viewModel.toggleProxy() },
                modifier = Modifier.weight(1f),
                colors = if (state.isRunning) {
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.buttonColors()
                }
            ) {
                Text(if (state.isRunning) "Остановить прокси" else "Запустить SOCKS5 (1080)")
            }

            OutlinedButton(onClick = { viewModel.clearLogs() }) {
                Text("Очистить логи")
            }
        }

        Text("Логи движка ciadpi:", style = MaterialTheme.typography.labelLarge)

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (logs.isEmpty()) {
                    item {
                        Text(
                            "Логи появятся после запуска прокси.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                } else {
                    items(logs) { line ->
                        Text(
                            text = line,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}
