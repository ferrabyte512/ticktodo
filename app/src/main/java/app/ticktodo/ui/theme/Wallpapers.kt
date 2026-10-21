package app.ticktodo.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.roundToInt
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.ticktodo.core.AppLog
import app.ticktodo.data.settings.Appearance
import app.ticktodo.data.settings.Wallpaper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Random
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.sin

/**
 * Draws the app background: page colour, optional wallpaper and a readability
 * scrim, with [content] on top.
 *
 * Performance: the wallpaper is rendered once, off the main thread, into a
 * small bitmap and then only that bitmap is drawn each frame. Redrawing
 * several full-screen gradients (or a live blur) on every frame made scrolling
 * janky on mid-range GPUs. Blur comes from rendering smaller and scaling up
 * smoothly, which is nearly free and works on every Android version.
 */
@Composable
fun AppBackground(content: @Composable () -> Unit) {
    val state = LocalAppearance.current
    val a = state.appearance
    Box(Modifier.fillMaxSize().background(state.pageColor)) {
        if (a.hasWallpaper) {
            Crossfade(
                targetState = a.wallpaperKey(state.dark) to a.blurDp,
                animationSpec = tween(500),
                label = "wallpaper",
            ) { (key, blur) ->
                WallpaperImage(key, blur, Modifier.fillMaxSize())
            }
            // Readability scrim in the page colour.
            Box(Modifier.fillMaxSize().background(state.pageColor.copy(alpha = a.overlay)))
        }
        content()
    }
}

/** Resolution factor for the cached wallpaper bitmap. Lower = softer (blurred) and lighter on memory. */
fun wallpaperRenderScale(blurDp: Float, photo: Boolean): Float {
    val sharp = if (photo) 1f else 0.5f
    return (sharp / (1f + blurDp.coerceAtLeast(0f) / 3f)).coerceIn(0.03f, 1f)
}

/** The wallpaper rendered once into a cached bitmap; see [AppBackground]. */
@Composable
fun WallpaperImage(key: WallpaperKey, blurDp: Float, modifier: Modifier = Modifier) {
    if (key.wallpaper == Wallpaper.NONE) {
        Box(modifier)
        return
    }
    val context = LocalContext.current
    val density = LocalDensity.current.density
    BoxWithConstraints(modifier) {
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        if (w <= 0 || h <= 0 || w == Int.MAX_VALUE || h == Int.MAX_VALUE) return@BoxWithConstraints
        val scale = wallpaperRenderScale(blurDp, key.wallpaper == Wallpaper.PHOTO)
        val image by produceState<ImageBitmap?>(initialValue = null, key, w, h, scale) {
            value = withContext(Dispatchers.Default) {
                try {
                    renderWallpaper(context, key, w, h, scale, density)
                } catch (e: Throwable) {
                    AppLog.w("wallpaper render failed", e)
                    null
                }
            }
        }
        val img = image
        if (img != null) {
            Canvas(Modifier.fillMaxSize()) {
                // Centre-crop the bitmap to the screen, scaled up with bilinear filtering.
                val srcRatio = img.width / img.height.toFloat()
                val dstRatio = size.width / size.height
                val (sw, sh) = if (srcRatio > dstRatio) (img.height * dstRatio).roundToInt() to img.height
                else img.width to (img.width / dstRatio).roundToInt()
                drawImage(
                    img,
                    srcOffset = IntOffset((img.width - sw) / 2, (img.height - sh) / 2),
                    srcSize = IntSize(sw.coerceAtLeast(1), sh.coerceAtLeast(1)),
                    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                    filterQuality = FilterQuality.Low,
                )
                if (key.dark && key.wallpaper == Wallpaper.PHOTO) drawRect(Color.Black.copy(alpha = 0.3f))
            }
        }
    }
}

