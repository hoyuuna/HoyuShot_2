package app.hoyu.shot.model

import androidx.compose.ui.graphics.Color

/**
 * Technical specification and evaluation of Android Camera2 Hardware Support Levels.
 */
data class Camera2LevelInfo(
    val levelName: String,
    val title: String,
    val badgeLabel: String,
    val badgeColor: Color,
    val isFullySupported: Boolean,
    val summary: String,
    val detailedExplanation: String,
    val manualControlsStatus: String,
    val rawCaptureStatus: String,
    val perFrameControlStatus: String
)

object Camera2LevelExplainer {

    fun getLevelInfo(levelName: String): Camera2LevelInfo {
        return when (levelName) {
            "LEVEL_3" -> Camera2LevelInfo(
                levelName = "LEVEL_3",
                title = "LEVEL 3 // TOÀN DIỆN NHẤT",
                badgeLabel = "OPTIMAL // FULL + RAW + REPROCESS",
                badgeColor = Color(0xFF10B981), // Green
                isFullySupported = true,
                summary = "Đủ 100% tính năng. Hỗ trợ tối đa toàn bộ khả năng điều khiển thủ công và cảm biến của Camera2 API.",
                detailedExplanation = "Cảm biến đạt cấp độ cao nhất trong kiến trúc Camera2 của Android. Thiết bị hỗ trợ can thiệp độc lập 100% vào tốc độ màn trập (SEC) theo nano giây, độ nhạy sáng (ISO), lấy nét thủ công quang học (MF Diopter), chụp ảnh RAW DNG 12-bit không nén và đồng bộ phơi sáng từng khung hình (per-frame control) không độ trễ.",
                manualControlsStatus = "Hỗ trợ toàn diện (ISO, SEC, MF Diopter, EV, AWB)",
                rawCaptureStatus = "Hỗ trợ RAW DNG Sensor chuẩn 10/12-bit",
                perFrameControlStatus = "Đồng bộ phơi sáng từng khung hình không độ trễ"
            )
            "FULL" -> Camera2LevelInfo(
                levelName = "FULL",
                title = "FULL // ĐẦY ĐỦ TÍNH NĂNG",
                badgeLabel = "SUPPORTED // FULL MANUAL",
                badgeColor = Color(0xFF10B981), // Green
                isFullySupported = true,
                summary = "Đủ tính năng chuyên nghiệp. Hỗ trợ đầy đủ các chế độ chụp thủ công M, S, ISO, P.",
                detailedExplanation = "Cảm biến hỗ trợ đầy đủ các lệnh can thiệp thủ công chuyên nghiệp của Camera2 API: chỉnh tay tốc độ màn trập (SEC), ISO cảm biến, lấy nét tay MF, bù trừ sáng EV và khóa cân bằng trắng độc lập. Tốc độ chụp liên tiếp ở độ phân giải tối đa đạt chuẩn 30fps.",
                manualControlsStatus = "Hỗ trợ đầy đủ (Manual ISO, Shutter, MF, EV)",
                rawCaptureStatus = "Hỗ trợ chuẩn (Tùy thuộc phần cứng cảm biến)",
                perFrameControlStatus = "Hỗ trợ khóa thông số từng khung hình"
            )
            "LIMITED" -> Camera2LevelInfo(
                levelName = "LIMITED",
                summary = "Hạn chế tính năng. Nhà sản xuất chỉ mở một phần Camera2 API, thiếu một số tính năng cao cấp.",
                title = "LIMITED // HẠN CHẾ MỘT SỐ TÍNH NĂNG",
                badgeLabel = "PARTIAL // LIMITED CONTROL",
                badgeColor = Color(0xFFF59E0B), // Amber
                isFullySupported = false,
                detailedExplanation = "Cảm biến ở cấp độ LIMITED. Nhà sản xuất giới hạn một số API can thiệp phần cứng. Bạn vẫn có thể chụp ảnh và chỉnh một số thông số cơ bản, nhưng có thể bị HẠN CHẾ: không hỗ trợ xuất ảnh RAW DNG nguyên bản, dải tốc độ màn trập bị thu hẹp hoặc không thể đồng bộ phơi sáng từng khung hình ở tốc độ cao.",
                manualControlsStatus = "Hạn chế một phần (phụ thuộc driver nhà sản xuất)",
                rawCaptureStatus = "Thường KHÔNG hỗ trợ RAW DNG sensor",
                perFrameControlStatus = "Không hỗ trợ đồng bộ mượt giữa các frame"
            )
            "LEGACY" -> Camera2LevelInfo(
                levelName = "LEGACY",
                title = "LEGACY // KHÔNG ĐỦ TÍNH NĂNG (GIẢ LẬP)",
                badgeLabel = "UNSUPPORTED // CAMERA1 EMULATION",
                badgeColor = Color(0xFFEF4444), // Red
                isFullySupported = false,
                summary = "Không đủ tính năng. Thiết bị chỉ chạy tầng giả lập Camera1 cũ, không hỗ trợ chỉnh tay độc lập.",
                detailedExplanation = "Thiết bị chỉ hoạt động ở chế độ tương thích ngược Camera1 cũ. Không hỗ trợ can thiệp độc lập vào thông số cảm biến: chỉnh ISO thủ công, tốc độ màn trập độc lập, lấy nét diopter MF và RAW DNG đều bị vô hiệu hoá. HoyuShot chỉ có thể chụp ở chế độ tự động cơ bản.",
                manualControlsStatus = "KHÔNG hỗ trợ chỉnh tay độc lập (Giả lập Camera1)",
                rawCaptureStatus = "Không hỗ trợ RAW",
                perFrameControlStatus = "Không hỗ trợ can thiệp từng khung hình"
            )
            "EXTERNAL" -> Camera2LevelInfo(
                levelName = "EXTERNAL",
                title = "EXTERNAL // CAMERA GẮN NGOÀI",
                badgeLabel = "EXTERNAL // USB UVC",
                badgeColor = Color(0xFF06B6D4), // Cyan
                isFullySupported = false,
                summary = "Camera gắn ngoài qua cổng USB. Tính năng phụ thuộc vào phần cứng của thiết bị ngoại vi.",
                detailedExplanation = "Camera gắn ngoài qua cổng kết nối USB (UVC/webcam). Năng lực điều khiển thủ công phụ thuộc hoàn toàn vào driver và phần cứng của thiết bị kết nối ngoài.",
                manualControlsStatus = "Phụ thuộc driver thiết bị ngoại vi",
                rawCaptureStatus = "Không hỗ trợ",
                perFrameControlStatus = "Phụ thuộc driver USB UVC"
            )
            else -> Camera2LevelInfo(
                levelName = levelName,
                title = "$levelName // CHƯA XÁC ĐỊNH",
                badgeLabel = "UNKNOWN STATUS",
                badgeColor = Color(0xFF71717A), // Gray
                isFullySupported = false,
                summary = "Hệ thống báo cấp độ phần cứng chưa xác định ($levelName).",
                detailedExplanation = "Hệ thống báo cấp độ phần cứng $levelName. Cần kiểm tra chi tiết từng khả năng cảm biến thực tế để xác định mức độ hỗ trợ.",
                manualControlsStatus = "Đang kiểm tra...",
                rawCaptureStatus = "Đang kiểm tra...",
                perFrameControlStatus = "Đang kiểm tra..."
            )
        }
    }

    /**
     * Complete list of all Camera2 hardware levels for the technical reference guide.
     */
    val allLevelsReference: List<Camera2LevelInfo> by lazy {
        listOf(
            getLevelInfo("LEVEL_3"),
            getLevelInfo("FULL"),
            getLevelInfo("LIMITED"),
            getLevelInfo("LEGACY"),
            getLevelInfo("EXTERNAL")
        )
    }
}
