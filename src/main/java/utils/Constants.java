package utils;

import java.awt.*;

/**
 * The Constants class serves as a container for various static constant values
 * used throughout the application. These constants include configurations
 * for IMAP and SMTP, timeout settings, server command strings, response codes,
 * UI-related attributes, and other utility properties.
 * <p>
 * This class is designed for easy access to widely used constant values without
 * the need for instantiation. All fields in this class are declared `public static`
 * and are intended to be immutable.
 * <p>
 * Categories of Constants:
 * 1. IMAP Configuration: Includes port numbers and command strings.
 * 2. SMTP Configuration: Includes port numbers and command strings.
 * 3. Timeout Settings: Defines socket and connection timeout values for network operations.
 * 4. Server Command Strings: Contains command constants for IMAP and SMTP protocols.
 * 5. Response Codes: Defines expected server response codes for IMAP and SMTP interactions.
 * 6. UI-related Constants: Includes UI dimensions, colors, font configurations, and resource paths.
 * 7. Utility Constants: Miscellaneous constants for application operations (e.g., email pagination, resource paths).
 */
public class Constants {
    // IMAP Configuration
    public static final int IMAP_SSL_PORT = 993;
    public static final int IMAP_PORT = 143;
    public static final String IMAP_TAG_PREFIX = "A";

    // SMTP Configuration
    public static final int SMTP_SSL_PORT = 465;
    public static final int SMTP_TLS_PORT = 587;
    public static final int SMTP_PORT = 2;

    // Local IP Configuration (null = auto, set to your local IP if needed)
    public static final String LOCAL_IP = null; // Set to your local IP address (e.g., "192.168.1.100") or null for auto

    // Timeouts
    public static final int SOCKET_TIMEOUT = 60000; // 60 seconds (increased for large email fetches)
    public static final int CONNECTION_TIMEOUT = 10000; // 10 seconds

    // IMAP Commands
    public static final String IMAP_CAPABILITY = "CAPABILITY";
    public static final String IMAP_LOGIN = "LOGIN";
    public static final String IMAP_SELECT = "SELECT";
    public static final String IMAP_FETCH = "FETCH";
    public static final String IMAP_LOGOUT = "LOGOUT";
    public static final String IMAP_LIST = "LIST";

    // SMTP Commands
    public static final String SMTP_EHLO = "EHLO";
    public static final String SMTP_HELO = "HELO";
    public static final String SMTP_STARTTLS = "STARTTLS";
    public static final String SMTP_AUTH_LOGIN = "AUTH LOGIN";
    public static final String SMTP_MAIL_FROM = "MAIL FROM:";
    public static final String SMTP_RCPT_TO = "RCPT TO:";
    public static final String SMTP_DATA = "DATA";
    public static final String SMTP_QUIT = "QUIT";

    // Response Codes
    public static final String IMAP_OK = "OK";
    public static final String IMAP_NO = "NO";
    public static final String IMAP_BAD = "BAD";

    public static final String SMTP_READY = "220";
    public static final String SMTP_OK = "250";
    public static final String SMTP_AUTH_CONTINUE = "334";
    public static final String SMTP_START_MAIL = "354";
    public static final String SMTP_CLOSING = "221";

    // Number of emails to fetch per request
    public static final int EMAILS_PER_PAGE = 50;
    public static final long CACHE_DURATION = 5 * 60 * 1000;

    public static Toolkit toolkit = Toolkit.getDefaultToolkit();
    public static Dimension dimension = toolkit.getScreenSize();
    public static Insets insets = toolkit.getScreenInsets(GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration());
    public static final int taskBarSize = insets.bottom;
    public static final int defaultMenuBarSize = 200;
    public static final int defaultIconSize = 22;
    public static Color onlineStatus = Color.decode("#43c95a");
    public static Color lighter_gray = Color.decode("#404040");
    public static Color bolder_gray = Color.decode("#3c3c3c");
    public static Color bright_red = Color.decode("#ff4d4d");
    public static Color bright_orange = Color.decode("#ffb04d");
    public static Color bright_green = Color.decode("#58C359");
    public static Color sky_blue = Color.decode("#38bdf8");
    public static Color deep_blue = Color.decode("#2a84ff");
    public static Color dark_gray = Color.decode("#282828");
    public static Color sent_time = Color.decode("#686868");
    public static Color white = Color.decode("#ffffff");
    public static final String resources = System.getProperty("user.dir") + "/src/main/resources/";
    public static final String DateTimeFormat = "dd/MM/yyyy";
    public static final Font systemFont = (Font) Toolkit.getDefaultToolkit().getDesktopProperty("win.messagebox.font");
}