package com.lmg.vk.engine.automix.analysis

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** One JUnit method running 32 separately asserted host scenario groups.
 * HTTP and the cache validator are test doubles; this is not a real-JNI test. */
class AnalysisPrefetchTest {
    @Test fun proxyTransportCacheAndGenerationSafety() = runBlocking {
        assertEquals(32, AnalysisPrefetchScenarios.runAll())
    }
}
