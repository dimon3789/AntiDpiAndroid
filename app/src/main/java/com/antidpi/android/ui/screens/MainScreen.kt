package com.antidpi.android.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.antidpi.android.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    var selectedTab by remember { mutableIntStateOf(0) }

    val tabs = listOf(
        "Прокси" to Icons.Default.PowerSettingsNew,
        "Подбор" to Icons.Default.Speed,
        "VLESS→JSON" to Icons.Default.Build,
        "Гайд" to Icons.Default.Info
    )

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Anti-DPI Proxy (ByeDPI)") })
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, (label, icon) ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedTab) {
                0 -> ProxyTab(viewModel)
                1 -> BenchmarkTab(viewModel)
                2 -> ConverterTab(viewModel)
                3 -> GuideTab()
            }
        }
    }
}
