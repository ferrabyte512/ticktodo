package app.ticktodo.ui.appearance

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import app.ticktodo.R
import app.ticktodo.ui.theme.ColorMath
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Holds the picker's value as HSV + alpha. Keeping HSV (not just ARGB) means the
 * hue is not lost when saturation or brightness reach zero.
 */
class ColorPickerState(initial: Int) {
    var hue by mutableFloatStateOf(0f)
    var saturation by mutableFloatStateOf(0f)
    var value by mutableFloatStateOf(0f)
    var alpha by mutableFloatStateOf(1f)

    init { set(initial) }

    val argb: Int get() = ColorMath.fromHsv(ColorMath.Hsv(hue.coerceIn(0f, 360f), saturation.coerceIn(0f, 1f), value.coerceIn(0f, 1f)), alpha)

    fun set(argb: Int) {
        val hsv = ColorMath.toHsv(argb)
        // Keep the current hue for greys, where hue is undefined.
        if (hsv.saturation > 0f && hsv.value > 0f) hue = hsv.hue
        saturation = hsv.saturation
        value = hsv.value
        alpha = ColorMath.alpha(argb) / 255f
    }
}

@Composable
fun rememberColorPickerState(initial: Int) = remember { ColorPickerState(initial) }

private val HueColors = listOf(0f, 60f, 120f, 180f, 240f, 300f, 360f).map { Color.hsv(it % 360f, 1f, 1f) }

/** Hue ring with an inner saturation/brightness square. Drag either selector. */
@Composable
fun DonutColorWheel(state: ColorPickerState, modifier: Modifier = Modifier) {
    val wheelLabel = stringResource(R.string.picker_wheel)
    val hueText = stringResource(R.string.picker_hue_value, state.hue.roundToInt())
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val side = min(maxWidth, 300.dp)
        Canvas(
            Modifier
                .size(side)
                .testTag("colorWheel")
                .semantics {
                    contentDescription = wheelLabel
                    stateDescription = hueText
                    progressBarRangeInfo = ProgressBarRangeInfo(state.hue, 0f..360f)
                    setProgress { v -> state.hue = v.coerceIn(0f, 360f); true }
                }
                .pointerInput(state) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val geometry = WheelGeometry(size.width.toFloat())
                        val onRing = geometry.isOnRing(down.position)
                        val inSquare = geometry.isInSquare(down.position)
                        if (!onRing && !inSquare) return@awaitEachGesture
                        fun apply(p: Offset) {
                            if (onRing) state.hue = geometry.hueAt(p)
                            else geometry.svAt(p).let { (s, v) -> state.saturation = s; state.value = v }
                        }
                        apply(down.position)
                        down.consume()
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            apply(change.position)
                            change.consume()
                        }
                    }
                },
        ) {
            val g = WheelGeometry(size.width)
            // Hue ring.
            drawCircle(
                brush = Brush.sweepGradient(HueColors, center),
                radius = g.ringCenterRadius,
                style = Stroke(width = g.ringWidth),
            )
            // Saturation (left to right) and brightness (top to bottom) square.
            val topLeft = Offset(g.squareLeft, g.squareTop)
            val sq = Size(g.squareSide, g.squareSide)
            val corner = CornerRadius(12.dp.toPx())
            drawRoundRect(Color.hsv(state.hue % 360f, 1f, 1f), topLeft, sq, corner)
            drawRoundRect(Brush.horizontalGradient(listOf(Color.White, Color.Transparent), startX = topLeft.x, endX = topLeft.x + sq.width), topLeft, sq, corner)
            drawRoundRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black), startY = topLeft.y, endY = topLeft.y + sq.height), topLeft, sq, corner)
            // Selectors.
            val hueRad = Math.toRadians(state.hue.toDouble())
            val ringPos = Offset(
                center.x + (g.ringCenterRadius * cos(hueRad)).toFloat(),
                center.y + (g.ringCenterRadius * sin(hueRad)).toFloat(),
            )
            selector(ringPos, Color.hsv(state.hue % 360f, 1f, 1f), g.ringWidth * 0.42f)
            val svPos = Offset(g.squareLeft + g.squareSide * state.saturation, g.squareTop + g.squareSide * (1f - state.value))
            selector(svPos, Color(ColorMath.withAlpha(state.argb, 1f)), 11.dp.toPx())
        }
    }
}

