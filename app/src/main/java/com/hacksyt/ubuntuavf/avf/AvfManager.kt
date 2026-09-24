package com.hacksyt.ubuntuavf.avf

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.lang.reflect.Proxy
import java.util.concurrent.Executor
import java.util.concurrent.Executors

enum class VmState {
    STOPPED,
    STARTING,
    RUNNING,
    STOPPING,
    DELETED,
    ERROR,
    UNSUPPORTED
}

data class VmCapabilities(
    val isAvfSupported: Boolean = false,
    val isCustomVmSupported: Boolean = false,
    val isProtectedVmSupported: Boolean = false,
    val hasManagePermission: Boolean = false,
    val hasCustomVmPermission: Boolean = false,
    val rawCapabilitiesBitmask: Int = 0,
    val statusMessage: String = ""
)

data class VmConfigData(
    val name: String = "ubuntu_vm",
    val memoryMb: Int = 2048,
    val vcpus: Int = 2,
    val kernelParams: String = "rootfstype=ext4 rw console=hvc0",
    val rootfsPath: String = "",
    val kernelPath: String? = null,
    val initrdPath: String? = null
)

interface VmEventListener {
    fun onPayloadStarted()
    fun onPayloadReady()
    fun onPayloadFinished(exitCode: Int)
    fun onError(errorCode: Int, message: String)
    fun onDied(reason: Int)
}

object AvfManager {
    private const val TAG = "AvfManager"
    private const val VIRTUAL_MACHINE_SERVICE = "virtualmachine"
    private const val PERM_MANAGE_VM = "android.permission.MANAGE_VIRTUAL_MACHINE"
    private const val PERM_CUSTOM_VM = "android.permission.USE_CUSTOM_VIRTUAL_MACHINE"

    private var activeVmInstance: Any? = null
    private var vmInputStream: InputStream? = null
    private var vmOutputStream: OutputStream? = null
    private var currentVmState: VmState = VmState.STOPPED
    private var eventListener: VmEventListener? = null

    val vmState: VmState get() = currentVmState

    fun checkCapabilities(context: Context): VmCapabilities {
        val hasManagePerm = ContextCompat.checkSelfPermission(context, PERM_MANAGE_VM) == PackageManager.PERMISSION_GRANTED
        val hasCustomPerm = ContextCompat.checkSelfPermission(context, PERM_CUSTOM_VM) == PackageManager.PERMISSION_GRANTED

        if (Build.VERSION.SDK_INT < 33) {
            return VmCapabilities(
                isAvfSupported = false,
                hasManagePermission = hasManagePerm,
                hasCustomVmPermission = hasCustomPerm,
                statusMessage = "AVF requires Android 13 (API level 33) or higher. Current: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})."
            )
        }

        return try {
            val vmmPair = getVmmService(context)
            val hasKvmNode = File("/dev/kvm").exists()

            if (vmmPair == null) {
                val detail = if (!hasKvmNode) {
                    "/dev/kvm node is missing on this kernel/emulator AVD. Hardware virtualization requires pKVM (e.g. Pixel 7+, Snapdragon 8 Gen 2+) or an emulator with nested KVM enabled."
                } else {
                    "VirtualMachineManager framework service is not exposed by this ROM build."
                }
                VmCapabilities(
                    isAvfSupported = false,
                    hasManagePermission = hasManagePerm,
                    hasCustomVmPermission = hasCustomPerm,
                    statusMessage = detail
                )
            } else {
                val (vmmClass, vmmService) = vmmPair
                var capsBitmask = 0
                var customSupported = false
                var protectedSupported = false

                runCatching {
                    val getCapabilitiesMethod = vmmClass.getMethod("getCapabilities")
                    capsBitmask = getCapabilitiesMethod.invoke(vmmService) as? Int ?: 0
                    // Bitmask constants: CAPABILITY_PROTECTED_VM (1 shl 0), CAPABILITY_NON_PROTECTED_VM (1 shl 1), CAPABILITY_CUSTOM_VM
                    protectedSupported = (capsBitmask and 0x1) != 0
                    customSupported = (capsBitmask and 0x2) != 0 || (capsBitmask and 0x4) != 0 || (capsBitmask > 0)
                }

                val msg = when {
                    !hasManagePerm -> "Permission MANAGE_VIRTUAL_MACHINE is not granted. Grant via ADB:\nadb shell pm grant ${context.packageName} $PERM_MANAGE_VM"
                    !hasCustomPerm -> "Permission USE_CUSTOM_VIRTUAL_MACHINE is not granted. Grant via ADB:\nadb shell pm grant ${context.packageName} $PERM_CUSTOM_VM"
                    else -> "AVF is fully supported and permissions are granted!"
                }

                VmCapabilities(
                    isAvfSupported = true,
                    isCustomVmSupported = customSupported,
                    isProtectedVmSupported = protectedSupported,
                    hasManagePermission = hasManagePerm,
                    hasCustomVmPermission = hasCustomPerm,
                    rawCapabilitiesBitmask = capsBitmask,
                    statusMessage = msg
                )
            }
        } catch (_: ClassNotFoundException) {
            val hasKvmNode = File("/dev/kvm").exists()
            val detail = if (!hasKvmNode) {
                "android.system.virtualmachine framework classes & /dev/kvm are missing. Requires a device/emulator with pKVM hardware virtualization support."
            } else {
                "android.system.virtualmachine framework classes not found on this Android build."
            }
            VmCapabilities(
                isAvfSupported = false,
                hasManagePermission = hasManagePerm,
                hasCustomVmPermission = hasCustomPerm,
                statusMessage = detail
            )
        } catch (e: Exception) {
            VmCapabilities(
                isAvfSupported = false,
                hasManagePermission = hasManagePerm,
                hasCustomVmPermission = hasCustomPerm,
                statusMessage = "Error querying AVF capabilities: ${e.localizedMessage}"
            )
        }
    }

