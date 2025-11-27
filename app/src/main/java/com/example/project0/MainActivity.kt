package com.example.project0

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.work.*
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.LinkedList
import java.util.Queue
import kotlin.system.measureTimeMillis
//
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
// --- GLOBAL VARIABLES ---
val sdkInt = Build.VERSION.SDK_INT
private lateinit var googleAuthClient: GoogleAuthClient
private var accessToken: String? = null
val cacheFileName = "scan_cache.json"

class MainActivity : ComponentActivity() {
    private lateinit var signInLauncher: ActivityResultLauncher<Intent>
    var composeLoggedEmail: MutableState<String>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        googleAuthClient = GoogleAuthClient(
            this,
            "318612317826-2ssobtupov5kajb1lrrpjt069t6j44pf.apps.googleusercontent.com",
            "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"
        )
        // >>> THÊM ĐOẠN XIN QUYỀN NÀY <<<
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
        signInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            val account = googleAuthClient.getAccountFromIntent(task)
            if (account != null) {
                composeLoggedEmail?.value = account.email ?: "Unknown"
                val authCode = account.serverAuthCode
                if (authCode != null) {
                    lifecycleScope.launch {
                        val token = withContext(Dispatchers.IO) {
                            googleAuthClient.fetchAccessToken(authCode)
                        }
                        accessToken = token
                    }
                }
            }
        }

        enableEdgeToEdge()
        setContent {
            val loggedEmail = remember { mutableStateOf("Chưa đăng nhập") }
            composeLoggedEmail = loggedEmail
            FileScannerAppModern(signInLauncher, loggedEmail)
        }
    }
}

// --- HELPER FUNCTIONS ---
fun clearCache(context: Context) {
    val cacheDir = File(context.filesDir, "cache_scan")
    val cacheFile = File(cacheDir, cacheFileName)
    if (cacheFile.exists()) cacheFile.delete()
}

fun formatSize(size: Long): String {
    if (size < 1024) return "$size B"
    val kb = size / 1024.0
    if (kb < 1024) return "%.2f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.2f MB".format(mb)
    val gb = mb / 1024.0
    return "%.2f GB".format(gb)
}

fun scanFilesRecursively(directory: File): List<File> {
    val fileList = mutableListOf<File>()
    val files = directory.listFiles() ?: return emptyList()
    for (file in files) {
        if (file.isDirectory) fileList.addAll(scanFilesRecursively(file))
        else fileList.add(file)
    }
    return fileList
}

fun scanFilesIteratively(startDirectory: File): List<File> {
    val fileList = mutableListOf<File>()
    val queue: Queue<File> = LinkedList()
    queue.add(startDirectory)
    while (queue.isNotEmpty()) {
        val currentDir = queue.poll()
        val filesInDir = currentDir.listFiles() ?: continue
        for (file in filesInDir) {
            if (file.isDirectory) queue.add(file)
            else fileList.add(file)
        }
    }
    return fileList
}

fun generateDeepFolder(baseDir: File, depth: Int) {
    var current = baseDir
    current.mkdirs()
    for (i in 1..depth) {
        current = File(current, "f")
        current.mkdirs()
    }
    try { File(current, "dummy_file.txt").createNewFile() } catch (e: Exception) { e.printStackTrace() }
}

// --- CACHE MODELS ---
@Serializable data class CachedFile(val name: String, val size: Long, val path: String)
@Serializable data class CacheData(val rootPath: String, val lastModified: Long, val files: List<CachedFile>)

fun saveCache(context: Context, root: File, files: List<File>) {
    val cacheDir = File(context.filesDir, "cache_scan")
    cacheDir.mkdirs()
    val mapped = files.map { CachedFile(it.name, it.length(), it.absolutePath) }
    val json = Json.encodeToString(CacheData(root.absolutePath, root.lastModified(), mapped))
    File(cacheDir, cacheFileName).writeText(json)
}

fun loadCache(context: Context, root: File): List<File>? {
    val cacheFile = File(File(context.filesDir, "cache_scan"), cacheFileName)
    if (!cacheFile.exists()) return null
    return try {
        val data = Json.decodeFromString<CacheData>(cacheFile.readText())
        if (data.rootPath != root.absolutePath) return null
        data.files.map { File(it.path) }
    } catch (_: Exception) { null }
}

