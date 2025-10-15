package application;

import com.formdev.flatlaf.fonts.roboto.FlatRobotoFont;
import com.formdev.flatlaf.themes.FlatMacDarkLaf;
import components.forms.FormsManager;
import components.panels.welcome.Welcome;
import raven.toast.Notifications;
import utils.Constants;

import javax.swing.*;
import java.awt.*;

public class Application extends JFrame {
    public Application() {
        init();
    }

    private void init() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        Notifications.getInstance().show(Notifications.Type.SUCCESS, Notifications.Location.TOP_CENTER, "Welcome");
        setSize((int) Constants.dimension.getWidth(), (int) (Constants.dimension.getHeight() - Constants.taskBarSize));
        Notifications.getInstance().setJFrame(this);
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

//        FormsManager.getInstance().showForm(new JPanel);
    }
}