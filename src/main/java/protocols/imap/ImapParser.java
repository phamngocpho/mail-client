package protocols.imap;

import models.Email;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
     * Format: * 1 FETCH (FLAGS (\Seen) BODY[HEADER.FIELDS (FROM TO SUBJECT DATE)] {123}
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
     * Example: FLAGS (\Seen \Flagged)
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
     * Extract header content từ BODY[HEADER.FIELDS ...]
     */
    private static String extractHeaders(String response) {
        // Tìm phần header giữa 2 dấu ngoặc nhọn hoặc sau HEADER.FIELDS
        int startIdx = response.indexOf("BODY[HEADER");
        if (startIdx == -1) return null;

        // Tìm vị trí bắt đầu của header content (sau dấu newline đầu tiên)
        int headerStart = response.indexOf("\r\n", startIdx);
        if (headerStart == -1) return null;

        // Tìm vị trí kết thúc (dấu đóng ngoặc hoặc end of string)
        int headerEnd = response.indexOf(")\r\n", headerStart);
        if (headerEnd == -1) headerEnd = response.length();

        return response.substring(headerStart + 2, headerEnd);
    }

    /**
     * Parse headers thành Email object
     */
    private static void parseHeaders(String headers, Email email) {
        String[] lines = headers.split("\r\n");
        String currentHeader = null;
        StringBuilder currentValue = new StringBuilder();

        for (String line : lines) {
            if (line.isEmpty()) continue;

            // Dòng mới của header (không bắt đầu bằng space/tab)
            if (!line.startsWith(" ") && !line.startsWith("\t")) {
                // Lưu header trước đó
                if (currentHeader != null) {
                    processHeader(currentHeader, currentValue.toString().trim(), email);
                }

                // Parse header mới
                int colonIdx = line.indexOf(':');
                if (colonIdx > 0) {
                    currentHeader = line.substring(0, colonIdx).trim().toUpperCase();
                    currentValue = new StringBuilder(line.substring(colonIdx + 1).trim());
                }
            } else {
                // Dòng tiếp theo của header (folded)
                currentValue.append(" ").append(line.trim());
            }
        }

        // Lưu header cuối cùng
        if (currentHeader != null) {
            processHeader(currentHeader, currentValue.toString().trim(), email);
        }
    }

    /**
     * Process từng header field
     */
    private static void processHeader(String header, String value, Email email) {
        switch (header) {
            case "FROM":
                email.setFrom(cleanEmailAddress(value));
                break;
            case "TO":
                String[] toAddresses = value.split(",");
                for (String addr : toAddresses) {
                    email.addTo(cleanEmailAddress(addr.trim()));
                }
                break;
            case "CC":
                String[] ccAddresses = value.split(",");
                for (String addr : ccAddresses) {
                    email.addCc(cleanEmailAddress(addr.trim()));
                }
                break;
            case "SUBJECT":
                email.setSubject(decodeSubject(value));
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
     * Clean email address (remove name part)
     * Example: "John Doe <john@example.com>" -> "john@example.com"
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
     * Decode subject (handle encoded-word format)
     * Example: =?UTF-8?B?SGVsbG8=?= -> Hello
     */
    private static String decodeSubject(String subject) {
        // Đơn giản hóa: chỉ handle base64 UTF-8
        Pattern pattern = Pattern.compile("=\\?UTF-8\\?B\\?([^?]+)\\?=");
        Matcher matcher = pattern.matcher(subject);

        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String encoded = matcher.group(1);
            String decoded = new String(Base64.getDecoder().decode(encoded));
            matcher.appendReplacement(result, Matcher.quoteReplacement(decoded));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * Parse date từ RFC 2822 format
     * Example: Thu, 13 Feb 2025 12:34:56 +0700
     */
    private static Date parseDate(String dateStr) {
        try {
            // Các format phổ biến
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
        return new Date(); // Fallback to current date
    }

    /**
     * Parse message count từ SELECT response
     * Example: * 42 EXISTS
     */
    public static int parseMessageCount(String response) {
        Pattern pattern = Pattern.compile("\\* (\\d+) EXISTS");
        Matcher matcher = pattern.matcher(response);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return 0;
    }

    public static EmailBody parseEmailBody(String response) {
        EmailBody emailBody = extractRawBody(response);
        String boundary = detectBoundary(response);
        emailBody.attachments = parseAttachments(response, boundary);
        if (emailBody.plainText.isEmpty() && emailBody.html.isEmpty()) {
            return emailBody;
        }

        if (!emailBody.plainText.isEmpty()) {
            String encoding = detectEncodingForPart(response, emailBody.plainText);
            emailBody.plainText = decodeContent(emailBody.plainText, encoding);
        }

        // Decode HTML nếu có
        if (!emailBody.html.isEmpty()) {
            String encoding = detectEncodingForPart(response, emailBody.html);
            emailBody.html = decodeContent(emailBody.html, encoding);
        }

        return emailBody;
    }

    /**
     * Extract raw body content, tách multipart chính xác hơn
     */
    private static EmailBody extractRawBody(String response) {
        EmailBody body = new EmailBody();
        String boundary = detectBoundary(response);

        if (boundary == null) {
            // Non-multipart: detect xem là HTML hay plain
            int bodyStart = response.indexOf("BODY[TEXT]");
            if (bodyStart > 10) {
                bodyStart += 10;
                int end = response.indexOf("\r\n", bodyStart);
                String content = response.substring(bodyStart, end > 0 ? end : response.length()).trim();

                if (response.toLowerCase().contains("text/html")) {
                    body.html = content;
                    body.plainText = htmlToPlainText(content);
                } else {
                    body.plainText = content;
                }
            }
            return body;
        }

        // Split parts bằng boundary
        String[] parts = response.split(Pattern.quote(boundary), -1);

        for (int i = 1; i < parts.length - 1; i++) {
            String part = parts[i].trim();

            // Parse text/plain
            if (part.toLowerCase().contains("text/plain")) {
                int headerEnd = part.indexOf("\r\n\r\n");
                if (headerEnd != -1) {
                    String content = part.substring(headerEnd + 4).trim();
                    int nextBound = content.indexOf(boundary);
                    if (nextBound != -1) {
                        content = content.substring(0, nextBound).trim();
                    }
                    body.plainText = content;
                }
            }

            // Parse text/html
            if (part.toLowerCase().contains("text/html")) {
                int headerEnd = part.indexOf("\r\n\r\n");
                if (headerEnd != -1) {
                    String content = part.substring(headerEnd + 4).trim();
                    int nextBound = content.indexOf(boundary);
                    if (nextBound != -1) {
                        content = content.substring(0, nextBound).trim();
                    }
                    body.html = content;
                }
            }
        }

        // Nếu chỉ có HTML, tạo plain text từ HTML
        if (body.plainText.isEmpty() && !body.html.isEmpty()) {
            body.plainText = htmlToPlainText(body.html);
        }

        return body;
    }

    /**
     * Parse attachments từ multipart response
     */
    private static List<Attachment> parseAttachments(String response, String boundary) {
        List<Attachment> attachments = new ArrayList<>();

        if (boundary == null) return attachments;

        String[] parts = response.split(Pattern.quote(boundary), -1);

        for (int i = 1; i < parts.length - 1; i++) {
            String part = parts[i].trim();

            if (part.toLowerCase().contains("content-disposition:")
                    && part.toLowerCase().contains("attachment")) {

                // Extract filename
                Pattern filenamePattern = Pattern.compile(
                        "filename=\"?([^\"\\r\\n]+)\"?",
                        Pattern.CASE_INSENSITIVE
                );
                Matcher matcher = filenamePattern.matcher(part);
                String filename = matcher.find() ? matcher.group(1) : "unknown";

                // Extract content type
                Pattern typePattern = Pattern.compile(
                        "Content-Type:\\s*([^;\\r\\n]+)",
                        Pattern.CASE_INSENSITIVE
                );
                Matcher typeMatcher = typePattern.matcher(part);
                String contentType = typeMatcher.find()
                        ? typeMatcher.group(1).trim()
                        : "application/octet-stream";

                // Extract encoding
                String encoding = detectEncodingForPart(response, part);

                // Extract data
                int headerEnd = part.indexOf("\r\n\r\n");
                if (headerEnd != -1) {
                    String content = part.substring(headerEnd + 4);

                    // Loại bỏ boundary cuối nếu có
                    int nextBoundary = content.indexOf("--" + boundary.substring(2));
                    if (nextBoundary > 0) {
                        content = content.substring(0, nextBoundary).trim();
                    }

                    logger.debug("Attachment: {}", filename);
                    logger.debug("Content-Type: {}", contentType);
                    logger.debug("Encoding: {}", encoding);
                    logger.debug("Raw content length: {}", content.length());

                    byte[] data = decodeAttachmentData(content, encoding);

                    logger.debug("Decoded data length: {}", data.length);
                    logger.debug("First 50 bytes: {}",
                            Arrays.toString(Arrays.copyOf(data, Math.min(50, data.length))));

                    // Validate file signature
                    if (data.length >= 4) {
                        int b1 = data[0] & 0xFF;
                        int b2 = data[1] & 0xFF;
                        int b3 = data[2] & 0xFF;
                        int b4 = data[3] & 0xFF;
                        logger.debug("File signature: [0x{}, 0x{}, 0x{}, 0x{}]",
                                Integer.toHexString(b1),
                                Integer.toHexString(b2),
                                Integer.toHexString(b3),
                                Integer.toHexString(b4));

                        // Check file type
                        if (b1 == 0x50 && b2 == 0x4B && b3 == 0x03 && b4 == 0x04) {
                            logger.debug("Valid ZIP/Office file (.docx/.xlsx/.pptx)");
                        } else if (b1 == 0x25 && b2 == 0x50 && b3 == 0x44 && b4 == 0x46) {
                            logger.debug("Valid PDF file");
                        } else if (b1 == 0xFF && b2 == 0xD8 && b3 == 0xFF) {
                            logger.debug("Valid JPEG file");
                        } else if (b1 == 0x89 && b2 == 0x50 && b3 == 0x4E && b4 == 0x47) {
                            logger.debug("Valid PNG file");
                        } else {
                            logger.warn("Unknown or corrupt file signature!");
                        }
                    }

                    attachments.add(new Attachment(filename, contentType, data));
                }
            }
        }

        return attachments;
    }

    private static byte[] decodeAttachmentData(String content, String encoding) {
        try {
            encoding = encoding.toLowerCase().trim();

            logger.debug("Decoding with encoding: {}", encoding);
            logger.debug("Content preview (first 100 chars): {}",
                    content.substring(0, Math.min(100, content.length())));

            switch (encoding) {
                case "base64":
                    // Clean Base64 string
                    String cleanBase64 = content
                            .replaceAll("[\\r\\n\\s]+", "")      // Loại bỏ whitespace
                            .replaceAll("[^A-Za-z0-9+/=]", ""); // Chỉ giữ ký tự hợp lệ

                    // Padding
                    while (cleanBase64.length() % 4 != 0) {
                        cleanBase64 += "=";
                    }

                    logger.debug("Cleaned Base64 length: {}", cleanBase64.length());

                    try {
                        byte[] decoded = Base64.getDecoder().decode(cleanBase64);
                        logger.debug("Successfully decoded {} bytes from Base64", decoded.length);

                        // Verify signature
                        if (decoded.length >= 4) {
                            int b1 = decoded[0] & 0xFF;
                            int b2 = decoded[1] & 0xFF;
                            int b3 = decoded[2] & 0xFF;
                            int b4 = decoded[3] & 0xFF;
                            logger.debug("Decoded signature: [0x{}, 0x{}, 0x{}, 0x{}]",
                                    Integer.toHexString(b1), Integer.toHexString(b2),
                                    Integer.toHexString(b3), Integer.toHexString(b4));
                        }

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
                    // Nếu data trông giống Base64, decode
                    if (content.matches("^[A-Za-z0-9+/=\\s]+$") && content.startsWith("UEs")) {
                        logger.warn("8bit/7bit data looks like Base64, attempting decode...");
                        return decodeAttachmentData(content, "base64");
                    }

                    // Binary data - giữ nguyên
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
     * Decode content theo encoding
     */
    private static String decodeContent(String content, String encoding) {
        if (content == null || content.isEmpty()) return "";

        try {
            switch (encoding.toLowerCase()) {
                case "base64":
                    StringBuilder cleanBase64 = new StringBuilder(
                            content.replaceAll("[\r\n]+", "").replaceAll("\\s+", "")
                    );
                    while (cleanBase64.length() % 4 != 0) {
                        cleanBase64.append("=");
                    }
                    if (isValidBase64(cleanBase64.toString())) {
                        byte[] decodedBytes = Base64.getDecoder().decode(cleanBase64.toString());
                        return new String(decodedBytes, StandardCharsets.UTF_8);
                    }
                    break;

                case "quoted-printable":
                    return decodeQuotedPrintable(content);

                default:
                    return content.trim();
            }
        } catch (Exception e) {
            logger.error("Decode error: {}", e.getMessage());
        }

        return content.trim();
    }

    /**
     * Detect boundary từ Content-Type
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
     * Detect encoding cho part cụ thể (tìm trong full response gần part)
     */
    private static String detectEncodingForPart(String response, String part) {
        // Tìm trong part
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

        // Tìm trong toàn bộ response
        matcher = pattern.matcher(response);
        if (matcher.find()) {
            String encoding = matcher.group(1).trim();
            logger.debug("Found encoding in response: {}", encoding);
            return encoding;
        }

        // Nếu không tìm thấy encoding, kiểm tra xem có phải Base64 không
        int headerEnd = part.indexOf("\r\n\r\n");
        if (headerEnd != -1) {
            String content = part.substring(headerEnd + 4).trim();

            // Lấy 100 ký tự đầu để test
            String sample = content.substring(0, Math.min(100, content.length()));

            // Base64 chỉ chứa: A-Z, a-z, 0-9, +, /, =
            boolean looksLikeBase64 = sample.matches("^[A-Za-z0-9+/=\\s]+$");

            // Kiểm tra signature của Base64-encoded ZIP
            if (looksLikeBase64 && sample.startsWith("UEs")) {
                logger.info("Auto-detected Base64 encoding (ZIP signature found)");
                return "base64";
            }

            // Kiểm tra tỷ lệ ký tự Base64
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
     * Decode quoted-printable cải thiện (handle =XX hex ở mọi vị trí)
     */
    private static String decodeQuotedPrintable(String qp) {
        if (qp == null) return "";
        // Remove soft line breaks
        qp = qp.replaceAll("=\\r?\\n", "");
        // Replace =XX hex với char
        Pattern hexPattern = Pattern.compile("=([0-9A-Fa-f]{2})");
        Matcher hexMatcher = hexPattern.matcher(qp);
        StringBuilder sb = new StringBuilder();
        while (hexMatcher.find()) {
            try {
                int hexValue = Integer.parseInt(hexMatcher.group(1), 16);
                hexMatcher.appendReplacement(sb, String.valueOf((char) hexValue));
            } catch (Exception ignored) {
                hexMatcher.appendReplacement(sb, hexMatcher.group(0));
            }
        }
        hexMatcher.appendTail(sb);
        return sb.toString();
    }

    private static boolean isValidBase64(String str) {
        if (str == null || str.isEmpty()) return false;
        // Regex strict hơn
        return str.matches("^([A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$");
    }

    private static boolean isUtf8Valid(String str) {
        try {
            StandardCharsets.UTF_8.newEncoder().encode(CharBuffer.wrap(str));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String htmlToPlainText(String html) {
        if (html == null) return "";
        else {
            html = decodeHtmlEntities(html);
            Pattern tagPattern = Pattern.compile("<[^>]*>");
            html = tagPattern.matcher(html).replaceAll(" ");
            html = html.replaceAll("\\s+", " ").trim();
            return html;
        }
    }

    private static String decodeHtmlEntities(String text) {
        if (text == null) return "";
        text = text.replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&quot;", "\"")
                .replaceAll("&#39;", "'")
                .replaceAll("&nbsp;", " ");
        // Numeric
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
     * Decode filename từ RFC 2047 format
     * Example: =?UTF-8?B?4bqibmgg...?= -> Ảnh chụp màn hình.png
     */
    public static String decodeFilename(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "attachment";
        }

        // Nếu không phải encoded format, trả về nguyên bản
        if (!filename.startsWith("=?") || !filename.endsWith("?=")) {
            return filename;
        }

        try {
            // Pattern: =?charset?encoding?encoded-text?=
            Pattern pattern = Pattern.compile("=\\?([^?]+)\\?([BQ])\\?([^?]+)\\?=", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(filename);

            StringBuilder result = new StringBuilder();
            int lastEnd = 0;

            while (matcher.find()) {
                // Append text trước encoded part
                result.append(filename, lastEnd, matcher.start());

                String charset = matcher.group(1);
                String encoding = matcher.group(2).toUpperCase();
                String encodedText = matcher.group(3);

                // Decode
                byte[] decodedBytes;
                if (encoding.equals("B")) {
                    // Base64
                    decodedBytes = Base64.getDecoder().decode(encodedText);
                } else {
                    // Quoted-Printable
                    decodedBytes = decodeQuotedPrintable(encodedText).getBytes(StandardCharsets.ISO_8859_1);
                }

                // Convert to string với charset
                String decoded = new String(decodedBytes, charset);
                result.append(decoded);

                lastEnd = matcher.end();
            }

            // Append phần còn lại
            result.append(filename.substring(lastEnd));

            return result.toString();

        } catch (Exception e) {
            logger.error("Failed to decode filename: {}", filename, e);
            return filename; // Fallback
        }
    }
}