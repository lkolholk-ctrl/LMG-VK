package com.lmg.vk.ui.screens

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.text.TextStyle
import com.lmg.vk.ui.glass.GlassDialog
import com.lmg.vk.ui.glass.GlassDialogButton
import com.lmg.vk.ui.liquid.FlatVerticalSlider
import com.lmg.vk.ui.liquid.FlatPlayerSlider
import com.lmg.vk.ui.theme.LiquidMetrics
import com.lmg.vk.ui.theme.LiquidSurfaces
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lmg.vk.R
import com.lmg.vk.engine.dsp.DspBand
import com.lmg.vk.engine.dsp.DspController
import com.lmg.vk.engine.dsp.DspSettings
import androidx.compose.ui.platform.LocalDensity
import com.lmg.vk.ui.theme.VkSansText
import com.lmg.vk.ui.theme.LiquidTheme
import kotlin.math.ln
import kotlin.math.exp

@Composable
internal fun DspSettingsContent() {
    val s by DspController.settings.collectAsState()
    val available by DspController.available.collectAsState()
    var selectedBand by remember { mutableIntStateOf(0) }
    var help by remember { mutableStateOf<Pair<String, String>?>(null) }
    val helpNote = stringResource(R.string.dsp_help_band_note)
    help?.let { (title, body) ->
        GlassDialog(visible = true, onDismiss = { help = null }, title = title,
            primaryButton = GlassDialogButton(stringResource(R.string.dsp_help_close), { help = null },
                backgroundColor = LiquidTheme.colors.textPrimary, textColor = LiquidTheme.colors.settingsBackground),
            content = {
                Text(body, color = LiquidTheme.colors.textSecondary, fontSize = 15.sp, lineHeight = 22.sp,
                    fontFamily = VkSansText,
                    modifier = Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()))
            })
    }
    DspCard {
        DspToggle(stringResource(R.string.dsp_enable), s.enabled) { on -> DspController.update { it.copy(enabled = on) } }
        if (!available) Text(stringResource(R.string.dsp_unavailable),
            color = LiquidTheme.colors.textSecondary, modifier = Modifier.padding(16.dp))
    }
    Spacer(Modifier.height(12.dp))
    DspSectionLabel(stringResource(R.string.dsp_mode))
    val modes = listOf(R.string.dsp_mode_off, R.string.dsp_graphic, R.string.dsp_parametric)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        modes.forEachIndexed { i, label ->
            DspChoice(s.mode == i, { DspController.update { it.copy(mode = i) } }, label = { Text(stringResource(label)) })
        }
    }
    if (s.mode == 1) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            DspSettings.PRESETS.forEachIndexed { index, (_, gains) ->
                val names = listOf(R.string.dsp_preset_flat, R.string.dsp_preset_bass,
                    R.string.dsp_preset_vocal, R.string.dsp_preset_rock, R.string.dsp_preset_electronic)
                DspChoice(s.graphic == gains, {
                    DspController.update { it.copy(graphic = gains) }
                }, label = { Text(stringResource(names[index])) })
            }
        }
        DspCard {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.dsp_gain) + ", dB", color = LiquidTheme.colors.textSecondary, fontSize = 13.sp)
                Text("−18 … +18", color = LiquidTheme.colors.textSecondary, fontSize = 13.sp)
            }
            BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                // Keep the whole bank visible when space permits, scroll rather than shrink labels.
                val bandWidth = (maxWidth / 10).coerceAtLeast(36.dp * LocalDensity.current.fontScale.coerceAtLeast(1f))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    val descriptions = listOf(R.string.dsp_help_31, R.string.dsp_help_62, R.string.dsp_help_125,
                        R.string.dsp_help_250, R.string.dsp_help_500, R.string.dsp_help_1000, R.string.dsp_help_2000,
                        R.string.dsp_help_4000, R.string.dsp_help_8000, R.string.dsp_help_16000)
                    s.graphic.forEachIndexed { i, gain ->
                        val explanation = stringResource(descriptions[i])
                        val title = "${DspSettings.GRAPHIC_LABELS[i]} Hz"
                        DspFader(DspSettings.GRAPHIC_LABELS[i], gain, -18f..18f,
                            "%+.1f".format(gain), Modifier.width(bandWidth),
                            accessibilityLabel = "${DspSettings.GRAPHIC_LABELS[i]} Hz",
                            accessibilityValue = db(gain), onInfo = { help = title to "$explanation\n\n$helpNote" }) { value ->
                            DspController.update { old -> old.copy(graphic = old.graphic.mapIndexed { j, g -> if (j == i) value else g }) }
                        }
                    }
                }
            }
            Text("Hz", color = LiquidTheme.colors.textSecondary, fontSize = 14.sp,
                modifier = Modifier.align(Alignment.End).padding(end = 14.dp, bottom = 4.dp))
        }
    } else if (s.mode == 2) {
        val columns = if (LocalDensity.current.fontScale > 1.3f) 2 else 4
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            repeat(8 / columns) { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(columns) { column ->
                        val i = row * columns + column
                        val title = stringResource(R.string.dsp_band_number, i + 1)
                        val explanation = parametricBandHelp(i, s.bands[i])
                        DspChoice(selectedBand == i, { selectedBand = i }, label = { Text("${i + 1}") },
                            description = stringResource(R.string.dsp_band_number, i + 1),
                            modifier = Modifier.weight(1f), bandButton = true, onInfo = { help = title to explanation })
                    }
                }
            }
        }
        val band = s.bands[selectedBand]
        fun changeBand(change: (DspBand) -> DspBand) = DspController.update { old ->
            old.copy(bands = old.bands.mapIndexed { i, b -> if (i == selectedBand) change(b) else b })
        }
        DspCard {
            DspToggle(stringResource(R.string.dsp_band_number, selectedBand + 1), band.enabled) { on -> changeBand { it.copy(enabled = on) } }
            val types = listOf(R.string.dsp_peak, R.string.dsp_low_shelf, R.string.dsp_high_shelf,
                R.string.dsp_low_pass, R.string.dsp_high_pass, R.string.dsp_notch)
            Row(Modifier.padding(horizontal = 12.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                types.forEachIndexed { i, label ->
                    DspChoice(band.type == i, { changeBand { it.copy(type = i) } }, label = { Text(stringResource(label)) })
                }
            }
            val frequencyTitle = stringResource(R.string.dsp_frequency)
            val gainTitle = stringResource(R.string.dsp_gain)
            val slopeTitle = stringResource(R.string.dsp_slope)
            val qTitle = stringResource(R.string.dsp_q)
            val frequencyHelp = stringResource(R.string.dsp_help_frequency)
            val gainHelp = stringResource(R.string.dsp_help_gain)
            val slopeHelp = stringResource(R.string.dsp_help_slope)
            val qHelp = stringResource(R.string.dsp_help_q)
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                DspFader(frequencyTitle, ln(band.frequency), ln(20f)..ln(20000f),
                    "${band.frequency.toInt()} Hz", Modifier.weight(1f), onInfo = { help = frequencyTitle to frequencyHelp }) { v -> changeBand { it.copy(frequency = exp(v)) } }
                if (band.type <= 2) DspFader(stringResource(R.string.dsp_gain), band.gain, -24f..24f,
                    db(band.gain), Modifier.weight(1f), onInfo = { help = gainTitle to gainHelp }) { v -> changeBand { it.copy(gain = v) } }
                if (band.type == 1 || band.type == 2) {
                    DspFader(stringResource(R.string.dsp_slope), band.slope, 0.1f..1f,
                        "%.2f".format(band.slope), Modifier.weight(1f), onInfo = { help = slopeTitle to slopeHelp }) { v -> changeBand { it.copy(slope = v) } }
                } else {
                    DspFader(stringResource(R.string.dsp_q), ln(band.q), ln(0.1f)..ln(20f),
                        "%.2f".format(band.q), Modifier.weight(1f), bipolar = false, onInfo = { help = qTitle to qHelp }) { v -> changeBand { it.copy(q = exp(v)) } }
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    DspSectionLabel(stringResource(R.string.dsp_level))
    DspCard {
        DspSlider(stringResource(R.string.dsp_preamp), s.preamp, -36f..12f, db(s.preamp)) {
            v -> DspController.update { it.copy(preamp = v) }
        }
        DspSlider(stringResource(R.string.dsp_headroom), s.headroom, 0f..24f, db(-s.headroom)) {
            v -> DspController.update { it.copy(headroom = v) }
        }
        Text(stringResource(R.string.dsp_headroom_hint), color = LiquidTheme.colors.textSecondary,
            fontSize = 13.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
    }
    Spacer(Modifier.height(12.dp))
    DspCard {
        DspToggle(stringResource(R.string.dsp_limiter), s.limiter) { on -> DspController.update { it.copy(limiter = on) } }
        if (s.limiter) {
            DspSlider(stringResource(R.string.dsp_threshold), s.threshold, -36f..s.ceiling, db(s.threshold)) {
                v -> DspController.update { it.copy(threshold = v) }
            }
            DspSlider(stringResource(R.string.dsp_ceiling), s.ceiling, -12f..0f, db(s.ceiling)) {
                v -> DspController.update { it.copy(ceiling = v) }
            }
            DspSlider(stringResource(R.string.dsp_release), s.releaseMs, 5f..2000f,
                stringResource(R.string.dsp_milliseconds, s.releaseMs.toInt())) {
                v -> DspController.update { it.copy(releaseMs = v) }
            }
        }
    }
    Text(stringResource(R.string.dsp_reset), color = LiquidTheme.colors.accentRed,
        fontSize = 13.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(CircleShape).clickable(onClick = DspController::reset)
            .padding(horizontal = 12.dp, vertical = 14.dp))
    Text(stringResource(R.string.dsp_format_hint), color = LiquidTheme.colors.textTertiary, fontSize = 13.sp, lineHeight = 18.sp)
}

private fun db(value: Float) = "%+.1f dB".format(value)

@Composable
private fun DspSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>,
                      display: String, onChange: (Float) -> Unit) {
    val colors = LiquidTheme.colors
    val span = range.endInclusive - range.start
    if (LocalDensity.current.fontScale > 1.1f) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = colors.textSecondary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text(display, color = colors.textPrimary, fontSize = 13.sp)
            }
            FlatPlayerSlider(
                value = ((value - range.start) / span).coerceIn(0f, 1f),
                onValueChange = { onChange(range.start + it * span) },
                label = label, displayValue = display,
                activeColor = colors.textPrimary, trackColor = colors.textPrimary.copy(alpha = 0.16f),
                zeroFraction = if (range.start < 0f && range.endInclusive > 0f) -range.start / span else null,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        return
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = colors.textSecondary, fontSize = 14.sp, lineHeight = 17.sp,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(110.dp))
        FlatPlayerSlider(
            value = ((value - range.start) / span).coerceIn(0f, 1f),
            onValueChange = { onChange(range.start + it * span) },
            label = label, displayValue = display,
            activeColor = colors.textPrimary, trackColor = colors.textPrimary.copy(alpha = 0.16f),
            zeroFraction = if (range.start < 0f && range.endInclusive > 0f) -range.start / span else null,
            modifier = Modifier.weight(1f),
        )
        Text(display, color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.width(72.dp))
    }
}

