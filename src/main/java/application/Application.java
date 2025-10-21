package application;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.formdev.flatlaf.fonts.roboto.FlatRobotoFont;
import com.formdev.flatlaf.themes.FlatMacDarkLaf;
import components.forms.FormsManager;
import components.panels.welcome.Welcome;
import raven.toast.Notifications;
import utils.Constants;

import javax.swing.*;
import java.awt.*;

/**
 * The Application class serves as the main entry point for the application.
 * It extends JFrame to provide a graphical user interface for the desktop application.
 * <p>
 * Upon initialization, it configures the main application window by setting its
 * size, location, and content pane. Additionally, it integrates with other
 * components such as Notifications, Constants, and FormsManager.
 * <p>
 * Responsibilities include:
 * - Setting up the main application frame with default configurations.
 * - Displaying a welcome message upon startup.
 * - Delegating content management to FormsManager.
 * - Configuring appearance using FlatLaf themes and fonts.
 */
public class Application extends JFrame {
    public Application() {
        init();
    }

    private void init() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setIconImage(new FlatSVGIcon("icons/application/mail_app.svg").getImage());
        Notifications.getInstance().show(Notifications.Type.SUCCESS, Notifications.Location.TOP_CENTER, "Welcome");
        setSize((int) Constants.dimension.getWidth(), (int) (Constants.dimension.getHeight() - Constants.taskBarSize));
        Notifications.getInstance().setJFrame(this);
        setTitle("Mail Client");
        setLocationRelativeTo(null);
        setContentPane(new Welcome());
        setMinimumSize(new Dimension((int) (Constants.dimension.getWidth() / 2), (int) Constants.dimension.getHeight() * 3 / 5));
        FormsManager.getInstance().initApplication(this);
    }

    public static void main(String[] args) {
        FlatRobotoFont.install();
        FlatMacDarkLaf.setup();
        UIManager.put("defaultFont", new Font(FlatRobotoFont.FAMILY, Font.PLAIN, Constants.systemFont.getSize() + 3));
        EventQueue.invokeLater(() -> new Application().setVisible(true));
    }
}