package components.notifications.Infomations;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

public class Info extends JPanel {
    public Info () {
        init();
        setOpaque(false);
    }
    private void init () {
        setSize(300, 200);

    }
    protected void paintComponent (Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(getBackground());
        g2.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 15, 15));
        g2.dispose();
        super.paintComponent(g);
    }
}
