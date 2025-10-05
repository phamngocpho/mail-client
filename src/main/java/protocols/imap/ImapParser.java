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

    public static String parseEmailBody(String response) {
        String rawBody = extractRawBody(response);
        if (rawBody.isEmpty()) return "";

        String encoding = detectEncodingForPart(response, rawBody);  // Detect cho part cụ thể
        String decodedBody;
        try {
            switch (encoding.toLowerCase()) {
                case "base64":
                    // Clean base64: Loại whitespace, \r\n từ lines
                    StringBuilder cleanBase64 = new StringBuilder(rawBody.replaceAll("[\r\n]+", "").replaceAll("\\s+", ""));
                    // Add padding thiếu
                    while (cleanBase64.length() % 4 != 0) {
                        cleanBase64.append("=");
                    }
                    if (isValidBase64(cleanBase64.toString())) {
                        byte[] decodedBytes = Base64.getDecoder().decode(cleanBase64.toString());
                        decodedBody = new String(decodedBytes, StandardCharsets.UTF_8);
                    } else {
                        logger.error("Invalid base64 detected, skipping decode.");
                        decodedBody = rawBody.trim();
                    }
                    break;
                case "quoted-printable":
                    decodedBody = decodeQuotedPrintable(rawBody);
                    break;
                default:
                    decodedBody = rawBody.trim();
                    break;
            }

            // Xử lý charset fallback
            if (!isUtf8Valid(decodedBody)) {
                try {
                    decodedBody = new String(decodedBody.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
                } catch (Exception ignored) {
                    // Keep as is
                }
            }

            // Clean HTML nếu có tags
            if (decodedBody.contains("<")) {
                decodedBody = htmlToPlainText(decodedBody);
            }

        } catch (Exception e) {
            logger.error("Body parse error: {}", e.getMessage());
            decodedBody = rawBody.trim();  // Fallback
        }

        return decodedBody.trim();
    }

    /**
     * Extract raw body content, tách multipart chính xác hơn
     */
    private static String extractRawBody(String response) {
        String boundary = detectBoundary(response);
        if (boundary == null) {
            // Non-multipart fallback
            int bodyStart = response.indexOf("BODY[TEXT]") + 10;
            if (bodyStart > 10) {
                int end = response.indexOf("\r\n", bodyStart);
                return response.substring(bodyStart, end > 0 ? end : response.length()).trim();
            }
            return "";
        }

        // Split parts bằng boundary
        String[] parts = response.split(Pattern.quote(boundary), -1);  // -1 để giữ empty parts
        String plainContent = "";

        for (int i = 1; i < parts.length - 1; i++) {  // Skip first/last
            String part = parts[i].trim();
            if (part.toLowerCase().contains("text/plain")) {
                // Tìm end headers (\r\n\r\n)
                int headerEnd = part.indexOf("\r\n\r\n");
                if (headerEnd != -1) {
                    plainContent = part.substring(headerEnd + 4).trim();  // Content sau
                    // Cut tại next boundary
                    int nextBound = plainContent.indexOf(boundary);
                    if (nextBound != -1) {
                        plainContent = plainContent.substring(0, nextBound).trim();
                    }
                    return plainContent;  // Found plain, return ngay
                }
            }
        }

        // Fallback HTML part
        for (int i = 1; i < parts.length - 1; i++) {
            String part = parts[i].trim();
            if (part.toLowerCase().contains("text/html")) {
                int headerEnd = part.indexOf("\r\n\r\n");
                if (headerEnd != -1) {
                    String htmlContent = part.substring(headerEnd + 4).trim();
                    int nextBound = htmlContent.indexOf(boundary);
                    if (nextBound != -1) {
                        htmlContent = htmlContent.substring(0, nextBound).trim();
                    }
                    plainContent = htmlToPlainText(htmlContent);  // Clean ngay
                    return plainContent;
                }
            }
        }

        return plainContent;
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
    private static String detectEncodingForPart(String response, String rawBody) {
        // Tìm encoding gần rawBody position
        int startPos = response.indexOf(rawBody);
        if (startPos == -1) startPos = 0;
        String context = response.substring(Math.max(0, startPos - 200), startPos + 200);

        Pattern encPattern = Pattern.compile("Content-Transfer-Encoding:\\s*([a-zA-Z0-9-]+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = encPattern.matcher(context);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
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

    // Giữ nguyên các helper khác: isValidBase64, isUtf8Valid, htmlToPlainText, decodeHtmlEntities
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
}