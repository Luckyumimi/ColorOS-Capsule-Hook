package io.github.luckyumimi.capsulehook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CapsuleAppearanceTest {
    @Test fun restoresTheOriginalWidthAfterRepeatedUpdates() {
        val lease = CapsuleLength<Any>()
        val params = Any()
        assertEquals(170, lease.apply(params, -2, 170))
        assertNull(lease.apply(params, 170, 170))
        assertEquals(180, lease.apply(params, 170, 180))
        assertEquals(-2, lease.restore(params, 180))
        assertNull(lease.restore(params, -2))
    }

    @Test fun doesNotRestoreAfterTheOemReplacesOrChangesTheWidth() {
        val lease = CapsuleLength<Any>()
        val params = Any()
        val newParams = Any()
        lease.apply(params, -2, 170)
        assertNull(lease.restore(newParams, 170))
        lease.apply(params, -2, 170)
        assertNull(lease.restore(params, 140))
    }

    @Test fun capturesANewNativeValueWhenItReappliesTheOverride() {
        val lease = CapsuleLength<Any>()
        val params = Any()
        lease.apply(params, -2, 170)
        assertEquals(170, lease.apply(params, 140, 170))
        assertEquals(140, lease.restore(params, 170))
        assertNull(lease.apply(params, 170, 170))
        assertNull(lease.restore(params, 170))
    }

    @Test fun backgroundUsesMeasuredCenterAndKeepsNativeOvershoot() {
        assertEquals(CapsuleBounds(0, 170, 30), capsuleBounds(170, 173, 30, null))
        assertEquals(CapsuleBounds(61, 111, 30), capsuleBounds(170, 173, 30, 50))
        assertEquals(CapsuleBounds(-5, 179, 30), capsuleBounds(170, 173, 30, 184))
        assertEquals(6f, capsuleBounds(170, 173, 30, 12)?.radius ?: 0f, 0f)
        assertNull(capsuleBounds(170, 173, 30, 0))
    }
}
