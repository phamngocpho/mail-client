package components.dialogs;

import com.formdev.flatlaf.FlatClientProperties;
import components.notifications.popup.GlassPanePopup;
import components.notifications.popup.component.SimplePopupBorder;
import controllers.ImapController;
import controllers.SmtpController;
import net.miginfocom.swing.MigLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import protocols.imap.ImapParser;
import raven.toast.Notifications;
import utils.ConfigUtils;
import values.Value;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.util.Objects;

/**
 * Dialog để nhập thông tin IMAP và kết nối
 * Tự động configure SMTP với cùng credentials
 */
public class ImapLoginDialog extends JDialog {
    private JTextField hostField;
    private JTextField emailField;
    private JPasswordField passwordField;
    private JComboBox<String> providerCombo;
    private JButton connectButton;

    private final ImapController imapController;
    private boolean connected = false;
    private static final Logger logger = LoggerFactory.getLogger(ImapLoginDialog.class);

    public ImapLoginDialog(Frame parent, ImapController controller) {
        super(parent, "Connect to Email Server", true);
        this.imapController = controller;
        initComponents();
        setSize(new Dimension(550, 450));
        setLocationRelativeTo(parent);
    }

    private void initComponents() {
        setLayout(new MigLayout("fill, insets 20", "[right][grow]", "[]10[]10[]10[]20[]"));

        // Title
        JLabel titleLabel = new JLabel("Email Server Configuration");
        add(titleLabel, "span, wrap");

        // Provider selection
        add(new JLabel("Provider:"), "");
        String[] providers = {"Gmail", "Yahoo", "Outlook", "Custom"};
        providerCombo = new JComboBox<>(providers);
        providerCombo.addActionListener(e -> updateHostField());
        add(providerCombo, "growx, wrap");

        // Host
        add(new JLabel("IMAP Host:"), "");
        hostField = new JTextField("imap.gmail.com");
        add(hostField, "growx, wrap");

        // Email
        add(new JLabel("Email:"), "");
        emailField = new JTextField();
        emailField.setText(ConfigUtils.getEmail());
        emailField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "your.email@example.com");
        add(emailField, "growx, wrap");

        // Password
        add(new JLabel("Password:"), "");
        passwordField = new JPasswordField();
        passwordField.setText(ConfigUtils.getAppPassword());
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

        // Enter key to connect
        getRootPane().setDefaultButton(connectButton);
    }

    private void updateHostField() {
        String provider = (String) providerCombo.getSelectedItem();
        switch (Objects.requireNonNull(provider)) {
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

        // Connect in background thread
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
     * Show dialog and return connection status
     */
    public static boolean showDialog(Frame parent, ImapController controller) {
        ImapLoginDialog dialog = new ImapLoginDialog(parent, controller);
        dialog.setVisible(true);
        return dialog.isConnected();
    }
}