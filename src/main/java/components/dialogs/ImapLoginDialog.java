package components.dialogs;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import controllers.ImapController;
import controllers.SmtpController;
import net.miginfocom.swing.MigLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import raven.toast.Notifications;
import utils.ConfigUtils;
import utils.Constants;

import javax.swing.*;
import java.awt.*;

/**
 * Dialog để nhập thông tin IMAP và kết nối
 * Tự động configure SMTP với cùng credentials
 */
public class ImapLoginDialog extends JDialog {
    private JTextField hostField;
    private JTextField emailField;
    private JPasswordField passwordField;
    private JButton connectButton;

    private final ImapController imapController;
    private boolean connected = false;
    private static final Logger logger = LoggerFactory.getLogger(ImapLoginDialog.class);
    private final int iconSize = Constants.defaultIconSize - 5;

    public ImapLoginDialog(Frame parent, ImapController controller) {
        super(parent, "Connect to Email Server", true);
        this.imapController = controller;
        initComponents();
        pack();
        setMinimumSize(new Dimension(550, 450));
        setLocationRelativeTo(parent);
    }

    private void initComponents() {
        setLayout(new MigLayout("fill, insets 20", "[right][grow]", "[]10[]10[]10[]10[]"));

        // Title
        JLabel titleLabel = new JLabel("Email Server Configuration");
        add(titleLabel, "span, wrap");

        // Provider selection
        add(new JLabel("Provider:"), "");
        JButton providerButton = getProviderButton();
        providerButton.setMargin(new Insets(2, 10, 2, 10));
        add(providerButton, "growx, wrap");

        // Host
        add(new JLabel("IMAP Host:"), "");
        hostField = new JTextField("imap.gmail.com");
        hostField.putClientProperty(FlatClientProperties.STYLE, "arc: 50; focusColor: null;");
        hostField.putClientProperty(FlatClientProperties.TEXT_FIELD_PADDING, new Insets(0, 4, 0, 4));
        add(hostField, "growx, wrap");

        // Email
        add(new JLabel("Email:"), "");
        emailField = new JTextField();
        emailField.setText(ConfigUtils.getEmail());
        emailField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "your.email@example.com");
        emailField.putClientProperty(FlatClientProperties.STYLE, "arc: 50; focusColor: null;");
        emailField.putClientProperty(FlatClientProperties.TEXT_FIELD_PADDING, new Insets(0, 4, 0, 4));
        add(emailField, "growx, wrap");

