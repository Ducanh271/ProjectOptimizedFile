package com.example.project0.core.drive

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject        // THÊM IMPORT NÀY
import java.io.File

object DriveUploader {

    private val client = OkHttpClient()

    suspend fun uploadToGoogleDrive(accessToken: String, file: File): Boolean {
        return try {
            val metadata = """
                {
                    "name": "${file.name}",
                    "mimeType": "application/octet-stream"
                }
            """.trimIndent()

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "metadata",
                    null,
                    metadata.toRequestBody("application/json; charset=utf-8".toMediaType())
                )
                .addFormDataPart(
                    "file",
                    file.name,
                    file.asRequestBody("application/octet-stream".toMediaType())
                )
                .build()

            val request = Request.Builder()
                .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
                .addHeader("Authorization", "Bearer $accessToken")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->

                val bodyString = response.body?.string()
                println("UPLOAD RESPONSE CODE = ${response.code}")
                println("UPLOAD RESPONSE BODY = $bodyString")

                // Parse ID từ JSON Google Drive trả về
                if (bodyString != null && response.isSuccessful) {
                    try {
                        val json = JSONObject(bodyString)
                        val fileId = json.getString("id")

                        val link = "https://drive.google.com/file/d/$fileId/view"
                        println("FILE LINK = $link")   // LINK CLICKABLE TRONG LOGCAT
                    } catch (e: Exception) {
                        println("Cannot parse file ID from response!")
                    }
                }

                return response.isSuccessful
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
