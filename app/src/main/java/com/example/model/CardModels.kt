package com.example.model

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import kotlin.math.abs

/**
 * Image quality measurements computed from luminance distribution and Laplacian variance.
 */
data class ImageQuality(
    val sharpness: Float,
    val brightness: Float,
    val contrast: Float
)

/**
 * Extracted visual region metadata.
 */
data class VisualRegion(
    val id: Int,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val area: Int,
    val areaRatio: Float,
    val meanBrightness: Float,
    val contrast: Float
)

/**
 * Visual layer representing a discrete card visual element with its own cropped bitmap.
 */
data class VisualLayer(
    val id: Int,
    val type: String = "visual_image",
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val zIndex: Int,
    val bitmap: Bitmap
)

/**
 * In-place editable text patch directly tapped on the card.
 * Seamlessly covers ONLY the specific old word using sampled background color and renders
 * the new text matching the card's original color, exact font size, and style.
 */
data class InPlaceTextPatch(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val xRatio: Float, // Center X ratio (0.0 to 1.0)
    val yRatio: Float, // Center Y ratio (0.0 to 1.0)
    val fontHeightPx: Float = 26f, // Exact text height in bitmap pixels
    val maskWidthPx: Float = 100f, // Exact width to erase old word
    val maskHeightPx: Float = 32f, // Exact height to erase old word
    val textColorHex: String = "#0F172A",
    val bgColorHex: String = "#FFFFFF",
    val isBold: Boolean = true,
    val fontType: String = "Serif" // "Serif" (Classic Bengali), "Sans" (Modern Clean), "Heading" (Heavy Display)
)

/**
 * Four corners in normalized coordinates (0.0 to 1.0) for manual perspective correction.
 */
data class CardCorners(
    val topLeft: Offset = Offset(0.05f, 0.05f),
    val topRight: Offset = Offset(0.95f, 0.05f),
    val bottomRight: Offset = Offset(0.95f, 0.95f),
    val bottomLeft: Offset = Offset(0.05f, 0.95f)
) {
    /**
     * Shoelace formula for polygon area.
     * Minimum valid area ratio is 0.08 per user specification.
     */
    fun calculateAreaRatio(): Float {
        val p0 = topLeft
        val p1 = topRight
        val p2 = bottomRight
        val p3 = bottomLeft

        val area = abs(
            (p0.x * p1.y) + (p1.x * p2.y) + (p2.x * p3.y) + (p3.x * p0.y) -
                    (p1.x * p0.y) - (p2.x * p1.y) - (p3.x * p2.y) - (p0.x * p3.y)
        ) / 2f

        return area
    }

    fun areCornersReasonable(): Boolean {
        return calculateAreaRatio() >= 0.08f && isConvex()
    }

    /**
     * Verifies that the quadrilateral is convex (all cross products have the same sign).
     */
    fun isConvex(): Boolean {
        val points = listOf(topLeft, topRight, bottomRight, bottomLeft)
        var sign = 0
        for (i in 0 until 4) {
            val p1 = points[i]
            val p2 = points[(i + 1) % 4]
            val p3 = points[(i + 2) % 4]

            val cross = (p2.x - p1.x) * (p3.y - p2.y) - (p2.y - p1.y) * (p3.x - p2.x)
            if (cross != 0f) {
                val currentSign = if (cross > 0f) 1 else -1
                if (sign == 0) {
                    sign = currentSign
                } else if (sign != currentSign) {
                    return false
                }
            }
        }
        return true
    }
}

/**
 * Complete analysis result for a processed card.
 */
data class CardAnalysis(
    val width: Int,
    val height: Int,
    val aspectRatio: Float,
    val orientation: String,
    val perspectiveFixed: Boolean,
    val manualCornersUsed: Boolean,
    val originalWidth: Int,
    val originalHeight: Int,
    val processingWidth: Int,
    val processingHeight: Int,
    val wasResized: Boolean,
    val quality: ImageQuality,
    val foregroundRatio: Float,
    val visualRegionCount: Int,
    val layerCount: Int,
    val cloneConfidence: Float,
    val reconstructionMode: String,
    val fallbackUsed: Boolean,
    val pixelSimilarity: Float,
    val layers: List<VisualLayer> = emptyList()
)

/**
 * Single card batch item for multi-card processing.
 */
data class BatchCardItem(
    val id: String,
    val uri: Uri,
    val originalBitmap: Bitmap? = null,
    val reconstructedBitmap: Bitmap? = null,
    val analysis: CardAnalysis? = null,
    val status: BatchStatus = BatchStatus.PENDING,
    val errorMessage: String? = null
)

enum class BatchStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    ERROR
}

sealed interface ProcessingUiState {
    data object Idle : ProcessingUiState
    data class Processing(val stepMessage: String, val progress: Float = 0f) : ProcessingUiState
    data class Success(val analysis: CardAnalysis, val reconstructedBitmap: Bitmap) : ProcessingUiState
    data class Error(val message: String) : ProcessingUiState
}
