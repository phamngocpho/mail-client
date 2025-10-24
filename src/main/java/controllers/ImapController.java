package controllers;

import components.panels.dashboard.Inbox;
import models.Email;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import protocols.imap.ImapParser;
import raven.toast.Notifications;
import services.ImapService;
import utils.AsyncUtils;
import utils.Constants;
import utils.EmailCacheManager;
import utils.EmailUtils;
import utils.EncodingUtils;

import javax.swing.*;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
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
    
    // Cache manager cho email body và attachments (lưu trên disk)
    private static EmailCacheManager cacheManager = null;

    public ImapController(Inbox inboxPanel, String folderName) {
        this.inboxPanel = inboxPanel;
        this.imapService = new ImapService();
        this.currentFolder = folderName;
        
        // Initialize cache manager (singleton)
        if (cacheManager == null) {
            cacheManager = new EmailCacheManager();
            logger.info("Initialized EmailCacheManager - {}", cacheManager.getCacheStats());
        }
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
                    AsyncUtils.showError("connect", e);
                }
            }
        };

        worker.execute();
    }

    /**
     * Try to auto-connect using saved credentials from ConfigUtils
     * If credentials are available, connects automatically in background
     * If connection fails or no credentials, invokes appropriate callback
     * 
     * @param folderName The folder to load after connection
     * @param onLoading Callback to show loading UI
     * @param onSuccess Callback when connection succeeds
     * @param onError Callback when connection fails (receives error message)
     */
    public void tryAutoConnect(String folderName, 
                               Runnable onLoading, 
                               Runnable onSuccess, 
                               java.util.function.Consumer<String> onError) {
        if (!utils.ConfigUtils.hasValidCredentials()) {
            logger.info("No saved credentials found");
            onError.accept("NO_CREDENTIALS");
            return;
        }
        
        logger.info("Found saved credentials, attempting auto-connect...");
        onLoading.run();
        
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                String host = utils.ConfigUtils.getImapHost();
                String email = utils.ConfigUtils.getEmail();
                String password = utils.ConfigUtils.getAppPassword();
                
                // Connect to IMAP
                connectSync(host, email, password);
                
                // Auto-configure SMTP with same credentials
                controllers.SmtpController smtpController = controllers.SmtpController.getInstance();
                smtpController.configureFromImap(host, email, password);
                
                return null;
            }
            
            @Override
            protected void done() {
                try {
                    get(); // Check for exceptions
                    
                    logger.info("Auto-connect successful");
                    setCurrentFolder(folderName);
                    loadFolder(folderName, Constants.EMAILS_PER_PAGE);
                    onSuccess.run();
                    
                } catch (Exception e) {
                    logger.error("Auto-connect failed: {}", e.getMessage(), e);
                    
                    String errorMsg = e.getCause() != null ? 
                                     e.getCause().getMessage() : 
                                     e.getMessage();
                    onError.accept(errorMsg);
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
                    AsyncUtils.showError("load emails", e);
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
                    AsyncUtils.showError("refresh", e);
                }
            }
        };

        worker.execute();
    }

    /**
     * Search emails trên server
     * Tìm kiếm trong toàn bộ email (subject, from, body)
     */
    /**
     * Perform search with automatic decision: server search if connected, local filter otherwise
     * 
     * @param query Search query
     * @param folder Folder to search in
     * @param onSuccess Callback with search results
     */
    public void performSearch(String query, String folder, java.util.function.Consumer<List<Email>> onSuccess) {
        // Empty query - return all cached emails
        if (query == null || query.trim().isEmpty()) {
            List<Email> allEmails = getCachedEmails(folder);
            if (allEmails != null) {
                onSuccess.accept(allEmails);
            } else {
                onSuccess.accept(new ArrayList<>());
            }
            logger.debug("Empty search query, returning all cached emails");
            return;
        }
        
        // Server search if connected
        if (isConnected()) {
            logger.info("Performing server search for: '{}'", query);
            
            SwingWorker<List<Email>, Void> worker = new SwingWorker<>() {
                @Override
                protected List<Email> doInBackground() throws Exception {
                    return imapService.searchEmails(folder, query);
                }
                
                @Override
                protected void done() {
                    try {
                        List<Email> results = get();
                        logger.info("Server search found {} results", results.size());
                        SwingUtilities.invokeLater(() -> onSuccess.accept(results));
                    } catch (Exception e) {
                        logger.error("Server search failed: {}", e.getMessage(), e);
                        AsyncUtils.showError("search emails", e);
                    }
                }
            };
            
            worker.execute();
        } 
        // Local filter if not connected
        else {
            logger.debug("Not connected, performing local search");
            List<Email> cached = getCachedEmails(folder);
            if (cached != null) {
                List<Email> filtered = EmailUtils.filterBySearchQuery(cached, query);
                onSuccess.accept(filtered);
            } else {
                onSuccess.accept(new ArrayList<>());
            }
        }
    }
    
    /**
     * Search emails on server (legacy method, kept for backward compatibility)
     * 
     * @param keyword Search keyword
     * @param requestingInbox The inbox requesting the search
     * @deprecated Use performSearch() instead for better flexibility
     */
    @Deprecated
    public void searchEmails(String keyword, Inbox requestingInbox) {
        if (keyword == null || keyword.trim().isEmpty()) {
            // Nếu keyword rỗng, load lại emails bình thường
            loadFolder(currentFolder, Constants.EMAILS_PER_PAGE);
            return;
        }
        
        SwingWorker<List<Email>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<Email> doInBackground() throws Exception {
                return imapService.searchEmails(currentFolder, keyword);
            }

            @Override
            protected void done() {
                try {
                    List<Email> emails = get();
                    // Sử dụng loadSearchResults() để không filter lại local
                    // (server đã tìm trong body rồi)
                    SwingUtilities.invokeLater(() -> requestingInbox.loadSearchResults(emails));
                    logger.debug("Loaded {} search results to UI", emails.size());
                } catch (Exception e) {
                    AsyncUtils.showError("search emails", e);
                }
            }
        };

        worker.execute();
    }

    /**
     * Update email flags on server
     */
    public void updateEmailFlags(Email email) {
        AsyncUtils.executeVoidAsync(
            () -> {
                try {
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
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            },
            () -> {
                logger.info("Flags synced with server");
                refresh();
            },
            e -> AsyncUtils.showError("update flags", e)
        );
    }

    /**
     * Mark email as read/unread
     */
    public void markAsRead(Email email, boolean read) {
        AsyncUtils.executeVoidAsync(
            () -> {
                try {
                    imapService.markAsRead(currentFolder, email.getMessageNumber(), read);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            },
            () -> {
                // Update local email object
                if (read) {
                    email.addFlag("Seen");
                } else {
                    email.removeFlag("Seen");
                }
                // Refresh UI
                inboxPanel.refreshEmailRow(email);
            },
            e -> AsyncUtils.showError("mark as read", e)
        );
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

        AsyncUtils.executeVoidAsync(
            () -> {
                try {
                    imapService.deleteEmail(currentFolder, email.getMessageNumber());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            },
            () -> {
                // Refresh to remove from the list
                refresh();
                Notifications.getInstance().show(Notifications.Type.SUCCESS, "Email deleted");
            },
            e -> AsyncUtils.showError("delete email", e)
        );
    }

    /**
     * Load email body khi user click vào email
     */
    public void loadEmailBody(Email email, String folderName) {
        int msgNum = email.getMessageNumber();
        
        // Kiểm tra cache trước (từ disk)
        if (cacheManager.hasBody(msgNum)) {
            logger.info("Using cached body from disk for message #{}", msgNum);
            
            // Restore attachments từ cache
            List<File> cachedAttachments = cacheManager.getAttachments(msgNum);
            boolean allAttachmentsExist = true;
            
            logger.debug("Checking cached attachments for message #{}: {} files", 
                        msgNum, cachedAttachments != null ? cachedAttachments.size() : 0);
            
            // Check xem tất cả attachments có tồn tại không
            if (cachedAttachments != null && !cachedAttachments.isEmpty()) {
                int existCount = 0;
                int missingCount = 0;
                for (File file : cachedAttachments) {
                    if (!file.exists()) {
                        logger.warn("Cached attachment file MISSING: {}", file.getAbsolutePath());
                        allAttachmentsExist = false;
                        missingCount++;
                    } else {
                        logger.debug("Cached attachment file EXISTS: {}", file.getName());
                        existCount++;
                    }
                }
                logger.info("Attachment check for message #{}: {} exist, {} missing", msgNum, existCount, missingCount);
            }
            
            // Nếu có attachment bị mất, invalidate cache và fetch lại
            if (!allAttachmentsExist && !cachedAttachments.isEmpty()) {
                logger.warn("Some attachments missing, invalidating cache and re-fetching message #{}", msgNum);
                cacheManager.clearMessage(msgNum);
                // Không return, sẽ fetch từ server ở dưới
            } else {
                // Cache hợp lệ, restore body và attachments
                email.setBody(cacheManager.getBody(msgNum));
                email.setBodyHtml(cacheManager.getBodyHtml(msgNum));
                email.setHtml(cacheManager.getBodyHtml(msgNum) != null && !cacheManager.getBodyHtml(msgNum).isEmpty());
                
                // Clear attachments cũ trước khi restore
                email.clearAttachments();
                
                if (cachedAttachments != null) {
                    for (File file : cachedAttachments) {
                        email.addAttachment(file);
                    }
                    logger.debug("Restored {} attachments from disk cache", cachedAttachments.size());
                }
                
                // Update UI - validate email vẫn được chọn
                for (Inbox inbox : registeredInboxes) {
                    SwingUtilities.invokeLater(() -> {
                        // Chỉ update nếu email này vẫn đang được hiển thị
                        inbox.updateEmailBodyIfCurrent(email);
                    });
                }
                return;
            }
        }
        
        // Nếu không có cache, fetch từ server
        logger.info("Fetching body from server for message #{}", msgNum);
        
        // Clear attachments trước khi fetch
        email.clearAttachments();
        
        AsyncUtils.executeAsync(
            () -> {
                try {
                    return imapService.fetchEmailBody(folderName, email.getMessageNumber());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            },
            emailBody -> {
                email.setBody(emailBody.plainText);
                email.setBodyHtml(emailBody.html);
                email.setHtml(!emailBody.html.isEmpty());
                
                // Lưu vào cache (disk)
                cacheManager.cacheBody(msgNum, emailBody.plainText, emailBody.html);

                logger.debug("Email body loaded from server. Attachments count: {}", emailBody.attachments.size());
                logger.debug("Plain text: {} chars", emailBody.plainText != null ? emailBody.plainText.length() : 0);
                logger.debug("HTML: {} chars", emailBody.html != null ? emailBody.html.length() : 0);

                // Tạo thư mục attachments trong cache directory để đồng nhất với cache
                // Lấy đường dẫn từ location của class file
                File attachmentDir;
                try {
                    Path classPath = Paths.get(ImapController.class.getProtectionDomain()
                            .getCodeSource().getLocation().toURI());
                    
                    Path baseDir;
                    if (classPath.toString().contains("target" + File.separator + "classes")) {
                        baseDir = classPath.getParent().getParent();
                    } else if (classPath.toString().endsWith(".jar")) {
                        baseDir = classPath.getParent();
                    } else {
                        baseDir = Paths.get(System.getProperty("user.dir"), "Mail Client");
                    }
                    
                    attachmentDir = baseDir.resolve(".mailclient/cache/attachments").toFile();
                } catch (Exception e) {
                    logger.error("Error determining attachment directory: {}", e.getMessage());
                    // Fallback to old location
                    File currentDir = new File(System.getProperty("user.dir"));
                    File projectRoot = currentDir.getName().equals("Mail Client") ? currentDir : new File(currentDir, "Mail Client");
                    attachmentDir = new File(projectRoot, "attachments");
                }
                
                if (!attachmentDir.exists()) {
                    if (!attachmentDir.mkdirs()) {
                        logger.warn("Failed to create directory: {}", attachmentDir.getAbsolutePath());
                        Notifications.getInstance().show(Notifications.Type.WARNING, "Failed to create directory for attachments");
                    } else {
                        logger.info("Created attachments directory: {}", attachmentDir.getAbsolutePath());
                    }
                } else {
                    logger.debug("Using existing attachments directory: {}", attachmentDir.getAbsolutePath());
                }

                for (ImapParser.Attachment att : emailBody.attachments) {
                    try {
                        String decodedFilename = EncodingUtils.decodeFilename(att.filename);
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
                
                // Lưu attachments vào cache (disk)
                cacheManager.cacheAttachments(msgNum, email.getAttachments());
                logger.debug("Cached body and {} attachments to disk for message #{}", email.getAttachments().size(), msgNum);

                // Update body cho tất cả inbox đang hiển thị email này
                // CHỈ update nếu email vẫn đang được chọn
                for (Inbox inbox : registeredInboxes) {
                    SwingUtilities.invokeLater(() -> inbox.updateEmailBodyIfCurrent(email));
                }
            },
            e -> AsyncUtils.showError("load email body", e)
        );
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
        // Note: Không clear cacheManager vì nó lưu trên disk để dùng lại khi mở app
        logger.info("Cleared email list cache (body cache retained on disk)");
    }

    /**
     * Check if connected
     */
    public boolean isConnected() {
        return imapService.isConnected();
    }


}