package app.hoyu.shot.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Memory-safe 3-frame Exposure Fusion HDR processor with:
 * 1. Median Threshold Bitmap (MTB) alignment to cancel handheld shake.
 * 2. Brightness-normalized deghosting anchored on the 0 EV reference frame.
 * 3. Row-streaming Mertens-inspired well-exposedness fusion.
 */
object HdrProcessor {

    private data class LumaSample(
        val width: Int,
        val height: Int,
        val luma: IntArray,
        val median: Int,
        val mean: Float,
        val scaleX: Float,
        val scaleY: Float
    )

    fun mergeHdrInPlace(
        refBitmap: Bitmap,
        underBytes: ByteArray,
        overBytes: ByteArray
    ) {
        if (!refBitmap.isMutable) return
        val width = refBitmap.width
        val height = refBitmap.height
        if (width < 8 || height < 8) return

        val decodeOpts = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val underBitmap = BitmapFactory.decodeByteArray(underBytes, 0, underBytes.size, decodeOpts)
        val overBitmap = BitmapFactory.decodeByteArray(overBytes, 0, overBytes.size, decodeOpts)

        if (underBitmap == null || overBitmap == null ||
            underBitmap.width != width || underBitmap.height != height ||
            overBitmap.width != width || overBitmap.height != height
        ) {
            underBitmap?.recycle()
            overBitmap?.recycle()
            return
        }

        try {
            // 1. Sample ~256px-wide luma grids for fast MTB alignment & brightness normalization
            val refSample = sampleLumaGrid(refBitmap)
            val underSample = sampleLumaGrid(underBitmap)
            val overSample = sampleLumaGrid(overBitmap)

            val (dxUnder, dyUnder) = findMtbShift(refSample, underSample)
            val (dxOver, dyOver) = findMtbShift(refSample, overSample)

            // Relative brightness multipliers to normalize -2 EV and +2 EV against 0 EV for deghosting
            val gainUnder = (refSample.mean / underSample.mean.coerceAtLeast(8f)).coerceIn(1.2f, 6.0f)
            val gainOver = (refSample.mean / overSample.mean.coerceAtLeast(8f)).coerceIn(0.16f, 0.85f)

            val refRow = IntArray(width)
            val underRow = IntArray(width)
            val overRow = IntArray(width)

            // Precompute Gaussian well-exposedness LUT (sigma = 0.24 around 0.5)
            val wellExposedLut = FloatArray(256) { i ->
                val d = (i / 255f) - 0.5f
                exp(-(d * d) / (2f * 0.24f * 0.24f))
            }

            // 2. Stream row-by-row fusion with alignment + deghosting
            for (y in 0 until height) {
                refBitmap.getPixels(refRow, 0, width, 0, y, width, 1)
                val yUnder = (y - dyUnder).coerceIn(0, height - 1)
                val yOver = (y - dyOver).coerceIn(0, height - 1)
                underBitmap.getPixels(underRow, 0, width, 0, yUnder, width, 1)
                overBitmap.getPixels(overRow, 0, width, 0, yOver, width, 1)

                for (x in 0 until width) {
                    val pxRef = refRow[x]
                    val pxUnder = underRow[(x - dxUnder).coerceIn(0, width - 1)]
                    val pxOver = overRow[(x - dxOver).coerceIn(0, width - 1)]

                    val r0 = (pxRef ushr 16) and 0xFF
                    val g0 = (pxRef ushr 8) and 0xFF
                    val b0 = pxRef and 0xFF
                    val y0 = ((77 * r0 + 150 * g0 + 29 * b0) ushr 8).coerceIn(0, 255)

                    val rU = (pxUnder ushr 16) and 0xFF
                    val gU = (pxUnder ushr 8) and 0xFF
                    val bU = pxUnder and 0xFF
                    val yU = ((77 * rU + 150 * gU + 29 * bU) ushr 8).coerceIn(0, 255)

                    val rO = (pxOver ushr 16) and 0xFF
                    val gO = (pxOver ushr 8) and 0xFF
                    val bO = pxOver and 0xFF
                    val yO = ((77 * rO + 150 * gO + 29 * bO) ushr 8).coerceIn(0, 255)

                    // Base well-exposedness weights
                    val wRef = max(0.10f, wellExposedLut[y0])
                    var wUnder = wellExposedLut[yU] * if (y0 > 180) 1.35f else 0.70f
                    var wOver = wellExposedLut[yO] * if (y0 < 85) 1.35f else 0.70f

                    // Deghosting: if moving subject diverges from 0 EV reference, drop bracket weight to 0
                    if (y0 in 18..236 && yU in 10..242) {
                        val diffU = abs(yU * gainUnder - y0)
                        if (diffU > 34f) {
                            wUnder *= ((54f - diffU) / 20f).coerceIn(0f, 1f)
                        }
                    }
                    if (y0 in 12..222 && yO in 14..242) {
                        val diffO = abs(yO * gainOver - y0)
                        if (diffO > 34f) {
                            wOver *= ((54f - diffO) / 20f).coerceIn(0f, 1f)
                        }
                    }

                    val sumW = wRef + wUnder + wOver
                    val rOut = ((r0 * wRef + rU * wUnder + rO * wOver) / sumW).roundToInt().coerceIn(0, 255)
                    val gOut = ((g0 * wRef + gU * wUnder + gO * wOver) / sumW).roundToInt().coerceIn(0, 255)
                    val bOut = ((b0 * wRef + bU * wUnder + bO * wOver) / sumW).roundToInt().coerceIn(0, 255)
                    refRow[x] = (pxRef and -0x1000000) or (rOut shl 16) or (gOut shl 8) or bOut
                }

                refBitmap.setPixels(refRow, 0, width, 0, y, width, 1)
            }
        } finally {
            underBitmap.recycle()
            overBitmap.recycle()
        }
    }

