package values;

import java.awt.*;

public class Value {
    static Toolkit toolkit = Toolkit.getDefaultToolkit();
    public static Dimension dimension = toolkit.getScreenSize();
    static Insets insets = toolkit.getScreenInsets(GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration());
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
    public static Color message_left = Color.decode("#303030");
    public static Color message_right = Color.decode("#1371ff");
    public static Color unsent_message = Color.decode("#8b8c90");
    public static Color sent_time = Color.decode("#686868");
    public static Color white = Color.decode("#ffffff");
    public static final String app_password = "qcdp ikeo ozcb ohnj";
    public static final String email = "chatapplication41@gmail.com";
    public static final String url = "jdbc:sqlserver://localhost:1433;" + "databaseName=Application;" + "user=sa;" + "password=160;" + "encrypt=true;" + "trustServerCertificate=true";
    public static final String resources = System.getProperty("user.dir") + "/src/main/resources/";
    public static final int PORT = 1234;
    public static final String SERVER = "administrator";
    public static final String DateTimeFormat = "dd/MM/yyyy";
    public static final Font systemFont = (Font) Toolkit.getDefaultToolkit().getDesktopProperty("win.messagebox.font");

}
