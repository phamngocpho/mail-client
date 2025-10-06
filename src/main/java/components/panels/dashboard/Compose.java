package components.panels.dashboard;
import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.formdev.flatlaf.themes.FlatMacDarkLaf;
import com.formdev.flatlaf.ui.FlatLineBorder;
import controllers.SmtpController;
import jnafilechooser.api.JnaFileChooser;
import models.Email;
import net.miginfocom.swing.MigLayout;
import raven.toast.Notifications;
import values.Value;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class Compose extends JPanel {
    private JTextField toField;
    private JTextField subjectField;
    private JTextArea bodyArea;
    private JComboBox<String> fromCombo;
    private JPanel ccPanel;
    private JPanel bccPanel;
    private JTextField ccField;
    private JTextField bccField;
    private boolean ccVisible = false;
    private boolean bccVisible = false;
    private JPanel attachmentPanel;

    private final List<File> attachments = new ArrayList<>();
    private final int iconSize = Value.defaultIconSize - 2;



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
        JPanel ccBccPanel = getCcBccPanel();
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
        bodyArea.setOpaque(false);
        bodyArea.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Write your message here...");
        bodyArea.setBorder(BorderFactory.createCompoundBorder(
                new FlatLineBorder(new Insets(1, 1, 1, 1), UIManager.getColor("Component.borderColor"), 1, 20),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)
        ));

        JScrollPane scrollPane = new JScrollPane(bodyArea);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        add(scrollPane, "wrap,grow,push");

        // Attachment panel (hiển thị file đính kèm)
        attachmentPanel = new JPanel(new MigLayout("wrap 1", "[grow]", "[]"));
        attachmentPanel.setOpaque(false);
        add(attachmentPanel, "wrap,growx");

        // Bottom toolbar
        add(createToolbarPanel(), "growx,h 60!");
        setBorder(BorderFactory.createMatteBorder(0, 2, 0, 0, Value.dark_gray));
    }

    private JPanel getCcBccPanel() {
        JPanel ccBccPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        ccBccPanel.setOpaque(false);

        JLabel ccLabel = new JLabel("CC");
        ccLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
        ccLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                toggleCc();
            }
        });

        JLabel bccLabel = new JLabel("BCC");
        bccLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
        bccLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                toggleBcc();
            }
        });

        ccBccPanel.add(ccLabel);
        ccBccPanel.add(bccLabel);
        return ccBccPanel;
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

    private JButton createToolbarButton(String iconPath, int size, ActionListener listener) {
        JButton btn = new JButton(new FlatSVGIcon(iconPath, size, size));
        btn.putClientProperty(FlatClientProperties.BUTTON_TYPE,
                FlatClientProperties.BUTTON_TYPE_BORDERLESS);
        btn.setFocusPainted(false);
        if (listener != null) {
            btn.addActionListener(listener);
        }
        return btn;
    }

    private JPanel createToolbarPanel() {
        JPanel panel = new JPanel(new MigLayout("fillx,insets 12 20 12 20", "[][][][][][][][][grow][]", "[]"));

        panel.add(createToolbarButton("icons/compose/bold.svg", iconSize, e -> {}));
        panel.add(createToolbarButton("icons/compose/italic.svg", iconSize, e -> {}));
        panel.add(createToolbarButton("icons/compose/align_left.svg", iconSize, e -> {}));
        panel.add(createToolbarButton("icons/compose/bullet.svg", iconSize, e -> {}));
        panel.add(createToolbarButton("icons/compose/numbered.svg", iconSize, e -> {}));
        panel.add(createToolbarButton("icons/compose/image.svg", iconSize, e -> {}));
        panel.add(createToolbarButton("icons/compose/edit.svg", iconSize, e -> {}));
        panel.add(createToolbarButton("icons/compose/link.svg", iconSize, e -> {}));
        panel.add(createToolbarButton("icons/compose/attach.svg", iconSize, e -> chooseAttachment()));

        panel.add(new JLabel(), "pushx,growx");

        // Send button
        JButton sendBtn = new JButton("Send");
        sendBtn.setFocusPainted(false);
        sendBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        sendBtn.putClientProperty(FlatClientProperties.BUTTON_TYPE, "roundRect");
        sendBtn.addActionListener(e -> sendEmail());

        panel.add(sendBtn, "split 2, gap 0, w 80!, h 30!");
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
            protected Boolean doInBackground() {
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
                    if (child instanceof JButton btn) {
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
        JnaFileChooser fileChooser = new JnaFileChooser();
        boolean result = fileChooser.showOpenDialog(SwingUtilities.getWindowAncestor(this));

        if (result) {
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

        JButton removeBtn = new JButton("Remove");
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