package utils;

import com.formdev.flatlaf.extras.FlatSVGIcon;

import javax.swing.*;
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
}