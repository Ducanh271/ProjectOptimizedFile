package com.example.project0

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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.LinkedList
import java.util.Queue

//import caching
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json


val sdkInt = Build.VERSION.SDK_INT
private lateinit var googleAuthClient: GoogleAuthClient
private var accessToken: String? = null


class MainActivity : ComponentActivity() {
    private lateinit var signInLauncher: ActivityResultLauncher<Intent>

    var composeLoggedEmail: MutableState<String>? = null

    //var loggedEmail by remember { mutableStateOf("Chưa đăng nhập") }

    //var loggedEmail = mutableStateOf("Chưa đăng nhập")


    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
//BO COMMENT NAY
        googleAuthClient = GoogleAuthClient(
            this,
            "318612317826-2ssobtupov5kajb1lrrpjt069t6j44pf.apps.googleusercontent.com",
            "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"
        )
        // Tạo launcher đăng nhập
        signInLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->

                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                val account = googleAuthClient.getAccountFromIntent(task)

                if (account != null) {
                    // UPDATE state của Compose thông qua biến trong Compose
                    composeLoggedEmail?.value = account.email ?: "Không rõ email"


                    val authCode = account.serverAuthCode
                    println("AUTH CODE = $authCode")
                    if (authCode != null) {
                        lifecycleScope.launch {
                            val token = withContext(Dispatchers.IO) {
                                googleAuthClient.fetchAccessToken(authCode)
                            }

                            println("ACCESS TOKEN = $token")

                            accessToken = token    // >>> chạy ở Main thread
                        }

                    }

                }
            }


        enableEdgeToEdge()

        setContent {
            val loggedEmail = remember { mutableStateOf("Chưa đăng nhập") }

            // save reference để Activity có thể update Compose state
            composeLoggedEmail = loggedEmail

            Column {
                Text("SDK version: $sdkInt")
                FileScannerAppV1(signInLauncher, loggedEmail)

            }
        }
    }
}
//xoa cache

fun clearCache(context: android.content.Context) {
    val cacheDir = File(context.filesDir, "cache_scan")
    val cacheFile = File(cacheDir, cacheFileName)
    if (cacheFile.exists()) cacheFile.delete()
}


fun signOutGoogle() {
    googleAuthClient.getSignInClient().signOut()
}


/**
 * Hàm đệ quy "chưa tối ưu" để quét file.
 * Rất dễ gây StackOverflowError nếu thư mục quá sâu.
 */
fun scanFilesRecursively(directory: File): List<File> {
    val fileList = mutableListOf<File>()
    val files = directory.listFiles() ?: return emptyList()

    for (file in files) {
        if (file.isDirectory) {
            // Lời gọi đệ quy – dễ gây StackOverflow nếu cây thư mục quá sâu
            fileList.addAll(scanFilesRecursively(file))
        } else {
            fileList.add(file)
        }
    }
    return fileList
}

/**
 * Hàm đã khử đệ quy – dùng Queue để duyệt cây thư mục.
 */
fun scanFilesIteratively(startDirectory: File): List<File> {
    val fileList = mutableListOf<File>()
    val queue: Queue<File> = LinkedList()
    queue.add(startDirectory)

    while (queue.isNotEmpty()) {
        val currentDir = queue.poll()
        val filesInDir = currentDir.listFiles() ?: continue

        for (file in filesInDir) {
            if (file.isDirectory) {
                queue.add(file)
            } else {
                fileList.add(file)
            }
        }
    }
    return fileList
}

/**
 * Tạo một cây thư mục sâu để demo crash/khử đệ quy.
 */
