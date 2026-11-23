package com.antidpi.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antidpi.android.ui.MainViewModel

@Composable
fun ConverterTab(viewModel: MainViewModel) {
    val state by viewModel.converterState.collectAsState()
    val clipboard = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedTextField(
            value = state.inputVless,
            onValueChange = { viewModel.updateVlessInput(it) },
            label = { Text("Вставьте vless:// ссылку") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 4
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { viewModel.convertVlessToJson() },
                modifier = Modifier.weight(1f),
                enabled = state.inputVless.isNotBlank()
            ) {
                Text("Сгенерировать Xray JSON")
            }

            OutlinedButton(
                onClick = {
                    if (state.generatedJson.isNotBlank()) {
                        clipboard.setText(AnnotatedString(state.generatedJson))
                    }
                },
                enabled = state.generatedJson.isNotBlank()
            ) {
                Text("Копировать")
            }
        }

        state.error?.let {
            Text("Ошибка: $it", color = MaterialTheme.colorScheme.error)
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    text = state.generatedJson.ifBlank {
                                            "Здесь появится готовый JSON-конфиг Xray-core с \"dialerProxy\": \"antidpi-out\" на 127.0.0.1:1080.\n" +
                            "Импортируйте его в v2rayNG, Karing, NekoBox или Throne как Custom Config."
                    },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
            }
        }
    }
}
