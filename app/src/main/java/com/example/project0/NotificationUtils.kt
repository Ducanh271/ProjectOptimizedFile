package com.example.project0

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import android.annotation.SuppressLint // <--- Thêm import này
// Tên kênh thông báo (bắt buộc)
const val CHANNEL_ID = "upload_channel_id"
const val CHANNEL_NAME = "Upload Notifications"

fun makeStatusNotification(message: String, context: Context) {

    // 1. Tạo Notification Channel (Chỉ cần làm 1 lần trên Android 8.0+)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val name = CHANNEL_NAME
        val descriptionText = "Thông báo trạng thái upload file"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }
        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    // 2. Xây dựng thông báo
    val builder = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_upload_done) // Icon mặc định của Android
        .setContentTitle("File Upload")
        .setContentText(message)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true) // Bấm vào thì tự biến mất

    // 3. Hiển thị thông báo
    with(NotificationManagerCompat.from(context)) {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            // ID unique để các thông báo không đè lên nhau (dùng thời gian hiện tại)
            @SuppressLint("MissingPermission")
            notify(System.currentTimeMillis().toInt(), builder.build())
        }
    }
}