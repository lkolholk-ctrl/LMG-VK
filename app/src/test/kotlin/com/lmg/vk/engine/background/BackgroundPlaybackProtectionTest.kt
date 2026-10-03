package com.lmg.vk.engine.background

import org.junit.Test

/** Pure policy/dispatch tests: no Android, another player, or test lease. */
class BackgroundPlaybackProtectionTest {
    @Test fun backgroundProtectionRegressions() {
        runBackgroundPlaybackProtectionScenarios()
    }
}
