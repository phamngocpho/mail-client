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
import utils.Constants;
import utils.HtmlEditorUtils;
import utils.UIUtils;

import javax.swing.*;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static utils.UIUtils.getFileIcon;

/**
 * The Compose class provides a user interface for composing and sending emails.
 * <p>
 * This class includes various components such as fields for recipient addresses,
 * subjects, and email bodies, as well as functionality for attaching files, managing
 * "CC" and "BCC" fields, and interacting with email server configurations. The Compose
 * class ensures dynamic updates to the UI and enables users to send emails efficiently
 * while validating inputs and handling attachments.
 */
public class Compose extends JPanel {
    private JTextField toField;
    private JTextField subjectField;
    private JTextPane bodyArea;
    private JTextField ccField;
    private JTextField bccField;
    private boolean ccVisible = false;
    private boolean bccVisible = false;
    private JPanel attachmentPanel;

    private final List<File> attachments = new ArrayList<>();
    private final int iconSize = Constants.defaultIconSize - 3;

    private final SmtpController controller;
    private JButton fromSelector;
    
    // References for dynamic components
    private JLabel ccLabel;
    private JLabel bccLabel;
    private JPanel ccBccPanel;

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
                "[]"  // Dynamic rows - no fixed row count
        ));

        // To field
        add(new JLabel("To"), "");
        toField = createTextField("Search or add a contact");
        toField.putClientProperty(FlatClientProperties.TEXT_FIELD_PADDING, new Insets(0, 4, 0, 4));
        add(toField, "growx,h 25!");
        ccBccPanel = getCcBccPanel();
        add(ccBccPanel, "wrap, al right");

        // Create CC field components (not added to layout yet)
        ccLabel = new JLabel("CC");
        ccField = createTextField("Add CC recipients");
        ccField.putClientProperty(FlatClientProperties.TEXT_FIELD_PADDING, new Insets(0, 4, 0, 4));

        // Create BCC field components (not added to layout yet)
        bccLabel = new JLabel("BCC");
        bccField = createTextField("Add BCC recipients");
        bccField.putClientProperty(FlatClientProperties.TEXT_FIELD_PADDING, new Insets(0, 4, 0, 4));

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
        
        // Spacer to push body area down
        add(new JLabel(), "span,h 1!,wrap");

        // Body area with HTML support
        bodyArea = new JTextPane();
        bodyArea.setContentType("text/html");

        // Configure HTMLEditorKit using utility class
        HTMLEditorKit kit = HtmlEditorUtils.createConfiguredEditorKit();
        bodyArea.setEditorKit(kit);
        bodyArea.setForeground(UIUtils.getTextColor());

        // Set initial HTML content
        bodyArea.setText(UIUtils.getHtmlTemplate());

        bodyArea.setOpaque(false);
        bodyArea.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Write your message here...");
        bodyArea.setBorder(BorderFactory.createCompoundBorder(
                new FlatLineBorder(new Insets(1, 1, 1, 1), UIManager.getColor("Component.borderColor"), 1, 20),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)
        ));

        JScrollPane scrollPane = new JScrollPane(bodyArea);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        add(scrollPane, "span,grow,push,wrap,wmin 0,hmin 0");

        // Attachment panel
        attachmentPanel = new JPanel(new MigLayout("wrap 1", "[grow]", "[]"));
        attachmentPanel.setOpaque(false);
        add(attachmentPanel, "span,growx,wrap");

        // Bottom toolbar
        add(createToolbarPanel(), "span,growx,h 60!");
        setBorder(BorderFactory.createMatteBorder(0, 2, 0, 0, Constants.dark_gray));
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

    /**
     * Creates and configures a JButton instance named "fromSelector".
     * The button is styled with a round rectangle appearance,
     * a left-aligned horizontal text position, no focus paint, and a hand cursor.
     * It also sets a dark gray background color and attaches an action listener
     * to display an email selector when clicked. The button's label is updated
     * using the updateFromEmail method.
     *
     * @return a configured instance of JButton
     */
    private JButton createFromSelector() {
        fromSelector = new JButton();
        fromSelector.setHorizontalTextPosition(SwingConstants.LEFT);
        fromSelector.putClientProperty(FlatClientProperties.BUTTON_TYPE, "roundRect");
        fromSelector.setFocusPainted(false);
        fromSelector.setCursor(new Cursor(Cursor.HAND_CURSOR));
        fromSelector.setBackground(Constants.dark_gray);
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

        panel.add(createToolbarButton("icons/compose/bold.svg", e -> toggleBold()));
        panel.add(createToolbarButton("icons/compose/italic.svg", e -> toggleItalic()));
        panel.add(createToolbarButton("icons/compose/align_left.svg", e -> alignLeft()));
        panel.add(createToolbarButton("icons/compose/bullet.svg", e -> insertBulletList()));
        panel.add(createToolbarButton("icons/compose/numbered.svg", e -> insertNumberedList()));
        panel.add(createToolbarButton("icons/compose/image.svg", e -> insertImage()));
        panel.add(createToolbarButton("icons/compose/edit.svg", e -> clearFormatting()));
        panel.add(createToolbarButton("icons/compose/link.svg", e -> insertLink()));
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

    /**
     * Helper method to find the index of a component in the panel.
     * 
     * @param component the component to find
     * @return the index of the component, or -1 if not found
     */
    private int getComponentIndex(Component component) {
        Component[] components = getComponents();
        for (int i = 0; i < components.length; i++) {
            if (components[i] == component) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Toggles the visibility of the "CC" field and its associated components.
     * <p>
     * This method manages the visibility of components related to the "CC" field based on the
     * current state. It updates the `ccVisible` flag and dynamically adds or removes the CC
     * field components from the layout. When made visible, the components are inserted after
     * the "To" field. The input field requests focus when shown.
     * Finally, the method triggers a revalidation and repainting of the component hierarchy.
     */
    private void toggleCc() {
        ccVisible = !ccVisible;
        
        if (ccVisible) {
            // Find the index after ccBccPanel to insert CC field
            int index = getComponentIndex(ccBccPanel);
            if (index >= 0) {
                add(ccLabel, "", index + 1);
                add(ccField, "growx,h 25!,span,wrap", index + 2);
                ccField.requestFocus();
            }
        } else {
            // Remove CC components
            remove(ccLabel);
            remove(ccField);
        }
        
        revalidate();
        repaint();
    }

    /**
     * Toggles the visibility of the BCC (Blind Carbon Copy) field and its corresponding label in the
     * email composition form.
     * <p>
     * This method performs the following actions:
     * - Flips the value of the `bccVisible` boolean field, determining the visibility of the BCC field.
     * - Dynamically adds or removes the BCC field components from the layout.
     * - When made visible, the components are inserted after the CC field (if visible) or after the "To" field.
     * - If the BCC field becomes visible, it requests focus.
     * - Revalidates and repaints the user interface to reflect the changes.
     * <p>
     * This method ensures the BCC field and its associated label are dynamically shown or hidden
     * based on the user's interaction with the toggle action.
     */
    private void toggleBcc() {
        bccVisible = !bccVisible;
        
        if (bccVisible) {
            // Find the index to insert BCC field
            // If CC is visible, insert after CC field; otherwise after ccBccPanel
            int index;
            if (ccVisible) {
                index = getComponentIndex(ccField);
            } else {
                index = getComponentIndex(ccBccPanel);
            }
            
            if (index >= 0) {
                add(bccLabel, "", index + 1);
                add(bccField, "growx,h 25!,span,wrap", index + 2);
                bccField.requestFocus();
            }
        } else {
            // Remove BCC components
            remove(bccLabel);
            remove(bccField);
        }
        
        revalidate();
        repaint();
    }

    /**
     * Sends an email based on the current composition form data.
     * <p>
     * This method validates the form before proceeding to send the email. If the form is invalid,
     * no further actions are taken. Once validated, it checks whether the SMTP configuration is available;
     * if not, an error message is displayed, prompting the user to log in first.
     * <p>
     * The method disables the "Send" button during the email-sending process and provides feedback
     * to the user by updating the button text to indicate the ongoing action. It employs a `SwingWorker`
     * to handle the email-sending process asynchronously:
     * <p>
     * - The `doInBackground` method sends the email using the controller's `sendEmail` method.
     * - After completion, the `done` method updates the UI and provides user feedback,
     *   such as success notifications or error messages.
     * <p>
     * Ensure the controller is properly configured before attempting to send emails to avoid errors.
     */
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

    /**
     * Validates the email composition form by ensuring required fields are populated
     * and confirming user intent for certain conditions.
     * <p>
     * The method performs the following validations:
     * <p>
     * 1. Checks if the "To" field is empty. If it is, it displays an error message
     *    and focuses on the field for correction.
     * 2. Checks if the email body field is empty. If it is, prompts the user with
     *    a confirmation dialog to send the email without a body.
     *
     * @return true if the form is valid and ready for submission; false otherwise
     */
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

    /**
     * Creates and initializes a new Email object based on the input fields and selections
     * from the email composition form.
     * <p>
     * The method performs the following actions:
     * - Sets the "From" address using the trimmed text from the `fromSelector`.
     * - Splits and adds recipient addresses from the "To" field.
     * - If the CC field is visible and contains input, it splits and adds recipient
     *   addresses from the CC field.
     * - Attaches all files from the `attachments` list to the email.
     * - Sets the email subject from the trimmed text of the subject field.
     * - Sets the email body from the content of the body text area.
     *
     * @return A fully constructed Email object with specified recipients, attachments,
     *         subject, and body.
     */
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

    /**
     * Clears all fields and components in the email composition form.
     * <p>
     * This method resets the input fields, removes the CC and BCC fields from the layout,
     * clears any attachments from the attachment panel, and refreshes the user interface. 
     * The following actions are performed:
     * <p>
     * - Resets the text fields for "To", "CC", "BCC", "Subject", and "Body" to empty strings.
     * - Removes CC and BCC fields from the layout if they are currently visible.
     * - Updates the `ccVisible` and `bccVisible` flags to `false`.
     * - Clears the `attachments` list that tracks attached files.
     * - Removes all components from the attachment panel to reflect the cleared attachments.
     * - Revalidates and repaints the components to ensure the UI is updated.
     */
    private void clearForm() {
        toField.setText("");
        ccField.setText("");
        bccField.setText("");
        subjectField.setText("");
        bodyArea.setText("");

        // Remove CC and BCC fields from layout if visible
        if (ccVisible) {
            remove(ccLabel);
            remove(ccField);
            ccVisible = false;
        }
        
        if (bccVisible) {
            remove(bccLabel);
            remove(bccField);
            bccVisible = false;
        }

        attachments.clear();
        attachmentPanel.removeAll();
        revalidate();
        repaint();
    }

    /**
     * Searches for and returns a JButton with the text containing "Send" within the panel's components.
     * The method iterates through the components of the current panel and its subpanels, checking
     * for a JButton with "Send" in its text.
     *
     * @return the JButton with text containing "Send" if found; otherwise, null
     */
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

    /**
     * Opens a file chooser dialog to allow the user to select files for attachment.
     * <p>
     * This method uses a JnaFileChooser to provide a file selection dialog. If the user
     * selects files and confirms their selection, the selected files are added to the
     * attachment's list and displayed in the attachment panel. Each attachment is visually
     * represented, and the user can manage these attachments in the panel.
     * <p>
     * The attachment processing involves:
     * - Adding selected files to the `attachments` list.
     * - Displaying each file in the attachment panel using the `addAttachmentToPanel` method.
     * - Refreshing the UI to ensure the attachment panel reflects the added files.
     */
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

    /**
     * Adds a file attachment as a visual component to the attachment panel.
     * The method creates a panel for displaying the file name and a remove button,
     * allowing users to view and remove an attachment from the attachment panel.
     *
     * @param file the file to be added as an attachment panel component
     */
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

    /**
     * Updates the "From" field in the email composition panel based on the email server configuration.
     * <p>
     * This method ensures that the "From" field reflects the current state of the email server
     * as managed by the controller. If the email server is configured, the username associated
     * with the account is displayed in the "From" field. Otherwise, a placeholder text is shown
     * prompting the user to connect to the email server.
     * <p>
     * This operation is executed on the Event Dispatch Thread to ensure thread safety for
     * Swing components.
     */
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

    /**
     * Displays a notification to the user when attempting to access the email selector
     * without the email server being configured.
     * <p>
     * This method checks the configuration status of the email server using the controller.
     * If the server is not configured, a warning notification is displayed at the top center
     * of the screen, informing the user to connect to the email server first.
     */
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

    // ========== Text Formatting Methods ==========
    // Delegate to HtmlEditorUtils for cleaner, more maintainable code

    /**
     * Toggles bold formatting for the selected text in the body area.
     */
    private void toggleBold() {
        HtmlEditorUtils.toggleBold(bodyArea);
    }

    /**
     * Toggles italic formatting for the selected text in the body area.
     */
    private void toggleItalic() {
        HtmlEditorUtils.toggleItalic(bodyArea);
    }

    /**
     * Aligns the selected paragraph to the left.
     */
    private void alignLeft() {
        HtmlEditorUtils.alignLeft(bodyArea);
    }

    /**
     * Inserts a bullet list at the current cursor position.
     */
    private void insertBulletList() {
        HtmlEditorUtils.insertBulletList(bodyArea);
    }

    /**
     * Inserts a numbered list at the current cursor position.
     */
    private void insertNumberedList() {
        HtmlEditorUtils.insertNumberedList(bodyArea);
    }

    /**
     * Opens a file chooser to select and insert an image into the email body.
     */
    private void insertImage() {
        JnaFileChooser fileChooser = new JnaFileChooser();
        fileChooser.addFilter("Image Files", "jpg", "jpeg", "png", "gif", "bmp", "webp");
        boolean result = fileChooser.showOpenDialog(SwingUtilities.getWindowAncestor(this));

        if (result) {
            File imageFile = fileChooser.getSelectedFile();
            HtmlEditorUtils.insertImage(bodyArea, imageFile);
        }
    }

    /**
     * Opens a dialog to insert a hyperlink at the selected text or cursor position.
     */
    private void insertLink() {
        String url = JOptionPane.showInputDialog(this,
                "Enter URL:",
                "Insert Link",
                JOptionPane.PLAIN_MESSAGE);

        if (url != null && !url.trim().isEmpty()) {
            HtmlEditorUtils.insertLink(bodyArea, url);
        }
    }

    /**
     * Clears all formatting from the selected text.
     */
    private void clearFormatting() {
        HtmlEditorUtils.clearFormatting(bodyArea);
    }

    // ========== Getters and Setters ==========

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

    // Setters for reply/forward functionality
    public void setTo(String to) {
        toField.setText(to);
    }

    public void setCc(String cc) {
        ccField.setText(cc);
    }

    public void setBcc(String bcc) {
        bccField.setText(bcc);
    }

    public void setSubject(String subject) {
        subjectField.setText(subject);
    }

    public void setBody(String body) {
        bodyArea.setText(body);
    }

    public void addAttachment(File file) {
        if (file != null && file.exists()) {
            attachments.add(file);
            addAttachmentToPanel(file);
            revalidate();
            repaint();
        }
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