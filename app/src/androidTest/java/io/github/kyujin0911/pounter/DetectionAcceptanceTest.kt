package io.github.kyujin0911.pounter

import kotlinx.coroutines.runBlocking
import org.junit.Test

/** Position/correction gate only. The independent field workflow gates remain explicit. */
class DetectionAcceptanceTest {
    @Test fun evaluateIndependentSet() = runBlocking { evaluateImages("independent") }
}