/** Renders a wallpaper into a bitmap of about (width × scale) by (height × scale). Null if a photo is missing. */
internal fun renderWallpaper(context: Context, key: WallpaperKey, width: Int, height: Int, scale: Float, density: Float): ImageBitmap? {
    val bw = (width * scale).roundToInt().coerceAtLeast(8)
    val bh = (height * scale).roundToInt().coerceAtLeast(8)
    if (key.wallpaper == Wallpaper.PHOTO) {
        return WallpaperStore.decode(WallpaperStore.file(context), max(bw, bh))?.asImageBitmap()
    }
    val image = ImageBitmap(bw, bh)
    val canvas = androidx.compose.ui.graphics.Canvas(image)
    val scene = sceneFor(key)
    CanvasDrawScope().draw(Density(density * scale), LayoutDirection.Ltr, canvas, Size(bw.toFloat(), bh.toFloat())) { scene(this) }
    return image
}

/** Everything that changes how a wallpaper looks; used as the cross-fade key. */
data class WallpaperKey(val wallpaper: Wallpaper, val gradientStart: Int, val gradientEnd: Int, val photoVersion: Long, val dark: Boolean)

fun Appearance.wallpaperKey(dark: Boolean, wallpaper: Wallpaper = this.wallpaper) =
    WallpaperKey(wallpaper, gradientStart, gradientEnd, photoVersion, dark)

/** The wallpaper on its own; also used for the thumbnails in the appearance screen. */
@Composable
fun WallpaperArt(key: WallpaperKey, modifier: Modifier = Modifier) {
    when (key.wallpaper) {
        Wallpaper.NONE -> Box(modifier)
        Wallpaper.PHOTO -> PhotoWallpaper(key.photoVersion, key.dark, modifier)
        else -> Box(
            modifier.drawWithCache {
                val draw = sceneFor(key)
                onDrawBehind { draw(this) }
            },
        )
    }
}

@Composable
private fun PhotoWallpaper(version: Long, dark: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val maxPx = with(density) { max(maxWidth.roundToPx(), maxHeight.roundToPx()) }.coerceIn(256, 2048)
        val bitmap by produceState<ImageBitmap?>(initialValue = null, version, maxPx) {
            value = withContext(Dispatchers.IO) { WallpaperStore.decode(WallpaperStore.file(context), maxPx)?.asImageBitmap() }
        }
        val image = bitmap
        if (image != null) {
            Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (dark) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
        }
        // Missing or unreadable photo: nothing is drawn and the plain page colour shows.
    }
}

// ---------------------------------------------------------------------------------------------
// Scenes
// ---------------------------------------------------------------------------------------------

