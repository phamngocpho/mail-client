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
    public static Color sent_time = Color.decode("#686868");
    public static Color white = Color.decode("#ffffff");
    public static final String resources = System.getProperty("user.dir") + "/src/main/resources/";
    public static final String DateTimeFormat = "dd/MM/yyyy";
    public static final Font systemFont = (Font) Toolkit.getDefaultToolkit().getDesktopProperty("win.messagebox.font");

}
