package components.dialogs;

import com.formdev.flatlaf.FlatClientProperties;
import controllers.ImapController;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;

/**
 * Dialog để nhập thông tin IMAP và kết nối
 */
public class ImapLoginDialog extends JDialog {
    private JTextField hostField;
    private JTextField emailField;
    private JPasswordField passwordField;
    private JComboBox<String> providerCombo;
    private JButton connectButton;
    private JButton cancelButton;

    private ImapController controller;
    private boolean connected = false;

    public ImapLoginDialog(Frame parent, ImapController controller) {
        super(parent, "Connect to Email Server", true);
        this.controller = controller;
        initComponents();
        setSize(450, 300);
        setLocationRelativeTo(parent);
    }

    private void initComponents() {
        setLayout(new MigLayout("fill, insets 20", "[right][grow]", "[]10[]10[]10[]20[]"));

        // Title
        JLabel titleLabel = new JLabel("Email Server Configuration");
        titleLabel.putClientProperty(FlatClientProperties.STYLE, "font: bold +4");
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
        emailField.setText("phopn.23it@vku.udn.vn");
        emailField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "your.email@example.com");
        add(emailField, "growx, wrap");

        // Password
        add(new JLabel("Password:"), "");
        passwordField = new JPasswordField();
        passwordField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "App Password for Gmail");
        add(passwordField, "growx, wrap");

        // Info label
        JLabel infoLabel = new JLabel("<html><i>For Gmail: Use App Password<br>" +
                "Generate at: myaccount.google.com/apppasswords</i></html>");
        infoLabel.setForeground(Color.GRAY);
        add(infoLabel, "span, wrap");

        // Buttons
        JPanel buttonPanel = new JPanel(new MigLayout("insets 0", "[grow][][][]", "[]"));

        connectButton = new JButton("Connect");
        connectButton.putClientProperty(FlatClientProperties.BUTTON_TYPE,
                FlatClientProperties.BUTTON_TYPE_BORDERLESS);
        connectButton.addActionListener(e -> connect());

        cancelButton = new JButton("Cancel");
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

    private void connect() {
        String host = hostField.getText().trim();
        String email = emailField.getText().trim();
        String password = new String(passwordField.getPassword());

        // Validate
        if (host.isEmpty() || email.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Please fill in all fields",
                    "Validation Error",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Disable button during connection
        connectButton.setEnabled(false);
        connectButton.setText("Connecting...");

        // Connect in background thread
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                controller.connect(host, email, password);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get(); // Check for exceptions
                    connected = true;
                    dispose();
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(ImapLoginDialog.this,
                            "Connection failed: " + e.getMessage(),
                            "Connection Error",
                            JOptionPane.ERROR_MESSAGE);
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