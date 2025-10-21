package utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Utility class for configuring logging directories.
 * Sets up the log directory path to be in the Mail Client folder.
 */
public class LogUtils {
    private static final Logger logger = LoggerFactory.getLogger(LogUtils.class);
    
    /**
     * Initialize log directory system property for logback configuration.
     * This must be called before any logging occurs (before logback initializes).
     * Call this method as the first line in Application.main().
     */
    public static void initLogDirectory() {
        try {
            // Lấy đường dẫn từ location của class file
            Path classPath = Paths.get(LogUtils.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            
            Path logDir;
            String classPathStr = classPath.toString();
            
            if (classPathStr.contains("target" + File.separator + "classes")) {
                // Đang chạy từ IDE: target/classes → lên Mail Client/logs
                logDir = classPath.getParent().getParent().resolve("logs");
            } else if (classPathStr.endsWith(".jar")) {
                // Đang chạy từ JAR: tạo logs cùng thư mục với jar
                logDir = classPath.getParent().resolve("logs");
            } else {
                // Fallback: tạo trong Mail Client/logs
                logDir = Paths.get(System.getProperty("user.dir"), "Mail Client", "logs");
            }
            
            // Set system property cho logback
            System.setProperty("LOG_DIR", logDir.toAbsolutePath().toString());
            
            // Log sau khi đã set (nếu logger đã khởi động)
            logger.debug("Log directory set to: {}", logDir.toAbsolutePath());
            
        } catch (Exception e) {
            // Fallback to default - log vào Mail Client/logs
            String fallbackDir = System.getProperty("user.dir") + File.separator + "Mail Client" + File.separator + "logs";
            System.setProperty("LOG_DIR", fallbackDir);
            System.err.println("Warning: Could not determine log directory, using fallback: " + fallbackDir);
        }
    }
}

