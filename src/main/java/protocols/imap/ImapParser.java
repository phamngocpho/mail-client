package protocols.imap;

import models.Email;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.AsyncUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The ImapParser class provides utility methods to parse and process IMAP responses.
 * It includes functionalities to extract and decode email data, parse headers, process attachments,
 * handle encodings, and manage multipart content within email messages.
 */
public class ImapParser {
    private static final Logger logger = LoggerFactory.getLogger(ImapParser.class);

    public static class EmailBody {
        public String plainText;
        public String html;
        public List<Attachment> attachments;

        public EmailBody() {
            this.plainText = "";
            this.html = "";
            this.attachments = new ArrayList<>();
        }
    }

    public static class Attachment {
        public String filename;
        public String contentType;
        public byte[] data;

        public Attachment(String filename, String contentType, byte[] data) {
            this.filename = filename;
            this.contentType = contentType;
            this.data = data;
        }
    }

    /**
     * Parse email từ FETCH response
     */
    public static Email parseEmailFromFetch(String response, int messageNumber) {
        Email email = new Email();
        email.setMessageNumber(messageNumber);

        // Parse FLAGS
        List<String> flags = parseFlags(response);
        email.setFlags(flags);

        // Parse headers
        String headers = extractHeaders(response);
        if (headers != null) {
            parseHeaders(headers, email);
        }

        return email;
    }

    /**
     * Parse FLAGS từ response
     */
    public static List<String> parseFlags(String response) {
        List<String> flags = new ArrayList<>();
        Pattern pattern = Pattern.compile("FLAGS \\(([^)]*)\\)");
        Matcher matcher = pattern.matcher(response);

        if (matcher.find()) {
            String flagsStr = matcher.group(1);
            String[] flagArray = flagsStr.split("\\s+");
            for (String flag : flagArray) {
                if (!flag.isEmpty()) {
                    flags.add(flag.replace("\\", ""));
                }
            }
        }

        return flags;
    }

    /**
     * Extract header content từ BODY[HEADER] hoặc BODY[HEADER.FIELDS ...]
     */
    private static String extractHeaders(String response) {
        int startIdx = response.indexOf("BODY[HEADER");
        if (startIdx == -1) return null;

        int headerStart = response.indexOf("\r\n", startIdx);
        if (headerStart == -1) return null;

        // Headers kết thúc bằng \r\n\r\n (double CRLF), sau đó là )
        int headerEnd = response.indexOf("\r\n\r\n)", headerStart);
        if (headerEnd == -1) {
            // Fallback: tìm )\r\n
            headerEnd = response.indexOf(")\r\n", headerStart);
            if (headerEnd == -1) headerEnd = response.length();
        } else {
            // Bao gồm cả \r\n\r\n
            headerEnd += 4; // +4 để include \r\n\r\n, nhưng không include )
        }

        String headers = response.substring(headerStart + 2, headerEnd);
        
        // Debug log để kiểm tra header extraction
        logger.debug("Extracted {}", AsyncUtils.createDetailedLog(headers, "headers", 500));
        
        // Check if Subject is truncated
        int subjectIdx = headers.indexOf("Subject:");
        if (subjectIdx != -1) {
            int subjectEnd = headers.indexOf("\r\n", subjectIdx);
            if (subjectEnd == -1) subjectEnd = headers.length();
            String subjectLine = headers.substring(subjectIdx, subjectEnd);
            logger.debug("Extracted subject line: [{}]", subjectLine);
            
            // Check for continuation (folded header)
            int nextLineStart = subjectEnd + 2;
            while (nextLineStart < headers.length() && 
                   (headers.charAt(nextLineStart) == ' ' || headers.charAt(nextLineStart) == '\t')) {
                int nextLineEnd = headers.indexOf("\r\n", nextLineStart);
                if (nextLineEnd == -1) nextLineEnd = headers.length();
                logger.debug("Subject continuation: [{}]", headers.substring(nextLineStart, nextLineEnd));
                nextLineStart = nextLineEnd + 2;
            }
        }
        
        return headers;
    }

    /**
     * Parses the raw email header string and extracts individual header fields.
     * The parsed header fields are then processed, and their data is added to the given Email object.
     *
     * @param headers The raw string containing email headers, typically separated by CRLF (\r\n).
     * @param email The Email object to populate with parsed header information.
     */
    private static void parseHeaders(String headers, Email email) {
        String[] lines = headers.split("\r\n");
        String currentHeader = null;
        StringBuilder currentValue = new StringBuilder();

        for (String line : lines) {
            if (line.isEmpty()) continue;

            if (!line.startsWith(" ") && !line.startsWith("\t")) {
                if (currentHeader != null) {
                    processHeader(currentHeader, currentValue.toString().trim(), email);
                }

                int colonIdx = line.indexOf(':');
                if (colonIdx > 0) {
                    currentHeader = line.substring(0, colonIdx).trim().toUpperCase();
                    currentValue = new StringBuilder(line.substring(colonIdx + 1).trim());
                }
            } else {
                currentValue.append(" ").append(line.trim());
            }
        }

        if (currentHeader != null) {
            processHeader(currentHeader, currentValue.toString().trim(), email);
        }
    }

