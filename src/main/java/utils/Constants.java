package utils;

import java.awt.*;

public class Constants {
    // IMAP Configuration
    public static final int IMAP_SSL_PORT = 993;
    public static final int IMAP_PORT = 143;
    public static final String IMAP_TAG_PREFIX = "A";

    // SMTP Configuration
    public static final int SMTP_SSL_PORT = 465;
    public static final int SMTP_TLS_PORT = 587;
    public static final int SMTP_PORT = 2;

    // Timeouts
    public static final int SOCKET_TIMEOUT = 30000; // 30 seconds
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