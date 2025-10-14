package components.panels.dashboard;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import components.dialogs.ImapLoginDialog;
import controllers.ImapController;
import net.miginfocom.swing.MigLayout;
import models.Email;
import raven.toast.Notifications;
import values.Value;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static utils.UIUtils.getFileIcon;

public class Inbox extends JPanel {
    private JTable emailTable;
    private DefaultTableModel tableModel;
    private JLabel fromLabel, subjectLabel, dateLabel;
    private JTextArea bodyTextArea;
    private JPanel attachmentsPanel;
    private List<Email> emails;

    // Star icons
    private FlatSVGIcon starFilledIcon;
    private FlatSVGIcon starOutlineIcon;
    private FlatSVGIcon selectOutlineIcon;
    private final int iconSize = Value.defaultIconSize - 5;

    // Controller
    private final ImapController controller;

    public Inbox() {
        emails = new ArrayList<>();
        loadIcons();
        init();
        // Khởi tạo controller
        this.controller = new ImapController(this);

        // Hiển thị login dialog khi khởi động
        SwingUtilities.invokeLater(this::showLoginDialog);
    }

    private void loadIcons() {
        starFilledIcon = new FlatSVGIcon("icons/menu/starred.svg", iconSize, iconSize);
        starOutlineIcon = new FlatSVGIcon("icons/inbox/star_outline.svg", iconSize, iconSize);
        selectOutlineIcon = new FlatSVGIcon("icons/inbox/select.svg", iconSize, iconSize);
    }

    private void init() {
        setLayout(new MigLayout("fill, insets 0", "[grow]", "[][grow]"));

        // Top toolbar với tabs (Primary, Social, Promotions, Updates)
        add(createTopToolbar(), "growx, wrap");

        // Split pane: Email list | Detail view
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(700);
        splitPane.setBorder(null);

        // Left: Email list
        splitPane.setLeftComponent(createEmailListPanel());

        // Right: Email detail
        splitPane.setRightComponent(createDetailPanel());
        splitPane.setDividerLocation((int) (Value.dimension.getWidth() * 0.57));
        splitPane.putClientProperty(FlatClientProperties.STYLE,
                "gripDotCount: 0;"
                        + "dividerSize: 5");

        add(splitPane, "grow");
    }