    fun setEventListener(listener: VmEventListener?) {
        this.eventListener = listener
    }

    private fun getVmmService(context: Context): Pair<Class<*>, Any>? {
        return runCatching {
            val vmmClass = Class.forName("android.system.virtualmachine.VirtualMachineManager")
            var service: Any? = runCatching { vmmClass.getMethod("from", Context::class.java).invoke(null, context) }.getOrNull()
            if (service == null) {
                service = runCatching { vmmClass.getMethod("getInstance", Context::class.java).invoke(null, context) }.getOrNull()
            }
            if (service == null) {
                service = runCatching { context.getSystemService(vmmClass) }.getOrNull()
            }
            if (service == null) {
                service = context.getSystemService(VIRTUAL_MACHINE_SERVICE) ?: context.getSystemService("virtual_machine")
            }
            if (service != null) Pair(vmmClass, service) else null
        }.getOrNull()
    }

    fun startUbuntuVm(context: Context, configData: VmConfigData): Result<Unit> {
        val caps = checkCapabilities(context)
        if (!caps.isAvfSupported) {
            currentVmState = VmState.UNSUPPORTED
            return Result.failure(IllegalStateException(caps.statusMessage))
        }

        return try {
            currentVmState = VmState.STARTING

            val (vmmClass, vmmService) = getVmmService(context)
                ?: throw IllegalStateException("VirtualMachineService unavailable.")
            val vmClass = Class.forName("android.system.virtualmachine.VirtualMachine")

            // Check if VM already exists
            val getVmMethod = vmmClass.getMethod("getVirtualMachine", String::class.java)
            var vmInstance = getVmMethod.invoke(vmmService, configData.name)

            if (vmInstance == null) {
                // Create VM config
                val configInstance = buildVmConfig(context, configData)

                // Create VM
                val createMethod = vmmClass.getMethod("create", String::class.java, configInstance.javaClass)
                vmInstance = createMethod.invoke(vmmService, configData.name, configInstance)
            }

            if (vmInstance == null) {
                throw IllegalStateException("Failed to instantiate VirtualMachine.")
            }

            activeVmInstance = vmInstance

            // Attach Callback proxy
            attachVmCallback(vmInstance)

            // Start VM
            val runMethod = runCatching { vmClass.getMethod("run") }.getOrElse { vmClass.getMethod("start") }
            runMethod.invoke(vmInstance)

            currentVmState = VmState.RUNNING

            // Extract Console Streams if available
            runCatching {
                val getConsoleOutput = vmClass.getMethod("getConsoleOutput")
                vmInputStream = getConsoleOutput.invoke(vmInstance) as? InputStream

                val getConsoleInput = vmClass.getMethod("getConsoleInput")
                vmOutputStream = getConsoleInput.invoke(vmInstance) as? OutputStream
            }.onFailure { e ->
                Log.w(TAG, "Console streams not directly available via getter: ${e.message}")
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting Ubuntu VM", e)
            currentVmState = VmState.ERROR
            eventListener?.onError(-1, e.localizedMessage ?: "Failed to start VM")
            Result.failure(e)
        }
    }

    fun stopVm(): Result<Unit> {
        val vm = activeVmInstance ?: return Result.success(Unit)
        return try {
            currentVmState = VmState.STOPPING
            val vmClass = Class.forName("android.system.virtualmachine.VirtualMachine")
            val stopMethod = vmClass.getMethod("stop")
            stopMethod.invoke(vm)

            vmInputStream?.close()
            vmOutputStream?.close()
            vmInputStream = null
            vmOutputStream = null
            activeVmInstance = null
            currentVmState = VmState.STOPPED
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping VM", e)
            currentVmState = VmState.ERROR
            Result.failure(e)
        }
    }

    fun writeToConsole(input: String) {
        try {
            vmOutputStream?.let { stream ->
                stream.write(input.toByteArray(Charsets.UTF_8))
                stream.flush()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write to VM console stream", e)
        }
    }

    fun getConsoleInputStream(): InputStream? = vmInputStream

    private fun buildVmConfig(context: Context, configData: VmConfigData): Any {
        // Try VirtualMachineConfig.fromFileName if vm_config.json exists in assets
        runCatching {
            val vmConfigClass = Class.forName("android.system.virtualmachine.VirtualMachineConfig")
            val fromFileNameMethod = vmConfigClass.getMethod("fromFileName", AssetManager::class.java, String::class.java)
            val res = fromFileNameMethod.invoke(null, context.assets, "vm_config.json")
            if (res != null) return res
        }

        // Fallback: Programmatic VirtualMachineCustomImageConfig.Builder
        val customConfigBuilderClass = Class.forName("android.system.virtualmachine.VirtualMachineCustomImageConfig\$Builder")
        val builder = customConfigBuilderClass.getConstructor(Context::class.java).newInstance(context)

        // Set name, OS, memory, vcpus
        customConfigBuilderClass.getMethod("setName", String::class.java).invoke(builder, configData.name)
        runCatching { customConfigBuilderClass.getMethod("setOs", String::class.java).invoke(builder, "ubuntu") }
        runCatching { customConfigBuilderClass.getMethod("setMemoryBytes", Long::class.javaPrimitiveType).invoke(builder, configData.memoryMb * 1024 * 1024L) }
        runCatching { customConfigBuilderClass.getMethod("setVcpus", Int::class.javaPrimitiveType).invoke(builder, configData.vcpus) }

        // Rootfs disk image
        val rootfsFile = File(configData.rootfsPath)
        if (rootfsFile.exists()) {
            runCatching {
                val pfd = ParcelFileDescriptor.open(rootfsFile, ParcelFileDescriptor.MODE_READ_WRITE)
                val diskBuilderClass = Class.forName("android.system.virtualmachine.VirtualMachineCustomImageConfig\$Disk\$Builder")
                val diskBuilder = diskBuilderClass.getConstructor().newInstance()
                diskBuilderClass.getMethod("setReadonly", Boolean::class.javaPrimitiveType).invoke(diskBuilder, false)
                diskBuilderClass.getMethod("setImageFd", ParcelFileDescriptor::class.java).invoke(diskBuilder, pfd)
                val disk = diskBuilderClass.getMethod("build").invoke(diskBuilder)

                val addDiskMethod = customConfigBuilderClass.getMethod("addDisk", disk.javaClass)
                addDiskMethod.invoke(builder, disk)
            }
        }

        val buildMethod = customConfigBuilderClass.getMethod("build")
        return buildMethod.invoke(builder)
    }

    private fun attachVmCallback(vmInstance: Any) {
        try {
            val vmCallbackClass = Class.forName("android.system.virtualmachine.VirtualMachineCallback")
            val callbackProxy = Proxy.newProxyInstance(
                vmCallbackClass.classLoader,
                arrayOf(vmCallbackClass)
            ) { _, method, args ->
                when (method.name) {
                    "onPayloadStarted" -> {
                        Log.i(TAG, "AVF Payload Started")
                        currentVmState = VmState.RUNNING
                        eventListener?.onPayloadStarted()
                    }
                    "onPayloadReady" -> {
                        Log.i(TAG, "AVF Payload Ready")
                        currentVmState = VmState.RUNNING
                        eventListener?.onPayloadReady()
                    }
                    "onPayloadFinished" -> {
                        val exitCode = args?.getOrNull(1) as? Int ?: 0
                        Log.i(TAG, "AVF Payload Finished with exit code: $exitCode")
                        currentVmState = VmState.STOPPED
                        eventListener?.onPayloadFinished(exitCode)
                    }
                    "onError" -> {
                        val errorCode = args?.getOrNull(1) as? Int ?: -1
                        val msg = args?.getOrNull(2) as? String ?: "Unknown VM error"
                        Log.e(TAG, "AVF Error ($errorCode): $msg")
                        currentVmState = VmState.ERROR
                        eventListener?.onError(errorCode, msg)
                    }
                    "onDied" -> {
                        val reason = args?.getOrNull(1) as? Int ?: 0
                        Log.e(TAG, "AVF VM Died, reason: $reason")
                        currentVmState = VmState.STOPPED
                        eventListener?.onDied(reason)
                    }
                }
                null
            }

            val vmClass = Class.forName("android.system.virtualmachine.VirtualMachine")
            val setCallbackMethod = vmClass.getMethod("setCallback", Executor::class.java, vmCallbackClass)
            setCallbackMethod.invoke(vmInstance, Executors.newSingleThreadExecutor(), callbackProxy)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to attach VirtualMachineCallback: ${e.message}")
        }
    }
}
