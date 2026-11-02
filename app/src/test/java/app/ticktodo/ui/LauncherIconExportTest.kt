package app.ticktodo.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.ticktodo.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Renders the launcher icon the way a launcher does (adaptive icon, full
 * bleed) and saves it as the 512×512 PNG that Play Console asks for, so the
 * store icon is always the same artwork as the app's. Output:
 * app/build/reports/icon/ic_launcher_512.png.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LauncherIconExportTest {

    @Test
    fun `adaptive launcher icon renders to a 512 px store icon`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val drawable = context.getDrawable(R.mipmap.ic_launcher)
        assertTrue(drawable is AdaptiveIconDrawable, "launcher icon should be adaptive")
        val size = 512
        // Adaptive icons are drawn at 108/72 of the visible size; Play wants the full square.
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val inset = (size * 18 / 108f)
        drawable.setBounds(-inset.toInt(), -inset.toInt(), size + inset.toInt(), size + inset.toInt())
        drawable.draw(canvas)
        val out = File("build/reports/icon").apply { mkdirs() }
        File(out, "ic_launcher_512.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertEquals(512, bitmap.width)
        // Not blank: the centre (white tick) and a corner (green background) differ.
        assertTrue(bitmap.getPixel(256, 300) != bitmap.getPixel(20, 20))
    }
}
