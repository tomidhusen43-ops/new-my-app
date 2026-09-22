package com.example.ui.dialogs

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.engine.CardExporter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportDialog(
    bitmap: Bitmap,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var filename by remember { mutableStateOf("card_clone") }
    var format by remember { mutableStateOf("PNG") }
    var quality by remember { mutableFloatStateOf(95f) }
    var exporting by remember { mutableStateOf(false) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    fun exportAndShare() {
        if (filename.trim().isEmpty()) {
            Toast.makeText(context, "একটি filename দিন।", Toast.LENGTH_SHORT).show()
            return
        }

        exporting = true
        coroutineScope.launch {
            val result = CardExporter.exportAndShare(
                context = context,
                bitmap = bitmap,
                filename = filename,
                format = format,
                quality = quality.toInt()
            )

            exporting = false
            result.onSuccess {
                Toast.makeText(context, "Card export হয়েছে।", Toast.LENGTH_SHORT).show()
                onDismiss()
            }.onFailure { e ->
                Toast.makeText(context, "Export failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!exporting) onDismiss() },
        title = {
            Text(text = "Export Card", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = filename,
                    onValueChange = { filename = it },
                    label = { Text("File name") },
                    placeholder = { Text("card_clone") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("export_filename_input")
                )

                Spacer(modifier = Modifier.height(16.dp))

                ExposedDropdownMenuBox(
                    expanded = dropdownExpanded,
                    onExpandedChange = { dropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = format,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Format") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                            .testTag("export_format_dropdown")
                    )

                    ExposedDropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("PNG") },
                            onClick = {
                                format = "PNG"
                                dropdownExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("JPG") },
                            onClick = {
                                format = "JPG"
                                dropdownExpanded = false
                            }
                        )
                    }
                }

                if (format == "JPG") {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Quality: ${quality.toInt()}%",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = quality,
                        onValueChange = { quality = it },
                        valueRange = 50f..100f,
                        steps = 9,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("export_quality_slider")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { exportAndShare() },
                enabled = !exporting,
                modifier = Modifier.testTag("export_submit_button")
            ) {
                if (exporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("EXPORTING...")
                } else {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("EXPORT & SHARE")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !exporting,
                modifier = Modifier.testTag("export_cancel_button")
            ) {
                Text("CANCEL")
            }
        }
    )
}
