package controllers;

import components.panels.dashboard.Inbox;
import models.Email;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import protocols.imap.ImapParser;
import raven.toast.Notifications;
import services.ImapService;
import utils.Constants;

import javax.swing.*;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The ImapController class is responsible for managing the interaction between
 * the IMAP email server and the application's inbox panel. It provides methods
 * for connecting to the server, loading emails, managing folders, and performing
 * various operations on email messages.
 */
public class ImapController {
    private final Inbox inboxPanel;
    private final ImapService imapService;
    private String currentFolder;
    private static final Logger logger = LoggerFactory.getLogger(ImapController.class);
    private final List<Inbox> registeredInboxes = new ArrayList<>();
    private final Map<String, List<Email>> emailCache = new HashMap<>();
    private final Map<String, Long> cacheTimestamps = new HashMap<>();

    public ImapController(Inbox inboxPanel, String folderName) {
        this.inboxPanel = inboxPanel;
        this.imapService = new ImapService();
        this.currentFolder = folderName;
    }

    public void registerInbox(Inbox inbox) {
        if (!registeredInboxes.contains(inbox)) {
            registeredInboxes.add(inbox);
        }
    }

    public void unregisterInbox(Inbox inbox) {
        registeredInboxes.remove(inbox);
    }

    private void notifyAllInboxes(List<Email> emails, String folder) {
        for (Inbox inbox : registeredInboxes) {
            if (inbox.getFolderName().equals(folder)) {
                SwingUtilities.invokeLater(() -> inbox.loadEmails(emails));
            }
        }
    }

    public void setCurrentFolder(String folderName) {
        this.currentFolder = folderName;
    }

    /**
     * Kiểm tra xem cache còn hợp lệ không
     */
    private boolean isCacheValid (String folder) {
        if (!emailCache.containsKey(folder)) {
            return false;
        }

        Long timestamp = cacheTimestamps.get(folder);
        if (timestamp == null) {
            return false;
        }

        long now = System.currentTimeMillis();
        boolean valid = (now - timestamp) < Constants.CACHE_DURATION;

        logger.debug("Cache for folder '{}' is {}", folder, valid ? "valid" : "expired");
        return valid;
    }

    /**
     * Lưu emails vào cache
     */
    private void cacheEmails(String folder, List<Email> emails) {
        emailCache.put(folder, new ArrayList<>(emails)); // Copy để tránh reference
        cacheTimestamps.put(folder, System.currentTimeMillis());
        logger.debug("Cached {} emails for folder '{}'", emails.size(), folder);
    }

    /**
     * Lấy emails từ cache
     */
    private List<Email> getCachedEmails(String folder) {
        List<Email> cached = emailCache.get(folder);
        return cached != null ? new ArrayList<>(cached) : null; // Copy để tránh modification
    }

    /**
     * Connect to IMAP server và load emails (synchronous version)
     * Dùng khi gọi từ background thread
     */
    public void connectSync(String host, String email, String password) throws Exception {
        // Connect to IMAP
        imapService.connect(host, email, password);

        // Fetch emails from INBOX
        List<Email> emails = imapService.fetchRecentEmails(currentFolder, Constants.EMAILS_PER_PAGE);

        // Update UI on EDT
        notifyAllInboxes(emails, currentFolder);
    }

    /**
     * Connect to IMAP server và load emails (asynchronous version)
     * Dùng khi gọi từ UI thread
     */
    public void connect(String host, String email, String password) {
        // Show loading state
        if (inboxPanel != null) {
            SwingUtilities.invokeLater(inboxPanel::showLoading);
        }

        SwingWorker<List<Email>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<Email> doInBackground() throws Exception {
                // Connect to IMAP
                imapService.connect(host, email, password);

                // Fetch emails from INBOX
                return imapService.fetchRecentEmails(currentFolder, Constants.EMAILS_PER_PAGE);
            }

            @Override
            protected void done() {
                try {
                    List<Email> emails = get();
                    notifyAllInboxes(emails, currentFolder);
                } catch (Exception e) {
                    showError("Failed to connect: " + e.getMessage());
                }
            }
        };