// --- MAIN UI ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileScannerAppModern(
    signInLauncher: ActivityResultLauncher<Intent>,
    loggedEmail: MutableState<String>
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // States
    var files by remember { mutableStateOf<List<File>>(emptyList()) }
    var uploadStatus by remember { mutableStateOf("Sẵn sàng") }
    var isScanning by remember { mutableStateOf(false) }
    var isUploading by remember { mutableStateOf(false) }
    var executionTime by remember { mutableStateOf<Long?>(null) }
    var scanTime by remember { mutableStateOf<Long?>(null) }
    val selectedFiles = remember { mutableStateOf(setOf<File>()) }

    // UI Expandable States
    var showScanOptions by remember { mutableStateOf(true) } // Mặc định mở
    var selectedFolder by remember { mutableStateOf(Environment.DIRECTORY_DOWNLOADS) }
    var isMenuExpanded by remember { mutableStateOf(false) }

    val rootDir = if (selectedFolder == "ALL") Environment.getExternalStorageDirectory()
    else Environment.getExternalStoragePublicDirectory(selectedFolder)

    val trapDir = rootDir

    // Init Login Check
    LaunchedEffect(Unit) {
        val acc = GoogleSignIn.getLastSignedInAccount(context)
        if (acc != null) loggedEmail.value = acc.email ?: "Unknown"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Performance Demo", style = MaterialTheme.typography.titleMedium)
                        Text("Coroutine vs WorkManager", style = MaterialTheme.typography.labelSmall)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        googleAuthClient.getSignInClient().signOut().addOnCompleteListener {
                            signInLauncher.launch(googleAuthClient.getSignInClient().signInIntent)
                        }
                    }) {
                        Icon(Icons.Default.AccountCircle, contentDescription = "Login")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.surface)
        ) {

            // 1. INFO CARD (Account & Folder)
            Card(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Account: ${loggedEmail.value}", style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Folder: ", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            Box {
                                Text(
                                    selectedFolder,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable { isMenuExpanded = true }
                                )
                                DropdownMenu(expanded = isMenuExpanded, onDismissRequest = { isMenuExpanded = false }) {
                                    listOf("ALL", Environment.DIRECTORY_DOWNLOADS, Environment.DIRECTORY_DCIM, Environment.DIRECTORY_PICTURES).forEach { folder ->
                                        DropdownMenuItem(text = { Text(folder) }, onClick = { selectedFolder = folder; isMenuExpanded = false })
                                    }
                                }
                            }
                        }
                    }
                    IconButton(onClick = { showScanOptions = !showScanOptions }) {
                        Icon(if (showScanOptions) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, "Toggle")
                    }
                }
            }

            // 2. SCAN CONTROLS (Collapsible)
            if (showScanOptions) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Scan Methods", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))

                        // Grid of Buttons
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Recursive
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        withContext(Dispatchers.Main) { isScanning = true; uploadStatus = "Scanning..." }
                                        val start = System.nanoTime()
                                        val res = scanFilesRecursively(trapDir)
                                        scanTime = System.nanoTime() - start
                                        withContext(Dispatchers.Main) { isScanning = false; files = res; uploadStatus = "Done" }
                                    }
                                },
                                enabled = !isScanning
                            ) { Text("Recursive", fontSize = 10.sp) }

                            // Iterative
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        withContext(Dispatchers.Main) { isScanning = true; uploadStatus = "Scanning..." }
                                        val start = System.nanoTime()
                                        val res = scanFilesIteratively(trapDir)
                                        scanTime = System.nanoTime() - start
                                        withContext(Dispatchers.Main) { isScanning = false; files = res; uploadStatus = "Done" }
                                    }
                                },
                                enabled = !isScanning
                            ) { Text("Iterative", fontSize = 10.sp) }

                            // Cache
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        val cached = loadCache(context, trapDir)
                                        if(cached != null) {
                                            withContext(Dispatchers.Main) { files = cached; uploadStatus = "Loaded Cache" }
                                        } else {
                                            val res = scanFilesIteratively(trapDir)
                                            saveCache(context, trapDir, res)
                                            withContext(Dispatchers.Main) { files = res; uploadStatus = "Scanned & Cached" }
                                        }
                                    }
                                },
                                enabled = !isScanning
                            ) { Text("Cache", fontSize = 10.sp) }
                        }

                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = {
                                    scope.launch(Dispatchers.IO) { generateDeepFolder(trapDir, 500) }
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("Gen Folder", fontSize = 10.sp) }

                            FilledTonalButton(
                                onClick = { clearCache(context); files = emptyList() },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                            ) { Text("Clear", fontSize = 10.sp) }
                        }
                    }
                }
            }

            // 3. STATUS & LIST HEADER
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Files: ${files.size} (Selected: ${selectedFiles.value.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (scanTime != null) {
                    Text("Time: ${scanTime!! / 1_000_000} ms", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }

            // 4. FILE LIST
            LazyColumn(
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            ) {
                items(files) { file ->
                    val isSelected = selectedFiles.value.contains(file)
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                            .clickable {
                                val current = selectedFiles.value.toMutableSet()
                                if (isSelected) current.remove(file) else current.add(file)
                                selectedFiles.value = current
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { chk ->
                                    val current = selectedFiles.value.toMutableSet()
                                    if (chk) current.add(file) else current.remove(file)
                                    selectedFiles.value = current
                                }
                            )
                            Icon(Icons.Outlined.Folder, contentDescription = null, tint = Color.Gray)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(file.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                Text(formatSize(file.length()), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                        }
                    }
                }
            }

            // 5. UPLOAD ACTIONS (Fixed at bottom)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Status: $uploadStatus", style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    if (executionTime != null) {
                        Text("Upload Time: ${executionTime} ms", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Sequential
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (accessToken == null) { uploadStatus = "No Token!"; return@Button }
                                scope.launch(Dispatchers.IO) {
                                    withContext(Dispatchers.Main) { isUploading = true; executionTime = null }
                                    val t = measureTimeMillis {
                                        selectedFiles.value.forEachIndexed { i, f ->
                                            withContext(Dispatchers.Main) { uploadStatus = "Up ${i+1}/${selectedFiles.value.size}..." }
                                            DriveUploader.uploadToGoogleDrive(accessToken!!, f)
                                        }
                                    }
                                    withContext(Dispatchers.Main) { isUploading = false; executionTime = t; uploadStatus = "Done Sequential" }
                                }
                            },
                            enabled = selectedFiles.value.isNotEmpty() && !isUploading,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Timer, null, modifier = Modifier.size(20.dp))
                                Text("Seq", fontSize = 10.sp)
                            }
                        }

                        // Parallel
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (accessToken == null) { uploadStatus = "No Token!"; return@Button }
                                scope.launch(Dispatchers.IO) {
                                    withContext(Dispatchers.Main) { isUploading = true; executionTime = null; uploadStatus = "Uploading Parallel..." }
                                    val t = measureTimeMillis {
                                        supervisorScope {
                                            selectedFiles.value.map { f -> async { DriveUploader.uploadToGoogleDrive(accessToken!!, f) } }.awaitAll()
                                        }
                                    }
                                    withContext(Dispatchers.Main) { isUploading = false; executionTime = t; uploadStatus = "Done Parallel" }
                                }
                            },
                            enabled = selectedFiles.value.isNotEmpty() && !isUploading
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Bolt, null, modifier = Modifier.size(20.dp))
                                Text("Parallel", fontSize = 10.sp)
                            }
                        }

                        // WorkManager
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (accessToken == null) { uploadStatus = "No Token!"; return@Button }
                                val wm = WorkManager.getInstance(context)
                                val cons = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                                selectedFiles.value.forEach { f ->
                                    val req = OneTimeWorkRequestBuilder<FileUploadWorker>()
                                        .setConstraints(cons)
                                        .setInputData(workDataOf("KEY_FILE_PATH" to f.absolutePath, "KEY_ACCESS_TOKEN" to accessToken))
                                        .build()
                                    wm.enqueue(req)
                                }
                                uploadStatus = "Queued in Background"
                                selectedFiles.value = emptySet()
                            },
                            enabled = selectedFiles.value.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00695C))
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.CloudUpload, null, modifier = Modifier.size(20.dp))
                                Text("Worker", fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- WORKER ---
class FileUploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val path = inputData.getString("KEY_FILE_PATH") ?: return Result.failure()
        val token = inputData.getString("KEY_ACCESS_TOKEN") ?: return Result.failure()
        val file = File(path)

        return if (file.exists()) {
            // Gọi upload
            val success = withContext(Dispatchers.IO) {
                DriveUploader.uploadToGoogleDrive(token, file)
            }

            if (success) {
                // >>> GỬI THÔNG BÁO THÀNH CÔNG <<<
                makeStatusNotification("✅ Upload xong: ${file.name}", applicationContext)
                Result.success()
            } else {
                // >>> GỬI THÔNG BÁO THẤT BẠI (Tùy chọn) <<<
                makeStatusNotification("❌ Lỗi upload: ${file.name}", applicationContext)
                Result.retry()
            }
        } else {
            Result.failure()
        }
    }
}
//class FileUploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
//    override suspend fun doWork(): Result {
//        val path = inputData.getString("KEY_FILE_PATH") ?: return Result.failure()
//        val token = inputData.getString("KEY_ACCESS_TOKEN") ?: return Result.failure()
//        val file = File(path)
//        return if (file.exists()) {
//            val success = withContext(Dispatchers.IO) { DriveUploader.uploadToGoogleDrive(token, file) }
//            if (success) Result.success() else Result.retry()
//        } else Result.failure()
//    }
//}

