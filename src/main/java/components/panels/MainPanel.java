package components.panels;

import components.menus.MainMenu;
import net.miginfocom.swing.MigLayout;
import utils.Constants;

import javax.swing.*;

/**
 * The MainPanel class represents the main graphical user interface element
 * that contains a menu bar (MainMenu) and a content area. It is designed
 * to organize and display different panels dynamically based on user interaction
 * with the menu.
 * <p>
 * This class extends JPanel and uses the MigLayout for flexible and precise layout
 * management. It initializes a menu bar on the left side and dynamically updates
 * the right-side content area with new panels as needed.
 */
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

    /**
     * Sets the content panel of the content area and refreshes the layout.
     *
     * @param panel the JPanel to be displayed in the content area
     */
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