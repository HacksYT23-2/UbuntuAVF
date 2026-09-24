package com.hacksyt.ubuntuavf.storage

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileOutputStream

data class SetupProgress(
    val bytesCopied: Long = 0,
    val totalBytes: Long = 0,
    val isCompleted: Boolean = false,
    val errorMessage: String? = null
) {
    val progressPercentage: Float
        get() = if (totalBytes > 0) (bytesCopied.toFloat() / totalBytes.toFloat()) else 0f
}

data class StorageInfo(
    val rootfsExists: Boolean,
    val rootfsSizeBytes: Long,
    val availableSpaceBytes: Long,
    val rootfsPath: String
)

object StorageManager {
    private const val ROOTFS_ASSET_NAME = "ubuntu-rootfs.img"
    private const val ROOTFS_FILE_NAME = "ubuntu-rootfs.img"
    private const val VM_CONFIG_FILE_NAME = "vm_config.json"
    private const val KERNEL_FILE_NAME = "kernel"
    private const val INITRD_FILE_NAME = "initrd"

    fun getDiskImageFile(context: Context): File {
        return File(context.filesDir, ROOTFS_FILE_NAME)
    }

    fun getVmConfigFile(context: Context): File {
        val configFile = File(context.filesDir, VM_CONFIG_FILE_NAME)
        if (!configFile.exists()) {
            runCatching {
                context.assets.open(VM_CONFIG_FILE_NAME).use { input ->
                    FileOutputStream(configFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
        return configFile
    }

    fun getKernelFile(context: Context): File? {
        val kernelFile = File(context.filesDir, KERNEL_FILE_NAME)
        if (kernelFile.exists() && kernelFile.length() > 0) {
            return kernelFile
        }
        // Check if in assets
        return runCatching {
            if (context.assets.list("")?.contains(KERNEL_FILE_NAME) == true) {
                context.assets.open(KERNEL_FILE_NAME).use { input ->
                    FileOutputStream(kernelFile).use { output -> input.copyTo(output) }
                }
                kernelFile
            } else null
        }.getOrNull()
    }

    fun getInitrdFile(context: Context): File? {
        val initrdFile = File(context.filesDir, INITRD_FILE_NAME)
        if (initrdFile.exists() && initrdFile.length() > 0) {
            return initrdFile
        }
        // Check if in assets
        return runCatching {
            if (context.assets.list("")?.contains(INITRD_FILE_NAME) == true) {
                context.assets.open(INITRD_FILE_NAME).use { input ->
                    FileOutputStream(initrdFile).use { output -> input.copyTo(output) }
                }
                initrdFile
            } else null
        }.getOrNull()
    }

    fun isDiskPrepared(context: Context): Boolean {
        val targetFile = getDiskImageFile(context)
        return targetFile.exists() && targetFile.length() > 1024 * 1024 // At least 1MB
    }

    fun setupDiskImageIfNeeded(context: Context): Flow<SetupProgress> = flow {
        val targetFile = getDiskImageFile(context)

        if (targetFile.exists() && targetFile.length() > 1024 * 1024) {
            emit(SetupProgress(bytesCopied = targetFile.length(), totalBytes = targetFile.length(), isCompleted = true))
            return@flow
        }

        val assetManager = context.assets
        val assetList = assetManager.list("") ?: emptyArray()

        if (!assetList.contains(ROOTFS_ASSET_NAME)) {
            emit(
                SetupProgress(
                    isCompleted = false,
                    errorMessage = "Rootfs asset ($ROOTFS_ASSET_NAME) not found in app assets."
                )
            )
            return@flow
        }

        var totalBytes = 0L
        runCatching {
            assetManager.openFd(ROOTFS_ASSET_NAME).use { fd ->
                totalBytes = fd.length
            }
        }.onFailure {
            // Uncompressed assets can openFd, compressed ones can't. Estimate if openFd fails.
            totalBytes = 3_500_000_000L
        }

        emit(SetupProgress(bytesCopied = 0, totalBytes = totalBytes, isCompleted = false))

        try {
            val buffer = ByteArray(256 * 1024) // 256 KB buffer for high speed copy
            var bytesCopied = 0L

            assetManager.open(ROOTFS_ASSET_NAME).use { inputStream ->
                FileOutputStream(targetFile).use { outputStream ->
                    var read: Int
                    var lastEmittedBytes = 0L

                    while (inputStream.read(buffer).also { read = it } != -1) {
                        outputStream.write(buffer, 0, read)
                        bytesCopied += read

                        // Emit progress every ~10MB copied
                        if (bytesCopied - lastEmittedBytes >= 10 * 1024 * 1024) {
                            lastEmittedBytes = bytesCopied
                            emit(SetupProgress(bytesCopied = bytesCopied, totalBytes = totalBytes, isCompleted = false))
                        }
                    }
                    outputStream.flush()
                }
            }

            emit(SetupProgress(bytesCopied = bytesCopied, totalBytes = bytesCopied, isCompleted = true))
        } catch (e: Exception) {
            emit(
                SetupProgress(
                    bytesCopied = 0,
                    totalBytes = totalBytes,
                    isCompleted = false,
                    errorMessage = "Failed to extract rootfs: ${e.localizedMessage}"
                )
            )
        }
    }.flowOn(Dispatchers.IO)

    fun getStorageInfo(context: Context): StorageInfo {
        val rootfs = getDiskImageFile(context)
        val filesDir = context.filesDir
        val freeBytes = filesDir.freeSpace
        return StorageInfo(
            rootfsExists = rootfs.exists() && rootfs.length() > 0,
            rootfsSizeBytes = if (rootfs.exists()) rootfs.length() else 0L,
            availableSpaceBytes = freeBytes,
            rootfsPath = rootfs.absolutePath
        )
    }
}
