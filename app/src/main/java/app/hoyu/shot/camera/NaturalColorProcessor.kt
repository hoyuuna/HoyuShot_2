package app.hoyu.shot.camera

import android.graphics.Bitmap
import app.hoyu.shot.model.ColorProfile
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Lightweight, memory-safe photographic color and tone engine for Camera2 JPEG output.
 *
 * Pipeline:
 * 1. Strided luma histogram analysis (black-point haze, contrast spread, scene key).
 * 2. Adaptive monotonic Filmic S-Curve LUT (midtone pop + natural shadow/highlight roll-off).
 * 3. Selective Vibrance with warm skin-tone protection (avoids neon clipping & orange faces).
 * 4. Streaming 3-row luminance-only micro-contrast (clarity) without color fringing.
 */
object NaturalColorProcessor {

    fun processBitmapInPlace(bitmap: Bitmap, profile: ColorProfile) {
        if (profile == ColorProfile.OFF || !bitmap.isMutable) return
        val width = bitmap.width
        val height = bitmap.height
        if (width < 4 || height < 4) return

        // 1. Analyze scene luminance distribution via strided sampling (~128x128 grid)
        val hist = IntArray(256)
        val rowBuffer = IntArray(width)
        val stepY = max(1, height / 128)
        val stepX = max(1, width / 128)
        var sampleCount = 0

        var sy = 0
        while (sy < height) {
            bitmap.getPixels(rowBuffer, 0, width, 0, sy, width, 1)
            var sx = 0
            while (sx < width) {
                val px = rowBuffer[sx]
                val r = (px ushr 16) and 0xFF
                val g = (px ushr 8) and 0xFF
                val b = px and 0xFF
                val y = ((77 * r + 150 * g + 29 * b) ushr 8).coerceIn(0, 255)
                hist[y]++
                sampleCount++
                sx += stepX
            }
            sy += stepY
        }
        if (sampleCount == 0) return

        val p02 = percentileFromHist(hist, sampleCount, 0.02f)
        val p50 = percentileFromHist(hist, sampleCount, 0.50f)
        val p98 = percentileFromHist(hist, sampleCount, 0.98f)
        val spread = (p98 - p02).coerceAtLeast(1)

        // 2. Build adaptive monotonic Filmic S-Curve LUT
        val blackAnchor = if (p02 > 6) ((p02 - 5f) * 0.42f).coerceIn(0f, 16f) else 0f
        val flatness = ((200f - spread) / 115f).coerceIn(0f, 1f)

        val sCurveAlpha = when (profile) {
            ColorProfile.NATURAL -> 0.062f + 0.036f * flatness
            ColorProfile.VIVID -> 0.095f + 0.042f * flatness
            ColorProfile.OFF -> 0f
        }
        val shadowLift = if (p50 < 95) ((95f - p50) / 95f) * 0.045f else 0f
        val vibranceStrength = when (profile) {
            ColorProfile.NATURAL -> 0.24f + 0.08f * flatness
            ColorProfile.VIVID -> 0.42f + 0.10f * flatness
            ColorProfile.OFF -> 0f
        }
        val clarityStrength = when (profile) {
            ColorProfile.NATURAL -> 0.14f
            ColorProfile.VIVID -> 0.22f
            ColorProfile.OFF -> 0f
        }

        val lumaLut = FloatArray(256)
        val rangeScale = 255f / (255f - blackAnchor).coerceAtLeast(1f)
        for (i in 0..255) {
            val anchored = ((i - blackAnchor) * rangeScale).coerceIn(0f, 255f)
            val x = anchored / 255f
            // Strictly monotonic S-curve (alpha < 0.159) + gentle shadow lift for dark scenes
            val lifted = (x + shadowLift * (1f - x) * (1f - x) * x * 4f).coerceIn(0f, 1f)
            val curved = (lifted - sCurveAlpha * sin(2.0 * PI * lifted).toFloat()).coerceIn(0f, 1f)
            lumaLut[i] = curved * 255f
        }

        // 3. Stream rows: Tone LUT + Selective Vibrance (skin-protected) + 3-row Luma Clarity
        var prevPixels = IntArray(width)
        var currPixels = IntArray(width)
        var nextPixels = IntArray(width)

        var prevY = FloatArray(width)
        var currY = FloatArray(width)
        var nextY = FloatArray(width)

        // Pre-process row 0
        bitmap.getPixels(currPixels, 0, width, 0, 0, width, 1)
        applyToneAndVibranceRow(currPixels, currY, width, lumaLut, vibranceStrength)
        System.arraycopy(currPixels, 0, prevPixels, 0, width)
        System.arraycopy(currY, 0, prevY, 0, width)

        val outRow = IntArray(width)

        for (y in 0 until height) {
            if (y + 1 < height) {
                bitmap.getPixels(nextPixels, 0, width, 0, y + 1, width, 1)
                applyToneAndVibranceRow(nextPixels, nextY, width, lumaLut, vibranceStrength)
            } else {
                System.arraycopy(currPixels, 0, nextPixels, 0, width)
                System.arraycopy(currY, 0, nextY, 0, width)
            }

            // Apply subtle luma-only micro-contrast (3x3 box blur residual, masked in deep shadows/highlights)
            for (x in 0 until width) {
                val px = currPixels[x]
                val yc = currY[x]
                val xm1 = if (x > 0) x - 1 else 0
                val xp1 = if (x + 1 < width) x + 1 else width - 1

                val blurY = (
                    (prevY[xm1] + 2f * prevY[x] + prevY[xp1]) +
                        2f * (currY[xm1] + 2f * yc + currY[xp1]) +
                        (nextY[xm1] + 2f * nextY[x] + nextY[xp1])
                    ) * 0.0625f

                // Attenuate clarity in very dark (<24) and very bright (>232) regions to prevent noise/clipping
                val mask = ((yc - 16f) / 36f).coerceIn(0f, 1f) * ((246f - yc) / 32f).coerceIn(0f, 1f)
                val deltaY = ((yc - blurY) * clarityStrength * mask).coerceIn(-16f, 16f)

                val r = (((px ushr 16) and 0xFF) + deltaY).roundToInt().coerceIn(0, 255)
                val g = (((px ushr 8) and 0xFF) + deltaY).roundToInt().coerceIn(0, 255)
                val b = ((px and 0xFF) + deltaY).roundToInt().coerceIn(0, 255)
                outRow[x] = (px and -0x1000000) or (r shl 16) or (g shl 8) or b
            }

            bitmap.setPixels(outRow, 0, width, 0, y, width, 1)

            // Rotate row buffers without reallocating
            val tmpPixels = prevPixels
            prevPixels = currPixels
            currPixels = nextPixels
            nextPixels = tmpPixels

            val tmpY = prevY
            prevY = currY
            currY = nextY
            nextY = tmpY
        }
    }

