package com.raoprime.downloadivo.ui.dialog

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.raoprime.downloadivo.R
import com.raoprime.downloadivo.model.AppUpdateInfo
import com.raoprime.downloadivo.network.UpdateManager
import com.raoprime.downloadivo.network.UpdateState
import com.raoprime.downloadivo.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun UpdateDialog(
    updateInfo: AppUpdateInfo,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val updateState by UpdateManager.updateState.collectAsState()

    Dialog(
        onDismissRequest = {
            if (!updateInfo.forceUpdate && updateState !is UpdateState.Downloading) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = !updateInfo.forceUpdate && updateState !is UpdateState.Downloading,
            dismissOnClickOutside = !updateInfo.forceUpdate && updateState !is UpdateState.Downloading
        )
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = Color(0xFF141416),
            border = BorderStroke(1.dp, Color(0xFF26262B)),
            shadowElevation = 24.dp,
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(horizontal = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
            ) {
                // Brand Header: Left-Aligned Original D-Arrow Logo + Title + Version Tag
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.app_icon_white),
                        contentDescription = null,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Update available",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            letterSpacing = (-0.2).sp
                        )
                        Text(
                            text = "v${updateInfo.versionName} • Build ${updateInfo.versionCode}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF8E8E93),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Section Label: "What's new" on the same clean surface
                Text(
                    text = "What's new",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                // Parsed & Sanitized Changelog Notes with Hanging Indents
                val rawLines = updateInfo.changelog.split("\n")
                val cleanLines = rawLines.mapNotNull { line ->
                    val trimmed = line.trim()
                        .removePrefix("✨")
                        .removePrefix("âœ¨")
                        .removePrefix("•")
                        .removePrefix("-")
                        .trim()
                    
                    // Filter out redundant titles that repeat version name or "Major Update"
                    if (trimmed.isNotBlank() && 
                        !trimmed.contains("Major Update", ignoreCase = true) &&
                        !trimmed.contains("Major Release", ignoreCase = true) &&
                        !trimmed.equals("Downloadivo", ignoreCase = true)) {
                        trimmed
                    } else {
                        null
                    }
                }

                // Scrollable container if notes exceed comfortable viewport
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (cleanLines.isEmpty()) {
                        Text(
                            text = "Performance enhancements and stability improvements.",
                            fontSize = 12.5.sp,
                            lineHeight = 18.sp,
                            color = Color(0xFFD1D1D6)
                        )
                    } else {
                        cleanLines.forEach { bulletText ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 7.dp, end = 9.dp)
                                        .size(4.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFA0A0A8))
                                )
                                Text(
                                    text = bulletText,
                                    fontSize = 12.5.sp,
                                    lineHeight = 18.sp,
                                    color = Color(0xFFD1D1D6),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Dynamic Action / Download Progress Section
                when (val state = updateState) {
                    is UpdateState.Downloading -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            LinearProgressIndicator(
                                progress = { state.progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = Color.White,
                                trackColor = Color(0xFF26262B)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            val curMb = String.format("%.1f", state.currentBytes / (1024.0 * 1024.0))
                            val totMb = String.format("%.1f", state.totalBytes / (1024.0 * 1024.0))
                            val pct = (state.progress * 100).toInt()

                            Text(
                                text = "Downloading update: $pct% ($curMb MB / $totMb MB)",
                                fontSize = 12.sp,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    is UpdateState.ReadyToInstall -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "✓ Download complete. Ready to install!",
                                fontSize = 12.5.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    HapticUtil.mediumClick(context)
                                    UpdateManager.promptInstall(context, state.apkFile)
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = Color(0xFF111114)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                            ) {
                                Icon(Icons.Default.DownloadDone, contentDescription = null, tint = Color(0xFF111114))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Install Update", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                            }
                        }
                    }
                    is UpdateState.Error -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Error: ${state.message}",
                                fontSize = 12.sp,
                                color = Color(0xFFFF5252),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        UpdateManager.downloadUpdate(context, updateInfo)
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF1E1E22),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                            ) {
                                Text("Retry Download", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    else -> {
                        // Unclipped Actions: "Later" (Charcoal) & "Update" (Soft-White)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (!updateInfo.forceUpdate) {
                                OutlinedButton(
                                    onClick = onDismiss,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = Color(0xFF1E1E22),
                                        contentColor = Color(0xFFDCDCE2)
                                    ),
                                    border = BorderStroke(1.dp, Color(0xFF2B2B32)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                ) {
                                    Text(
                                        text = "Later",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.5.sp
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    HapticUtil.heavyClick(context)
                                    coroutineScope.launch {
                                        val apk = UpdateManager.downloadUpdate(context, updateInfo)
                                        if (apk != null) {
                                            UpdateManager.promptInstall(context, apk)
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = Color(0xFF111114)
                                ),
                                modifier = Modifier
                                    .weight(if (!updateInfo.forceUpdate) 1.25f else 1f)
                                    .heightIn(min = 48.dp)
                            ) {
                                Text(
                                    text = "Update",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
