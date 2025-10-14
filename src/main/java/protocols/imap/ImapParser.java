package protocols.imap;

import models.Email;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
     * Extract header content từ BODY[HEADER.FIELDS ...]
     */
    private static String extractHeaders(String response) {
        int startIdx = response.indexOf("BODY[HEADER");
        if (startIdx == -1) return null;

        int headerStart = response.indexOf("\r\n", startIdx);
        if (headerStart == -1) return null;

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
     * Process từng header field
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
     * Decode subject (handle an encoded-word format)
     */
    private static String decodeSubject(String subject) {
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

    public static EmailBody parseEmailBody(String response) {
        EmailBody emailBody = extractRawBody(response);
        String boundary = detectBoundary(response);
        emailBody.attachments = parseAttachments(response, boundary);

        // Không decode lại vì đã decode trong extractRawBody và parseMultipart
        return emailBody;
    }

    /**
     * Extract raw body content
     */
    private static EmailBody extractRawBody(String response) {
        EmailBody body = new EmailBody();

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
     * Parse multipart content (có thể nested)
     */
    private static void parseMultipart(String content, String boundary, EmailBody body) {
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
                body.plainText = decodeContent(partContent.trim(), encoding, charset);
                logger.debug("Extracted plain text ({} chars) with charset: {}", body.plainText.length(), charset);
            }

            // Parse text/html
            if (headers.toLowerCase().contains("content-type: text/html") ||
                    headers.toLowerCase().contains("content-type:text/html")) {
                body.html = decodeContent(partContent.trim(), encoding, charset);
                logger.debug("Extracted HTML ({} chars) with charset: {}", body.html.length(), charset);
            }
        }
    }

    /**
     * Extract content từ email đơn giản (không multipart)
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

        return decodeContent(content, encoding, charset);
    }

    /**
     * Extract encoding từ headers của một part
     */
    private static String extractEncodingFromHeaders(String headers) {
        Pattern pattern = Pattern.compile(
                "Content-Transfer-Encoding:\\s*([^\\r\\n]+)",
                Pattern.CASE_INSENSITIVE
        );
        Matcher matcher = pattern.matcher(headers);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "7bit";
    }

    /**
     * Extract charset từ Content-Type header
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

                    logger.debug("Decoded data length: {}", data.length);

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
     * Decode content theo encoding và charset
     */
    private static String decodeContent(String content, String encoding, String charset) {
        if (content == null || content.isEmpty()) return "";

        try {
            // Normalize charset name
            charset = normalizeCharset(charset);

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
                    String decoded = decodeQuotedPrintable(content);
                    // Convert from ISO-8859-1 to a specified charset if needed
                    return new String(decoded.getBytes(StandardCharsets.ISO_8859_1), charset);

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
     * Normalize charset name (handle các variant khác nhau)b
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
     * Detect encoding cho part cụ thể
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
     * Decode quoted-printable
     */
    private static String decodeQuotedPrintable(String qp) {
        if (qp == null) return "";
        qp = qp.replaceAll("=\\r?\\n", "");
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
     * Decode filename từ RFC 2047 format
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
}