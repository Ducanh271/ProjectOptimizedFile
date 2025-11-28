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
import java.io.File
import java.util.LinkedList
import java.util.Queue
import kotlin.system.measureTimeMillis
//
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

// --- GLOBAL VARIABLES ---
val sdkInt = Build.VERSION.SDK_INT
private lateinit var googleAuthClient: GoogleAuthClient
private var accessToken: String? = null

var memoryCacheBinary: ByteArray? = null
var memoryCacheFiles: List<File>? = null

class MainActivity : ComponentActivity() {
    private lateinit var signInLauncher: ActivityResultLauncher<Intent>
    var composeLoggedEmail: MutableState<String>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        googleAuthClient = GoogleAuthClient(
            this,
            BuildConfig.GOOGLE_CLIENT_ID,     // Đã thay chuỗi cứng
            BuildConfig.GOOGLE_CLIENT_SECRET  // Đã thay chuỗi cứng
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

// ========================================================
// BINARY CACHE (DISK + MEMORY)
// ========================================================

// Save binary to disk + to memory
fun saveCacheBinary(context: Context, root: File, files: List<File>) {
    val dir = File(context.filesDir, "cache_bin")
    dir.mkdirs()
    val file = File(dir, "scan_cache.bin")

    val baos = ByteArrayOutputStream()
    val out = DataOutputStream(baos)

    // Write header
    out.writeUTF(root.absolutePath)
    out.writeLong(root.lastModified())
    out.writeInt(files.size)

    // Write all files
    for (f in files) {
        out.writeUTF(f.absolutePath)
        out.writeLong(f.length())
    }

    out.flush()
    val binaryData = baos.toByteArray()

    // Save to disk
    file.writeBytes(binaryData)

    // Save to memory
    memoryCacheBinary = binaryData
    memoryCacheFiles = files.toList()
}

fun loadCacheBinaryFromDisk(context: Context, root: File): List<File>? {
    val file = File(File(context.filesDir, "cache_bin"), "scan_cache.bin")
    if (!file.exists()) return null

    return decodeBinaryCache(file.readBytes(), root)
}

fun loadCacheBinaryFromMemory(root: File): List<File>? {
    val data = memoryCacheBinary ?: return null
    return decodeBinaryCache(data, root)
}

fun decodeBinaryCache(binary: ByteArray, root: File): List<File>? {
    return try {
        val inp = DataInputStream(ByteArrayInputStream(binary))

        val savedRoot = inp.readUTF()
        if (savedRoot != root.absolutePath) return null

        val savedModified = inp.readLong()
        if (savedModified != root.lastModified()) return null

        val count = inp.readInt()
        val list = ArrayList<File>(count)

        repeat(count) {
            val path = inp.readUTF()
            inp.readLong() // skip size
            list.add(File(path))
        }

        list
    } catch (e: Exception) {
        null
    }
}

fun clearCache(context: Context) {
    memoryCacheBinary = null
    memoryCacheFiles = null

    val dir = File(context.filesDir, "cache_bin")
    val file = File(dir, "scan_cache.bin")
    if (file.exists()) file.delete()
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
                                        DropdownMenuItem(text = { Text(folder) },
                                            onClick = {
                                                selectedFolder = folder;
                                                isMenuExpanded = false
                                                // reset memory cache khi đổi folder
                                                memoryCacheBinary = null
                                                memoryCacheFiles = null
                                            })
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

                            // Cache Disk
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        val start = System.nanoTime()  // đo thời gian

                                        val cached = loadCacheBinaryFromDisk(context, trapDir)
                                        if (cached != null) {
                                            val end = System.nanoTime()
                                            scanTime = end - start

                                            withContext(Dispatchers.Main) {
                                                files = cached
                                                uploadStatus = "Loaded Binary Disk"
                                            }
                                        } else {
                                            val res = scanFilesIteratively(trapDir)
                                            saveCacheBinary(context, trapDir, res)

                                            val end = System.nanoTime()
                                            scanTime = end - start

                                            withContext(Dispatchers.Main) {
                                                files = res
                                                uploadStatus = "Scanned + Cached (Binary Disk)"
                                            }
                                        }
                                    }
                                }
                            ) { Text("Cache Disk", fontSize = 10.sp) }


                            // Cache Memory
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        val start = System.nanoTime()  // đo thời gian

                                        val cached = loadCacheBinaryFromMemory(trapDir)
                                        if (cached != null) {
                                            val end = System.nanoTime()
                                            scanTime = end - start

                                            withContext(Dispatchers.Main) {
                                                files = cached
                                                uploadStatus = "Loaded Binary Memory"
                                            }
                                        } else {
                                            val res = scanFilesIteratively(trapDir)
                                            saveCacheBinary(context, trapDir, res)

                                            val end = System.nanoTime()
                                            scanTime = end - start

                                            withContext(Dispatchers.Main) {
                                                files = res
                                                uploadStatus = "Scanned + Cached (Memory)"
                                            }
                                        }
                                    }
                                }
                            ) { Text("Cache Mem", fontSize = 10.sp) }
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
                    val ns = scanTime!!
                    val ms = ns / 1_000_000.0
                    Text("Time: ${ns} ns  (${String.format("%.4f", ms)} ms)",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
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