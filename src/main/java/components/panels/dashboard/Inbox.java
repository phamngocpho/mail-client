package components.panels.dashboard;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import components.dialogs.ImapLoginDialog;
import components.panels.MainPanel;
import controllers.ImapController;
import net.miginfocom.swing.MigLayout;
import models.Email;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import protocols.imap.ImapParser;
import raven.toast.Notifications;
import utils.Constants;
import utils.EmailComposerHelper;
import utils.EmailUtils;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
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
 * - loadEmails(List<Email> emailList): Loads a list of emails into the table.
 * - refreshTable(): Refreshes the entire email table.
 * - openAttachment(File file): Opens a given attachment file using the default application.
 * <p>
 * Note: String formatting and email parsing methods have been moved to EmailUtils for reusability.
 */
public class Inbox extends JPanel {
    private static final Logger logger = LoggerFactory.getLogger(Inbox.class);
    
    private JTable emailTable;
    private DefaultTableModel tableModel;
    private JLabel fromLabel, subjectLabel, dateLabel;
    private JTextArea bodyTextArea;
    private JPanel attachmentsPanel;
    private Loading loadingPanel; // Loading overlay panel
    private List<Email> emails;
    private List<Email> allEmails; // Danh sách email gốc (trước khi search)
    private Email currentViewingEmail; // Email đang được xem trong detail view
    private String currentSearchQuery = ""; // Query tìm kiếm hiện tại

    // Star icons
    private FlatSVGIcon starFilledIcon;
    private FlatSVGIcon starOutlineIcon;
    private FlatSVGIcon selectOutlineIcon;
    private final int iconSize = Constants.defaultIconSize - 5;

    // View switching
    private JPanel contentPanel;
    private CardLayout cardLayout;
    private static final String LIST_VIEW = "LIST";
    private static final String DETAIL_VIEW = "DETAIL";

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
        allEmails = new ArrayList<>();
        loadIcons();
        init();

