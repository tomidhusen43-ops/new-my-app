package com.example.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.CropRotate
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Style
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.CardProcessingEngine
import com.example.model.CardAnalysis
import com.example.model.CardCorners
import com.example.security.SecurityManager
import com.example.ui.dialogs.SecuritySettingsDialog
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.AccentPrimary
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToResult: (source: Bitmap, reconstructed: Bitmap, template: Bitmap?, extractedForeground: Bitmap?, analysis: CardAnalysis) -> Unit,
    onNavigateToCornerEditor: (Bitmap, CardCorners) -> Unit,
    onNavigateToBatch: () -> Unit,
    onOpenPinSetup: () -> Unit,
    manualCorners: CardCorners? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Slot 1: Main Source Card (Contains text, names, numbers, photos, logos)
    var sourceImagePrepared by remember { mutableStateOf<CardProcessingEngine.PreparedImage?>(null) }

    // Slot 2: Target Blank Template Card (Clean background design without text/photos)
    var templateImagePrepared by remember { mutableStateOf<CardProcessingEngine.PreparedImage?>(null) }
    var useDefaultBlankTemplate by remember { mutableStateOf(false) }

    var isProcessing by remember { mutableStateOf(false) }
    var currentStepMessage by remember { mutableStateOf("") }
    var showSecurityDialog by remember { mutableStateOf(false) }

    // Launchers for Slot 1 (Source Main Card)
    val sourcePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val prep = CardProcessingEngine.prepareImage(context, uri)
                    sourceImagePrepared = prep
                    Toast.makeText(context, "Main Card loaded successfully", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to load Main Card: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val sourceFallbackLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val prep = CardProcessingEngine.prepareImage(context, uri)
                    sourceImagePrepared = prep
                    Toast.makeText(context, "Main Card loaded successfully", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to load Main Card: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Launchers for Slot 2 (Target Blank Template Card)
    val templatePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val prep = CardProcessingEngine.prepareImage(context, uri)
                    templateImagePrepared = prep
                    useDefaultBlankTemplate = false
                    Toast.makeText(context, "Blank Template loaded successfully", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to load Blank Template: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val templateFallbackLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val prep = CardProcessingEngine.prepareImage(context, uri)
                    templateImagePrepared = prep
                    useDefaultBlankTemplate = false
                    Toast.makeText(context, "Blank Template loaded successfully", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to load Blank Template: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun openSourcePicker() {
        try {
            sourcePickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } catch (_: Exception) {
            sourceFallbackLauncher.launch("image/*")
        }
    }

    fun openTemplatePicker() {
        try {
            templatePickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } catch (_: Exception) {
            templateFallbackLauncher.launch("image/*")
        }
    }

    fun startProcessing() {
        val sourcePrep = sourceImagePrepared ?: run {
            Toast.makeText(context, "Please select the Main Card first", Toast.LENGTH_SHORT).show()
            return
        }

        isProcessing = true
        coroutineScope.launch {
            try {
                currentStepMessage = "Extracting text, logos & photos..."
                delay(300)

                currentStepMessage = "Comparing designs & matching missing elements..."
                delay(350)

                val targetTemplateBitmap = templateImagePrepared?.bitmap ?: if (useDefaultBlankTemplate) {
                    CardProcessingEngine.createDefaultBlankTemplate(
                        width = sourcePrep.bitmap.width,
                        height = sourcePrep.bitmap.height
                    )
                } else null

                val (analysis, reconstructed, extractedFg) = CardProcessingEngine.processDualCardPipeline(
                    sourcePrepared = sourcePrep,
                    templateBitmap = targetTemplateBitmap,
                    manualCorners = manualCorners
                )

                currentStepMessage = "Reconstruction completed!"
                delay(200)

                isProcessing = false
                onNavigateToResult(
                    sourcePrep.bitmap,
                    reconstructed,
                    targetTemplateBitmap,
                    extractedFg,
                    analysis
                )
            } catch (e: Exception) {
                isProcessing = false
                Toast.makeText(context, "Processing failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    if (showSecurityDialog) {
        SecuritySettingsDialog(
            onDismiss = { showSecurityDialog = false },
            onOpenPinSetup = onOpenPinSetup
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Brush.linearGradient(listOf(AccentPrimary, AccentCyan))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Card Clone AI",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Dual-Card Smart Content Transfer",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showSecurityDialog = true },
                        modifier = Modifier.testTag("home_security_button")
                    ) {
                        val isPinOn = SecurityManager.isPinEnabled(context)
                        Icon(
                            imageVector = if (isPinOn) Icons.Default.Shield else Icons.Default.Security,
                            contentDescription = "Security Settings",
                            tint = if (isPinOn) AccentGreen else AccentCyan
                        )
                    }

                    IconButton(
                        onClick = onNavigateToBatch,
                        modifier = Modifier.testTag("home_batch_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Collections,
                            contentDescription = "Batch Mode",
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Important Notice in Bengali
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Layers, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Smart Content Transfer",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AccentCyan
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "১ম বক্সে আপনার কার্ড দিন এবং ২য় বক্সে নতুন ফাঁকা কার্ডের ডিজাইন দিন। ১ম কার্ডের সব নাম, লেখা ও ছবি ২য় কার্ডে বসে যাবে। ২য় কার্ডে যে ডিজাইন আগে থেকেই আছে তা ডাবল বসবে না।",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        lineHeight = 18.sp
                    )
                }
            }

            // ==========================================
            // SLOT 1: MAIN CARD (Contains text & photos)
            // ==========================================
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, if (sourceImagePrepared != null) AccentCyan else DarkBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(AccentPrimary.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("1", color = AccentPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Main Card (Source)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "যে কার্ডে সব টেক্সট, নাম ও ফটো আছে",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        if (sourceImagePrepared != null) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(20.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val src = sourceImagePrepared
                    if (src == null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(DarkSurfaceVariant)
                                .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                                .clickable { openSourcePicker() }
                                .padding(vertical = 24.dp, horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = AccentPrimary, modifier = Modifier.size(36.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Select Main Card", fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 14.sp)
                                Text("Pick card image with text & photos", color = TextSecondary, fontSize = 11.sp)
                            }
                        }
                    } else {
                        val bmp = src.bitmap
                        val imageBitmap = remember(bmp) { bmp.asImageBitmap() }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black)
                                .border(1.dp, DarkBorder, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            val aspect = bmp.width.toFloat() / bmp.height.toFloat()
                            Image(
                                bitmap = imageBitmap,
                                contentDescription = "Source Card",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(aspect.coerceIn(0.5f, 2.5f))
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { openSourcePicker() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Change", fontSize = 12.sp)
                            }

                            FilledTonalButton(
                                onClick = { onNavigateToCornerEditor(bmp, manualCorners ?: CardCorners()) },
                                modifier = Modifier.weight(1.3f)
                            ) {
                                Icon(Icons.Default.CropRotate, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Crop & Straighten", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // ====================================================
            // SLOT 2: TARGET BLANK TEMPLATE (Design background)
            // ====================================================
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, if (templateImagePrepared != null || useDefaultBlankTemplate) AccentCyan else DarkBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(AccentCyan.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("2", color = AccentCyan, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Blank Template Card",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "ফাঁকা ডিজাইন করা কার্ড (টেক্সট ছাড়া)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        if (templateImagePrepared != null || useDefaultBlankTemplate) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(20.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val tmpl = templateImagePrepared
                    if (tmpl == null && !useDefaultBlankTemplate) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(DarkSurfaceVariant)
                                .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                                .clickable { openTemplatePicker() }
                                .padding(vertical = 24.dp, horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Style, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(36.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Select Blank Template", fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 14.sp)
                                Text("Pick blank design template image", color = TextSecondary, fontSize = 11.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = {
                                useDefaultBlankTemplate = true
                                templateImagePrepared = null
                                Toast.makeText(context, "Default clean white card selected", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Or Use Clean White Canvas", fontSize = 12.sp, color = TextSecondary)
                        }
                    } else if (tmpl != null) {
                        val bmp = tmpl.bitmap
                        val imageBitmap = remember(bmp) { bmp.asImageBitmap() }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black)
                                .border(1.dp, DarkBorder, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            val aspect = bmp.width.toFloat() / bmp.height.toFloat()
                            Image(
                                bitmap = imageBitmap,
                                contentDescription = "Template Card",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(aspect.coerceIn(0.5f, 2.5f))
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedButton(
                            onClick = { openTemplatePicker() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Change Template", fontSize = 12.sp)
                        }
                    } else {
                        Surface(
                            color = DarkSurfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Clean White Canvas selected", color = AccentGreen, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                OutlinedButton(onClick = { openTemplatePicker() }) {
                                    Text("Upload Custom", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Processing Indicator
            AnimatedVisibility(visible = isProcessing) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp,
                            color = AccentCyan
                        )
                        Column {
                            Text(
                                text = "AI Processing",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = currentStepMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = AccentCyan
                            )
                        }
                    }
                }
            }

            // Action Button
            Button(
                onClick = { startProcessing() },
                enabled = !isProcessing && sourceImagePrepared != null,
                colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("start_dual_reconstruction_button")
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Processing...", fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Transfer Content to Target Card",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
