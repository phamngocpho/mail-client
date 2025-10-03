package protocols.imap;

import models.Email;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ImapParser {

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

        StringBuffer result = new StringBuffer();
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
            e.printStackTrace();
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

    /**
     * Parse email body từ FETCH response
     */
    public static String parseEmailBody(String response) {
        StringBuilder body = new StringBuilder();
        boolean inBody = false;

        String[] lines = response.split("\r\n");

        for (String line : lines) {
            // Bắt đầu body sau dòng "BODY[TEXT] {size}"
            if (line.contains("BODY[TEXT]")) {
                inBody = true;
                continue;
            }

            // Kết thúc khi gặp tagged response
            if (line.matches("^[A-Z0-9]+ (OK|NO|BAD).*")) {
                break;
            }

            if (inBody) {
                // Bỏ qua dòng chứa chỉ ")"
                if (line.trim().equals(")")) {
                    break;
                }
                body.append(line).append("\n");
            }
        }

        return body.toString().trim();
    }

    /**
     * Check if response is OK
     */
    public static boolean isOK(String response, String tag) {
        return response.contains(tag + " OK");
    }

    /**
     * Check if response is error
     */
    public static boolean isError(String response, String tag) {
        return response.contains(tag + " NO") || response.contains(tag + " BAD");
    }
}