package com.antidpi.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun GuideTab() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GuideStepCard(
            title = "Шаг 1. Исключите Anti-DPI Proxy в VPN-клиенте (ОБЯЗАТЕЛЬНО)",
            body = "Чтобы избежать зацикливания трафика (routing loop), добавьте пакет com.antidpi.android в список исключений (Per-App Proxy / Bypass Apps) в Karing, v2rayNG, NekoBox или Throne."
        )
        GuideStepCard(
            title = "Шаг 2. Подберите рабочую стратегию",
            body = "Перейдите на вкладку «Подбор», вставьте URL подписки (например, BLACK_VLESS_RUS_mobile.txt) или несколько vless:// ссылок и нажмите «Автоподбор стратегии». Приложение само выберет лучшую стратегию под вашего оператора."
        )
        GuideStepCard(
            title = "Шаг 3. Запустите локальный SOCKS5-прокси",
            body = "На вкладке «Прокси» нажмите «Запустить SOCKS5 (1080)». Убедитесь, что появилось постоянное уведомление Anti-DPI активен (127.0.0.1:1080)."
        )
        GuideStepCard(
            title = "Шаг 4. Импортируйте Xray JSON с dialerProxy",
            body = "На вкладке «VLESS→JSON» вставьте вашу vless:// ссылку, скопируйте сгенерированный JSON и импортируйте его в VPN-клиент (Import Custom Config from Clipboard). После этого включайте VPN."
        )
    }
}

@Composable
private fun GuideStepCard(title: String, body: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
