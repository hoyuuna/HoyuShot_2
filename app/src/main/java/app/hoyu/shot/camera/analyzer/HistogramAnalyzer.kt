package app.hoyu.shot.camera.analyzer

import android.graphics.Bitmap
import android.media.Image
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.hoyu.shot.model.HistogramData
import app.hoyu.shot.model.ViewfinderOverlayData
import java.nio.ByteBuffer
import kotlin.math.abs

class HistogramAnalyzer {

    private val lumaBins = IntArray(256)
    private val redBins = IntArray(256)
    private val greenBins = IntArray(256)
    private val blueBins = IntArray(256)

    private var overlayFrameCounter = 0L

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

    /**
     * Computes real spatial focus peaking (luminance edge gradient) and highlight clipping
     * zebra mask mapped into portrait screen coordinates.
     */
    @Synchronized
    fun analyzeOverlays(
        image: Image,
        sensorOrientation: Int,
        isFront: Boolean,
        computePeaking: Boolean,
        computeZebra: Boolean
    ): ViewfinderOverlayData {
        if (!computePeaking && !computeZebra) return ViewfinderOverlayData()
        val planes = image.planes
        if (planes.isEmpty()) return ViewfinderOverlayData()

        val yBuffer = planes[0].buffer
        val yRowStride = planes[0].rowStride
        val yPixelStride = planes[0].pixelStride
        val yCapacity = yBuffer.remaining()
        val width = image.width
        val height = image.height
        if (width < 16 || height < 16) return ViewfinderOverlayData()

        val cols = 48
        val rows = 64
        val totalCells = cols * rows
        val peakingStrength = ByteArray(totalCells)
        val zebraOver = IntArray(totalCells)
        val zebraTotal = IntArray(totalCells)

        val step = (width / 160).coerceAtLeast(2)
        val gradOffset = step

        for (row in gradOffset until (height - gradOffset) step step) {
            val ry = row.toFloat() / height
            val rowBase = row * yRowStride
            for (col in gradOffset until (width - gradOffset) step step) {
                val idx = rowBase + col * yPixelStride
                if (idx >= yCapacity) continue
                val yC = yBuffer.get(idx).toInt() and 0xFF

                val rx = col.toFloat() / width
                val sx: Float
                val sy: Float
                when (((sensorOrientation % 360) + 360) % 360) {
                    90 -> {
                        sx = if (isFront) ry else 1f - ry
                        sy = rx
                    }
                    270 -> {
                        sx = if (isFront) 1f - ry else ry
                        sy = 1f - rx
                    }
                    180 -> {
                        sx = if (isFront) rx else 1f - rx
                        sy = 1f - ry
                    }
                    else -> {
                        sx = if (isFront) 1f - rx else rx
                        sy = ry
                    }
                }

                val gx = (sx * cols).toInt().coerceIn(0, cols - 1)
                val gy = (sy * rows).toInt().coerceIn(0, rows - 1)
                val cellIdx = gy * cols + gx

                if (computeZebra) {
                    zebraTotal[cellIdx]++
                    if (yC >= 238) {
                        zebraOver[cellIdx]++
                    }
                }

                if (computePeaking) {
                    val idxL = rowBase + (col - gradOffset) * yPixelStride
                    val idxR = rowBase + (col + gradOffset) * yPixelStride
                    val idxU = (row - gradOffset) * yRowStride + col * yPixelStride
                    val idxD = (row + gradOffset) * yRowStride + col * yPixelStride
                    if (idxL >= 0 && idxR < yCapacity && idxU >= 0 && idxD < yCapacity) {
                        val yL = yBuffer.get(idxL).toInt() and 0xFF
                        val yR = yBuffer.get(idxR).toInt() and 0xFF
                        val yU = yBuffer.get(idxU).toInt() and 0xFF
                        val yD = yBuffer.get(idxD).toInt() and 0xFF
                        val grad = (abs(yR - yL) + abs(yD - yU)).coerceAtMost(127)
                        if (grad > (peakingStrength[cellIdx].toInt() and 0xFF)) {
                            peakingStrength[cellIdx] = grad.toByte()
                        }
                    }
                }
            }
        }

        val zebraMask = ByteArray(totalCells)
        if (computeZebra) {
            for (i in 0 until totalCells) {
                val tot = zebraTotal[i]
                if (tot > 0 && zebraOver[i] * 4 >= tot) {
                    zebraMask[i] = 1
                }
            }
        }

        return ViewfinderOverlayData(
            cols = cols,
            rows = rows,
            peakingStrength = peakingStrength,
            zebraMask = zebraMask,
            frameId = ++overlayFrameCounter
        )
    }

