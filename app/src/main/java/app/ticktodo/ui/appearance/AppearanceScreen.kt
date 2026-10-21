package app.ticktodo.ui.appearance

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Colorize
import androidx.compose.material.icons.outlined.DoNotDisturbOn
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ticktodo.R
import app.ticktodo.data.settings.AppSettings
import app.ticktodo.data.settings.Appearance
import app.ticktodo.data.settings.ThemeMode
import app.ticktodo.data.settings.Wallpaper
import app.ticktodo.ui.common.GlassSurface
import app.ticktodo.ui.common.SectionTitle
import app.ticktodo.ui.theme.AccentPresets
import app.ticktodo.ui.theme.ColorMath
import app.ticktodo.ui.theme.LocalAppearance
import app.ticktodo.ui.theme.TickTheme
import app.ticktodo.ui.theme.WallpaperArt
import app.ticktodo.ui.theme.WallpaperImage
import app.ticktodo.ui.theme.label
import app.ticktodo.ui.theme.seed
import app.ticktodo.ui.theme.wallpaperKey

private val SceneWallpapers = listOf(
    Wallpaper.NONE, Wallpaper.PEACH_GLOW, Wallpaper.MOUNTAINS, Wallpaper.DUNES, Wallpaper.SEA_MIST,
    Wallpaper.FOREST, Wallpaper.WAVES, Wallpaper.AURORA, Wallpaper.GRADIENT, Wallpaper.PHOTO,
)

fun wallpaperLabel(w: Wallpaper): Int = when (w) {
    Wallpaper.NONE -> R.string.wallpaper_none
    Wallpaper.PEACH_GLOW -> R.string.wallpaper_peach
    Wallpaper.MOUNTAINS -> R.string.wallpaper_mountains
    Wallpaper.DUNES -> R.string.wallpaper_dunes
    Wallpaper.SEA_MIST -> R.string.wallpaper_sea
    Wallpaper.FOREST -> R.string.wallpaper_forest
    Wallpaper.WAVES -> R.string.wallpaper_waves
    Wallpaper.AURORA -> R.string.wallpaper_aurora
    Wallpaper.GRADIENT -> R.string.wallpaper_gradient
    Wallpaper.PHOTO -> R.string.wallpaper_photo
}

/** Which colour the picker sheet is editing. */
private enum class PickerTarget { ACCENT, GRADIENT_START, GRADIENT_END }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(onBack: () -> Unit, viewModel: AppearanceViewModel = hiltViewModel()) {
    val settings by viewModel.state.collectAsStateWithLifecycle()
    val importing by viewModel.importing.collectAsStateWithLifecycle()
    val s = settings ?: return
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.importPhoto(uri)
    }
    var picker by remember { mutableStateOf<PickerTarget?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.appearance_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            AppearanceContent(
                settings = s,
                photoAvailable = viewModel.photoAvailable(),
                importing = importing,
                onThemeMode = viewModel::setThemeMode,
                onAccent = viewModel::setAccent,
                onCustomAccent = { picker = PickerTarget.ACCENT },
                onWallpaper = { w ->
                    when {
                        w == Wallpaper.PHOTO && (!viewModel.photoAvailable() || s.appearance.wallpaper == Wallpaper.PHOTO) ->
                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        else -> viewModel.setWallpaper(w)
                    }
                },
                onEditGradientStart = { picker = PickerTarget.GRADIENT_START },
                onEditGradientEnd = { picker = PickerTarget.GRADIENT_END },
                onOverlay = viewModel::setOverlay,
                onBlur = viewModel::setBlur,
                onGlass = viewModel::setGlassCards,
                onCardOpacity = viewModel::setCardOpacity,
                onDynamic = viewModel::setDynamicColor,
                onReset = viewModel::reset,
                modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
            )
        }
    }

    val a = s.appearance
    when (picker) {
        PickerTarget.ACCENT -> ColorPickerSheet(
            title = stringResource(R.string.appearance_custom_accent),
            initial = a.customAccent ?: a.accent.seed,
            showAlpha = false,
            presets = AccentPresets.map { it.seed },
            onDismiss = { picker = null },
            onApply = { viewModel.setCustomAccent(it); picker = null },
        )
        PickerTarget.GRADIENT_START, PickerTarget.GRADIENT_END -> {
            val start = picker == PickerTarget.GRADIENT_START
            ColorPickerSheet(
                title = stringResource(if (start) R.string.appearance_gradient_start else R.string.appearance_gradient_end),
                initial = if (start) a.gradientStart else a.gradientEnd,
                showAlpha = true,
                presets = GradientPresets,
                onDismiss = { picker = null },
                onApply = { c ->
                    if (start) viewModel.setGradient(c, a.gradientEnd) else viewModel.setGradient(a.gradientStart, c)
                    picker = null
                },
            )
        }
        null -> Unit
    }
}

