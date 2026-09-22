package com.example.ui.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
    var isSaving by remember { mutableStateOf(false) }

    // Live adjustable parameters for fine positioning
    var currentReconstructed by remember(reconstructedBitmap) { mutableStateOf(reconstructedBitmap) }
    var currentScale by remember { mutableFloatStateOf(0.96f) }
    var currentOffsetX by remember { mutableFloatStateOf(0f) }
    var currentOffsetY by remember { mutableFloatStateOf(0f) }

    val tabs = listOf("নতুন কার্ড", "শুধু টেক্সট ও ফটো", "মূল কার্ড", "তুলনা")

    fun recomputeLiveComposite() {
        val targetTmpl = templateBitmap ?: CardProcessingEngine.createDefaultBlankTemplate(
            width = originalBitmap.width,
            height = originalBitmap.height
        )
        val fg = extractedForegroundBitmap ?: CardProcessingEngine.extractForegroundElements(originalBitmap)

        coroutineScope.launch {
            val updated = CardProcessingEngine.compositeOntoTemplate(
                templateBitmap = targetTmpl,
                foregroundBitmap = fg,
                scaleMultiplier = currentScale,
                offsetX = currentOffsetX,
                offsetY = currentOffsetY
            )
            currentReconstructed = updated
        }
    }

    if (showExportDialog) {
        ExportDialog(
            bitmap = currentReconstructed,
            onDismiss = { showExportDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "কার্ড ফলাফল",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${currentReconstructed.width} × ${currentReconstructed.height} px • ${analysis.orientation}",
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
                            contentDescription = "Adjust",
                            tint = if (showAdjusters) AccentCyan else TextSecondary
                        )
                    }

                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                CardExporter.exportAndShare(context, currentReconstructed, "card_clone")
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
                                    text = "টেক্সট ও ছবির পজিশন অ্যাডজাস্ট",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentCyan
                                )
                                OutlinedButton(
                                    onClick = {
                                        currentScale = 0.96f
                                        currentOffsetX = 0f
                                        currentOffsetY = 0f
                                        recomputeLiveComposite()
                                    }
                                ) {
                                    Text("রিসেট", fontSize = 11.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Scale slider
                            Text("সাইজ / স্কেল: ${((currentScale * 100).toInt())}%", fontSize = 11.sp, color = TextSecondary)
                            Slider(
                                value = currentScale,
                                onValueChange = {
                                    currentScale = it
                                    recomputeLiveComposite()
                                },
                                valueRange = 0.70f..1.30f,
                                colors = SliderDefaults.colors(thumbColor = AccentCyan, activeTrackColor = AccentCyan)
                            )

                            // Vertical offset
                            Text("উপরে / নিচে সরানো: ${currentOffsetY.toInt()} px", fontSize = 11.sp, color = TextSecondary)
                            Slider(
                                value = currentOffsetY,
                                onValueChange = {
                                    currentOffsetY = it
                                    recomputeLiveComposite()
                                },
                                valueRange = -150f..150f,
                                colors = SliderDefaults.colors(thumbColor = AccentPrimary, activeTrackColor = AccentPrimary)
                            )

                            // Horizontal offset
                            Text("বামে / ডানে সরানো: ${currentOffsetX.toInt()} px", fontSize = 11.sp, color = TextSecondary)
                            Slider(
                                value = currentOffsetX,
                                onValueChange = {
                                    currentOffsetX = it
                                    recomputeLiveComposite()
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
                                        text = "নতুন রিকনস্ট্রাক্ট করা কার্ড",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Surface(color = AccentGreen.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp)) {
                                        Text(
                                            text = "১০০% নিখুঁত ফিট",
                                            color = AccentGreen,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                val imageBitmap = remember(currentReconstructed) { currentReconstructed.asImageBitmap() }
                                val aspect = currentReconstructed.width.toFloat() / currentReconstructed.height.toFloat()

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
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Info, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "১ম কার্ডের সব নাম, লেখা, লোগো ও ছবি ২য় কার্ডে নিখুঁতভাবে বসানো হয়েছে।",
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }

                    1 -> {
                        // Extracted Transparent Layer
                        val fg = extractedForegroundBitmap ?: remember(originalBitmap) {
                            CardProcessingEngine.extractForegroundElements(originalBitmap)
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
                                    text = "আলাদা করা টেক্সট, লোগো ও ফটো লেয়ার (স্বচ্ছ ব্যাকগ্রাউন্ড)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentCyan
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "মূল কার্ডের ব্যাকগ্রাউন্ড বাদ দিয়ে সব লেখা ও ফটো পিএনজি স্বচ্ছ লেয়ার হিসেবে আলাদা করা হয়েছে।",
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
                                    text = "মূল কার্ড (Original Upload)",
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
                                    Text("মূল কার্ড", fontWeight = FontWeight.Bold, color = TextSecondary, fontSize = 12.sp)
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
                                    Text("নতুন ব্ল্যাঙ্ক ডিজাইনে তৈরি কার্ড", fontWeight = FontWeight.Bold, color = AccentCyan, fontSize = 12.sp)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Image(
                                        bitmap = remember(currentReconstructed) { currentReconstructed.asImageBitmap() },
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
                        Text("এক্সপোর্ট", fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            isSaving = true
                            coroutineScope.launch {
                                try {
                                    val result = CardExporter.saveToGallery(
                                        context = context,
                                        bitmap = currentReconstructed,
                                        filename = "Card_Clone_HD_${System.currentTimeMillis()}",
                                        format = "PNG",
                                        quality = 100
                                    )
                                    isSaving = false
                                    if (result.isSuccess) {
                                        Toast.makeText(context, "গ্যালারিতে কার্ড সেভ হয়েছে!", Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(context, "সেভ ব্যর্থ: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                    }
                                } catch (e: Exception) {
                                    isSaving = false
                                    Toast.makeText(context, "সেভ ব্যর্থ: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                        modifier = Modifier.weight(1.4f)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("সেভ হচ্ছে...")
                        } else {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("গ্যালারিতে সেভ", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
