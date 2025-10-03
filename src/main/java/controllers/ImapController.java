package controllers;

import components.panels.dashboard.Inbox;
import models.Email;
import protocols.imap.ImapException;
import services.ImapService;

import javax.swing.*;
import java.util.List;

/**
 * Controller để kết nối ImapService với Inbox GUI
 */
public class ImapController {
    private Inbox inboxPanel;
    private ImapService imapService;
    private String currentFolder = "INBOX";

    public ImapController(Inbox inboxPanel) {
        this.inboxPanel = inboxPanel;
        this.imapService = new ImapService();
    }

    /**
     * Connect to IMAP server và load emails
     */
    public void connect(String host, String email, String password) {
        // Show loading state
        SwingUtilities.invokeLater(() -> inboxPanel.showLoading());

        SwingWorker<List<Email>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<Email> doInBackground() throws Exception {
                // Connect to IMAP
                imapService.connect(host, email, password);

                // Fetch emails from INBOX
                return imapService.fetchRecentEmails(currentFolder, 100);
            }

            @Override
            protected void done() {
                try {
                    List<Email> emails = get();
                    inboxPanel.loadEmails(emails);

                    System.out.println("✓ Connected successfully! Loaded " + emails.size() + " emails.");
                } catch (Exception e) {
                    showError("Failed to connect: " + e.getMessage());
                    e.printStackTrace();
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
                    e.printStackTrace();
                }
            }
        };

        worker.execute();
    }

    /**
     * Refresh current folder
     */
    public void refresh() {
        loadFolder(currentFolder, 100);
    }

    /**
     * Update email flags on server
     */
    public void updateEmailFlags(Email email) {
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
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
        SwingWorker<String, Void> worker = new SwingWorker<>() {
            @Override
            protected String doInBackground() throws Exception {
                return imapService.fetchEmailBody(folderName, email.getMessageNumber());
            }

            @Override
            protected void done() {
                try {
                    String body = get();
                    email.setBody(body);
                    // Trigger UI update
                    inboxPanel.updateEmailBody(email);
                } catch (Exception e) {
                    showError("Failed to load email body: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        };

        worker.execute();
    }

    /**
     * Disconnect from IMAP
     */
    public void disconnect() {
        if (imapService != null) {
            imapService.disconnect();
        }
    }

    /**
     * Check if connected
     */
    public boolean isConnected() {
        return imapService != null && imapService.isConnected();
    }

    /**
     * Show error dialog
     */
    private void showError(String message) {
        JOptionPane.showMessageDialog(
                inboxPanel,
                message,
                "Error",
                JOptionPane.ERROR_MESSAGE
        );
    }
}