@Composable
private fun DspFader(label: String, value: Float, range: ClosedFloatingPointRange<Float>,
                     display: String, modifier: Modifier = Modifier,
                     accessibilityLabel: String = label, accessibilityValue: String = display,
                     bipolar: Boolean = true, onInfo: () -> Unit, onChange: (Float) -> Unit) {
    val colors = LiquidTheme.colors
    val span = range.endInclusive - range.start
    Column(modifier.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(display, color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center, maxLines = 1)
        FlatVerticalSlider(
            value = ((value - range.start) / span).coerceIn(0f, 1f),
            onValueChange = { onChange(range.start + it * span) },
            label = accessibilityLabel, displayValue = accessibilityValue,
            activeColor = colors.textPrimary, trackColor = colors.textPrimary.copy(alpha = 0.12f),
            zeroFraction = if (bipolar && range.start < 0f && range.endInclusive > 0f) -range.start / span else null,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(label, color = colors.textSecondary, fontSize = 14.sp, lineHeight = 18.sp,
            textAlign = TextAlign.Center, minLines = 2, maxLines = 2)
        DspInfoButton(accessibilityLabel, onInfo)
    }
}

@Composable
private fun DspCard(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()
        .background(LiquidSurfaces.card(LiquidTheme.colors.isDark), LiquidMetrics.CardShape)
        .padding(vertical = 4.dp), content = content)
}