/** Soft, warm defaults offered in the gradient colour picker. */
private val GradientPresets = listOf(
    0xFFFFE3D0, 0xFFF6EFE6, 0xFFFFD2B8, 0xFFF3D9C7, 0xFFFBE7C6, 0xFFE9E1F5, 0xFFDDEDE6, 0xFFDDE7F2,
).map { it.toInt() }

/** Stateless body, so it can be tested and previewed without a ViewModel. */
@Composable
fun AppearanceContent(
    settings: AppSettings,
    photoAvailable: Boolean,
    importing: Boolean,
    onThemeMode: (ThemeMode) -> Unit,
    onAccent: (app.ticktodo.data.settings.AppTheme) -> Unit,
    onCustomAccent: () -> Unit,
    onWallpaper: (Wallpaper) -> Unit,
    onEditGradientStart: () -> Unit,
    onEditGradientEnd: () -> Unit,
    onOverlay: (Float) -> Unit,
    onBlur: (Float) -> Unit,
    onGlass: (Boolean) -> Unit,
    onCardOpacity: (Float) -> Unit,
    onDynamic: (Boolean) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val a = settings.appearance
    // Slider drafts give a live preview; the value is saved when the drag ends.
    var overlay by remember(a.overlay) { mutableFloatStateOf(a.overlay) }
    var blur by remember(a.blurDp) { mutableFloatStateOf(a.blurDp) }
    var cardOpacity by remember(a.cardOpacity) { mutableFloatStateOf(a.cardOpacity) }
    val draft = a.copy(overlay = overlay, blurDp = blur, cardOpacity = cardOpacity)

    Column(modifier.verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        LivePreview(settings.themeMode, draft, settings.dynamicColor, Modifier.padding(16.dp))

        SectionTitle(stringResource(R.string.settings_theme))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            ThemeMode.entries.forEachIndexed { i, mode ->
                SegmentedButton(
                    selected = settings.themeMode == mode,
                    onClick = { onThemeMode(mode) },
                    shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                ) {
                    Text(stringResource(when (mode) { ThemeMode.SYSTEM -> R.string.theme_system; ThemeMode.LIGHT -> R.string.theme_light; ThemeMode.DARK -> R.string.theme_dark }))
                }
            }
        }

        SectionTitle(stringResource(R.string.appearance_accent))
        AccentRow(a, dynamicOn = settings.dynamicColor, onAccent = onAccent, onCustom = onCustomAccent)
        if (settings.dynamicColor) {
            Text(
                stringResource(R.string.appearance_dynamic_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SwitchRow(stringResource(R.string.settings_dynamic_color), stringResource(R.string.settings_dynamic_color_body), settings.dynamicColor, onDynamic)
        }

        SectionTitle(stringResource(R.string.appearance_wallpaper))
        WallpaperGrid(a, photoAvailable, importing, onWallpaper)
        if (a.wallpaper == Wallpaper.GRADIENT) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ColorButton(stringResource(R.string.appearance_gradient_start), a.gradientStart, onEditGradientStart, Modifier.weight(1f))
                ColorButton(stringResource(R.string.appearance_gradient_end), a.gradientEnd, onEditGradientEnd, Modifier.weight(1f))
            }
        }
        if (a.wallpaper == Wallpaper.PHOTO && photoAvailable) {
            OutlinedButton(onClick = { onWallpaper(Wallpaper.PHOTO) }, modifier = Modifier.padding(horizontal = 16.dp)) {
                Icon(Icons.Outlined.AddPhotoAlternate, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.appearance_change_photo))
            }
        }

        if (a.hasWallpaper) {
            SliderRow(
                label = stringResource(R.string.appearance_readability),
                valueText = "${(overlay * 100).toInt()}%",
                value = overlay,
                range = Appearance.OVERLAY_RANGE,
                tag = "overlaySlider",
                onChange = { overlay = it },
                onDone = { onOverlay(overlay) },
            )
            SliderRow(
                label = stringResource(R.string.appearance_blur),
                valueText = "${blur.toInt()}",
                value = blur,
                range = Appearance.BLUR_RANGE,
                tag = "blurSlider",
                onChange = { blur = it },
                onDone = { onBlur(blur) },
            )
            SwitchRow(stringResource(R.string.appearance_glass), stringResource(R.string.appearance_glass_body), a.glassCards, onGlass)
            if (a.glassCards) {
                SliderRow(
                    label = stringResource(R.string.appearance_card_opacity),
                    valueText = "${(cardOpacity * 100).toInt()}%",
                    value = cardOpacity,
                    range = Appearance.CARD_OPACITY_RANGE,
                    tag = "cardOpacitySlider",
                    onChange = { cardOpacity = it },
                    onDone = { onCardOpacity(cardOpacity) },
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onReset, modifier = Modifier.padding(horizontal = 16.dp)) {
            Icon(Icons.Outlined.Restore, null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.appearance_reset))
        }
    }
}