    /**
     * Processes a specific email header and updates the corresponding field in the given Email object.
     *
     * @param header The name of the email header (e.g., "FROM", "TO", "SUBJECT").
     * @param value The value associated with the header, typically as a raw string.
     * @param email The Email objects to be updated with the processed header information.
     */
    private static void processHeader(String header, String value, Email email) {
        switch (header) {
            case "FROM":
                email.setFrom(cleanEmailAddress(value));
                break;
            case "TO":
                String[] toAddresses = value.split(",");
                for (String address : toAddresses) {
                    email.addTo(cleanEmailAddress(address.trim()));
                }
                break;
            case "CC":
                String[] ccAddresses = value.split(",");
                for (String address : ccAddresses) {
                    email.addCc(cleanEmailAddress(address.trim()));
                }
                break;
            case "SUBJECT":
                logger.debug("=== SUBJECT PROCESSING ===");
                logger.debug("Raw subject value: [{}]", value);
                logger.debug("Raw {}", AsyncUtils.createLengthLog(value, "subject"));
                String decodedSubject = decodeSubject(value);
                logger.debug("Decoded subject: [{}]", decodedSubject);
                email.setSubject(decodedSubject);
                break;
            case "DATE":
                email.setDate(parseDate(value));
                break;
            case "MESSAGE-ID":
                email.setMessageId(value);
                break;
        }
    }

    /**
     * Clean email address
     */
    private static String cleanEmailAddress(String address) {
        Pattern pattern = Pattern.compile("<([^>]+)>");
        Matcher matcher = pattern.matcher(address);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return address.trim();
    }

    /**
     * Decodes a subject string encoded in RFC 2047 format.
     * Supports multiple charsets (UTF-8, ISO-8859-1, etc.) and both Base64 (B) and Quoted-Printable (Q) encodings.
     * Handles line-wrapped encoded text and multiple encoded sections.
     *
     * @param subject The subject string to decode. May contain encoded sections like "=?charset?encoding?text?="
     * @return The decoded plain text subject. If decoding fails, returns the original subject.
     */
    private static String decodeSubject(String subject) {
        if (subject == null || subject.isEmpty()) {
            return subject;
        }

        try {
            logger.debug("Original subject: {}", subject);
            
            // Step 1: First, collapse multiple whitespace to a single space
            String normalized = subject.replaceAll("\\s+", " ").trim();
            
            // Step 2: Fix malformed encoded-words by removing spaces within =?...?= blocks
            // Remove space after =?
            normalized = normalized.replaceAll("=\\?\\s+", "=?");
            // Remove space before ?=
            normalized = normalized.replaceAll("\\s+\\?=", "?=");
            // Remove spaces around encoding: ? B ? → ?B?
            normalized = normalized.replaceAll("\\?\\s+([BQbq])\\s+\\?", "?$1?");
            
            // Step 3: Remove spaces WITHIN the charset portion (e.g., "=?U TF-8?B?" → "=?UTF-8?B?")
            // This handles cases where the charset is split: =?UTF- 8?B? or =?U TF-8?B?
            // Apply multiple times to handle multiple-word charsets
            Pattern charsetFixPattern = Pattern.compile("=\\?([A-Za-z0-9\\-]+)\\s+([A-Za-z0-9\\-]+)\\?([BQbq])\\?");
            int maxIterations = 5; // Prevent infinite loops
            for (int i = 0; i < maxIterations; i++) {
                Matcher charsetMatcher = charsetFixPattern.matcher(normalized);
                StringBuilder sb = new StringBuilder();
                boolean found = false;
                while (charsetMatcher.find()) {
                    found = true;
                    String part1 = charsetMatcher.group(1);
                    String part2 = charsetMatcher.group(2);
                    String encoding = charsetMatcher.group(3);
                    charsetMatcher.appendReplacement(sb, "=?" + part1 + part2 + "?" + encoding + "?");
                }
                charsetMatcher.appendTail(sb);
                normalized = sb.toString();
                if (!found) break; // No more matches, we're done
            }
            
            // Step 4: Merge adjacent encoded-words (RFC 2047 allows splitting long subjects)
            // Pattern: =?...?= followed by whitespace and another =?...?=
            normalized = normalized.replaceAll("\\?=\\s+=\\?", "?==?");
            
            logger.debug("Normalized subject: {}", normalized);
            
            // RFC 2047 pattern: =?charset?encoding?encoded-text?=
            // Supports any charset, B (Base64) or Q (Quoted-Printable) encoding
            Pattern pattern = Pattern.compile("=\\?([^?\\s]+)\\?([BQbq])\\?([^?]+)\\?=");
            Matcher matcher = pattern.matcher(normalized);

            StringBuilder result = new StringBuilder();
            int lastEnd = 0;
            boolean hasMatch = false;

            while (matcher.find()) {
                hasMatch = true;
                // Add any text before this match
                result.append(normalized, lastEnd, matcher.start());

                String charset = matcher.group(1).trim();
                String encoding = matcher.group(2).toUpperCase();
                String encodedText = matcher.group(3).trim();

                logger.debug("Decoding: charset={}, encoding={}, {}", 
                        charset, encoding, AsyncUtils.createLengthLog(encodedText, "text"));

                try {
                    String decoded = AsyncUtils.decodeEncodedText(encodedText, encoding, charset);
                    result.append(decoded);
                    logger.debug("Successfully decoded to: {}", decoded);
                } catch (Exception e) {
                    logger.warn("Failed to decode subject part: charset={}, encoding={}, text={}, error={}", 
                            charset, encoding, 
                            encodedText.substring(0, Math.min(50, encodedText.length())),
                            e.getMessage());
                    // If decode fails, append the original encoded text
                    result.append(matcher.group(0));
                }

                lastEnd = matcher.end();
            }

            // Add any remaining text after the last match
            result.append(normalized.substring(lastEnd));
            
            String finalResult = result.toString().trim();
            
            // If no match was found and the subject looks like it might be encoded, try a more lenient pattern
            if (!hasMatch && subject.contains("=?")) {
                logger.warn("Subject appears to be encoded but standard pattern didn't match: {}", normalized);
                logger.warn("Attempting lenient decoding...");
                
                // Try a more lenient pattern that allows spaces in charset
                // Pattern: =? (any chars) ? (B or Q) ? (encoded text) ?=
                Pattern lenientPattern = Pattern.compile("=\\?([A-Za-z0-9\\-\\s]+?)\\?([BQbq])\\?([^?]+)\\?=");
                Matcher lenientMatcher = lenientPattern.matcher(normalized);
                
                StringBuilder lenientResult = new StringBuilder();
                int lenientLastEnd = 0;
                boolean lenientMatch = false;
                
                while (lenientMatcher.find()) {
                    lenientMatch = true;
                    lenientResult.append(normalized, lenientLastEnd, lenientMatcher.start());
                    
                    String charset = lenientMatcher.group(1).replaceAll("\\s+", ""); // Remove all spaces from the charset
                    String encoding = lenientMatcher.group(2).toUpperCase();
                    String encodedText = lenientMatcher.group(3).trim();
                    
                    logger.debug("Lenient decoding: charset={}, encoding={}, {}", 
                            charset, encoding, AsyncUtils.createLengthLog(encodedText, "text"));
                    
                    try {
                        String decoded = AsyncUtils.decodeEncodedText(encodedText, encoding, charset);
                        lenientResult.append(decoded);
                        logger.debug("Lenient decode successful: {}", decoded);
                    } catch (Exception e) {
                        logger.warn("Lenient decode failed: {}", e.getMessage());
                        lenientResult.append(lenientMatcher.group(0));
                    }
                    
                    lenientLastEnd = lenientMatcher.end();
                }
                
                if (lenientMatch) {
                    lenientResult.append(normalized.substring(lenientLastEnd));
                    String lenientFinal = lenientResult.toString().trim();
                    logger.debug("Lenient decoded subject: {}", lenientFinal);
                    return lenientFinal;
                }
                
                // If even a lenient pattern fails, return a normalized version (not original)
                logger.warn("Both standard and lenient patterns failed, returning normalized subject");
                return normalized;
            }
            
            logger.debug("Final decoded subject: {}", finalResult);
            return finalResult;
            
        } catch (Exception e) {
            logger.error("Error decoding subject: {}", e.getMessage(), e);
            return subject;
        }
    }

