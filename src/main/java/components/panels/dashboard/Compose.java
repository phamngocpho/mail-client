package components.panels.dashboard;
import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.formdev.flatlaf.themes.FlatMacDarkLaf;
import controllers.SmtpController;
import models.Email;
import net.miginfocom.swing.MigLayout;
import raven.toast.Notifications;
import values.Value;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Compose extends JPanel {
    private JTextField toField;
    private JTextField subjectField;
    private JTextArea bodyArea;
    private JComboBox<String> fromCombo;
    private JLabel ccLabel;
    private JLabel bccLabel;
    private JPanel ccPanel;
    private JPanel bccPanel;
    private JTextField ccField;
    private JTextField bccField;
    private boolean ccVisible = false;
    private boolean bccVisible = false;
    private JPanel attachmentPanel;

    private final List<File> attachments = new ArrayList<>();



    private SmtpController controller;

    public Compose() {
        this.controller = SmtpController.getInstance();
        init();
    }

    /**
     * Set controller (nếu muốn inject từ bên ngoài)
     */
    public void setController(SmtpController controller) {
        this.controller = controller;
    }

    private void init() {
        setLayout(new MigLayout(
                "fillx,insets 10",
                "[grow,fill]",
                "[][][][][][] [grow,fill] []"
        ));


        // To field
        JPanel toPanel = createFieldPanel("To", true);
        toField = new JTextField();
        toField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Search or add a contact");
        toField.putClientProperty(FlatClientProperties.STYLE, "arc: 30");
        toPanel.add(toField, "growx,pushx, h 25!");

        // CC/BCC labels
        JPanel ccBccPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        ccBccPanel.setOpaque(false);

        ccLabel = new JLabel("CC");
        ccLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
        ccLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                toggleCc();
            }
        });

        bccLabel = new JLabel("BCC");
        bccLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
        bccLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                toggleBcc();
            }
        });

        ccBccPanel.add(ccLabel);
        ccBccPanel.add(bccLabel);
        toPanel.add(ccBccPanel);

        add(toPanel, "wrap,growx");

        // CC field (hidden by default)
        ccPanel = createFieldPanel("CC", false);
        ccField = new JTextField();
        ccField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Add CC recipients");
        ccField.putClientProperty(FlatClientProperties.STYLE, "arc: 30");
        ccPanel.add(ccField, "growx, h 25!");
        ccPanel.setVisible(false);
        add(ccPanel, "wrap,growx");

        // BCC field (hidden by default)
        bccPanel = createFieldPanel("BCC", false);
        bccField = new JTextField();
        bccField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Add BCC recipients");
        bccField.putClientProperty(FlatClientProperties.STYLE, "arc: 30");
        bccPanel.add(bccField, "growx, h 25!");
        bccPanel.setVisible(false);
        add(bccPanel, "wrap,growx");

        // Subject field
        JPanel subjectPanel = createFieldPanel("Subject", false);
        subjectField = new JTextField();
        subjectField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Add a subject");
        subjectField.putClientProperty(FlatClientProperties.STYLE, "arc: 30");
        subjectPanel.add(subjectField, "growx, h 25!");
        add(subjectPanel, "wrap,growx");

        // From field
        JPanel fromPanel = createFieldPanel("From", false);
        fromCombo = new JComboBox<>(new String[]{getDefaultEmail()});
        fromCombo.setBackground(Value.dark_gray);

        JLabel avatarLabel = new JLabel();
        avatarLabel.setPreferredSize(new Dimension(32, 32));
        avatarLabel.setOpaque(true);
        fromPanel.add(avatarLabel, "w 32!,h 32!");
        fromPanel.add(fromCombo, "growx");
        add(fromPanel, "wrap,growx");

        // Separator
        add(new JSeparator(), "wrap,growx,h 1!");

        // Body area
        bodyArea = new JTextArea();
        bodyArea.setLineWrap(true);
        bodyArea.setWrapStyleWord(true);
        bodyArea.putClientProperty(FlatClientProperties.STYLE, "margin: 10,10,10,10");
        bodyArea.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Write your message here...");

        JScrollPane scrollPane = new JScrollPane(bodyArea);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        add(scrollPane, "wrap,grow,pushy");

        // Attachment panel (hiển thị file đính kèm)
        attachmentPanel = new JPanel(new MigLayout("wrap 1", "[grow]", "[]"));
        attachmentPanel.setOpaque(false);
        add(attachmentPanel, "wrap,growx");

        // Bottom toolbar
        add(createToolbarPanel(), "growx,h 60!");
    }

    private JPanel createFieldPanel(String label, boolean isToField) {
        JPanel panel = new JPanel(new MigLayout(
                "fillx,insets 0",
                "[60!]" + (isToField ? "[grow,fill][]" : "[grow,fill]"),
                "[]"
        ));

        JLabel labelComp = new JLabel(label);
        panel.add(labelComp);

        return panel;
    }

    private JPanel createToolbarPanel() {
        JPanel panel = new JPanel(new MigLayout("fillx,insets 12 20 12 20", "[][][][][][][][grow][]", "[]"));

        // Toolbar buttons
        String[] icons = {"B", "I", "≡", "• •", "1.", "🖼", "✎", "🔗"};
        for (String icon : icons) {
            JButton btn = new JButton(icon);
            btn.putClientProperty(FlatClientProperties.BUTTON_TYPE,
                    FlatClientProperties.BUTTON_TYPE_BORDERLESS);
            btn.setFocusPainted(false);
            panel.add(btn);
        }

        panel.add(new JLabel(), "pushx,growx");

        // Send button
        JButton sendBtn = new JButton("✉ Send");
        sendBtn.setFocusPainted(false);
        sendBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        sendBtn.putClientProperty(FlatClientProperties.BUTTON_TYPE, "roundRect");
        sendBtn.addActionListener(e -> sendEmail());

        JButton attachBtn = new JButton();
        attachBtn.setIcon(new FlatSVGIcon("icons/attach.svg",25, 25));
        attachBtn.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_BORDERLESS);
        attachBtn.setToolTipText("Attach file");
        attachBtn.addActionListener(e -> chooseAttachment());

        JButton dropdownBtn = new JButton("▼");
        dropdownBtn.setFocusPainted(false);
        dropdownBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        panel.add(attachBtn);
        panel.add(sendBtn, "split 2,gap 0");
        panel.add(dropdownBtn, "gap 2");

        return panel;
    }

    private void toggleCc() {
        ccVisible = !ccVisible;
        ccPanel.setVisible(ccVisible);
        if (ccVisible) {
            ccField.requestFocus();
        }
        revalidate();
        repaint();
    }

    private void toggleBcc() {
        bccVisible = !bccVisible;
        bccPanel.setVisible(bccVisible);
        if (bccVisible) {
            bccField.requestFocus();
        }
        revalidate();
        repaint();
    }

    /**
     * Send email - tương tự như connect() trong Inbox
     */
    private void sendEmail() {
        // Validate
        if (!validateForm()) {
            return;
        }

        // Check if SMTP configured
        if (!controller.isConfigured()) {
            showError("SMTP not configured. Please login first.");
            return;
        }

        // Show loading state
        JButton sendBtn = findSendButton();
        if (sendBtn != null) {
            sendBtn.setEnabled(false);
            sendBtn.setText("Sending...");
        }

        // Send in background thread
        SwingWorker<Boolean, Void> worker = new SwingWorker<>() {
            @Override
            protected Boolean doInBackground() throws Exception {
                return controller.sendEmail(createEmail());
            }

            @Override
            protected void done() {
                try {
                    boolean success = get();
                    if (success) {
                        Notifications.getInstance().show(Notifications.Type.SUCCESS, "Email sent successfully!");
                        clearForm();
                    } else {
                        showError("Failed to send email. Please check your connection.");
                    }
                } catch (Exception e) {
                    showError("Error: " + e.getMessage());
                } finally {
                    if (sendBtn != null) {
                        sendBtn.setEnabled(true);
                        sendBtn.setText("Send");
                    }
                }
            }
        };

        worker.execute();
    }

    private boolean validateForm() {
        String to = toField.getText().trim();
        if (to.isEmpty()) {
            showError("Please enter recipient email address");
            toField.requestFocus();
            return false;
        }

        String body = bodyArea.getText().trim();
        if (body.isEmpty()) {
            int result = JOptionPane.showConfirmDialog(
                    this,
                    "Email body is empty. Send anyway?",
                    "Confirm",
                    JOptionPane.YES_NO_OPTION
            );
            return result == JOptionPane.YES_OPTION;
        }

        return true;
    }

    private Email createEmail() {
        Email email = new Email();
        email.setFrom(getFrom());

        // Parse To addresses
        String[] toAddresses = toField.getText().split("[,;]");
        for (String addr : toAddresses) {
            String trimmed = addr.trim();
            if (!trimmed.isEmpty()) {
                email.addTo(trimmed);
            }
        }

        // Parse CC addresses
        if (ccVisible && !ccField.getText().trim().isEmpty()) {
            String[] ccAddresses = ccField.getText().split("[,;]");
            for (String addr : ccAddresses) {
                String trimmed = addr.trim();
                if (!trimmed.isEmpty()) {
                    email.addCc(trimmed);
                }
            }
        }
        for (File file : attachments) {
            email.addAttachment(file);
        }

        email.setSubject(subjectField.getText().trim());
        email.setBody(bodyArea.getText());

        return email;
    }

    private void clearForm() {
        toField.setText("");
        ccField.setText("");
        bccField.setText("");
        subjectField.setText("");
        bodyArea.setText("");
        ccPanel.setVisible(false);
        bccPanel.setVisible(false);
        ccVisible = false;
        bccVisible = false;
        revalidate();
        repaint();
        attachments.clear();
        attachmentPanel.removeAll();
    }

    private JButton findSendButton() {
        Component[] components = getComponents();
        for (Component comp : components) {
            if (comp instanceof JPanel) {
                for (Component child : ((JPanel) comp).getComponents()) {
                    if (child instanceof JButton) {
                        JButton btn = (JButton) child;
                        if (btn.getText().contains("Send")) {
                            return btn;
                        }
                    }
                }
            }
        }
        return null;
    }

    private void chooseAttachment() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setMultiSelectionEnabled(true);
        int result = fileChooser.showOpenDialog(this);

        if (result == JFileChooser.APPROVE_OPTION) {
            File[] selectedFiles = fileChooser.getSelectedFiles();
            for (File f : selectedFiles) {
                attachments.add(f);
                addAttachmentToPanel(f);
            }
            revalidate();
            repaint();
        }
    }

    private void addAttachmentToPanel(File file) {
        JPanel fileItem = new JPanel(new MigLayout("insets 5, fillx", "[grow][]"));
        fileItem.setBackground(new Color(40, 40, 40));
        fileItem.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

        JLabel nameLabel = new JLabel("📄 " + file.getName());
        nameLabel.setForeground(Color.WHITE);

        JButton removeBtn = new JButton("❌");
        removeBtn.setFocusPainted(false);
        removeBtn.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_BORDERLESS);
        removeBtn.addActionListener(e -> {
            attachments.remove(file);
            attachmentPanel.remove(fileItem);
            revalidate();
            repaint();
        });

        fileItem.add(nameLabel, "growx");
        fileItem.add(removeBtn);
        attachmentPanel.add(fileItem, "growx, wrap");
    }



    private String getDefaultEmail() {
        if (controller.isConfigured()) {
            return controller.getUsername();
        }
        return "your@email.com";
    }

    /**
     * Show error dialog
     */
    private void showError(String message) {
        Notifications.getInstance().show(Notifications.Type.ERROR, message);
    }

    // Getters
    public String getTo() {
        return toField.getText();
    }

    public String getCc() {
        return ccField.getText();
    }

    public String getBcc() {
        return bccField.getText();
    }

    public String getSubject() {
        return subjectField.getText();
    }

    public String getBody() {
        return bodyArea.getText();
    }

    public String getFrom() {
        return (String) fromCombo.getSelectedItem();
    }
    public static void main(String[] args) {
        FlatMacDarkLaf.setup();
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame();
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(700, 600);

            Compose panel = new Compose();
            frame.add(panel);

            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}