/** A miniature app screen drawn with the draft appearance, so changes are visible before leaving. */
@Composable
private fun LivePreview(mode: ThemeMode, draft: Appearance, dynamic: Boolean, modifier: Modifier) {
    TickTheme(themeMode = mode, appearance = draft, dynamicColor = dynamic) {
        val state = LocalAppearance.current
        Box(
            modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
                .background(state.pageColor)
                .testTag("livePreview"),
        ) {
            if (draft.hasWallpaper) {
                WallpaperImage(draft.wallpaperKey(state.dark), draft.blurDp, Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(state.pageColor.copy(alpha = draft.overlay)))
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.scope_today), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
                PreviewTask(stringResource(R.string.appearance_sample_task_1), done = false)
                PreviewTask(stringResource(R.string.appearance_sample_task_2), done = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = {}, enabled = true, modifier = Modifier.heightIn(min = 36.dp)) { Text(stringResource(R.string.action_add_task)) }
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Check, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewTask(title: String, done: Boolean) {
    GlassSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (done) Icons.Outlined.Check else Icons.Outlined.Circle,
                null,
                tint = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
        }
    }
}

@Composable
private fun AccentRow(a: Appearance, dynamicOn: Boolean, onAccent: (app.ticktodo.data.settings.AppTheme) -> Unit, onCustom: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            AccentPresets.chunked(5).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { theme ->
                        Swatch(
                            color = Color(theme.seed),
                            label = stringResource(theme.label),
                            selected = !dynamicOn && a.customAccent == null && a.accent == theme,
                            onClick = { onAccent(theme) },
                        )
                    }
                    if (row.size < 5) {
                        CustomSwatch(selected = !dynamicOn && a.customAccent != null, custom = a.customAccent, onClick = onCustom)
                    }
                }
            }
        }
    }
}

@Composable
private fun Swatch(color: Color, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(60.dp)) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(color)
                .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(Icons.Outlined.Check, null, tint = Color(ColorMath.onColor(color.toArgb())))
        }
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun CustomSwatch(selected: Boolean, custom: Int?, onClick: () -> Unit) {
    val label = stringResource(R.string.appearance_custom)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(60.dp)) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Brush.sweepGradient(listOf(0f, 60f, 120f, 180f, 240f, 300f, 360f).map { Color.hsv(it % 360f, 0.55f, 0.95f) }))
                .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                .selectable(selected = selected, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = label }
                .testTag("customAccent"),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(26.dp).clip(CircleShape).background(custom?.let { Color(it) } ?: MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Colorize, null, modifier = Modifier.size(16.dp), tint = custom?.let { Color(ColorMath.onColor(it)) } ?: MaterialTheme.colorScheme.onSurface) }
        }
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun WallpaperGrid(a: Appearance, photoAvailable: Boolean, importing: Boolean, onSelect: (Wallpaper) -> Unit) {
    val dark = LocalAppearance.current.dark
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SceneWallpapers.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { w ->
                    val selected = a.wallpaper == w
                    val label = stringResource(wallpaperLabel(w))
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.75f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(LocalAppearance.current.pageColor)
                                .border(
                                    if (selected) 3.dp else 1.dp,
                                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    RoundedCornerShape(16.dp),
                                )
                                .selectable(selected = selected, role = Role.RadioButton) { onSelect(w) }
                                .semantics { contentDescription = label }
                                .testTag("wallpaper_${w.name}"),
                            contentAlignment = Alignment.Center,
                        ) {
                            when {
                                w == Wallpaper.NONE -> Icon(Icons.Outlined.DoNotDisturbOn, null, tint = MaterialTheme.colorScheme.outline)
                                w == Wallpaper.PHOTO && importing -> CircularProgressIndicator(Modifier.size(28.dp))
                                w == Wallpaper.PHOTO && !photoAvailable -> Icon(Icons.Outlined.AddPhotoAlternate, null, tint = MaterialTheme.colorScheme.primary)
                                else -> WallpaperArt(a.wallpaperKey(dark, w), Modifier.fillMaxSize())
                            }
                            if (selected) {
                                Box(
                                    Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center,
                                ) { Icon(Icons.Outlined.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp)) }
                            }
                        }
                        Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ColorButton(label: String, color: Int, onClick: () -> Unit, modifier: Modifier) {
    FilledTonalButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp)) {
        Box(Modifier.size(20.dp).clip(CircleShape).checkerboard(6f).background(Color(color)).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    tag: String,
    enabled: Boolean = true,
    onChange: (Float) -> Unit,
    onDone: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).animateContentSize()) {
        Row {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value = value, onValueChange = onChange, onValueChangeFinished = onDone, valueRange = range, enabled = enabled, modifier = Modifier.testTag(tag))
    }
}

@Composable
private fun SwitchRow(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChange).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}
