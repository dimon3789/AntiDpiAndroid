package com.antidpi.android.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.antidpi.android.benchmark.SubscriptionBenchmark
import com.antidpi.android.model.AntiDpiStrategy
import com.antidpi.android.model.BenchmarkResult
import com.antidpi.android.service.AntiDpiProxyService
import com.antidpi.android.service.ProxyState
import com.antidpi.android.strategy.StrategyMapper
import com.antidpi.android.xray.VlessToXrayConverter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BenchmarkUiState(
    val isRunning: Boolean = false,
    val progressText: String = "",
    val results: List<BenchmarkResult> = emptyList(),
    val error: String? = null
)

data class ConverterUiState(
    val inputVless: String = "",
    val generatedJson: String = "",
    val error: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val proxyState: StateFlow<ProxyState> = AntiDpiProxyService.state
    val proxyLogs: StateFlow<List<String>> = AntiDpiProxyService.logs

    private val _strategies = MutableStateFlow(StrategyMapper.defaultPresets)
    val strategies: StateFlow<List<AntiDpiStrategy>> = _strategies.asStateFlow()

    private val _selectedStrategy = MutableStateFlow(StrategyMapper.defaultPresets.first())
    val selectedStrategy: StateFlow<AntiDpiStrategy> = _selectedStrategy.asStateFlow()

    private val _benchmarkState = MutableStateFlow(BenchmarkUiState())
    val benchmarkState: StateFlow<BenchmarkUiState> = _benchmarkState.asStateFlow()

    private val _converterState = MutableStateFlow(ConverterUiState())
    val converterState: StateFlow<ConverterUiState> = _converterState.asStateFlow()

    fun selectStrategy(strategy: AntiDpiStrategy) {
        _selectedStrategy.value = strategy
    }

    fun addZapret2Strategy(name: String, zapret2Args: String) {
        val mapped = StrategyMapper.mapZapret2Line(
            id = "custom_${System.currentTimeMillis()}",
            name = name.ifBlank { "Custom zapret2" },
            zapret2Args = zapret2Args
        )
        _strategies.value = listOf(mapped) + _strategies.value
        _selectedStrategy.value = mapped
    }

    fun toggleProxy() {
        val ctx = getApplication<Application>()
        if (proxyState.value.isRunning) {
            AntiDpiProxyService.stop(ctx)
        } else {
            val s = _selectedStrategy.value
            AntiDpiProxyService.start(
                context = ctx,
                strategyName = s.name,
                args = s.ciadpiArgs
            )
        }
    }

    fun clearLogs() = AntiDpiProxyService.clearLogs()

    fun runSubscriptionBenchmark(subscriptionInput: String) {
        if (_benchmarkState.value.isRunning) return
        viewModelScope.launch {
            _benchmarkState.value = BenchmarkUiState(
                isRunning = true,
                progressText = "Извлечение серверов из подписки..."
            )
            try {
                val endpoints = SubscriptionBenchmark.extractEndpoints(subscriptionInput, maxNodes = 6)
                val results = SubscriptionBenchmark.runBenchmark(
                    context = getApplication(),
                    strategies = _strategies.value,
                    endpoints = endpoints
                ) { idx, total, name ->
                    _benchmarkState.value = _benchmarkState.value.copy(
                        progressText = "Тест $idx/$total: $name"
                    )
                }
                _benchmarkState.value = BenchmarkUiState(
                    isRunning = false,
                    progressText = "Готово! Протестировано стратегий: ${results.size}",
                    results = results
                )
                results.firstOrNull()?.takeIf { it.reachableCount > 0 }?.let { best ->
                    _selectedStrategy.value = best.strategy
                }
            } catch (e: Exception) {
                _benchmarkState.value = BenchmarkUiState(
                    isRunning = false,
                    error = e.message ?: "Ошибка бенчмарка"
                )
            }
        }
    }

    fun updateVlessInput(value: String) {
        _converterState.value = _converterState.value.copy(inputVless = value, error = null)
    }

    fun convertVlessToJson() {
        val raw = _converterState.value.inputVless
        runCatching {
            VlessToXrayConverter.convertToXrayJson(
                vlessUri = raw,
                antiDpiHost = proxyState.value.host,
                antiDpiPort = proxyState.value.port
            )
        }.onSuccess { json ->
            _converterState.value = _converterState.value.copy(
                generatedJson = json,
                error = null
            )
        }.onFailure { err ->
            _converterState.value = _converterState.value.copy(
                generatedJson = "",
                error = err.message ?: "Неверный формат vless://"
            )
        }
    }
}
