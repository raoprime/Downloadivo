package com.raoprime.downloadivo.ui.dialog

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
            shape = RoundedCornerShape(24.dp),
            color = SurfaceDark,
            border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFF2E2E2E)),
            shadowElevation = 24.dp,
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(horizontal = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Sleek Studio Sparkle Icon Header
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(SurfaceElevated, Color(0xFF181818))
                            )
                        )
                        .border(1.dp, Color(0xFF333333), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = TextCrisp,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Title
                Text(
                    text = "Downloadivo Update",
                    style = Typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        letterSpacing = 0.2.sp
                    ),
                    color = TextCrisp
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Version Tag Pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceElevated)
                        .border(0.5.dp, Color(0xFF2C2C2C), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4CAF50))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "v${updateInfo.versionName}",
                        color = TextCrisp,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Text(
                        text = " • Build ${updateInfo.versionCode}",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Premium Changelog Details Card
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = SurfaceElevated,
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFF262626)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 190.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Icon(
                                Icons.Default.Checklist,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "RELEASE HIGHLIGHTS",
                                style = Typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.5.sp,
                                    letterSpacing = 1.sp
                                ),
                                color = TextSecondary
                            )
                        }

                        val rawLines = updateInfo.changelog.split("\n")
                        rawLines.forEach { line ->
                            // Clean up any garbled UTF-8 artifacts or raw prefixes
                            val cleanLine = line.trim()
                                .removePrefix("✨")
                                .removePrefix("âœ¨")
                                .removePrefix("•")
                                .removePrefix("-")
                                .trim()

                            if (cleanLine.isNotBlank()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 6.dp, end = 8.dp)
                                            .size(4.dp)
                                            .clip(CircleShape)
                                            .background(TextCrisp)
                                    )
                                    Text(
                                        text = cleanLine,
                                        style = Typography.bodySmall.copy(
                                            fontSize = 12.sp,
                                            lineHeight = 17.sp
                                        ),
                                        color = Color(0xFFD4D4D4)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

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
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = TextCrisp,
                                trackColor = Color(0xFF282828)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            val curMb = String.format("%.1f", state.currentBytes / (1024.0 * 1024.0))
                            val totMb = String.format("%.1f", state.totalBytes / (1024.0 * 1024.0))
                            val pct = (state.progress * 100).toInt()

                            Text(
                                text = "Downloading update: $pct% ($curMb MB / $totMb MB)",
                                style = Typography.bodySmall,
                                color = TextCrisp,
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
                                style = Typography.bodySmall,
                                color = TextCrisp,
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
                                    containerColor = TextCrisp,
                                    contentColor = CanvasDark
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                            ) {
                                Icon(Icons.Default.DownloadDone, contentDescription = null, tint = CanvasDark)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Install Update Now", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
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
                                style = Typography.bodySmall,
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
                                    containerColor = SurfaceElevated,
                                    contentColor = TextCrisp
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                            ) {
                                Text("Retry Download", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    else -> {
                        // Initial State: "Later" & "Update Now" buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (!updateInfo.forceUpdate) {
                                OutlinedButton(
                                    onClick = onDismiss,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = TextSecondary
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF282828)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                ) {
                                    Text("Later", style = Typography.labelMedium, fontWeight = FontWeight.SemiBold)
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
                                    containerColor = TextCrisp,
                                    contentColor = CanvasDark
                                ),
                                modifier = Modifier
                                    .weight(1.4f)
                                    .height(44.dp)
                            ) {
                                Icon(
                                    Icons.Default.Download,
                                    contentDescription = null,
                                    tint = CanvasDark,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Update Now", style = Typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
