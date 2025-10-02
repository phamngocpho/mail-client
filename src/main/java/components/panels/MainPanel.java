package components.panels;

import components.menus.MainMenu;
import net.miginfocom.swing.MigLayout;
import values.Value;

import javax.swing.*;
import javax.swing.border.LineBorder;

public class MainPanel extends JPanel {

    private MainMenu menu;
    private JPanel contentArea;

    public MainPanel() {
        init();
    }

    private void init() {
        setLayout(new MigLayout("fill, insets 0", "[" + Value.defaultMenuBarSize + "!][grow]", "[grow]"));

        menu = new MainMenu();

        contentArea = new JPanel(new MigLayout("fill, insets 0"));
        contentArea.setBorder(new LineBorder(Value.deep_blue, 1));

        menu.setMenuItemClickListener(this::setContent);

        menu.addLabel("Work");
        menu.addLabel("Personal");
        menu.addLabel("Important");

        add(menu, "grow");
        add(contentArea, "grow, gap 10 1 0 1");
    }

    // Method để thay đổi content
    public void setContent(JPanel panel) {
        contentArea.removeAll();
        contentArea.add(panel, "grow");
        contentArea.revalidate();
        contentArea.repaint();
    }

    public MainMenu getMenu() {
        return menu;
    }
}