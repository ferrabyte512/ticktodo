package app.ticktodo.ui.theme

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Colour conversions and contrast helpers on plain ARGB ints, so they run in
 * unit tests without Android or Compose. Unit tested in ColorMathTest.
 */
object ColorMath {

    data class Hsv(val hue: Float, val saturation: Float, val value: Float) {
        init {
            require(hue in 0f..360f && saturation in 0f..1f && value in 0f..1f)
        }
    }

    fun alpha(argb: Int) = (argb ushr 24) and 0xFF
    fun red(argb: Int) = (argb shr 16) and 0xFF
    fun green(argb: Int) = (argb shr 8) and 0xFF
    fun blue(argb: Int) = argb and 0xFF

    fun argb(a: Int, r: Int, g: Int, b: Int): Int =
        (a.coerceIn(0, 255) shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    fun withAlpha(argb: Int, alpha: Float): Int = (argb and 0x00FFFFFF) or ((alpha.coerceIn(0f, 1f) * 255).roundToInt() shl 24)

    // ---- HSV ----------------------------------------------------------------------------------

    fun toHsv(argb: Int): Hsv {
        val r = red(argb) / 255f
        val g = green(argb) / 255f
        val b = blue(argb) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min
        val hue = when {
            delta == 0f -> 0f
            max == r -> 60f * (((g - b) / delta) % 6f)
            max == g -> 60f * (((b - r) / delta) + 2f)
            else -> 60f * (((r - g) / delta) + 4f)
        }.let { if (it < 0f) it + 360f else it }.coerceIn(0f, 360f)
        val sat = if (max == 0f) 0f else delta / max
        return Hsv(hue, sat.coerceIn(0f, 1f), max.coerceIn(0f, 1f))
    }

    fun fromHsv(hsv: Hsv, alpha: Float = 1f): Int {
        val c = hsv.value * hsv.saturation
        val h = (hsv.hue % 360f) / 60f
        val x = c * (1 - abs(h % 2f - 1))
        val (r1, g1, b1) = when (h.toInt()) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val m = hsv.value - c
        return argb(
            (alpha.coerceIn(0f, 1f) * 255).roundToInt(),
            ((r1 + m) * 255).roundToInt(),
            ((g1 + m) * 255).roundToInt(),
            ((b1 + m) * 255).roundToInt(),
        )
    }

    // ---- HEX ----------------------------------------------------------------------------------

    /** "#RRGGBB" when opaque, otherwise "#AARRGGBB". */
    fun toHex(argb: Int, includeAlpha: Boolean = alpha(argb) != 0xFF): String =
        if (includeAlpha) "#%08X".format(argb) else "#%06X".format(argb and 0xFFFFFF)

    /** Accepts RGB, RRGGBB or AARRGGBB, with or without '#'. Returns null when invalid. */
    fun parseHex(input: String): Int? {
        val s = input.trim().removePrefix("#")
        if (s.isEmpty() || !s.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
        return when (s.length) {
            3 -> {
                val r = s[0].digitToInt(16) * 17
                val g = s[1].digitToInt(16) * 17
                val b = s[2].digitToInt(16) * 17
                argb(255, r, g, b)
            }
            6 -> (0xFF000000 or s.toLong(16)).toInt()
            8 -> s.toLong(16).toInt()
            else -> null
        }
    }

    // ---- Contrast (WCAG 2.x) ------------------------------------------------------------------

    fun relativeLuminance(argb: Int): Double {
        fun channel(c: Int): Double {
            val s = c / 255.0
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(red(argb)) + 0.7152 * channel(green(argb)) + 0.0722 * channel(blue(argb))
    }

    fun contrast(a: Int, b: Int): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    /** Mixes [from] towards [to]; amount 0 = from, 1 = to. Alpha of the result is opaque. */
    fun blend(from: Int, to: Int, amount: Float): Int {
        val t = amount.coerceIn(0f, 1f)
        fun mix(a: Int, b: Int) = (a + (b - a) * t).roundToInt()
        return argb(255, mix(red(from), red(to)), mix(green(from), green(to)), mix(blue(from), blue(to)))
    }

    /**
     * Darkens (on a light background) or lightens (on a dark background) [color]
     * in small steps until it reaches [minRatio] against [background]. Keeps the
     * hue, so a custom accent stays recognisable but always readable.
     */
    fun ensureContrast(color: Int, background: Int, minRatio: Double): Int {
        var c = withAlpha(color, 1f)
        if (contrast(c, background) >= minRatio) return c
        val target = if (relativeLuminance(background) > 0.5) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        for (step in 1..40) {
            c = blend(withAlpha(color, 1f), target, step / 40f)
            if (contrast(c, background) >= minRatio) return c
        }
        return target
    }

    /**
     * Text colour for [background]: white, a soft near-black, or pure black,
     * whichever is readable. The black/white pair always reaches at least
     * about 4.58:1, so the result never falls below WCAG AA.
     */
    fun onColor(background: Int): Int {
        val white = 0xFFFFFFFF.toInt()
        val soft = 0xFF1B1B1B.toInt()
        val black = 0xFF000000.toInt()
        val onWhite = contrast(white, background)
        val onBlack = contrast(black, background)
        return when {
            onWhite >= onBlack -> white
            contrast(soft, background) >= 4.5 -> soft
            else -> black
        }
    }

    fun shiftHue(argb: Int, degrees: Float): Int {
        val hsv = toHsv(argb)
        return fromHsv(hsv.copy(hue = ((hsv.hue + degrees) % 360f + 360f) % 360f), alpha(argb) / 255f)
    }
}
