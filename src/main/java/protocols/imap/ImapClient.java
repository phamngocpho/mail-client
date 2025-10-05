package protocols.imap;

import models.Email;
import models.Folder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.Constants;
import utils.NetworkUtils;

import javax.net.ssl.SSLSocket;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public class ImapClient {
    private SSLSocket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private int tagCounter = 0;
    private boolean connected = false;
    private boolean authenticated = false;
    private String selectedFolder = null;
    private static final Logger logger = LoggerFactory.getLogger(ImapClient.class);

    /**
     * Connect to IMAP server với SSL
     */
    public void connect(String host, int port) throws ImapException {
        try {
            System.out.println("Connecting to " + host + ":" + port);
            socket = NetworkUtils.createSSLSocket(host, port);
            reader = NetworkUtils.createReader(socket);
            writer = NetworkUtils.createWriter(socket);

            // Đọc greeting từ server
            String greeting = readResponse();
            System.out.println("← " + greeting);

            if (!greeting.startsWith("* OK")) {
                throw new ImapException("Invalid server greeting: " + greeting);
            }

            connected = true;
        } catch (IOException e) {
            throw new ImapException("Connection failed: " + e.getMessage(), e);
        }
    }

    /**
     * Connect với default SSL port (993)
     */
    public void connect(String host) throws ImapException {
        connect(host, Constants.IMAP_SSL_PORT);
    }

    /**
     * Login với username và password
     */
    public void login(String username, String password) throws ImapException {
        if (!connected) {
            throw new ImapException("Not connected to server");
        }

        String tag = nextTag();
        String command = String.format("%s LOGIN %s %s", tag, quote(username), quote(password));

        // Log command (ẩn password)
        System.out.println("→ " + tag + " LOGIN " + quote(username) + " ****");

        sendCommand(command);
        String response = readFullResponse(tag);

        if (ImapParser.isError(response, tag)) {
            throw new ImapException(command, response, "Login failed");
        }

        authenticated = true;
        System.out.println("✓ Login successful");
    }

    /**
     * Select folder để đọc emails
     */
    public int selectFolder(String folderName) throws ImapException {
        if (!authenticated) {
            throw new ImapException("Not authenticated");
        }

        String tag = nextTag();
        String command = String.format("%s SELECT %s", tag, quote(folderName));

        System.out.println("→ " + command);
        sendCommand(command);
        String response = readFullResponse(tag);

        if (ImapParser.isError(response, tag)) {
            throw new ImapException(command, response, "Failed to select folder: " + folderName);
        }

        selectedFolder = folderName;
        int messageCount = ImapParser.parseMessageCount(response);
        System.out.println("✓ Selected folder: " + folderName + " (" + messageCount + " messages)");

        return messageCount;
    }

    /**
     * Fetch emails từ folder đã select
     * @param start Message number bắt đầu (1-indexed)
     * @param end Message number kết thúc
     */
    public List<Email> fetchEmails(int start, int end) throws ImapException {
        if (selectedFolder == null) {
            throw new ImapException("No folder selected");
        }

        List<Email> emails;
        String tag = nextTag();

        // Fetch headers: FROM, TO, SUBJECT, DATE, FLAGS
        String command = String.format("%s FETCH %d:%d (FLAGS BODY[HEADER.FIELDS (FROM TO SUBJECT DATE MESSAGE-ID)] BODY.PEEK[])", tag, start, end);

        System.out.println("→ " + command);
        sendCommand(command);
        String response = readFullResponse(tag);

        if (ImapParser.isError(response, tag)) {
            throw new ImapException(command, response, "Failed to fetch emails");
        }

        // Parse từng email từ response
        emails = parseFetchResponse(response);
        System.out.println("✓ Fetched " + emails.size() + " emails");

        return emails;
    }

    /**
     * Fetch body của một email cụ thể
     */
    public ImapParser.EmailBody fetchEmailBody(int messageNumber) throws ImapException {
        if (selectedFolder == null) {
            throw new ImapException("No folder selected");
        }

        String tag = nextTag();
        String command = String.format("%s FETCH %d BODY[TEXT]", tag, messageNumber);

        System.out.println("→ " + command);
        sendCommand(command);
        String response = readFullResponse(tag);

        if (ImapParser.isError(response, tag)) {
            throw new ImapException(command, response, "Failed to fetch email body");
        }

        return ImapParser.parseEmailBody(response);
    }

    /**
     * Fetch all emails từ folder
     */
    public List<Email> fetchAllEmails() throws ImapException {
        int count = selectFolder(selectedFolder != null ? selectedFolder : "INBOX");
        if (count == 0) return new ArrayList<>();
        return fetchEmails(1, count);
    }

    /**
     * List tất cả folders
     */
    public List<Folder> listFolders() throws ImapException {
        if (!authenticated) {
            throw new ImapException("Not authenticated");
        }

        String tag = nextTag();
        String command = String.format("%s LIST \"\" \"*\"", tag);

        System.out.println("→ " + command);
        sendCommand(command);
        String response = readFullResponse(tag);

        if (ImapParser.isError(response, tag)) {
            throw new ImapException(command, response, "Failed to list folders");
        }

        return parseFolderList(response);
    }

    /**
     * Logout và đóng kết nối
     */
    public void logout() throws ImapException {
        if (!connected) return;

        try {
            String tag = nextTag();
            String command = tag + " LOGOUT";

            System.out.println("→ " + command);
            sendCommand(command);
            readFullResponse(tag);

            close();
            System.out.println("✓ Logged out");
        } catch (Exception e) {
            throw new ImapException("Logout failed: " + e.getMessage(), e);
        }
    }

    /**
     * Đóng kết nối
     */
    public void close() {
        try {
            if (reader != null) reader.close();
            if (writer != null) writer.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            logger.error("Error while closing connection", e);
        }
        connected = false;
        authenticated = false;
        selectedFolder = null;
    }

    // Helper Methods

    private String nextTag() {
        return Constants.IMAP_TAG_PREFIX + String.format("%03d", ++tagCounter);
    }

    private void sendCommand(String command) {
        writer.println(command);
        writer.flush();
    }

    private String readResponse() throws ImapException {
        try {
            return reader.readLine();
        } catch (IOException e) {
            throw new ImapException("Failed to read response: " + e.getMessage(), e);
        }
    }

    private String readFullResponse(String tag) throws ImapException {
        StringBuilder response = new StringBuilder();
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line).append("\r\n");
                System.out.println("← " + line);

                // Kết thúc khi gặp tagged response
                if (line.startsWith(tag + " ")) {
                    break;
                }
            }
        } catch (IOException e) {
            throw new ImapException("Failed to read response: " + e.getMessage(), e);
        }
        return response.toString();
    }

    private String quote(String text) {
        // Thêm quotes nếu cần (có space hoặc special chars)
        if (text.contains(" ") || text.contains("\"")) {
            return "\"" + text.replace("\"", "\\\"") + "\"";
        }
        return text;
    }

    /**
     * Parse FETCH response thành list emails
     */
    private List<Email> parseFetchResponse(String response) {
        List<Email> emails = new ArrayList<>();
        String[] lines = response.split("(?=\\* \\d+ FETCH)");

        for (String block : lines) {
            if (block.trim().startsWith("* ") && block.contains("FETCH")) {
                // Extract message number
                int msgNum = extractMessageNumber(block);
                if (msgNum > 0) {
                    Email email = ImapParser.parseEmailFromFetch(block, msgNum);
                    ImapParser.EmailBody emailBody = ImapParser.parseEmailBody(block);
                    email.setBody(emailBody.plainText);
                    email.setBodyHtml(emailBody.html);
                    email.setHtml(!emailBody.html.isEmpty());
                    emails.add(email);
                }
            }
        }

        return emails;
    }

    /**
     * Extract message number từ FETCH response
     * Example: "* 1 FETCH ..." -> 1
     */
    private int extractMessageNumber(String response) {
        try {
            String[] parts = response.trim().split("\\s+");
            if (parts.length >= 2 && parts[0].equals("*")) {
                return Integer.parseInt(parts[1]);
            }
        } catch (NumberFormatException e) {
            // Ignore
        }
        return -1;
    }

    /**
     * Parse LIST response thành folders
     */
    private List<Folder> parseFolderList(String response) {
        List<Folder> folders = new ArrayList<>();
        String[] lines = response.split("\r\n");

        for (String line : lines) {
            if (line.startsWith("* LIST")) {
                // Format: * LIST (\HasNoChildren) "/" "INBOX"
                String folderName = extractFolderName(line);
                if (folderName != null) {
                    folders.add(new Folder(folderName));
                }
            }
        }

        return folders;
    }

    /**
     * Extract folder name từ LIST response
     */
    private String extractFolderName(String line) {
        // Tìm phần cuối cùng trong quotes
        int lastQuote = line.lastIndexOf("\"");
        int secondLastQuote = line.lastIndexOf("\"", lastQuote - 1);

        if (lastQuote > secondLastQuote && secondLastQuote >= 0) {
            return line.substring(secondLastQuote + 1, lastQuote);
        }

        return null;
    }

    // Getters
    public boolean isConnected() {
        return connected;
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public String getSelectedFolder() {
        return selectedFolder;
    }
}