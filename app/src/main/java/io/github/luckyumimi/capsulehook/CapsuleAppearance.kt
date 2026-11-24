package io.github.luckyumimi.capsulehook

// Set either value to null to run the other example by itself.
internal val demoWidthDp: Int? = 170
internal val demoBackgroundArgb: Int? = 0xA3336761.toInt()

/** Tracks one width write without taking ownership of an OEM replacement LayoutParams. */
internal class CapsuleLength<T : Any> {
    private var owner: T? = null
    private var original = 0
    private var applied = 0

    fun apply(params: T, current: Int, requested: Int): Int? {
        if (owner !== params || applied != current) {
            owner = null
            if (current == requested) return null
            owner = params
            original = current
        }
        if (current == requested) return null
        applied = requested
        return requested
    }

    fun restore(params: T?, current: Int?): Int? {
        val result = original.takeIf { owner != null && owner === params && current == applied }
        owner = null
        return result
    }
}

internal data class CapsuleBounds(val left: Int, val right: Int, val height: Int) {
    val radius: Float get() = minOf(right - left, height) / 2f
}

internal fun capsuleBounds(width: Int, measuredWidth: Int, height: Int, animatedWidth: Int?): CapsuleBounds? {
    if (width <= 0 || height <= 0) return null
    if (animatedWidth == null) return CapsuleBounds(0, width, height)
    if (animatedWidth <= 0 || measuredWidth <= 0) return null
    // Match ColorOS integer rounding, including negative offsets during overshoot.
    val left = (measuredWidth - animatedWidth) / 2
    return CapsuleBounds(left, left + animatedWidth, height)
}
