package services;

import models.Email;
import models.Folder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import protocols.imap.ImapClient;
import protocols.imap.ImapException;
import protocols.imap.ImapParser;
import utils.EmailUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * The ImapService class provides functionality for managing email retrieval
 * and operations using the IMAP protocol. It includes methods for connecting
 * to an IMAP server, fetching emails and folders, and performing actions such
 * as marking emails as read or deleting them.
 */
public class ImapService {
    private final ImapClient client;
    private String currentHost;
    private String currentUser;
    private boolean isConnected = false;
    private static ImapService instance;
    private final Logger logger = LoggerFactory.getLogger(ImapService.class);

    public ImapService() {
        this.client = new ImapClient();
    }

    /**
     * Connect và login
     */
    public void connect(String host, String username, String password) throws ImapException {
        connect(host, 993, username, password);
    }

    /**
     * Connect với custom port
     */
    public void connect(String host, int port, String username, String password) throws ImapException {
        try {
            client.connect(host, port);
            client.login(username, password);

            this.currentHost = host;
            this.currentUser = username;
            this.isConnected = true;
        } catch (ImapException e) {
            isConnected = false;
            throw e;
        }
    }

    /**
     * Fetch emails từ INBOX
     */
    public List<Email> fetchInbox() throws ImapException {
        return fetchFolder("INBOX");
    }

    /**
     * Fetch emails từ folder cụ thể
     */
    public List<Email> fetchFolder(String folderName) throws ImapException {
        if (!isConnected) {
            throw new ImapException("Not connected. Call connect() first.");
        }

        try {
            int messageCount = client.selectFolder(folderName);

            if (messageCount == 0) {
                return new ArrayList<>();
            }

            // Fetch tất cả emails
            List<Email> emails = client.fetchEmails(1, messageCount);

            // Sử dụng EmailUtils để filter và sort emails
            return EmailUtils.processEmails(emails);
        } catch (ImapException e) {
            throw new ImapException("Failed to fetch folder '" + folderName + "': " + e.getMessage(), e);
        }
    }

    /**
     * Fetch N emails mới nhất - ĐÃ SỬA: thêm sort để đảm bảo thứ tự nhất quán
     */
    public List<Email> fetchRecentEmails(String folderName, int count) throws ImapException {
        if (!isConnected) {
            throw new ImapException("Not connected. Call connect() first.");
        }

        try {
            int messageCount = client.selectFolder(folderName);

            if (messageCount == 0) {
                return new ArrayList<>();
            }

            // Tính start index
            int start = Math.max(1, messageCount - count + 1);

            List<Email> emails = client.fetchEmails(start, messageCount);

            // Sử dụng EmailUtils để filter và sort emails
            return EmailUtils.processEmails(emails);
        } catch (ImapException e) {
            throw new ImapException("Failed to fetch recent emails: " + e.getMessage(), e);
        }
    }

    /**
     * Fetch range của emails
     */
    public List<Email> fetchEmailRange(String folderName, int start, int end) throws ImapException {
        if (!isConnected) {
            throw new ImapException("Not connected. Call connect() first.");
        }

        try {
            client.selectFolder(folderName);
            return client.fetchEmails(start, end);
        } catch (ImapException e) {
            throw new ImapException("Failed to fetch email range: " + e.getMessage(), e);
        }
    }

    /**
     * Fetch body của email cụ thể
     */
    public ImapParser.EmailBody fetchEmailBody(String folderName, int messageNumber) throws ImapException {
        if (!isConnected) {
            throw new ImapException("Not connected. Call connect() first.");
        }

        try {
            if (!folderName.equals(client.getSelectedFolder())) {
                client.selectFolder(folderName);
            }

            return client.fetchEmailBody(messageNumber);
        } catch (ImapException e) {
            throw new ImapException("Failed to fetch email body: " + e.getMessage(), e);
        }
    }

    /**
     * Update flags cho email trong folder hiện tại
     */
    public void updateFlags(String folderName, int messageNumber, List<String> flags, boolean add) throws ImapException {
        if (!isConnected) {
            throw new ImapException("Not connected. Call connect() first.");
        }

        try {
            // Select folder nếu chưa select
            if (!folderName.equals(client.getSelectedFolder())) {
                client.selectFolder(folderName);
            }

            client.updateFlags(messageNumber, flags, add);
        } catch (ImapException e) {
            throw new ImapException("Failed to update flags: " + e.getMessage(), e);
        }
    }

    /**
     * Mark email as read
     */
    public void markAsRead(String folderName, int messageNumber, boolean read) throws ImapException {
        updateFlags(folderName, messageNumber, List.of("\\Seen"), read);
    }

    /**
     * Toggle star
     */
    public void toggleStar(String folderName, int messageNumber, boolean starred) throws ImapException {
        updateFlags(folderName, messageNumber, List.of("\\Flagged"), starred);
    }

    /**
     * Delete email (mark + expunge)
     */
    public void deleteEmail(String folderName, int messageNumber) throws ImapException {
        if (!isConnected) {
            throw new ImapException("Not connected. Call connect() first.");
        }

        try {
            if (!folderName.equals(client.getSelectedFolder())) {
                client.selectFolder(folderName);
            }

            // Mark as deleted
            client.markAsDeleted(messageNumber);

            // Permanently delete
            client.expunge();
        } catch (ImapException e) {
            throw new ImapException("Failed to delete email: " + e.getMessage(), e);
        }
    }