private fun sceneFor(key: WallpaperKey): DrawScope.() -> Unit {
    val dark = key.dark
    // Night versions keep the hues but sink them towards a warm near-black.
    fun c(argb: Long, alpha: Float = 1f): Color {
        val base = argb.toInt()
        val v = if (dark) ColorMath.blend(base, 0xFF14110E.toInt(), 0.45f) else base
        return Color(v).copy(alpha = alpha)
    }
    return when (key.wallpaper) {
        Wallpaper.PEACH_GLOW -> {
            {
                drawRect(Brush.verticalGradient(listOf(c(0xFFFBF6F1), c(0xFFFCEFE4))))
                glow(Offset(size.width * 0.95f, size.height * 0.85f), size.maxDimension * 0.7f, c(0xFFFFCDB0, 0.85f))
                glow(Offset(size.width * 0.05f, size.height * 0.12f), size.maxDimension * 0.55f, c(0xFFFFE2CF, 0.9f))
                glow(Offset(size.width * 0.6f, size.height * 0.35f), size.maxDimension * 0.35f, c(0xFFFFD7C4, 0.45f))
                // Two soft ribbons.
                ribbon(size.height * 0.30f, size.height * 0.06f, c(0xFFFFE6D6, 0.7f), phase = 0.4f)
                ribbon(size.height * 0.72f, size.height * 0.08f, c(0xFFFFD2B8, 0.45f), phase = 2.1f)
            }
        }
        Wallpaper.MOUNTAINS -> {
            {
                drawRect(Brush.verticalGradient(listOf(c(0xFFF7CFB6), c(0xFFFBE6D6), c(0xFFF8F0E8)), endY = size.height * 0.7f))
                glow(Offset(size.width * 0.72f, size.height * 0.30f), size.width * 0.35f, c(0xFFFFE0C4, 0.9f))
                drawCircle(c(0xFFFFD2AE), radius = size.width * 0.075f, center = Offset(size.width * 0.72f, size.height * 0.30f))
                ridge(seed = 11, base = 0.47f, amp = 0.10f, color = c(0xFFEBC0A8), peaks = 7)
                mist(0.46f, c(0xFFFFFFFF, 0.25f))
                ridge(seed = 23, base = 0.57f, amp = 0.11f, color = c(0xFFD99F84), peaks = 6)
                mist(0.58f, c(0xFFFFFFFF, 0.18f))
                ridge(seed = 37, base = 0.69f, amp = 0.10f, color = c(0xFFB97A64), peaks = 5)
                ridge(seed = 51, base = 0.82f, amp = 0.08f, color = c(0xFF8F5B4B), peaks = 4)
            }
        }
        Wallpaper.DUNES -> {
            {
                drawRect(Brush.verticalGradient(listOf(c(0xFFFCE7D4), c(0xFFF8EEE3)), endY = size.height * 0.5f))
                glow(Offset(size.width * 0.25f, size.height * 0.28f), size.width * 0.4f, c(0xFFFFD9B8, 0.9f))
                dune(0.50f, 0.07f, 1.3f, 0.0f, Brush.verticalGradient(listOf(c(0xFFF3D6B8), c(0xFFEBC6A0))))
                dune(0.62f, 0.08f, 1.1f, 1.7f, Brush.verticalGradient(listOf(c(0xFFEABF95), c(0xFFDFA97C))))
                dune(0.75f, 0.07f, 0.9f, 3.2f, Brush.verticalGradient(listOf(c(0xFFDDA577), c(0xFFCB8C5F))))
                dune(0.87f, 0.05f, 1.2f, 4.4f, Brush.verticalGradient(listOf(c(0xFFC98A5E), c(0xFFB77750))))
            }
        }
        Wallpaper.SEA_MIST -> {
            {
                drawRect(Brush.verticalGradient(listOf(c(0xFFDCE7EC), c(0xFFF1EEE9), c(0xFFE6EEF0))))
                glow(Offset(size.width * 0.5f, size.height * 0.38f), size.width * 0.6f, c(0xFFFFF4EA, 0.9f))
                ridge(seed = 5, base = 0.44f, amp = 0.05f, color = c(0xFFC7D6DD), peaks = 4)
                mist(0.44f, c(0xFFFFFFFF, 0.35f))
                for (i in 0 until 7) {
                    val y = size.height * (0.52f + i * 0.07f)
                    ribbon(y, size.height * 0.012f, c(0xFF9FB8C4, 0.18f + i * 0.04f), phase = i * 0.9f)
                }
            }
        }
        Wallpaper.FOREST -> {
            {
                drawRect(Brush.verticalGradient(listOf(c(0xFFE8EFE4), c(0xFFF6F2EA))))
                glow(Offset(size.width * 0.8f, size.height * 0.2f), size.width * 0.45f, c(0xFFFFF0DC, 0.9f))
                pines(seed = 3, baseFrac = 0.55f, heightFrac = 0.16f, count = 18, color = c(0xFFB9CDB8))
                mist(0.55f, c(0xFFFFFFFF, 0.3f))
                pines(seed = 9, baseFrac = 0.68f, heightFrac = 0.2f, count = 14, color = c(0xFF8EAE93))
                mist(0.68f, c(0xFFFFFFFF, 0.2f))
                pines(seed = 17, baseFrac = 0.86f, heightFrac = 0.26f, count = 10, color = c(0xFF5E7F67))
            }
        }
        Wallpaper.WAVES -> {
            {
                drawRect(c(0xFFFBF3EA))
                band(0.18f, 0.20f, c(0xFFFFE3D2, 0.9f), 0.2f)
                band(0.38f, 0.22f, c(0xFFFDD3BA, 0.75f), 1.4f)
                band(0.60f, 0.24f, c(0xFFF8BC9A, 0.6f), 2.6f)
                band(0.82f, 0.26f, c(0xFFF2A57E, 0.5f), 3.9f)
            }
        }
        Wallpaper.AURORA -> {
            {
                drawRect(c(0xFFFBF6F0))
                glow(Offset(size.width * 0.1f, size.height * 0.15f), size.maxDimension * 0.6f, c(0xFFFFCBAA, 0.8f))
                glow(Offset(size.width * 0.95f, size.height * 0.45f), size.maxDimension * 0.55f, c(0xFFE4D2F4, 0.8f))
                glow(Offset(size.width * 0.35f, size.height * 0.95f), size.maxDimension * 0.6f, c(0xFFCDEBE0, 0.8f))
                glow(Offset(size.width * 0.6f, size.height * 0.6f), size.maxDimension * 0.3f, c(0xFFFFE7C7, 0.6f))
            }
        }
        Wallpaper.GRADIENT -> {
            {
                drawRect(
                    Brush.linearGradient(
                        listOf(Color(key.gradientStart), Color(key.gradientEnd)).map { if (dark) it.copy(alpha = it.alpha * 0.55f) else it },
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                    ),
                )
            }
        }
        Wallpaper.NONE, Wallpaper.PHOTO -> { {} }
    }
}