        // Password
        add(new JLabel("Password:"), "");
        passwordField = new JPasswordField();
        passwordField.setText(ConfigUtils.getAppPassword());
        passwordField.putClientProperty(FlatClientProperties.STYLE, "arc: 50; focusColor: null;");
        passwordField.putClientProperty(FlatClientProperties.TEXT_FIELD_PADDING, new Insets(0, 4, 0, 4));
        passwordField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "App Password for Gmail");
        add(passwordField, "growx, wrap");

        // Info label
        JLabel infoLabel = new JLabel("<html><i>For Gmail: Use App Password<br>" +
                "Generate at: myaccount.google.com/apppasswords<br>" +
                "<b>Note:</b> This will configure both IMAP (read) and SMTP (send)</i></html>");
        infoLabel.setForeground(Color.GRAY);
        add(infoLabel, "span, wrap");

        // Buttons
        JPanel buttonPanel = new JPanel(new MigLayout("insets 0", "[grow][][][]", "[]"));

        connectButton = new JButton("Connect");
        connectButton.putClientProperty(FlatClientProperties.BUTTON_TYPE,
                FlatClientProperties.BUTTON_TYPE_BORDERLESS);
        connectButton.addActionListener(e -> connect());

        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> dispose());

        buttonPanel.add(new JLabel(), "grow"); // Spacer
        buttonPanel.add(cancelButton);
        buttonPanel.add(connectButton);

        add(buttonPanel, "span, growx");

        // Enter a key to connect
        getRootPane().setDefaultButton(connectButton);
    }

    private JButton getProviderButton() {
        JButton providerButton = new JButton("Gmail");
        providerButton.setIcon(new FlatSVGIcon("icons/dialog/gmail.svg", iconSize - 2, iconSize - 2));
        providerButton.setHorizontalAlignment(SwingConstants.LEFT);
        providerButton.putClientProperty(FlatClientProperties.STYLE,
                "arc: 50; focusColor: null; background: #3e3e3e");

        JPopupMenu providerPopup = getProviderPopup(providerButton);

        providerButton.addActionListener(e -> {
            providerPopup.setPreferredSize(new Dimension(
                    providerButton.getWidth(),
                    providerPopup.getPreferredSize().height));
            providerPopup.show(providerButton, 0, providerButton.getHeight());
        });

        return providerButton;
    }

    private JPopupMenu getProviderPopup(JButton providerButton) {
        JPopupMenu providerPopup = new JPopupMenu();

        String[] providers = {"Gmail", "Yahoo", "Outlook", "Custom"};
        for (String provider : providers) {
            JMenuItem item = new JMenuItem(provider);
            item.setIcon(new FlatSVGIcon("icons/dialog/" + provider.toLowerCase() + ".svg", iconSize, iconSize));
            item.addActionListener(e -> {
                providerButton.setText(provider);
                providerButton.setIcon(new FlatSVGIcon("icons/dialog/" + provider.toLowerCase() + ".svg", iconSize - 2, iconSize - 2));
                updateHostFieldForProvider(provider);
            });
            providerPopup.add(item);
        }
        return providerPopup;
    }

    private void updateHostFieldForProvider(String provider) {
        switch (provider) {
            case "Gmail":
                hostField.setText("imap.gmail.com");
                passwordField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT,
                        "App Password (16 chars)");
                break;
            case "Yahoo":
                hostField.setText("imap.mail.yahoo.com");
                passwordField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT,
                        "App Password");
                break;
            case "Outlook":
                hostField.setText("outlook.office365.com");
                passwordField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT,
                        "Password");
                break;
            case "Custom":
                hostField.setText("");
                passwordField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT,
                        "Password");
                break;
        }
    }

    /**
     * Establishes a connection to the IMAP server and attempts to autoconfigure SMTP settings.
     * <br>
     * The method retrieves user-provided host, email, and password input values,
     * validates these inputs, and triggers the connection process in a background thread
     * while disabling the "Connect" button temporarily.
     *
     * If the connection is successful, both the IMAP and SMTP configurations are completed,
     * and a success notification is displayed. In case of failure, the user is notified via an error message,
     * and the "Connect" button is re-enabled for retry.
     *
     * <ul>
     * Steps:
     * - Validate input fields: host, email, and password.
     * - Temporarily disable the "Connect" button and update its text to indicate the connection process.
     * - Use a SwingWorker to connect to the IMAP server and configure SMTP settings in the background.
     * - Handle exceptions and update the UI based on success or failure.
     * </ul>
     *
     * Exceptions during the connection are caught and logged, and error messages are displayed to the user.
     */
    private void connect() {
        String host = hostField.getText().trim();
        String email = emailField.getText().trim();
        String password = new String(passwordField.getPassword());

        // Validate
        if (host.isEmpty() || email.isEmpty() || password.isEmpty()) {

            Notifications.getInstance().show(Notifications.Type.WARNING, Notifications.Location.TOP_CENTER, "Please fill in all fields");
            return;
        }

        // Disable button during connection
        connectButton.setEnabled(false);
        connectButton.setText("Connecting...");

        // Connect in the background thread
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                imapController.connectSync(host, email, password);

                // Auto-configure SMTP với cùng credentials
                SmtpController smtpController = SmtpController.getInstance();
                smtpController.configureFromImap(host, email, password);

                return null;
            }

            @Override
            protected void done() {
                try {
                    get(); // Check for exceptions - Nếu sai mật khẩu, exception sẽ throw ở đây
                    connected = true;

                    Notifications.getInstance().show(Notifications.Type.SUCCESS, Notifications.Location.TOP_CENTER, "Connected successfully! Both IMAP and SMTP are configured.");
                    dispose();
                } catch (Exception e) {
                    Notifications.getInstance().show(Notifications.Type.ERROR, "Connection failed: " + e.getMessage());
                    logger.error(e.getMessage(), e);

                    connectButton.setEnabled(true);
                    connectButton.setText("Connect");
                }
            }
        };

        worker.execute();
    }

    public boolean isConnected() {
        return connected;
    }

    /**
     * Show a dialog and return connection status
     */
    public static boolean showDialog(Frame parent, ImapController controller) {
        ImapLoginDialog dialog = new ImapLoginDialog(parent, controller);
        dialog.setVisible(true);
        return dialog.isConnected();
    }
}