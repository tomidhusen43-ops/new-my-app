package com.example.ui.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.CardExporter
import com.example.engine.CardProcessingEngine
import com.example.model.CardAnalysis
import com.example.model.InPlaceTextPatch
import com.example.ui.dialogs.ExportDialog
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.AccentPrimary
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import kotlin.math.hypot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    originalBitmap: Bitmap,
    reconstructedBitmap: Bitmap,
    templateBitmap: Bitmap? = null,
    extractedForegroundBitmap: Bitmap? = null,
    analysis: CardAnalysis,
    onBack: () -> Unit,
    onRebuild: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showAdjusters by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    // 1:1 Scale by default to eliminate any doubling/ghosting!
    var baseCompositeBitmap by remember(reconstructedBitmap) { mutableStateOf(reconstructedBitmap) }
    var currentScale by remember { mutableFloatStateOf(1.0f) }
    var currentOffsetX by remember { mutableFloatStateOf(0f) }
    var currentOffsetY by remember { mutableFloatStateOf(0f) }

    // List of In-Place Text Patches created by directly tapping on the card
    val inPlacePatches = remember { mutableStateListOf<InPlaceTextPatch>() }
    var activeEditingPatch by remember { mutableStateOf<InPlaceTextPatch?>(null) }
    var isCreatingNewPatch by remember { mutableStateOf(false) }

    // Live rendered bitmap with seamless background in-painting + matched font text
    val finalDisplayBitmap = remember(baseCompositeBitmap, inPlacePatches.toList()) {
        CardProcessingEngine.renderCardWithInPlacePatches(baseCompositeBitmap, inPlacePatches)
    }

    val tabs = listOf("Card Preview", "Extracted Layer", "Original Card", "Compare")

    fun recomputeBaseComposite() {
        val targetTmpl = templateBitmap ?: CardProcessingEngine.createDefaultBlankTemplate(
            width = originalBitmap.width,
            height = originalBitmap.height
        )
        val fg = extractedForegroundBitmap ?: CardProcessingEngine.extractForegroundElements(originalBitmap, templateBitmap)

        coroutineScope.launch {
            val updated = CardProcessingEngine.compositeOntoTemplate(
                templateBitmap = targetTmpl,
                foregroundBitmap = fg,
                scaleMultiplier = currentScale,
                offsetX = currentOffsetX,
                offsetY = currentOffsetY
            )
            baseCompositeBitmap = updated
        }
    }

    if (showExportDialog) {
        ExportDialog(
            bitmap = finalDisplayBitmap,
            onDismiss = { showExportDialog = false }
        )
    }

    // Interactive In-Place Edit Dialog: Opened by tapping directly on the card
    activeEditingPatch?.let { currentPatch ->
        var tempText by remember { mutableStateOf(currentPatch.text) }
        var tempFontSize by remember { mutableFloatStateOf(currentPatch.fontSizeSp) }
        var tempColorHex by remember { mutableStateOf(currentPatch.textColorHex) }
        var tempWidthRatio by remember { mutableFloatStateOf(currentPatch.widthRatio) }
        var tempHeightRatio by remember { mutableFloatStateOf(currentPatch.heightRatio) }
        var tempIsBold by remember { mutableStateOf(currentPatch.isBold) }

        AlertDialog(
            onDismissRequest = {
                activeEditingPatch = null
                isCreatingNewPatch = false
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.TouchApp, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Tap-to-Edit Card Text", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Notice: কার্ডের আগের লেখা (তারিখ, নাম বা নম্বর) মুছে নতুন লেখা বসবে। কার্ডের ফন্ট ও রঙের সাথে মিলিয়ে নিখুঁতভাবে তৈরি হবে।",
                        fontSize = 11.sp,
                        color = AccentCyan,
                        lineHeight = 16.sp
                    )

                    OutlinedTextField(
                        value = tempText,
                        onValueChange = { tempText = it },
                        label = { Text("Enter New Text (নতুন লেখা)") },
                        placeholder = { Text("e.g. নতুন তারিখ, নাম বা নম্বর") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Font Size Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Font Size", fontSize = 12.sp, color = TextSecondary)
                        Text("${tempFontSize.toInt()} sp", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentCyan)
                    }
                    Slider(
                        value = tempFontSize,
                        onValueChange = { tempFontSize = it },
                        valueRange = 10f..44f,
                        colors = SliderDefaults.colors(thumbColor = AccentCyan, activeTrackColor = AccentCyan)
                    )

                    // Mask Coverage Width
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Erase Box Width", fontSize = 12.sp, color = TextSecondary)
                        Text("${(tempWidthRatio * 100).toInt()}%", fontSize = 12.sp, color = TextSecondary)
                    }
                    Slider(
                        value = tempWidthRatio,
                        onValueChange = { tempWidthRatio = it },
                        valueRange = 0.08f..0.70f
                    )

                    // Matched Color Palette
                    Text("Font Color (স্বয়ংক্রিয় কালার ম্যাচ):", fontSize = 12.sp, color = TextSecondary)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Include sampled color as first option
                        val colorPresets = listOf(
                            currentPatch.textColorHex,
                            "#0F172A", // Deep Navy Black
                            "#DC2626", // Bengali Card Bold Red
                            "#2563EB", // Royal Blue
                            "#059669", // Emerald Green
                            "#D97706"  // Gold / Bronze
                        ).distinct()

                        colorPresets.forEach { hex ->
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(Color(android.graphics.Color.parseColor(hex)))
                                    .border(
                                        width = if (tempColorHex.equals(hex, ignoreCase = true)) 2.5.dp else 1.dp,
                                        color = if (tempColorHex.equals(hex, ignoreCase = true)) Color.White else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { tempColorHex = hex }
                            )
                        }
                    }

                    // Bold / Regular Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Bold Font", fontSize = 12.sp, color = TextSecondary)
                        OutlinedButton(
                            onClick = { tempIsBold = !tempIsBold }
                        ) {
                            Text(if (tempIsBold) "Bold (বোল্ড)" else "Regular (নরমাল)", fontSize = 11.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val updated = currentPatch.copy(
                            text = tempText,
                            fontSizeSp = tempFontSize,
                            textColorHex = tempColorHex,
                            widthRatio = tempWidthRatio,
                            heightRatio = tempHeightRatio,
                            isBold = tempIsBold
                        )
                        if (isCreatingNewPatch) {
                            inPlacePatches.add(updated)
                        } else {
                            val idx = inPlacePatches.indexOfFirst { it.id == currentPatch.id }
                            if (idx >= 0) {
                                inPlacePatches[idx] = updated
                            }
                        }
                        activeEditingPatch = null
                        isCreatingNewPatch = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary)
                ) {
                    Text("Apply & Replace Text")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        activeEditingPatch = null
                        isCreatingNewPatch = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Card Studio",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${finalDisplayBitmap.width} × ${finalDisplayBitmap.height} px • 1:1 Pixel Match",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("result_back_button")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showAdjusters = !showAdjusters },
                        modifier = Modifier.testTag("result_adjust_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Fine-Tune Layout",
                            tint = if (showAdjusters) AccentCyan else TextSecondary
                        )
                    }

                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                CardExporter.exportAndShare(context, finalDisplayBitmap, "card_clone")
                            }
                        },
                        modifier = Modifier.testTag("result_share_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = TextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground)
                .padding(innerPadding)
        ) {
            // Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = DarkSurface,
                contentColor = AccentPrimary
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == index) AccentCyan else TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Interactive Position Fine-Tuning Panel
                AnimatedVisibility(visible = showAdjusters) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Fine Alignment (1:1 Pixel Match)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentCyan
                                )
                                OutlinedButton(
                                    onClick = {
                                        currentScale = 1.0f
                                        currentOffsetX = 0f
                                        currentOffsetY = 0f
                                        recomputeBaseComposite()
                                    }
                                ) {
                                    Text("Reset 1:1", fontSize = 11.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text("Scale: ${((currentScale * 100).toInt())}%", fontSize = 11.sp, color = TextSecondary)
                            Slider(
                                value = currentScale,
                                onValueChange = {
                                    currentScale = it
                                    recomputeBaseComposite()
                                },
                                valueRange = 0.80f..1.20f,
                                colors = SliderDefaults.colors(thumbColor = AccentCyan, activeTrackColor = AccentCyan)
                            )

                            Text("Vertical Shift: ${currentOffsetY.toInt()} px", fontSize = 11.sp, color = TextSecondary)
                            Slider(
                                value = currentOffsetY,
                                onValueChange = {
                                    currentOffsetY = it
                                    recomputeBaseComposite()
                                },
                                valueRange = -100f..100f,
                                colors = SliderDefaults.colors(thumbColor = AccentPrimary, activeTrackColor = AccentPrimary)
                            )

                            Text("Horizontal Shift: ${currentOffsetX.toInt()} px", fontSize = 11.sp, color = TextSecondary)
                            Slider(
                                value = currentOffsetX,
                                onValueChange = {
                                    currentOffsetX = it
                                    recomputeBaseComposite()
                                },
                                valueRange = -100f..100f,
                                colors = SliderDefaults.colors(thumbColor = AccentPrimary, activeTrackColor = AccentPrimary)
                            )
                        }
                    }
                }

                when (selectedTab) {
                    0 -> {
                        // ====================================================
                        // INTERACTIVE RECONSTRUCTED CARD (DIRECT TAP-TO-EDIT)
                        // ====================================================
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.TouchApp, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Tap on any text to edit",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                    }
                                    Surface(color = AccentGreen.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp)) {
                                        Text(
                                            text = "Zero Doubling",
                                            color = AccentGreen,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Notice: কার্ডের যেকোনো লেখার ওপর সরাসরি আঙুল দিয়ে টাচ করুন (যেমন: তারিখ, মোবাইল নম্বর বা নাম)—কার্ডের ফন্ট ও কালারের সাথে হুবহু মিলিয়ে লেখাটি পরিবর্তন হবে।",
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                val imageBitmap = remember(finalDisplayBitmap) { finalDisplayBitmap.asImageBitmap() }
                                val aspect = finalDisplayBitmap.width.toFloat() / finalDisplayBitmap.height.toFloat()

                                BoxWithConstraints(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.Black)
                                        .border(1.5.dp, DarkBorder, RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val containerWidthPx = constraints.maxWidth.toFloat()
                                    val containerHeightPx = containerWidthPx / aspect

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(aspect.coerceIn(0.5f, 2.5f))
                                            .pointerInput(finalDisplayBitmap) {
                                                detectTapGestures { offset ->
                                                    val tapXRatio = (offset.x / size.width).coerceIn(0.02f, 0.98f)
                                                    val tapYRatio = (offset.y / size.height).coerceIn(0.02f, 0.98f)

                                                    // Check if tap hit an existing patch
                                                    val existing = inPlacePatches.find { patch ->
                                                        val dist = hypot(patch.xRatio - tapXRatio, patch.yRatio - tapYRatio)
                                                        dist < 0.08f
                                                    }

                                                    if (existing != null) {
                                                        isCreatingNewPatch = false
                                                        activeEditingPatch = existing
                                                    } else {
                                                        // Sample color around the tap
                                                        val (textHex, bgHex) = CardProcessingEngine.sampleTextAndBackgroundColors(
                                                            finalDisplayBitmap,
                                                            tapXRatio,
                                                            tapYRatio
                                                        )

                                                        val newPatch = InPlaceTextPatch(
                                                            text = "",
                                                            xRatio = tapXRatio,
                                                            yRatio = tapYRatio,
                                                            widthRatio = 0.28f,
                                                            heightRatio = 0.07f,
                                                            fontSizeSp = 18f,
                                                            textColorHex = textHex,
                                                            bgColorHex = bgHex,
                                                            isBold = true
                                                        )
                                                        isCreatingNewPatch = true
                                                        activeEditingPatch = newPatch
                                                    }
                                                }
                                            }
                                    ) {
                                        Image(
                                            bitmap = imageBitmap,
                                            contentDescription = "New Reconstructed Card",
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.fillMaxSize()
                                        )

                                        // Subtle indicators on active edited areas
                                        inPlacePatches.forEach { patch ->
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                            ) {
                                                val posX = (patch.xRatio * containerWidthPx) - 16.dp.value
                                                val posY = (patch.yRatio * containerHeightPx) - 16.dp.value

                                                Box(
                                                    modifier = Modifier
                                                        .offset(x = posX.dp, y = posY.dp)
                                                        .size(32.dp)
                                                        .clip(CircleShape)
                                                        .background(AccentCyan.copy(alpha = 0.25f))
                                                        .border(1.dp, AccentCyan, CircleShape)
                                                        .clickable {
                                                            isCreatingNewPatch = false
                                                            activeEditingPatch = patch
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Edit,
                                                        contentDescription = null,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Active edits summary list
                                if (inPlacePatches.isNotEmpty()) {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = "Active Text Edits (${inPlacePatches.size}):",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AccentCyan
                                        )

                                        inPlacePatches.forEachIndexed { index, patch ->
                                            Surface(
                                                color = DarkSurfaceVariant,
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(12.dp)
                                                                .clip(CircleShape)
                                                                .background(Color(android.graphics.Color.parseColor(patch.textColorHex)))
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(
                                                            text = patch.text.ifBlank { "Untitled Edit" },
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = TextPrimary
                                                        )
                                                    }

                                                    Row {
                                                        IconButton(
                                                            onClick = {
                                                                isCreatingNewPatch = false
                                                                activeEditingPatch = patch
                                                            },
                                                            modifier = Modifier.size(28.dp)
                                                        ) {
                                                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = AccentCyan, modifier = Modifier.size(16.dp))
                                                        }

                                                        IconButton(
                                                            onClick = { inPlacePatches.removeAt(index) },
                                                            modifier = Modifier.size(28.dp)
                                                        ) {
                                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        // Extracted Transparent Layer
                        val fg = extractedForegroundBitmap ?: remember(originalBitmap) {
                            CardProcessingEngine.extractForegroundElements(originalBitmap, templateBitmap)
                        }
                        val fgBitmap = remember(fg) { fg.asImageBitmap() }
                        val aspect = fg.width.toFloat() / fg.height.toFloat()

                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "Extracted Foreground (Smart Subtracted)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentCyan
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Notice: ২য় কার্ডে যে ডিজাইন আগে থেকেই ছিল তা বাদ দিয়ে শুধুমাত্র অনুপস্থিত লেখা ও ছবিগুলো আলাদা করা হয়েছে।",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF1E293B))
                                        .border(1.dp, DarkBorder, RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        bitmap = fgBitmap,
                                        contentDescription = "Extracted Layer",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(aspect.coerceIn(0.5f, 2.5f))
                                    )
                                }
                            }
                        }
                    }

                    2 -> {
                        // Original Card
                        val origBitmap = remember(originalBitmap) { originalBitmap.asImageBitmap() }
                        val aspect = originalBitmap.width.toFloat() / originalBitmap.height.toFloat()

                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "Original Source Card",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.height(12.dp))

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.Black)
                                        .border(1.dp, DarkBorder, RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        bitmap = origBitmap,
                                        contentDescription = "Original Card",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(aspect.coerceIn(0.5f, 2.5f))
                                    )
                                }
                            }
                        }
                    }

                    3 -> {
                        // Side-by-Side Comparison
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Original Card", fontWeight = FontWeight.Bold, color = TextSecondary, fontSize = 12.sp)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Image(
                                        bitmap = remember(originalBitmap) { originalBitmap.asImageBitmap() },
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }

                            Card(
                                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Reconstructed Card (With In-Place Edits)", fontWeight = FontWeight.Bold, color = AccentCyan, fontSize = 12.sp)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Image(
                                        bitmap = remember(finalDisplayBitmap) { finalDisplayBitmap.asImageBitmap() },
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Action Bar
            Surface(
                color = DarkSurface,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = { showExportDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export HD", fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            isSaving = true
                            coroutineScope.launch {
                                try {
                                    val result = CardExporter.saveToGallery(
                                        context = context,
                                        bitmap = finalDisplayBitmap,
                                        filename = "Card_Clone_Seamless_${System.currentTimeMillis()}",
                                        format = "PNG",
                                        quality = 100
                                    )
                                    isSaving = false
                                    if (result.isSuccess) {
                                        Toast.makeText(context, "Card saved to gallery successfully!", Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(context, "Save failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                    }
                                } catch (e: Exception) {
                                    isSaving = false
                                    Toast.makeText(context, "Save failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                        modifier = Modifier.weight(1.4f)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Saving...")
                        } else {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save to Gallery", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
