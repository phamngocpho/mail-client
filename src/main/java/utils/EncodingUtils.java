package utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Utility class for encoding and decoding operations.
 * Handles Base64, Quoted-Printable, and other email encoding formats.
 */
public class EncodingUtils {
    private static final Logger logger = LoggerFactory.getLogger(EncodingUtils.class);

    /**
     * Decodes encoded text based on the specified encoding type.
     * Handles both Base64 and Quoted-Printable encodings with proper preprocessing.
     *
     * @param encodedText the encoded text to decode
     * @param encoding the encoding type ("B" for Base64, "Q" for Quoted-Printable)
     * @param charset the charset to use for the decoded string
     * @return the decoded string
     * @throws Exception if decoding fails
     */
    public static String decodeEncodedText(String encodedText, String encoding, String charset) throws Exception {
        String decoded;
        if ("B".equals(encoding)) {
            // Base64 decode
            // Remove any remaining whitespace from encoded text
            encodedText = encodedText.replaceAll("\\s+", "");
            byte[] decodedBytes = Base64.getDecoder().decode(encodedText);
            decoded = new String(decodedBytes, charset);
        } else {
            // Quoted-Printable decode
            // In Q encoding, underscore represents space
            encodedText = encodedText.replace("_", " ");
            decoded = decodeQuotedPrintable(encodedText);
        }
        return decoded;
    }

    /**
     * Decodes Quoted-Printable encoded text.
     * Handles soft line breaks and hex-encoded characters.
     *
     * @param qp the quoted-printable encoded text
     * @return the decoded text
     */
    public static String decodeQuotedPrintable(String qp) {
        if (qp == null) return "";
        
        logger.debug("QP Input length: {} chars", qp.length());
        
        // Clean up artifact pattern: số đơn lẻ + nhiều spaces/newlines + =
        qp = qp.replaceAll("^\\d{1,4}\\s{5,}=", "=");

        // Fix soft breaks: Remove = + optional spaces + \r?\n + optional spaces
        qp = qp.replaceAll("=\\s*\\r?\\n\\s*", "");

        // Decode =XX hex
        java.util.regex.Pattern hexPattern = java.util.regex.Pattern.compile("=([0-9A-Fa-f]{2})");
        java.util.regex.Matcher hexMatcher = hexPattern.matcher(qp);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
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

            // Remove trailing `=` (soft line break không hoàn chỉnh ở cuối)
            while (result.endsWith("=")) {
                result = result.substring(0, result.length() - 1).trim();
            }
            
            // Final clean: Loại multiple spaces/newlines, trim edges
            result = result.replaceAll("\\s+", " ").trim();

            logger.debug("QP Output length: {} chars", result.length());

            return result;
        } catch (Exception e) {
            logger.error("Error converting to UTF-8: {}", e.getMessage());
            return qp;
        }
    }

    /**
     * Encodes text to Base64.
     *
     * @param text the text to encode
     * @return the Base64 encoded string
     */
    public static String base64Encode(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Decodes Base64 text.
     *
     * @param encoded the Base64 encoded text
     * @return the decoded string
     */
    public static String base64Decode(String encoded) {
        return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
    }

    /**
     * Checks if a string is valid Base64.
     *
     * @param str the string to check
     * @return true if valid Base64, false otherwise
     */
    public static boolean isValidBase64(String str) {
        if (str == null || str.isEmpty()) return false;
        return str.matches("^([A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$");
    }

    /**
     * Normalizes charset name to standard format.
     *
     * @param charset the charset name to normalize
     * @return the normalized charset name
     */
    public static String normalizeCharset(String charset) {
        if (charset == null || charset.isEmpty()) {
            return "UTF-8";
        }

        charset = charset.toUpperCase().trim();

        return switch (charset) {
            case "UTF8" -> "UTF-8";
            case "ISO-8859-1", "ISO8859-1", "LATIN1" -> "ISO-8859-1";
            case "US-ASCII", "ASCII" -> "US-ASCII";
            default -> charset;
        };
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
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "=\\?([^?]+)\\?([BQ])\\?([^?]+)\\?=", 
                java.util.regex.Pattern.CASE_INSENSITIVE
            );
            java.util.regex.Matcher matcher = pattern.matcher(filename);

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
            // If decoding fails, return the original filename
            return filename;
        }
    }
}