private fun DrawScope.glow(center: Offset, radius: Float, color: Color) {
    drawCircle(Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), center = center, radius = radius), radius, center)
}

private fun DrawScope.mist(atFrac: Float, color: Color) {
    val y = size.height * atFrac
    drawRect(
        Brush.verticalGradient(listOf(color.copy(alpha = 0f), color, color.copy(alpha = 0f)), startY = y - size.height * 0.06f, endY = y + size.height * 0.06f),
        topLeft = Offset(0f, y - size.height * 0.06f),
        size = Size(size.width, size.height * 0.12f),
    )
}

/** Jagged mountain ridge filled to the bottom; deterministic for a given seed. */
private fun DrawScope.ridge(seed: Int, base: Float, amp: Float, color: Color, peaks: Int) {
    val rnd = Random(seed.toLong())
    val path = Path()
    val baseY = size.height * base
    path.moveTo(0f, baseY)
    val steps = peaks * 2
    val points = (0..steps).map { i ->
        val up = i % 2 == 1
        val h = size.height * amp * (if (up) 0.55f + rnd.nextFloat() * 0.45f else rnd.nextFloat() * 0.25f)
        Offset(size.width * i / steps, baseY - h)
    }
    // Smooth ridgeline: curve through the midpoints, using each point as control.
    path.lineTo(points.first().x, points.first().y)
    for (i in 1 until points.size) {
        val prev = points[i - 1]
        val cur = points[i]
        val mid = Offset((prev.x + cur.x) / 2, (prev.y + cur.y) / 2)
        path.quadraticTo(prev.x, prev.y, mid.x, mid.y)
    }
    path.lineTo(points.last().x, points.last().y)
    path.lineTo(size.width, size.height)
    path.lineTo(0f, size.height)
    path.close()
    drawPath(path, color)
}

/** Smooth dune: a sine-like curve filled downwards. */
private fun DrawScope.dune(base: Float, amp: Float, freq: Float, phase: Float, brush: Brush) {
    val path = Path()
    val baseY = size.height * base
    path.moveTo(0f, size.height)
    val n = 48
    for (i in 0..n) {
        val x = size.width * i / n
        val y = baseY - size.height * amp * sin((i / n.toFloat()) * Math.PI.toFloat() * freq + phase)
        path.lineTo(x, y)
    }
    path.lineTo(size.width, size.height)
    path.close()
    drawPath(path, brush)
}