private fun DrawScope.selector(at: Offset, fill: Color, radius: Float) {
    drawCircle(Color.Black.copy(alpha = 0.25f), radius + 3.dp.toPx(), at)
    drawCircle(Color.White, radius + 2.dp.toPx(), at)
    drawCircle(fill, radius - 1.dp.toPx(), at)
}

/** Shared maths for drawing and hit-testing the wheel. */
private class WheelGeometry(sidePx: Float) {
    val center = Offset(sidePx / 2, sidePx / 2)
    val outerRadius = sidePx / 2
    val ringWidth = outerRadius * 0.17f
    val ringCenterRadius = outerRadius - ringWidth / 2
    private val innerRadius = outerRadius - ringWidth - outerRadius * 0.06f
    val squareSide = innerRadius * sqrt(2f) * 0.96f
    val squareLeft = center.x - squareSide / 2
    val squareTop = center.y - squareSide / 2

    fun isOnRing(p: Offset): Boolean {
        val d = hypot(p.x - center.x, p.y - center.y)
        return d >= outerRadius - ringWidth * 1.4f && d <= outerRadius + ringWidth * 0.4f
    }

    fun isInSquare(p: Offset) = p.x in squareLeft - 16f..squareLeft + squareSide + 16f && p.y in squareTop - 16f..squareTop + squareSide + 16f

    fun hueAt(p: Offset): Float {
        val deg = Math.toDegrees(atan2((p.y - center.y).toDouble(), (p.x - center.x).toDouble())).toFloat()
        return (deg + 360f) % 360f
    }

    fun svAt(p: Offset): Pair<Float, Float> =
        ((p.x - squareLeft) / squareSide).coerceIn(0f, 1f) to (1f - (p.y - squareTop) / squareSide).coerceIn(0f, 1f)
}

/** Grey/white squares that show through transparent colours. */
fun Modifier.checkerboard(cell: Float = 16f): Modifier = drawBehind {
    val light = Color(0xFFFFFFFF)
    val dark = Color(0xFFDADADA)
    drawRect(light)
    var y = 0f
    var row = 0
    while (y < size.height) {
        var x = if (row % 2 == 0) 0f else cell
        while (x < size.width) {
            drawRect(dark, Offset(x, y), Size(cell, cell))
            x += cell * 2
        }
        y += cell
        row++
    }
}

/**
 * Complete picker: wheel, fine-tune sliders, optional opacity, HEX input and a
 * before/after preview. Calls [onChange] for every edit.
 */
@Composable
fun ColorPickerContent(
    state: ColorPickerState,
    original: Int,
    showAlpha: Boolean,
    presets: List<Int>,
    modifier: Modifier = Modifier,
) {
    val current = state.argb
    // Text the user is typing. Kept while it is incomplete or while it matches the
    // colour it produced; any other change (dragging the wheel) replaces it.
    var draft by remember { mutableStateOf<String?>(null) }
    var appliedFromHex by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(current) { if (current != appliedFromHex) draft = null }
    val hexText = draft ?: ColorMath.toHex(current, includeAlpha = showAlpha)
    val hexError = draft != null && !isValidHex(draft!!, showAlpha)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DonutColorWheel(state, Modifier.fillMaxWidth())

        Preview(original, current)

        LabeledSlider(
            stringResource(R.string.picker_saturation), state.saturation, "saturationSlider",
            track = listOf(Color.hsv(state.hue % 360f, 0f, state.value), Color.hsv(state.hue % 360f, 1f, state.value)),
        ) { state.saturation = it }
        LabeledSlider(
            stringResource(R.string.picker_brightness), state.value, "brightnessSlider",
            track = listOf(Color.Black, Color.hsv(state.hue % 360f, state.saturation, 1f)),
        ) { state.value = it }
        if (showAlpha) OpacitySlider(state)

        OutlinedTextField(
            value = hexText,
            onValueChange = { input ->
                val cleaned = input.uppercase().filter { it == '#' || it.isDigit() || it in 'A'..'F' }.take(9)
                draft = cleaned
                if (isValidHex(cleaned, showAlpha)) {
                    val parsed = ColorMath.parseHex(cleaned)!!
                    val color = if (showAlpha) parsed else parsed or 0xFF000000.toInt()
                    state.set(color)
                    appliedFromHex = state.argb
                } else {
                    appliedFromHex = null
                }
            },
            label = { Text(stringResource(R.string.picker_hex)) },
            supportingText = { Text(stringResource(if (hexError) R.string.picker_hex_error else if (showAlpha) R.string.picker_hex_hint_alpha else R.string.picker_hex_hint)) },
            isError = hexError,
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth().testTag("hexField"),
        )

        if (presets.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                presets.forEach { preset ->
                    val label = ColorMath.toHex(preset, includeAlpha = false)
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(preset))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                            .clickable { state.set(if (showAlpha) ColorMath.withAlpha(preset, state.alpha) else preset) }
                            .semantics { contentDescription = label },
                    )
                }
            }
        }
    }
}