@Composable
private fun DspSectionLabel(text: String) {
    Text(text, color = LiquidTheme.colors.textSecondary, fontSize = 14.sp,
        fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
}

@Composable
private fun DspChoice(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit,
                      description: String? = null, modifier: Modifier = Modifier, bandButton: Boolean = false, onInfo: (() -> Unit)? = null) {
    val colors = LiquidTheme.colors
    Box(modifier.heightIn(min = 48.dp)
        .selectable(selected = selected, role = Role.RadioButton,
            interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
        .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
        .padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.then(if (bandButton) Modifier.fillMaxWidth().heightIn(min = 36.dp) else Modifier)
            .background(if (selected) colors.textPrimary else LiquidSurfaces.card(colors.isDark), CircleShape)
            .padding(horizontal = if (bandButton) 8.dp else 16.dp, vertical = if (bandButton) 2.dp else 8.dp), contentAlignment = Alignment.Center) {
            CompositionLocalProvider(
                LocalContentColor provides if (selected) colors.settingsBackground else colors.textPrimary,
                LocalTextStyle provides TextStyle(fontSize = if (bandButton) 16.sp else 14.sp, fontWeight = FontWeight.Medium, fontFamily = VkSansText),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    label()
                    if (onInfo != null) DspInfoButton(description.orEmpty(), onInfo, tint = LocalContentColor.current)
                }
            }
        }
    }
}