/** Thin flowing band across the width. */
private fun DrawScope.ribbon(centerY: Float, thickness: Float, color: Color, phase: Float) {
    val top = Path()
    val n = 40
    top.moveTo(0f, centerY)
    for (i in 0..n) {
        val x = size.width * i / n
        top.lineTo(x, centerY + thickness * 2.5f * sin(i / n.toFloat() * 6.2f + phase))
    }
    for (i in n downTo 0) {
        val x = size.width * i / n
        top.lineTo(x, centerY + thickness + thickness * 2.5f * sin(i / n.toFloat() * 6.2f + phase + 0.5f))
    }
    top.close()
    drawPath(top, color)
}

/** Wide diagonal wave band for the abstract scene. */
private fun DrawScope.band(centerFrac: Float, heightFrac: Float, color: Color, phase: Float) {
    val path = Path()
    val n = 40
    val cy = size.height * centerFrac
    val amp = size.height * 0.06f
    path.moveTo(0f, size.height)
    for (i in 0..n) {
        val t = i / n.toFloat()
        path.lineTo(size.width * t, cy - size.height * heightFrac * 0.3f * t + amp * sin(t * 5f + phase))
    }
    path.lineTo(size.width, size.height)
    path.close()
    drawPath(path, color)
}

/** A row of simple pine silhouettes standing on a baseline. */
private fun DrawScope.pines(seed: Int, baseFrac: Float, heightFrac: Float, count: Int, color: Color) {
    val rnd = Random(seed.toLong())
    val baseY = size.height * baseFrac
    drawRect(color, topLeft = Offset(0f, baseY), size = Size(size.width, size.height - baseY))
    val step = size.width / count
    for (i in 0..count) {
        val cx = step * i + step * (rnd.nextFloat() - 0.5f) * 0.6f
        val h = size.height * heightFrac * (0.6f + rnd.nextFloat() * 0.4f)
        val w = h * 0.38f
        val path = Path().apply {
            moveTo(cx, baseY - h)
            lineTo(cx + w / 2, baseY + 1f)
            lineTo(cx - w / 2, baseY + 1f)
            close()
        }
        drawPath(path, color)
    }
}

// ---------------------------------------------------------------------------------------------
// User photo storage
// ---------------------------------------------------------------------------------------------

/**
 * Keeps the user's chosen photo as a resized private copy in app storage, so it
 * does not depend on gallery permissions and never leaves the device (it is
 * excluded from backup with the rest of the app's data).
 */
@Singleton
class WallpaperStore @Inject constructor(@ApplicationContext private val context: Context) {

    /** Copies and downsizes the picked image. Returns false if it cannot be read. */
    suspend fun import(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val bitmap = decodeUri(context, uri, MAX_SIDE) ?: return@withContext false
            val target = file(context)
            target.parentFile?.mkdirs()
            val tmp = File(target.parentFile, "custom.tmp")
            tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            bitmap.recycle()
            tmp.renameTo(target) || run { target.delete(); tmp.renameTo(target) }
        } catch (e: Exception) {
            AppLog.w("wallpaper import failed", e)
            false
        }
    }

    fun exists(): Boolean = file(context).isFile

    companion object {
        private const val MAX_SIDE = 2048

        fun file(context: Context): File = File(context.filesDir, "wallpaper/custom.jpg")

        /** Decodes [file] no larger than about [maxSide] pixels; null if missing or unreadable. */
        fun decode(file: File, maxSide: Int): Bitmap? {
            if (!file.isFile) return null
            return try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
                val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxSide) }
                BitmapFactory.decodeFile(file.path, options)
            } catch (e: OutOfMemoryError) {
                null
            } catch (e: Exception) {
                null
            }
        }

        fun sampleSize(width: Int, height: Int, maxSide: Int): Int {
            var sample = 1
            while (max(width, height) / (sample * 2) >= maxSide) sample *= 2
            return sample
        }

        private fun decodeUri(context: Context, uri: Uri, maxSide: Int): Bitmap? {
            val resolver = context.contentResolver
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // ImageDecoder also applies the photo's rotation metadata.
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                    val w = info.size.width
                    val h = info.size.height
                    val scale = minOf(1f, maxSide / max(w, h).toFloat())
                    decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                if (bounds.outWidth <= 0) return null
                val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxSide) }
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            }
        }
    }
}