@Composable
private fun Preview(original: Int, current: Int) {
    val animated by animateColorAsState(Color(current), label = "preview")
    Row(Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(16.dp)).checkerboard()) {
        Box(Modifier.weight(1f).height(64.dp).background(Color(original)), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.picker_current), color = Color(ColorMath.onColor(ColorMath.withAlpha(original, 1f))), style = MaterialTheme.typography.labelLarge)
        }
        Box(
            Modifier.weight(1f).height(64.dp).background(animated).testTag("newPreview"),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.picker_new) + "  Aa",
                color = Color(ColorMath.onColor(ColorMath.withAlpha(current, 1f))),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            )
        }
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, tag: String, track: List<Color>, onChange: (Float) -> Unit) {
    Column {
        Row {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text("${(value * 100).roundToInt()}%", style = MaterialTheme.typography.labelLarge)
        }
        // The track shows the colour range the slider moves through.
        Box(contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(14.dp).clip(CircleShape).background(Brush.horizontalGradient(track)))
            Slider(
                value = value,
                onValueChange = onChange,
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(activeTrackColor = Color.Transparent, inactiveTrackColor = Color.Transparent),
                modifier = Modifier.testTag(tag),
            )
        }
    }
}

@Composable
private fun OpacitySlider(state: ColorPickerState) {
    val opaque = Color(ColorMath.withAlpha(state.argb, 1f))
    Column {
        Row {
            Text(stringResource(R.string.picker_opacity), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text("${(state.alpha * 100).roundToInt()}%", style = MaterialTheme.typography.labelLarge)
        }
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(14.dp).clip(CircleShape).checkerboard(18f)
                    .background(Brush.horizontalGradient(listOf(opaque.copy(alpha = 0f), opaque))),
            )
            Slider(
                value = state.alpha,
                onValueChange = { state.alpha = it },
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(activeTrackColor = Color.Transparent, inactiveTrackColor = Color.Transparent),
                modifier = Modifier.testTag("opacitySlider"),
            )
        }
    }
}

/** 3 or 6 hex digits, or 8 (with alpha) when opacity is editable. */
fun isValidHex(text: String, allowAlpha: Boolean): Boolean {
    val digits = text.removePrefix("#")
    val lengthOk = digits.length == 3 || digits.length == 6 || (allowAlpha && digits.length == 8)
    return lengthOk && ColorMath.parseHex(digits) != null
}

/** Bottom sheet wrapper with Cancel / Apply. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorPickerSheet(
    title: String,
    initial: Int,
    showAlpha: Boolean,
    presets: List<Int>,
    onDismiss: () -> Unit,
    onApply: (Int) -> Unit,
) {
    val state = rememberColorPickerState(initial)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding(),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            ColorPickerContent(state, initial, showAlpha, presets)
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onApply(state.argb) }) { Text(stringResource(R.string.action_apply)) }
            }
        }
    }
}
