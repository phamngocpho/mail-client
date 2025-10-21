package utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Utility class for managing user preferences.
 * Stores preferences in a file located in the user's home directory.
 * This allows the application to remember user settings across sessions.
 */
public class PreferencesUtils {
    private static final Logger logger = LoggerFactory.getLogger(PreferencesUtils.class);
    private static final String PREFERENCES_DIR = ".mailclient";
    private static final String PREFERENCES_FILE = "preferences.properties";
    private static final Properties preferences = new Properties();
    private static Path preferencesPath;
    
    // Preference keys
    private static final String KEY_HAS_SEEN_WELCOME = "has_seen_welcome";
    
    static {
        initPreferences();
    }
    
    /**
     * Initialize preferences by creating the preferences directory and file if needed,
     * then loading existing preferences.
     */
    private static void initPreferences() {
        try {
            // Lấy đường dẫn từ location của class file (trong Mail Client/)
            // Sử dụng toURI() để xử lý đúng trên cả Windows và Unix
            Path classPath = Paths.get(PreferencesUtils.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            
            Path basePath;
            String classPathStr = classPath.toString();
            
            if (classPathStr.contains("target" + File.separator + "classes")) {
                // Đang chạy từ IDE: target/classes → lên Mail Client/
                basePath = classPath.getParent().getParent();
            } else if (classPathStr.endsWith(".jar")) {
                // Đang chạy từ JAR: lấy thư mục chứa jar
                basePath = classPath.getParent();
            } else {
                // Fallback: dùng user.dir
                basePath = Paths.get(System.getProperty("user.dir"));
            }
            
            Path preferencesDir = basePath.resolve(PREFERENCES_DIR);
            
            // Tạo thư mục nếu chưa tồn tại
            if (!Files.exists(preferencesDir)) {
                Files.createDirectories(preferencesDir);
                logger.info("Created preferences directory: {}", preferencesDir);
            }
            
            // Tạo file preferences
            preferencesPath = preferencesDir.resolve(PREFERENCES_FILE);
            if (!Files.exists(preferencesPath)) {
                Files.createFile(preferencesPath);
                logger.info("Created preferences file: {}", preferencesPath);
            }
            
            // Load preferences hiện tại
            loadPreferences();
            
        } catch (Exception e) {
            logger.error("Error initializing preferences: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Load preferences from file
     */
    private static void loadPreferences() {
        try (InputStream input = Files.newInputStream(preferencesPath)) {
            preferences.load(input);
            logger.info("Loaded preferences successfully");
        } catch (IOException e) {
            logger.error("Error loading preferences: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Save preferences to file
     */
    private static void savePreferences() {
        try (OutputStream output = Files.newOutputStream(preferencesPath)) {
            preferences.store(output, "Mail Client User Preferences");
            logger.info("Saved preferences successfully");
        } catch (IOException e) {
            logger.error("Error saving preferences: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Check if user has seen the welcome screen
     * @return true if user has seen welcome screen, false otherwise
     */
    public static boolean hasSeenWelcome() {
        return Boolean.parseBoolean(preferences.getProperty(KEY_HAS_SEEN_WELCOME, "false"));
    }
    
    /**
     * Mark that user has seen the welcome screen
     */
    public static void setHasSeenWelcome(boolean value) {
        preferences.setProperty(KEY_HAS_SEEN_WELCOME, String.valueOf(value));
        savePreferences();
        logger.info("Set has_seen_welcome to: {}", value);
    }
    
    /**
     * Reset all preferences (useful for testing or resetting the app)
     */
    public static void resetPreferences() {
        preferences.clear();
        savePreferences();
        logger.info("Reset all preferences");
    }
    
    /**
     * Get a custom preference value
     * @param key The preference key
     * @param defaultValue The default value if key doesn't exist
     * @return The preference value or default value
     */
    public static String getPreference(String key, String defaultValue) {
        return preferences.getProperty(key, defaultValue);
    }
    
    /**
     * Set a custom preference value
     * @param key The preference key
     * @param value The preference value
     */
    public static void setPreference(String key, String value) {
        preferences.setProperty(key, value);
        savePreferences();
    }
}

