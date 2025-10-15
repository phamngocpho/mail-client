package components.panels;

import components.menus.MainMenu;
import net.miginfocom.swing.MigLayout;
import utils.Constants;

import javax.swing.*;

public class MainPanel extends JPanel {

    private MainMenu menu;
    private JPanel contentArea;

    public MainPanel() {
        init();
    }

    private void init() {
        setLayout(new MigLayout("fill, insets 0", "[" + Constants.defaultMenuBarSize + "!][grow]", "[grow]"));

        menu = new MainMenu();

        contentArea = new JPanel(new MigLayout("fill, insets 0"));

        menu.setMenuItemClickListener(this::setContent);

        menu.addLabel("Work");
        menu.addLabel("Personal");
        menu.addLabel("Important");

        add(menu, "grow");
        add(contentArea, "grow, gap 10 1 0 1");
    }

    // Method để thay đổi content
    public void setContent(JPanel panel) {
        SwingUtilities.invokeLater(() -> {
            contentArea.removeAll();
            contentArea.add(panel, "grow");
            contentArea.revalidate();
            contentArea.repaint();
        });
    }

    public MainMenu getMenu() {
        return menu;
    }
}