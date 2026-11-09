# HoyuShot

Ứng dụng máy ảnh thủ công chuyên nghiệp (Manual Pro Camera) cho Android, viết hoàn toàn bằng **Kotlin**, **Camera2 API** và **Jetpack Compose**.

---

## 📸 Tính năng nổi bật
* **Chế độ phơi sáng chuyên nghiệp:** `AUTO`, `P` (Program), `S` (Shutter Priority), `ISO` (ISO Priority), `M` (Manual).
* **Kiểm soát thủ công chi tiết:** ISO, Tốc độ màn trập (Shutter Speed theo nanoseconds), Lấy nét thủ công (Manual Focus Diopter), Cân bằng trắng (Kelvin 2500K - 8500K), Bù trừ sáng (EV).
* **Hỗ trợ ảnh RAW (DNG):** Lưu song song ảnh RAW nguyên bản từ cảm biến qua `DngCreator` chuẩn Adobe và JPEG chất lượng cao.
* **Thước đo cân bằng đường chân trời (Horizon Level):** Tự động phát hiện góc nghiêng và báo cân bằng $\pm 1^\circ$.
* **Biểu đồ quang phổ thời gian thực (Live RGB & Luma Histogram):** Phân tích 256 dải sắc độ frame-by-frame.
* **Giao diện vòng xoay (Dial Wheel UI):** Thao tác ngón cái tiện lợi kèm phản hồi xúc giác rung (Haptic feedback).

---

## 🚀 Tự động Build & Release (CI/CD)
* Mỗi lần push code lên nhánh `main`, GitHub Actions sẽ tự động:
  1. Compile dự án Kotlin trên môi trường cloud.
  2. Ký số APK với chữ ký phát hành vĩnh viễn (tránh conflict khi update).
  3. Tự động tăng version `0.0.x` (bắt đầu từ `0.0.1`, cộng dần theo số lần build).
  4. Đăng tải file cài đặt `.apk` dưới dạng **Pre-release** trên mục Releases của GitHub.
