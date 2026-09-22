package com.example.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.example.engine.CardExporter
import com.example.engine.CardProcessingEngine
import com.example.model.BatchCardItem
import com.example.model.BatchStatus
import com.example.model.CardAnalysis
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentError
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchScreen(
    onBack: () -> Unit,
    onInspectCard: (Bitmap, Bitmap, CardAnalysis) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val batchItems = remember { mutableStateListOf<BatchCardItem>() }
    var isProcessingAll by remember { mutableStateOf(false) }
    var currentProcessingIndex by remember { mutableStateOf(-1) }
    var overallProgress by remember { mutableFloatStateOf(0f) }

    val multiPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val maxAllowed = 20 - batchItems.size
            val toAdd = uris.take(maxAllowed)
            coroutineScope.launch {
                for (uri in toAdd) {
                    try {
                        val prepared = CardProcessingEngine.prepareImage(context, uri)
                        batchItems.add(
                            BatchCardItem(
                                id = UUID.randomUUID().toString(),
                                uri = uri,
                                originalBitmap = prepared.bitmap,
                                status = BatchStatus.PENDING
                            )
                        )
                    } catch (e: Exception) {
                        // skip failed decode
                    }
                }
            }
        }
    }

    fun startBatchProcessing() {
        if (batchItems.isEmpty() || isProcessingAll) return
        isProcessingAll = true
        coroutineScope.launch {
            val total = batchItems.size
            for (i in 0 until total) {
                val item = batchItems[i]
                if (item.status == BatchStatus.COMPLETED && item.reconstructedBitmap != null) {
                    overallProgress = (i + 1).toFloat() / total.toFloat()
                    continue
                }

                currentProcessingIndex = i
                batchItems[i] = item.copy(status = BatchStatus.PROCESSING)

                try {
                    val prepared = CardProcessingEngine.prepareImage(context, item.uri)
                    val (analysis, reconstructed) = CardProcessingEngine.processCardPipeline(prepared)

                    // Auto-save to gallery
                    CardExporter.saveToGallery(
                        context = context,
                        bitmap = reconstructed,
                        filename = "batch_card_${i + 1}_${System.currentTimeMillis()}"
                    )

                    batchItems[i] = item.copy(
                        originalBitmap = prepared.bitmap,
                        reconstructedBitmap = reconstructed,
                        analysis = analysis,
                        status = BatchStatus.COMPLETED
                    )
                } catch (e: Exception) {
                    batchItems[i] = item.copy(
                        status = BatchStatus.ERROR,
                        errorMessage = e.localizedMessage
                    )
                }

                overallProgress = (i + 1).toFloat() / total.toFloat()
            }
            isProcessingAll = false
            currentProcessingIndex = -1
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "ব্যাচ প্রসেসিং সম্পন্ন হয়েছে এবং গ্যালারিতে সেভ করা হয়েছে!", Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("ব্যাচ প্রসেসিং (Batch Mode)")
                        Text(
                            text = "${batchItems.size}/20 কার্ড নির্বাচিত",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("batch_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { multiPickerLauncher.launch("image/*") },
                        enabled = batchItems.size < 20 && !isProcessingAll,
                        modifier = Modifier.testTag("batch_add_more_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = "Add Cards"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkSurface
                )
            )
        },
        bottomBar = {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    if (isProcessingAll) {
                        Text(
                            text = "প্রসেসিং হচ্ছে: ${currentProcessingIndex + 1} / ${batchItems.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = AccentCyan
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { overallProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = AccentCyan,
                            trackColor = DarkSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { multiPickerLauncher.launch("image/*") },
                            enabled = !isProcessingAll && batchItems.size < 20,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("batch_pick_button")
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("যোগ করুন")
                        }

                        Button(
                            onClick = { startBatchProcessing() },
                            enabled = batchItems.isNotEmpty() && !isProcessingAll,
                            modifier = Modifier
                                .weight(1.4f)
                                .testTag("batch_start_button")
                        ) {
                            if (isProcessingAll) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("প্রসেসিং...")
                            } else {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("সব প্রসেস করুন (${batchItems.size})")
                            }
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
                .padding(innerPadding)
        ) {
            if (batchItems.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AddPhotoAlternate,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "কোনো কার্ড যুক্ত করা হয়নি",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "একসাথে সর্বোচ্চ ২০টি কার্ড সিলেক্ট করে নিমেষেই রিকনস্ট্রাক্ট করুন।",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = { multiPickerLauncher.launch("image/*") },
                        modifier = Modifier.testTag("batch_empty_add_button")
                    ) {
                        Text("কার্ড সিলেক্ট করুন")
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(batchItems, key = { _, item -> item.id }) { index, item ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                                .clickable(enabled = item.status == BatchStatus.COMPLETED) {
                                    if (item.originalBitmap != null && item.reconstructedBitmap != null && item.analysis != null) {
                                        onInspectCard(item.originalBitmap, item.reconstructedBitmap, item.analysis)
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Thumbnail
                                val displayBmp = item.reconstructedBitmap ?: item.originalBitmap
                                if (displayBmp != null) {
                                    val bmpBitmap = remember(displayBmp) { displayBmp.asImageBitmap() }
                                    Image(
                                        bitmap = bmpBitmap,
                                        contentDescription = "Card $index",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(DarkSurfaceVariant)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(DarkSurfaceVariant)
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "কার্ড #${index + 1}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    when (item.status) {
                                        BatchStatus.PENDING -> {
                                            Text(
                                                text = "অপেক্ষমাণ...",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TextSecondary
                                            )
                                        }
                                        BatchStatus.PROCESSING -> {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(12.dp),
                                                    strokeWidth = 2.dp,
                                                    color = AccentCyan
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "প্রসেসিং...",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = AccentCyan
                                                )
                                            }
                                        }
                                        BatchStatus.COMPLETED -> {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Default.CheckCircle,
                                                    contentDescription = null,
                                                    tint = AccentGreen,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "রিকনস্ট্রাক্ট সম্পন্ন (ট্যাপ করে দেখুন)",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = AccentGreen
                                                )
                                            }
                                        }
                                        BatchStatus.ERROR -> {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Default.Error,
                                                    contentDescription = null,
                                                    tint = AccentError,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = item.errorMessage ?: "ব্যর্থ হয়েছে",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = AccentError
                                                )
                                            }
                                        }
                                    }
                                }

                                if (!isProcessingAll) {
                                    IconButton(
                                        onClick = { batchItems.removeAt(index) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove",
                                            tint = TextMuted,
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
}
