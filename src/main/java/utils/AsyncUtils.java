package utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import raven.toast.Notifications;

import javax.swing.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Utility class for handling common asynchronous operations and reducing code duplication.
 * Provides reusable patterns for SwingWorker operations and error handling.
 */
public class AsyncUtils {
    private static final Logger logger = LoggerFactory.getLogger(AsyncUtils.class);

    /**
     * Executes a background task with proper error handling and UI updates.
     * This method reduces code duplication for common SwingWorker patterns.
     *
     * @param backgroundTask the task to execute in background
     * @param onSuccess callback to execute on success (runs on EDT)
     * @param onError callback to execute on error (runs on EDT)
     * @param <T> the return type of the background task
     */
    public static <T> void executeAsync(Supplier<T> backgroundTask, Consumer<T> onSuccess, Consumer<Exception> onError) {
        SwingWorker<T, Void> worker = new SwingWorker<>() {
            @Override
            protected T doInBackground() {
                return backgroundTask.get();
            }

            @Override
            protected void done() {
                try {
                    T result = get();
                    if (onSuccess != null) {
                        onSuccess.accept(result);
                    }
                } catch (Exception e) {
                    logger.error("Background task failed", e);
                    if (onError != null) {
                        onError.accept(e);
                    }
                }
            }
        };
        worker.execute();
    }

