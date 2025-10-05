package controllers;

import components.panels.dashboard.Inbox;
import models.Email;
import protocols.imap.ImapParser;
import raven.toast.Notifications;
import services.ImapService;

import javax.swing.*;
import java.util.List;

/**
 * Controller để kết nối ImapService với Inbox GUI
 */
public class ImapController {
    private final Inbox inboxPanel;
    private final ImapService imapService;
    private String currentFolder = "INBOX";

    public ImapController(Inbox inboxPanel) {
        this.inboxPanel = inboxPanel;
        this.imapService = new ImapService();
    }

    /**
     * Connect to IMAP server và load emails (synchronous version)
     * Dùng khi gọi từ background thread
     */
    public void connectSync(String host, String email, String password) throws Exception {
        // Connect to IMAP
        imapService.connect(host, email, password);

        // Fetch emails from INBOX
        List<Email> emails = imapService.fetchRecentEmails(currentFolder, 30);

        // Update UI on EDT
        SwingUtilities.invokeLater(() -> {
            inboxPanel.loadEmails(emails);
            System.out.println("✓ Connected successfully! Loaded " + emails.size() + " emails.");
        });
    }

    /**
     * Connect to IMAP server và load emails (asynchronous version)
     * Dùng khi gọi từ UI thread
     */
    public void connect(String host, String email, String password) {
        // Show loading state
        SwingUtilities.invokeLater(inboxPanel::showLoading);

        SwingWorker<List<Email>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<Email> doInBackground() throws Exception {
                // Connect to IMAP
                imapService.connect(host, email, password);

                // Fetch emails from INBOX
                return imapService.fetchRecentEmails(currentFolder, 30);
            }

            @Override
            protected void done() {
                try {
                    List<Email> emails = get();
                    inboxPanel.loadEmails(emails);
                    System.out.println("✓ Connected successfully! Loaded " + emails.size() + " emails.");
                } catch (Exception e) {
                    showError("Failed to connect: " + e.getMessage());
                }
            }
        };

        worker.execute();
    }

    /**
     * Load emails from specific folder
     */
    public void loadFolder(String folderName, int count) {
        SwingWorker<List<Email>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<Email> doInBackground() throws Exception {
                currentFolder = folderName;
                return imapService.fetchRecentEmails(folderName, count);
            }

            @Override
            protected void done() {
                try {
                    List<Email> emails = get();
                    inboxPanel.loadEmails(emails);
                } catch (Exception e) {
                    showError("Failed to load emails: " + e.getMessage());
                }
            }
        };

        worker.execute();
    }

    /**
     * Refresh current folder
     */
    public void refresh() {
        loadFolder(currentFolder, 30);
    }

    /**
     * Update email flags on server
     */
    public void updateEmailFlags(Email email) {
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                // TODO: Implement flag update on IMAP server
                // imapService.updateFlags(email.getMessageNumber(), email.getFlags());
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                } catch (Exception e) {
                    showError("Failed to update flags: " + e.getMessage());
                }
            }
        };

        worker.execute();
    }

    /**
     * Load email body khi user click vào email
     */
    public void loadEmailBody(Email email, String folderName) {
        SwingWorker<ImapParser.EmailBody, Void> worker = new SwingWorker<>() {
            @Override
            protected ImapParser.EmailBody doInBackground() throws Exception {
                return imapService.fetchEmailBody(folderName, email.getMessageNumber());
            }

            @Override
            protected void done() {
                try {
                    ImapParser.EmailBody emailBody = get();
                    email.setBody(emailBody.plainText);
                    email.setBodyHtml(emailBody.html);
                    email.setHtml(!emailBody.html.isEmpty());
                    inboxPanel.updateEmailBody(email);
                } catch (Exception e) {
                    showError("Failed to load email body: " + e.getMessage());
                }
            }
        };

        worker.execute();
    }

    /**
     * Disconnect from IMAP
     */
    public void disconnect() {
        imapService.disconnect();
    }

    /**
     * Check if connected
     */
    public boolean isConnected() {
        return imapService.isConnected();
    }

    /**
     * Show error dialog
     */
    private void showError(String message) {
        Notifications.getInstance().show(Notifications.Type.ERROR, message);
    }
}