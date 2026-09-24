package com.hacksyt.ubuntuavf.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hacksyt.ubuntuavf.avf.VmConfigData

@Composable
fun VmSettingsScreen(
    currentConfig: VmConfigData,
    onSaveConfig: (VmConfigData) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var memoryMb by remember { mutableFloatStateOf(currentConfig.memoryMb.toFloat()) }
    var vcpus by remember { mutableFloatStateOf(currentConfig.vcpus.toFloat()) }
    var kernelParams by remember { mutableStateOf(currentConfig.kernelParams) }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F14))
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Settings,
                contentDescription = null,
                tint = Color(0xFF69F0AE),
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(
                text = "VM Settings",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        // Memory Configuration Card
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
                    Text(
                        text = "Memory Allocation (RAM)",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${memoryMb.toInt()} MB",
                        color = Color(0xFF69F0AE),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Slider(
                    value = memoryMb,
                    onValueChange = { memoryMb = it },
                    valueRange = 1024f..8192f,
                    steps = 6, // 1024, 2048, 3072, 4096, 5120, 6144, 7168, 8192
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF69F0AE),
                        activeTrackColor = Color(0xFF69F0AE)
                    )
                )

                Text(
                    text = "Allocating 2048 MB to 4096 MB is recommended for standard Ubuntu workloads.",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
        }

        // vCPU Configuration Card
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
                    Text(
                        text = "Virtual CPU Cores (vCPUs)",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${vcpus.toInt()} Cores",
                        color = Color(0xFF69F0AE),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Slider(
                    value = vcpus,
                    onValueChange = { vcpus = it },
                    valueRange = 1f..8f,
                    steps = 6,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF69F0AE),
                        activeTrackColor = Color(0xFF69F0AE)
                    )
                )

                Text(
                    text = "Matches host CPU topology when running in AVF.",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
        }

        // Kernel Parameters Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1B22)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Kernel Command Line Parameters",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = kernelParams,
                    onValueChange = { kernelParams = it },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF69F0AE),
                        unfocusedBorderColor = Color.DarkGray
                    ),
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Default: rootfstype=ext4 rw console=hvc0",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Save Button
        Button(
            onClick = {
                val updated = currentConfig.copy(
                    memoryMb = memoryMb.toInt(),
                    vcpus = vcpus.toInt(),
                    kernelParams = kernelParams
                )
                onSaveConfig(updated)
                Toast.makeText(context, "VM Configuration Saved!", Toast.LENGTH_SHORT).show()
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF69F0AE),
                contentColor = Color.Black
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Icon(Icons.Default.Save, contentDescription = null)
            Spacer(modifier = Modifier.padding(4.dp))
            Text("Save VM Settings", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}
