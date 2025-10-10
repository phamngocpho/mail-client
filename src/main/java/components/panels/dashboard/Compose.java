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

public class Compose extends JPanel {
    private JTextField toField;
    private JTextField subjectField;
    private JTextArea bodyArea;
    private JTextField ccField;
    private JTextField bccField;
    private boolean ccVisible = false;
    private boolean bccVisible = false;
    private JPanel attachmentPanel;

    private final List<File> attachments = new ArrayList<>();
    private final int iconSize = Value.defaultIconSize - 3;

    private final SmtpController controller;
    private JButton fromSelector;

    public Compose() {
        this.controller = SmtpController.getInstance();
        controller.addPropertyChangeListener(evt -> {
            if ("configured".equals(evt.getPropertyName()) &&
                    Boolean.TRUE.equals(evt.getNewValue())) {
                updateFromEmail();
            }
        });
        init();
    }

    private void init() {
        setLayout(new MigLayout(
                "fillx,insets 10",
                "[60!][grow,fill][fill]",
                "[][][][][][1!][grow,fill][][]"
        ));

        // To field
        add(new JLabel("To"), "");
        toField = createTextField("Search or add a contact");
        toField.putClientProperty(FlatClientProperties.TEXT_FIELD_PADDING, new Insets(0, 4, 0, 4));
        add(toField, "growx,h 25!");
        add(getCcBccPanel(), "wrap, al right");

        // CC field (hidden by default)
        JLabel ccLabel = new JLabel("CC");
        ccLabel.setVisible(false);
        add(ccLabel, "");
        ccField = createTextField("Add CC recipients");
        ccField.setVisible(false);
        ccField.putClientProperty(FlatClientProperties.TEXT_FIELD_PADDING, new Insets(0, 4, 0, 4));
        add(ccField, "growx,h 25!,span,wrap");

        // BCC field (hidden by default)
        JLabel bccLabel = new JLabel("BCC");
        bccLabel.setVisible(false);
        add(bccLabel, "");
        bccField = createTextField("Add BCC recipients");
        bccField.setVisible(false);
        bccField.putClientProperty(FlatClientProperties.TEXT_FIELD_PADDING, new Insets(0, 4, 0, 4));
        add(bccField, "growx,h 25!,span,wrap");

        // Subject field
        add(new JLabel("Subject"), "");
        subjectField = createTextField("Add a subject");
        subjectField.putClientProperty(FlatClientProperties.TEXT_FIELD_PADDING, new Insets(0, 4, 0, 4));
        add(subjectField, "growx,h 25!,span,wrap");

        // From field
        add(new JLabel("From"), "");

        fromSelector = createFromSelector();
        add(fromSelector, "grow,al right,h 28!, span, w ::30%, wrap");

        // Separator
        add(new JSeparator(), "span,growx,h 1!,wrap");

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
        add(scrollPane, "span,grow,push,wrap");

        // Attachment panel
        attachmentPanel = new JPanel(new MigLayout("wrap 1", "[grow]", "[]"));
        attachmentPanel.setOpaque(false);
        add(attachmentPanel, "span,growx,wrap");

        // Bottom toolbar
        add(createToolbarPanel(), "span,growx,h 60!");
        setBorder(BorderFactory.createMatteBorder(0, 2, 0, 0, Value.dark_gray));
    }