    private JPanel createTopToolbar() {
        JPanel toolbar = new JPanel(new MigLayout("fillx, insets 3", "[]5[]5[]push[grow]", "[]"));

        // Connect button
        JButton connectButton = new JButton("Connect");
        connectButton.putClientProperty(FlatClientProperties.BUTTON_TYPE,
                FlatClientProperties.BUTTON_TYPE_BORDERLESS);
        connectButton.addActionListener(e -> showLoginDialog());
        toolbar.add(connectButton, "");

        // Refresh button
        JButton refreshButton = new JButton(new FlatSVGIcon("icons/inbox/refresh.svg", iconSize, iconSize));
        refreshButton.putClientProperty(FlatClientProperties.BUTTON_TYPE,
                FlatClientProperties.BUTTON_TYPE_BORDERLESS);
        refreshButton.setToolTipText("Refresh");
        refreshButton.addActionListener(e -> {
            if (controller != null && controller.isConnected()) {
                controller.refresh();
            }
        });
        toolbar.add(refreshButton, "top, gap top 5, gap right 3");

        // Category tabs
        // Tab counters
        JTabbedPane categoryTabs = new JTabbedPane(JTabbedPane.TOP);
        categoryTabs.addTab("Primary (50)", new JPanel());
        categoryTabs.addTab("Social (3)", new JPanel());
        categoryTabs.addTab("Promotions (3)", new JPanel());
        categoryTabs.addTab("Updates (0)", new JPanel());
        toolbar.add(categoryTabs, "w 400:600:, h 40!");

        // Search box (right side)
        JButton searchButton = new JButton(new FlatSVGIcon("icons/inbox/search.svg", iconSize - 1, iconSize - 1));
        searchButton.setMargin(new Insets(4, 4, 4, 4));

        JTextField searchField = new JTextField();
        searchField.putClientProperty(FlatClientProperties.STYLE, "arc: 50;");
        searchField.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_COMPONENT, searchButton);
        searchField.setPreferredSize(new Dimension(300, 40));
        toolbar.add(searchField, "w 250:300:350, gap right 10, al right");
        return toolbar;
    }

    private void showContextMenu(MouseEvent e) {
        int row = emailTable.rowAtPoint(e.getPoint());
        if (row < 0 || row >= emails.size()) return;

        emailTable.setRowSelectionInterval(row, row);
        Email email = emails.get(row);

        JPopupMenu menu = new JPopupMenu();

        // Mark as read/unread
        JMenuItem markReadItem = new JMenuItem(
                email.hasFlag("Seen") ? "Mark as Unread" : "Mark as Read"
        );
        markReadItem.addActionListener(ev -> controller.markAsRead(email, !email.hasFlag("Seen")));
        menu.add(markReadItem);

        // Delete
        JMenuItem deleteItem = new JMenuItem("Delete");
        deleteItem.addActionListener(ev -> controller.deleteEmail(email));
        menu.add(deleteItem);

        menu.show(emailTable, e.getX(), e.getY());
    }

    /**
     * Refresh a single email row
     */
    public void refreshEmailRow(Email email) {
        int index = emails.indexOf(email);
        if (index >= 0) {
            tableModel.setValueAt(
                    email.hasFlag("Seen") ? extractName(email.getFrom()) :
                            "<html><b>" + extractName(email.getFrom()) + "</b></html>",
                    index, 2
            );
            tableModel.fireTableRowsUpdated(index, index);
        }
    }

    /**
     * Show a login dialog to connect to IMAP
     */
    private void showLoginDialog() {
        Frame parentFrame = (Frame) SwingUtilities.getWindowAncestor(this);
        boolean connected = ImapLoginDialog.showDialog(parentFrame, controller);

        // Nếu user cancel, hiển thị thông báo
        if (!connected) {
            subjectLabel.setText("Not Connected");
            fromLabel.setText("");
            dateLabel.setText("");
            bodyTextArea.setText("Click 'Connect' button to connect to your email server and load emails.");
        }
    }

    /**
     * Show loading state
     */
    public void showLoading() {
        subjectLabel.setText("Loading emails...");
        fromLabel.setText("");
        dateLabel.setText("");
        bodyTextArea.setText("Please wait while we fetch your emails from the server.");
        tableModel.setRowCount(0);
    }

    /**
     * Email list panel (left side)
     */
    private JScrollPane createEmailListPanel() {
        String[] columns = {"", "", "Sender", "Subject", "Time"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 0; // Chỉ checkbox editable, star sẽ handle bằng mouse click
            }

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                if (columnIndex == 0) return Boolean.class;
                if (columnIndex == 1) return ImageIcon.class; // Star column
                return String.class;
            }
        };

        emailTable = new JTable(tableModel);
        emailTable.setFocusable(false);
        emailTable.setRowHeight(48);
        emailTable.setShowGrid(false);
        emailTable.setIntercellSpacing(new Dimension(0, 0));
        emailTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Column widths
        emailTable.getColumnModel().getColumn(0).setMaxWidth(40);  // Checkbox
        emailTable.getColumnModel().getColumn(0).setHeaderRenderer((jTable, o, b, b1, i, i1) -> new JLabel(selectOutlineIcon));
        emailTable.getColumnModel().getColumn(1).setMaxWidth(40);  // Star
        emailTable.getColumnModel().getColumn(1).setHeaderRenderer((jTable, o, b, b1, i, i1) -> new JLabel(starOutlineIcon));
        emailTable.getColumnModel().getColumn(2).setPreferredWidth(180); // Sender
        emailTable.getColumnModel().getColumn(3).setPreferredWidth(400); // Subject
        emailTable.getColumnModel().getColumn(4).setPreferredWidth(80);  // Time

        // Custom header
        JTableHeader header = emailTable.getTableHeader();
        header.setPreferredSize(new Dimension(header.getWidth(), 40));

        // Custom cell renderer
        emailTable.setDefaultRenderer(String.class, new EmailCellRenderer());
        emailTable.setDefaultRenderer(ImageIcon.class, new StarCellRenderer());

        // Mouse listener for star column clicks
        emailTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showContextMenu(e);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showContextMenu(e);
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                int row = emailTable.rowAtPoint(e.getPoint());
                int col = emailTable.columnAtPoint(e.getPoint());

                if (col == 1 && row >= 0 && row < emails.size()) {
                    toggleStarred(row);
                }
            }
        });

        // Selection listener
        emailTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && emailTable.getSelectedRow() >= 0) {
                showEmailDetail(emailTable.getSelectedRow());
            }
        });

        JScrollPane scrollPane = new JScrollPane(emailTable);
        scrollPane.setBorder(null);

        return scrollPane;
    }

    /**
     * Toggle starred status
     */
    private void toggleStarred(int row) {
        if (row < 0 || row >= emails.size()) return;

        Email email = emails.get(row);

        // Toggle flagged status
        email.toggleFlag("Flagged");

        // Update table cell
        boolean isStarred = email.hasFlag("Flagged");
        tableModel.setValueAt(isStarred ? starFilledIcon : starOutlineIcon, row, 1);

        // Sync with the IMAP server if connected
        if (controller != null && controller.isConnected()) {
            controller.updateEmailFlags(email);
        }
    }

    /**
     * Email detail panel (right side)
     */
    private JPanel createDetailPanel() {
        // THAY ĐỔI: BorderLayout để body có thể scroll độc lập
        JPanel detailPanel = new JPanel(new BorderLayout());

        // Panel chứa header info (top)
        JPanel headerPanel = new JPanel(new MigLayout("fillx, insets 20", "[grow]", "[]10[]10[]10"));

        // Subject (large)
        subjectLabel = new JLabel("Select an email to view");
        subjectLabel.putClientProperty(FlatClientProperties.STYLE, "font:bold +2");
        headerPanel.add(subjectLabel, "growx, wrap");

        // From (with avatar)
        JPanel fromPanel = new JPanel(new MigLayout("insets 0", "[]10[]", "[]"));

        JLabel avatarLabel = new JLabel("");
        fromPanel.add(avatarLabel);

        fromLabel = new JLabel("sender@example.com");
        fromPanel.add(fromLabel);

        headerPanel.add(fromPanel, "growx, wrap");

        // Date
        dateLabel = new JLabel("");
        dateLabel.setForeground(Color.GRAY);
        headerPanel.add(dateLabel, "growx, wrap");

        // Attachments panel - hidemode 3 để không chiếm space khi ẩn
        attachmentsPanel = new JPanel(new MigLayout("insets 0,fillx", "[grow]", "[]"));
        attachmentsPanel.setVisible(false);
        headerPanel.add(attachmentsPanel, "growx, wrap, hidemode 3");

        detailPanel.add(headerPanel, BorderLayout.NORTH);

        // Body - Panel riêng với scroll (center)
        bodyTextArea = new JTextArea();
        bodyTextArea.setLineWrap(true);
        bodyTextArea.setWrapStyleWord(true);
        bodyTextArea.setEditable(false);
        bodyTextArea.setMargin(new Insets(10, 20, 10, 20));

        JScrollPane bodyScroll = new JScrollPane(bodyTextArea);
        bodyScroll.setBorder(null);
        bodyScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        bodyScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        detailPanel.add(bodyScroll, BorderLayout.CENTER);

        return detailPanel;
    }

    /**
     * Populate attachments panel
     */
    private void populateAttachmentsPanel(List<File> attachments) {
        attachmentsPanel.removeAll();
        attachmentsPanel.setVisible(false);

        if (!attachments.isEmpty()) {
            attachmentsPanel.setVisible(true);

            JLabel attachLabel = new JLabel("Attachments (" + attachments.size() + "):");
            attachLabel.putClientProperty(FlatClientProperties.STYLE, "font:bold");
            attachmentsPanel.add(attachLabel, "wrap, gaptop 10");

            for (File file : attachments) {
                JPanel filePanel = new JPanel(new MigLayout("insets 5", "[]10[]", "[]"));
                filePanel.putClientProperty(FlatClientProperties.STYLE,
                        "arc:10;" +
                                "background:lighten(@background,5%)");

                JLabel iconLabel = new JLabel(getFileIcon(file));
                filePanel.add(iconLabel);

                JButton fileBtn = new JButton(file.getName());
                fileBtn.putClientProperty(FlatClientProperties.BUTTON_TYPE,
                        FlatClientProperties.BUTTON_TYPE_BORDERLESS);
                fileBtn.setToolTipText("Click to open: " + file.getName());
                fileBtn.addActionListener(e -> openAttachment(file));
                filePanel.add(fileBtn);

                String fileSize = formatFileSize(file.length());
                JLabel sizeLabel = new JLabel(fileSize);
                sizeLabel.setForeground(Color.GRAY);
                filePanel.add(sizeLabel);

                attachmentsPanel.add(filePanel, "growx, wrap, gaptop 5");
            }
        }

        attachmentsPanel.revalidate();
        attachmentsPanel.repaint();
    }

    /**
     * Show email detail
     */
    private void showEmailDetail(int row) {
        if (row < 0 || row >= emails.size()) return;

        Email email = emails.get(row);

        subjectLabel.setText(email.getSubject() != null ? email.getSubject() : "(No Subject)");
        fromLabel.setText(email.getFrom() != null ? email.getFrom() : "Unknown");

        SimpleDateFormat sdf = new SimpleDateFormat("EEE, MMM dd, yyyy 'at' hh:mm a");
        dateLabel.setText(email.getDate() != null ? sdf.format(email.getDate()) : "");

        populateAttachmentsPanel(email.getAttachments());

        // Load body
        if (email.getBody() == null || email.getBody().isEmpty()) {
            bodyTextArea.setText("Loading email content...");
            if (controller != null && controller.isConnected()) {
                controller.loadEmailBody(email, "INBOX");
            }
        } else {
            bodyTextArea.setText(email.getBody());
            bodyTextArea.setCaretPosition(0);
        }
    }

    /**
     * Update email body sau khi load xong
     */
    public void updateEmailBody(Email email) {
        int selectedRow = emailTable.getSelectedRow();
        if (selectedRow >= 0 && selectedRow < emails.size()) {
            Email selectedEmail = emails.get(selectedRow);
            if (selectedEmail.getMessageNumber() == email.getMessageNumber()) {
                // Update body text
                bodyTextArea.setText(email.getBody() != null ? email.getBody() : "(No content)");
                bodyTextArea.setCaretPosition(0);

                populateAttachmentsPanel(email.getAttachments());
            }
        }
    }

    /**
     * Format file size (bytes -> KB/MB)
     */
    private String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        }
    }

    /**
     * Load emails (call from controller)
     */
    public void loadEmails(List<Email> emailList) {
        this.emails = emailList;
        refreshTable();

        // Hiển thị thông báo nếu không có email
        if (emails.isEmpty()) {
            subjectLabel.setText("No emails found");
            fromLabel.setText("");
            dateLabel.setText("");
            bodyTextArea.setText("Your inbox is empty or no emails match the current filter.");
        }
    }

    /**
     * Refresh table
     */
    private void refreshTable() {
        // Sort emails by date DESC (newest)
        emails.sort(Comparator.comparing(Email::getDate).reversed());

        tableModel.setRowCount(0);

        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd");

        for (Email email : emails) {
            boolean isRead = email.hasFlag("Seen");
            boolean isStarred = email.hasFlag("Flagged");

            String sender = email.getFrom() != null ? extractName(email.getFrom()) : "Unknown";
            String subject = email.getSubject() != null ? email.getSubject() : "(No Subject)";
            String time = email.getDate() != null ? sdf.format(email.getDate()) : "";

            // Make unread emails bold
            if (!isRead) {
                sender = "<html><b>" + sender + "</b></html>";
                subject = "<html><b>" + subject + "</b></html>";
            }

            tableModel.addRow(new Object[]{
                    false,              // Checkbox
                    isStarred ? starFilledIcon : starOutlineIcon, // Star icon
                    sender,
                    subject,
                    time
            });
        }

        // Auto-scroll to top sau refresh (để thư gần nhất visible ngay)
        if (!emails.isEmpty() && emailTable != null) {
            emailTable.scrollRectToVisible(emailTable.getCellRect(0, 0, true));
        }
    }

    /**
     * Extract name from email address
     * "John Doe <john@example.com>" -> "John Doe"
     */
    private String extractName(String email) {
        if (email == null) return "Unknown";
        if (email.contains("<")) {
            return email.substring(0, email.indexOf("<")).trim();
        }
        return email.split("@")[0];
    }

    /**
     * Custom cell renderer for email rows
     */
    private static class EmailCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int column) {

            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            // Star column
            if (column == 1) {
                setHorizontalAlignment(CENTER);
            } else {
                setHorizontalAlignment(LEFT);
            }

            return c;
        }
    }

    /**
     * Custom renderer for star column
     */
    private static class StarCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int column) {

            JLabel label = new JLabel();
            label.setHorizontalAlignment(CENTER);

            if (value instanceof ImageIcon) {
                label.setIcon((ImageIcon) value);
            }

            // Set background for selection
            if (isSelected) {
                label.setBackground(table.getSelectionBackground());
                label.setOpaque(true);
            } else {
                label.setBackground(table.getBackground());
                label.setOpaque(true);
            }

            // Change cursor to hand when hovering
            label.setCursor(new Cursor(Cursor.HAND_CURSOR));

            return label;
        }
    }

    private void openAttachment(File file) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();
                if (file.exists()) {
                    desktop.open(file);
                } else {
                    Notifications.getInstance().show(Notifications.Type.ERROR, "File not found: " + file.getName());
                }
            } else {
                Notifications.getInstance().show(Notifications.Type.ERROR, "Desktop not supported on this platform");
            }
        } catch (Exception e) {
            Notifications.getInstance().show(Notifications.Type.ERROR, "Cannot open file: " + e.getMessage());
        }
    }
}