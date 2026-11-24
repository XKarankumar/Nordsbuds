package com.example.ui.components

import android.content.Context
import android.os.PowerManager
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.EmeraldActive
import com.example.ui.theme.OnePlusRed
import com.example.ui.theme.OnePlusRedGlow
import kotlin.math.PI
import kotlin.math.sin

/**
 * Battery-aware animation gate: pauses GPU animations whenever the lifecycle is paused/stopped
 * or the screen is turned off (`PowerManager.isInteractive == false`).
 */
@Composable
fun rememberAnimationsActive(): Boolean {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    var isStarted by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    var isScreenInteractive by remember {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        mutableStateOf(pm?.isInteractive ?: true)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            isStarted = when (event) {
                Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME -> true
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> false
                else -> lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            }
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            isScreenInteractive = pm?.isInteractive ?: true
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return isStarted && isScreenInteractive
}

@Composable
fun rememberAnimatedBatteryLevel(targetLevel: Int?): Int? {
    if (targetLevel == null) return null
    val animated by animateIntAsState(
        targetValue = targetLevel.coerceIn(0, 100),
        animationSpec = spring(stiffness = 280f, dampingRatio = 0.85f),
        label = "battery_counter",
    )
    return animated
}

/**
 * Lightweight GPU-friendly Bluetooth connection & charging pulse ring canvas.
 */
@Composable
fun BluetoothPulseRings(
    isScanning: Boolean,
    isConnected: Boolean,
    isCharging: Boolean,
    modifier: Modifier = Modifier,
) {
    val active = rememberAnimationsActive() && (isScanning || isConnected || isCharging)
    val phase = if (active) {
        val transition = rememberInfiniteTransition(label = "bt_pulse")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = if (isScanning) 1500 else 2800,
                    easing = LinearEasing,
                ),
                repeatMode = RepeatMode.Restart,
            ),
            label = "bt_pulse_phase",
        )
        value
    } else {
        0.35f
    }

    val ringColor = when {
        isCharging -> EmeraldActive
        isConnected -> OnePlusRedGlow
        else -> OnePlusRed
    }

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = size.minDimension / 2f
        val ringCount = if (isScanning) 3 else 2
        for (i in 0 until ringCount) {
            val offsetPhase = (phase + i.toFloat() / ringCount) % 1f
            val radius = maxRadius * (0.45f + 0.55f * offsetPhase)
            val alpha = ((1f - offsetPhase) * if (isScanning) 0.55f else 0.28f).coerceIn(0f, 1f)
            drawCircle(
                color = ringColor.copy(alpha = alpha),
                radius = radius,
                center = center,
                style = Stroke(width = if (isScanning) 2.5.dp.toPx() else 1.5.dp.toPx()),
            )
        }
    }
}

/**
 * Lightweight GPU-friendly acoustic wave driven by the real Equalizer band gains and playback state.
 * Stops animating when the screen is off or app is backgrounded.
 */
@Composable
fun EqualizerAcousticWave(
    bandNormalizedLevels: List<Float>,
    isPlaybackActive: Boolean,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(84.dp),
) {
    val animationsActive = rememberAnimationsActive() && isPlaybackActive
    val phase = if (animationsActive) {
        val transition = rememberInfiniteTransition(label = "eq_wave")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = (2f * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2600, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "eq_wave_phase",
        )
        value
    } else {
        0f
    }

    val breath = if (animationsActive) {
        val transition = rememberInfiniteTransition(label = "eq_breath")
        val value by transition.animateFloat(
            initialValue = 0.72f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "eq_breath_amp",
        )
        value
    } else {
        0.55f
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val midY = h / 2f
        val safeBands = if (bandNormalizedLevels.isEmpty()) listOf(0.5f, 0.5f, 0.5f, 0.5f, 0.5f) else bandNormalizedLevels

        // Draw subtle centerline
        drawLine(
            color = Color.White.copy(alpha = 0.08f),
            start = Offset(0f, midY),
            end = Offset(w, midY),
            strokeWidth = 1.dp.toPx(),
        )

        // Draw EQ response curve + dynamic acoustic harmonic wave
        val eqPath = Path()
        val wavePath = Path()
        val steps = 64

        for (i in 0..steps) {
            val t = i.toFloat() / steps
            val x = t * w

            // Interpolate EQ gain across bands (0..1 -> -1..+1)
            val scaledIndex = t * (safeBands.size - 1)
            val idx0 = scaledIndex.toInt().coerceIn(0, safeBands.lastIndex)
            val idx1 = (idx0 + 1).coerceIn(0, safeBands.lastIndex)
            val frac = scaledIndex - idx0
            val bandGain = safeBands[idx0] * (1f - frac) + safeBands[idx1] * frac
            val eqOffset = (0.5f - bandGain) * (h * 0.62f)

            val eqY = midY + eqOffset
            if (i == 0) eqPath.moveTo(x, eqY) else eqPath.lineTo(x, eqY)

            val envelope = sin(t * PI).toFloat()
            val harmonic = sin(t * 4f * PI.toFloat() + phase) * (h * 0.14f) * breath * envelope
            val waveY = eqY + harmonic
            if (i == 0) wavePath.moveTo(x, waveY) else wavePath.lineTo(x, waveY)
        }

        drawPath(
            path = wavePath,
            brush = Brush.horizontalGradient(
                colors = listOf(
                    OnePlusRed.copy(alpha = 0.15f),
                    OnePlusRedGlow.copy(alpha = 0.45f),
                    OnePlusRed.copy(alpha = 0.15f),
                ),
            ),
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
        )

        drawPath(
            path = eqPath,
            brush = Brush.horizontalGradient(
                colors = listOf(
                    OnePlusRed,
                    OnePlusRedGlow,
                    Color.White.copy(alpha = 0.9f),
                    OnePlusRedGlow,
                    OnePlusRed,
                ),
            ),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
        )

        // Draw band nodes
        safeBands.forEachIndexed { index, norm ->
            val x = if (safeBands.size <= 1) w / 2f else (index.toFloat() / (safeBands.size - 1)) * w
            val y = midY + (0.5f - norm) * (h * 0.62f)
            drawCircle(
                color = OnePlusRedGlow.copy(alpha = 0.35f),
                radius = 7.dp.toPx(),
                center = Offset(x, y),
            )
            drawCircle(
                color = Color.White,
                radius = 3.dp.toPx(),
                center = Offset(x, y),
            )
        }
    }
}
