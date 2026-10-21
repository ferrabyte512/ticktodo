package app.ticktodo.ui

import androidx.compose.ui.graphics.toArgb
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.ticktodo.data.settings.AppTheme
import app.ticktodo.ui.theme.ColorMath
import app.ticktodo.ui.theme.schemeFromSeed
import app.ticktodo.ui.theme.seed
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ColorMathTest {

    @Test fun `hex parsing accepts 3, 6 and 8 digits with or without hash`() {
        assertEquals(0xFFE8743B.toInt(), ColorMath.parseHex("#E8743B"))
        assertEquals(0xFFE8743B.toInt(), ColorMath.parseHex("e8743b"))
        assertEquals(0x80E8743B.toInt(), ColorMath.parseHex("#80E8743B"))
        assertEquals(0xFFFFAA00.toInt(), ColorMath.parseHex("#FA0"))
        assertNull(ColorMath.parseHex("#12"))
        assertNull(ColorMath.parseHex("#GG0000"))
        assertNull(ColorMath.parseHex(""))
        assertNull(ColorMath.parseHex("#1234567"))
    }

    @Test fun `hex formatting includes alpha only when not opaque`() {
        assertEquals("#E8743B", ColorMath.toHex(0xFFE8743B.toInt()))
        assertEquals("#80E8743B", ColorMath.toHex(0x80E8743B.toInt()))
        assertEquals("#FFE8743B", ColorMath.toHex(0xFFE8743B.toInt(), includeAlpha = true))
    }

    @Test fun `HSV round trip is exact for a grid of 8-bit colours`() {
        var checked = 0
        for (r in 0..255 step 15) for (g in 0..255 step 15) for (b in 0..255 step 15) {
            val c = ColorMath.argb(255, r, g, b)
            assertEquals(c, ColorMath.fromHsv(ColorMath.toHsv(c)), "round trip of ${ColorMath.toHex(c)}")
            checked++
        }
        assertTrue(checked > 4000)
    }

    @Test fun `known HSV values`() {
        val red = ColorMath.toHsv(0xFFFF0000.toInt())
        assertEquals(0f, red.hue)
        assertEquals(1f, red.saturation)
        assertEquals(0xFF00FF00.toInt(), ColorMath.fromHsv(ColorMath.Hsv(120f, 1f, 1f)))
        assertEquals(0x800000FF.toInt(), ColorMath.fromHsv(ColorMath.Hsv(240f, 1f, 1f), alpha = 0.5f))
    }

    @Test fun `contrast ratios match WCAG reference values`() {
        assertEquals(21.0, ColorMath.contrast(0xFF000000.toInt(), 0xFFFFFFFF.toInt()), 0.01)
        assertEquals(1.0, ColorMath.contrast(0xFF777777.toInt(), 0xFF777777.toInt()), 0.001)
        // #767676 on white is the classic 4.54:1 example.
        assertEquals(4.54, ColorMath.contrast(0xFF767676.toInt(), 0xFFFFFFFF.toInt()), 0.02)
    }

    @Test fun `ensureContrast reaches the target and keeps already-good colours`() {
        val bg = 0xFFFBF8F4.toInt()
        for (c in listOf(0xFFFFFF00, 0xFFE8743B, 0xFF00FFFF, 0xFFFFFFFF, 0xFF8E7CC3)) {
            val fixed = ColorMath.ensureContrast(c.toInt(), bg, 4.5)
            assertTrue(ColorMath.contrast(fixed, bg) >= 4.5, ColorMath.toHex(c.toInt()))
        }
        val good = 0xFF333333.toInt()
        assertEquals(good, ColorMath.ensureContrast(good, bg, 4.5))
        val dark = 0xFF161311.toInt()
        assertTrue(ColorMath.contrast(ColorMath.ensureContrast(0xFF202060.toInt(), dark, 4.5), dark) >= 4.5)
    }

    @Test fun `onColor is always readable`() {
        for (r in 0..255 step 17) for (g in 0..255 step 17) for (b in 0..255 step 17) {
            val c = ColorMath.argb(255, r, g, b)
            assertTrue(ColorMath.contrast(ColorMath.onColor(c), c) >= 4.5, ColorMath.toHex(c))
        }
    }

    @Test fun `alpha helpers`() {
        assertEquals(0x80, ColorMath.alpha(ColorMath.withAlpha(0xFF123456.toInt(), 0.5f)) )
        assertTrue(abs(ColorMath.alpha(ColorMath.withAlpha(0xFF123456.toInt(), 0f))) == 0)
    }
}

/** Every preset and some deliberately awkward custom accents must give readable schemes. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ThemeContrastTest {
    private val awkward = listOf(0xFFFFFF00, 0xFFFFFFFF, 0xFF000000, 0xFF00FFFF, 0xFF777777, 0xFFFFC0CB, 0xFF1A0033).map { it.toInt() }

    @Test fun `text roles meet WCAG AA on their backgrounds in light and dark`() {
        val seeds = AppTheme.entries.map { it.seed } + awkward
        for (seed in seeds) for (dark in listOf(false, true)) {
            val s = schemeFromSeed(seed, dark)
            val name = "${ColorMath.toHex(seed)} dark=$dark"
            fun check(fg: androidx.compose.ui.graphics.Color, bg: androidx.compose.ui.graphics.Color, min: Double, role: String) =
                assertTrue(ColorMath.contrast(fg.toArgb(), bg.toArgb()) >= min, "$role $name: ${ColorMath.contrast(fg.toArgb(), bg.toArgb())}")
            check(s.primary, s.background, 4.5, "primary/background")
            check(s.onPrimary, s.primary, 4.5, "onPrimary/primary")
            check(s.onPrimaryContainer, s.primaryContainer, 4.5, "onPrimaryContainer")
            check(s.onSecondaryContainer, s.secondaryContainer, 4.5, "onSecondaryContainer")
            check(s.onTertiaryContainer, s.tertiaryContainer, 4.5, "onTertiaryContainer")
            check(s.onSurface, s.surfaceContainerLow, 7.0, "onSurface/card")
            check(s.onSurfaceVariant, s.surfaceContainerLow, 4.5, "onSurfaceVariant/card")
            check(s.tertiary, s.background, 3.0, "tertiary ring")
        }
    }

    @Test fun `default light theme is warm off-white with an apricot accent`() {
        val s = schemeFromSeed(AppTheme.APRICOT.seed, dark = false)
        val bg = s.background.toArgb()
        assertTrue(ColorMath.red(bg) > ColorMath.blue(bg), "background should lean warm")
        assertTrue(ColorMath.relativeLuminance(bg) > 0.9, "background should be near white")
        val container = ColorMath.toHsv(s.primaryContainer.toArgb())
        assertTrue(container.hue in 10f..40f, "primary container should be light orange, hue=${container.hue}")
    }
}