    private fun applyToneAndVibranceRow(
        pixels: IntArray,
        outY: FloatArray,
        width: Int,
        lumaLut: FloatArray,
        vibranceStrength: Float
    ) {
        for (x in 0 until width) {
            val px = pixels[x]
            val rIn = (px ushr 16) and 0xFF
            val gIn = (px ushr 8) and 0xFF
            val bIn = px and 0xFF

            val yOld = 0.299f * rIn + 0.587f * gIn + 0.114f * bIn
            val yIdx = yOld.roundToInt().coerceIn(0, 255)
            val yNew = lumaLut[yIdx]

            // Ratio scaling preserves true hue when adjusting tone
            val scale = if (yOld > 1.0f) yNew / yOld else 1.0f
            val rTone = (rIn * scale).coerceIn(0f, 255f)
            val gTone = (gIn * scale).coerceIn(0f, 255f)
            val bTone = (bIn * scale).coerceIn(0f, 255f)

            val maxC = max(rTone, max(gTone, bTone))
            val minC = min(rTone, min(gTone, bTone))
            val delta = maxC - minC

            var rOut = rTone
            var gOut = gTone
            var bOut = bTone

            if (maxC > 2f && delta > 1f) {
                val sat = (delta / maxC).coerceIn(0f, 1f)
                // Boost muted colors more than already-saturated colors
                var boost = vibranceStrength * (1f - sat).pow(1.4f)

                // Protect warm human skin tones (R > G > B in ~10..45 deg hue band)
                if (rTone > gTone && gTone > bTone && delta > 10f) {
                    val rg = rTone - gTone
                    val gb = gTone - bTone
                    val ratioRg = rg / delta
                    val ratioGb = gb / delta
                    if (ratioRg in 0.15f..0.72f && ratioGb >= 0.12f) {
                        val skinWeight = (1f - kotlin.math.abs(ratioRg - 0.42f) / 0.30f).coerceIn(0f, 1f)
                        boost *= (1f - 0.72f * skinWeight)
                    }
                }

                // Softly reduce vibrance near blown highlights so bright clouds stay white
                val highlightGuard = ((250f - maxC) / 35f).coerceIn(0.25f, 1f)
                val satGain = 1f + boost * highlightGuard

                rOut = (yNew + (rTone - yNew) * satGain).coerceIn(0f, 255f)
                gOut = (yNew + (gTone - yNew) * satGain).coerceIn(0f, 255f)
                bOut = (yNew + (bTone - yNew) * satGain).coerceIn(0f, 255f)
            }

            val rFinal = rOut.roundToInt().coerceIn(0, 255)
            val gFinal = gOut.roundToInt().coerceIn(0, 255)
            val bFinal = bOut.roundToInt().coerceIn(0, 255)
            outY[x] = 0.299f * rFinal + 0.587f * gFinal + 0.114f * bFinal
            pixels[x] = (px and -0x1000000) or (rFinal shl 16) or (gFinal shl 8) or bFinal
        }
    }

    private fun percentileFromHist(hist: IntArray, total: Int, fraction: Float): Int {
        val target = (total * fraction).roundToInt().coerceIn(0, total - 1)
        var cumulative = 0
        for (i in 0..255) {
            cumulative += hist[i]
            if (cumulative >= target) return i
        }
        return 255
    }
}
