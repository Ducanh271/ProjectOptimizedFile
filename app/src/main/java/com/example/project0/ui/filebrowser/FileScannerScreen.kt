package com.example.project0.ui.filebrowser

import android.os.Environment
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.project0.viewmodel.FileScannerViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileScannerScreen(
    viewModel: FileScannerViewModel,
    onLoginClick: () -> Unit
) {
    // state từ ViewModel
    val files = viewModel.files
    val selectedFiles = viewModel.selectedFiles.value
    val uploadStatus = viewModel.uploadStatus
    val scanTime = viewModel.scanTime
    val executionTime = viewModel.executionTime
    val isScanning = viewModel.isScanning
    val isUploading = viewModel.isUploading
    val loggedEmail = viewModel.loggedEmail

    // state UI cục bộ
    var showScanOptions by remember { mutableStateOf(true) }
    var selectedFolder by remember { mutableStateOf(Environment.DIRECTORY_DOWNLOADS) }
    var isMenuExpanded by remember { mutableStateOf(false) }

    val rootDir: File =
        if (selectedFolder == "ALL") Environment.getExternalStorageDirectory()
        else Environment.getExternalStoragePublicDirectory(selectedFolder)

    val trapDir = rootDir

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
                    IconButton(onClick = onLoginClick) {
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .padding(12.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Account: $loggedEmail",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Folder: ",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                            Box {
                                Text(
                                    selectedFolder,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable { isMenuExpanded = true }
                                )
                                DropdownMenu(
                                    expanded = isMenuExpanded,
                                    onDismissRequest = { isMenuExpanded = false }
                                ) {
                                    listOf(
                                        "ALL",
                                        Environment.DIRECTORY_DOWNLOADS,
                                        Environment.DIRECTORY_DCIM,
                                        Environment.DIRECTORY_PICTURES
                                    ).forEach { folder ->
                                        DropdownMenuItem(
                                            text = { Text(folder) },
                                            onClick = {
                                                selectedFolder = folder
                                                isMenuExpanded = false
                                                // đổi folder thì xóa cache
                                                viewModel.clearCache()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    IconButton(onClick = { showScanOptions = !showScanOptions }) {
                        Icon(
                            if (showScanOptions) Icons.Default.KeyboardArrowUp
                            else Icons.Default.KeyboardArrowDown,
                            contentDescription = "Toggle"
                        )
                    }
                }
            }

            // 2. SCAN CONTROLS
            if (showScanOptions) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "Scan Methods",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(8.dp))

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.scanRecursive(trapDir) },
                                enabled = !isScanning
                            ) { Text("Recursive", fontSize = 10.sp) }

                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.scanIterative(trapDir) },
                                enabled = !isScanning
                            ) { Text("Iterative", fontSize = 10.sp) }

                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.scanWithDiskCache(trapDir) },
                                enabled = !isScanning
                            ) { Text("Cache Disk", fontSize = 10.sp) }

                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.scanWithMemoryCache(trapDir) },
                                enabled = !isScanning
                            ) { Text("Cache Mem", fontSize = 10.sp) }
                        }

                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = { viewModel.generateDeepFolder(trapDir, 500) },
                                modifier = Modifier.weight(1f)
                            ) { Text("Gen Folder", fontSize = 10.sp) }

                            FilledTonalButton(
                                onClick = { viewModel.clearCache() },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                )
                            ) { Text("Clear", fontSize = 10.sp) }
                        }
                    }
                }
            }

            // 3. STATUS & LIST HEADER
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Files: ${files.size} (Selected: ${selectedFiles.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (scanTime != null) {
                    val ns = scanTime
                    val ms = ns!! / 1_000_000.0
                    Text(
                        "Time: ${ns} ns  (${String.format("%.4f", ms)} ms)",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }

            // 4. FILE LIST
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            ) {
                items(files) { file ->
                    val isSelected = selectedFiles.contains(file)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .clickable { viewModel.onFileClicked(file) },
                        colors = CardDefaults.cardColors(
                            containerColor =
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
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
                                    viewModel.onFileChecked(file, chk)
                                }
                            )
                            Icon(
                                Icons.Outlined.Folder,
                                contentDescription = null,
                                tint = Color.Gray
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    file.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1
                                )
                                Text(
                                    formatSize(file.length()),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
            }

            // 5. UPLOAD ACTIONS
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Status: $uploadStatus",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1
                    )
                    if (executionTime != null) {
                        Text(
                            "Upload Time: ${executionTime} ms",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.uploadSequential() },
                            enabled = selectedFiles.isNotEmpty() && !isUploading,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Timer, null, modifier = Modifier.size(20.dp))
                                Text("Seq", fontSize = 10.sp)
                            }
                        }

                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.uploadParallel() },
                            enabled = selectedFiles.isNotEmpty() && !isUploading
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Bolt, null, modifier = Modifier.size(20.dp))
                                Text("Parallel", fontSize = 10.sp)
                            }
                        }

                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.enqueueUploadWork() },
                            enabled = selectedFiles.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF00695C)
                            )
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

// === HÀM FORMAT SIZE (chỉ dành cho UI, giữ ở đây) ===
fun formatSize(size: Long): String {
    if (size < 1024) return "$size B"
    val kb = size / 1024.0
    if (kb < 1024) return "%.2f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.2f MB".format(mb)
    val gb = mb / 1024.0
    return "%.2f GB".format(gb)
}
