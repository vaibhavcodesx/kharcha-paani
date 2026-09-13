package com.example.ui.components

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CharcoalBorder
import com.example.ui.theme.CharcoalCard
import com.example.ui.theme.CharcoalElevated
import com.example.ui.theme.ChartreuseSecondary
import com.example.ui.theme.MutedWarmGray
import com.example.ui.theme.OffWhiteText
import com.example.ui.theme.SubMutedWarmGray
import com.example.ui.theme.WarmAmberPrimary
import java.util.Locale

@Composable
fun QuickCaptureBar(
    isParsing: Boolean,
    onParseText: (String) -> Unit,
    onParseImage: (Bitmap) -> Unit,
    onParsePdf: ((ByteArray, String) -> Unit)? = null,
    onManualAdd: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var inputText by remember { mutableStateOf("") }
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    // Voice recognition launcher
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = spoken?.firstOrNull() ?: ""
            if (spokenText.isNotBlank()) {
                inputText = spokenText
                onParseText(spokenText)
            }
        }
    }

    // PDF Statement picker launcher
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null && onParsePdf != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes() ?: ByteArray(0)
                inputStream?.close()
                if (bytes.isNotEmpty()) {
                    onParsePdf(bytes, "statement.pdf")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Image picker launcher for receipt / payment screenshot
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.isMutableRequired = true
                    }
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
                if (bitmap != null) {
                    onParseImage(bitmap)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }


    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CharcoalCard)
            .border(1.dp, CharcoalBorder, RoundedCornerShape(20.dp))
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = {
                    Text(
                        "e.g. burger 370 mcd gpay, lent Rohan 500...",
                        style = MaterialTheme.typography.bodyMedium.copy(color = SubMutedWarmGray, fontSize = 13.sp)
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("quick_capture_input"),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = OffWhiteText, fontSize = 14.sp),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = WarmAmberPrimary,
                    unfocusedBorderColor = CharcoalBorder,
                    focusedContainerColor = CharcoalElevated,
                    unfocusedContainerColor = CharcoalElevated,
                    cursorColor = WarmAmberPrimary
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    focusManager.clearFocus()
                    if (inputText.isNotBlank()) {
                        onParseText(inputText)
                        inputText = ""
                    }
                }),
                trailingIcon = {
                    if (inputText.isNotEmpty()) {
                        IconButton(
                            onClick = { inputText = "" },
                            modifier = Modifier
                                .size(24.dp)
                                .bounceClick { inputText = "" }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = MutedWarmGray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Action Button: Parse if text present, or Voice Mic / Send with tactile feedback
            if (isParsing) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CharcoalElevated)
                        .shimmerLoading(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = WarmAmberPrimary,
                        strokeWidth = 2.5.dp
                    )
                }
            } else if (inputText.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(WarmAmberPrimary)
                        .bounceClick {
                            focusManager.clearFocus()
                            onParseText(inputText)
                            inputText = ""
                        }
                        .testTag("quick_parse_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Parse with AI",
                        tint = Color(0xFF0E0D0C),
                        modifier = Modifier.size(22.dp)
                    )
                }
            } else {
                // Voice Mic Button with tactile bounce
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CharcoalElevated)
                        .bounceClick {
                            try {
                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(
                                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                    )
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                                    putExtra(
                                        RecognizerIntent.EXTRA_PROMPT,
                                        "Speak expense (e.g. 'Coffee 150 card', 'Received 5000 from client')"
                                    )
                                }
                                speechLauncher.launch(intent)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        .testTag("voice_mic_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Voice Input",
                        tint = WarmAmberPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Secondary Fast Capture Options: Camera / Gallery Receipt, PDF Statement & Fast Presets
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Receipt Scan
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(CharcoalElevated)
                    .bounceClick { imagePickerLauncher.launch("image/*") }
                    .padding(horizontal = 9.dp, vertical = 6.dp)
                    .testTag("scan_receipt_button")
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    tint = ChartreuseSecondary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "Scan Bill",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = OffWhiteText,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp
                    )
                )
            }

            // Upload PDF Statement Button
            if (onParsePdf != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(CharcoalElevated)
                        .bounceClick { pdfPickerLauncher.launch("application/pdf") }
                        .padding(horizontal = 9.dp, vertical = 6.dp)
                        .testTag("upload_pdf_statement_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = WarmAmberPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Import PDF",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = OffWhiteText,
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Quick Example Chips
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf("Chai ₹20", "Uber ₹240").forEach { preset ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(CharcoalElevated)
                            .border(1.dp, CharcoalBorder, RoundedCornerShape(8.dp))
                            .bounceClick { onParseText(preset) }
                            .padding(horizontal = 7.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = preset,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = SubMutedWarmGray,
                                fontSize = 10.sp
                            )
                        )
                    }
                }
            }
        }

    }
}
