package local.qqmusic.relay

import android.animation.ValueAnimator
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.PI
import kotlin.math.sin

@Composable
internal fun PlaybackBars(modifier: Modifier = Modifier, spectrumBands: State<FloatArray>? = null) {
    val phase = if (spectrumBands != null) null else if (ValueAnimator.areAnimatorsEnabled()) {
        val transition = rememberInfiniteTransition(label = "Playback bars")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = (2 * PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)),
            label = "Bar movement",
        )
    } else remember { mutableFloatStateOf(0f) }
    val accent = MaterialTheme.colorScheme.primary

    Canvas(modifier) {
        val angle = phase?.value ?: 0f
        val bands = spectrumBands?.value
        val count = AudioSpectrum.BAND_COUNT
        val spacing = size.width / count
        val width = spacing * 0.58f
        for (index in 0 until count) {
            val level = bands?.get(index) ?: run {
                val mix = 0.54 + 0.24 * sin(angle * 2 + index * 0.42) +
                    0.17 * sin(angle * 3 - index * 0.83)
                val taper = sin(PI * (index + 1) / (count + 1)).toFloat()
                mix.toFloat() * (0.4f + 0.6f * taper)
            }
            val height = size.height * level
            drawRoundRect(
                color = accent.copy(alpha = 0.07f + 0.18f * index / (count - 1)),
                topLeft = Offset(index * spacing, size.height - height),
                size = Size(width, height),
                cornerRadius = CornerRadius(width / 2),
            )
        }
    }
}
