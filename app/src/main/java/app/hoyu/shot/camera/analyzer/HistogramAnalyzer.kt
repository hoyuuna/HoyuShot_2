package app.hoyu.shot.camera.analyzer

import android.media.Image
import app.hoyu.shot.model.HistogramData
import java.nio.ByteBuffer

class HistogramAnalyzer {

    private val lumaBins = IntArray(256)
    private val redBins = IntArray(256)
    private val greenBins = IntArray(256)
    private val blueBins = IntArray(256)

    @Synchronized
    fun analyze(image: Image): HistogramData {
        lumaBins.fill(0)
        redBins.fill(0)
        greenBins.fill(0)
        blueBins.fill(0)

        val planes = image.planes
        if (planes.isEmpty()) return HistogramData()

        val yBuffer: ByteBuffer = planes[0].buffer
        val yRowStride = planes[0].rowStride
        val yPixelStride = planes[0].pixelStride

        val width = image.width
        val height = image.height

        // Downsample step to guarantee 60fps analysis without frame drops
        val step = 4

        var maxBinVal = 1

        for (row in 0 until height step step) {
            for (col in 0 until width step step) {
                val yIndex = row * yRowStride + col * yPixelStride
                if (yIndex < yBuffer.remaining()) {
                    val yVal = yBuffer.get(yIndex).toInt() and 0xFF
                    lumaBins[yVal]++
                    if (lumaBins[yVal] > maxBinVal) {
                        maxBinVal = lumaBins[yVal]
                    }
                }
            }
        }

        // If planes have U and V, calculate RGB approximate distribution
        if (planes.size >= 3) {
            val uBuffer = planes[1].buffer
            val vBuffer = planes[2].buffer
            val uRowStride = planes[1].rowStride
            val uPixelStride = planes[1].pixelStride

            for (row in 0 until height step (step * 2)) {
                for (col in 0 until width step (step * 2)) {
                    val yIdx = row * yRowStride + col * yPixelStride
                    val uvRow = row / 2
                    val uvCol = col / 2
                    val uvIdx = uvRow * uRowStride + uvCol * uPixelStride

                    if (yIdx < yBuffer.remaining() && uvIdx < uBuffer.remaining() && uvIdx < vBuffer.remaining()) {
                        val y = (yBuffer.get(yIdx).toInt() and 0xFF) - 16
                        val u = (uBuffer.get(uvIdx).toInt() and 0xFF) - 128
                        val v = (vBuffer.get(uvIdx).toInt() and 0xFF) - 128

                        // Standard YUV to RGB approximation
                        val r = (1.164f * y + 1.596f * v).toInt().coerceIn(0, 255)
                        val g = (1.164f * y - 0.813f * v - 0.391f * u).toInt().coerceIn(0, 255)
                        val b = (1.164f * y + 2.018f * u).toInt().coerceIn(0, 255)

                        redBins[r]++
                        greenBins[g]++
                        blueBins[b]++
                    }
                }
            }
        }

        return HistogramData(
            lumaBins = lumaBins.copyOf(),
            redBins = redBins.copyOf(),
            greenBins = greenBins.copyOf(),
            blueBins = blueBins.copyOf(),
            maxCount = maxBinVal
        )
    }
}

