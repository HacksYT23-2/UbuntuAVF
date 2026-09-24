package com.hacksyt.ubuntuavf.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.hacksyt.ubuntuavf.MainActivity
import com.hacksyt.ubuntuavf.R
import com.hacksyt.ubuntuavf.avf.AvfManager
import com.hacksyt.ubuntuavf.avf.VmConfigData
import com.hacksyt.ubuntuavf.avf.VmEventListener
import com.hacksyt.ubuntuavf.storage.StorageManager

class UbuntuVmService : Service(), VmEventListener {

    companion object {
        private const val TAG = "UbuntuVmService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "ubuntu_vm_service_channel"

        const val ACTION_START_VM = "com.hacksyt.ubuntuavf.ACTION_START_VM"
        const val ACTION_STOP_VM = "com.hacksyt.ubuntuavf.ACTION_STOP_VM"

        const val EXTRA_RAM_MB = "extra_ram_mb"
        const val EXTRA_VCPUS = "extra_vcpus"

        fun startVm(context: Context, ramMb: Int = 2048, vcpus: Int = 2) {
            val intent = Intent(context, UbuntuVmService::class.java).apply {
                action = ACTION_START_VM
                putExtra(EXTRA_RAM_MB, ramMb)
                putExtra(EXTRA_VCPUS, vcpus)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopVm(context: Context) {
            val intent = Intent(context, UbuntuVmService::class.java).apply {
                action = ACTION_STOP_VM
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        AvfManager.setEventListener(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_VM -> {
                val ramMb = intent.getIntExtra(EXTRA_RAM_MB, 2048)
                val vcpus = intent.getIntExtra(EXTRA_VCPUS, 2)
                startForegroundWithNotification("Starting Ubuntu VM...")
                runVm(ramMb, vcpus)
            }
            ACTION_STOP_VM -> {
                stopVmInternal()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        AvfManager.setEventListener(null)
        AvfManager.stopVm()
        super.onDestroy()
    }

    private fun runVm(ramMb: Int, vcpus: Int) {
        val rootfsFile = StorageManager.getDiskImageFile(this)
        val config = VmConfigData(
            name = "ubuntu_vm",
            memoryMb = ramMb,
            vcpus = vcpus,
            kernelParams = "rootfstype=ext4 rw console=hvc0",
            rootfsPath = rootfsFile.absolutePath,
            kernelPath = StorageManager.getKernelFile(this)?.absolutePath,
            initrdPath = StorageManager.getInitrdFile(this)?.absolutePath
        )

        val result = AvfManager.startUbuntuVm(this, config)
        if (result.isSuccess) {
            updateNotification("Ubuntu VM Running ($ramMb MB RAM, $vcpus vCPUs)")
        } else {
            val errorMsg = result.exceptionOrNull()?.localizedMessage ?: "Failed to start VM"
            updateNotification("Ubuntu VM Error: $errorMsg")
            Log.e(TAG, "Failed to start VM: $errorMsg")
        }
    }

    private fun stopVmInternal() {
        AvfManager.stopVm()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Ubuntu VM Execution Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows ongoing status of the Ubuntu Virtual Machine."
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    private fun startForegroundWithNotification(statusText: String) {
        val notification = buildNotification(statusText)
        val foregroundType = if (Build.VERSION.SDK_INT >= 34) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        startForeground(NOTIFICATION_ID, notification, foregroundType)
    }

    private fun updateNotification(statusText: String) {
        val notification = buildNotification(statusText)
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(statusText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, UbuntuVmService::class.java).apply {
            action = ACTION_STOP_VM
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Ubuntu AVF Virtual Machine")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop VM", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    // VmEventListener Callbacks
    override fun onPayloadStarted() {
        updateNotification("Ubuntu VM Payload Started...")
    }

    override fun onPayloadReady() {
        updateNotification("Ubuntu VM Ready & Running")
    }

    override fun onPayloadFinished(exitCode: Int) {
        updateNotification("Ubuntu VM Exited (Code $exitCode)")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onError(errorCode: Int, message: String) {
        updateNotification("Ubuntu VM Error ($errorCode): $message")
    }

    override fun onDied(reason: Int) {
        updateNotification("Ubuntu VM Terminated ($reason)")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
}