    private fun sampleLumaGrid(bitmap: Bitmap): LumaSample {
        val w = bitmap.width
        val h = bitmap.height
        val step = max(1, max(w, h) / 240)
        val sw = max(1, w / step)
        val sh = max(1, h / step)
        val luma = IntArray(sw * sh)
        val hist = IntArray(256)
        val row = IntArray(w)
        var sum = 0L

        for (sy in 0 until sh) {
            val y = (sy * step).coerceAtMost(h - 1)
            bitmap.getPixels(row, 0, w, 0, y, w, 1)
            val base = sy * sw
            for (sx in 0 until sw) {
                val px = row[(sx * step).coerceAtMost(w - 1)]
                val r = (px ushr 16) and 0xFF
                val g = (px ushr 8) and 0xFF
                val b = px and 0xFF
                val yVal = ((77 * r + 150 * g + 29 * b) ushr 8).coerceIn(0, 255)
                luma[base + sx] = yVal
                hist[yVal]++
                sum += yVal
            }
        }

        val total = luma.size.coerceAtLeast(1)
        val target = total / 2
        var cum = 0
        var median = 128
        for (i in 0..255) {
            cum += hist[i]
            if (cum >= target) {
                median = i
                break
            }
        }

        return LumaSample(
            width = sw,
            height = sh,
            luma = luma,
            median = median,
            mean = sum.toFloat() / total.toFloat(),
            scaleX = w.toFloat() / sw.toFloat(),
            scaleY = h.toFloat() / sh.toFloat()
        )
    }

    private fun findMtbShift(ref: LumaSample, target: LumaSample): Pair<Int, Int> {
        val w = minOf(ref.width, target.width)
        val h = minOf(ref.height, target.height)
        if (w < 16 || h < 16) return 0 to 0

        val maxShift = 10
        var bestDx = 0
        var bestDy = 0
        var bestErr = Int.MAX_VALUE

        // Coarse search (step 2) then fine refinement (step 1) around best
        for (dy in -maxShift..maxShift step 2) {
            for (dx in -maxShift..maxShift step 2) {
                val err = mtbMismatchScore(ref, target, w, h, dx, dy)
                if (err < bestErr) {
                    bestErr = err
                    bestDx = dx
                    bestDy = dy
                }
            }
        }

        var refinedDx = bestDx
        var refinedDy = bestDy
        for (dy in (bestDy - 1)..(bestDy + 1)) {
            for (dx in (bestDx - 1)..(bestDx + 1)) {
                val err = mtbMismatchScore(ref, target, w, h, dx, dy)
                if (err < bestErr) {
                    bestErr = err
                    refinedDx = dx
                    refinedDy = dy
                }
            }
        }

        return (refinedDx * ref.scaleX).roundToInt() to (refinedDy * ref.scaleY).roundToInt()
    }

    private fun mtbMismatchScore(
        ref: LumaSample,
        target: LumaSample,
        w: Int,
        h: Int,
        dx: Int,
        dy: Int
    ): Int {
        val margin = 12
        val xStart = margin
        val xEnd = w - margin
        val yStart = margin
        val yEnd = h - margin
        if (xEnd <= xStart || yEnd <= yStart) return 0

        val refMed = ref.median
        val tgtMed = target.median
        var err = 0

        for (y in yStart until yEnd) {
            val ty = (y - dy).coerceIn(0, h - 1)
            val rRow = y * ref.width
            val tRow = ty * target.width
            for (x in xStart until xEnd) {
                val rVal = ref.luma[rRow + x]
                // Exclude pixels very close to median to prevent noise flipping
                if (abs(rVal - refMed) <= 4) continue
                val tx = (x - dx).coerceIn(0, w - 1)
                val tVal = target.luma[tRow + tx]
                if (abs(tVal - tgtMed) <= 4) continue
                if ((rVal > refMed) != (tVal > tgtMed)) {
                    err++
                }
            }
        }
        return err
    }
}
