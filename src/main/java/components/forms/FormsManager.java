package components.forms;

import application.Application;
import com.formdev.flatlaf.extras.FlatAnimatedLafChange;

import javax.swing.*;
import java.awt.*;

/**
 * The FormsManager class is responsible for managing the display and
 * initialization of forms in the application. It provides functionality to
 * initialize the main application frame and dynamically update its content pane
 * to show different forms.
 *
 * This class follows a singleton pattern to ensure only one global instance
 * is used throughout the application.
 */
public class FormsManager {
    private Application application;
    private static FormsManager instance;

    public static FormsManager getInstance() {
        if (instance == null) {
            instance = new FormsManager();
        }
        return instance;
    }

    public void initApplication(Application application) {
        this.application = application;
    }

    public void showForm(JComponent form) {
        EventQueue.invokeLater(() -> {
            FlatAnimatedLafChange.showSnapshot();
            application.setContentPane(form);
            application.revalidate();
            application.repaint();
            FlatAnimatedLafChange.hideSnapshotWithAnimation();
        });
    }
}
