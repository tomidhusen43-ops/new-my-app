package com.example.ui.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.CardExporter
import com.example.engine.CardProcessingEngine
import com.example.model.CardAnalysis
import com.example.model.EditableCardText
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
    var showTextEditor by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    // Live adjustable parameters for fine positioning
    var baseCompositeBitmap by remember(reconstructedBitmap) { mutableStateOf(reconstructedBitmap) }
    var currentScale by remember { mutableFloatStateOf(0.96f) }
    var currentOffsetX by remember { mutableFloatStateOf(0f) }
    var currentOffsetY by remember { mutableFloatStateOf(0f) }

    // PDF-style In-Place Text Overlays (e.g. ZILZAL, phone corrections)
    val textOverlayList = remember {
        mutableStateListOf<EditableCardText>().apply {
            addAll(CardProcessingEngine.getDefaultDetectedTexts())
        }
    }

    var editingTextItem by remember { mutableStateOf<EditableCardText?>(null) }

    // Final rendered bitmap combining base composite + text edits
    val finalDisplayBitmap = remember(baseCompositeBitmap, textOverlayList.map { it.text + it.isVisible + it.colorHex + it.fontSizeSp + it.xRatio + it.yRatio }) {
        CardProcessingEngine.renderCardWithTextEdits(baseCompositeBitmap, textOverlayList)
    }

    val tabs = listOf("Reconstructed", "Extracted Layer", "Original Card", "Compare")

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

    // Modal Dialog to edit a specific text item
    editingTextItem?.let { currentItem ->
        var tempText by remember { mutableStateOf(currentItem.text) }
        var tempSize by remember { mutableFloatStateOf(currentItem.fontSizeSp) }
        var tempColor by remember { mutableStateOf(currentItem.colorHex) }
        var tempX by remember { mutableFloatStateOf(currentItem.xRatio) }
        var tempY by remember { mutableFloatStateOf(currentItem.yRatio) }

        AlertDialog(
            onDismissRequest = { editingTextItem = null },
            title = {
                Text(text = "Edit Card Text", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Notice: ভুল বানান মুছে সঠিক বানান দিন (যেমন: ZILZAL)।",
                        fontSize = 11.sp,
                        color = AccentCyan
                    )

                    OutlinedTextField(
                        value = tempText,
                        onValueChange = { tempText = it },
                        label = { Text("Text Content") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Font Size: ${tempSize.toInt()} sp", fontSize = 11.sp, color = TextSecondary)
                    Slider(
                        value = tempSize,
                        onValueChange = { tempSize = it },
                        valueRange = 12f..48f,
                        colors = SliderDefaults.colors(thumbColor = AccentCyan, activeTrackColor = AccentCyan)
                    )

                    Text("Position (X - Horizontal): ${(tempX * 100).toInt()}%", fontSize = 11.sp, color = TextSecondary)
                    Slider(
                        value = tempX,
                        onValueChange = { tempX = it },
                        valueRange = 0.05f..0.95f
                    )

                    Text("Position (Y - Vertical): ${(tempY * 100).toInt()}%", fontSize = 11.sp, color = TextSecondary)
                    Slider(
                        value = tempY,
                        onValueChange = { tempY = it },
                        valueRange = 0.05f..0.95f
                    )

                    Text("Color Preset:", fontSize = 11.sp, color = TextSecondary)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val colorPresets = listOf("#0F172A", "#DC2626", "#2563EB", "#059669", "#D97706")
                        colorPresets.forEach { hex ->
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(android.graphics.Color.parseColor(hex)))
                                    .border(
                                        width = if (tempColor == hex) 2.5.dp else 1.dp,
                                        color = if (tempColor == hex) Color.White else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { tempColor = hex }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val idx = textOverlayList.indexOfFirst { it.id == currentItem.id }
                        if (idx >= 0) {
                            textOverlayList[idx] = currentItem.copy(
                                text = tempText,
                                fontSizeSp = tempSize,
                                colorHex = tempColor,
                                xRatio = tempX,
                                yRatio = tempY,
                                isVisible = true
                            )
                        }
                        editingTextItem = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary)
                ) {
                    Text("Apply Changes")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingTextItem = null }) {
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
                            text = "${finalDisplayBitmap.width} × ${finalDisplayBitmap.height} px • ${analysis.orientation}",
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
                    // PDF In-Place Text Editor Button
                    IconButton(
                        onClick = { showTextEditor = !showTextEditor },
                        modifier = Modifier.testTag("result_text_editor_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.EditNote,
                            contentDescription = "Text Editor",
                            tint = if (showTextEditor) AccentCyan else TextSecondary
                        )
                    }

                    // Positioning Adjusters Button
                    IconButton(
                        onClick = { showAdjusters = !showAdjusters },
                        modifier = Modifier.testTag("result_adjust_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Adjust",
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
                // PDF-Style In-Place Text Editor Panel
                AnimatedVisibility(visible = showTextEditor) {
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
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.EditNote, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "PDF Text Editor",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = AccentCyan
                                    )
                                }
                                FilledTonalButton(
                                    onClick = {
                                        val newText = EditableCardText(
                                            label = "New Label",
                                            text = "New Text",
                                            xRatio = 0.5f,
                                            yRatio = 0.5f,
                                            isVisible = true
                                        )
                                        textOverlayList.add(newText)
                                        editingTextItem = newText
                                    }
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Add Text", fontSize = 11.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Notice: যেকোনো লেখার ওপর ট্যাপ করে বানান বা নম্বর ঠিক করুন (যেমন: ZILZAL)।",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // List of text overlay items
                            textOverlayList.forEachIndexed { index, item ->
                                Surface(
                                    color = DarkSurface,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable { editingTextItem = item }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.label,
                                                fontSize = 11.sp,
                                                color = AccentCyan,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = item.text,
                                                fontSize = 13.sp,
                                                color = TextPrimary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = {
                                                    textOverlayList[index] = item.copy(isVisible = !item.isVisible)
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = if (item.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                    contentDescription = "Toggle Visibility",
                                                    tint = if (item.isVisible) AccentGreen else TextSecondary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }

                                            IconButton(
                                                onClick = { editingTextItem = item }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.EditNote,
                                                    contentDescription = "Edit Text",
                                                    tint = AccentCyan,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }

                                            if (index >= 3) {
                                                IconButton(
                                                    onClick = { textOverlayList.removeAt(index) }
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Delete",
                                                        tint = Color.Red.copy(alpha = 0.8f),
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Interactive Position & Size Fine-Tuning Panel
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
                                    text = "Layout & Scale Adjustments",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentCyan
                                )
                                OutlinedButton(
                                    onClick = {
                                        currentScale = 0.96f
                                        currentOffsetX = 0f
                                        currentOffsetY = 0f
                                        recomputeBaseComposite()
                                    }
                                ) {
                                    Text("Reset", fontSize = 11.sp)
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
                                valueRange = 0.70f..1.30f,
                                colors = SliderDefaults.colors(thumbColor = AccentCyan, activeTrackColor = AccentCyan)
                            )

                            Text("Vertical Shift: ${currentOffsetY.toInt()} px", fontSize = 11.sp, color = TextSecondary)
                            Slider(
                                value = currentOffsetY,
                                onValueChange = {
                                    currentOffsetY = it
                                    recomputeBaseComposite()
                                },
                                valueRange = -150f..150f,
                                colors = SliderDefaults.colors(thumbColor = AccentPrimary, activeTrackColor = AccentPrimary)
                            )

                            Text("Horizontal Shift: ${currentOffsetX.toInt()} px", fontSize = 11.sp, color = TextSecondary)
                            Slider(
                                value = currentOffsetX,
                                onValueChange = {
                                    currentOffsetX = it
                                    recomputeBaseComposite()
                                },
                                valueRange = -150f..150f,
                                colors = SliderDefaults.colors(thumbColor = AccentPrimary, activeTrackColor = AccentPrimary)
                            )
                        }
                    }
                }

                when (selectedTab) {
                    0 -> {
                        // Reconstructed New Card
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
                                    Text(
                                        text = "Target Reconstructed Card",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Surface(color = AccentGreen.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp)) {
                                        Text(
                                            text = "Smart Diff Fit",
                                            color = AccentGreen,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                val imageBitmap = remember(finalDisplayBitmap) { finalDisplayBitmap.asImageBitmap() }
                                val aspect = finalDisplayBitmap.width.toFloat() / finalDisplayBitmap.height.toFloat()

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.Black)
                                        .border(1.dp, DarkBorder, RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        bitmap = imageBitmap,
                                        contentDescription = "New Card",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(aspect.coerceIn(0.5f, 2.5f))
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Info, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Notice: ২য় কার্ডে যে ডিজাইন আগে থেকেই ছিল তা ডাবল হয়নি, শুধু নতুন লেখা ও উপাদান যুক্ত হয়েছে।",
                                            color = TextSecondary,
                                            fontSize = 11.sp
                                        )
                                    }

                                    FilledTonalButton(
                                        onClick = { showTextEditor = true }
                                    ) {
                                        Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Edit Text", fontSize = 11.sp)
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
                                    text = "Extracted Layer (Transparent PNG)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentCyan
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Notice: মূল কার্ডের ব্যাকগ্রাউন্ড ফিল্টার করে সমস্ত লেখা ও লোগো আলাদা করা হয়েছে।",
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
                                    Text("Target Reconstructed Card", fontWeight = FontWeight.Bold, color = AccentCyan, fontSize = 12.sp)
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
                                        filename = "Card_Clone_HD_${System.currentTimeMillis()}",
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
