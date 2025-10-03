package services;

import models.Email;
import models.Folder;
import protocols.imap.ImapClient;
import protocols.imap.ImapException;

import java.util.ArrayList;
import java.util.List;

public class ImapService {
    private ImapClient client;
    private String currentHost;
    private String currentUser;
    private boolean isConnected = false;

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
            return client.fetchEmails(1, messageCount);
        } catch (ImapException e) {
            throw new ImapException("Failed to fetch folder '" + folderName + "': " + e.getMessage(), e);
        }
    }

    /**
     * Fetch N emails mới nhất
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

            return client.fetchEmails(start, messageCount);
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
     * List tất cả folders
     */
    public List<Folder> listFolders() throws ImapException {
        if (!isConnected) {
            throw new ImapException("Not connected. Call connect() first.");
        }

        return client.listFolders();
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
            e.printStackTrace();
        } finally {
            client.close();
            isConnected = false;
        }
    }

    /**
     * Test connection
     */
    public boolean testConnection(String host, String username, String password) {
        try {
            ImapClient testClient = new ImapClient();
            testClient.connect(host);
            testClient.login(username, password);
            testClient.logout();
            testClient.close();
            return true;
        } catch (Exception e) {
            return false;
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