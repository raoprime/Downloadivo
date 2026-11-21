package com.raoprime.downloadivo.ui.dialog

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.raoprime.downloadivo.BuildConfig
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

    var downloadedApk by remember { mutableStateOf<File?>(null) }

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
            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderDark),
            shadowElevation = 20.dp,
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(horizontal = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Rocket / Sparkle Icon Header
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(TextCrisp, SurfaceElevated)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = CanvasDark,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Title & Version Pill
                Text(
                    text = "New Update Available",
                    style = Typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = TextCrisp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceElevated)
                        .border(1.dp, CardBorderDark, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
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

                Spacer(modifier = Modifier.height(14.dp))

                // Changelog Card
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = SurfaceElevated,
                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderDark),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 160.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "What's New:",
                            style = Typography.labelMedium,
                            color = TextCrisp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        val lines = updateInfo.changelog.split("\n")
                        lines.forEach { line ->
                            val cleanLine = line.trim()
                            if (cleanLine.isNotBlank()) {
                                Row(
                                    modifier = Modifier.padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                        text = if (cleanLine.startsWith("✨") || cleanLine.startsWith("•") || cleanLine.startsWith("-")) "" else "• ",
                                        color = TextCrisp,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = cleanLine,
                                        style = Typography.bodySmall,
                                        color = TextSecondary,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

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
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = TextCrisp,
                                trackColor = CardBorderDark
                            )

                            Spacer(modifier = Modifier.height(8.dp))

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
                        downloadedApk = state.apkFile
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
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(TextCrisp)
                                    .bouncyClick {
                                        UpdateManager.promptInstall(context, state.apkFile)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.DownloadDone, contentDescription = null, tint = CanvasDark)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Install Update Now",
                                        fontWeight = FontWeight.Bold,
                                        color = CanvasDark,
                                        fontSize = 14.sp
                                    )
                                }
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
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(SurfaceElevated)
                                    .border(1.dp, CardBorderDark, RoundedCornerShape(12.dp))
                                    .bouncyClick {
                                        coroutineScope.launch {
                                            UpdateManager.downloadUpdate(context, updateInfo)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Retry Download", color = TextCrisp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    else -> {
                        // Initial State: "Update Now" & "Later" buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (!updateInfo.forceUpdate) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(SurfaceElevated)
                                        .border(1.dp, CardBorderDark, RoundedCornerShape(12.dp))
                                        .bouncyClick { onDismiss() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Later",
                                        style = Typography.labelMedium,
                                        color = TextSecondary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1.5f)
                                    .height(46.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(TextCrisp)
                                    .bouncyClick {
                                        coroutineScope.launch {
                                            val apk = UpdateManager.downloadUpdate(context, updateInfo)
                                            if (apk != null) {
                                                UpdateManager.promptInstall(context, apk)
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Download, contentDescription = null, tint = CanvasDark, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Update Now",
                                        style = Typography.labelMedium,
                                        color = CanvasDark,
                                        fontWeight = FontWeight.ExtraBold
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
