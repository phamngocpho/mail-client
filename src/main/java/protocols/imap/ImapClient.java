package protocols.imap;

import models.Email;
import models.Folder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.Constants;
import utils.NetworkUtils;

import javax.net.ssl.SSLSocket;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The ImapClient class provides functionality for connecting to an IMAP server,
 * managing folders, retrieving emails, and manipulating email flags. It uses
 * the IMAP protocol to perform various operations such as fetching emails,
 * updating flags, and working with server-side folders.
 * <p>
 * This class supports secure SSL connections and provides methods to handle
 * common tasks like reading email headers, fetching email bodies, marking emails
 * as read, and deleting emails. The commands are executed through an established
 * IMAP connection using a tagging-based system to track responses from the server.
 * <p>
 * Fields:
 * - socket: The TCP socket used to connect to the IMAP server.
 * - reader: The input stream for reading server responses.
 * - writer: The output stream for sending commands to the server.
 * - tagCounter: A counter to generate unique IMAP tags for commands.
 * - connected: Indicates whether the client is currently connected to an IMAP server.
 * - authenticated: Indicates whether the client has successfully authenticated with the server.
 * - selectedFolder: The current folder selected for IMAP commands.
 * - logger: Logger used for tracing and debugging client operations.
 */
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
            logger.info("Connecting to {}:{}", host, port);
            socket = NetworkUtils.createSSLSocket(
                    host, port,
                    null,
                    0
            );
            reader = NetworkUtils.createReader(socket);
            writer = NetworkUtils.createWriter(socket);

            // Đọc greeting từ server
            String greeting = readResponse();
            logger.debug("← Server greeting: {}", greeting);

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
        logger.debug("→ {} LOGIN {} ****", tag, quote(username));

        sendCommand(command);
        String response = readFullResponse(tag);

        if (ImapParser.isError(response, tag)) {
            throw new ImapException(command, response, "Login failed");
        }

        authenticated = true;
        logger.info("Login successful for user: {}", username);
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

        logger.debug("→ {}", command);
        sendCommand(command);
        String response = readFullResponse(tag);

        if (ImapParser.isError(response, tag)) {
            throw new ImapException(command, response, "Failed to select folder: " + folderName);
        }

        selectedFolder = folderName;
        int messageCount = ImapParser.parseMessageCount(response);
        logger.info("Selected folder: {} ({} messages)", folderName, messageCount);

        return messageCount;
    }

    /**
     * Fetch emails từ folder đã select - CHỈ FETCH HEADERS
     * Body sẽ được fetch riêng khi user click vào email
     *
     * @param start Message number bắt đầu (1-indexed)
     * @param end   Message number kết thúc
     */
    public List<Email> fetchEmails(int start, int end) throws ImapException {
        if (selectedFolder == null) {
            throw new ImapException("No folder selected");
        }

        List<Email> emails;
        String tag = nextTag();

        // CHỈ FETCH HEADERS - KHÔNG FETCH BODY
        // Dùng BODY.PEEK[HEADER] để tránh truncation của subjects dài
        String command = String.format("%s FETCH %d:%d (FLAGS BODY.PEEK[HEADER])",
                tag, start, end);

        logger.debug("→ {}", command);
        sendCommand(command);
        String response = readFullResponse(tag);
        
        logger.debug("FETCH response length: {} bytes", response.length());
        if (!response.isEmpty() && response.length() <= 5000) {
            logger.debug("Full FETCH response: {}", response);
        } else if (response.length() > 5000) {
            logger.debug("FETCH response preview (first 5000 chars): {}", response.substring(0, 5000));
        }

        if (ImapParser.isError(response, tag)) {
            throw new ImapException(command, response, "Failed to fetch emails");
        }

        // Parse headers only
        emails = parseFetchResponse(response);
        logger.debug("Fetched {} email headers from folder: {}", emails.size(), selectedFolder);

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
        String command = String.format("%s FETCH %d (BODY[])", tag, messageNumber);

        logger.debug("→ {}", command);
        sendCommand(command);
        String response = readFullResponse(tag);

        logger.debug("Raw IMAP response length: {} bytes", response.length());
        logger.debug("Raw response preview (first 500 chars): {}",
                response.substring(0, Math.min(500, response.length())));

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

        logger.debug("→ {}", command);
        sendCommand(command);
        String response = readFullResponse(tag);

        if (ImapParser.isError(response, tag)) {
            throw new ImapException(command, response, "Failed to list folders");
        }

        return parseFolderList(response);
    }

    /**
     * Update flags cho một email
     *
     * @param messageNumber Message number (1-indexed)
     * @param flags         List flags cần update (ví dụ: "\\Seen", "\\Flagged", "\\Deleted")
     * @param add           true = thêm flags, false = xóa flags
     */
    public void updateFlags(int messageNumber, List<String> flags, boolean add) throws ImapException {
        if (selectedFolder == null) {
            throw new ImapException("No folder selected");
        }

        String tag = nextTag();
        String flagsStr = String.join(" ", flags);
        String mode = add ? "+FLAGS" : "-FLAGS";
        String command = String.format("%s STORE %d %s (%s)", tag, messageNumber, mode, flagsStr);

        logger.debug("→ {}", command);
        sendCommand(command);
        String response = readFullResponse(tag);

        if (ImapParser.isError(response, tag)) {
            throw new ImapException(command, response, "Failed to update flags");
        }

        logger.debug("Flags updated for message #{}", messageNumber);
    }

    /**
     * Mark email as read/unread
     */
    public void markAsRead(int messageNumber, boolean read) throws ImapException {
        updateFlags(messageNumber, List.of("\\Seen"), read);
    }

    /**
     * Toggle starred status
     */
    public void toggleStar(int messageNumber, boolean starred) throws ImapException {
        updateFlags(messageNumber, List.of("\\Flagged"), starred);
    }

    /**
     * Mark for deletion (sẽ xóa khi gọi EXPUNGE)
     */
    public void markAsDeleted(int messageNumber) throws ImapException {
        updateFlags(messageNumber, List.of("\\Deleted"), true);
    }

    /**
     * Permanently removes all messages marked as deleted from the currently selected folder
     * on the IMAP server.
     * <p>
     * This method sends the EXPUNGE command to the server to clear deleted messages.
     * If no folder is selected, an ImapException is thrown. An error response
     * from the server also results in an ImapException.
     *
     * @throws ImapException if there is no folder selected, or if the server responds
     *         with an error to the EXPUNGE command
     */
    public void expunge() throws ImapException {
        if (selectedFolder == null) {
            throw new ImapException("No folder selected");
        }

        String tag = nextTag();
        String command = tag + " EXPUNGE";

        logger.debug("→ {}", command);
        sendCommand(command);
        String response = readFullResponse(tag);

        if (ImapParser.isError(response, tag)) {
            throw new ImapException(command, response, "EXPUNGE failed");
        }

        logger.info("Expunged deleted messages from folder: {}", selectedFolder);
    }

    /**
     * Copy email to another folder
     */
    public void copyEmail(int messageNumber, String targetFolder) throws ImapException {
        if (selectedFolder == null) {
            throw new ImapException("No folder selected");
        }

        String tag = nextTag();
        String command = String.format("%s COPY %d %s", tag, messageNumber, quote(targetFolder));

        logger.debug("→ {}", command);
        sendCommand(command);
        String response = readFullResponse(tag);

        if (ImapParser.isError(response, tag)) {
            throw new ImapException(command, response, "Failed to copy email");
        }

        logger.info("Email #{} copied to folder: {}", messageNumber, targetFolder);
    }

    /**
     * Logout và đóng kết nối
     */
    public void logout() throws ImapException {
        if (!connected) return;

        try {
            String tag = nextTag();
            String command = tag + " LOGOUT";

            logger.debug("→ {}", command);
            sendCommand(command);
            readFullResponse(tag);

            close();
            logger.info("Logged out successfully");
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

    /**
     * Reads a complete IMAP response, including both text and binary literal data,
     * for a specific command tag from the server. It collects all lines of the response
     * until the line starting with the given tag is reached.
     *
     * @param tag the command tag used to identify the end of the response
     * @return the full response received from the server as a string, including all lines
     *         and literal data
     * @throws ImapException if an I/O error occurs while reading the response, or if the
     *         response cannot be properly processed
     */
    private String readFullResponse(String tag) throws ImapException {
        StringBuilder response = new StringBuilder();
        int lineCount = 0;

        try {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line).append("\r\n");
                lineCount++;

                // Check nếu có literal data (binary attachment)
                if (line.contains("{") && line.endsWith("}")) {
                    int size = extractLiteralSize(line);
                    if (size > 0) {
                        // Đọc binary data
                        char[] buffer = new char[size];
                        int bytesRead = reader.read(buffer, 0, size);
                        if (bytesRead != size) {
                            logger.debug("Expected {} bytes but read {} bytes", size, bytesRead);
                        }
                        response.append(buffer);
                        response.append("\r\n");
                    }
                }

                if (line.startsWith(tag + " ")) {
                    logger.debug("← {} ({} lines)", line, lineCount);
                    break;
                }
            }
        } catch (IOException e) {
            throw new ImapException("Failed to read response: " + e.getMessage(), e);
        }
        return response.toString();
    }

    /**
     * Extracts the literal size from an IMAP response line in the format "{size}\r\n".
     * The size is expected to appear at the end of the provided line. If the line does not
     * match the expected format or does not contain a valid size, the method returns 0.
     *
     * @param line the IMAP response line from which the literal size needs to be extracted
     * @return the extracted literal size as an integer if successful; otherwise, returns 0
     */
    private int extractLiteralSize(String line) {
        try {
            // IMAP literal format: {123}\r\n
            // Phải ở cuối dòng, theo sau bởi \r\n hoặc }
            Pattern literalPattern = Pattern.compile("\\{(\\d+)}\\s*$");
            Matcher matcher = literalPattern.matcher(line);

            if (matcher.find()) {
                int size = Integer.parseInt(matcher.group(1));
                logger.debug("Found literal size: {}", size);
                return size;
            }
        } catch (Exception e) {
            logger.debug("Not a valid IMAP literal: {}", line);
        }
        return 0;
    }

    /**
     * Surrounds the given text with double quotes if it contains spaces or double-quote characters.
     * Any existing double quotes within the text are escaped with a backslash.
     *
     * @param text the input string to be quoted
     * @return the quoted string if the input contains spaces or double-quote characters,
     *         otherwise returns the original string
     */
    private String quote(String text) {
        // Thêm quotes (có space hoặc special chars)
        if (text.contains(" ") || text.contains("\"")) {
            return "\"" + text.replace("\"", "\\\"") + "\"";
        }
        return text;
    }

    /**
     * Parse FETCH response thành list emails - CHỈ PARSE HEADERS
     */
    private List<Email> parseFetchResponse(String response) {
        List<Email> emails = new ArrayList<>();
        String[] lines = response.split("(?=\\* \\d+ FETCH)");

        for (String block : lines) {
            if (block.trim().startsWith("* ") && block.contains("FETCH")) {
                // Extract message number
                int msgNum = extractMessageNumber(block);
                if (msgNum > 0) {
                    // CHỈ PARSE HEADERS - KHÔNG PARSE BODY
                    Email email = ImapParser.parseEmailFromFetch(block, msgNum);

                    // Set body placeholder để không bị null
                    email.setBody("");
                    email.setBodyHtml("");
                    email.setHtml(false);

                    emails.add(email);
                }
            }
        }

        return emails;
    }

    /**
     * Extracts the message number from a server response string.
     * The method assumes the response begins with an asterisk (*) followed by a message number.
     * If the response does not conform to this format or the number cannot be parsed, it returns -1.
     *
     * @param response the server response string, which is expected to begin with
     *                 "* <message_number>" format
     * @return the extracted message number if successfully parsed; otherwise, returns -1
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