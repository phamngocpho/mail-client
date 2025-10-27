package utils;

import com.formdev.flatlaf.extras.FlatSVGIcon;

import javax.swing.*;
import java.awt.*;
import java.io.File;

/**
 * Utility class for handling user interface related functionalities.
 */
public class UIUtils {
    private static final int DEFAULT_ICON_SIZE = Constants.defaultIconSize;

    /**
     * Lấy biểu tượng SVG dựa trên phần mở rộng của tệp
     * @param file Tệp cần lấy biểu tượng
     * @param iconSize Kích thước biểu tượng
     * @return Biểu tượng SVG tương ứng với loại tệp
     */
    public static Icon getFileIcon(File file, int iconSize) {
        String fileName = file.getName().toLowerCase();
        String iconPath = "icons/compose/files/";

        if (fileName.endsWith(".txt")) {
            iconPath += "txt.svg";
        } else if (fileName.endsWith(".pdf")) {
            iconPath += "pdf.svg";
        } else if (fileName.endsWith(".doc") || fileName.endsWith(".docx")) {
            iconPath += "word.svg";
        } else if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") ||
                fileName.endsWith(".png") || fileName.endsWith(".gif") ||
                fileName.endsWith(".bmp") || fileName.endsWith(".svg")) {
            iconPath += "image.svg";
        } else if (fileName.endsWith(".mp3") || fileName.endsWith(".wav") ||
                fileName.endsWith(".flac") || fileName.endsWith(".aac") ||
                fileName.endsWith(".m4a") || fileName.endsWith(".ogg")) {
            iconPath += "audio.svg";
        } else if (fileName.endsWith(".mp4") || fileName.endsWith(".avi") ||
                fileName.endsWith(".mkv") || fileName.endsWith(".mov") ||
                fileName.endsWith(".wmv") || fileName.endsWith(".flv")) {
            iconPath += "video.svg";
        } else if (fileName.endsWith(".exe") || fileName.endsWith(".msi")) {
            iconPath += "exe.svg";
        } else if (fileName.endsWith(".bat") || fileName.endsWith(".cmd")) {
            iconPath += "bat.svg";
        } else {
            iconPath += "unknown.svg";
        }

        return new FlatSVGIcon(iconPath, iconSize, iconSize);
    }

    /**
     * Lấy biểu tượng SVG với kích thước mặc định
     * @param file Tệp cần lấy biểu tượng
     * @return Biểu tượng SVG tương ứng với loại tệp
     */
    public static Icon getFileIcon(File file) {
        return getFileIcon(file, DEFAULT_ICON_SIZE);
    }

    /**
     * Lấy màu văn bản từ UIManager hoặc trả về màu mặc định
     * @return Màu văn bản phù hợp với theme hiện tại
     */
    public static Color getTextColor() {
        // Ưu tiên lấy từ Label vì ổn định hơn
        Color textColor = UIManager.getColor("Label.foreground");
        if (textColor == null) {
            textColor = UIManager.getColor("TextField.foreground");
        }
        if (textColor == null) {
            // Detect dark theme by background color
            Color bg = UIManager.getColor("Panel.background");
            if (bg != null && isDarkColor(bg)) {
                textColor = Constants.white;
            } else {
                textColor = Color.BLACK;
            }
        }
        return textColor;
    }

    /**
     * Kiểm tra xem màu có phải là màu tối không
     * @param color Màu cần kiểm tra
     * @return true nếu là màu tối
     */
    private static boolean isDarkColor(Color color) {
        // Tính độ sáng theo công thức YIQ
        double brightness = (color.getRed() * 299 + color.getGreen() * 587 + color.getBlue() * 114) / 1000.0;
        return brightness < 128;
    }

    /**
     * Chuyển đổi màu sang chuỗi hex cho HTML/CSS
     * @param color Màu cần chuyển đổi
     * @return Chuỗi màu hex (vd: "#ffffff")
     */
    public static String colorToHex(Color color) {
        return String.format("#%02x%02x%02x",
                color.getRed(), color.getGreen(), color.getBlue());
    }

    /**
     * Lấy font size từ UIManager
     * @return Font size được set trong application
     */
    public static int getFontSize() {
        Font defaultFont = UIManager.getFont("defaultFont");
        if (defaultFont != null) {
            return defaultFont.getSize();
        }

        return Constants.systemFont.getSize();
    }

    /**
     * Tạo HTML template với màu văn bản và font size phù hợp
     * @return HTML template string với màu text và size đúng
     */
    public static String getHtmlTemplate() {
        Color textColor = getTextColor();
        String colorHex = colorToHex(textColor);
        int fontSize = Constants.systemFont.getSize(); // Use base system font size

        // Set color, font-size và line-height để khớp với UI và tránh text nhảy
        return String.format(
            "<html><body style='color: %s; font-size: %dpx; line-height: 1.5; margin: 0; padding: 0;'></body></html>",
            colorHex, fontSize
        );
    }
}