@Composable
private fun DspToggle(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = LiquidTheme.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp)
        .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
        .padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Canvas(Modifier.size(42.dp, 26.dp)) {
            drawRoundRect(if (checked) colors.accent else colors.textTertiary,
                cornerRadius = CornerRadius(size.height / 2))
            val radius = size.height / 2 - 2.dp.toPx()
            val x = if (checked) size.width - size.height / 2 else size.height / 2
            drawCircle(Color.White, radius, Offset(x, size.height / 2))
        }
    }
}


@Composable
private fun DspInfoButton(label: String, onClick: () -> Unit, tint: Color = LiquidTheme.colors.textSecondary) {
    val description = stringResource(R.string.dsp_help_open, label)
    Box(Modifier.size(32.dp).clip(CircleShape)
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
            role = Role.Button, onClickLabel = description, onClick = onClick)
        .semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(17.dp)) { drawCircle(tint, style = Stroke(1.2.dp.toPx())) }
        Text("!", color = tint, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clearAndSetSemantics { })
    }
}

@Composable
private fun parametricBandHelp(index: Int, band: DspBand): String {
    val names = listOf(R.string.dsp_peak, R.string.dsp_low_shelf, R.string.dsp_high_shelf,
        R.string.dsp_low_pass, R.string.dsp_high_pass, R.string.dsp_notch)
    val descriptions = listOf(R.string.dsp_help_peak, R.string.dsp_help_low_shelf, R.string.dsp_help_high_shelf,
        R.string.dsp_help_low_pass, R.string.dsp_help_high_pass, R.string.dsp_help_notch)
    return stringResource(R.string.dsp_help_param_band, index + 1,
        stringResource(names[band.type]), band.frequency.toInt(), stringResource(descriptions[band.type]))
}
