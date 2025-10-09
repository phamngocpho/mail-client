package components.panels.welcome;

import com.formdev.flatlaf.FlatClientProperties;
import components.forms.FormsManager;
import components.panels.MainPanel;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;

public class Welcome extends JPanel {
    public Welcome() {
        init();
    }

    private void init() {
        setLayout(new MigLayout("fill, insets 0"));

        JButton btn = new JButton("Welcome");
        btn.putClientProperty(FlatClientProperties.STYLE, "arc:100");

        btn.setBackground(Color.decode("#2C2C2C"));
//        btn.setFocusPainted(false);
        btn.addActionListener(e -> {
            FormsManager.getInstance().showForm(new MainPanel());
        });

        btn.setPreferredSize(new Dimension(240, 80));
        btn.setMaximumSize(new Dimension(360, 120));
        add(btn, "dock center, span, align center");
    }
}