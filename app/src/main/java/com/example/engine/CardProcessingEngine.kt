package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import com.example.model.CardAnalysis
import com.example.model.CardCorners
import com.example.model.ImageQuality
import com.example.model.VisualLayer
import com.example.model.VisualRegion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object CardProcessingEngine {

    private const val MAX_PROCESSING_SIZE = 2400

    data class PreparedImage(
        val bitmap: Bitmap,
        val originalWidth: Int,
        val originalHeight: Int,
        val processingWidth: Int,
        val processingHeight: Int,
        val wasResized: Boolean,
        val quality: ImageQuality
    )

    /**
     * Decodes and prepares an image from Uri with downscaling if needed and quality metrics calculation.
     */
    suspend fun prepareImage(context: Context, uri: Uri): PreparedImage = withContext(Dispatchers.IO) {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        } ?: throw IllegalArgumentException("ছবিটি পড়া যাচ্ছে না।")

        val origWidth = options.outWidth
        val origHeight = options.outHeight

        if (origWidth <= 0 || origHeight <= 0) {
            throw IllegalArgumentException("অকার্যকর ছবির ফাইল।")
        }

        val longestSide = max(origWidth, origHeight)
        var sampleSize = 1
        var wasResized = false

        if (longestSide > MAX_PROCESSING_SIZE) {
            wasResized = true
            sampleSize = max(1, longestSide / MAX_PROCESSING_SIZE)
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        var decoded: Bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: throw IllegalArgumentException("ছবিটি পড়া যাচ্ছে না।")

        // Further fine scale if longest side still exceeds MAX_PROCESSING_SIZE
        val currentLongest = max(decoded.width, decoded.height)
        if (currentLongest > MAX_PROCESSING_SIZE) {
            val scale = MAX_PROCESSING_SIZE.toFloat() / currentLongest.toFloat()
            val newW = max(1, (decoded.width * scale).toInt())
            val newH = max(1, (decoded.height * scale).toInt())
            val scaled = Bitmap.createScaledBitmap(decoded, newW, newH, true)
            if (scaled != decoded) {
                decoded.recycle()
                decoded = scaled
            }
            wasResized = true
        }

        val quality = calculateImageQuality(decoded)

        PreparedImage(
            bitmap = decoded,
            originalWidth = origWidth,
            originalHeight = origHeight,
            processingWidth = decoded.width,
            processingHeight = decoded.height,
            wasResized = wasResized,
            quality = quality
        )
    }

    /**
     * Calculates Laplacian variance (sharpness), mean brightness, and contrast std.
     */
    fun calculateImageQuality(bitmap: Bitmap): ImageQuality {
        val w = bitmap.width
        val h = bitmap.height

        // Downsample for quality analysis if too large for speed
        val sampleW = min(w, 400)
        val sampleH = min(h, 400)
        val sampleBitmap = if (sampleW != w || sampleH != h) {
            Bitmap.createScaledBitmap(bitmap, sampleW, sampleH, false)
        } else {
            bitmap
        }

        val pixels = IntArray(sampleW * sampleH)
        sampleBitmap.getPixels(pixels, 0, sampleW, 0, 0, sampleW, sampleH)

        var totalLum = 0.0
        val luminance = FloatArray(pixels.size)

        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val lum = 0.299f * r + 0.587f * g + 0.114f * b
            luminance[i] = lum
            totalLum += lum
        }

        val meanLum = totalLum / pixels.size

        var varLumSum = 0.0
        for (lum in luminance) {
            val diff = lum - meanLum
            varLumSum += diff * diff
        }
        val contrast = sqrt(varLumSum / pixels.size)

        // Laplacian variance
        // 3x3 kernel:
        // [ 0  1  0]
        // [ 1 -4  1]
        // [ 0  1  0]
        var laplacianSum = 0.0
        var laplacianCount = 0
        val laplacianValues = ArrayList<Double>()

        for (y in 1 until sampleH - 1) {
            val yOffset = y * sampleW
            for (x in 1 until sampleW - 1) {
                val center = luminance[yOffset + x]
                val top = luminance[(y - 1) * sampleW + x]
                val bottom = luminance[(y + 1) * sampleW + x]
                val left = luminance[yOffset + (x - 1)]
                val right = luminance[yOffset + (x + 1)]

                val lap = (top + bottom + left + right - 4f * center).toDouble()
                laplacianValues.add(lap)
                laplacianSum += lap
                laplacianCount++
            }
        }

        var sharpness = 0.0
        if (laplacianCount > 0) {
            val lapMean = laplacianSum / laplacianCount
            var lapVarSum = 0.0
            for (v in laplacianValues) {
                val d = v - lapMean
                lapVarSum += d * d
            }
            sharpness = lapVarSum / laplacianCount
        }

        if (sampleBitmap != bitmap) {
            sampleBitmap.recycle()
        }

        return ImageQuality(
            sharpness = ((sharpness * 100).toInt() / 100f).coerceAtLeast(0f),
            brightness = ((meanLum * 100).toInt() / 100f).coerceIn(0f, 255f),
            contrast = ((contrast * 100).toInt() / 100f).coerceAtLeast(0f)
        )
    }

    /**
     * Automatic card boundary detection. Finds candidate 4-corner polygon if card is on contrasting background.
     */
    fun findCardContour(bitmap: Bitmap): CardCorners? {
        val w = bitmap.width
        val h = bitmap.height
        val edgeW = min(w, 200)
        val edgeH = min(h, 200)
        val sample = Bitmap.createScaledBitmap(bitmap, edgeW, edgeH, false)
        val pixels = IntArray(edgeW * edgeH)
        sample.getPixels(pixels, 0, edgeW, 0, 0, edgeW, edgeH)

        // Find boundary edges
        var minX = edgeW
        var maxX = 0
        var minY = edgeH
        var maxY = 0

        // Corner background sample
        val bgPixel = pixels[0]
        val bgR = Color.red(bgPixel)
        val bgG = Color.green(bgPixel)
        val bgB = Color.blue(bgPixel)

        var cardPixelCount = 0
        for (y in 0 until edgeH) {
            for (x in 0 until edgeW) {
                val p = pixels[y * edgeW + x]
                val r = Color.red(p)
                val g = Color.green(p)
                val b = Color.blue(p)
                val diff = abs(r - bgR) + abs(g - bgG) + abs(b - bgB)
                if (diff > 45) {
                    cardPixelCount++
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }

        sample.recycle()

        // If card occupies a reasonable portion (15% to 95%)
        val total = edgeW * edgeH
        if (cardPixelCount > total * 0.15 && cardPixelCount < total * 0.95 && maxX > minX && maxY > minY) {
            val padX = max(0f, (maxX - minX) * 0.01f)
            val padY = max(0f, (maxY - minY) * 0.01f)

            val left = ((minX - padX) / edgeW).coerceIn(0.02f, 0.98f)
            val right = ((maxX + padX) / edgeW).coerceIn(0.02f, 0.98f)
            val top = ((minY - padY) / edgeH).coerceIn(0.02f, 0.98f)
            val bottom = ((maxY + padY) / edgeH).coerceIn(0.02f, 0.98f)

            return CardCorners(
                topLeft = Offset(left, top),
                topRight = Offset(right, top),
                bottomRight = Offset(right, bottom),
                bottomLeft = Offset(left, bottom)
            )
        }

        return null
    }

    /**
     * Warps the quadrilateral defined by [corners] into a rectangular perspective-corrected bitmap.
     */
    fun perspectiveCorrect(bitmap: Bitmap, corners: CardCorners): Bitmap {
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()

        val tl = corners.topLeft
        val tr = corners.topRight
        val br = corners.bottomRight
        val bl = corners.bottomLeft

        val pTlX = tl.x * w
        val pTlY = tl.y * h
        val pTrX = tr.x * w
        val pTrY = tr.y * h
        val pBrX = br.x * w
        val pBrY = br.y * h
        val pBlX = bl.x * w
        val pBlY = bl.y * h

        val widthA = hypot(pBrX - pBlX, pBrY - pBlY)
        val widthB = hypot(pTrX - pTlX, pTrY - pTlY)
        val heightA = hypot(pTrX - pBrX, pTrY - pBrY)
        val heightB = hypot(pTlX - pBlX, pTlY - pBlY)

        val targetWidth = max(widthA, widthB).toInt().coerceAtLeast(10)
        val targetHeight = max(heightA, heightB).toInt().coerceAtLeast(10)

        val src = floatArrayOf(
            pTlX, pTlY,
            pTrX, pTrY,
            pBrX, pBrY,
            pBlX, pBlY
        )

        val dst = floatArrayOf(
            0f, 0f,
            targetWidth.toFloat() - 1f, 0f,
            targetWidth.toFloat() - 1f, targetHeight.toFloat() - 1f,
            0f, targetHeight.toFloat() - 1f
        )

        val matrix = Matrix()
        matrix.setPolyToPoly(src, 0, dst, 0, 4)

        val corrected = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(corrected)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(bitmap, matrix, paint)

        return corrected
    }

    /**
     * Estimates card background color by sampling pixels from borders.
     */
    fun estimateBackground(bitmap: Bitmap): Int {
        val w = bitmap.width
        val h = bitmap.height
        val borderSize = max(2, min(20, (min(w, h) * 0.03f).toInt()))

        val rValues = ArrayList<Int>()
        val gValues = ArrayList<Int>()
        val bValues = ArrayList<Int>()

        // Sample top and bottom
        for (y in 0 until borderSize) {
            for (x in 0 until w step 4) {
                val pTop = bitmap.getPixel(x, y)
                val pBottom = bitmap.getPixel(x, h - 1 - y)
                rValues.add(Color.red(pTop))
                gValues.add(Color.green(pTop))
                bValues.add(Color.blue(pTop))
                rValues.add(Color.red(pBottom))
                gValues.add(Color.green(pBottom))
                bValues.add(Color.blue(pBottom))
            }
        }

        // Sample left and right
        for (x in 0 until borderSize) {
            for (y in borderSize until (h - borderSize) step 4) {
                val pLeft = bitmap.getPixel(x, y)
                val pRight = bitmap.getPixel(w - 1 - x, y)
                rValues.add(Color.red(pLeft))
                gValues.add(Color.green(pLeft))
                bValues.add(Color.blue(pLeft))
                rValues.add(Color.red(pRight))
                gValues.add(Color.green(pRight))
                bValues.add(Color.blue(pRight))
            }
        }

        rValues.sort()
        gValues.sort()
        bValues.sort()

        val medR = if (rValues.isNotEmpty()) rValues[rValues.size / 2] else 255
        val medG = if (gValues.isNotEmpty()) gValues[gValues.size / 2] else 255
        val medB = if (bValues.isNotEmpty()) bValues[bValues.size / 2] else 255

        return Color.rgb(medR, medG, medB)
    }

    /**
     * Checks if two boxes overlap or are adjacent with padding.
     */
    private fun boxesOverlap(a: Rect, b: Rect, padding: Int = 6): Boolean {
        val ax1 = a.left - padding
        val ay1 = a.top - padding
        val ax2 = a.right + padding
        val ay2 = a.bottom + padding

        val bx1 = b.left - padding
        val by1 = b.top - padding
        val bx2 = b.right + padding
        val by2 = b.bottom + padding

        return !(ax2 < bx1 || bx2 < ax1 || ay2 < by1 || by2 < ay1)
    }

    /**
     * Merges adjacent and overlapping bounding boxes into unified visual regions.
     */
    private fun mergeRegions(regions: List<Rect>): List<Rect> {
        if (regions.isEmpty()) return emptyList()

        var working = regions.toMutableList()
        var changed = true

        while (changed) {
            changed = false
            val result = mutableListOf<Rect>()
            val used = BooleanArray(working.size)

            for (i in working.indices) {
                if (used[i]) continue
                var current = working[i]

                for (j in i + 1 until working.size) {
                    if (used[j]) continue
                    val other = working[j]
                    if (boxesOverlap(current, other, padding = 6)) {
                        current = Rect(
                            min(current.left, other.left),
                            min(current.top, other.top),
                            max(current.right, other.right),
                            max(current.bottom, other.bottom)
                        )
                        used[j] = true
                        changed = true
                    }
                }
                result.add(current)
                used[i] = true
            }
            working = result
        }

        return working
    }

    /**
     * Extracts visual regions, foreground mask ratio, confidence, and layers from card bitmap.
     */
    fun extractVisualLayers(bitmap: Bitmap, bgColor: Int): Triple<List<VisualRegion>, List<VisualLayer>, Float> {
        val w = bitmap.width
        val h = bitmap.height
        val totalArea = w * h

        val bgR = Color.red(bgColor)
        val bgG = Color.green(bgColor)
        val bgB = Color.blue(bgColor)

        // Grid-based foreground scanning
        val gridStep = max(2, (min(w, h) / 300))
        val gridW = w / gridStep
        val gridH = h / gridStep

        var fgCount = 0
        val rawBoxes = ArrayList<Rect>()

        val fgThreshold = 22.0

        for (gy in 0 until gridH) {
            val y = gy * gridStep
            for (gx in 0 until gridW) {
                val x = gx * gridStep
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                val diff = sqrt(((r - bgR) * (r - bgR) + (g - bgG) * (g - bgG) + (b - bgB) * (b - bgB)).toDouble())
                if (diff > fgThreshold) {
                    fgCount++
                    rawBoxes.add(
                        Rect(
                            max(0, x - gridStep),
                            max(0, y - gridStep),
                            min(w, x + gridStep * 2),
                            min(h, y + gridStep * 2)
                        )
                    )
                }
            }
        }

        val foregroundRatio = if (gridW * gridH > 0) {
            (fgCount.toFloat() / (gridW * gridH).toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

        val mergedBoxes = mergeRegions(rawBoxes)
            .filter { box ->
                val boxArea = box.width() * box.height()
                val ratio = boxArea.toFloat() / totalArea.toFloat()
                ratio in 0.0008f..0.92f && box.width() >= 6 && box.height() >= 6
            }
            .sortedByDescending { it.width() * it.height() }

        val visualRegions = ArrayList<VisualRegion>()
        val visualLayers = ArrayList<VisualLayer>()

        for ((index, box) in mergedBoxes.withIndex()) {
            val pad = 6
            val x1 = max(0, box.left - pad)
            val y1 = max(0, box.top - pad)
            val x2 = min(w, box.right + pad)
            val y2 = min(h, box.bottom + pad)
            val cropW = x2 - x1
            val cropH = y2 - y1

            if (cropW <= 0 || cropH <= 0) continue

            val cropBitmap = Bitmap.createBitmap(bitmap, x1, y1, cropW, cropH)

            visualRegions.add(
                VisualRegion(
                    id = index,
                    x = x1,
                    y = y1,
                    width = cropW,
                    height = cropH,
                    area = cropW * cropH,
                    areaRatio = (cropW * cropH).toFloat() / totalArea.toFloat(),
                    meanBrightness = 128f,
                    contrast = 40f
                )
            )

            visualLayers.add(
                VisualLayer(
                    id = index,
                    type = "visual_image",
                    x = x1,
                    y = y1,
                    width = cropW,
                    height = cropH,
                    zIndex = index,
                    bitmap = cropBitmap
                )
            )
        }

        return Triple(visualRegions, visualLayers, foregroundRatio)
    }

    /**
     * Calculates conservative confidence score for segmentation.
     */
    fun calculateCloneConfidence(regionCount: Int, foregroundRatio: Float): Float {
        if (regionCount == 0) return 0.0f
        if (regionCount > 40) return 0.25f
        if (foregroundRatio > 0.92f) return 0.20f
        if (foregroundRatio < 0.01f) return 0.20f

        var confidence = 0.90f
        if (regionCount > 25) {
            confidence -= 0.35f
        } else if (regionCount > 15) {
            confidence -= 0.20f
        } else if (regionCount > 8) {
            confidence -= 0.10f
        }

        if (foregroundRatio > 0.85f) {
            confidence -= 0.20f
        } else if (foregroundRatio < 0.03f) {
            confidence -= 0.20f
        }

        return confidence.coerceIn(0.0f, 1.0f)
    }

    fun chooseReconstructionMode(confidence: Float, regionCount: Int): String {
        return if (confidence < 0.50f || regionCount > 40 || regionCount == 0) {
            "exact_visual_clone"
        } else {
            "visual_regions"
        }
    }

    /**
     * Compares pixel similarity between original and reconstructed image.
     */
    fun compareSimilarity(orig: Bitmap, recon: Bitmap): Float {
        val sampleSize = 64
        val s1 = Bitmap.createScaledBitmap(orig, sampleSize, sampleSize, false)
        val s2 = Bitmap.createScaledBitmap(recon, sampleSize, sampleSize, false)

        val p1 = IntArray(sampleSize * sampleSize)
        val p2 = IntArray(sampleSize * sampleSize)

        s1.getPixels(p1, 0, sampleSize, 0, 0, sampleSize, sampleSize)
        s2.getPixels(p2, 0, sampleSize, 0, 0, sampleSize, sampleSize)

        var totalDiff = 0.0
        val maxDiff = sampleSize * sampleSize * 3 * 255.0

        for (i in p1.indices) {
            val c1 = p1[i]
            val c2 = p2[i]
            val rDiff = abs(Color.red(c1) - Color.red(c2))
            val gDiff = abs(Color.green(c1) - Color.green(c2))
            val bDiff = abs(Color.blue(c1) - Color.blue(c2))
            totalDiff += (rDiff + gDiff + bDiff)
        }

        s1.recycle()
        s2.recycle()

        val similarity = (1.0 - (totalDiff / maxDiff)).coerceIn(0.0, 1.0)
        return ((similarity * 1000).toInt() / 1000f)
    }

    /**
     * Executes the full pipeline on a card:
     * 1. Perspective correction (auto or manual)
     * 2. Background estimation
     * 3. Visual layers extraction
     * 4. Smart reconstruction mode selection
     * 5. Reconstruction and Emergency fallback verification
     */
    suspend fun processCardPipeline(
        prepared: PreparedImage,
        manualCorners: CardCorners? = null
    ): Pair<CardAnalysis, Bitmap> = withContext(Dispatchers.Default) {
        val originalBitmap = prepared.bitmap

        // 1. Perspective correction
        val autoCorners = if (manualCorners == null) findCardContour(originalBitmap) else null
        val effectiveCorners = manualCorners ?: autoCorners

        val (perspectiveFixed, workingBitmap) = if (effectiveCorners != null && effectiveCorners.areCornersReasonable()) {
            Pair(true, perspectiveCorrect(originalBitmap, effectiveCorners))
        } else {
            Pair(false, originalBitmap.copy(Bitmap.Config.ARGB_8888, true))
        }

        // 2. Background Estimation
        val bgColor = estimateBackground(workingBitmap)

        // 3. Visual extraction
        val (regions, layers, fgRatio) = extractVisualLayers(workingBitmap, bgColor)

        // 4. Clone confidence & mode
        val confidence = calculateCloneConfidence(regions.size, fgRatio)
        val chosenMode = chooseReconstructionMode(confidence, regions.size)

        // 5. Reconstruction
        val w = workingBitmap.width
        val h = workingBitmap.height

        var reconstructedBitmap: Bitmap
        var finalMode = chosenMode
        var fallbackUsed = false

        if (chosenMode == "exact_visual_clone" || layers.isEmpty()) {
            reconstructedBitmap = workingBitmap.copy(Bitmap.Config.ARGB_8888, true)
            finalMode = "exact_visual_clone"
        } else {
            // Rebuild from layers on clean canvas
            val canvasBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(canvasBitmap)
            canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

            for (layer in layers.sortedBy { it.zIndex }) {
                canvas.drawBitmap(layer.bitmap, layer.x.toFloat(), layer.y.toFloat(), paint)
            }

            // Similarity safety check
            val similarity = compareSimilarity(workingBitmap, canvasBitmap)
            if (similarity < 0.60f) {
                // Emergency fallback to exact clone
                canvasBitmap.recycle()
                reconstructedBitmap = workingBitmap.copy(Bitmap.Config.ARGB_8888, true)
                finalMode = "exact_visual_clone"
                fallbackUsed = true
            } else {
                reconstructedBitmap = canvasBitmap
            }
        }

        val similarity = compareSimilarity(workingBitmap, reconstructedBitmap)

        val orientation = if (w > h) "landscape" else if (h > w) "portrait" else "square"
        val aspectRatio = ((w.toFloat() / h.toFloat()) * 1000).toInt() / 1000f

        val analysis = CardAnalysis(
            width = w,
            height = h,
            aspectRatio = aspectRatio,
            orientation = orientation,
            perspectiveFixed = perspectiveFixed,
            manualCornersUsed = manualCorners != null,
            originalWidth = prepared.originalWidth,
            originalHeight = prepared.originalHeight,
            processingWidth = prepared.processingWidth,
            processingHeight = prepared.processingHeight,
            wasResized = prepared.wasResized,
            quality = prepared.quality,
            foregroundRatio = ((fgRatio * 1000).toInt() / 1000f),
            visualRegionCount = regions.size,
            layerCount = layers.size,
            cloneConfidence = confidence,
            reconstructionMode = finalMode,
            fallbackUsed = fallbackUsed,
            pixelSimilarity = similarity,
            layers = layers
        )

        Pair(analysis, reconstructedBitmap)
    }
}