    /**
     * Move email to another folder (copy and delete)
     */


    /**
     * List tất cả folders
     */
    public List<Folder> listFolders() throws ImapException {
        if (!isConnected) {
            throw new ImapException("Not connected. Call connect() first.");
        }

        return client.listFolders();
    }

    /**
     * Search emails trên server theo keyword
     * Tìm trong toàn bộ email (subject, from, body)
     * 
     * @param folderName folder cần tìm kiếm
     * @param keyword từ khóa tìm kiếm
     * @return List emails tìm được
     */
    public List<Email> searchEmails(String folderName, String keyword) throws ImapException {
        if (!isConnected) {
            throw new ImapException("Not connected. Call connect() first.");
        }
        
        if (keyword == null || keyword.trim().isEmpty()) {
            return new ArrayList<>();
        }
        
        try {
            // Select folder nếu cần
            if (!folderName.equals(client.getSelectedFolder())) {
                client.selectFolder(folderName);
            }
            
            // Search để lấy message numbers
            List<Integer> messageNumbers = client.searchEmails(keyword);
            
            if (messageNumbers.isEmpty()) {
                logger.info("Search '{}' found 0 emails in folder '{}'", keyword, folderName);
                return new ArrayList<>();
            }
            
            logger.info("Search found {} message numbers in ENTIRE folder, fetching details...", messageNumbers.size());
            
            // Fetch tất cả emails cùng lúc (tối ưu hơn nhiều so với fetch từng email)
            List<Email> emails = client.fetchEmailsByNumbers(messageNumbers);
            
            logger.info("Search '{}' completed: found {} emails in ENTIRE folder '{}' (not limited by recent fetch)",
                       keyword, emails.size(), folderName);
            
            // Sử dụng EmailUtils để filter và sort emails
            return EmailUtils.processEmails(emails);
        } catch (ImapException e) {
            throw new ImapException("Failed to search emails: " + e.getMessage(), e);
        }
    }

    /**
     * Disconnect
     */
    public void disconnect() {
        try {
            if (isConnected) {
                client.logout();
            }
        } catch (ImapException e) {
            logger.error(e.getMessage(), e);
        } finally {
            client.close();
            isConnected = false;
        }
    }

    public void moveEmail(String fromFolder, int messageNumber, String targetFolder) throws ImapException {
        if (!isConnected) {
            throw new ImapException("Not connected. Call connect() first.");
        }

        // Tự động phát hiện Trash folder đúng tên
        String trashFolder = targetFolder;
        if ("Trash".equalsIgnoreCase(targetFolder)) {
            trashFolder = "[Gmail]/Trash";  // Gmail IMAP requires exact path
        }

        try {
            if (!fromFolder.equals(client.getSelectedFolder())) {
                client.selectFolder(fromFolder);
            }

            // Sao chép email sang thư mục Trash thật
            client.copyEmail(messageNumber, trashFolder);

            // Đánh dấu email đã xóa trong folder gốc và expunge
            client.markAsDeleted(messageNumber);
            client.expunge();

            logger.info("Moved email #{} from '{}' → '{}'", messageNumber, fromFolder, trashFolder);
        } catch (ImapException e) {
            throw new ImapException("Failed to move email: " + e.getMessage(), e);
        }
    }

    /**
     * Tự động phát hiện đúng thư mục Trash của server (Gmail, Outlook,...)
     */
    private String detectTrashFolder() {
        try {
            List<Folder> folders = listFolders();
            for (Folder f : folders) {
                String name = f.getFullPath().toLowerCase();
                if (name.contains("trash") || name.contains("deleted")) {
                    logger.debug("Detected trash folder: {}", f.getFullPath());
                    return f.getFullPath();
                }
            }
        } catch (Exception e) {
            logger.warn("Cannot auto-detect Trash folder, fallback to [Gmail]/Trash");
        }
        // Nếu không tìm thấy thì fallback
        return "[Gmail]/Trash";
    }



    /**
     * Expunge specific folder
     */
    public void expunge(String folderName) throws ImapException {
        if (!isConnected) {
            throw new ImapException("Not connected. Call connect() first.");
        }

        try {
            if (!folderName.equals(client.getSelectedFolder())) {
                client.selectFolder(folderName);
            }

            client.expunge();
            logger.info("Expunged folder: {}", folderName);
        } catch (ImapException e) {
            throw new ImapException("Failed to expunge: " + e.getMessage(), e);
        }
    }

    /**
     * Lấy tất cả email trong thư mục chỉ định (ví dụ "Trash")
     */
    public List<Email> fetchAllEmails(String folderName) throws ImapException {
        if (!isConnected) throw new ImapException("Not connected to IMAP");
        client.selectFolder(folderName);
        return client.fetchAllEmails(); // đã có sẵn trong ImapClient
    }


    /**
     * Debug: List all folders to find exact Trash folder name
     */
    public void printAllFolders() {
        try {
            List<Folder> folders = listFolders();
            System.out.println("\n========== ALL IMAP FOLDERS ==========");
            for (Folder folder : folders) {
                System.out.println("  📁 " + folder.getFullPath());
            }
            System.out.println("======================================\n");
        } catch (Exception e) {
            logger.error("Failed to list folders", e);
        }
    }


    // Getters
    public boolean isConnected() {
        return isConnected && client.isAuthenticated();
    }

    public String getCurrentHost() {
        return currentHost;
    }

    public String getCurrentUser() {
        return currentUser;
    }
}