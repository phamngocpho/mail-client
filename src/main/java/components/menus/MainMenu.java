package components.menus;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import components.panels.dashboard.*;
import net.miginfocom.swing.MigLayout;
import utils.Constants;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * The MainMenu class represents the main navigation menu of an application.
 * It extends the JPanel class and provides a layout for various menu items,
 * headers, labels, and content panels. This class includes functionality for
 * selecting menu items, handling mouse events, and allowing the main application
 * to listen to menu item click events.
 */
public class MainMenu extends JPanel {

    private JPanel menuItemsPanel;
    private JPanel labelsPanel;
    private final Color selectedColor = new Color(60, 64, 67);
    private final Color hoverColor = new Color(50, 54, 57);
    private MenuItem selectedItem;
    private MenuItemClickListener clickListener;
    private JPanel defaultContent;
    private Compose composePanel;   // Reference to Compose
    private Drafts localDraftsPanel;   // Reference to Drafts panel

    // Interface để xử lý sự kiện click
    public interface MenuItemClickListener {
        void onMenuItemClicked(JPanel contentPanel);
    }

    public MainMenu() {
        init();
    }

    // Setter để MainPanel có thể đăng ký listener
    public void setMenuItemClickListener(MenuItemClickListener listener) {
        this.clickListener = listener;

        if (localDraftsPanel != null) {
            localDraftsPanel.setMenuItemClickListener(listener);
        }

        if (defaultContent != null) {
            clickListener.onMenuItemClicked(defaultContent);
        }
    }

    private void init() {
        setLayout(new MigLayout("fill", "[" + Constants.defaultMenuBarSize + "!]", "[][][grow]"));
        // Header with hamburger menu and Gmail logo
        JPanel header = createHeader();
        add(header, "wrap, growx");

        // Compose button
        JPanel composeContent = new Compose();
        JButton composeBtn = createComposeButton(composeContent);
        add(composeBtn, "wrap, gapx 15 15, gapy 10 10");

        // Scrollable content
        JScrollPane scrollPane = new JScrollPane();
        scrollPane.setBorder(null);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        JPanel content = new JPanel(new MigLayout("fill, insets 0", "[grow]", "[][grow]"));

        // Menu items
        menuItemsPanel = new JPanel(new MigLayout("fillx, insets 8 8 10 8", "[grow]", ""));
        addMenuItems();
        content.add(menuItemsPanel, "wrap, growx");

        // Labels section
        JPanel labelsSection = createLabelsSection();
        content.add(labelsSection, "grow");

        scrollPane.setViewportView(content);
        add(scrollPane, "grow");
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new MigLayout("insets 15 15 15 15", "[][grow]", "[]"));

        // Hamburger menu
        JButton menuBtn = new JButton("", new FlatSVGIcon("icons/menu/menu.svg", Constants.defaultIconSize, Constants.defaultIconSize));
        menuBtn.setBorderPainted(false);
        menuBtn.setContentAreaFilled(false);
        menuBtn.setFocusPainted(false);
        menuBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        // Gmail logo
        JLabel logoLabel = new JLabel("Gmail");

        header.add(menuBtn);
        header.add(logoLabel, "gapx 15");

