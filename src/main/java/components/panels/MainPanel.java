package components.panels;

import components.menus.MainMenu;
import components.panels.dashboard.Compose;
import components.panels.dashboard.Drafts;
import net.miginfocom.swing.MigLayout;
import utils.Constants;

import javax.swing.*;

/**
 * The MainPanel class represents the main graphical user interface element
 * that contains a menu bar (MainMenu) and a content area. It is designed
 * to organize and display different panels dynamically based on user interaction
 * with the menu.
 */
public class MainPanel extends JPanel {

    private MainMenu menu;
    private JPanel contentArea;
    private JPanel currentPanel; // Track current panel
    private Compose composePanel;
    private Drafts draftsPanel;
    private static MainPanel instance;

    public MainPanel() {
        init();
    }

    private void init() {
        setLayout(new MigLayout("fill, insets 0", "[" + Constants.defaultMenuBarSize + "!][grow]", "[grow]"));

        menu = new MainMenu();
        contentArea = new JPanel(new MigLayout("fill, insets 0"));

        // Create Compose and Drafts panels
        composePanel = new Compose();
        draftsPanel = new Drafts();

        // ⭐ KẾT NỐI 2 PANELS VỚI NHAU
        composePanel.setDraftsPanel(draftsPanel);
        draftsPanel.setComposePanel(composePanel);

        // Set menu item click listener with auto-save
        menu.setMenuItemClickListener(this::setContentWithAutoSave);

        menu.addLabel("Work");
        menu.addLabel("Personal");
        menu.addLabel("Important");

        add(menu, "grow");
        add(contentArea, "grow, gap 10 1 0 1");
    }

    /**
     * Sets the content panel with auto-save functionality
     * Automatically saves draft when switching away from Compose panel
     */
    private void setContentWithAutoSave(JPanel newPanel) {
        SwingUtilities.invokeLater(() -> {
            // LƯU DRAFT KHI CHUYỂN KHỎI COMPOSE PANEL
            if (currentPanel instanceof Compose) {
                ((Compose) currentPanel).saveDraftBeforeSwitching();
            }

            // Switch to new panel
            contentArea.removeAll();
            contentArea.add(newPanel, "grow");
            contentArea.revalidate();
            contentArea.repaint();

            // Update current panel reference
            currentPanel = newPanel;

            // ⭐ REFRESH DRAFTS PANEL NẾU ĐANG CHUYỂN ĐẾN NÓ
            if (newPanel instanceof Drafts) {
                ((Drafts) newPanel).refresh();
            }
        });
    }

    /**
     * Original setContent method (kept for backward compatibility)
     */
    public void setContent(JPanel panel) {
        setContentWithAutoSave(panel);
    }

    public MainMenu getMenu() {
        return menu;
    }

    public Compose getComposePanel() {
        return composePanel;
    }

    public Drafts getDraftsPanel() {
        return draftsPanel;
    }

}