package application;

import com.formdev.flatlaf.fonts.roboto.FlatRobotoFont;
import com.formdev.flatlaf.themes.FlatMacDarkLaf;
import components.notifications.custom.CustomNotification;
import components.forms.FormsManager;
import components.notifications.popup.GlassPanePopup;
import components.panels.welcome.Welcome;
import raven.toast.Notifications;
import values.Value;

import javax.swing.*;
import java.awt.*;

public class Application extends JFrame {
    public Application() {
        GlassPanePopup.install(this);
        init();
    }

    private void init() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
//        Notifications.getInstance().show(Notifications.Type.SUCCESS, "test");
        setSize((int) Value.dimension.getWidth(), (int) (Value.dimension.getHeight() - Value.taskBarSize));
        Notifications.getInstance().setJFrame(this);
        CustomNotification customNotification = new CustomNotification();
        customNotification.setJFrame(this);
        setLocationRelativeTo(null);
        setContentPane(new Welcome());
        setMinimumSize(new Dimension((int) (Value.dimension.getWidth() / 2), (int) Value.dimension.getHeight() * 3 / 5));
        FormsManager.getInstance().initApplication(this);
    }

    public static void main(String[] args) {
        FlatRobotoFont.install();
        FlatMacDarkLaf.setup();
        UIManager.put("defaultFont", new Font(FlatRobotoFont.FAMILY, Font.PLAIN, 15));
        EventQueue.invokeLater(() -> new Application().setVisible(true));

//        FormsManager.getInstance().showForm(new sth);
    }
}