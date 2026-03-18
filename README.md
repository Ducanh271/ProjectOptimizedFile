# 🟢 App Chấm Công - Smart Attendance System

Một ứng dụng điểm danh nội bộ hiện đại dành cho doanh nghiệp, được xây dựng trên nền tảng Android với giao diện Jetpack Compose. Ứng dụng tích hợp công nghệ nhận diện khuôn mặt và hệ thống phát hiện giả mạo (Liveness & Deepfake Detection) nhằm đảm bảo tính minh bạch và an toàn tuyệt đối.

## ✨ Tính năng nổi bật

* **🔒 Bảo mật & Xác thực đa tầng:** * Đăng nhập an toàn với JWT (JSON Web Token).
    * Mã hóa và lưu trữ Token nội bộ bằng tiêu chuẩn AES-256 (Jetpack Security).
    * Luồng xác thực OTP và bắt buộc đổi mật khẩu cho lần đăng nhập đầu tiên.
* **📸 Điểm danh bằng khuôn mặt (Face Recognition):**
    * Sử dụng CameraX để quét khuôn mặt theo thời gian thực.
    * Tích hợp hệ thống AI phát hiện thực thể sống (Liveness Detection) và chống Deepfake, ngăn chặn việc sử dụng ảnh/video giả mạo.
* **📅 Quản lý thời gian trực quan:**
    * Giao diện trang chủ hiện đại với lịch cuộn ngang (Horizontal Calendar) và đồng hồ kim (Analog Clock).
    * Xem danh sách và lịch sử điểm danh chi tiết theo ngày/tháng.
* **🎨 Giao diện người dùng (UI/UX):**
    * Được xây dựng 100% bằng Jetpack Compose với tông màu Xanh nõn chuối chủ đạo.
    * Trải nghiệm mượt mà, hỗ trợ điều hướng (Navigation) tối ưu không giật lag.

## 🛠 Công nghệ & Kiến trúc (Tech Stack)

Dự án áp dụng chặt chẽ mô hình **Clean Architecture** và **MVVM** (Model-View-ViewModel) để đảm bảo code dễ bảo trì và mở rộng.

* **Ngôn ngữ:** Kotlin
* **UI Framework:** Jetpack Compose
* **Dependency Injection:** Dagger Hilt
* **Network & API:** Retrofit2, OkHttp3, Coroutines & Flow
* **Local Storage:** DataStore, EncryptedSharedPreferences (Hardware-backed security)
* **Camera:** CameraX API
* **Backend tích hợp:** Hệ thống API được viết bằng Golang và dịch vụ AI bằng Python.

## 📁 Cấu trúc thư mục (Folder Structure)

```text
app/src/main/java/com/example/appchamcong/
├── data/               # Lớp xử lý dữ liệu (API, Local Storage, Repositories)
├── di/                 # Khởi tạo Dependency Injection (NetworkModule, DatabaseModule)
├── ui/                 # Lớp giao diện người dùng
│   ├── components/     # Các thành phần UI dùng chung (Buttons, TextFields, TopBar)
│   ├── navigation/     # Quản lý luồng điều hướng (AuthGraph, MainGraph)
│   ├── screens/        # Các màn hình chính (Login, Home, FaceCheck, History)
│   └── theme/          # Định nghĩa màu sắc, font chữ (GreenMain, Typography)
├── utils/              # Các hàm tiện ích và Resource Wrapper xử lý lỗi
└── BaseApplication.kt  # Entry point cho Dagger Hilt