    /**
     * Parses a date string into a Date object using multiple date formats.
     * If the date string cannot be parsed with any of the specified formats,
     * the current date and time are returned as a fallback.
     *
     * @param dateStr the date string to be parsed. This is expected to be in one of
     *                the predefined formats, such as "EEE, dd MMM yyyy HH:mm:ss Z"
     *                or similar.
     * @return a Date object representing the parsed date if successful, or the
     *         current date and time if parsing fails.
     */
    private static Date parseDate(String dateStr) {
        try {
            String[] formats = {
                    "EEE, dd MMM yyyy HH:mm:ss Z",
                    "dd MMM yyyy HH:mm:ss Z",
                    "EEE, dd MMM yyyy HH:mm:ss zzz"
            };

            for (String format : formats) {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat(format, Locale.ENGLISH);
                    return sdf.parse(dateStr);
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            logger.error("Error parsing date: {}", dateStr);
        }
        return new Date();
    }

    /**
     * Parse message count từ SELECT response
     */
    public static int parseMessageCount(String response) {
        Pattern pattern = Pattern.compile("\\* (\\d+) EXISTS");
        Matcher matcher = pattern.matcher(response);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return 0;
    }

    /**
     * Parses the body of an email from the given IMAP FETCH response.
     * The method extracts raw body content, detects boundaries for
     * multipart content, and parses attachments if applicable.
     *
     * @param response The IMAP FETCH response containing the email body data.
     * @return An EmailBody object containing the plain text, HTML content,
     *         and a list of attachments parsed from the response.
     */
    public static EmailBody parseEmailBody(String response) {
        EmailBody emailBody = extractRawBody(response);
        String boundary = detectBoundary(response);
        emailBody.attachments = parseAttachments(response, boundary);

        // Không decode lại vì đã decode trong extractRawBody và parseMultipart
        return emailBody;
    }

    /**
     * Extracts the raw body content from an IMAP FETCH response. This method identifies
     * the body segment, detects multipart boundaries if present, and processes the content
     * to separate plain text, HTML, and attachments.
     *
     * @param response The IMAP FETCH response containing the raw email data.
     * @return An EmailBody object containing the parsed plain text, HTML, and attachments.
     */
    private static EmailBody extractRawBody(String response) {
        EmailBody body = new EmailBody();

        logger.debug("Response length: {} bytes", response.length());

        int bodyStart = response.indexOf("BODY[]");
        if (bodyStart == -1) {
            return body;
        }

        int literalStart = response.indexOf("{", bodyStart);
        if (literalStart == -1) {
            return body;
        }

        int literalEnd = response.indexOf("}", literalStart);
        if (literalEnd == -1) {
            return body;
        }

        int contentStart = literalEnd + 3;
        int contentEnd = response.indexOf("\r\n)", contentStart);
        if (contentEnd == -1) {
            contentEnd = response.length();
        }

        String emailContent = response.substring(contentStart, contentEnd);
        String boundary = detectBoundary(emailContent);

        if (boundary == null) {
            // Email đơn giản không có multipart
            if (emailContent.toLowerCase().contains("text/html")) {
                body.html = extractSimpleContent(emailContent);
                body.plainText = htmlToPlainText(body.html);
            } else {
                body.plainText = extractSimpleContent(emailContent);
            }
            return body;
        }

        // Parse multipart
        parseMultipart(emailContent, boundary, body);

        // Nếu chỉ có HTML, tạo plain text từ HTML
        if (body.plainText.isEmpty() && !body.html.isEmpty()) {
            body.plainText = htmlToPlainText(body.html);
        }

        return body;
    }

    /**
     * Parses a multipart email content using the provided boundary and updates the given EmailBody object.
     * The method processes multipart sections, determines their content type, and decodes plain text or HTML
     * content. It also recursively handles nested multipart sections while skipping attachments.
     *
     * @param content The raw multipart content of the email to be parsed.
     * @param boundary The boundary string used to separate parts of the multipart content.
     * @param body The EmailBody object to populate with the extracted plain text and HTML content.
     */
    private static void parseMultipart(String content, String boundary, EmailBody body) {
        logger.debug("Boundary: {}", boundary);
        logger.debug("Content length: {} bytes", content.length());
        String[] parts = content.split(Pattern.quote(boundary));

        for (int i = 1; i < parts.length; i++) {
            String part = parts[i].trim();

            if (part.startsWith("--")) {
                continue;
            }

            int headerEnd = part.indexOf("\r\n\r\n");
            if (headerEnd == -1) {
                headerEnd = part.indexOf("\n\n");
            }

            if (headerEnd == -1) {
                continue;
            }

            String headers = part.substring(0, headerEnd);
            String partContent = part.substring(headerEnd + 4);

            int nextBoundary = partContent.indexOf("\r\n--");
            if (nextBoundary > 0) {
                partContent = partContent.substring(0, nextBoundary);
            }

            // Skip attachment
            if (headers.toLowerCase().contains("content-disposition:")
                    && headers.toLowerCase().contains("attachment")) {
                continue;
            }

            // Kiểm tra nested multipart
            String nestedBoundary = detectBoundary(headers);
            if (nestedBoundary != null) {
                logger.debug("Found nested multipart with boundary: {}", nestedBoundary);
                parseMultipart(partContent, nestedBoundary, body);
                continue;
            }

            // Extract charset và encoding
            String charset = extractCharsetFromHeaders(headers);
            String encoding = extractEncodingFromHeaders(headers);

            // Parse text/plain
            if (headers.toLowerCase().contains("content-type: text/plain") ||
                    headers.toLowerCase().contains("content-type:text/plain")) {
                logger.debug("Found text/plain part");
                logger.debug("{}", AsyncUtils.createEncodingLog(encoding, charset));
                logger.debug("Raw content preview: {}", AsyncUtils.createPreview(partContent));

                body.plainText = decodeContent(partContent.trim(), encoding, charset);

                logger.debug("{}", AsyncUtils.createDecodedLog(body.plainText, "plain text"));
            }

            // Parse text/html
            if (headers.toLowerCase().contains("content-type: text/html") ||
                    headers.toLowerCase().contains("content-type:text/html")) {
                body.html = decodeContent(partContent.trim(), encoding, charset);
                logger.debug("Extracted {} with charset: {}", AsyncUtils.createLengthLog(body.html, "HTML"), charset);
            }
        }
    }

    /**
     * Extracts the simplified content of an email from its raw content.
     * This method identifies and separates the headers and body of an email,
     * determines the character set and encoding from the headers, and decodes
     * the email body accordingly.
     *
     * @param emailContent The raw string representation of an email,
     *                     including headers and body content.
     * @return The decoded plain text of the email body. If decoding
     *         headers and body fails or headers cannot be identified,
     *         the trimmed email content is returned.
     */
    private static String extractSimpleContent(String emailContent) {
        int headerEnd = emailContent.indexOf("\r\n\r\n");
        if (headerEnd == -1) {
            headerEnd = emailContent.indexOf("\n\n");
        }

        if (headerEnd == -1) {
            return emailContent.trim();
        }

        String headers = emailContent.substring(0, headerEnd);
        String content = emailContent.substring(headerEnd + 4).trim();

        // Extract charset và encoding
        String charset = extractCharsetFromHeaders(headers);
        String encoding = extractEncodingFromHeaders(headers);

        logger.debug("Headers: {}", AsyncUtils.createDetailedLog(headers, "headers", 300));
        logger.debug("Detected charset: {}, encoding: {}", charset, encoding);
        logger.debug("{}", AsyncUtils.createContentLog(content));

        return decodeContent(content, encoding, charset);
    }

    /**
     * Extracts the value of the "Content-Transfer-Encoding" field from the provided email headers.
     * The method searches for the "Content-Transfer-Encoding" header in the given string using
     * a case-insensitive regular expression. If found, the value is trimmed and returned.
     * If the header is missing, a default encoding of "7bit" is returned.
     *
     * @param headers The raw string containing email headers, typically separated by CRLF (\r\n).
     * @return The extracted encoding value as a String (e.g., "base64", "quoted-printable"),
     *         or "7bit" if the header is not present.
     */
    private static String extractEncodingFromHeaders(String headers) {
        Pattern pattern = Pattern.compile(
                "Content-Transfer-Encoding:\\s*([^\\r\\n]+)",
                Pattern.CASE_INSENSITIVE
        );
        Matcher matcher = pattern.matcher(headers);
        if (matcher.find()) {
            String encoding = matcher.group(1).trim();

            // Chỉ lấy phần encoding hợp lệ (trước dấu ; hoặc space)
            if (encoding.contains(";")) {
                encoding = encoding.substring(0, encoding.indexOf(";")).trim();
            }
            if (encoding.contains(":")) {
                encoding = encoding.substring(0, encoding.indexOf(":")).trim();
            }

            logger.debug("Extracted encoding: '{}'", encoding);
            return encoding;
        }
        return "7bit";
    }

    /**
     * Extracts the charset from the given headers string. The method searches for a charset
     * declaration within the headers using a case-insensitive regular expression. If a charset
     * is found, it is returned trimmed. If no charset is detected, the default value "UTF-8" is returned.
     *
     * @param headers The raw string containing headers, typically including the "Content-Type" field
     *                or similar metadata where a charset might be specified.
     * @return The extracted charset as a string (e.g., "UTF-8", "ISO-8859-1"). If no charset is found,
     *         the default value "UTF-8" is returned.
     */
    private static String extractCharsetFromHeaders(String headers) {
        Pattern pattern = Pattern.compile(
                "charset=[\"']?([^\"'\\s;\\r\\n]+)[\"']?",
                Pattern.CASE_INSENSITIVE
        );
        Matcher matcher = pattern.matcher(headers);
        if (matcher.find()) {
            String charset = matcher.group(1).trim();
            logger.debug("Found charset: {}", charset);
            return charset;
        }
        logger.debug("No charset found, defaulting to UTF-8");
        return "UTF-8";
    }

    /**
     * Parses a response string containing multipart data and extracts attachments based on the specified boundary.
     *
     * @param response the input string containing the multipart data
     * @param boundary the boundary delimiter used to separate the parts of the multipart data
     * @return a list of attachments extracted from the multipart data, or an empty list if no attachments are found
     */
    private static List<Attachment> parseAttachments(String response, String boundary) {
        List<Attachment> attachments = new ArrayList<>();

        if (boundary == null) return attachments;

        String[] parts = response.split(Pattern.quote(boundary), -1);

        for (int i = 1; i < parts.length - 1; i++) {
            String part = parts[i].trim();

            if (part.toLowerCase().contains("content-disposition:")
                    && part.toLowerCase().contains("attachment")) {

                Pattern filenamePattern = Pattern.compile(
                        "filename=\"?([^\"\\r\\n]+)\"?",
                        Pattern.CASE_INSENSITIVE
                );
                Matcher matcher = filenamePattern.matcher(part);
                String filename = matcher.find() ? matcher.group(1) : "unknown";

                Pattern typePattern = Pattern.compile(
                        "Content-Type:\\s*([^;\\r\\n]+)",
                        Pattern.CASE_INSENSITIVE
                );
                Matcher typeMatcher = typePattern.matcher(part);
                String contentType = typeMatcher.find()
                        ? typeMatcher.group(1).trim()
                        : "application/octet-stream";

                String encoding = detectEncodingForPart(response, part);

                int headerEnd = part.indexOf("\r\n\r\n");
                if (headerEnd != -1) {
                    String content = part.substring(headerEnd + 4);

                    int nextBoundary = content.indexOf("--" + boundary.substring(2));
                    if (nextBoundary > 0) {
                        content = content.substring(0, nextBoundary).trim();
                    }

                    logger.debug("Attachment: {}", filename);
                    logger.debug("Content-Type: {}", contentType);
                    logger.debug("Encoding: {}", encoding);

                    byte[] data = decodeAttachmentData(content, encoding);

                    logger.debug("{}", AsyncUtils.createDataLengthLog(data, "Decoded data"));

                    attachments.add(new Attachment(filename, contentType, data));
                }
            }
        }

        return attachments;
    }

    /**
     * Decodes the attachment data based on the specified encoding.
     *
     * @param content the content of the attachment as a string to be decoded
     * @param encoding the encoding type of the attachment content (e.g., "base64", "quoted-printable", "7bit", "8bit", "binary")
     * @return the decoded attachment data as a byte array; returns an empty byte array if decoding fails or an error occurs
     */
    private static byte[] decodeAttachmentData(String content, String encoding) {
        try {
            encoding = encoding.toLowerCase().trim();

            logger.debug("Decoding with encoding: {}", encoding);

            switch (encoding) {
                case "base64":
                    StringBuilder cleanBase64 = new StringBuilder(content
                            .replaceAll("[\\r\\n\\s]+", "")
                            .replaceAll("[^A-Za-z0-9+/=]", ""));

                    while (cleanBase64.length() % 4 != 0) {
                        cleanBase64.append("=");
                    }

                    logger.debug("Cleaned Base64 length: {}", cleanBase64.length());

                    try {
                        byte[] decoded = Base64.getDecoder().decode(cleanBase64.toString());
                        logger.debug("Successfully decoded {} bytes from Base64", decoded.length);
                        return decoded;
                    } catch (IllegalArgumentException e) {
                        logger.error("Invalid Base64 data: {}", e.getMessage());
                        return new byte[0];
                    }

                case "quoted-printable":
                    return decodeQuotedPrintable(content).getBytes(StandardCharsets.UTF_8);

                case "7bit":
                case "8bit":
                case "binary":
                    if (content.matches("^[A-Za-z0-9+/=\\s]+$") && content.startsWith("UEs")) {
                        logger.warn("8bit/7bit data looks like Base64, attempting decode...");
                        return decodeAttachmentData(content, "base64");
                    }

                    logger.debug("Treating as binary data");
                    return content.getBytes(StandardCharsets.ISO_8859_1);

                default:
                    logger.warn("Unknown encoding: {}, treating as binary", encoding);
                    return content.getBytes(StandardCharsets.ISO_8859_1);
            }
        } catch (Exception e) {
            logger.error("Decode attachment error: {}", e.getMessage(), e);
            return new byte[0];
        }
    }

    /**
     * Decodes a given content string using the specified encoding and charset.
     * The method supports multiple encoding schemes such as Base64, quoted-printable,
     * and other standard text encodings (7bit, 8bit, binary, etc.).
     *
     * @param content the content string to decode; must not be null or empty
     * @param encoding the encoding scheme of the content, e.g., "base64", "quoted-printable", etc.
     * @param charset the charset to convert the decoded content into, e.g., "UTF-8", "ISO-8859-1"
     * @return the decoded content as a string in the specified charset, or the original content trimmed
     *         if decoding fails or the content is invalid for the given encoding
     */
    private static String decodeContent(String content, String encoding, String charset) {
        if (content == null || content.isEmpty()) return "";

        try {
            // Normalize charset name
            charset = normalizeCharset(charset);

            logger.debug("{}", AsyncUtils.createEncodingLog(encoding, charset));
            logger.debug("{}", AsyncUtils.createContentLog(content, 100));

            // Auto-detect quoted-printable if the encoding header is missing/wrong but content looks like QP
            if (!encoding.equalsIgnoreCase("quoted-printable") && 
                !encoding.equalsIgnoreCase("base64") &&
                content.matches("(?s).*=[0-9A-Fa-f]{2}.*")) {
                logger.debug("Auto-detected Quoted-Printable encoding from content pattern");
                encoding = "quoted-printable";
            }

            switch (encoding.toLowerCase().trim()) {
                case "base64":
                    StringBuilder cleanBase64 = new StringBuilder(
                            content.replaceAll("[\\r\\n]+", "").replaceAll("\\s+", "")
                    );
                    while (cleanBase64.length() % 4 != 0) {
                        cleanBase64.append("=");
                    }
                    if (isValidBase64(cleanBase64.toString())) {
                        byte[] decodedBytes = Base64.getDecoder().decode(cleanBase64.toString());
                        return new String(decodedBytes, charset);
                    }
                    break;

                case "quoted-printable":
                    logger.debug("Before QP decode: {}", AsyncUtils.createPreview(content, 100));
                    String decoded = decodeQuotedPrintable(content);
                    logger.debug("After QP decode: {}", AsyncUtils.createPreview(decoded, 100));
                    return decoded;

                case "7bit":
                case "8bit":
                case "binary":
                default:
                    // Try to decode as UTF-8 or specified charset
                    try {
                        return new String(content.getBytes(StandardCharsets.ISO_8859_1), charset);
                    } catch (Exception e) {
                        logger.debug("Failed to decode with charset {}, using default", charset);
                        return content.trim();
                    }
            }
        } catch (Exception e) {
            logger.error("Decode error: {}", e.getMessage());
        }

        return content.trim();
    }

    /**
     * Normalizes the provided charset name to a standard format.
     * If the input charset is null or empty, it defaults to "UTF-8".
     * Common charset variants are mapped to their standard names.
     *
     * @param charset the name of the charset to normalize, which may be null or empty
     * @return the normalized charset name; if the input is null or empty, returns "UTF-8"
     */
    private static String normalizeCharset(String charset) {
        if (charset == null || charset.isEmpty()) {
            return "UTF-8";
        }

        charset = charset.toUpperCase().trim();

        // Map các charset variant
        return switch (charset) {
            case "UTF8" -> "UTF-8";
            case "ISO-8859-1", "ISO8859-1", "LATIN1" -> "ISO-8859-1";
            case "US-ASCII", "ASCII" -> "US-ASCII";
            default -> charset;
        };
    }

    /**
     * Detects and extracts the boundary value from a given response string.
     * The boundary value is typically used in multipart data structures.
     *
     * @param response the input string containing the boundary definition
     * @return the extracted boundary string prefixed with "--", or null if no boundary is found
     */
    private static String detectBoundary(String response) {
        Pattern pattern = Pattern.compile("boundary=[\"']?([^\";\\s]+)[\"']?", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(response);
        if (matcher.find()) {
            return "--" + matcher.group(1);
        }
        return null;
    }

    /**
     * Detects the content transfer encoding for a specified part of a response. It attempts to identify
     * the encoding through specific headers or by analyzing the content structure.
     *
     * @param response The full response string which may contain multiple parts and headers.
     * @param part The specific part of the response to analyze for encoding information.
     * @return The detected encoding as a string, such as "base64", or "7bit" if no encoding is identified.
     */
    private static String detectEncodingForPart(String response, String part) {
        Pattern pattern = Pattern.compile(
                "Content-Transfer-Encoding:\\s*([^\\r\\n]+)",
                Pattern.CASE_INSENSITIVE
        );
        Matcher matcher = pattern.matcher(part);
        if (matcher.find()) {
            String encoding = matcher.group(1).trim();
            logger.debug("Found encoding in part: {}", encoding);
            return encoding;
        }

        matcher = pattern.matcher(response);
        if (matcher.find()) {
            String encoding = matcher.group(1).trim();
            logger.debug("Found encoding in response: {}", encoding);
            return encoding;
        }

        int headerEnd = part.indexOf("\r\n\r\n");
        if (headerEnd != -1) {
            String content = part.substring(headerEnd + 4).trim();
            String sample = content.substring(0, Math.min(100, content.length()));
            boolean looksLikeBase64 = sample.matches("^[A-Za-z0-9+/=\\s]+$");

            if (looksLikeBase64 && sample.startsWith("UEs")) {
                logger.info("Auto-detected Base64 encoding (ZIP signature found)");
                return "base64";
            }

            long base64Chars = sample.chars()
                    .filter(c -> (c >= 'A' && c <= 'Z') ||
                            (c >= 'a' && c <= 'z') ||
                            (c >= '0' && c <= '9') ||
                            c == '+' || c == '/' || c == '=')
                    .count();

            double base64Ratio = (double) base64Chars / sample.replaceAll("\\s", "").length();

            if (base64Ratio > 0.95) {
                logger.info("Auto-detected Base64 encoding ({}% Base64 chars)",
                        (int)(base64Ratio * 100));
                return "base64";
            }
        }

        logger.warn("No encoding found, defaulting to 7bit");
        return "7bit";
    }

    /**
     * Decodes a given string encoded in the quoted-printable format into its original form.
     * Quoted-printable encoding is often used to encode text in email messages and other protocols
     * to encode special and non-ASCII characters.
     *
     * @param qp the input string encoded in quoted-printable format; may be null
     * @return the decoded string; if the input is null, returns an empty string
     */
    private static String decodeQuotedPrintable(String qp) {
        if (qp == null) return "";

        logger.debug("QP Input preview: [{}]", AsyncUtils.createPreview(qp));

        // Fix soft breaks: Remove = + optional spaces + \r?\n + optional spaces (không thêm space mới)
        qp = qp.replaceAll("=\\s*\\r?\\n\\s*", "");  // → "notice d" thành "noticed"

        // Decode =XX hex (giữ nguyên)
        Pattern hexPattern = Pattern.compile("=([0-9A-Fa-f]{2})");
        Matcher hexMatcher = hexPattern.matcher(qp);

        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        int lastEnd = 0;

        while (hexMatcher.find()) {
            try {
                String beforeMatch = qp.substring(lastEnd, hexMatcher.start());
                baos.write(beforeMatch.getBytes(StandardCharsets.ISO_8859_1));

                int hexValue = Integer.parseInt(hexMatcher.group(1), 16);
                baos.write(hexValue);

                lastEnd = hexMatcher.end();
            } catch (Exception e) {
                logger.warn("Invalid hex in QP: {} (keeping raw)", hexMatcher.group(0));
                String raw = qp.substring(lastEnd, hexMatcher.end());
                try {
                    baos.write(raw.getBytes(StandardCharsets.ISO_8859_1));
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
                lastEnd = hexMatcher.end();
            }
        }

        try {
            String remaining = qp.substring(lastEnd);
            baos.write(remaining.getBytes(StandardCharsets.ISO_8859_1));
        } catch (Exception e) {
            logger.error("Error adding remaining text: {}", e.getMessage());
        }

        try {
            String result = baos.toString(StandardCharsets.UTF_8);

            // Final clean: Loại multiple spaces/newlines, trim edges
            result = result.replaceAll("\\s+", " ").trim();

            logger.debug("QP Output preview: [{}]", AsyncUtils.createPreview(result));

            return result;
        } catch (Exception e) {
            logger.error("Error converting to UTF-8: {}", e.getMessage());
            return qp;
        }
    }

    private static boolean isValidBase64(String str) {
        if (str == null || str.isEmpty()) return false;
        return str.matches("^([A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$");
    }

    private static String htmlToPlainText(String html) {
        if (html == null) return "";
        html = decodeHtmlEntities(html);
        Pattern tagPattern = Pattern.compile("<[^>]*>");
        html = tagPattern.matcher(html).replaceAll(" ");
        html = html.replaceAll("\\s+", " ").trim();
        return html;
    }

    /**
     * Decodes HTML entities in the provided string and converts them into their corresponding characters.
     * Supports decoding named HTML entities (e.g., &amp;, &lt;, &gt;, etc.) and numeric character references (e.g., &#39; or &#123;).
     *
     * @param text the string containing HTML entities to be decoded.
     *             If {@code null}, an empty string is returned.
     * @return a string with all HTML entities decoded into their corresponding characters.
     */
    private static String decodeHtmlEntities(String text) {
        if (text == null) return "";
        text = text.replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&quot;", "\"")
                .replaceAll("&#39;", "'")
                .replaceAll("&nbsp;", " ");
        Pattern numPattern = Pattern.compile("&#(\\d+);");
        Matcher numMatcher = numPattern.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (numMatcher.find()) {
            try {
                char ch = (char) Integer.parseInt(numMatcher.group(1));
                numMatcher.appendReplacement(sb, String.valueOf(ch));
            } catch (Exception ignored) {
                numMatcher.appendReplacement(sb, numMatcher.group(0));
            }
        }
        numMatcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Check if response is error
     */
    public static boolean isError(String response, String tag) {
        return response.contains(tag + " NO") || response.contains(tag + " BAD");
    }

    /**
     * Decodes an encoded filename string based on MIME encoding standards.
     * If the input is null, empty, or not properly encoded, it returns a default
     * value or the original string.
     *
     * @param filename the encoded filename string to be decoded; it may be in MIME
     *                 encoded-word format (e.g., "=?charset?encoding?encoded text?=").
     * @return the decoded version of the filename if it was properly encoded, or
     *         the original string if decoding fails, or it doesn't match the
     *         encoding pattern. If the input is null or empty, it returns "attachment".
     */
    public static String decodeFilename(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "attachment";
        }

        if (!filename.startsWith("=?") || !filename.endsWith("?=")) {
            return filename;
        }

        try {
            Pattern pattern = Pattern.compile("=\\?([^?]+)\\?([BQ])\\?([^?]+)\\?=", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(filename);

            StringBuilder result = new StringBuilder();
            int lastEnd = 0;

            while (matcher.find()) {
                result.append(filename, lastEnd, matcher.start());

                String charset = matcher.group(1);
                String encoding = matcher.group(2).toUpperCase();
                String encodedText = matcher.group(3);

                byte[] decodedBytes;
                if (encoding.equals("B")) {
                    decodedBytes = Base64.getDecoder().decode(encodedText);
                } else {
                    decodedBytes = decodeQuotedPrintable(encodedText).getBytes(StandardCharsets.ISO_8859_1);
                }

                String decoded = new String(decodedBytes, charset);
                result.append(decoded);

                lastEnd = matcher.end();
            }

            result.append(filename.substring(lastEnd));

            return result.toString();

        } catch (Exception e) {
            logger.error("Failed to decode filename: {}", filename, e);
            return filename;
        }
    }

    public static void main(String[] args) {
        
        // Test case 1: Hex decode (tiếng Việt)
        String input1 = "Hi Ph=E1=BA=A1m";
        String output1 = decodeQuotedPrintable(input1);
        System.out.println("Test 1 - Input: " + input1);
        System.out.println("Test 1 - Expected: Hi Phạm");
        System.out.println("Test 1 - Actual: " + output1 + " (Đúng nếu = 'Hi Phạm')");
        System.out.println();

        // Test case 2: Soft break chuẩn (không space)
        String input2 = "We notice=\r\n d you";
        String output2 = decodeQuotedPrintable(input2);
        System.out.println("Test 2 - Input: " + input2.replace("\r\n", "\\r\\n"));
        System.out.println("Test 2 - Expected: We noticed you");
        System.out.println("Test 2 - Actual: " + output2 + " (Đúng nếu nối liền)");
        System.out.println();

        // Test case 3: Với space sau = (lỗi phổ biến)
        String input3 = "We notice= \r\n d you";
        String output3 = decodeQuotedPrintable(input3);
        System.out.println("Test 3 - Input: " + input3.replace("\r\n", "\\r\\n"));
        System.out.println("Test 3 - Expected: We noticed you");
        System.out.println("Test 3 - Actual: " + output3 + " (Sai nếu vẫn có space, cần fix regex)");
        System.out.println();

        // Test case 4: Sample từ email (truncated)
        String input4 = "<div dir=3D\"ltr\"><div>Hi Ph=E1=BA=A1m,</div><div><br /></div><div>We notice= d you started the process to upgrade to Cursor Pro but didn't complete it.</div>";
        String output4 = decodeQuotedPrintable(input4);
        System.out.println("Test 4 - Input preview: " + input4.substring(0, 100) + "...");
        System.out.println("Test 4 - Expected preview: <div dir=\"ltr\"><div>Hi Phạm,</div>...We noticed you...");
        System.out.println("Test 4 - Actual preview: " + output4.substring(0, 100) + "...");
        System.out.println();

        // Test case 5: UTF-8 Base64 encoded subject
        String subject1 = "=?UTF-8?B?VuG7qSB2aeG7h2MgbuG7mXAgbuG7mXAgbmjhu4i7jWM=?= 2025-2026";
        String decoded1 = decodeSubject(subject1);
        System.out.println("Test 5 - Input: " + subject1);
        System.out.println("Test 5 - Actual: " + decoded1);
        System.out.println();

        // Test case 6: Line-wrapped subject (like in the screenshot)
        String subject2 = "=?UTF-8?B?\nVuG7qSB2aeG7h2MgbuG7mXAgbuG7mXAgbmjhu4i7jWMgGjDrSBo4bu\n?= 2025-2026";
        String decoded2 = decodeSubject(subject2);
        System.out.println("Test 6 - Input (wrapped): " + subject2.replace("\n", "\\n"));
        System.out.println("Test 6 - Actual: " + decoded2);
        System.out.println();

        // Test case 7: Quoted-Printable subject
        String subject3 = "=?UTF-8?Q?Hello_Ph=E1=BA=A1m?=";
        String decoded3 = decodeSubject(subject3);
        System.out.println("Test 7 - Input: " + subject3);
        System.out.println("Test 7 - Expected: Hello Phạm");
        System.out.println("Test 7 - Actual: " + decoded3);
        System.out.println();

        // Test case 8: Multiple encoded sections
        String subject4 = "=?UTF-8?B?SGVsbG8=?= =?UTF-8?B?IFdvcmxk?=";
        String decoded4 = decodeSubject(subject4);
        System.out.println("Test 8 - Input: " + subject4);
        System.out.println("Test 8 - Expected: Hello World");
        System.out.println("Test 8 - Actual: " + decoded4);
        System.out.println();

        // Test case 9: Mixed encoded and plain text
        String subject5 = "Re: =?UTF-8?B?VGjDtG5nIGLDoW8=?= - Important";
        String decoded5 = decodeSubject(subject5);
        System.out.println("Test 9 - Input: " + subject5);
        System.out.println("Test 9 - Actual: " + decoded5);
        System.out.println();
        
        // Test case 10: Malformed subject with space in charset (the bug from screenshot)
        String subject6 = "=?U TF-8?B?xJDhu4MgY+G6rXAgbmjhuq10IGThu68gbGnhu4d1IGdpYSBo4bqhbiBCSOG7iVQgbsSDbSAyMDI2?=";
        String decoded6 = decodeSubject(subject6);
        System.out.println("Test 10 - Input (malformed with space in charset): " + subject6);
        System.out.println("Test 10 - Expected: Để cập nhật dữ liệu gia hạn BHYT năm 2026");
        System.out.println("Test 10 - Actual: " + decoded6);
        System.out.println();
        
        // Test case 11: Very malformed subject with newline in charset
        String subject7 = "=?UTF- 8?B?VuG7gSB2aeG7h2MgbuG7mXAgaOG7jWMgcGjDrQ==?=";
        String decoded7 = decodeSubject(subject7);
        System.out.println("Test 11 - Input (malformed with space after UTF-): " + subject7);
        System.out.println("Test 11 - Expected: Về việc nộp học phí");
        System.out.println("Test 11 - Actual: " + decoded7);
    }
}