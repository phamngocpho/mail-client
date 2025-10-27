package protocols.imap;

import models.Email;
import models.Folder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.Constants;
import utils.ImapUtils;
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
        String command = String.format("%s LOGIN %s %s", tag, ImapUtils.quoteImapString(username), ImapUtils.quoteImapString(password));

        // Log command (ẩn password)
        logger.debug("→ {} LOGIN {} ****", tag, ImapUtils.quoteImapString(username));

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
        String command = String.format("%s SELECT %s", tag, ImapUtils.quoteImapString(folderName));

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

        int totalCount = end - start + 1;
        int batchSize = 25; // Fetch 25 emails at a time to avoid timeout
        
        // If range is small, fetch all at once
        if (totalCount <= batchSize) {
            return fetchEmailBatch(start, end);
        }
        
        // Otherwise, fetch in batches
        logger.info("Fetching {} emails in batches of {}", totalCount, batchSize);
        List<Email> allEmails = new ArrayList<>();
        
        for (int batchStart = start; batchStart <= end; batchStart += batchSize) {
            int batchEnd = Math.min(batchStart + batchSize - 1, end);
            logger.debug("Fetching batch: {} to {} ({} of {})", 
                        batchStart, batchEnd, allEmails.size(), totalCount);
            
            List<Email> batchEmails = fetchEmailBatch(batchStart, batchEnd);
            allEmails.addAll(batchEmails);
        }
        
        logger.info("Fetched total {} email headers from folder: {}", allEmails.size(), selectedFolder);
        return allEmails;
    }
    
    /**
     * Fetch a single batch of emails (internal method)
     */
    private List<Email> fetchEmailBatch(int start, int end) throws ImapException {
        String tag = nextTag();

        // CHỈ FETCH HEADERS - KHÔNG FETCH BODY
        // Dùng BODY.PEEK[HEADER] để tránh truncation của subjects dài
        String command = String.format("%s FETCH %d:%d (FLAGS BODY.PEEK[HEADER])",
                tag, start, end);

        logger.debug("→ {}", command);
        sendCommand(command);
        String response = readFullResponse(tag);

        if (ImapParser.isError(response, tag)) {
            throw new ImapException(command, response, "Failed to fetch emails");
        }

        // Parse headers only
        List<Email> emails = parseFetchResponse(response);
        logger.debug("Fetched {} email headers in this batch", emails.size());

        return emails;
    }

    /**
     * Fetch emails theo danh sách message numbers (tối ưu cho search results)
     * Fetch tất cả cùng lúc thay vì từng email một
     * 
     * @param messageNumbers danh sách message numbers cần fetch
     * @return List emails đã fetch
     */
    public List<Email> fetchEmailsByNumbers(List<Integer> messageNumbers) throws ImapException {
        if (selectedFolder == null) {
            throw new ImapException("No folder selected");
        }
        
        if (messageNumbers == null || messageNumbers.isEmpty()) {
            return new ArrayList<>();
        }
        
        // Tạo sequence-set cho IMAP: "1,5,10,15" hoặc "1:5,10:15"
        String sequenceSet = ImapUtils.buildSequenceSet(messageNumbers);
        
        String tag = nextTag();
        String command = String.format("%s FETCH %s (FLAGS INTERNALDATE BODY[HEADER.FIELDS (FROM TO SUBJECT DATE)])",
                tag, sequenceSet);

        logger.debug("→ Fetching {} emails with sequence-set", messageNumbers.size());
        sendCommand(command);
        String response = readFullResponse(tag);

        if (ImapParser.isError(response, tag)) {
            throw new ImapException(command, response, "Failed to fetch emails by numbers");
        }

        // Parse headers
        List<Email> emails = parseFetchResponse(response);
        logger.debug("Fetched {} email headers", emails.size());

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
        String command = String.format("%s COPY %d %s", tag, messageNumber, ImapUtils.quoteImapString(targetFolder));

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
     * Sends literal data to IMAP server after receiving continuation response.
     * Helper method to avoid code duplication.
     * 
     * @param literalData the literal data to send
     * @throws ImapException if continuation response is not received or I/O error occurs
     */
    private void sendLiteralData(String literalData) throws ImapException {
        try {
            String continuation = reader.readLine();
            if (continuation != null && continuation.startsWith("+")) {
                writer.println(literalData);
                writer.flush();
                logger.debug("→ {} (literal data)", literalData);
            } else {
                throw new ImapException("Expected continuation response, got: " + continuation);
            }
        } catch (IOException e) {
            throw new ImapException("Failed to send literal: " + e.getMessage(), e);
        }
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

    /**
     * Search emails trên server theo keyword
     * Tìm kiếm trong toàn bộ email (subject, from, body)
     * Hỗ trợ tiếng Việt có dấu và khoảng trắng
     *
     * @param keyword từ khóa tìm kiếm
     * @return List message numbers của emails tìm được
     */
    public List<Integer> searchEmails(String keyword) throws ImapException {
        if (selectedFolder == null) {
            throw new ImapException("No folder selected");
        }

        if (keyword == null || keyword.trim().isEmpty()) {
            return new ArrayList<>();
        }

        String tag = nextTag();

        // Try different search strategies in order
        logger.debug("Searching ENTIRE folder '{}' for: '{}'", selectedFolder, keyword);

        // Strategy 1: Try UTF-8 with proper encoding
        try {
            return searchWithUtf8(tag, keyword);
        } catch (Exception e) {
            logger.warn("UTF-8 search failed: {}", e.getMessage());
        }

        // Strategy 2: Try simple TEXT search
        try {
            return searchWithText(keyword);
        } catch (Exception e) {
            logger.warn("TEXT search failed: {}", e.getMessage());
        }

        // Strategy 3: Try individual field searches without OR
        try {
            return searchWithIndividualFields(keyword);
        } catch (Exception e) {
            logger.error("All search strategies failed for keyword: {}", keyword);
            return new ArrayList<>();
        }
    }

    /**
     * Search with UTF-8 charset using literal string format
     */
    private List<Integer> searchWithUtf8(String tag, String keyword) throws ImapException {
        // Use literal format for UTF-8 strings: {byte_count}\r\nactual_string
        byte[] keywordBytes = keyword.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int byteCount = keywordBytes.length;

        // Send command with literal format
        String command = String.format("%s SEARCH CHARSET UTF-8 TEXT {%d}", tag, byteCount);
        logger.debug("→ {} (UTF-8 literal)", command);

        writer.println(command);
        writer.flush();

        // Wait for continuation response "+" and send literal data
        sendLiteralData(keyword);

        String response = readFullResponse(tag);

        if (ImapParser.isError(response, tag)) {
            throw new ImapException("UTF-8 search failed");
        }

        return parseSearchResponse(response);
    }

    /**
     * Simple TEXT search
     */
    private List<Integer> searchWithText(String keyword) throws ImapException {
        String tag = nextTag();

        // Use literal format for TEXT search too
        byte[] keywordBytes = keyword.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int byteCount = keywordBytes.length;

        String command = String.format("%s SEARCH TEXT {%d}", tag, byteCount);
        logger.debug("→ {} (TEXT literal)", command);

        writer.println(command);
        writer.flush();

        // Wait for continuation response "+" and send literal data
        sendLiteralData(keyword);

        String response = readFullResponse(tag);

        if (ImapParser.isError(response, tag)) {
            throw new ImapException("TEXT search failed");
        }

        return parseSearchResponse(response);
    }

    /**
     * Search individual fields (SUBJECT, FROM, BODY) separately and combine results
     */
    private List<Integer> searchWithIndividualFields(String keyword) {
        List<Integer> allResults = new ArrayList<>();
        String[] fields = {"SUBJECT", "FROM", "BODY"};

        for (String field : fields) {
            try {
                String tag = nextTag();
                byte[] keywordBytes = keyword.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                int byteCount = keywordBytes.length;

                String command = String.format("%s SEARCH %s {%d}", tag, field, byteCount);
                logger.debug("→ {} (searching {})", command, field);

                writer.println(command);
                writer.flush();

                // Wait for continuation response "+" and send literal data
                sendLiteralData(keyword);

                String response = readFullResponse(tag);

                if (!ImapParser.isError(response, tag)) {
                    List<Integer> fieldResults = parseSearchResponse(response);
                    // Add unique results only
                    for (Integer msgNum : fieldResults) {
                        if (!allResults.contains(msgNum)) {
                            allResults.add(msgNum);
                        }
                    }
                    logger.debug("Found {} results in {}", fieldResults.size(), field);
                }
            } catch (Exception e) {
                logger.warn("Search in {} failed: {}", field, e.getMessage());
            }
        }

        logger.debug("Total unique results from all fields: {}", allResults.size());
        return allResults;
    }

    private static String getCommand(boolean needsUtf8, String tag, String quotedKeyword) {
        String command;

        if (needsUtf8) {
            // Tìm kiếm với UTF-8 charset trong SUBJECT, FROM, hoặc BODY
            command = String.format("%s SEARCH CHARSET UTF-8 OR OR SUBJECT %s FROM %s BODY %s",
                    tag, quotedKeyword, quotedKeyword, quotedKeyword);
        } else {
            // Tìm kiếm đơn giản với TEXT (cho ASCII)
            command = String.format("%s SEARCH OR OR SUBJECT %s FROM %s BODY %s",
                    tag, quotedKeyword, quotedKeyword, quotedKeyword);
        }
        return command;
    }

    /**
     * Fallback search method - tìm kiếm đơn giản hơn khi CHARSET không được hỗ trợ
     */
    private List<Integer> searchEmailsSimple(String keyword) throws ImapException {
        String tag = nextTag();
        String quotedKeyword = ImapUtils.quoteImapString(keyword);
        
        // Thử với TEXT command đơn giản
        String command = String.format("%s SEARCH TEXT %s", tag, quotedKeyword);
        
        logger.debug("→ {} (fallback)", command);
        sendCommand(command);
        String response = readFullResponse(tag);
        
        if (ImapParser.isError(response, tag)) {
            // Nếu vẫn lỗi, trả về empty list thay vì throw exception
            logger.error("Search failed for keyword: {}", keyword);
            return new ArrayList<>();
        }
        
        return parseSearchResponse(response);
    }
    
    /**
     * Parse SEARCH response để lấy list message numbers
     * Response format: * SEARCH 1 5 10 15
     */
    private List<Integer> parseSearchResponse(String response) {
        List<Integer> messageNumbers = new ArrayList<>();
        
        // Tìm dòng "* SEARCH ..."
        String[] lines = response.split("\r\n");
        for (String line : lines) {
            if (line.startsWith("* SEARCH")) {
                // Extract numbers sau "* SEARCH "
                String numbersStr = line.substring("* SEARCH".length()).trim();
                if (!numbersStr.isEmpty()) {
                    String[] numbers = numbersStr.split("\\s+");
                    for (String numStr : numbers) {
                        try {
                            messageNumbers.add(Integer.parseInt(numStr));
                        } catch (NumberFormatException e) {
                            logger.warn("Failed to parse message number: {}", numStr);
                        }
                    }
                }
                break;
            }
        }
        
        logger.debug("Search found {} messages: {}", messageNumbers.size(), messageNumbers);
        return messageNumbers;
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