package com.example.project0.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.project0.core.drive.DriveUploader
import com.example.project0.core.notification.makeStatusNotification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class FileUploadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val path = inputData.getString("KEY_FILE_PATH") ?: return Result.failure()
        val token = inputData.getString("KEY_ACCESS_TOKEN") ?: return Result.failure()
        val file = File(path)

        return if (file.exists()) {
            val success = withContext(Dispatchers.IO) {
                DriveUploader.uploadToGoogleDrive(token, file)
            }

            if (success) {
                makeStatusNotification("✅ Upload xong: ${file.name}", applicationContext)
                Result.success()
            } else {
                makeStatusNotification("❌ Lỗi upload: ${file.name}", applicationContext)
                Result.retry()
            }
        } else {
            Result.failure()
        }
    }
}
