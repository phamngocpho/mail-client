package utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import raven.toast.Notifications;

import java.io.IOException;
import java.io.InputStream;
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
        try (InputStream input = ConfigUtils.class.getClassLoader().getResourceAsStream("local.properties")) {
            if (input == null) {
                throw new IOException("local.properties not found in classpath");
            }
            properties.load(input);
            loaded = true;
            logger.info("Loaded local.properties successfully");
        } catch (IOException e) {
            logger.error("Cannot load local.properties: {}", e.getMessage());
            Notifications.getInstance().show(Notifications.Type.ERROR, "Cannot load local.properties!");
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
}