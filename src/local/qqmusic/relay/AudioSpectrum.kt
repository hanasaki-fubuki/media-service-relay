package local.qqmusic.relay

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

internal class AudioSpectrum {
    private val bitReverse = IntArray(FRAME_SIZE) { Integer.reverse(it) ushr (32 - FFT_BITS) }
    private val hann = DoubleArray(FRAME_SIZE) { 0.5 - 0.5 * cos(2 * PI * it / (FRAME_SIZE - 1)) }
    private val cosine = DoubleArray(FRAME_SIZE / 2) { cos(-2 * PI * it / FRAME_SIZE) }
    private val sine = DoubleArray(FRAME_SIZE / 2) { sin(-2 * PI * it / FRAME_SIZE) }
    private val edges = IntArray(BAND_COUNT + 1) {
        val hz = 55 * (16_000.0 / 55).pow(it.toDouble() / BAND_COUNT)
        (hz * FRAME_SIZE / SAMPLE_RATE).roundToInt().coerceAtLeast(1)
    }
    private val real = DoubleArray(FRAME_SIZE)
    private val imaginary = DoubleArray(FRAME_SIZE)
    private val smoothed = FloatArray(BAND_COUNT)

    fun bands(pcm: ShortArray): FloatArray {
        for (index in 0 until FRAME_SIZE) {
            real[bitReverse[index]] = pcm[index] * (hann[index] / 32_768)
        }
        imaginary.fill(0.0)

        var width = 2
        while (width <= FRAME_SIZE) {
            val half = width / 2
            val stride = FRAME_SIZE / width
            for (block in 0 until FRAME_SIZE step width) {
                for (offset in 0 until half) {
                    val left = block + offset
                    val right = left + half
                    val angle = offset * stride
                    val re = real[right] * cosine[angle] - imaginary[right] * sine[angle]
                    val im = real[right] * sine[angle] + imaginary[right] * cosine[angle]
                    real[right] = real[left] - re
                    imaginary[right] = imaginary[left] - im
                    real[left] += re
                    imaginary[left] += im
                }
            }
            width *= 2
        }

        for (band in smoothed.indices) {
            val first = edges[band]
            val end = maxOf(first + 1, edges[band + 1])
            var peak = 0.0
            for (bin in first until end) {
                peak = maxOf(peak, real[bin] * real[bin] + imaginary[bin] * imaginary[bin])
            }
            val power = peak / ((FRAME_SIZE / 4.0) * (FRAME_SIZE / 4.0))
            val db = 10 * log10(power.coerceAtLeast(1e-12))
            val level = ((db + 62) / 48).toFloat().coerceIn(0f, 1f)
            val speed = if (level > smoothed[band]) 0.72f else 0.22f
            smoothed[band] += (level - smoothed[band]) * speed
        }
        return smoothed.copyOf()
    }

    companion object {
        private const val FFT_BITS = 11
        const val FRAME_SIZE = 1 shl FFT_BITS
        const val BAND_COUNT = 25
        const val SAMPLE_RATE = 48_000
    }
}
