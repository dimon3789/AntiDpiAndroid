package com.antidpi.android.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antidpi.android.ui.MainViewModel

@Composable
fun BenchmarkTab(viewModel: MainViewModel) {
    val strategies by viewModel.strategies.collectAsState()
    val selected by viewModel.selectedStrategy.collectAsState()
    val benchState by viewModel.benchmarkState.collectAsState()

    var subscriptionInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedTextField(
            value = subscriptionInput,
            onValueChange = { subscriptionInput = it },
            label = { Text("URL подписки (BLACK_VLESS_RUS_mobile.txt) или список vless://") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3
        )

        Button(
            onClick = { viewModel.runSubscriptionBenchmark(subscriptionInput) },
            enabled = !benchState.isRunning && subscriptionInput.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (benchState.isRunning) "Идет автоподбор..." else "Автоподбор стратегии под оператора")
        }

        if (benchState.isRunning) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        if (benchState.progressText.isNotBlank()) {
            Text(benchState.progressText, style = MaterialTheme.typography.bodySmall)
        }
        benchState.error?.let {
            Text("Ошибка: $it", color = MaterialTheme.colorScheme.error)
        }

        Text("Стратегии (нажмите для выбора):", style = MaterialTheme.typography.labelLarge)

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (benchState.results.isNotEmpty()) {
                items(benchState.results) { res ->
                    val isSelected = res.strategy.id == selected.id
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.selectStrategy(res.strategy) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(res.strategy.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "${res.reachableCount}/${res.totalTested} (${if (res.avgLatencyMs < Long.MAX_VALUE) "${res.avgLatencyMs}ms" else "—"})",
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                res.strategy.ciadpiArgs.joinToString(" "),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            } else {
                items(strategies) { strategy ->
                    val isSelected = strategy.id == selected.id
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.selectStrategy(strategy) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("[${strategy.tier}] ${strategy.name}", style = MaterialTheme.typography.titleSmall)
                            Text(strategy.description, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                strategy.ciadpiArgs.joinToString(" "),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        }
    }
}
