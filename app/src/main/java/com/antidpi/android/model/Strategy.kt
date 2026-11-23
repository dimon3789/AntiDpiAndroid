package com.antidpi.android.model

enum class StrategyTier {
    GOLDEN,
    SILVER,
    CUSTOM
}

data class AntiDpiStrategy(
    val id: String,
    val name: String,
    val tier: StrategyTier,
    val description: String,
    val ciadpiArgs: List<String>,
    val sourceZapret2: String? = null,
    val droppedFlags: List<String> = emptyList()
) {
    fun commandLinePreview(host: String = "127.0.0.1", port: Int = 1080): String {
        return (listOf("libciadpi.so", "--ip", host, "--port", port.toString()) + ciadpiArgs)
            .joinToString(" ")
    }
}

data class BenchmarkResult(
    val strategy: AntiDpiStrategy,
    val reachableCount: Int,
    val totalTested: Int,
    val avgLatencyMs: Long
) {
    val successRate: Float
        get() = if (totalTested == 0) 0f else reachableCount.toFloat() / totalTested.toFloat()
}
