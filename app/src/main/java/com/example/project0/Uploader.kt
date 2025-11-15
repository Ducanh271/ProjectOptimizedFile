package com.example.project0



import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

// Tạo một client duy nhất
private val client = OkHttpClient()

/**
 * Hàm này sẽ đọc file thật và upload lên server test
 * Nó là một "suspend function" nên phải chạy trong Coroutine
 */
suspend fun uploadFileReal(file: File): Boolean { // <-- Sửa 1: Thêm kiểu trả về Boolean
    return try { // <-- Sửa 2: Dùng try/catch như một biểu thức
        val fileBody = file.asRequestBody(guessMediaType(file.name))

        val request = Request.Builder()
            .url("https://httpbin.org/post")
            .post(fileBody)
            .build()

        client.newCall(request).execute().use { response ->
            // Sửa 3: Trả về true/false dựa trên response
            if (!response.isSuccessful) {
                println("Upload failed: ${response.code}")
                false // Trả về false nếu thất bại
            } else {
                println("Upload successful")
                true // Trả về true nếu thành công
            }
        }
    } catch (e: Exception) {
        // Sửa 4: Trả về false nếu có lỗi mạng
        e.printStackTrace()
        false
    }
}

// Hàm nhỏ để đoán loại file
private fun guessMediaType(fileName: String): okhttp3.MediaType? {
    return when {
        fileName.endsWith(".jpg", true) || fileName.endsWith(".jpeg", true) -> "image/jpeg".toMediaTypeOrNull()
        fileName.endsWith(".png", true) -> "image/png".toMediaTypeOrNull()
        fileName.endsWith(".txt", true) -> "text/plain".toMediaTypeOrNull()
        else -> "application/octet-stream".toMediaTypeOrNull() // Mặc định
    }
}