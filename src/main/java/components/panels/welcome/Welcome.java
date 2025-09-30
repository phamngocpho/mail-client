package components.panels.welcome;

import components.buttons.GradientButton;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;

public class Welcome extends JPanel {
    public Welcome() {
        init();
    }

    private void init() {
        setLayout(new MigLayout("fill, insets 0"));

        GradientButton btn = new GradientButton();
        btn.setText("Hello World");

        btn.setColor1(Color.decode("#2C2C2C"));
        btn.setColor2(Color.decode("#2C2C2C"));
        btn.setSizeSpeed(12f);

        btn.setPreferredSize(new Dimension(240, 80));
        btn.setMaximumSize(new Dimension(360, 120));

        add(btn, "dock center, span, align center");
    }
}