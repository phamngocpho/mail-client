package controllers;

import components.panels.dashboard.Inbox;
import models.Email;
import protocols.imap.ImapParser;
import raven.toast.Notifications;
import services.ImapService;

import javax.swing.*;
import java.util.ArrayList;
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
        List<Email> emails = imapService.fetchRecentEmails(currentFolder, 11);

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
                return imapService.fetchRecentEmails(currentFolder, 11);
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
        loadFolder(currentFolder, 11);
    }

    /**
     * Update email flags on server
     */
    public void updateEmailFlags(Email email) {
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                // Lấy flags từ email object
                List<String> flagsToAdd = new ArrayList<>();
                List<String> flagsToRemove = new ArrayList<>();

                // Check which flags changed
                if (email.hasFlag("Flagged")) {
                    flagsToAdd.add("\\Flagged");
                } else {
                    flagsToRemove.add("\\Flagged");
                }

                if (email.hasFlag("Seen")) {
                    flagsToAdd.add("\\Seen");
                } else {
                    flagsToRemove.add("\\Seen");
                }

                // Update trên server
                if (!flagsToAdd.isEmpty()) {
                    imapService.updateFlags(currentFolder, email.getMessageNumber(), flagsToAdd, true);
                }
                if (!flagsToRemove.isEmpty()) {
                    imapService.updateFlags(currentFolder, email.getMessageNumber(), flagsToRemove, false);
                }

                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    System.out.println("✓ Flags synced with server");
                } catch (Exception e) {
                    showError("Failed to update flags: " + e.getMessage());
                }
            }
        };

        worker.execute();
    }

    /**
     * Mark email as read/unread
     */
    public void markAsRead(Email email, boolean read) {
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                imapService.markAsRead(currentFolder, email.getMessageNumber(), read);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    // Update local email object
                    if (read) {
                        email.addFlag("Seen");
                    } else {
                        email.removeFlag("Seen");
                    }
                    // Refresh UI
                    inboxPanel.refreshEmailRow(email);
                } catch (Exception e) {
                    showError("Failed to mark as read: " + e.getMessage());
                }
            }
        };

        worker.execute();
    }

    /**
     * Delete email
     */
    public void deleteEmail(Email email) {
        // Confirm dialog
        int result = JOptionPane.showConfirmDialog(
                null,
                "Are you sure you want to delete this email?",
                "Confirm Delete",
                JOptionPane.YES_NO_OPTION
        );

        if (result != JOptionPane.YES_OPTION) {
            return;
        }

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                imapService.deleteEmail(currentFolder, email.getMessageNumber());
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    // Refresh to remove from list
                    refresh();
                    Notifications.getInstance().show(Notifications.Type.SUCCESS, "Email deleted");
                } catch (Exception e) {
                    showError("Failed to delete email: " + e.getMessage());
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