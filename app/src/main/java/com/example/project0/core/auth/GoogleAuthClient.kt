package com.example.project0.core.auth

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class GoogleAuthClient(
    private val context: Context,
    private val clientId: String,       // WEB CLIENT ID
    private val clientSecret: String    // WEB CLIENT SECRET
) {

    private val httpClient = OkHttpClient()

    fun getSignInClient() = GoogleSignIn.getClient(
        context,
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .requestServerAuthCode(clientId, true)
            .requestScopes(
                Scope("https://www.googleapis.com/auth/drive.file")
            )
            .build()
    )

    fun getAccountFromIntent(task: Task<GoogleSignInAccount>): GoogleSignInAccount? {
        return try {
            task.getResult(ApiException::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun fetchAccessToken(authCode: String): String? = withContext(Dispatchers.IO) {
        try {
            val body = FormBody.Builder()
                .add("code", authCode)
                .add("client_id", clientId)
                .add("client_secret", clientSecret)
                .add("redirect_uri", "http://localhost")   // <<< ĐÚNG CHUỖI NÀY
                .add("grant_type", "authorization_code")
                .build()

            val request = Request.Builder()
                .url("https://oauth2.googleapis.com/token")
                .post(body)
                .build()

            val response = OkHttpClient().newCall(request).execute()
            val json = JSONObject(response.body?.string() ?: "{}")

            println("TOKEN RESPONSE = $json")

            json.optString("access_token", null)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

}