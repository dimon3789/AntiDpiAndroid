package com.antidpi.android

import com.antidpi.android.strategy.StrategyMapper
import org.junit.Assert.assertTrue
import org.junit.Test

class StrategyMapperTest {

    @Test
    fun `maps zapret2 flags and strips raw socket ttl flags`() {
        val zapret = "--dpi-desync=fake,disorder2 --dpi-desync-split-pos=midsld " +
            "--dpi-desync-ttl=5 --dpi-desync-fooling=badseq --hostcase --domcase"

        val mapped = StrategyMapper.mapZapret2Line("t1", "Test Golden", zapret)

        assertTrue(mapped.ciadpiArgs.contains("--disorder"))
        assertTrue(mapped.ciadpiArgs.contains("0+sm"))
        assertTrue(mapped.ciadpiArgs.contains("--mod-http=h,d"))
        assertTrue(mapped.droppedFlags.any { it.contains("--dpi-desync-ttl") })
        assertTrue(mapped.droppedFlags.any { it.contains("--dpi-desync-fooling") })
    }
}
