package com.example.project0

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
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

                return response.isSuccessful
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

