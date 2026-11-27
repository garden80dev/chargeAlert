package com.example.batteryalarm.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.material3.curvedText
import androidx.wear.compose.material3.timeTextCurvedText
import androidx.wear.compose.material3.timeTextSeparator
import com.example.batteryalarm.BatteryState
import com.example.batteryalarm.R

private val OnBright = Color(0xFF14110A)

@Composable
fun BatteryScreen(
    battery: BatteryState,
    monitoring: Boolean,
    ringing: Boolean,
    threshold: Int,
    onToggleMonitoring: () -> Unit,
    onStopAlarm: () -> Unit,
    onClose: () -> Unit,
) {
    // Spazio occupato dall'anello lungo il bordo: il contenuto resta all'interno
    val screenDp = LocalConfiguration.current.screenWidthDp.dp
    val ringBand = screenDp * RingGeometry.INNER_EDGE

    val stops = remember(threshold) { ringStops(threshold / 100f) }
    val levelColor = when {
        ringing -> Neon.Magenta
        battery.known -> colorAt(stops, battery.percent / 100f)
        else -> Neon.TextMuted
    }
    val listState = rememberTransformingLazyColumnState()

    AppScaffold {
        BatteryRing(
            percent = battery.percent,
            charging = battery.charging,
            ringing = ringing,
            threshold = threshold,
            contentDescription = stringResource(
                R.string.ring_description,
                battery.percent.coerceAtLeast(0),
                stringResource(if (battery.charging) R.string.charging else R.string.not_charging),
            ),
            modifier = Modifier.fillMaxSize(),
        )

        ScreenScaffold(
            scrollState = listState,
            timeText = { StatusTimeText(battery, ringing, Modifier.padding(ringBand + 3.dp)) },
            // Il contenuto sta dentro l'anello e sotto il testo curvo in alto.
            // Il padding inferiore viene sostituito dallo spazio dell'EdgeButton.
            contentPadding = PaddingValues(
                start = ringBand + 4.dp,
                end = ringBand + 4.dp,
                top = ringBand + 17.dp,
            ),
            edgeButtonSpacing = 4.dp,
            // L'indicatore di scorrimento coprirebbe l'anello; la corona scorre comunque
            scrollIndicator = null,
            edgeButton = {
                EdgeButton(
                    onClick = onClose,
                    buttonSize = EdgeButtonSize.ExtraSmall,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Neon.RedDeep,
                        contentColor = Color.White,
                        iconColor = Color.White,
                    ),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_close),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.close_app), fontWeight = FontWeight.Bold)
                }
            },
        ) { contentPadding ->
            TransformingLazyColumn(
                state = listState,
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                modifier = Modifier.fillMaxSize(),
            ) {
                item { PercentHero(battery, levelColor) }
                item { HintText(battery, monitoring, ringing, threshold) }
                // Un'azione alla volta: durante l'allarme conta solo fermarlo
                item {
                    val buttonWidth = Modifier.widthIn(max = screenDp * 0.64f)
                    if (ringing) {
                        GradientButton(
                            onClick = onStopAlarm,
                            colors = listOf(Neon.Magenta, Neon.Violet),
                            contentColor = Color.White,
                            icon = R.drawable.ic_bell,
                            label = stringResource(R.string.stop_alarm),
                            modifier = buttonWidth,
                        )
                    } else {
                        GradientButton(
                            onClick = onToggleMonitoring,
                            colors = if (monitoring) listOf(Neon.Orange, Neon.Yellow)
                            else listOf(Neon.Green, Neon.Cyan),
                            contentColor = OnBright,
                            icon = if (monitoring) R.drawable.ic_stop else R.drawable.ic_play,
                            label = stringResource(
                                if (monitoring) R.string.stop_monitoring else R.string.start_monitoring
                            ),
                            modifier = buttonWidth,
                        )
                    }
                }
            }
        }
    }
}

/** Ora e stato di ricarica scritti lungo il bordo superiore, all'interno dell'anello. */
@Composable
private fun StatusTimeText(battery: BatteryState, ringing: Boolean, modifier: Modifier) {
    val (label, color) = when {
        ringing -> stringResource(R.string.status_unplug_now) to Neon.Magenta
        battery.charging -> stringResource(R.string.status_charging) to Neon.Yellow
        else -> stringResource(R.string.status_unplugged) to Neon.TextMuted
    }
    TimeText(
        modifier = modifier,
        maxSweepAngle = 125f,
        contentPadding = PaddingValues(top = 2.dp),
    ) { time ->
        timeTextCurvedText(time)
        timeTextSeparator()
        curvedText(label, maxSweepAngle = 90f, color = color, fontWeight = FontWeight.Bold)
    }
}

/** Percentuale grande con gradiente nel colore attuale dell'anello. */
@Composable
private fun PercentHero(battery: BatteryState, color: Color) {
    val text = buildAnnotatedString {
        append(if (battery.known) battery.percent.toString() else "--")
        withStyle(SpanStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold)) { append("%") }
    }
    val brush = remember(color) {
        Brush.verticalGradient(listOf(lerp(color, Color.White, 0.55f), color))
    }
    BasicText(
        text = text,
        style = TextStyle(
            brush = brush,
            fontSize = 40.sp,
            lineHeight = 40.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-1).sp,
            textAlign = TextAlign.Center,
        ),
    )
}

@Composable
private fun HintText(battery: BatteryState, monitoring: Boolean, ringing: Boolean, threshold: Int) {
    val (text, color) = when {
        !monitoring -> stringResource(R.string.hint_monitoring_off) to Neon.TextMuted
        ringing -> stringResource(R.string.alarm_message) to Neon.Magenta
        battery.charging && battery.percent < threshold ->
            stringResource(R.string.hint_to_threshold, threshold - battery.percent) to Neon.TextDim
        battery.charging -> stringResource(R.string.hint_threshold_reached) to Neon.Green
        else -> stringResource(R.string.hint_alarm_at, threshold) to Neon.TextDim
    }
    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.labelSmall,
        textAlign = TextAlign.Center,
        maxLines = 2,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun GradientButton(
    onClick: () -> Unit,
    colors: List<Color>,
    contentColor: Color,
    @DrawableRes icon: Int,
    label: String,
    modifier: Modifier = Modifier,
) {
    val painter = remember(colors) { GradientPainter(Brush.horizontalGradient(colors)) }
    Button(
        onClick = onClick,
        containerPainter = painter,
        // 48dp: tap target minimo, più basso dei 52dp standard per stare su schermi da 192dp
        modifier = modifier.fillMaxWidth().height(48.dp),
        colors = ButtonDefaults.buttonWithContainerPainterColors(
            contentColor = contentColor,
            secondaryContentColor = contentColor.copy(alpha = 0.75f),
            iconColor = contentColor,
        ),
        icon = { Icon(painterResource(icon), contentDescription = null) },
        label = { Text(label, fontWeight = FontWeight.Bold) },
    )
}

/**
 * Riempie il contenitore con un gradiente. BrushPainter non va bene qui:
 * un gradiente orizzontale dichiara altezza intrinseca 0 e il pulsante
 * calcolerebbe una scala infinita.
 */
private class GradientPainter(private val brush: Brush) : Painter() {
    override val intrinsicSize: Size get() = Size.Unspecified
    override fun DrawScope.onDraw() {
        drawRect(brush)
    }
}
