package com.example.batteryalarm.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Geometria dell'anello, in frazioni del diametro dello schermo.
 * Gli angoli seguono la convenzione del Canvas: 0° = ore 3, senso orario.
 * L'apertura in basso lascia spazio all'EdgeButton.
 */
object RingGeometry {
    const val START_ANGLE = 160f
    const val SWEEP_ANGLE = 220f
    const val STROKE = 0.05f
    private const val GLOW_FACTOR = 1.8f
    private const val EDGE_MARGIN = 0.006f

    const val GLOW = STROKE * GLOW_FACTOR

    /** Distanza dal bordo dello schermo alla linea centrale dell'anello. */
    const val CENTERLINE_INSET = GLOW / 2f + EDGE_MARGIN

    /** Distanza dal bordo dello schermo al bordo interno dell'anello. */
    const val INNER_EDGE = CENTERLINE_INSET + STROKE / 2f
}

/**
 * Anello di carica lungo la circonferenza: traccia, zona oltre la soglia,
 * arco a gradiente con alone, tacca della soglia e punto luminoso sulla testa.
 */
@Composable
fun BatteryRing(
    percent: Int,
    charging: Boolean,
    ringing: Boolean,
    threshold: Int,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(percent) {
        progress.animateTo(
            targetValue = percent.coerceIn(0, 100) / 100f,
            animationSpec = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
        )
    }

    // Pulsazione solo quando serve: in carica (lenta) o allarme attivo (veloce)
    val pulse: State<Float> = if (charging || ringing) {
        rememberInfiniteTransition(label = "pulse").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(if (ringing) 450 else 1400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pulseValue",
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    val thresholdFraction = threshold / 100f
    val stops = remember(thresholdFraction) { ringStops(thresholdFraction) }

    Canvas(modifier.semantics { this.contentDescription = contentDescription }) {
        val d = size.minDimension
        val stroke = d * RingGeometry.STROKE
        val glow = d * RingGeometry.GLOW
        val inset = d * RingGeometry.CENTERLINE_INSET
        val radius = d / 2f - inset
        val arcSize = Size(radius * 2f, radius * 2f)
        val topLeft = Offset(center.x - radius, center.y - radius)
        val start = RingGeometry.START_ANGLE
        val sweep = RingGeometry.SWEEP_ANGLE
        val thresholdAngle = start + sweep * thresholdFraction

        fun pointAt(angleDeg: Float, r: Float = radius): Offset {
            val rad = angleDeg * PI.toFloat() / 180f
            return Offset(center.x + r * cos(rad), center.y + r * sin(rad))
        }

        // 1) Traccia: grigia fino alla soglia, magenta tenue oltre (zona da evitare).
        //    Estremità arrotondate disegnate a mano per avere il colore giusto a ogni capo.
        val dangerTrack = Neon.Magenta.copy(alpha = 0.30f)
        drawArc(Neon.Track, start, sweep * thresholdFraction, false, topLeft, arcSize,
            style = Stroke(stroke, cap = StrokeCap.Butt))
        drawArc(dangerTrack, thresholdAngle, sweep * (1f - thresholdFraction), false, topLeft, arcSize,
            style = Stroke(stroke, cap = StrokeCap.Butt))
        drawCircle(Neon.Track, stroke / 2f, pointAt(start))
        drawCircle(dangerTrack, stroke / 2f, pointAt(start + sweep))

        // 2) Arco di carica a gradiente con alone "neon"
        val p = progress.value
        if (p > 0f) {
            // Il gradiente parte dal bordo del tappo arrotondato, così l'inizio resta rosso
            val capDeg = (stroke / 2f) / radius * 180f / PI.toFloat()
            val brush = Brush.sweepGradient(
                colorStops = stops.map { (f, c) -> (capDeg + sweep * f) / 360f to c }
                    .let { listOf(0f to stops.first().second) + it }
                    .toTypedArray(),
                center = center,
            )
            val glowAlpha = if (ringing) 0.20f + 0.45f * pulse.value else 0.22f
            rotate(start - capDeg, pivot = center) {
                drawArc(brush, capDeg, sweep * p, false, topLeft, arcSize,
                    alpha = glowAlpha, style = Stroke(glow, cap = StrokeCap.Round))
                drawArc(brush, capDeg, sweep * p, false, topLeft, arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }

        // 3) Indicatore della soglia: tacca bianca attraverso l'anello
        drawLine(
            color = Color.White,
            start = pointAt(thresholdAngle, radius - stroke * 0.85f),
            end = pointAt(thresholdAngle, radius + stroke * 0.85f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )

        // 4) Testa luminosa sulla posizione attuale
        if (percent >= 0) {
            val head = pointAt(start + sweep * p)
            val headColor = if (ringing) Neon.Magenta else colorAt(stops, p)
            drawHead(head, headColor, stroke, pulse.value)
        }
    }
}

private fun DrawScope.drawHead(at: Offset, color: Color, stroke: Float, pulse: Float) {
    drawCircle(color.copy(alpha = 0.35f), radius = stroke * (0.95f + 0.35f * pulse), center = at)
    drawCircle(color, radius = stroke * 0.62f, center = at)
    drawCircle(Color.White, radius = stroke * 0.30f, center = at)
}
