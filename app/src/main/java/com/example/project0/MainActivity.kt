    package com.example.project0

    import android.content.Intent
    import android.net.Uri
    import android.os.Build
    import android.os.Bundle
    import android.os.Environment
    import android.provider.Settings
    import androidx.activity.ComponentActivity
    import androidx.activity.compose.setContent
    import androidx.activity.enableEdgeToEdge
    import androidx.compose.foundation.layout.Arrangement
    import androidx.compose.foundation.layout.Column
    import androidx.compose.foundation.layout.Row
    import androidx.compose.foundation.layout.Spacer
    import androidx.compose.foundation.layout.fillMaxSize
    import androidx.compose.foundation.layout.fillMaxWidth
    import androidx.compose.foundation.layout.height
    import androidx.compose.foundation.layout.padding
    import androidx.compose.foundation.lazy.LazyColumn
    import androidx.compose.foundation.lazy.items
    import androidx.compose.material3.Button
    import androidx.compose.material3.ExperimentalMaterial3Api
    import androidx.compose.material3.MaterialTheme
    import androidx.compose.material3.Scaffold
    import androidx.compose.material3.Text
    import androidx.compose.material3.TopAppBar
    import androidx.compose.runtime.Composable
    import androidx.compose.runtime.getValue
    import androidx.compose.runtime.mutableStateOf
    import androidx.compose.runtime.remember
    import androidx.compose.runtime.rememberCoroutineScope
    import androidx.compose.runtime.setValue
    import androidx.compose.ui.Modifier
    import androidx.compose.ui.platform.LocalContext
    import androidx.compose.ui.unit.dp
    import kotlinx.coroutines.CoroutineScope
    import kotlinx.coroutines.Dispatchers
    import kotlinx.coroutines.launch
    import kotlinx.coroutines.withContext
    import java.io.File
    import androidx.compose.material3.CircularProgressIndicator // <-- Thêm import này
    import androidx.compose.ui.Alignment // <-- Thêm import này
    import androidx.compose.foundation.layout.Box // <-- Thêm import này
    import androidx.compose.foundation.layout.size // <-- Thêm import này
    import androidx.compose.foundation.layout.width // <-- Thêm import này
    import androidx.compose.material3.CircularProgressIndicator
    import androidx.compose.foundation.layout.Box
    // for ver2
    import java.util.LinkedList
    import java.util.Queue
    val sdkInt = Build.VERSION.SDK_INT
    class MainActivity : ComponentActivity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            enableEdgeToEdge()
            // Chúng ta sẽ không xin quyền ở đây nữa, mà xin lúc nhấn nút
            setContent {
                Column {
                    Text("SDK version: $sdkInt")
                    FileScannerAppV1()
                }

            }
        }
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
                // Đây chính là lời gọi đệ quy, nguyên nhân của StackOverflow
                fileList.addAll(scanFilesRecursively(file))
            } else {
                fileList.add(file)
            }
        }

        return fileList
    }
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileScannerAppV1() {
    var files by remember { mutableStateOf<List<File>>(emptyList()) }
    var uploadStatus by remember { mutableStateOf("Chưa upload") }
    var isGenerating by remember { mutableStateOf(false) }
    var isScanning by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // --- SỬA 1: Định nghĩa thư mục "bẫy" ---
    // Cả hai nút sẽ cùng làm việc với thư mục "deep_test" bên trong "Downloads"
    val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    val trapDir = File(downloadDir, "deep_test_trap") // Tên thư mục bẫy

    Scaffold(
        topBar = { TopAppBar(title = { Text("File Scanner V1 (Chưa tối ưu)") }) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(all = 16.dp)
        )
        {
            Button(onClick = {
                // --- SỬA 2: Nút Scan File ---
                // Chỉ cần kiểm tra quyền và chạy logic
                val scanLogic: () -> Unit = {
                    if (trapDir.exists()) {
                        scope.launch(Dispatchers.IO) {
                            withContext(Dispatchers.Main) {
                                isScanning = true
                                uploadStatus = "Đang quét (sẽ crash)..."
                            }
                            // BÙM! Sẽ crash ở đây
                         //   val result = scanFilesRecursively(trapDir)
                            val result = scanFilesRecursively(trapDir)
                            scanFilesRecursively(trapDir)

                            withContext(Dispatchers.Main) {
                                isScanning = false
                                uploadStatus = "quet xong"
                                files = result
                            }
                        }
                    } else {
                        uploadStatus = "Hãy nhấn 'Generate Deep Folder' trước!"
                    }
                }

                // Logic kiểm tra quyền (giữ nguyên)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (Environment.isExternalStorageManager()) {
                        scanLogic()
                    } else {
                        // Xin quyền
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        intent.data = Uri.parse("package:${context.packageName}")
                        context.startActivity(intent)
                    }
                } else {
                    scanLogic()
                }
            },
                enabled = !isGenerating && !isScanning
            ) {
                if (isScanning) Text("Đang quét...") else Text("Scan File (Dùng Đệ quy)")
            }
            Spacer(modifier = Modifier.height(16.dp))
// button toi uu de quy
            Button(onClick = {
                // --- SỬA 2: Nút Scan File ---
                // Chỉ cần kiểm tra quyền và chạy logic
                val scanLogic: () -> Unit = {
                    if (trapDir.exists()) {
                        scope.launch(Dispatchers.IO) {
                            withContext(Dispatchers.Main) {
                                isScanning = true
                                uploadStatus = "Đang quét "
                            }
                            // Lần này sẽ không crash n
                            //   val result = scanFilesRecursively(trapDir)
                            val result = scanFilesIteratively(trapDir)

                            withContext(Dispatchers.Main) {
                                isScanning = false
                                uploadStatus = "quet xong"
                                files = result
                            }
                        }
                    } else {
                        uploadStatus = "Hãy nhấn 'Generate Deep Folder' trước!"
                    }
                }

                // Logic kiểm tra quyền (giữ nguyên)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (Environment.isExternalStorageManager()) {
                        scanLogic()
                    } else {
                        // Xin quyền
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        intent.data = Uri.parse("package:${context.packageName}")
                        context.startActivity(intent)
                    }
                } else {
                    scanLogic()
                }
            },
                enabled = !isGenerating && !isScanning
            ) {
                if (isScanning) Text("Đang quét...") else Text("Scan File đã khử đệ quy ")
            }
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    // --- SỬA 3: Nút Generate ---
                    // Phải kiểm tra quyền trước khi tạo
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
                        scope.launch(Dispatchers.IO) {
                            withContext(Dispatchers.Main) {
                                isGenerating = true
                                uploadStatus = "Đang tạo 1,000 thư mục..."
                            }

                            // Tạo bẫy với 1,000 tầng
                            generateDeepFolder(trapDir, 1000)

                            withContext(Dispatchers.Main) {
                                isGenerating = false
                                uploadStatus = "Đã tạo xong 'deep_test_trap'!"
                            }
                        }
                    } else {
                        uploadStatus = "Cần cấp quyền 'Tất cả file' trước!"
                    }
                },
                enabled = !isGenerating && !isScanning
            ) {
                if (isGenerating) {
                    Box(contentAlignment = Alignment.Center) { /*...Cục xoay...*/ }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Đang tạo...")
                } else {
                    Text("Generate Deep Folder")
                }
            }

            // ... (LazyColumn và Text trạng thái giữ nguyên) ...
            Spacer(modifier = Modifier.height(16.dp))
            Text("File List:", style = MaterialTheme.typography.headlineSmall)
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(files) { file ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(file.name, modifier = Modifier.weight(1f))

                        Button(onClick = {
                            uploadStatus = "Uploading ${file.name}..."
                            Thread.sleep(3000)
                            uploadStatus = "Uploaded ${file.name}"
                        }) {
                            Text("Upload")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Trạng thái: $uploadStatus")
        }
    }
}

    fun generateDeepFolder(baseDir: File, depth: Int) {
        var current = baseDir
        current.mkdirs() // Tạo .../deep_test_trap

        for (i in 1..depth) {
            // Dùng tên "f" (1 ký tự) để không vượt PATH_MAX
            current = File(current, "f")
            current.mkdirs()
        }

        // Tạo 1 file "mồi" ở tầng sâu nhất
        // Chúng ta bọc try-catch vì thao tác file có thể thất bại
        try {
            val dummyFile = File(current, "dummy_file.txt")
            dummyFile.createNewFile()
        } catch (e: Exception) {
            e.printStackTrace() // In lỗi nếu có
        }
    }
    fun scanFilesIteratively(startDirectory: File): List<File> {
        val fileList = mutableListOf<File>()
        val queue: Queue<File> = LinkedList() // Dùng hàng đợi
        queue.add(startDirectory)

        while (queue.isNotEmpty()) {
            val currentDir = queue.poll()
            // Bỏ qua các thư mục bị cấm (như Android/data)
            val filesInDir = currentDir.listFiles() ?: continue

            for (file in filesInDir) {
                if (file.isDirectory) {
                    queue.add(file) // Thêm thư mục con vào hàng đợi
                } else {
                    fileList.add(file) // Thêm file vào kết quả
                }
            }
        }
        return fileList
    }
