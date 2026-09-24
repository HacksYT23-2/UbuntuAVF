package com.hacksyt.ubuntuavf.ui.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hacksyt.ubuntuavf.avf.VmState
import kotlinx.coroutines.launch

@Composable
fun UbuntuTerminalScreen(
    terminalLines: List<AnnotatedString>,
    vmState: VmState,
    onSendCommand: (String) -> Unit,
    onClearBuffer: () -> Unit,
    onStartVm: () -> Unit,
    onStopVm: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var inputText by remember { mutableStateOf("") }
    var isCtrlActive by remember { mutableStateOf(false) }
    var isAltActive by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    // Auto-scroll to bottom on new output
    LaunchedEffect(terminalLines.size) {
        if (terminalLines.isNotEmpty()) {
            listState.animateScrollToItem(terminalLines.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F14))
    ) {
        // Top Console Control Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1B1B22))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(10.dp)
                        .height(10.dp)
                        .background(
                            color = when (vmState) {
                                VmState.RUNNING -> Color(0xFF4CAF50)
                                VmState.STARTING -> Color(0xFFFFC107)
                                VmState.STOPPING -> Color(0xFFFF9800)
                                else -> Color(0xFFF44336)
                            },
                            shape = RoundedCornerShape(5.dp)
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Ubuntu Terminal (${vmState.name})",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace
                )
            }

            Row {
                if (vmState == VmState.RUNNING) {
                    IconButton(onClick = onStopVm) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop VM", tint = Color(0xFFFF5252))
                    }
                } else {
                    IconButton(onClick = onStartVm) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Start VM", tint = Color(0xFF69F0AE))
                    }
                }

                IconButton(onClick = {
                    val fullLog = terminalLines.joinToString("\n") { it.text }
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Ubuntu Terminal Log", fullLog))
                    Toast.makeText(context, "Terminal log copied!", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy Log", tint = Color.LightGray)
                }

                IconButton(onClick = onClearBuffer) {
                    Icon(Icons.Default.Delete, contentDescription = "Clear Buffer", tint = Color.LightGray)
                }
            }
        }

        // Terminal Output Window
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(8.dp)
                .background(Color(0xFF050508), shape = RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            if (terminalLines.isEmpty()) {
                Text(
                    text = if (vmState == VmState.RUNNING) {
                        "Connected to Ubuntu serial console (hvc0).\nWaiting for terminal output...\nType commands below."
                    } else {
                        "VM is currently ${vmState.name}.\nTap the Play button above to start Ubuntu VM."
                    },
                    color = Color.Gray,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(terminalLines) { line ->
                        Text(
                            text = line,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }

        // Virtual Helper Key Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .background(Color(0xFF16161D))
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            KeyChip(
                text = "CTRL",
                isActive = isCtrlActive,
                onClick = { isCtrlActive = !isCtrlActive }
            )
            KeyChip(
                text = "ALT",
                isActive = isAltActive,
                onClick = { isAltActive = !isAltActive }
            )
            KeyChip(text = "ESC", onClick = { onSendCommand("\u001B") })
            KeyChip(text = "TAB", onClick = { onSendCommand("\t") })
            KeyChip(text = "Ctrl+C", onClick = { onSendCommand("\u0003") })
            KeyChip(text = "Ctrl+D", onClick = { onSendCommand("\u0004") })
            KeyChip(text = "Ctrl+Z", onClick = { onSendCommand("\u001A") })
            KeyChip(text = "|", onClick = { onSendCommand("|") })
            KeyChip(text = "/", onClick = { onSendCommand("/") })
            KeyChip(text = "-", onClick = { onSendCommand("-") })
            KeyChip(text = "~", onClick = { onSendCommand("~") })
            KeyChip(text = "↑", onClick = { onSendCommand("\u001B[A") })
            KeyChip(text = "↓", onClick = { onSendCommand("\u001B[B") })
            KeyChip(text = "←", onClick = { onSendCommand("\u001B[D") })
            KeyChip(text = "→", onClick = { onSendCommand("\u001B[C") })
        }

        // Command Input Bar
        Surface(
            color = Color(0xFF1B1B22),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$ ",
                    color = Color(0xFF69F0AE),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp)
                )

                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text(
                            text = if (vmState == VmState.RUNNING) "Type command..." else "Start VM to execute",
                            color = Color.Gray,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp
                        )
                    },
                    modifier = Modifier.weight(1f),
                    enabled = vmState == VmState.RUNNING,
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF0F0F14),
                        unfocusedContainerColor = Color(0xFF0F0F14),
                        focusedBorderColor = Color(0xFF69F0AE),
                        unfocusedBorderColor = Color.DarkGray
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (inputText.isNotEmpty() || isCtrlActive) {
                                sendCommandWithModifiers(inputText, isCtrlActive, isAltActive, onSendCommand)
                                inputText = ""
                                isCtrlActive = false
                                isAltActive = false
                            }
                        }
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        sendCommandWithModifiers(inputText, isCtrlActive, isAltActive, onSendCommand)
                        inputText = ""
                        isCtrlActive = false
                        isAltActive = false
                    },
                    enabled = vmState == VmState.RUNNING
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send Command",
                        tint = if (vmState == VmState.RUNNING) Color(0xFF69F0AE) else Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
private fun KeyChip(
    text: String,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isActive) Color(0xFF69F0AE) else Color(0xFF2A2A36),
            contentColor = if (isActive) Color.Black else Color.White
        ),
        shape = RoundedCornerShape(6.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        modifier = Modifier.height(32.dp)
    ) {
        Text(text = text, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}

private fun sendCommandWithModifiers(
    input: String,
    ctrl: Boolean,
    alt: Boolean,
    sendAction: (String) -> Unit
) {
    if (ctrl && input.isNotEmpty()) {
        val firstChar = input[0].uppercaseChar()
        val asciiVal = (firstChar.code - 64).coerceIn(1, 26)
        sendAction(asciiVal.toChar().toString())
    } else if (alt && input.isNotEmpty()) {
        sendAction("\u001B$input")
    } else {
        sendAction("$input\n")
    }
}
