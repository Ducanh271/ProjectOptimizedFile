package com.example.project0.viewmodel

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.project0.core.drive.DriveUploader
import com.example.project0.worker.FileUploadWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.LinkedList
import java.util.Queue
import kotlin.system.measureTimeMillis

class FileScannerViewModel(
    application: Application
) : AndroidViewModel(application) {

    // ========== UI STATE ==========

    var loggedEmail by mutableStateOf("Chưa đăng nhập")
        private set

    var files by mutableStateOf<List<File>>(emptyList())
        private set

    var selectedFiles = mutableStateOf(setOf<File>())

    var uploadStatus by mutableStateOf("Sẵn sàng")
        private set

    var scanTime by mutableStateOf<Long?>(null)
        private set

    var executionTime by mutableStateOf<Long?>(null)
        private set

    var isScanning by mutableStateOf(false)
        private set

    var isUploading by mutableStateOf(false)
        private set

    // token nhận từ Activity
    var accessToken by mutableStateOf<String?>(null)
        private set

    // ========== CACHE TRONG RAM ==========
    private var memoryCacheBinary: ByteArray? = null
    private var memoryCacheFiles: List<File>? = null

    private val io = Dispatchers.IO

    // -------- LOGIN / TOKEN --------
    fun updateLoggedEmail(email: String) {
        loggedEmail = email
    }

    fun updateAccessToken(token: String?) {
        accessToken = token
    }

    // -------- CHỌN FILE --------
    fun onFileClicked(file: File) {
        val current = selectedFiles.value.toMutableSet()
        if (current.contains(file)) current.remove(file) else current.add(file)
        selectedFiles.value = current
    }

    fun onFileChecked(file: File, checked: Boolean) {
        val current = selectedFiles.value.toMutableSet()
        if (checked) current.add(file) else current.remove(file)
        selectedFiles.value = current
    }

    fun clearSelection() {
        selectedFiles.value = emptySet()
    }

    // ========== HÀM SCAN FILE ==========

    fun scanRecursive(root: File) {
        viewModelScope.launch(io) {
            isScanning = true
            uploadStatus = "Scanning..."
            val start = System.nanoTime()
            val result = scanFilesRecursively(root)
            scanTime = System.nanoTime() - start
            files = result
            isScanning = false
            uploadStatus = "Done"
        }
    }

    fun scanIterative(root: File) {
        viewModelScope.launch(io) {
            isScanning = true
            uploadStatus = "Scanning..."
            val start = System.nanoTime()
            val result = scanFilesIteratively(root)
            scanTime = System.nanoTime() - start
            files = result
            isScanning = false
            uploadStatus = "Done"
        }
    }

    fun scanWithDiskCache(root: File) {
        viewModelScope.launch(io) {
            isScanning = true
            val start = System.nanoTime()

            val cached = loadCacheBinaryFromDisk(root)
            if (cached != null) {
                scanTime = System.nanoTime() - start
                files = cached
                isScanning = false
                uploadStatus = "Loaded Binary Disk"
                return@launch
            }

            val result = scanFilesIteratively(root)
            saveCacheBinary(root, result)
            scanTime = System.nanoTime() - start
            files = result
            isScanning = false
            uploadStatus = "Scanned + Cached (Binary Disk)"
        }
    }

    fun scanWithMemoryCache(root: File) {
        viewModelScope.launch(io) {
            isScanning = true
            val start = System.nanoTime()

            val cached = loadCacheBinaryFromMemory(root)
            if (cached != null) {
                scanTime = System.nanoTime() - start
                files = cached
                isScanning = false
                uploadStatus = "Loaded Binary Memory"
                return@launch
            }

            val result = scanFilesIteratively(root)
            saveCacheBinary(root, result)
            scanTime = System.nanoTime() - start
            files = result
            isScanning = false
            uploadStatus = "Scanned + Cached (Memory)"
        }
    }

    fun generateDeepFolder(baseDir: File, depth: Int = 500) {
        viewModelScope.launch(io) {
            var current = baseDir
            current.mkdirs()
            for (i in 1..depth) {
                current = File(current, "f")
                current.mkdirs()
            }
            try {
                File(current, "dummy_file.txt").createNewFile()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun clearCache() {
        memoryCacheBinary = null
        memoryCacheFiles = null

        val context = getApplication<Application>()
        val dir = File(context.filesDir, "cache_bin")
        val file = File(dir, "scan_cache.bin")
        if (file.exists()) file.delete()

        files = emptyList()
        selectedFiles.value = emptySet()
        scanTime = null
        executionTime = null
        uploadStatus = "Cleared"
    }

    // ========== HÀM UPLOAD ==========

    fun uploadSequential() {
        val token = accessToken
        if (token == null) {
            uploadStatus = "No Token!"
            return
        }

        viewModelScope.launch(io) {
            isUploading = true
            executionTime = null
            val selected = selectedFiles.value.toList()

            val t = measureTimeMillis {
                selected.forEachIndexed { i, f ->
                    uploadStatus = "Up ${i + 1}/${selected.size}..."
                    DriveUploader.uploadToGoogleDrive(token, f)
                }
            }

            isUploading = false
            executionTime = t
            uploadStatus = "Done Sequential"
        }
    }

    fun uploadParallel() {
        val token = accessToken
        if (token == null) {
            uploadStatus = "No Token!"
            return
        }

        viewModelScope.launch(io) {
            isUploading = true
            executionTime = null
            uploadStatus = "Uploading Parallel..."
            val selected = selectedFiles.value.toList()

            val t = measureTimeMillis {
                val jobs = selected.map { file ->
                    async(io) { DriveUploader.uploadToGoogleDrive(token, file) }
                }
                jobs.awaitAll()
            }

            isUploading = false
            executionTime = t
            uploadStatus = "Done Parallel"
        }
    }

    fun enqueueUploadWork() {
        val token = accessToken
        if (token == null) {
            uploadStatus = "No Token!"
            return
        }

        val appContext = getApplication<Application>()
        val wm = WorkManager.getInstance(appContext)
        val cons = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        selectedFiles.value.forEach { f ->
            val req = OneTimeWorkRequestBuilder<FileUploadWorker>()
                .setConstraints(cons)
                .setInputData(
                    workDataOf(
                        "KEY_FILE_PATH" to f.absolutePath,
                        "KEY_ACCESS_TOKEN" to token
                    )
                )
                .build()
            wm.enqueue(req)
        }

        uploadStatus = "Queued in Background"
        clearSelection()
    }

    // ========== HÀM PRIVATE (SCAN + CACHE) ==========

    private fun scanFilesRecursively(directory: File): List<File> {
        val fileList = mutableListOf<File>()
        val files = directory.listFiles() ?: return emptyList()
        for (file in files) {
            if (file.isDirectory) fileList.addAll(scanFilesRecursively(file))
            else fileList.add(file)
        }
        return fileList
    }

    private fun scanFilesIteratively(startDirectory: File): List<File> {
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

    // ---- BINARY CACHE (DISK + MEMORY) ----

    private fun saveCacheBinary(root: File, files: List<File>) {
        val context = getApplication<Application>()
        val dir = File(context.filesDir, "cache_bin")
        dir.mkdirs()
        val file = File(dir, "scan_cache.bin")

        val baos = ByteArrayOutputStream()
        val out = DataOutputStream(baos)

        // header
        out.writeUTF(root.absolutePath)
        out.writeLong(root.lastModified())
        out.writeInt(files.size)

        files.forEach { f ->
            out.writeUTF(f.absolutePath)
            out.writeLong(f.length())
        }

        out.flush()
        val binaryData = baos.toByteArray()

        file.writeBytes(binaryData)

        memoryCacheBinary = binaryData
        memoryCacheFiles = files.toList()
    }

    private fun loadCacheBinaryFromDisk(root: File): List<File>? {
        val context = getApplication<Application>()
        val file = File(File(context.filesDir, "cache_bin"), "scan_cache.bin")
        if (!file.exists()) return null
        return decodeBinaryCache(file.readBytes(), root)
    }

    private fun loadCacheBinaryFromMemory(root: File): List<File>? {
        val data = memoryCacheBinary ?: return null
        return decodeBinaryCache(data, root)
    }

    private fun decodeBinaryCache(binary: ByteArray, root: File): List<File>? {
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
}
