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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Chúng ta sẽ không xin quyền ở đây nữa, mà xin lúc nhấn nút
        setContent {
            FileScannerAppV1()
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
    var files by remember { mutableStateOf(listOf<File>()) }
    var uploadStatus by remember { mutableStateOf("Chưa upload") }
    val context = LocalContext.current // Lấy context để mở Cài đặt

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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // Từ Android 11 (API 30) trở lên, dùng cách mới
                    if (Environment.isExternalStorageManager()) {
                        // ĐÃ CÓ QUYỀN: Chạy code "phá" app
//                        val rootDir = Environment.getExternalStorageDirectory()
//                        files = scanFilesRecursively(rootDir)
                        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        files = scanFilesRecursively(downloadDir)
                    } else {
                        // CHƯA CÓ QUYỀN: Mở Cài đặt để xin quyền
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        intent.data = Uri.parse("package:${context.packageName}")
                        context.startActivity(intent)
                    }
                } else {
                    // 2. Từ Android 10 (API 29) trở xuống
                    // Trên các máy cũ này, chúng ta không có quyền MANAGE,
                    // và cũng không xin quyền READ cũ.
                    // Nên app sẽ không quét được nhiều, nhưng cũng không crash.
                    // Chấp nhận để tập trung demo trên API 30+
//                    val rootDir = Environment.getExternalStorageDirectory()
//                    files = scanFilesRecursively(rootDir)
                    val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    files = scanFilesRecursively(downloadDir)
                }
            }) {
                Text("Scan File (Dùng Đệ quy)")
            }

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
            Text("Upload Status: $uploadStatus")
        }
    }
}
//package com.example.project0
    //
    //import android.Manifest
    //import android.os.Bundle
    //import android.os.Environment
    //import androidx.activity.ComponentActivity
    //import androidx.activity.compose.setContent
    //import androidx.activity.enableEdgeToEdge
    //import androidx.compose.foundation.layout.Arrangement
    //import androidx.compose.foundation.layout.Column
    //import androidx.compose.foundation.layout.Row
    //import androidx.compose.foundation.layout.Spacer
    //import androidx.compose.foundation.layout.fillMaxSize
    //import androidx.compose.foundation.layout.fillMaxWidth
    //import androidx.compose.foundation.layout.height
    //import androidx.compose.foundation.layout.padding
    //import androidx.compose.foundation.lazy.LazyColumn
    //import androidx.compose.foundation.lazy.items
    //import androidx.compose.material3.Button
    //import androidx.compose.material3.ExperimentalMaterial3Api
    //import androidx.compose.material3.MaterialTheme
    //import androidx.compose.material3.Scaffold
    //import androidx.compose.material3.Text
    //import androidx.compose.material3.TopAppBar
    //import androidx.compose.runtime.Composable
    //import androidx.compose.runtime.getValue
    //import androidx.compose.runtime.mutableStateOf
    //import androidx.compose.runtime.remember
    //import androidx.compose.runtime.setValue
    //import androidx.compose.ui.Modifier
    //import androidx.compose.ui.unit.dp
    //import java.io.File
    //
    //class MainActivity : ComponentActivity() {
    //    override fun onCreate(savedInstanceState: Bundle?) {
    //        super.onCreate(savedInstanceState)
    //        enableEdgeToEdge()
    //
    //        // 1. Xin quyền (cách làm "ngây thơ", không kiểm tra kết quả)
    //        requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 100)
    //
    //        setContent {
    //            FileScannerAppV1()
    //        }
    //    }
    //}
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
    //            // Đây chính là lời gọi đệ quy, nguyên nhân của StackOverflow
    //            fileList.addAll(scanFilesRecursively(file))
    //        } else {
    //            fileList.add(file)
    //        }
    //    }
    //    return fileList
    //}
    //
    //@OptIn(ExperimentalMaterial3Api::class)
    //@Composable
    //fun FileScannerAppV1() {
    //    var files by remember { mutableStateOf(listOf<File>()) }
    //    var uploadStatus by remember { mutableStateOf("Chưa upload") }
    //
    //    Scaffold(
    //        topBar = { TopAppBar(title = { Text("File Scanner V1 (Chưa tối ưu)") }) }
    //    ) { paddingValues ->
    //        Column(
    //            modifier = Modifier
    //                .fillMaxSize() // Dùng fillMaxSize để thấy rõ hơn
    //                .padding(paddingValues)
    //                .padding(all = 16.dp)
    //        )
    //        {
    //            Button(onClick = {
    //                // 2. Vấn đề: Quét trực tiếp trên Main Thread
    //                // App sẽ bị treo cứng (lag) trong khi quét
    ////                val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    ////                files = scanFilesRecursively(downloadDir)
    //                val rootDir = Environment.getExternalStorageDirectory()
    //                files = scanFilesRecursively(rootDir)
    //            }) {
    //                Text("Scan File (Dùng Đệ quy)")
    //            }
    //
    //            Spacer(modifier = Modifier.height(16.dp))
    //
    //            Text("File List:", style = MaterialTheme.typography.headlineSmall)
    //            LazyColumn(modifier = Modifier.weight(1f)) { // Dùng weight để chiếm hết không gian còn lại
    //                items(files) { file ->
    //                    Row(
    //                        modifier = Modifier
    //                            .fillMaxWidth()
    //                            .padding(4.dp),
    //                        horizontalArrangement = Arrangement.SpaceBetween
    //                    ) {
    //                        Text(file.name, modifier = Modifier.weight(1f)) // Thêm weight để tên file dài không đẩy nút
    //
    //                        // 3. Vấn đề: Upload trên Main Thread
    //                        Button(onClick = {
    //                            uploadStatus = "Uploading ${file.name}..."
    //
    //                            // Giả lập upload 3 giây, làm "đóng băng" app
    //                            Thread.sleep(3000)
    //
    //                            uploadStatus = "Uploaded ${file.name}"
    //                        }) {
    //                            Text("Upload")
    //                        }
    //                    }
    //                }
    //            }
    //
    //            Spacer(modifier = Modifier.height(16.dp))
    //            Text("Upload Status: $uploadStatus")
    //        }
    //    }
    //}