        return header;
    }

    private JButton createComposeButton(JPanel composeContent) {
        // LƯU REFERENCEaddMenuItems
        if (composeContent instanceof Compose) {
            this.composePanel = (Compose) composeContent;
        }
        JButton btn = new JButton("Compose", new FlatSVGIcon("icons/menu/compose.svg", Constants.defaultIconSize, Constants.defaultIconSize));
        btn.setPreferredSize(new Dimension(150, 48));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setFocusPainted(false);

        btn.addActionListener(e -> {
            if (clickListener != null) {
                clickListener.onMenuItemClicked(composeContent);
            }
        });

        return btn;
    }

    private MenuItem createMenuItem(String iconPath, String text, boolean selected, JPanel content) {
        MenuItem item = new MenuItem(
                new FlatSVGIcon(iconPath, Constants.defaultIconSize, Constants.defaultIconSize),
                text,
                selected,
                content
        );
        item.putClientProperty(FlatClientProperties.STYLE, "arc:50");
        return item;
    }

    private void addMenuItems() {
        JPanel inboxContent = new Inbox("INBOX", "ALL");
        JPanel starredContent = new Inbox("INBOX", "STARRED");
        JPanel snoozedContent = new Inbox("[Gmail]/Snoozed", "ALL");
        JPanel sentContent = new Inbox("[Gmail]/Sent Mail", "ALL");
        localDraftsPanel = new Drafts();  // ← THAY ĐỔI: Dùng Drafts thay vì Inbox

        // Kết nối Compose với Drafts
        if (composePanel != null) {
            localDraftsPanel.setComposePanel(composePanel);
            composePanel.setDraftsPanel(localDraftsPanel);
            localDraftsPanel.setMenuItemClickListener(this.clickListener);
        }
        JPanel moreContent = createContentPanel("More Content");

        MenuItem inbox = createMenuItem("icons/menu/inbox.svg", "Inbox", true, inboxContent);
        MenuItem starred = createMenuItem("icons/inbox/star_outline.svg", "Starred", false, starredContent);
        MenuItem snoozed = createMenuItem("icons/menu/snoozed.svg", "Snoozed", false, snoozedContent);
        MenuItem sent = createMenuItem("icons/menu/sent.svg", "Sent", false, sentContent);
        MenuItem drafts = createMenuItem("icons/menu/drafts.svg", "Drafts", false, localDraftsPanel );
        MenuItem more = createMenuItem("icons/menu/more.svg", "More", false, moreContent);

        menuItemsPanel.add(inbox, "wrap, growx");
        menuItemsPanel.add(starred, "wrap, growx");
        menuItemsPanel.add(snoozed, "wrap, growx");
        menuItemsPanel.add(sent, "wrap, growx");
        menuItemsPanel.add(drafts, "wrap, growx");
        menuItemsPanel.add(more, "wrap, growx");

        selectedItem = inbox;
        defaultContent = inboxContent;
    }

    // Helper method để tạo panel nội dung mẫu
    private JPanel createContentPanel(String title) {
        JPanel panel = new JPanel(new MigLayout("fill, insets 20"));
        JLabel label = new JLabel(title, SwingConstants.CENTER);
        panel.add(label, "grow");
        return panel;
    }

    private JPanel createLabelsSection() {
        JPanel section = new JPanel(new MigLayout("fill, insets 20 8 10 8", "[grow]", "[][]"));

        JPanel headerPanel = new JPanel(new MigLayout("insets 5 12 5 12", "[grow][]", "[]"));

        JLabel labelsLabel = new JLabel("Labels");

        JButton addBtn = new JButton("+");
        addBtn.setBorderPainted(false);
        addBtn.setContentAreaFilled(false);
        addBtn.setFocusPainted(false);
        addBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        headerPanel.add(labelsLabel);
        headerPanel.add(addBtn);

        section.add(headerPanel, "wrap, growx");

        labelsPanel = new JPanel(new MigLayout("fillx, insets 0", "[grow]", ""));
        section.add(labelsPanel, "grow");

        return section;
    }

    public void addLabel(String name) {
        JPanel labelContent = createContentPanel(name + " Content");
        MenuItem label = new MenuItem(new FlatSVGIcon("icons/menu/add.svg", Constants.defaultIconSize, Constants.defaultIconSize), name, false, labelContent);
        label.putClientProperty(FlatClientProperties.STYLE, "arc:50");
        labelsPanel.add(label, "wrap, growx");
        labelsPanel.revalidate();
        labelsPanel.repaint();
    }

    // Menu Item Component
    private class MenuItem extends JPanel {
        private boolean isSelected;
        private final JPanel contentPanel;

        public MenuItem(FlatSVGIcon icon, String text, boolean selected, JPanel contentPanel) {
            this.isSelected = selected;
            this.contentPanel = contentPanel;
            setLayout(new MigLayout("insets 8 12 8 12", "[][]", "[]"));
            setCursor(new Cursor(Cursor.HAND_CURSOR));

            JLabel iconLabel = new JLabel(icon);

            JLabel textLabel = new JLabel(text);

            add(iconLabel);
            add(textLabel, "gapx 12");

            updateStyle();
            addMouseListeners();
        }

        private void addMouseListeners() {
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    setSelected(true);

                    // Load emails khi chuyển tab
                    if (contentPanel instanceof Inbox inbox) {
                        // Chỉ refresh nếu đã connected - gọi qua Inbox để có loading skeleton
                        if (inbox.getController() != null && inbox.getController().isConnected()) {
                            inbox.reloadFolder();
                        }
                    }

                    // Gọi listener để thông báo cho MainPanel cập nhật nội dung
                    if (clickListener != null && contentPanel != null) {
                        clickListener.onMenuItemClicked(contentPanel);
                    }
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    if (!isSelected) {
                        setBackground(hoverColor);
                        setOpaque(true);
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    if (!isSelected) {
                        setOpaque(false);
                        repaint();
                    }
                }
            });
        }

        public void setSelected(boolean selected) {
            if (selectedItem != null && selectedItem != this) {
                selectedItem.setSelected(false);
            }

            this.isSelected = selected;
            updateStyle();

            if (selected) {
                selectedItem = this;
            }
        }

        private void updateStyle() {
            if (isSelected) {
                setOpaque(true);
                setBackground(selectedColor);
            } else {
                setOpaque(false);
                setBorder(null);
            }
            repaint();
        }
    }
}