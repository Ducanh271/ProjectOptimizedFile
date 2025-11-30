package com.example.project0.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.project0.BuildConfig
import com.example.project0.core.auth.GoogleAuthClient
import com.example.project0.ui.filebrowser.FileScannerScreen
import com.example.project0.viewmodel.FileScannerViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var signInLauncher: ActivityResultLauncher<Intent>
    private lateinit var googleAuthClient: GoogleAuthClient

    private val fileScannerViewModel: FileScannerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        googleAuthClient = GoogleAuthClient(
            this,
            BuildConfig.GOOGLE_CLIENT_ID,
            BuildConfig.GOOGLE_CLIENT_SECRET
        )

        // xin quyền thông báo (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
            }
        }

        // launcher login Google
        signInLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                val account = googleAuthClient.getAccountFromIntent(task)
                if (account != null) {
                    fileScannerViewModel.updateLoggedEmail(account.email ?: "Unknown")
                    val authCode = account.serverAuthCode
                    if (authCode != null) {
                        lifecycleScope.launch {
                            val token = withContext(Dispatchers.IO) {
                                googleAuthClient.fetchAccessToken(authCode)
                            }
                            fileScannerViewModel.updateAccessToken(token)
                        }
                    }
                }
            }

        // nếu trước đó đã login thì lấy email lại
        val lastAcc = GoogleSignIn.getLastSignedInAccount(this)
        if (lastAcc != null) {
            fileScannerViewModel.updateLoggedEmail(lastAcc.email ?: "Unknown")
        }

        enableEdgeToEdge()
        setContent {
            FileScannerScreen(
                viewModel = fileScannerViewModel,
                onLoginClick = {
                    // logout trước rồi login lại
                    googleAuthClient.getSignInClient().signOut().addOnCompleteListener {
                        signInLauncher.launch(
                            googleAuthClient.getSignInClient().signInIntent
                        )
                    }
                }
            )
        }
    }
}