    /**
     * Generates a compact, screen-aligned frosted-blurred live backdrop bitmap from the preview
     * YUV frame for iOS-style translucent popup glass panels.
     */
    @Synchronized
    fun generateBlurredBackdrop(
        image: Image,
        sensorOrientation: Int,
        isFront: Boolean
    ): ImageBitmap? {
        val planes = image.planes
        if (planes.size < 3) return null

        val yBuffer = planes[0].buffer
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer
        val yRowStride = planes[0].rowStride
        val yPixelStride = planes[0].pixelStride
        val uRowStride = planes[1].rowStride
        val uPixelStride = planes[1].pixelStride
        val vRowStride = planes[2].rowStride
        val vPixelStride = planes[2].pixelStride
        val yCap = yBuffer.remaining()
        val uCap = uBuffer.remaining()
        val vCap = vBuffer.remaining()

        val srcW = image.width
        val srcH = image.height
        if (srcW < 8 || srcH < 8) return null

        val normOrient = ((sensorOrientation % 360) + 360) % 360
        val outW = if (normOrient == 90 || normOrient == 270) 72 else 96
        val outH = if (normOrient == 90 || normOrient == 270) 96 else 72
        val pixels = IntArray(outW * outH)

        for (oy in 0 until outH) {
            val sy = oy.toFloat() / outH
            for (ox in 0 until outW) {
                val sx = ox.toFloat() / outW
                val rx: Float
                val ry: Float
                when (normOrient) {
                    90 -> {
                        rx = sy
                        ry = if (isFront) sx else 1f - sx
                    }
                    270 -> {
                        rx = 1f - sy
                        ry = if (isFront) 1f - sx else sx
                    }
                    180 -> {
                        rx = if (isFront) sx else 1f - sx
                        ry = 1f - sy
                    }
                    else -> {
                        rx = if (isFront) 1f - sx else sx
                        ry = sy
                    }
                }
                val col = (rx * srcW).toInt().coerceIn(0, srcW - 1)
                val row = (ry * srcH).toInt().coerceIn(0, srcH - 1)
                val yIdx = row * yRowStride + col * yPixelStride
                val uvIdx = (row / 2) * uRowStride + (col / 2) * uPixelStride
                val vvIdx = (row / 2) * vRowStride + (col / 2) * vPixelStride

                if (yIdx < yCap && uvIdx < uCap && vvIdx < vCap) {
                    val y = (yBuffer.get(yIdx).toInt() and 0xFF) - 16
                    val u = (uBuffer.get(uvIdx).toInt() and 0xFF) - 128
                    val v = (vBuffer.get(vvIdx).toInt() and 0xFF) - 128
                    val r = (1.164f * y + 1.596f * v).toInt().coerceIn(0, 255)
                    val g = (1.164f * y - 0.813f * v - 0.391f * u).toInt().coerceIn(0, 255)
                    val b = (1.164f * y + 2.018f * u).toInt().coerceIn(0, 255)
                    pixels[oy * outW + ox] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
        }

        // Fast 2-pass separable box blur (radius = 5) for smooth iOS-like frosted glass
        val tmp = IntArray(outW * outH)
        boxBlurPass(pixels, tmp, outW, outH, radius = 5)
        boxBlurPass(pixels, tmp, outW, outH, radius = 4)

        return Bitmap.createBitmap(pixels, outW, outH, Bitmap.Config.ARGB_8888).asImageBitmap()
    }

    private fun boxBlurPass(src: IntArray, tmp: IntArray, w: Int, h: Int, radius: Int) {
        // Horizontal pass -> tmp
        for (y in 0 until h) {
            val rowOff = y * w
            for (x in 0 until w) {
                var rSum = 0
                var gSum = 0
                var bSum = 0
                var count = 0
                val x0 = (x - radius).coerceAtLeast(0)
                val x1 = (x + radius).coerceAtMost(w - 1)
                for (kx in x0..x1) {
                    val p = src[rowOff + kx]
                    rSum += (p shr 16) and 0xFF
                    gSum += (p shr 8) and 0xFF
                    bSum += p and 0xFF
                    count++
                }
                tmp[rowOff + x] = (0xFF shl 24) or ((rSum / count) shl 16) or ((gSum / count) shl 8) or (bSum / count)
            }
        }
        // Vertical pass -> src
        for (x in 0 until w) {
            for (y in 0 until h) {
                var rSum = 0
                var gSum = 0
                var bSum = 0
                var count = 0
                val y0 = (y - radius).coerceAtLeast(0)
                val y1 = (y + radius).coerceAtMost(h - 1)
                for (ky in y0..y1) {
                    val p = tmp[ky * w + x]
                    rSum += (p shr 16) and 0xFF
                    gSum += (p shr 8) and 0xFF
                    bSum += p and 0xFF
                    count++
                }
                src[y * w + x] = (0xFF shl 24) or ((rSum / count) shl 16) or ((gSum / count) shl 8) or (bSum / count)
            }
        }
    }
}

