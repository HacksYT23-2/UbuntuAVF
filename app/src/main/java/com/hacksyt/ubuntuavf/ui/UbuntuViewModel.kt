package com.hacksyt.ubuntuavf.ui

import android.app.Application
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hacksyt.ubuntuavf.avf.AvfManager
import com.hacksyt.ubuntuavf.avf.VmCapabilities
import com.hacksyt.ubuntuavf.avf.VmConfigData
import com.hacksyt.ubuntuavf.avf.VmState
import com.hacksyt.ubuntuavf.service.UbuntuVmService
import com.hacksyt.ubuntuavf.storage.SetupProgress
import com.hacksyt.ubuntuavf.storage.StorageInfo
import com.hacksyt.ubuntuavf.storage.StorageManager
import com.hacksyt.ubuntuavf.ui.terminal.TerminalBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.InputStream

class UbuntuViewModel(application: Application) : AndroidViewModel(application) {

    private val _vmState = MutableStateFlow(VmState.STOPPED)
    val vmState: StateFlow<VmState> = _vmState.asStateFlow()

    private val _capabilities = MutableStateFlow(VmCapabilities())
    val capabilities: StateFlow<VmCapabilities> = _capabilities.asStateFlow()

    private val _storageInfo = MutableStateFlow(StorageInfo(false, 0, 0, ""))
    val storageInfo: StateFlow<StorageInfo> = _storageInfo.asStateFlow()

    private val _setupProgress = MutableStateFlow(SetupProgress())
    val setupProgress: StateFlow<SetupProgress> = _setupProgress.asStateFlow()

    private val _configData = MutableStateFlow(
        VmConfigData(
            name = "ubuntu_vm",
            memoryMb = 2048,
            vcpus = 2,
            kernelParams = "rootfstype=ext4 rw console=hvc0",
            rootfsPath = StorageManager.getDiskImageFile(application).absolutePath
        )
    )
    val configData: StateFlow<VmConfigData> = _configData.asStateFlow()

    private val terminalBuffer = TerminalBuffer(maxLines = 2000)
    private val _terminalLines = MutableStateFlow<List<AnnotatedString>>(emptyList())
    val terminalLines: StateFlow<List<AnnotatedString>> = _terminalLines.asStateFlow()

    private var consoleReaderJob: Job? = null

    init {
        checkCapabilities()
        refreshStorageInfo()
        prepareStorage()
    }

    fun checkCapabilities() {
        val caps = AvfManager.checkCapabilities(getApplication())
        _capabilities.value = caps
    }

    fun refreshStorageInfo() {
        _storageInfo.value = StorageManager.getStorageInfo(getApplication())
    }

    fun prepareStorage() {
        viewModelScope.launch {
            StorageManager.setupDiskImageIfNeeded(getApplication()).collect { progress ->
                _setupProgress.value = progress
                if (progress.isCompleted) {
                    refreshStorageInfo()
                    val diskFile = StorageManager.getDiskImageFile(getApplication())
                    _configData.value = _configData.value.copy(rootfsPath = diskFile.absolutePath)
                }
            }
        }
    }

    fun startVm() {
        val app = getApplication<Application>()
        val config = _configData.value
        _vmState.value = VmState.STARTING
        UbuntuVmService.startVm(app, config.memoryMb, config.vcpus)

        viewModelScope.launch {
            // Monitor state & start console reader loop
            repeat(10) {
                delay(500)
                val currentState = AvfManager.vmState
                _vmState.value = currentState
                if (currentState == VmState.RUNNING) {
                    startConsoleReaderLoop()
                    return@launch
                }
            }
        }
    }

    fun stopVm() {
        val app = getApplication<Application>()
        _vmState.value = VmState.STOPPING
        UbuntuVmService.stopVm(app)
        consoleReaderJob?.cancel()
        consoleReaderJob = null
        _vmState.value = VmState.STOPPED
    }

    fun sendConsoleInput(input: String) {
        if (_vmState.value == VmState.RUNNING) {
            AvfManager.writeToConsole(input)
        }
    }

    fun clearTerminalBuffer() {
        terminalBuffer.clear()
        _terminalLines.value = emptyList()
    }

    fun updateConfig(newConfig: VmConfigData) {
        _configData.value = newConfig
    }

    private fun startConsoleReaderLoop() {
        consoleReaderJob?.cancel()
        consoleReaderJob = viewModelScope.launch(Dispatchers.IO) {
            val stream: InputStream? = AvfManager.getConsoleInputStream()
            if (stream == null) {
                terminalBuffer.append("[System] Console stream connecting...\n")
                _terminalLines.value = terminalBuffer.getLines()
            }

            val buffer = ByteArray(1024)
            while (_vmState.value == VmState.RUNNING) {
                try {
                    val activeStream = stream ?: AvfManager.getConsoleInputStream()
                    if (activeStream != null && activeStream.available() > 0) {
                        val bytesRead = activeStream.read(buffer)
                        if (bytesRead > 0) {
                            val text = String(buffer, 0, bytesRead, Charsets.UTF_8)
                            terminalBuffer.append(text)
                            _terminalLines.value = terminalBuffer.getLines()
                        }
                    } else {
                        delay(100)
                    }
                } catch (_: Exception) {
                    delay(200)
                }
            }
        }
    }
}
