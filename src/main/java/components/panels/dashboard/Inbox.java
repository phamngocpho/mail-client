package components.panels.dashboard;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import components.dialogs.ImapLoginDialog;
import controllers.ImapController;
import net.miginfocom.swing.MigLayout;
import models.Email;
import raven.toast.Notifications;
import utils.Constants;

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

/**
 * This class represents the Inbox panel, which is a graphical user interface (GUI)
 * component used to display and interact with a user's email inbox.
 * It extends from JPanel to integrate with the Swing framework.
 * The Inbox includes an email list, email details, and controls for managing emails.
 * <p>
 * Fields:
 * - emailTable: Represents the table displaying the list of emails.
 * - tableModel: Data model for the email table.
 * - fromLabel: Label to display the sender's information.
 * - subjectLabel: Label to display the email subject.
 * - dateLabel: Label to display the email date.
 * - bodyTextArea: Text area to display the email body.
 * - attachmentsPanel: Panel for displaying email attachments.
 * - emails: List of emails loaded into the inbox.
 * - starFilledIcon: Icon used to indicate a starred email.
 * - starOutlineIcon: Icon used to indicate an unstarred email.
 * - selectOutlineIcon: Icon used to indicate a selectable item.
 * - iconSize: Size for icons used in the Inbox.
 * - controller: Controller handling Inbox-related actions.
 * <p>
 * Methods:
 * - Inbox(): Constructs the Inbox panel and initializes its components.
 * - loadIcons(): Loads icons used throughout the Inbox panel.
 * - init(): Initializes the Inbox's layout and components.
 * - createTopToolbar(): Creates the top toolbar of the inbox.
 * - showContextMenu(MouseEvent e): Displays a context menu for the email table upon a right-click event.
 * - refreshEmailRow(Email email): Refreshes a specific email row in the table.
 * - showLoginDialog(): Displays a login dialog to connect to the email server.
 * - showLoading(): Displays a loading state in the UI when performing actions.
 * - createEmailListPanel(): Creates the left panel that lists email summaries.
 * - toggleStarred(int row): Toggles the starred status of a specific email.
 * - createDetailPanel(): Creates the right panel for showing email details.
 * - populateAttachmentsPanel(List<File> attachments): Populates the panel with email attachments.
 * - getFileBtn(File file): Creates and returns a button for an attachment file.
 * - showEmailDetail(int row): Displays the details of a selected email.
 * - updateEmailBody(Email email): Updates the email body after it has been loaded.
 * - formatFileSize(long bytes): Formats file sizes from bytes to a user-friendly string (e.g., KB, MB).
 * - loadEmails(List<Email> emailList): Loads a list of emails into the table.
 * - refreshTable(): Refreshes the entire email table.
 * - extractName(String email): Extracts the name from an email address.
 * - openAttachment(File file): Opens a given attachment file using the default application.
 */
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
    private final int iconSize = Constants.defaultIconSize - 5;

    // Controller
    private final ImapController controller;
    private static ImapController sharedController; // Controller dùng chung
    private final String folderName;
    private final String filterMode;

    public Inbox(String folderName) {
        this(folderName, "ALL");
    }

    public Inbox(String folderName, String filterMode) {
        this.folderName = folderName;
        this.filterMode = filterMode;
        emails = new ArrayList<>();
        loadIcons();
        init();

        if (sharedController == null) {
            sharedController = new ImapController(null, folderName); // null vì sẽ register sau
            this.controller = sharedController;

            if ("INBOX".equals(folderName)) {
                SwingUtilities.invokeLater(this::showLoginDialog);
            }
        } else {
            this.controller = sharedController;
            // Load ngay nếu đã connected
            if (controller.isConnected()) {
                controller.loadFolder(folderName, Constants.EMAILS_PER_PAGE);
            }
        }

        // Register inbox này với controller
        controller.registerInbox(this);

        // Load emails nếu đã connected và không phải INBOX (INBOX sẽ load sau khi login)
        if (controller.isConnected() && !"INBOX".equals(folderName)) {
            controller.loadFolder(folderName, Constants.EMAILS_PER_PAGE);
        }
    }

    public ImapController getController() {
        return controller;
    }

    private void loadIcons() {
        starFilledIcon = new FlatSVGIcon("icons/menu/starred.svg", iconSize, iconSize);
        starOutlineIcon = new FlatSVGIcon("icons/inbox/star_outline.svg", iconSize, iconSize);
        selectOutlineIcon = new FlatSVGIcon("icons/inbox/select.svg", iconSize, iconSize);
    }

    public String getFolderName() {
        return folderName;
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
        splitPane.setDividerLocation((int) (Constants.dimension.getWidth() * 0.57));
        splitPane.putClientProperty(FlatClientProperties.STYLE,
                "gripDotCount: 0;"
                        + "dividerSize: 5");

        add(splitPane, "grow");
    }

    /**
     * Creates and returns the top toolbar panel for the inbox interface.
     * The toolbar includes various components such as a "Connect" button,
     * a "Refresh" button, category tabs for email types (e.g., Primary, Social, Promotions),
     * and a search box.
     * <p>
     * The "Connect" button allows users to authenticate and connect to their email server.
     * The "Refresh" button reloads content if the user is connected.
     * The category tabs display groups of emails, and the search box enables searching within emails.
     *
     * @return a JPanel instance representing the top toolbar with configured components
     */
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
        refreshButton.putClientProperty(FlatClientProperties.STYLE, "arc: 50; borderColor: null; focusColor: null; background: null");
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

    /**
     * Displays a context menu for the email table upon a right-click action.
     * <p>
     * This method identifies the email row based on the mouse event, selects the row,
     * and creates a context menu with actions like marking the email as read/unread
     * and deleting the email. The menu is displayed at the location of the mouse click.
     *
     * @param e the MouseEvent that triggered the context menu, providing the
     *          location of the click and other contextual information
     */
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

        // Load emails cho tất cả các tab sau khi connect
        if (connected) {
            controller.setCurrentFolder(folderName);
            controller.loadFolder(folderName, Constants.EMAILS_PER_PAGE);
        }

        // Nếu user cancel, hiển thị thông báo
        if (!connected) {
            subjectLabel.setText("<html>Not Connected</html>");
            fromLabel.setText("");
            dateLabel.setText("");
            bodyTextArea.setText("Click 'Connect' button to connect to your email server and load emails.");
        }
    }

    /**
     * Show loading state
     */
    public void showLoading() {
        subjectLabel.setText("<html>Loading emails...</html>");
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
        // BorderLayout để body có thể scroll độc lập
        JPanel detailPanel = new JPanel(new BorderLayout());

        // Panel chứa header info (top)
        JPanel headerPanel = new JPanel(new MigLayout("fillx, insets 20", "[grow]", "[]10[]10[]10"));

        // Subject (large)
        subjectLabel = new JLabel("<html>Select an email to view</html>");
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
                JPanel filePanel = new JPanel(new MigLayout("insets 5", "[]10[]10[]", "[]"));
                filePanel.putClientProperty(FlatClientProperties.STYLE,
                        "arc:10;" +
                                "background:lighten(@background,5%)");

                JLabel iconLabel = new JLabel(getFileIcon(file));
                filePanel.add(iconLabel, "aligny center");

                JButton fileBtn = getFileBtn(file);
                filePanel.add(fileBtn, "aligny center");

                String fileSize = formatFileSize(file.length());
                JLabel sizeLabel = new JLabel(fileSize);
                sizeLabel.setForeground(Color.GRAY);
                filePanel.add(sizeLabel, "aligny center");

                attachmentsPanel.add(filePanel, "growx, wrap, gaptop 5");
            }
        }

        attachmentsPanel.revalidate();
        attachmentsPanel.repaint();
    }

    /**
     * Creates a JButton for the provided file, displaying its name (truncated if too long)
     * and setting up an action to open the file on click.
     *
     * @param file the file for which the button is created
     * @return a JButton configured to display the file name and open the file
     */
    private JButton getFileBtn(File file) {
        String fileName = file.getName();
        String displayName = fileName.length() > 40
                ? fileName.substring(0, 37) + "..."
                : fileName;
        JButton fileBtn = new JButton("<html>" + displayName + "</html>");
        fileBtn.putClientProperty(FlatClientProperties.BUTTON_TYPE,
                FlatClientProperties.BUTTON_TYPE_BORDERLESS);
        fileBtn.setToolTipText("Click to open: " + fileName);
        fileBtn.addActionListener(e -> openAttachment(file));
        fileBtn.putClientProperty(FlatClientProperties.STYLE, "focusWidth: 0;");
        return fileBtn;
    }

    /**
     * Show email detail
     */
    private void showEmailDetail(int row) {
        if (row < 0 || row >= emails.size()) return;

        Email email = emails.get(row);

        String subject = email.getSubject() != null ? email.getSubject() : "(No Subject)";
        subjectLabel.setText("<html>" + subject + "</html>");
        fromLabel.setText(email.getFrom() != null ? email.getFrom() : "Unknown");

        SimpleDateFormat sdf = new SimpleDateFormat("EEE, MMM dd, yyyy 'at' hh:mm a");
        dateLabel.setText(email.getDate() != null ? sdf.format(email.getDate()) : "");

        populateAttachmentsPanel(email.getAttachments());

        // Load body
        if (email.getBody() == null || email.getBody().isEmpty()) {
            bodyTextArea.setText("Loading email content...");
            if (controller != null && controller.isConnected()) {
                controller.loadEmailBody(email, folderName);
            }
        } else {
            bodyTextArea.setText(email.getBody());
            bodyTextArea.setCaretPosition(0);
        }

        // Tự động đánh dấu là đã đọc
        if (!email.hasFlag("Seen")) {
            // Đánh dấu đã đọc sau 1 giây
            Timer timer = getTimer(email);
            timer.start();
        }
    }

    private Timer getTimer(Email email) {
        Timer timer = new Timer(1000, e -> {
            if (controller != null && controller.isConnected()) {
                controller.markAsRead(email, true);

                // Refresh nếu đang filter UNREAD
                if ("UNREAD".equals(filterMode)) {
                    Timer refreshTimer = new Timer(500, ev -> controller.refresh());
                    refreshTimer.setRepeats(false);
                    refreshTimer.start();
                }
            }
        });
        timer.setRepeats(false);
        return timer;
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
        // Lọc emails theo filterMode
        if ("STARRED".equals(filterMode)) {
            this.emails = emailList.stream()
                    .filter(email -> email.hasFlag("Flagged"))
                    .collect(java.util.stream.Collectors.toList());
        } else if ("UNREAD".equals(filterMode)) {
            this.emails = emailList.stream()
                    .filter(email -> !email.hasFlag("Seen"))
                    .collect(java.util.stream.Collectors.toList());
        } else {
            this.emails = emailList;
        }

        refreshTable();

        if (emails.isEmpty()) {
            String message = "STARRED".equals(filterMode)
                    ? "No starred emails found"
                    : "UNREAD".equals(filterMode)
                    ? "No unread emails found"
                    : "Your inbox is empty or no emails match the current filter.";

            subjectLabel.setText("<html>No emails found</html>");
            fromLabel.setText("");
            dateLabel.setText("");
            bodyTextArea.setText(message);
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
    private class EmailCellRenderer extends DefaultTableCellRenderer {
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

            // Highlight unread emails với background sáng hơn
            if (row >= 0 && row < emails.size()) {
                Email email = emails.get(row);
                if (!email.hasFlag("Seen") && !isSelected) {
                    // Unread email - background sáng hơn
                    c.setBackground(UIManager.getColor("Table.background"));
                    Color bg = c.getBackground();
                    // Làm sáng thêm 5%
                    c.setBackground(new Color(
                            Math.min(255, bg.getRed() + 13),
                            Math.min(255, bg.getGreen() + 13),
                            Math.min(255, bg.getBlue() + 13)
                    ));
                } else if (!isSelected) {
                    c.setBackground(table.getBackground());
                }
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

    /**
     * Attempts to open the given file using the default application associated with its type.
     * If the desktop environment does not support this operation or the file cannot be opened, an error notification is displayed.
     *
     * @param file the file to be opened; must not be null
     */
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