        if (sharedController == null) {
            sharedController = new ImapController(null, folderName); // null vì sẽ register sau
            this.controller = sharedController;

            if ("INBOX".equals(folderName)) {
                // Kiểm tra xem có credentials trong config không
                SwingUtilities.invokeLater(this::tryAutoConnect);
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
            // Show loading skeleton trước khi load
            showLoadingPanel("Loading " + folderName + "...", "Fetching messages");
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

        // CardLayout để chuyển đổi giữa list view và detail view
        cardLayout = new CardLayout();
        contentPanel = new JPanel(cardLayout);

        // Add list view
        contentPanel.add(createEmailListPanel(), LIST_VIEW);

        // Add detail view
        contentPanel.add(createDetailPanel(), DETAIL_VIEW);

        // Mặc định hiển thị list view
        cardLayout.show(contentPanel, LIST_VIEW);

        add(contentPanel, "grow");
        
        // Create and add loading panel with scroll support
        loadingPanel = new Loading();
        loadingPanel.setVisible(false);
        JScrollPane loadingScrollPane = new JScrollPane(loadingPanel);
        loadingScrollPane.setBorder(null);
        loadingScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        contentPanel.add(loadingScrollPane, "LOADING");
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
        JTextField searchField = new JTextField();
        searchField.putClientProperty(FlatClientProperties.STYLE, "arc: 50;");
        searchField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Search in emails (Press Enter)...");
        searchField.setPreferredSize(new Dimension(300, 40));
        
        JButton searchButton = new JButton(new FlatSVGIcon("icons/inbox/search.svg", iconSize - 1, iconSize - 1));
        searchButton.setMargin(new Insets(4, 4, 4, 4));
        searchButton.putClientProperty(FlatClientProperties.STYLE, "arc: 50; borderColor: null; focusColor: null");
        searchButton.setToolTipText("Search");
        
        searchField.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_COMPONENT, searchButton);
        
        // Action khi nhấn Enter trong search field
        searchField.addActionListener(e -> performSearch(searchField.getText()));
        
        // Action khi click search button
        searchButton.addActionListener(e -> performSearch(searchField.getText()));
        
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
     * Perform search in emails
     * Delegates to ImapController.performSearch() which handles server/local logic
     */
    private void performSearch(String query) {
        currentSearchQuery = query == null ? "" : query.trim();

        // Show loading skeleton khi search
        if (!currentSearchQuery.isEmpty()) {
            if (controller != null && controller.isConnected()) {
                showLoadingPanel("Searching entire mailbox...", 
                                "Query: \"" + currentSearchQuery + "\" • Scope: Entire folder");
            } else {
                showLoadingPanel("Searching loaded emails...", 
                                "Query: \"" + currentSearchQuery + "\" • Scope: Recently loaded only");
            }
        }
        
        // Delegate to controller
        if (controller != null) {
            controller.performSearch(currentSearchQuery, folderName, results -> {
                // Update emails and refresh UI
                allEmails = new ArrayList<>(results);
                emails = new ArrayList<>(results);
                refreshTable();
                
                // Hide loading và show list view
                hideLoadingPanel();

                // Nếu search và không có kết quả, hiển thị message trong detail view
                if (!currentSearchQuery.isEmpty() && emails.isEmpty()) {
                    subjectLabel.setText("<html>No results found</html>");
                    fromLabel.setText("");
                    dateLabel.setText("");
                    bodyTextArea.setText("No emails match your search query: \"" + currentSearchQuery + "\"\n\n" +
                                       (controller.isConnected() ? 
                                           "Search was performed on ALL emails (not limited to recent 50)." :
                                           "Note: Not connected. Only searched in recently loaded emails."));
                    cardLayout.show(contentPanel, DETAIL_VIEW);
                } else if (!currentSearchQuery.isEmpty()) {
                    // Có kết quả search - log thông tin
                    logger.info("Found {} email(s) matching: \"{}\"", emails.size(), currentSearchQuery);
                } else if (emails.isEmpty()) {
                    // Clear search nhưng không có email nào
                    subjectLabel.setText("<html>No emails found</html>");
                    fromLabel.setText("");
                    dateLabel.setText("");
                    bodyTextArea.setText("Your inbox is empty or no emails match the current filter.");
                    cardLayout.show(contentPanel, DETAIL_VIEW);
                }

                logger.debug("Search '{}' completed with {} results", currentSearchQuery, emails.size());
            });
        }
    }

    /**
     * Refresh a single email row
     */
    public void refreshEmailRow(Email email) {
        int index = emails.indexOf(email);
        if (index >= 0) {
            tableModel.setValueAt(
                    email.hasFlag("Seen") ? EmailUtils.extractName(email.getFrom()) :
                            "<html><b>" + EmailUtils.extractName(email.getFrom()) + "</b></html>",
                    index, 2
            );
            tableModel.fireTableRowsUpdated(index, index);
        }
    }

    /**
     * Try to auto-connect using saved credentials from ConfigUtils
     * Delegates to ImapController.tryAutoConnect()
     */
    private void tryAutoConnect() {
        // Show loading panel immediately with detailed message
        showLoadingPanel("Checking saved credentials...", "Reading configuration file");
        
        controller.tryAutoConnect(
            folderName,
            // onLoading - connecting to server
            () -> updateLoadingPanel("Connecting to mail server...", "Authenticating with IMAP server"),
            // onSuccess - connection successful
            () -> {
                updateLoadingPanel("Loading emails...", "Fetching messages from server");
                // Hide loading panel after a short delay (to show the final message)
                Timer hideTimer = new Timer(800, e -> {
                    hideLoadingPanel();
                    Notifications.getInstance().show(Notifications.Type.SUCCESS, "Connected successfully!");
                });
                hideTimer.setRepeats(false);
                hideTimer.start();
            },
            // onError - connection failed
            (errorMsg) -> {
                hideLoadingPanel();
                if ("NO_CREDENTIALS".equals(errorMsg)) {
                    logger.info("No saved credentials, showing login dialog");
                    showLoginDialog();
                } else {
                    logger.error("Auto-connect failed: {}", errorMsg);
                    Notifications.getInstance().show(Notifications.Type.ERROR, 
                            "Auto-connect failed: " + errorMsg + ". Please login again.");
                    
                    subjectLabel.setText("<html>Connection Failed</html>");
                    fromLabel.setText("");
                    dateLabel.setText("");
                    bodyTextArea.setText("Failed to connect with saved credentials.\n\n" +
                            "Error: " + errorMsg + "\n\n" +
                            "Please click 'Connect' button to enter your credentials again.");
                    
                    SwingUtilities.invokeLater(this::showLoginDialog);
                }
            }
        );
    }
    
    /**
     * Show loading panel with message
     */
    private void showLoadingPanel(String message) {
        showLoadingPanel(message, "Please wait...");
    }
    
    /**
     * Show loading panel with custom messages
     */
    private void showLoadingPanel(String message, String subMessage) {
        if (loadingPanel != null) {
            loadingPanel.setMessages(message, subMessage);
            
            SwingUtilities.invokeLater(() -> {
                if (contentPanel != null && cardLayout != null) {
                    cardLayout.show(contentPanel, "LOADING");
                }
                loadingPanel.setVisible(true);
            });
        }
    }
    
    /**
     * Update loading panel messages
     */
    private void updateLoadingPanel(String message, String subMessage) {
        if (loadingPanel != null && loadingPanel.isVisible()) {
            if (message != null) {
                loadingPanel.setMessage(message);
            }
            if (subMessage != null) {
                loadingPanel.setSubMessage(subMessage);
            }
        }
    }
    
    /**
     * Hide loading panel and return to list view
     * Loading component tự động áp dụng minimum display time để tránh nhấp nháy
     */
    private void hideLoadingPanel() {
        if (loadingPanel != null) {
            // Loading component tự quản lý minimum display time
            loadingPanel.hideWithMinimumDuration(() -> SwingUtilities.invokeLater(() -> {
                if (contentPanel != null && cardLayout != null) {
                    cardLayout.show(contentPanel, LIST_VIEW);
                }
            }));
        }
    }
    
    /**
     * Public method để reload folder với loading skeleton
     * Được gọi từ MainMenu khi user click vào menu item
     */
    public void reloadFolder() {
        if (controller != null && controller.isConnected()) {
            // Show loading skeleton
            showLoadingPanel("Loading " + folderName + "...", "Fetching messages");
            // Update current folder và load
            controller.setCurrentFolder(folderName);
            controller.loadFolder(folderName, Constants.EMAILS_PER_PAGE);
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
        TableColumnModel columnModel = emailTable.getColumnModel();
        TableColumn checkboxColumn = columnModel.getColumn(0);
        checkboxColumn.setMaxWidth(40);  // Checkbox
        checkboxColumn.setHeaderRenderer((jTable, o, b, b1, i, i1) -> new JLabel(selectOutlineIcon));

        TableColumn starColumn = columnModel.getColumn(1);
        starColumn.setMaxWidth(40);  // Star
        starColumn.setHeaderRenderer((jTable, o, b, b1, i, i1) -> new JLabel(starOutlineIcon));

        columnModel.getColumn(2).setPreferredWidth(180); // Sender
        columnModel.getColumn(3).setPreferredWidth(400); // Subject
        columnModel.getColumn(4).setPreferredWidth(80);  // Time

        // Custom header
        JTableHeader header = emailTable.getTableHeader();
        header.setPreferredSize(new Dimension(header.getWidth(), 40));

        // Custom cell renderer
        emailTable.setDefaultRenderer(String.class, new EmailCellRenderer());
        emailTable.setDefaultRenderer(ImageIcon.class, new StarCellRenderer());

        // Mouse listener for star column clicks and row selection
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

                // Toggle star khi click vào star column
                if (col == 1 && row >= 0 && row < emails.size()) {
                    toggleStarred(row);
                } 
                // Chỉ chuyển sang detail view khi left-click
                else if (SwingUtilities.isLeftMouseButton(e) && row >= 0 && row < emails.size()) {
                    emailTable.setRowSelectionInterval(row, row);
                    showEmailDetail(row);
                    cardLayout.show(contentPanel, DETAIL_VIEW);
                }
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
     * Email detail panel with back button
     */
    private JPanel createDetailPanel() {
        // BorderLayout để body có thể scroll độc lập
        JPanel detailPanel = new JPanel(new BorderLayout());

        // Back button toolbar (top)
        JPanel backToolbar = getBackToolbar();

        detailPanel.add(backToolbar, BorderLayout.NORTH);

        // Panel chứa header info và body
        JPanel contentWrapper = new JPanel(new MigLayout("fill, insets 0", "[grow]", "[][grow]"));

        // Panel chứa header info (top of content)
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

        contentWrapper.add(headerPanel, "growx, wrap, gapbottom 0");

        // Body - Panel riêng với scroll (center)
        bodyTextArea = new JTextArea() {
            @Override
            public Dimension getPreferredScrollableViewportSize() {
                // Override để không bị giới hạn bởi default columns/rows
                return getPreferredSize();
            }
            
            @Override
            public boolean getScrollableTracksViewportWidth() {
                // Track viewport width để fill hết chiều ngang
                return true;
            }
        };
        bodyTextArea.setLineWrap(true);
        bodyTextArea.setWrapStyleWord(true);
        bodyTextArea.setEditable(false);
        bodyTextArea.setMargin(new Insets(10, 20, 10, 20));

        JScrollPane bodyScroll = new JScrollPane(bodyTextArea);
        bodyScroll.setBorder(null);
        bodyScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        bodyScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        contentWrapper.add(bodyScroll, "grow, push, wrap");

        // Attachments panel - hidemode 3 để không chiếm space khi ẩn
        // Sử dụng wrap để files tự động xuống dòng khi hết chỗ
        attachmentsPanel = new JPanel(new MigLayout("insets 20, fillx, wrap 3", "[30%][30%][30%]", "[]"));
        attachmentsPanel.setVisible(false);
        contentWrapper.add(attachmentsPanel, "growx, hidemode 3");

        detailPanel.add(contentWrapper, BorderLayout.CENTER);

        return detailPanel;
    }

    private JPanel getBackToolbar() {
        JPanel backToolbar = new JPanel(new MigLayout("fillx, insets 10", "[]push[][]", "[]"));
        JButton backButton = new JButton(new FlatSVGIcon("icons/inbox/arrow_back.svg", iconSize, iconSize));
        backButton.putClientProperty(FlatClientProperties.STYLE, "arc: 50; borderColor: null; focusColor: null");
        backButton.setToolTipText("Back to inbox");
        backButton.addActionListener(e -> goBackToList());
        backToolbar.add(backButton);

        // Reply button
        JButton replyButton = new JButton(new FlatSVGIcon("icons/compose/reply.svg", iconSize, iconSize));
        replyButton.putClientProperty(FlatClientProperties.STYLE, "arc: 50; borderColor: null; focusColor: null");
        replyButton.setToolTipText("Reply");
        replyButton.addActionListener(e -> handleReply());
        backToolbar.add(replyButton, "gap right 5");

        // Forward button
        JButton forwardButton = new JButton(new FlatSVGIcon("icons/compose/forward.svg", iconSize, iconSize));
        forwardButton.putClientProperty(FlatClientProperties.STYLE, "arc: 50; borderColor: null; focusColor: null");
        forwardButton.setToolTipText("Forward");
        forwardButton.addActionListener(e -> handleForward());
        backToolbar.add(forwardButton);
        return backToolbar;
    }

    /**
     * Go back to list view
     */
    private void goBackToList() {
        cardLayout.show(contentPanel, LIST_VIEW);
        emailTable.clearSelection();
        currentViewingEmail = null;
    }

    /**
     * Handle reply to current email
     * Uses EmailComposerHelper to prepare the reply draft
     */
    private void handleReply() {
        if (currentViewingEmail == null) {
            Notifications.getInstance().show(Notifications.Type.WARNING, "No email selected to reply");
            return;
        }

        // Use helper to prepare reply draft
        EmailComposerHelper.EmailDraft draft = EmailComposerHelper.prepareReply(currentViewingEmail);
        
        // Create compose panel and populate with draft data
        Compose composePanel = new Compose();
        composePanel.setTo(draft.getTo());
        composePanel.setSubject(draft.getSubject());
        composePanel.setBody(draft.getBody());
        
        // Show compose panel
        showComposePanel(composePanel);
    }

    /**
     * Handle forward current email
     * Uses EmailComposerHelper to prepare the forward draft
     */
    private void handleForward() {
        if (currentViewingEmail == null) {
            Notifications.getInstance().show(Notifications.Type.WARNING, "No email selected to forward");
            return;
        }

        // Use helper to prepare forward draft
        EmailComposerHelper.EmailDraft draft = EmailComposerHelper.prepareForward(currentViewingEmail);
        
        // Create compose panel and populate with draft data
        Compose composePanel = new Compose();
        composePanel.setTo(draft.getTo());
        composePanel.setSubject(draft.getSubject());
        composePanel.setBody(draft.getBody());
        
        // Copy attachments if any
        if (draft.getAttachments() != null && !draft.getAttachments().isEmpty()) {
            for (File attachment : draft.getAttachments()) {
                composePanel.addAttachment(attachment);
            }
        }
        
        // Show compose panel
        showComposePanel(composePanel);
    }

    /**
     * Show compose panel in content area (keep menu visible)
     */
    private void showComposePanel(Compose composePanel) {
        // Tìm MainPanel ancestor
        MainPanel mainPanel = findMainPanel();
        if (mainPanel != null) {
            mainPanel.setContent(composePanel);
        } else {
            logger.warn("Cannot find MainPanel ancestor");
            Notifications.getInstance().show(Notifications.Type.ERROR, "Cannot open compose window");
        }
    }

    /**
     * Find MainPanel ancestor in component hierarchy
     */
    private MainPanel findMainPanel() {
        Container parent = getParent();
        while (parent != null) {
            if (parent instanceof MainPanel) {
                return (MainPanel) parent;
            }
            parent = parent.getParent();
        }
        return null;
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
            attachmentsPanel.add(attachLabel, "span, wrap, gaptop 10");

            for (File file : attachments) {
                JPanel filePanel = new JPanel(new MigLayout("insets 8", "[]8[grow]", "[]2[]"));
                filePanel.putClientProperty(FlatClientProperties.STYLE,
                        "arc:10;" +
                                "background:lighten(@background,5%)");
                filePanel.setCursor(new Cursor(Cursor.HAND_CURSOR));
                
                // Icon
                JLabel iconLabel = new JLabel(getFileIcon(file));
                filePanel.add(iconLabel, "aligny top, spany 2");

                // File name - truncate nếu quá dài
                String fileName = file.getName();
                String displayName = fileName.length() > 35
                        ? fileName.substring(0, 32) + "..."
                        : fileName;
                JLabel nameLabel = new JLabel(displayName);
                nameLabel.putClientProperty(FlatClientProperties.STYLE, "font:normal");
                nameLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
                filePanel.add(nameLabel, "growx, wrap");

                // File size
                String fileSize = EmailUtils.formatFileSize(file.length());
                JLabel sizeLabel = new JLabel(fileSize);
                sizeLabel.setForeground(Color.GRAY);
                sizeLabel.putClientProperty(FlatClientProperties.STYLE, "font:-2");
                filePanel.add(sizeLabel);
                
                // Click handler for panel
                filePanel.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        openAttachment(file);
                    }
                    
                    @Override
                    public void mouseEntered(MouseEvent e) {
                        filePanel.putClientProperty(FlatClientProperties.STYLE,
                                "arc:10;" +
                                "background:lighten(@background,8%)");
                        filePanel.revalidate();
                        filePanel.repaint();
                    }
                    
                    @Override
                    public void mouseExited(MouseEvent e) {
                        filePanel.putClientProperty(FlatClientProperties.STYLE,
                                "arc:10;" +
                                "background:lighten(@background,5%)");
                        filePanel.revalidate();
                        filePanel.repaint();
                    }
                });
                
                // Tooltip
                filePanel.setToolTipText("Click to open: " + fileName);

                // Mỗi file chiếm 1 cell, tự động wrap sau 3 files
                attachmentsPanel.add(filePanel, "growx, gaptop 5");
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
        this.currentViewingEmail = email; // Lưu email đang xem

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
            // Ưu tiên HTML body (format tốt hơn), nếu không có thì dùng plain text
            String displayBody;
            if (email.getBodyHtml() != null && !email.getBodyHtml().isEmpty()) {
                // Convert HTML sang plain text - giữ format tự nhiên từ HTML
                displayBody = ImapParser.htmlToPlainText(email.getBodyHtml());
            } else {
                // Plain text: decode entities + unwrap hard line breaks
                String decodedBody = ImapParser.decodeHtmlEntities(email.getBody());
                displayBody = EmailUtils.unwrapPlainTextEmail(decodedBody);
            }
            
            bodyTextArea.setText(displayBody);
            bodyTextArea.setCaretPosition(0);
            
            // Debug: Log kích thước khi set text (sau một chút để layout hoàn tất)
            final String finalDisplayBody = displayBody;
            SwingUtilities.invokeLater(() -> {
                logger.debug("=== Email Body Display Debug (after layout) ===");
                logger.debug("Body length: {} chars", finalDisplayBody.length());
                logger.debug("bodyTextArea actual size: {}x{}", bodyTextArea.getWidth(), bodyTextArea.getHeight());
                logger.debug("bodyTextArea preferredSize: {}", bodyTextArea.getPreferredSize());
                logger.debug("bodyTextArea visibleRect: {}", bodyTextArea.getVisibleRect());
                
                Container scrollPane = bodyTextArea.getParent().getParent();
                logger.debug("scrollPane size: {}x{}", scrollPane.getWidth(), scrollPane.getHeight());
                logger.debug("scrollPane viewport size: {}x{}", 
                        bodyTextArea.getParent().getWidth(), bodyTextArea.getParent().getHeight());
                        
                // Log parent hierarchy để debug layout
                Container p = bodyTextArea.getParent();
                int level = 0;
                while (p != null && level < 5) {
                    logger.debug("  Parent level {}: {} - size: {}x{}", 
                            level, p.getClass().getSimpleName(), p.getWidth(), p.getHeight());
                    p = p.getParent();
                    level++;
                }
            });
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
     * Update email body CHỈ KHI email đó vẫn đang được chọn
     * Tránh race condition khi user click nhanh
     */
    public void updateEmailBodyIfCurrent(Email email) {
        int selectedRow = emailTable.getSelectedRow();
        if (selectedRow < 0 || selectedRow >= emails.size()) {
            logger.debug("Email #{} loaded but no row selected, skipping UI update", email.getMessageNumber());
            return;
        }
        
        Email currentEmail = emails.get(selectedRow);
        if (currentEmail.getMessageNumber() != email.getMessageNumber()) {
            logger.debug("Email #{} loaded but user switched to email #{}, skipping UI update", 
                        email.getMessageNumber(), currentEmail.getMessageNumber());
            return;
        }
        
        // Email vẫn được chọn, update UI
        logger.debug("Updating UI for email #{} (still selected)", email.getMessageNumber());
        
        // Ưu tiên HTML body (format tốt hơn), nếu không có thì dùng plain text
        String displayBody;
        if (email.getBodyHtml() != null && !email.getBodyHtml().isEmpty()) {
            // Convert HTML sang plain text - giữ format tự nhiên từ HTML
            displayBody = ImapParser.htmlToPlainText(email.getBodyHtml());
            logger.debug("Using HTML body converted to plain text");
        } else if (email.getBody() != null) {
            // Plain text: decode entities + unwrap hard line breaks
            String decodedBody = ImapParser.decodeHtmlEntities(email.getBody());
            displayBody = EmailUtils.unwrapPlainTextEmail(decodedBody);
            logger.debug("Using plain text body with unwrap");
        } else {
            displayBody = "(No content)";
        }
        
        bodyTextArea.setText(displayBody);
        bodyTextArea.setCaretPosition(0);
        populateAttachmentsPanel(email.getAttachments());
        
        // Debug: Log kích thước sau khi setText
        final String finalDisplayBody = displayBody;
        final int messageNumber = email.getMessageNumber();
        SwingUtilities.invokeLater(() -> {
            logger.debug("=== updateEmailBodyIfCurrent Debug ===");
            logger.debug("Email #{} body length: {} chars", messageNumber, finalDisplayBody.length());
            logger.debug("bodyTextArea size: {}x{}", bodyTextArea.getWidth(), bodyTextArea.getHeight());
            logger.debug("bodyTextArea viewport size: {}x{}", 
                    bodyTextArea.getParent().getWidth(), bodyTextArea.getParent().getHeight());
            logger.debug("First 200 chars of body: {}", 
                    finalDisplayBody.length() > 200 ? finalDisplayBody.substring(0, 200) : finalDisplayBody);
        });
    }


    /**
     * Load emails (call from controller)
     */
    public void loadEmails(List<Email> emailList) {
        loadEmails(emailList, false);
    }
    
    /**
     * Load search results từ server (không filter lại local)
     */
    public void loadSearchResults(List<Email> emailList) {
        loadEmails(emailList, true);
    }
    
    /**
     * Load emails với option chỉ định có phải từ server search không
     * 
     * @param emailList danh sách emails
     * @param fromServerSearch true nếu đây là kết quả từ server search (không filter lại)
     */
    private void loadEmails(List<Email> emailList, boolean fromServerSearch) {
        // Lọc emails theo filterMode
        List<Email> filteredEmails;
        if ("STARRED".equals(filterMode)) {
            filteredEmails = emailList.stream()
                    .filter(email -> email.hasFlag("Flagged"))
                    .collect(java.util.stream.Collectors.toList());
        } else if ("UNREAD".equals(filterMode)) {
            filteredEmails = emailList.stream()
                    .filter(email -> !email.hasFlag("Seen"))
                    .collect(java.util.stream.Collectors.toList());
        } else {
            filteredEmails = emailList;
        }
        
        // Lưu danh sách gốc (sau khi filter mode nhưng trước khi search)
        this.allEmails = new ArrayList<>(filteredEmails);
        
        // Nếu đây là kết quả từ server search, không filter lại local
        if (fromServerSearch) {
            this.emails = new ArrayList<>(allEmails);
            logger.debug("Loaded {} search results from server (no local filtering)", emails.size());
        }
        // Apply local search nếu có và không phải từ server search
        else if (currentSearchQuery != null && !currentSearchQuery.isEmpty()) {
            this.emails = EmailUtils.filterBySearchQuery(allEmails, currentSearchQuery);
            logger.debug("Applied local search filter: {} emails match", emails.size());
        } else {
            this.emails = new ArrayList<>(allEmails);
        }

        refreshTable();
        
        // Hide loading panel after data is loaded
        hideLoadingPanel();

        if (emails.isEmpty()) {
            String message = getMessage();

            subjectLabel.setText("<html>No emails found</html>");
            fromLabel.setText("");
            dateLabel.setText("");
            bodyTextArea.setText(message);
        } else if (fromServerSearch && currentSearchQuery != null && !currentSearchQuery.isEmpty()) {
            // Thông báo thành công cho server search
            logger.info("Displaying {} search results from entire folder", emails.size());
        }
    }

    private String getMessage() {
        String message;
        if (currentSearchQuery != null && !currentSearchQuery.isEmpty()) {
            message = """
                    No emails match your search query in the ENTIRE folder.
                    
                    Search was performed on ALL emails (not limited to recent 50).""";
        } else if ("STARRED".equals(filterMode)) {
            message = "No starred emails found";
        } else if ("UNREAD".equals(filterMode)) {
            message = "No unread emails found";
        } else {
            message = "Your inbox is empty or no emails match the current filter.";
        }
        return message;
    }

    /**
     * Refresh table
     */
    private void refreshTable() {
        // Sort emails by date DESC (newest) - handle null dates
        emails.sort(Comparator.comparing(Email::getDate, Comparator.nullsLast(Comparator.naturalOrder())).reversed());

        tableModel.setRowCount(0);

        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd");

        for (Email email : emails) {
            boolean isRead = email.hasFlag("Seen");
            boolean isStarred = email.hasFlag("Flagged");

            String sender = email.getFrom() != null ? EmailUtils.extractName(email.getFrom()) : "Unknown";
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