//package com.example.project0
//
//import android.content.Intent
//import android.os.Build
//import android.os.Bundle
//import android.os.Environment
//import android.provider.Settings
//import androidx.activity.ComponentActivity
//import androidx.activity.compose.setContent
//import androidx.activity.enableEdgeToEdge
//import androidx.activity.result.ActivityResultLauncher
//import androidx.activity.result.contract.ActivityResultContracts
//import androidx.compose.foundation.clickable
//import androidx.compose.foundation.layout.*
//import androidx.compose.foundation.lazy.LazyColumn
//import androidx.compose.foundation.lazy.items
//import androidx.compose.material3.Button
//import androidx.compose.material3.Checkbox
//import androidx.compose.material3.DropdownMenu
//import androidx.compose.material3.DropdownMenuItem
//import androidx.compose.material3.ExperimentalMaterial3Api
//import androidx.compose.material3.MaterialTheme
//import androidx.compose.material3.Scaffold
//import androidx.compose.material3.Text
//import androidx.compose.material3.TopAppBar
//import androidx.compose.runtime.Composable
//import androidx.compose.runtime.LaunchedEffect
//import androidx.compose.runtime.MutableState
//import androidx.compose.runtime.getValue
//import androidx.compose.runtime.mutableStateOf
//import androidx.compose.runtime.remember
//import androidx.compose.runtime.rememberCoroutineScope
//import androidx.compose.runtime.setValue
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.platform.LocalContext
//import androidx.compose.ui.unit.dp
//import androidx.core.net.toUri
//import androidx.lifecycle.lifecycleScope
//import com.google.android.gms.auth.api.signin.GoogleSignIn
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.launch
//import kotlinx.coroutines.withContext
//import java.io.File
//import java.util.LinkedList
//import java.util.Queue
//
////import caching
//import kotlinx.serialization.Serializable
//import kotlinx.serialization.encodeToString
//import kotlinx.serialization.json.Json
////
//import kotlin.system.measureTimeMillis
//import kotlinx.coroutines.async
//import kotlinx.coroutines.awaitAll
//import kotlinx.coroutines.supervisorScope
////
//import android.content.Context
//import androidx.work.CoroutineWorker
//import androidx.work.WorkerParameters
//import androidx.work.workDataOf
//import kotlinx.coroutines.withContext
//val sdkInt = Build.VERSION.SDK_INT
//private lateinit var googleAuthClient: GoogleAuthClient
//private var accessToken: String? = null
//
//
//class MainActivity : ComponentActivity() {
//    private lateinit var signInLauncher: ActivityResultLauncher<Intent>
//
//    var composeLoggedEmail: MutableState<String>? = null
//
//    //var loggedEmail by remember { mutableStateOf("Chưa đăng nhập") }
//
//    //var loggedEmail = mutableStateOf("Chưa đăng nhập")
//
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//
//        super.onCreate(savedInstanceState)
////BO COMMENT NAY
//        googleAuthClient = GoogleAuthClient(
//            this,
//            "318612317826-2ssobtupov5kajb1lrrpjt069t6j44pf.apps.googleusercontent.com",
//            "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"
//        )
//        // Tạo launcher đăng nhập
//        signInLauncher =
//            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
//
//                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
//                val account = googleAuthClient.getAccountFromIntent(task)
//
//                if (account != null) {
//                    // UPDATE state của Compose thông qua biến trong Compose
//                    composeLoggedEmail?.value = account.email ?: "Không rõ email"
//
//
//                    val authCode = account.serverAuthCode
//                    println("AUTH CODE = $authCode")
//                    if (authCode != null) {
//                        lifecycleScope.launch {
//                            val token = withContext(Dispatchers.IO) {
//                                googleAuthClient.fetchAccessToken(authCode)
//                            }
//
//                            println("ACCESS TOKEN = $token")
//
//                            accessToken = token    // >>> chạy ở Main thread
//                        }
//
//                    }
//
//                }
//            }
//
//
//        enableEdgeToEdge()
//
//        setContent {
//            val loggedEmail = remember { mutableStateOf("Chưa đăng nhập") }
//
//            // save reference để Activity có thể update Compose state
//            composeLoggedEmail = loggedEmail
//
//            Column {
//                Text("SDK version: $sdkInt")
//                FileScannerAppV1(signInLauncher, loggedEmail)
//
//            }
//        }
//    }
//}
////xoa cache
//
//fun clearCache(context: android.content.Context) {
//    val cacheDir = File(context.filesDir, "cache_scan")
//    val cacheFile = File(cacheDir, cacheFileName)
//    if (cacheFile.exists()) cacheFile.delete()
//}
//
//
//fun signOutGoogle() {
//    googleAuthClient.getSignInClient().signOut()
//}
//
//
///**
// * Hàm đệ quy "chưa tối ưu" để quét file.
// * Rất dễ gây StackOverflowError nếu thư mục quá sâu.
// */
//fun scanFilesRecursively(directory: File): List<File> {
//    val fileList = mutableListOf<File>()
//    val files = directory.listFiles() ?: return emptyList()
//
//    for (file in files) {
//        if (file.isDirectory) {
//            // Lời gọi đệ quy – dễ gây StackOverflow nếu cây thư mục quá sâu
//            fileList.addAll(scanFilesRecursively(file))
//        } else {
//            fileList.add(file)
//        }
//    }
//    return fileList
//}
//
///**
// * Hàm đã khử đệ quy – dùng Queue để duyệt cây thư mục.
// */
//fun scanFilesIteratively(startDirectory: File): List<File> {
//    val fileList = mutableListOf<File>()
//    val queue: Queue<File> = LinkedList()
//    queue.add(startDirectory)
//
//    while (queue.isNotEmpty()) {
//        val currentDir = queue.poll()
//        val filesInDir = currentDir.listFiles() ?: continue
//
//        for (file in filesInDir) {
//            if (file.isDirectory) {
//                queue.add(file)
//            } else {
//                fileList.add(file)
//            }
//        }
//    }
//    return fileList
//}
//
///**
// * Tạo một cây thư mục sâu để demo crash/khử đệ quy.
// */
//fun generateDeepFolder(baseDir: File, depth: Int) {
//    var current = baseDir
//    current.mkdirs()
//
//    for (i in 1..depth) {
//        current = File(current, "f")
//        current.mkdirs()
//    }
//
//    try {
//        val dummyFile = File(current, "dummy_file.txt")
//        dummyFile.createNewFile()
//    } catch (e: Exception) {
//        e.printStackTrace()
//    }
//}
//
///**
// * Định dạng kích thước file cho dễ đọc.
// */
//fun formatSize(size: Long): String {
//    if (size < 1024) return "$size B"
//    val kb = size / 1024.0
//    if (kb < 1024) return "%.2f KB".format(kb)
//    val mb = kb / 1024.0
//    if (mb < 1024) return "%.2f MB".format(mb)
//    val gb = mb / 1024.0
//    return "%.2f GB".format(gb)
//}
//
//@Serializable
//data class CachedFile(
//    val name: String,
//    val size: Long,
//    val path: String
//)
//
//@Serializable
//data class CacheData(
//    val rootPath: String,
//    val lastModified: Long,
//    val files: List<CachedFile>
//)
//
//val cacheFileName = "scan_cache.json"
//
//
////Ham luu cache
//fun saveCache(context: android.content.Context, root: File, files: List<File>) {
//    val cacheDir = File(context.filesDir, "cache_scan")
//    cacheDir.mkdirs()
//
//    val cacheFile = File(cacheDir, cacheFileName)
//
//    val mapped = files.map {
//        CachedFile(it.name, it.length(), it.absolutePath)
//    }
//
//    val json = Json.encodeToString(
//        CacheData(
//            rootPath = root.absolutePath,
//            lastModified = root.lastModified(),
//            files = mapped
//        )
//    )
//
//    cacheFile.writeText(json)
//}
//
////ham load cache
//fun loadCache(context: android.content.Context, root: File): List<File>? {
//    val cacheDir = File(context.filesDir, "cache_scan")
//    val cacheFile = File(cacheDir, cacheFileName)
//    if (!cacheFile.exists()) return null
//
//    val json = cacheFile.readText()
//
//    return try {
//        val data = Json.decodeFromString<CacheData>(json)
//
//        if (data.rootPath != root.absolutePath) return null
//        if (data.lastModified != root.lastModified()) return null
//
//        data.files.map { File(it.path) }
//
//    } catch (_: Exception) {
//        null
//    }
//}
//
//
//@OptIn(ExperimentalMaterial3Api::class)
//
//@Composable
//fun FileScannerAppV1(
//    signInLauncher: ActivityResultLauncher<Intent>,
//    loggedEmail: MutableState<String>
//) {
//    val context = LocalContext.current   // CHỈ ĐỂ DÒNG NÀY
//
//
//    LaunchedEffect(Unit) {
//        val acc = GoogleSignIn.getLastSignedInAccount(context)
//        if (acc != null) {
//            loggedEmail.value = acc.email ?: "Không rõ email"
//        }
//    }
//
//    var files by remember { mutableStateOf<List<File>>(emptyList()) }
//    var uploadStatus by remember { mutableStateOf("Chưa upload") }
//    var isGenerating by remember { mutableStateOf(false) }
//    var isScanning by remember { mutableStateOf(false) }
//
//    var isUploading by remember { mutableStateOf(false) }
//    var executionTime by remember { mutableStateOf<Long?>(null) }
//    val selectedFiles = remember { mutableStateOf(setOf<File>()) }
//    //bien tinh thgi scannfile
//    var scanTime by remember { mutableStateOf<Long?>(null) }
//
//    //val context = LocalContext.current
//    val scope = rememberCoroutineScope()
//
//    val rootFolders = listOf(
//        "ALL",
//        Environment.DIRECTORY_DOWNLOADS,
//        Environment.DIRECTORY_DOCUMENTS,
//        Environment.DIRECTORY_DCIM,
//        Environment.DIRECTORY_MOVIES,
//        Environment.DIRECTORY_PICTURES
//    )
//
//    var selectedFolder by remember { mutableStateOf(Environment.DIRECTORY_DOWNLOADS) }
//    var isMenuExpanded by remember { mutableStateOf(false) }
//
//    val rootDir =
//        if (selectedFolder == "ALL") {
//            Environment.getExternalStorageDirectory()
//        } else {
//            Environment.getExternalStoragePublicDirectory(selectedFolder)
//        }
//
//    // Có thể dùng rootDir trực tiếp làm thư mục “bẫy”
//    val trapDir = rootDir
//
//
//    Scaffold(
//        topBar = { TopAppBar(title = { Text("File Scanner V1 (Chưa tối ưu)") }) }
//    ) { paddingValues ->
//        Column(
//            modifier = Modifier
//                .fillMaxSize()
//                .padding(paddingValues)
//                .padding(16.dp)
//        ) {
//            // Chọn thư mục gốc
//            Column {
//
//                Button(
//                    onClick = {
//                        // 1) Sign out tài khoản hiện tại để tránh cache
//                        googleAuthClient.getSignInClient().signOut().addOnCompleteListener {
//
//                            // 2) Sau khi sign out → mở UI đăng nhập mới
//                            val intent = googleAuthClient.getSignInClient().signInIntent
//                            signInLauncher.launch(intent)
//                        }
//                    }
//                ) {
//                    Text("Đăng nhập Google Drive (Đổi tài khoản)")
//                }
//
//               // Text("Tài khoản hiện tại: $loggedEmail")
//
//                Text("Tài khoản hiện tại: ${loggedEmail.value}")
//
//
//                Text("Chọn thư mục gốc:")
//                Button(onClick = { isMenuExpanded = true }) {
//                    Text(selectedFolder)
//                }
//                DropdownMenu(
//                    expanded = isMenuExpanded,
//                    onDismissRequest = { isMenuExpanded = false }
//                ) {
//                    rootFolders.forEach { folderName ->
//                        DropdownMenuItem(
//                            text = { Text(folderName) },
//                            onClick = {
//                                selectedFolder = folderName
//                                isMenuExpanded = false
//                            }
//                        )
//                    }
//                }
//            }
//
//            Spacer(modifier = Modifier.height(16.dp))
//
//            // Nút Scan ĐỆ QUY (chưa tối ưu)
//            Button(
//                onClick = {
//                    val scanLogic: () -> Unit = {
//                        if (trapDir.exists()) {
//                            scope.launch(Dispatchers.IO) {
//                                withContext(Dispatchers.Main) {
//                                    scanTime = null
//                                    isScanning = true
//                                    uploadStatus = "Đang quét (dùng đệ quy)..."
//                                }
//
//                                //val result = scanFilesRecursively(trapDir)
//
//                                val start = System.nanoTime()
//                                val result = scanFilesRecursively(trapDir)
//                                val end = System.nanoTime()
//                                scanTime = end - start
//
//
//                                withContext(Dispatchers.Main) {
//                                    isScanning = false
//                                    uploadStatus = "Quét xong (đệ quy)"
//                                    files = result
//                                }
//                            }
//                        } else {
//                            uploadStatus = "Thư mục không tồn tại!"
//                        }
//                    }
//
//                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
//                        if (Environment.isExternalStorageManager()) {
//                            scanLogic()
//                        } else {
//                            val intent =
//                                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
//                            intent.data = "package:${context.packageName}".toUri()
//                            context.startActivity(intent)
//                        }
//                    } else {
//                        scanLogic()
//                    }
//                },
//                enabled = !isGenerating && !isScanning
//            ) {
//                if (isScanning) Text("Đang quét...") else Text("Scan File (Dùng đệ quy)")
//            }
//
//            Spacer(modifier = Modifier.height(16.dp))
//
//            // Nút Scan ĐÃ KHỬ ĐỆ QUY
//            Button(
//                onClick = {
//                    val scanLogic: () -> Unit = {
//                        if (trapDir.exists()) {
//                            scope.launch(Dispatchers.IO) {
//                                withContext(Dispatchers.Main) {
//                                    scanTime = null
//                                    isScanning = true
//                                    uploadStatus = "Đang quét (khử đệ quy)..."
//                                }
//
//                              //  val result = scanFilesIteratively(trapDir)
//
//                                val start = System.nanoTime()
//                                val result = scanFilesIteratively(trapDir)
//                                val end = System.nanoTime()
//                                scanTime = end - start
//
//
//                                withContext(Dispatchers.Main) {
//                                    isScanning = false
//                                    uploadStatus = "Quét xong (khử đệ quy)"
//                                    files = result
//                                }
//                            }
//                        } else {
//                            uploadStatus = "Thư mục không tồn tại!"
//                        }
//                    }
//
//                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
//                        if (Environment.isExternalStorageManager()) {
//                            scanLogic()
//                        } else {
//                            val intent =
//                                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
//                            intent.data = "package:${context.packageName}".toUri()
//                            context.startActivity(intent)
//                        }
//                    } else {
//                        scanLogic()
//                    }
//                },
//                enabled = !isGenerating && !isScanning
//            ) {
//                if (isScanning) Text("Đang quét...") else Text("Scan File đã khử đệ quy")
//            }
//
//            Spacer(modifier = Modifier.height(16.dp))
//
//            //nut scan dequy + cache
//            Button(
//                onClick = {
//                    val logic = {
//                        scope.launch(Dispatchers.IO) {
//
//                            val cached = loadCache(context, trapDir)
//                            if (cached != null) {
//                                val start = System.nanoTime()
//                                val end = System.nanoTime()
//                                scanTime = end - start
//
//                                withContext(Dispatchers.Main) {
//                                    uploadStatus = "Dùng cache (Đệ quy)"
//                                    files = cached
//                                }
//                                return@launch
//                            }
//
//                            withContext(Dispatchers.Main) {
//                                scanTime = null
//                                isScanning = true
//                                uploadStatus = "Đang quét (Đệ quy + Cache)..."
//                            }
//
//                            val start = System.nanoTime()
//                            val result = scanFilesRecursively(trapDir)
//
//
//
//                            saveCache(context, trapDir, result)
//                            val end = System.nanoTime()
//                            scanTime = end - start
//
//                            withContext(Dispatchers.Main) {
//                                isScanning = false
//                                uploadStatus = "Hoàn tất (Đệ quy + Cache)"
//                                files = result
//                            }
//                        }
//                    }
//
//                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
//                        !Environment.isExternalStorageManager()
//                    ) {
//                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
//                        intent.data = "package:${context.packageName}".toUri()
//                        context.startActivity(intent)
//                    } else logic()
//                },
//                enabled = !isScanning && !isGenerating
//            ) {
//                Text("Scan File (Đệ quy + Cache)")
//            }
//
//
//            Spacer(modifier = Modifier.height(16.dp))
//
//            //Nut scan khu de quy + cache
//            Button(
//                onClick = {
//                    val logic = {
//                        scope.launch(Dispatchers.IO) {
//
//                            val cached = loadCache(context, trapDir)
//                            if (cached != null) {
//                                val start = System.nanoTime()
//                                val end = System.nanoTime()
//                                scanTime = end - start
//
//                                withContext(Dispatchers.Main) {
//                                    uploadStatus = "Dùng cache (Iterative)"
//                                    files = cached
//                                }
//                                return@launch
//                            }
//
//                            withContext(Dispatchers.Main) {
//                                scanTime = null
//                                isScanning = true
//                                uploadStatus = "Đang quét (Iterative + Cache)..."
//                            }
//
//                            val start = System.nanoTime()
//                            val result = scanFilesIteratively(trapDir)
//                            saveCache(context, trapDir, result)
//                            val end = System.nanoTime()
//                            scanTime = end - start
//
//                            withContext(Dispatchers.Main) {
//                                isScanning = false
//                                uploadStatus = "Hoàn tất (Iterative + Cache)"
//                                files = result
//                            }
//                        }
//                    }
//
//                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
//                        !Environment.isExternalStorageManager()
//                    ) {
//                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
//                        intent.data = "package:${context.packageName}".toUri()
//                        context.startActivity(intent)
//                    } else logic()
//                },
//                enabled = !isScanning && !isGenerating
//            ) {
//                Text("Scan File (Khử đệ quy + Cache)")
//            }
//
//
//            Spacer(modifier = Modifier.height(16.dp))
//
//            Row(
//                modifier = Modifier.fillMaxWidth(),
//                horizontalArrangement = Arrangement.SpaceBetween,
//                verticalAlignment = Alignment.CenterVertically
//            ) {
//                // Nút Generate Deep Folder
//                Button(
//                    onClick = {
//                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
//                            Environment.isExternalStorageManager()
//                        ) {
//                            scope.launch(Dispatchers.IO) {
//                                withContext(Dispatchers.Main) {
//                                    isGenerating = true
//                                    uploadStatus = "Đang tạo 1,000 thư mục..."
//                                }
//
//                                generateDeepFolder(trapDir, 1000)
//
//                                withContext(Dispatchers.Main) {
//                                    isGenerating = false
//                                    uploadStatus = "Đã tạo xong deep folder!"
//                                }
//                            }
//                        } else {
//                            uploadStatus = "Cần cấp quyền 'Tất cả tệp' trước!"
//                        }
//                    },
//                    enabled = !isGenerating && !isScanning,
//                    modifier = Modifier.weight(1f)
//                ) {
//                    if (isGenerating) {
//                        Text("Đang tạo...")
//                    } else {
//                        Text("Generate Deep Folder")
//                    }
//                }
//
//                Spacer(modifier = Modifier.width(12.dp))
//
//                // Nút XÓA CACHE
//                Button(
//                    onClick = {
//                        clearCache(context)
//                        uploadStatus = "Đã xóa cache!"
//                        scanTime = null     // reset hiển thị thời gian
//                        files = emptyList() // tùy chọn: xoá danh sách file
//                    },
//                    enabled = !isGenerating && !isScanning,
//                    modifier = Modifier.weight(1f)
//                ) {
//                    Text("Xóa Cache")
//                }
//            }
//
//// --- KHU VỰC ĐIỀU KHIỂN UPLOAD (QUAN TRỌNG) ---
//            Text("Đã chọn: ${selectedFiles.value.size} file",
//                style = MaterialTheme.typography.titleMedium,
//                modifier = Modifier.padding(vertical = 8.dp)
//                )
//
//            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
//                // NÚT 1: UPLOAD TUẦN TỰ (Thông thường)
//                Button(
//                    modifier = Modifier.weight(1f),
//                    enabled = selectedFiles.value.isNotEmpty() && !isUploading && accessToken != null,
//                    onClick = {
//                        scope.launch(Dispatchers.IO) {
//                            withContext(Dispatchers.Main) {
//                                isUploading = true
//                                executionTime = null
//                                uploadStatus = "Đang upload Tuần tự..."
//                            }
//                            // Đo thời gian chạy
//                            val time = measureTimeMillis {
//                                selectedFiles.value.forEachIndexed { index, file ->
//                                    // Thông báo đang up file nào
//                                    withContext(Dispatchers.Main) {
//                                        uploadStatus = "Đang up (${index + 1}/${selectedFiles.value.size}): ${file.name}..."
//                                    }
//
//                                    // Upload file này xong mới tới file tiếp theo (Blocking)
//                                    val ok = DriveUploader.uploadToGoogleDrive(accessToken!!, file)
//
//                                    if (!ok) println("Lỗi upload file: ${file.name}")
//                                }
//                            }
//
//                            withContext(Dispatchers.Main) {
//                                isUploading = false
//                                executionTime = time
//                                uploadStatus = "Hoàn tất Tuần tự!"
//                            }
//                        }
//                    }
//                ) {
//                    Text("Up Tuần tự\n(Chậm)")
//                }
//
//                // NÚT 2: UPLOAD SONG SONG (Coroutine Async)
//                Button(
//                    modifier = Modifier.weight(1f),
//                    enabled = selectedFiles.value.isNotEmpty() && !isUploading && accessToken != null,
//                    onClick = {
//                        scope.launch(Dispatchers.IO) {
//                            withContext(Dispatchers.Main) {
//                                isUploading = true
//                                executionTime = null
//                                uploadStatus = "Đang upload Song song..."
//                            }
//
//                            val time = measureTimeMillis {
//                                // supervisorScope: Nếu 1 file lỗi, các file khác vẫn chạy tiếp
//                                supervisorScope {
//                                    // B1: Tạo danh sách các tác vụ (Deferred jobs)
//                                    // Nó bắn lệnh upload ngay lập tức, không chờ
//                                    val jobs = selectedFiles.value.map { file ->
//                                        async {
//                                            val ok = DriveUploader.uploadToGoogleDrive(accessToken!!, file)
//                                            // Có thể update UI từng phần nhỏ ở đây nếu muốn
//                                            ok
//                                        }
//                                    }
//
//                                    // B2: Chờ tất cả các tác vụ cùng về đích
//                                    jobs.awaitAll()
//                                }
//                            }
//
//                            withContext(Dispatchers.Main) {
//                                isUploading = false
//                                executionTime = time
//                                uploadStatus = "Hoàn tất Song song!"
//                            }
//                        }
//                    }
//                ) {
//                    Text("Up Song song\n(Nhanh)")
//                }
//            }
//            Spacer(modifier = Modifier.height(8.dp))
//
//            // Hàng 2: Nút WorkManager (Upload Ngầm)
//            Button(
//                modifier = Modifier.fillMaxWidth(), // Chiếm hết chiều ngang
//                enabled = selectedFiles.value.isNotEmpty() && accessToken != null,
//                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
//                    containerColor = androidx.compose.ui.graphics.Color(0xFF00695C) // Màu xanh đậm khác biệt
//                ),
//                onClick = {
//                    val workManager = androidx.work.WorkManager.getInstance(context)
//
//                    // 1. Tạo ràng buộc: Chỉ chạy khi CÓ MẠNG (tránh tốn pin vô ích)
//                    val constraints = androidx.work.Constraints.Builder()
//                        .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
//                        .build()
//
//                    // 2. Duyệt qua từng file và tạo lệnh bài (Request)
//                    selectedFiles.value.forEach { file ->
//                        val inputData = androidx.work.workDataOf(
//                            "KEY_FILE_PATH" to file.absolutePath,
//                            "KEY_ACCESS_TOKEN" to accessToken
//                        )
//
//                        val uploadRequest = androidx.work.OneTimeWorkRequestBuilder<FileUploadWorker>()
//                            .setConstraints(constraints)
//                            .setInputData(inputData)
//                            .addTag("upload_tag") // Để quản lý nhóm nếu cần
//                            .build()
//
//                        // 3. Gửi lệnh đi (Fire and Forget)
//                        workManager.enqueue(uploadRequest)
//                    }
//
//                    uploadStatus = "Đã giao ${selectedFiles.value.size} file cho hệ thống.\nBạn có thể tắt app ngay bây giờ!"
//                    // Reset lựa chọn sau khi gửi
//                    selectedFiles.value = emptySet()
//                }
//            ) {
//                Text("3. Upload Ngầm (WorkManager) - Tắt app vẫn chạy")
//            }
//            // Hiển thị kết quả thời gian
//            if (executionTime != null) {
//                Text(
//                    text = "⏱️ Thời gian chạy: ${executionTime} ms (${executionTime!! / 1000}s)",
//                    color = MaterialTheme.colorScheme.primary,
//                    style = MaterialTheme.typography.titleMedium,
//                    modifier = Modifier.padding(vertical = 8.dp)
//                )
//            }
//            Text("Trạng thái: $uploadStatus", style = MaterialTheme.typography.bodyMedium)
//
//
//            Spacer(modifier = Modifier.height(16.dp))
//
//            // Danh sách file
//            Text("File List:", style = MaterialTheme.typography.headlineSmall)
//            LazyColumn(modifier = Modifier.weight(1f)) {
//                items(files) { file ->
//                    val isSelected = selectedFiles.value.contains(file)
//                    Row(
//                        modifier = Modifier
//                            .fillMaxWidth()
//                            .clickable {
//                                // Logic chọn/bỏ chọn file
//                                val currentSet = selectedFiles.value.toMutableSet()
//                                if (isSelected) currentSet.remove(file) else currentSet.add(file)
//                                selectedFiles.value     = currentSet
//                            }
//                            .padding(4.dp),
//                        horizontalArrangement = Arrangement.SpaceBetween
//                    ) {
//                        // 1. Checkbox ở đầu dòng
//                        Checkbox(
//                            checked = isSelected,
//                            onCheckedChange = { checked ->
//                                val currentSet = selectedFiles.value.toMutableSet()
//                                if (checked) currentSet.add(file) else currentSet.remove(file)
//                                selectedFiles.value = currentSet
//                            }
//                        )
//                        Spacer(modifier = Modifier.width(8.dp))
//                        Column(modifier = Modifier.weight(1f)) {
//                            Text(file.name)
//                            Text(
//                                formatSize(file.length()),
//                                style = MaterialTheme.typography.bodySmall
//                            )
//                        }
//
//                        Button(onClick = {
//                            //  >>> THAY BẰNG KIỂM TRA RÕ RÀNG HƠN
//                            if (accessToken == null) {
//                                println("Upload FAIL: accessToken = null")     // <<< LOG
//                                uploadStatus = "Token null — chưa lấy được access_token!"
//                                return@Button
//                            }
//
//                            scope.launch(Dispatchers.IO) {
//                                withContext(Dispatchers.Main) {
//                                    uploadStatus = "Uploading ${file.name} lên Drive..."
//                                }
//
//                                val ok = DriveUploader.uploadToGoogleDrive(accessToken!!, file)
//
//                                withContext(Dispatchers.Main) {
//                                    uploadStatus = if (ok) "Upload thành công!" else "Upload thất bại!"
//                                }
//                            }
//                        }) {
//                            Text("Upload lên Drive")
//                        }
//
//
//                    }
//                }
//            }
//
//            Spacer(modifier = Modifier.height(16.dp))
//            Text("Trạng thái: $uploadStatus")
//
//            //UI hien thi thoi gian
//            scanTime?.let { ns ->
//                val ms = ns / 1_000_000.0
//                Text("Thời gian quét: ${ns} ns (≈ %.3f ms)".format(ms))
//            }
//
//        }
//    }
//}
//
//class FileUploadWorker(
//    context: Context,
//    workerParams: WorkerParameters
//) : CoroutineWorker(context, workerParams) {
//
//    override suspend fun doWork(): Result {
//        // 1. Lấy dữ liệu đầu vào
//        val filePath = inputData.getString("KEY_FILE_PATH") ?: return Result.failure()
//        val accessToken = inputData.getString("KEY_ACCESS_TOKEN") ?: return Result.failure()
//
//        val file = File(filePath)
//        if (!file.exists()) return Result.failure()
//
//        // 2. Gọi hàm upload (Tái sử dụng DriveUploader cũ)
//        // CoroutineWorker mặc định chạy trên Dispatchers.Default,
//        // nhưng ta chuyển sang IO cho chắc chắn vì là tác vụ mạng.
//        val isSuccess = withContext(Dispatchers.IO) {
//            try {
//                // Log để bạn soi Logcat thấy nó chạy ngầm
//                println("Worker đang chạy ngầm: Uploading ${file.name}...")
//                DriveUploader.uploadToGoogleDrive(accessToken, file)
//            } catch (e: Exception) {
//                e.printStackTrace()
//                false
//            }
//        }
//
//        // 3. Trả kết quả
//        return if (isSuccess) {
//            Result.success(workDataOf("KEY_OUTPUT_NAME" to file.name))
//        } else {
//            // Nếu thất bại (ví dụ mất mạng), WorkManager sẽ tự động thử lại sau
//            Result.retry()
//        }
//    }
//}