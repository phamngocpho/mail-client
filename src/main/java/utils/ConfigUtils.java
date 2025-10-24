package utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import raven.toast.Notifications;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Utility class for managing configuration properties and related settings.
 * This class is responsible for loading and providing access to application
 * configuration values defined in the `local.properties` file.
 * <p>
 * The class initializes the property values upon loading by invoking the `loadProperties`
 * method. If the file cannot be loaded, an error message is logged and displayed via
 * the notification system.
 */
public class ConfigUtils {
    private static final Properties properties = new Properties();
    private static boolean loaded = false;
    private static final Logger logger = LoggerFactory.getLogger(ConfigUtils.class);

    // Load file properties
    static {
        loadProperties();
    }

    private static void loadProperties() {
        try {
            // Thử load từ resources trước
            InputStream input = ConfigUtils.class.getClassLoader().getResourceAsStream("local.properties");
            if (input != null) {
                properties.load(input);
                input.close();
                loaded = true;
                logger.info("Loaded local.properties from resources successfully");
            } else {
                logger.warn("local.properties not found in resources. User needs to login first.");
                loaded = false;
            }
        } catch (Exception e) {
            logger.error("Cannot load local.properties: {}", e.getMessage());
            loaded = false;
        }
    }
    
    /**
     * Get path to resources directory (src/main/resources)
     */
    private static Path getResourcesPath() {
        try {
            // Lấy đường dẫn từ class location
            Path classPath = Paths.get(ConfigUtils.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            
            String classPathStr = classPath.toString();
            
            if (classPathStr.contains("target" + File.separator + "classes")) {
                // target/classes → src/main/resources
                Path projectRoot = classPath.getParent().getParent();
                return projectRoot.resolve("src").resolve("main").resolve("resources");
            } else if (classPathStr.endsWith(".jar")) {
                // lưu vào thư mục resources bên cạnh jar
                Path jarDir = classPath.getParent();
                Path resourcesDir = jarDir.resolve("resources");
                if (!Files.exists(resourcesDir)) {
                    Files.createDirectories(resourcesDir);
                }
                return resourcesDir;
            } else {
                // Fallback
                return Paths.get(System.getProperty("user.dir"))
                        .resolve("src").resolve("main").resolve("resources");
            }
        } catch (Exception e) {
            logger.error("Error getting resources path: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Save login credentials to local.properties in resources directory
     */
    public static void saveLoginCredentials(String imapHost, String email, String password) {
        try {
            Path resourcesPath = getResourcesPath();
            if (resourcesPath == null) {
                throw new IOException("Cannot determine resources path");
            }
            
            Path configFilePath = resourcesPath.resolve("local.properties");
            
            // Update properties
            properties.setProperty("imap_host", imapHost);
            properties.setProperty("email", email);
            properties.setProperty("app_password", password);
            
            // Derive SMTP host from IMAP host
            String smtpHost = imapHost.replace("imap.", "smtp.");
            properties.setProperty("smtp_host", smtpHost);
            properties.setProperty("smtp_port", "587");
            properties.setProperty("imap_port", "993");
            
            // Save to file
            try (OutputStream output = Files.newOutputStream(configFilePath)) {
                properties.store(output, "Mail Client Login Credentials");
                loaded = true;
                logger.info("Saved login credentials to: {}", configFilePath);
            }
        } catch (Exception e) {
            logger.error("Error saving login credentials: {}", e.getMessage(), e);
            Notifications.getInstance().show(Notifications.Type.ERROR, "Cannot save login credentials!");
        }
    }

    // Getter methods
    public static String getEmail() {
        return properties.getProperty("email", "");
    }

    public static String getAppPassword() {
        return properties.getProperty("app_password", "");
    }

    public static String getSmtpHost() {
        return properties.getProperty("smtp_host", "smtp.gmail.com");
    }

    public static int getSmtpPort() {
        return Integer.parseInt(properties.getProperty("smtp_port", "587"));
    }

    public static String getImapHost() {
        return properties.getProperty("imap_host", "imap.gmail.com");
    }

    public static int getImapPort() {
        return Integer.parseInt(properties.getProperty("imap_port", "993"));
    }

    public static boolean isLoaded() {
        return loaded;
    }
    
    /**
     * Check if we have valid login credentials
     */
    public static boolean hasValidCredentials() {
        return loaded && 
               !getEmail().isEmpty() && 
               !getAppPassword().isEmpty() && 
               !getImapHost().isEmpty();
    }
}