fun generateDeepFolder(baseDir: File, depth: Int) {
    var current = baseDir
    current.mkdirs()

    for (i in 1..depth) {
        current = File(current, "f")
        current.mkdirs()
    }

    try {
        val dummyFile = File(current, "dummy_file.txt")
        dummyFile.createNewFile()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

/**
 * Định dạng kích thước file cho dễ đọc.
 */
fun formatSize(size: Long): String {
    if (size < 1024) return "$size B"
    val kb = size / 1024.0
    if (kb < 1024) return "%.2f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.2f MB".format(mb)
    val gb = mb / 1024.0
    return "%.2f GB".format(gb)
}

@Serializable
data class CachedFile(
    val name: String,
    val size: Long,
    val path: String
)

@Serializable
data class CacheData(
    val rootPath: String,
    val lastModified: Long,
    val files: List<CachedFile>
)

val cacheFileName = "scan_cache.json"


//Ham luu cache
fun saveCache(context: android.content.Context, root: File, files: List<File>) {
    val cacheDir = File(context.filesDir, "cache_scan")
    cacheDir.mkdirs()

    val cacheFile = File(cacheDir, cacheFileName)

    val mapped = files.map {
        CachedFile(it.name, it.length(), it.absolutePath)
    }

    val json = Json.encodeToString(
        CacheData(
            rootPath = root.absolutePath,
            lastModified = root.lastModified(),
            files = mapped
        )
    )

    cacheFile.writeText(json)
}

//ham load cache
fun loadCache(context: android.content.Context, root: File): List<File>? {
    val cacheDir = File(context.filesDir, "cache_scan")
    val cacheFile = File(cacheDir, cacheFileName)
    if (!cacheFile.exists()) return null

    val json = cacheFile.readText()

    return try {
        val data = Json.decodeFromString<CacheData>(json)

        if (data.rootPath != root.absolutePath) return null
        if (data.lastModified != root.lastModified()) return null

        data.files.map { File(it.path) }

    } catch (e: Exception) {
        null
    }
}


@OptIn(ExperimentalMaterial3Api::class)

@Composable
fun FileScannerAppV1(
    signInLauncher: ActivityResultLauncher<Intent>,
    loggedEmail: MutableState<String>
) {
    val context = LocalContext.current   // CHỈ ĐỂ DÒNG NÀY


    LaunchedEffect(Unit) {
        val acc = GoogleSignIn.getLastSignedInAccount(context)
        if (acc != null) {
            loggedEmail.value = acc.email ?: "Không rõ email"
        }
    }



    var files by remember { mutableStateOf<List<File>>(emptyList()) }
    var uploadStatus by remember { mutableStateOf("Chưa upload") }
    var isGenerating by remember { mutableStateOf(false) }
    var isScanning by remember { mutableStateOf(false) }

    //bien tinh thgi scannfile
    var scanTime by remember { mutableStateOf<Long?>(null) }

    //val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val rootFolders = listOf(
        "ALL",
        Environment.DIRECTORY_DOWNLOADS,
        Environment.DIRECTORY_DOCUMENTS,
        Environment.DIRECTORY_DCIM,
        Environment.DIRECTORY_MOVIES,
        Environment.DIRECTORY_PICTURES
    )

    var selectedFolder by remember { mutableStateOf(Environment.DIRECTORY_DOWNLOADS) }
    var isMenuExpanded by remember { mutableStateOf(false) }

    val rootDir =
        if (selectedFolder == "ALL") {
            Environment.getExternalStorageDirectory()
        } else {
            Environment.getExternalStoragePublicDirectory(selectedFolder)
        }

    // Có thể dùng rootDir trực tiếp làm thư mục “bẫy”
    val trapDir = rootDir


    Scaffold(
        topBar = { TopAppBar(title = { Text("File Scanner V1 (Chưa tối ưu)") }) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Chọn thư mục gốc
            Column {

                Button(
                    onClick = {
                        // 1) Sign out tài khoản hiện tại để tránh cache
                        googleAuthClient.getSignInClient().signOut().addOnCompleteListener {

                            // 2) Sau khi sign out → mở UI đăng nhập mới
                            val intent = googleAuthClient.getSignInClient().signInIntent
                            signInLauncher.launch(intent)
                        }
                    }
                ) {
                    Text("Đăng nhập Google Drive (Đổi tài khoản)")
                }

               // Text("Tài khoản hiện tại: $loggedEmail")

                Text("Tài khoản hiện tại: ${loggedEmail.value}")


                Text("Chọn thư mục gốc:")
                Button(onClick = { isMenuExpanded = true }) {
                    Text(selectedFolder)
                }
                DropdownMenu(
                    expanded = isMenuExpanded,
                    onDismissRequest = { isMenuExpanded = false }
                ) {
                    rootFolders.forEach { folderName ->
                        DropdownMenuItem(
                            text = { Text(folderName) },
                            onClick = {
                                selectedFolder = folderName
                                isMenuExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Nút Scan ĐỆ QUY (chưa tối ưu)
            Button(
                onClick = {
                    val scanLogic: () -> Unit = {
                        if (trapDir.exists()) {
                            scope.launch(Dispatchers.IO) {
                                withContext(Dispatchers.Main) {
                                    scanTime = null
                                    isScanning = true
                                    uploadStatus = "Đang quét (dùng đệ quy)..."
                                }

                                //val result = scanFilesRecursively(trapDir)

                                val start = System.nanoTime()
                                val result = scanFilesRecursively(trapDir)
                                val end = System.nanoTime()
                                scanTime = end - start


                                withContext(Dispatchers.Main) {
                                    isScanning = false
                                    uploadStatus = "Quét xong (đệ quy)"
                                    files = result
                                }
                            }
                        } else {
                            uploadStatus = "Thư mục không tồn tại!"
                        }
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        if (Environment.isExternalStorageManager()) {
                            scanLogic()
                        } else {
                            val intent =
                                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                            intent.data = "package:${context.packageName}".toUri()
                            context.startActivity(intent)
                        }
                    } else {
                        scanLogic()
                    }
                },
                enabled = !isGenerating && !isScanning
            ) {
                if (isScanning) Text("Đang quét...") else Text("Scan File (Dùng đệ quy)")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Nút Scan ĐÃ KHỬ ĐỆ QUY
            Button(
                onClick = {
                    val scanLogic: () -> Unit = {
                        if (trapDir.exists()) {
                            scope.launch(Dispatchers.IO) {
                                withContext(Dispatchers.Main) {
                                    scanTime = null
                                    isScanning = true
                                    uploadStatus = "Đang quét (khử đệ quy)..."
                                }

                              //  val result = scanFilesIteratively(trapDir)

                                val start = System.nanoTime()
                                val result = scanFilesIteratively(trapDir)
                                val end = System.nanoTime()
                                scanTime = end - start


                                withContext(Dispatchers.Main) {
                                    isScanning = false
                                    uploadStatus = "Quét xong (khử đệ quy)"
                                    files = result
                                }
                            }
                        } else {
                            uploadStatus = "Thư mục không tồn tại!"
                        }
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        if (Environment.isExternalStorageManager()) {
                            scanLogic()
                        } else {
                            val intent =
                                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                            intent.data = "package:${context.packageName}".toUri()
                            context.startActivity(intent)
                        }
                    } else {
                        scanLogic()
                    }
                },
                enabled = !isGenerating && !isScanning
            ) {
                if (isScanning) Text("Đang quét...") else Text("Scan File đã khử đệ quy")
            }

            Spacer(modifier = Modifier.height(16.dp))

            //nut scan dequy + cache
            Button(
                onClick = {
                    val logic = {
                        scope.launch(Dispatchers.IO) {

                            val cached = loadCache(context, trapDir)
                            if (cached != null) {
                                val start = System.nanoTime()
                                val end = System.nanoTime()
                                scanTime = end - start

                                withContext(Dispatchers.Main) {
                                    uploadStatus = "Dùng cache (Đệ quy)"
                                    files = cached
                                }
                                return@launch
                            }

                            withContext(Dispatchers.Main) {
                                scanTime = null
                                isScanning = true
                                uploadStatus = "Đang quét (Đệ quy + Cache)..."
                            }

                            val start = System.nanoTime()
                            val result = scanFilesRecursively(trapDir)



                            saveCache(context, trapDir, result)
                            val end = System.nanoTime()
                            scanTime = end - start

                            withContext(Dispatchers.Main) {
                                isScanning = false
                                uploadStatus = "Hoàn tất (Đệ quy + Cache)"
                                files = result
                            }
                        }
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                        !Environment.isExternalStorageManager()
                    ) {
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        intent.data = "package:${context.packageName}".toUri()
                        context.startActivity(intent)
                    } else logic()
                },
                enabled = !isScanning && !isGenerating
            ) {
                Text("Scan File (Đệ quy + Cache)")
            }


            Spacer(modifier = Modifier.height(16.dp))

            //Nut scan khu de quy + cache
            Button(
                onClick = {
                    val logic = {
                        scope.launch(Dispatchers.IO) {

                            val cached = loadCache(context, trapDir)
                            if (cached != null) {
                                val start = System.nanoTime()
                                val end = System.nanoTime()
                                scanTime = end - start

                                withContext(Dispatchers.Main) {
                                    uploadStatus = "Dùng cache (Iterative)"
                                    files = cached
                                }
                                return@launch
                            }

                            withContext(Dispatchers.Main) {
                                scanTime = null
                                isScanning = true
                                uploadStatus = "Đang quét (Iterative + Cache)..."
                            }

                            val start = System.nanoTime()
                            val result = scanFilesIteratively(trapDir)
                            saveCache(context, trapDir, result)
                            val end = System.nanoTime()
                            scanTime = end - start

                            withContext(Dispatchers.Main) {
                                isScanning = false
                                uploadStatus = "Hoàn tất (Iterative + Cache)"
                                files = result
                            }
                        }
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                        !Environment.isExternalStorageManager()
                    ) {
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        intent.data = "package:${context.packageName}".toUri()
                        context.startActivity(intent)
                    } else logic()
                },
                enabled = !isScanning && !isGenerating
            ) {
                Text("Scan File (Khử đệ quy + Cache)")
            }


            Spacer(modifier = Modifier.height(16.dp))

            // Nút Generate Deep Folder + xoa CACHE
//            Button(
//                onClick = {
//                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
//                        Environment.isExternalStorageManager()
//                    ) {
//                        scope.launch(Dispatchers.IO) {
//                            withContext(Dispatchers.Main) {
//                                isGenerating = true
//                                uploadStatus = "Đang tạo 1,000 thư mục..."
//                            }
//
//                            generateDeepFolder(trapDir, 1000)
//
//                            withContext(Dispatchers.Main) {
//                                isGenerating = false
//                                uploadStatus = "Đã tạo xong deep folder!"
//                            }
//                        }
//                    } else {
//                        uploadStatus = "Cần cấp quyền 'Tất cả tệp' trước!"
//                    }
//                },
//                enabled = !isGenerating && !isScanning
//            ) {
//                if (isGenerating) {
//                    Row(verticalAlignment = Alignment.CenterVertically) {
//                        Text("Đang tạo...")
//                    }
//                } else {
//                    Text("Generate Deep Folder")
//                }
//            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Nút Generate Deep Folder
                Button(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                            Environment.isExternalStorageManager()
                        ) {
                            scope.launch(Dispatchers.IO) {
                                withContext(Dispatchers.Main) {
                                    isGenerating = true
                                    uploadStatus = "Đang tạo 1,000 thư mục..."
                                }

                                generateDeepFolder(trapDir, 1000)

                                withContext(Dispatchers.Main) {
                                    isGenerating = false
                                    uploadStatus = "Đã tạo xong deep folder!"
                                }
                            }
                        } else {
                            uploadStatus = "Cần cấp quyền 'Tất cả tệp' trước!"
                        }
                    },
                    enabled = !isGenerating && !isScanning,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isGenerating) {
                        Text("Đang tạo...")
                    } else {
                        Text("Generate Deep Folder")
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Nút XÓA CACHE
                Button(
                    onClick = {
                        clearCache(context)
                        uploadStatus = "Đã xóa cache!"
                        scanTime = null     // reset hiển thị thời gian
                        files = emptyList() // tùy chọn: xoá danh sách file
                    },
                    enabled = !isGenerating && !isScanning,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Xóa Cache")
                }
            }


            Spacer(modifier = Modifier.height(16.dp))

            // Danh sách file
            Text("File List:", style = MaterialTheme.typography.headlineSmall)
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(files) { file ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(file.name)
                            Text(
                                formatSize(file.length()),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Button(onClick = {

                            //  >>> THAY BẰNG KIỂM TRA RÕ RÀNG HƠN
                            if (accessToken == null) {
                                println("Upload FAIL: accessToken = null")     // <<< LOG
                                uploadStatus = "Token null — chưa lấy được access_token!"
                                return@Button
                            }


                            scope.launch(Dispatchers.IO) {
                                withContext(Dispatchers.Main) {
                                    uploadStatus = "Uploading ${file.name} lên Drive..."
                                }

                                val ok = DriveUploader.uploadToGoogleDrive(accessToken!!, file)

                                withContext(Dispatchers.Main) {
                                    uploadStatus = if (ok) "Upload thành công!" else "Upload thất bại!"
                                }
                            }
                        }) {
                            Text("Upload lên Drive")
                        }


                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Trạng thái: $uploadStatus")

            //UI hien thi thoi gian
            scanTime?.let { ns ->
                val ms = ns / 1_000_000.0
                Text("Thời gian quét: ${ns} ns (≈ %.3f ms)".format(ms))
            }

        }
    }
}