    private JTextField createTextField(String placeholder) {
        JTextField field = new JTextField();
        field.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, placeholder);
        field.putClientProperty(FlatClientProperties.STYLE,
                "arc: 30; " +
                        "focusWidth: 0; " +
                        "borderColor: " + String.format("#%06X", (UIManager.getColor("Component.borderColor").getRGB() & 0x3e3e3e)) + "; " +
                        "focusedBorderColor: " + String.format("#%06X", (UIManager.getColor("Component.borderColor").getRGB() & 0x3e3e3e)));
        return field;
    }

    private JButton createFromSelector() {
        fromSelector = new JButton();
        fromSelector.setHorizontalTextPosition(SwingConstants.LEFT);
        fromSelector.putClientProperty(FlatClientProperties.BUTTON_TYPE, "roundRect");
        fromSelector.setFocusPainted(false);
        fromSelector.setCursor(new Cursor(Cursor.HAND_CURSOR));
        fromSelector.setBackground(Value.dark_gray);
        fromSelector.addActionListener(e -> showEmailSelector());
        updateFromEmail();
        return fromSelector;
    }

    private JPanel getCcBccPanel() {
        JPanel ccBccPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        ccBccPanel.setOpaque(false);

        JLabel ccLabelBtn = new JLabel("CC");
        ccLabelBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        ccLabelBtn.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                toggleCc();
            }
        });

        JLabel bccLabelBtn = new JLabel("BCC");
        bccLabelBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        bccLabelBtn.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                toggleBcc();
            }
        });

        ccBccPanel.add(ccLabelBtn);
        ccBccPanel.add(bccLabelBtn);
        return ccBccPanel;
    }

    private JButton createToolbarButton(String iconPath, ActionListener listener) {
        JButton btn = new JButton(new FlatSVGIcon(iconPath, iconSize, iconSize));
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

        panel.add(createToolbarButton("icons/compose/bold.svg", e -> {}));
        panel.add(createToolbarButton("icons/compose/italic.svg", e -> {}));
        panel.add(createToolbarButton("icons/compose/align_left.svg", e -> {}));
        panel.add(createToolbarButton("icons/compose/bullet.svg", e -> {}));
        panel.add(createToolbarButton("icons/compose/numbered.svg", e -> {}));
        panel.add(createToolbarButton("icons/compose/image.svg", e -> {}));
        panel.add(createToolbarButton("icons/compose/edit.svg", e -> {}));
        panel.add(createToolbarButton("icons/compose/link.svg", e -> {}));
        panel.add(createToolbarButton("icons/compose/attach.svg", e -> chooseAttachment()));

        panel.add(new JLabel(), "pushx,growx");

        JButton sendBtn = new JButton("Send");
        sendBtn.setFocusPainted(false);
        sendBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        sendBtn.putClientProperty(FlatClientProperties.BUTTON_TYPE, "roundRect");
        sendBtn.addActionListener(e -> sendEmail());

        panel.add(sendBtn, "split 2, gap 0, w 100!, h 30!");
        return panel;
    }

    private void toggleCc() {
        ccVisible = !ccVisible;
        Component[] components = getComponents();
        for (int i = 0; i < components.length; i++) {
            if (components[i] instanceof JLabel && ((JLabel) components[i]).getText().equals("CC")) {
                components[i].setVisible(ccVisible);
                if (i + 1 < components.length && components[i + 1] == ccField) {
                    ccField.setVisible(ccVisible);
                    if (ccVisible) {
                        ccField.requestFocus();
                    }
                }
                break;
            }
        }
        revalidate();
        repaint();
    }

    private void toggleBcc() {
        bccVisible = !bccVisible;
        Component[] components = getComponents();
        for (int i = 0; i < components.length; i++) {
            if (components[i] instanceof JLabel && ((JLabel) components[i]).getText().equals("BCC")) {
                components[i].setVisible(bccVisible);
                if (i + 1 < components.length && components[i + 1] == bccField) {
                    bccField.setVisible(bccVisible);
                    if (bccVisible) {
                        bccField.requestFocus();
                    }
                }
                break;
            }
        }
        revalidate();
        repaint();
    }

    private void sendEmail() {
        if (!validateForm()) {
            return;
        }

        if (!controller.isConfigured()) {
            showError("SMTP not configured. Please login first.");
            return;
        }

        JButton sendBtn = findSendButton();
        if (sendBtn != null) {
            sendBtn.setEnabled(false);
            sendBtn.setText("Sending...");
        }

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
        email.setFrom(fromSelector.getText().trim());

        String[] toAddresses = toField.getText().split("[,;]");
        for (String addr : toAddresses) {
            String trimmed = addr.trim();
            if (!trimmed.isEmpty()) {
                email.addTo(trimmed);
            }
        }

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

        Component[] components = getComponents();
        for (Component comp : components) {
            if (comp instanceof JLabel label) {
                if ("CC".equals(label.getText()) || "BCC".equals(label.getText())) {
                    label.setVisible(false);
                }
            }
        }
        ccField.setVisible(false);
        bccField.setVisible(false);
        ccVisible = false;
        bccVisible = false;

        attachments.clear();
        attachmentPanel.removeAll();
        revalidate();
        repaint();
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

    private Icon getFileIcon(File file) {
        String fileName = file.getName().toLowerCase();
        String iconPath = "icons/compose/files/";

        if (fileName.endsWith(".txt")) {
            iconPath += "txt.svg";
        } else if (fileName.endsWith(".pdf")) {
            iconPath += "pdf.svg";
        } else if (fileName.endsWith(".doc") || fileName.endsWith(".docx")) {
            iconPath += "word.svg";
        } else if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") ||
                fileName.endsWith(".png") || fileName.endsWith(".gif") ||
                fileName.endsWith(".bmp") || fileName.endsWith(".svg")) {
            iconPath += "image.svg";
        } else if (fileName.endsWith(".mp3") || fileName.endsWith(".wav") ||
                fileName.endsWith(".flac") || fileName.endsWith(".aac") ||
                fileName.endsWith(".m4a") || fileName.endsWith(".ogg")) {
            iconPath += "audio.svg";
        } else if (fileName.endsWith(".mp4") || fileName.endsWith(".avi") ||
                fileName.endsWith(".mkv") || fileName.endsWith(".mov") ||
                fileName.endsWith(".wmv") || fileName.endsWith(".flv")) {
            iconPath += "video.svg";
        } else if (fileName.endsWith(".exe") || fileName.endsWith(".msi")) {
            iconPath += "exe.svg";
        } else if (fileName.endsWith(".bat") || fileName.endsWith(".cmd")) {
            iconPath += "bat.svg";
        } else {
            iconPath += "unknown.svg";
        }

        return new FlatSVGIcon(iconPath, iconSize, iconSize);
    }

    private void addAttachmentToPanel(File file) {
        JPanel fileItem = new JPanel(new MigLayout("insets 5, fillx", "[grow][]"));
        fileItem.setBackground(new Color(40, 40, 40));
        fileItem.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

        JLabel nameLabel = new JLabel(file.getName());
        nameLabel.setIcon(getFileIcon(file));
        nameLabel.setForeground(Color.WHITE);

        JButton removeBtn = new JButton(new FlatSVGIcon("icons/compose/remove.svg", iconSize, iconSize));
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

    private void updateFromEmail() {
        SwingUtilities.invokeLater(() -> {
            if (fromSelector != null) {
                if (controller.isConfigured()) {
                    fromSelector.setText(controller.getUsername());
                } else {
                    fromSelector.setText("Connect to email server...");
                }
                revalidate();
                repaint();
            }
        });
    }

    private void showEmailSelector() {
        if (!controller.isConfigured()) {
            Notifications.getInstance().show(
                    Notifications.Type.WARNING,
                    Notifications.Location.TOP_CENTER,
                    "Please connect to email server first"
            );
        }
    }

    private void showError(String message) {
        Notifications.getInstance().show(Notifications.Type.ERROR, message);
    }

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