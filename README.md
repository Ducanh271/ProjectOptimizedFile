 ProjectOptimizedFile (Android/Kotlin)

Project cho môn **Software Optimization**: demo một số kỹ thuật tối ưu hoá trong bài toán xử lý file, bao gồm:
- **Recursion reduction** (khử đệ quy khi duyệt cây thư mục để tránh `StackOverflowError`)
- **Concurrency** (dùng Kotlin Coroutines để chạy tác vụ nặng ở background thread)
- Demo upload file thật qua mạng (OkHttp)

> App viết bằng **Kotlin + Jetpack Compose**, target Android SDK hiện đang cấu hình `compileSdk = 36`, `minSdk = 26`.

---

## Tính năng chính

### 1) Quét file theo kiểu đệ quy (chưa tối ưu)
- Nút: **“Scan File (Dùng Đệ quy)”**
- Hàm: `scanFilesRecursively(directory: File): List<File>`
- Mục đích: minh hoạ vấn đề khi thư mục quá sâu có thể gây **StackOverflowError**.

### 2) Quét file đã khử đệ quy (tối ưu hơn)
- Nút: **“Scan File đã khử đệ quy”**
- Hàm: `scanFilesIteratively(startDirectory: File): List<File>`
- Dùng hàng đợi (BFS) để duyệt thư mục, tránh call stack sâu.

### 3) Generate thư mục sâu để “test crash / test tối ưu”
- Nút: **“Generate Deep Folder”**
- Hàm: `generateDeepFolder(baseDir: File, depth: Int)`
- Tạo chuỗi thư mục con lồng nhau (mặc định depth = 1000) và tạo `dummy_file.txt` ở tầng sâu nhất.

### 4) Upload file thật (demo network I/O)
- Nút trên mỗi file: **“Upload (Real)”**
- Hàm: `uploadFileReal(file: File): Boolean` (suspend)
- Upload lên server test: `https://httpbin.org/post` bằng **OkHttp**.

---

## Tech stack

- **Kotlin**
- **Jetpack Compose (Material 3)**
- **Kotlin Coroutines**
- **OkHttp** (`com.squareup.okhttp3:okhttp:5.3.0`)

---

## Quyền truy cập (Permissions)

App dùng:
- `android.permission.MANAGE_EXTERNAL_STORAGE`
- `android.permission.INTERNET`

Vì sử dụng **MANAGE_EXTERNAL_STORAGE** (All files access), trên Android 11+ (API 30+) bạn cần cấp quyền **“All files access”** trong Settings.

Trong app, logic xin quyền được gọi khi bạn bấm các nút scan/generate (không xin quyền ngay khi mở app).

---

## Cách chạy project

### Yêu cầu
- Android Studio (khuyến nghị bản mới)
- Android SDK phù hợp (compileSdk/targetSdk đang là 36)
- Thiết bị/Emulator Android `minSdk >= 26`

### Run
1. Clone repo:
   ```bash
   git clone https://github.com/Ducanh271/ProjectOptimizedFile.git
   cd ProjectOptimizedFile
   ```
2. Mở bằng Android Studio
3. Sync Gradle
4. Run module `app`

---

## Cách sử dụng (flow gợi ý)

1. Chọn thư mục gốc (Downloads/Documents/DCIM/Movies/Pictures hoặc **ALL**).
2. (Tuỳ chọn) bấm **Generate Deep Folder** để tạo cây thư mục sâu (phục vụ demo).
3. Thử:
   - **Scan File (Dùng Đệ quy)** để thấy rủi ro crash khi cây thư mục quá sâu
   - **Scan File đã khử đệ quy** để thấy cách fix bằng duyệt lặp (BFS/Queue)
4. Danh sách file sẽ hiển thị kèm kích thước (B/KB/MB/GB).
5. Bấm **Upload (Real)** để upload 1 file lên httpbin và xem trạng thái thành công/thất bại.

---

## Cấu trúc thư mục (chính)

- `app/src/main/java/com/example/project0/MainActivity.kt`
  - UI Compose
  - `scanFilesRecursively(...)`
  - `scanFilesIteratively(...)`
  - `generateDeepFolder(...)`
  - format hiển thị size
- `app/src/main/java/com/example/project0/Uploader.kt`
  - `uploadFileReal(...)` dùng OkHttp
  - `guessMediaType(...)`

---

## Ghi chú / Hạn chế

- `MANAGE_EXTERNAL_STORAGE` là quyền nhạy cảm; khi publish Play Store sẽ bị kiểm soát rất chặt.
- Quét “ALL” (root external storage) có thể chậm, phụ thuộc thiết bị và quyền truy cập.
- Upload chỉ là demo lên `httpbin.org` (không phải server production).

---