        worker.execute();
    }

    /**
     * Load emails from a specific folder
     */
    public void loadFolder(String folderName, int count) {
        // Kiểm tra cache
        if (isCacheValid(folderName)) {
            List<Email> cachedEmails = getCachedEmails(folderName);
            if (cachedEmails != null) {
                logger.info("Using cached emails for folder: {}", folderName);
                notifyAllInboxes(cachedEmails, folderName);
                return; // Không cần fetch từ server
            }
        }

        // Nếu không có cache hoặc cache hết hạn, fetch từ server
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
                    cacheEmails(folderName, emails); // Lưu vào cache
                    notifyAllInboxes(emails, folderName);
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
        // Xóa cache khi refresh
        emailCache.remove(currentFolder);
        cacheTimestamps.remove(currentFolder);
        logger.info("Cleared cache for folder: {}", currentFolder);

        SwingWorker<List<Email>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<Email> doInBackground() throws Exception {
                return imapService.fetchRecentEmails(currentFolder, Constants.EMAILS_PER_PAGE);
            }

            @Override
            protected void done() {
                try {
                    List<Email> emails = get();
                    cacheEmails(currentFolder, emails); // Lưu lại cache mới
                    notifyAllInboxes(emails, currentFolder);
                } catch (Exception e) {
                    showError("Failed to refresh: " + e.getMessage());
                }
            }
        };

        worker.execute();
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
                    logger.info("Flags synced with server");
                    refresh();
                } catch (Exception e) {
                    showError("Failed to update flags: " + e.getMessage());
                    logger.error("Failed to update flags: {}", e.getMessage());
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
                    // Refresh to remove from the list
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

                    logger.debug("Email body loaded. Attachments count: {}", emailBody.attachments.size());
                    logger.debug("Email body loaded. Attachments count: {}", emailBody.attachments.size());
                    logger.debug("Plain text preview (first 200 chars): {}",
                            emailBody.plainText.substring(0, Math.min(200, emailBody.plainText.length())));
                    logger.debug("HTML preview (first 200 chars): {}",
                            emailBody.html.substring(0, Math.min(200, emailBody.html.length())));

                    // Tạo thư mục attachments trong project nếu chưa tồn tại
                    File attachmentDir = new File("attachments");
                    if (!attachmentDir.exists()) {
                        if (!attachmentDir.mkdirs()) {
                            logger.warn("Failed to create directory: {}", attachmentDir.getAbsolutePath());
                            Notifications.getInstance().show(Notifications.Type.WARNING, "Failed to create directory for attachments");
                        }
                    }

                    for (ImapParser.Attachment att : emailBody.attachments) {
                        try {
                            String decodedFilename = ImapParser.decodeFilename(att.filename);
                            String safeFilename = sanitizeFilename(decodedFilename);

                            logger.debug("Original: {} → Decoded: {} → Safe: {}",
                                    att.filename, decodedFilename, safeFilename);

                            File attachmentFile = new File(attachmentDir, safeFilename);

                            // Chỉ ghi file nếu chưa tồn tại
                            if (!attachmentFile.exists()) {
                                // Đảm bảo tên file unique nếu cần
                                attachmentFile = getAttachmentFile(attachmentDir, safeFilename);

                                try (FileOutputStream fos = new FileOutputStream(attachmentFile)) {
                                    fos.write(att.data);
                                }

                                logger.debug("Saved NEW attachment: {} ({} bytes) to: {}",
                                        safeFilename, att.data.length, attachmentFile.getAbsolutePath());
                            } else {
                                logger.debug("Attachment ALREADY EXISTS, reusing: {}", attachmentFile.getAbsolutePath());
                            }

                            email.addAttachment(attachmentFile);

                        } catch (Exception e) {
                            logger.error("Failed to save attachment: {}", att.filename, e);
                        }
                    }

                    logger.debug("Total attachments added to email: {}", email.getAttachments().size());

                    // Update body cho tất cả inbox đang hiển thị email này
                    for (Inbox inbox : registeredInboxes) {
                        SwingUtilities.invokeLater(() -> inbox.updateEmailBody(email));
                    }

                } catch (Exception e) {
                    showError("Failed to load email body: " + e.getMessage());
                    logger.error("Error loading email body", e);
                }
            }
        };

        worker.execute();
    }

    /**
     * Generates a File object pointing to a new attachment file in the specified directory.
     * Ensures the file name is unique by appending a counter to the base name if a file
     * with the same name already exists.
     *
     * @param attachmentDir the directory where the attachment file should be stored
     * @param safeFilename the sanitized filename to be used for the attachment
     * @return a File object representing the unique attachment file
     */
    private static File getAttachmentFile(File attachmentDir, String safeFilename) {
        File attachmentFile = new File(attachmentDir, safeFilename);

        // Đảm bảo tên tệp là duy nhất
        int counter = 1;
        String baseName;
        int dotIndex = safeFilename.lastIndexOf('.');
        if (dotIndex > 0) {
            baseName = safeFilename.substring(0, dotIndex);
            String ext = safeFilename.substring(dotIndex);
            while (attachmentFile.exists()) {
                attachmentFile = new File(attachmentDir, baseName + "_" + counter + ext);
                counter++;
            }
        }
        return attachmentFile;
    }

    /**
     * Sanitize filename để tránh lỗi trên Windows
     * - Loại bỏ ký tự không hợp lệ: < > : " / \ | ? *
     * - Giới hạn độ dài tên file
     */
    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "attachment";
        }

        // Loại bỏ ký tự không hợp lệ trên Windows
        String safe = filename.replaceAll("[<>:\"/\\\\|?*]", "_");

        // Loại bỏ khoảng trắng đầu/cuối
        safe = safe.trim();

        // Giới hạn độ dài (Windows max 255 chars, để 100 cho an toàn)
        if (safe.length() > 100) {
            // Giữ extension
            int dotIndex = safe.lastIndexOf('.');
            if (dotIndex > 0) {
                String name = safe.substring(0, dotIndex);
                String ext = safe.substring(dotIndex);
                safe = name.substring(0, Math.min(name.length(), 95)) + ext;
            } else {
                safe = safe.substring(0, 100);
            }
        }

        // Nếu rỗng sau khi sanitize, dùng tên mặc định
        if (safe.isEmpty()) {
            return "attachment";
        }

        return safe;
    }

    /**
     * Disconnect from IMAP
     */
    public void disconnect() {
        imapService.disconnect();
        emailCache.clear();
        cacheTimestamps.clear();
        logger.info("Cleared all email cache");
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

    /**
     * Update inbox panel with emails và log thông tin
     * Method chung để tránh duplicate code
     */
    private void updateInboxWithEmails(List<Email> emails) {
        SwingUtilities.invokeLater(() -> {
            notifyAllInboxes(emails, currentFolder);
            logger.info("Connected successfully! Loaded {} emails", emails.size());
        });
    }
}