    /**
     * Executes a background task that doesn't return a value (Void).
     * Useful for operations like updating flags, deleting emails, etc.
     *
     * @param backgroundTask the task to execute in background
     * @param onSuccess callback to execute on success (runs on EDT)
     * @param onError callback to execute on error (runs on EDT)
     */
    public static void executeVoidAsync(Runnable backgroundTask, Runnable onSuccess, Consumer<Exception> onError) {
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                backgroundTask.run();
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    if (onSuccess != null) {
                        onSuccess.run();
                    }
                } catch (Exception e) {
                    logger.error("Background task failed", e);
                    if (onError != null) {
                        onError.accept(e);
                    }
                }
            }
        };
        worker.execute();
    }

    /**
     * Shows a standardized error notification.
     * Centralizes error message formatting to reduce duplication.
     *
     * @param operation the operation that failed
     * @param error the exception that occurred
     */
    public static void showError(String operation, Exception error) {
        String message = String.format("Failed to %s: %s", operation, error.getMessage());
        Notifications.getInstance().show(Notifications.Type.ERROR, message);
        logger.error("Operation failed: {}", operation, error);
    }

    /**
     * Shows a simple error notification with custom message.
     *
     * @param message the error message to display
     */
    public static void showError(String message) {
        Notifications.getInstance().show(Notifications.Type.ERROR, message);
        logger.error("Error: {}", message);
    }

    /**
     * Creates a preview string with limited length for logging purposes.
     * Reduces duplication of substring operations in logging statements.
     *
     * @param content the content to preview
     * @param maxLength maximum length of the preview
     * @return preview string with length information
     */
    public static String createPreview(String content, int maxLength) {
        if (content == null) return "null";
        int actualLength = Math.min(maxLength, content.length());
        return String.format("%s (first %d chars)", 
            content.substring(0, actualLength), 
            content.length());
    }

    /**
     * Creates a preview string with default length of 200 characters.
     *
     * @param content the content to preview
     * @return preview string with length information
     */
    public static String createPreview(String content) {
        return createPreview(content, 200);
    }

    /**
     * Creates a logging message for content with length information.
     * Reduces duplication of "X chars" logging patterns.
     *
     * @param content the content to log
     * @param label the label for the content (e.g., "Headers", "Content", "Subject")
     * @return formatted logging message
     */
    public static String createLengthLog(String content, String label) {
        if (content == null) {
            return String.format("%s: null", label);
        }
        return String.format("%s: %d chars", label, content.length());
    }

    /**
     * Creates a logging message for content with length and preview.
     * Combines length info with preview for comprehensive logging.
     *
     * @param content the content to log
     * @param label the label for the content
     * @param previewLength maximum length for preview
     * @return formatted logging message with length and preview
     */
    public static String createDetailedLog(String content, String label, int previewLength) {
        if (content == null) {
            return String.format("%s: null", label);
        }
        return String.format("%s: %d chars, preview: %s", 
            label, content.length(), createPreview(content, previewLength));
    }

    /**
     * Creates a logging message for content with length and preview (default 200 chars).
     *
     * @param content the content to log
     * @param label the label for the content
     * @return formatted logging message with length and preview
     */
    public static String createDetailedLog(String content, String label) {
        return createDetailedLog(content, label, 200);
    }

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
            byte[] decodedBytes = java.util.Base64.getDecoder().decode(encodedText);
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
     * Creates a logging message for encoding and charset information.
     * Reduces duplication of "Encoding: {}, Charset: {}" patterns.
     *
     * @param encoding the encoding type
     * @param charset the charset
     * @return formatted logging message
     */
    public static String createEncodingLog(String encoding, String charset) {
        return String.format("Encoding: %s, Charset: %s", encoding, charset);
    }

    /**
     * Creates a standardized content logging message.
     * Reduces duplication of "Content: {}" patterns.
     *
     * @param content the content to log
     * @param previewLength maximum length for preview
     * @return formatted logging message
     */
    public static String createContentLog(String content, int previewLength) {
        return String.format("Content: %s", createDetailedLog(content, "content", previewLength));
    }

    /**
     * Creates a standardized content logging message with default 200 chars preview.
     *
     * @param content the content to log
     * @return formatted logging message
     */
    public static String createContentLog(String content) {
        return createContentLog(content, 200);
    }

    /**
     * Creates a standardized decoded content logging message.
     * Reduces duplication of "Decoded {}" patterns.
     *
     * @param content the decoded content to log
     * @param contentType the type of content (e.g., "plain text", "HTML")
     * @return formatted logging message
     */
    public static String createDecodedLog(String content, String contentType) {
        return String.format("Decoded %s", createDetailedLog(content, contentType));
    }

    /**
     * Creates a logging message for data length information.
     * Reduces duplication of "X length: {}" patterns.
     *
     * @param data the data array
     * @param label the label for the data
     * @return formatted logging message
     */
    public static String createDataLengthLog(byte[] data, String label) {
        if (data == null) {
            return String.format("%s: null", label);
        }
        return String.format("%s: %d bytes", label, data.length);
    }

    /**
     * Creates a logging message for fetched items before processing.
     * Reduces duplication of "Fetched X items before filtering" patterns.
     *
     * @param count the number of items fetched
     * @param itemType the type of items (e.g., "emails", "messages")
     * @return formatted logging message
     */
    public static String createFetchedLog(int count, String itemType) {
        return String.format("Fetched %d %s before filtering", count, itemType);
    }

    /**
     * Creates a logging message for fetched emails before filtering.
     * Convenience method for the most common use case.
     *
     * @param count the number of emails fetched
     * @return formatted logging message
     */
    public static String createFetchedEmailsLog(int count) {
        return createFetchedLog(count, "emails");
    }

    /**
     * Decodes Quoted-Printable encoded text.
     * This is a simplified version for utility use.
     *
     * @param qp the quoted-printable encoded text
     * @return the decoded text
     */
    private static String decodeQuotedPrintable(String qp) {
        if (qp == null) return "";
        
        // Fix soft breaks: Remove = + optional spaces + \r?\n + optional spaces
        qp = qp.replaceAll("=\\s*\\r?\\n\\s*", "");
        
        // Decode =XX hex
        java.util.regex.Pattern hexPattern = java.util.regex.Pattern.compile("=([0-9A-Fa-f]{2})");
        java.util.regex.Matcher hexMatcher = hexPattern.matcher(qp);
        
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        int lastEnd = 0;
        
        while (hexMatcher.find()) {
            try {
                String beforeMatch = qp.substring(lastEnd, hexMatcher.start());
                baos.write(beforeMatch.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
                
                int hexValue = Integer.parseInt(hexMatcher.group(1), 16);
                baos.write(hexValue);
                
                lastEnd = hexMatcher.end();
            } catch (Exception e) {
                // Keep raw text if hex decode fails
                String raw = qp.substring(lastEnd, hexMatcher.end());
                try {
                    baos.write(raw.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
                } catch (java.io.IOException ex) {
                    throw new RuntimeException(ex);
                }
                lastEnd = hexMatcher.end();
            }
        }
        
        try {
            String remaining = qp.substring(lastEnd);
            baos.write(remaining.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
        } catch (Exception e) {
            // Ignore
        }
        
        try {
            String result = baos.toString(java.nio.charset.StandardCharsets.UTF_8);
            // Final clean: Remove multiple spaces/newlines, trim edges
            result = result.replaceAll("\\s+", " ").trim();
            return result;
        } catch (Exception e) {
            return qp;
        }
    }
}
