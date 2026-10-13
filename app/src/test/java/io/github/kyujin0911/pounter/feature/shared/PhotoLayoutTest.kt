package io.github.kyujin0911.pounter.feature.shared

import org.junit.Assert.*
import org.junit.Test

class PhotoLayoutTest {
    @Test
    fun shortHeightAndLargeFontUseScrollableConfirm() {
        assertEquals(240f, photoViewportHeightDp(800f, 360f, 1.5f), .01f)
        assertEquals(180f, photoViewportHeightDp(400f, 360f, 1.5f), .01f)
        assertEquals(120f, photoViewportHeightDp(120f, 360f, 1.5f), .01f)
        assertFalse(shouldPinConfirmAction(719f, 1f))
        assertFalse(shouldPinConfirmAction(800f, 2f))
        assertTrue(shouldPinConfirmAction(800f, 1f))
    }
}
