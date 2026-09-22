package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.security.SecurityManager
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.AccentPrimary
import com.example.ui.theme.AccentWarning
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class PinMode {
    UNLOCK,
    SETUP
}

@Composable
fun PinLockScreen(
    mode: PinMode = PinMode.UNLOCK,
    onSuccess: () -> Unit,
    onCancel: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var enteredPin by remember { mutableStateOf("") }
    var firstEnteredPin by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val shakeOffset = remember { Animatable(0f) }

    fun triggerShake() {
        coroutineScope.launch {
            shakeOffset.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 400
                    0f at 0
                    -25f at 50
                    25f at 100
                    -20f at 150
                    20f at 200
                    -10f at 250
                    10f at 300
                    0f at 400
                }
            )
        }
    }

    fun handlePinComplete(pin: String) {
        if (mode == PinMode.UNLOCK) {
            if (SecurityManager.verifyPin(context, pin)) {
                SecurityManager.unlock()
                onSuccess()
            } else {
                errorMessage = "ভুল পিন কোড! পুনরায় চেষ্টা করুন।"
                triggerShake()
                enteredPin = ""
            }
        } else {
            // SETUP MODE
            if (firstEnteredPin == null) {
                firstEnteredPin = pin
                enteredPin = ""
                errorMessage = null
            } else {
                if (firstEnteredPin == pin) {
                    SecurityManager.setPin(context, pin)
                    Toast.makeText(context, "পিন সফলভাবে সেট করা হয়েছে!", Toast.LENGTH_SHORT).show()
                    onSuccess()
                } else {
                    errorMessage = "পিন মেলেনি! প্রথম থেকে আবার দিন।"
                    triggerShake()
                    firstEnteredPin = null
                    enteredPin = ""
                }
            }
        }
    }

    fun onKeyClick(key: String) {
        if (enteredPin.length < 4) {
            errorMessage = null
            val newPin = enteredPin + key
            enteredPin = newPin
            if (newPin.length == 4) {
                handlePinComplete(newPin)
            }
        }
    }

    fun onBackspace() {
        if (enteredPin.isNotEmpty()) {
            enteredPin = enteredPin.dropLast(1)
            errorMessage = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(AccentPrimary.copy(alpha = 0.8f), AccentCyan.copy(alpha = 0.8f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (mode == PinMode.UNLOCK) Icons.Default.Lock else Icons.Default.Shield,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(34.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = if (mode == PinMode.UNLOCK) {
                    "নিরাপত্তা পিন দিন"
                } else {
                    if (firstEnteredPin == null) "নতুন ৪ সংখ্যার পিন দিন" else "পিনটি নিশ্চিত করুন"
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (mode == PinMode.UNLOCK) {
                    "আপনার কার্ড ও ডাটা সুরক্ষিত রয়েছে"
                } else {
                    "অ্যাপ খোলার জন্য এই পিনটি প্রয়োজন হবে"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 4 Dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.offset { IntOffset(shakeOffset.value.roundToInt(), 0) }
            ) {
                for (i in 0 until 4) {
                    val isFilled = i < enteredPin.length
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(
                                if (isFilled) AccentCyan else DarkSurfaceVariant
                            )
                            .border(
                                width = 1.5.dp,
                                color = if (isFilled) AccentCyan else DarkBorder,
                                shape = CircleShape
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Error message
            Box(modifier = Modifier.height(24.dp)) {
                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = AccentWarning,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Keypad
            val keys = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("", "0", "DEL")
            )

            for (row in keys) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    for (k in row) {
                        if (k.isEmpty()) {
                            Spacer(modifier = Modifier.size(68.dp))
                        } else if (k == "DEL") {
                            Box(
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(CircleShape)
                                    .clickable { onBackspace() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Backspace,
                                    contentDescription = "Delete",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(CircleShape)
                                    .background(DarkSurface)
                                    .border(1.dp, DarkBorder, CircleShape)
                                    .clickable { onKeyClick(k) }
                                    .testTag("pin_key_$k"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = k,
                                    color = TextPrimary,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            if (onCancel != null) {
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = onCancel) {
                    Text("বাতিল করুন", color = TextSecondary)
                }
            }
        }
    }
}
