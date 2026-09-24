package com.hacksyt.ubuntuavf.ui.dashboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hacksyt.ubuntuavf.avf.VmCapabilities
import com.hacksyt.ubuntuavf.avf.VmConfigData
import com.hacksyt.ubuntuavf.avf.VmState
import com.hacksyt.ubuntuavf.storage.SetupProgress
import com.hacksyt.ubuntuavf.storage.StorageInfo

@Composable
fun VmDashboardScreen(
    vmState: VmState,
    capabilities: VmCapabilities,
    storageInfo: StorageInfo,
    setupProgress: SetupProgress,
    configData: VmConfigData,
    onStartVm: () -> Unit,
    onStopVm: () -> Unit,
    onNavigateToTerminal: () -> Unit,
    onRefreshDiagnostics: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F14))
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Title Banner
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Ubuntu AVF",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Android Virtualization Framework",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.LightGray
                )
            }
            IconButton(onClick = onRefreshDiagnostics) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color(0xFF69F0AE))
            }
        }

        // 1. VM Active Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1B22)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = null,
                            tint = Color(0xFF69F0AE),
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = "Ubuntu 22.04 LTS VM",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Status pill
                    Box(
                        modifier = Modifier
                            .background(
                                color = when (vmState) {
                                    VmState.RUNNING -> Color(0xFF1B5E20)
                                    VmState.STARTING -> Color(0xFFF57F17)
                                    VmState.STOPPING -> Color(0xFFE65100)
                                    else -> Color(0xFF37474F)
                                },
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = vmState.name,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Allocated Hardware: ${configData.memoryMb} MB RAM | ${configData.vcpus} vCPUs",
                    color = Color.LightGray,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (vmState == VmState.RUNNING) {
                        Button(
                            onClick = onStopVm,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Stop VM")
                        }
                    } else {
                        Button(
                            onClick = onStartVm,
                            enabled = storageInfo.rootfsExists && capabilities.isAvfSupported,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF69F0AE),
                                contentColor = Color.Black
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Start VM", fontWeight = FontWeight.Bold)
                        }
                    }

                    OutlinedButton(
                        onClick = onNavigateToTerminal,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Code, contentDescription = null, tint = Color(0xFF69F0AE))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Terminal", color = Color.White)
                    }
                }
            }
        }

        // 2. Storage Setup & Disk Image Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1B22)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.SdCard,
                        contentDescription = null,
                        tint = Color(0xFF4FC3F7),
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = "Root Filesystem (Storage)",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (!setupProgress.isCompleted && setupProgress.totalBytes > 0) {
                    Text(
                        text = "Setting up writable Ubuntu rootfs image...",
                        color = Color.LightGray,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { setupProgress.progressPercentage },
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF4FC3F7),
                        trackColor = Color.DarkGray
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${(setupProgress.bytesCopied / 1024 / 1024)} MB / ${(setupProgress.totalBytes / 1024 / 1024)} MB",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                } else if (storageInfo.rootfsExists) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF69F0AE),
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        Text(
                            text = "ubuntu-rootfs.img Ready (${(storageInfo.rootfsSizeBytes / 1024 / 1024)} MB)",
                            color = Color(0xFF69F0AE),
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                } else {
                    Text(
                        text = "Rootfs image missing. Extracting from assets...",
                        color = Color(0xFFFFD700),
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Free App Storage: ${(storageInfo.availableSpaceBytes / 1024 / 1024 / 1024)} GB available",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
        }

        // 3. AVF System Capability & Permissions Diagnostics Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1B22)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = if (capabilities.isAvfSupported) Color(0xFF69F0AE) else Color(0xFFFF5252),
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = "AVF Framework Diagnostics",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                DiagnosticRow(
                    label = "AVF Service Supported",
                    isOK = capabilities.isAvfSupported
                )
                DiagnosticRow(
                    label = "Custom OS VM Image",
                    isOK = capabilities.isCustomVmSupported
                )
                DiagnosticRow(
                    label = "MANAGE_VIRTUAL_MACHINE Perm",
                    isOK = capabilities.hasManagePermission
                )
                DiagnosticRow(
                    label = "USE_CUSTOM_VIRTUAL_MACHINE Perm",
                    isOK = capabilities.hasCustomVmPermission
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = capabilities.statusMessage,
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .background(Color(0xFF0F0F14), shape = RoundedCornerShape(6.dp))
                        .padding(8.dp)
                        .fillMaxWidth()
                )

                // ADB Perm Grant Helper button if permissions missing
                AnimatedVisibility(visible = !capabilities.hasManagePermission || !capabilities.hasCustomVmPermission) {
                    Column(modifier = Modifier.padding(top = 10.dp)) {
                        Text(
                            text = "Grant permissions via ADB command:",
                            color = Color(0xFFFFD700),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        val adbCmd = "adb shell pm grant ${context.packageName} android.permission.MANAGE_VIRTUAL_MACHINE && adb shell pm grant ${context.packageName} android.permission.USE_CUSTOM_VIRTUAL_MACHINE"

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .background(Color(0xFF050508), shape = RoundedCornerShape(6.dp))
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = adbCmd,
                                color = Color.Green,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("ADB Command", adbCmd))
                                Toast.makeText(context, "ADB Command copied!", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy ADB Command", tint = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, isOK: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = Color.LightGray, fontSize = 13.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (isOK) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (isOK) Color(0xFF69F0AE) else Color(0xFFFF5252),
                modifier = Modifier.padding(end = 4.dp)
            )
            Text(
                text = if (isOK) "Granted/OK" else "Missing",
                color = if (isOK) Color(0xFF69F0AE) else Color(0xFFFF5252),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
