package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.engine.CardProcessingEngine
import com.example.model.CardCorners
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentError
import com.example.ui.theme.AccentPrimary
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import kotlin.math.hypot
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CornerEditorScreen(
    bitmap: Bitmap,
    initialCorners: CardCorners = CardCorners(),
    onApplyCorners: (CardCorners) -> Unit,
    onCancel: () -> Unit
) {
    var corners by remember { mutableStateOf(initialCorners) }
    var activeCornerIndex by remember { mutableStateOf<Int?>(null) }

    val isValid = remember(corners) { corners.areCornersReasonable() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("কার্ডের কোণ নির্ধারণ করুন") },
                navigationIcon = {
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier.testTag("corner_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val auto = CardProcessingEngine.findCardContour(bitmap)
                            if (auto != null) corners = auto
                        },
                        modifier = Modifier.testTag("corner_auto_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Auto Detect"
                        )
                    }
                    IconButton(
                        onClick = { corners = CardCorners() },
                        modifier = Modifier.testTag("corner_reset_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkSurface
                )
            )
        },
        bottomBar = {
            Surface(
                color = DarkSurface,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    if (!isValid) {
                        Text(
                            text = "সতর্কতা: কোণগুলো একটি স্পষ্ট চারকোণা আকৃতি গঠন করতে হবে।",
                            color = AccentError,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    } else {
                        Text(
                            text = "কার্ডের ৪টি কোণে পয়েন্টগুলো টেনে বসিয়ে পারফেক্ট সাইজ দিন।",
                            color = AccentCyan,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("corner_cancel_action")
                        ) {
                            Text("বাতিল")
                        }

                        Button(
                            onClick = { onApplyCorners(corners) },
                            enabled = isValid,
                            modifier = Modifier
                                .weight(1.5f)
                                .testTag("corner_apply_action")
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("প্রয়োগ করুন")
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground)
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                val containerWidth = maxWidth.value
                val containerHeight = maxHeight.value

                // Compute aspect fit scale
                val imageAspect = bitmap.width.toFloat() / bitmap.height.toFloat()
                val containerAspect = containerWidth / containerHeight

                val displayWidth: Float
                val displayHeight: Float

                if (imageAspect > containerAspect) {
                    displayWidth = containerWidth
                    displayHeight = containerWidth / imageAspect
                } else {
                    displayHeight = containerHeight
                    displayWidth = containerHeight * imageAspect
                }

                val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }

                Box(
                    modifier = Modifier
                        .width(displayWidth.dp)
                        .height(displayHeight.dp)
                        .pointerInput(displayWidth, displayHeight) {
                            val handleRadiusPx = 36.dp.toPx()
                            detectDragGestures(
                                onDragStart = { offset ->
                                    val normX = (offset.x / (displayWidth * density)).coerceIn(0f, 1f)
                                    val normY = (offset.y / (displayHeight * density)).coerceIn(0f, 1f)

                                    val points = listOf(
                                        corners.topLeft,
                                        corners.topRight,
                                        corners.bottomRight,
                                        corners.bottomLeft
                                    )

                                    var closestIndex = -1
                                    var closestDist = Float.MAX_VALUE

                                    for (i in points.indices) {
                                        val p = points[i]
                                        val px = p.x * displayWidth * density
                                        val py = p.y * displayHeight * density
                                        val dist = hypot(offset.x - px, offset.y - py)
                                        if (dist < handleRadiusPx && dist < closestDist) {
                                            closestDist = dist
                                            closestIndex = i
                                        }
                                    }

                                    activeCornerIndex = if (closestIndex >= 0) closestIndex else null
                                },
                                onDrag = { change, _ ->
                                    activeCornerIndex?.let { index ->
                                        change.consume()
                                        val normX = (change.position.x / (displayWidth * density)).coerceIn(0f, 1f)
                                        val normY = (change.position.y / (displayHeight * density)).coerceIn(0f, 1f)
                                        val newPos = Offset(normX, normY)

                                        corners = when (index) {
                                            0 -> corners.copy(topLeft = newPos)
                                            1 -> corners.copy(topRight = newPos)
                                            2 -> corners.copy(bottomRight = newPos)
                                            3 -> corners.copy(bottomLeft = newPos)
                                            else -> corners
                                        }
                                    }
                                },
                                onDragEnd = {
                                    activeCornerIndex = null
                                },
                                onDragCancel = {
                                    activeCornerIndex = null
                                }
                            )
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        // Draw card bitmap
                        drawImage(
                            image = imageBitmap,
                            dstOffset = IntOffset.Zero,
                            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt())
                        )

                        // Scaled coordinates
                        val p0 = Offset(corners.topLeft.x * size.width, corners.topLeft.y * size.height)
                        val p1 = Offset(corners.topRight.x * size.width, corners.topRight.y * size.height)
                        val p2 = Offset(corners.bottomRight.x * size.width, corners.bottomRight.y * size.height)
                        val p3 = Offset(corners.bottomLeft.x * size.width, corners.bottomLeft.y * size.height)

                        val polyPath = Path().apply {
                            moveTo(p0.x, p0.y)
                            lineTo(p1.x, p1.y)
                            lineTo(p2.x, p2.y)
                            lineTo(p3.x, p3.y)
                            close()
                        }

                        // Fill translucent
                        drawPath(
                            path = polyPath,
                            color = (if (isValid) AccentPrimary else AccentError).copy(alpha = 0.22f)
                        )

                        // Outline
                        drawPath(
                            path = polyPath,
                            color = if (isValid) AccentCyan else AccentError,
                            style = Stroke(width = 3.dp.toPx())
                        )

                        // Corner handles
                        val points = listOf(
                            Pair("TL", p0),
                            Pair("TR", p1),
                            Pair("BR", p2),
                            Pair("BL", p3)
                        )

                        for ((idx, item) in points.withIndex()) {
                            val isActive = activeCornerIndex == idx
                            val radius = if (isActive) 16.dp.toPx() else 12.dp.toPx()

                            // Outer pulse ring
                            drawCircle(
                                color = if (isValid) AccentPrimary.copy(alpha = 0.4f) else AccentError.copy(alpha = 0.4f),
                                radius = radius + 6.dp.toPx(),
                                center = item.second
                            )

                            // Inner circle
                            drawCircle(
                                color = if (isValid) AccentCyan else AccentError,
                                radius = radius,
                                center = item.second
                            )

                            // Center dot
                            drawCircle(
                                color = Color.White,
                                radius = 4.dp.toPx(),
                                center = item.second
                            )
                        }
                    }
                }
            }
        }
    }
}
