package utils;

import models.Email;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Utility class for common email operations to reduce code duplication.
 * Provides reusable methods for email filtering, sorting, and validation.
 */
public class EmailUtils {
    private static final Logger logger = LoggerFactory.getLogger(EmailUtils.class);

    /**
     * Filters out invalid emails that don't have essential information.
     * Removes emails that have neither subject nor from address.
     *
     * @param emails the list of emails to filter
     * @return the filtered list of valid emails
     */
    public static List<Email> filterValidEmails(List<Email> emails) {
        if (emails == null) {
            return new ArrayList<>();
        }

        int beforeFilter = emails.size();
        
        emails.removeIf(email -> {
            boolean invalid = (email.getSubject() == null || email.getSubject().isEmpty()) && 
                              (email.getFrom() == null || email.getFrom().isEmpty());
            if (invalid) {
                logger.debug("Filtering out invalid email - Subject: [{}], From: [{}]", 
                    email.getSubject(), email.getFrom());
            }
            return invalid;
        });
        
        int afterFilter = emails.size();
        logger.info("Filtered {} invalid emails, remaining: {}", beforeFilter - afterFilter, afterFilter);
        
        return emails;
    }

    /**
     * Sorts emails by date in descending order (newest first).
     * Handles null dates by placing them at the end.
     *
     * @param emails the list of emails to sort
     * @return the sorted list of emails
     */
    public static List<Email> sortEmailsByDate(List<Email> emails) {
        if (emails == null) {
            return new ArrayList<>();
        }
        
        emails.sort(Comparator.comparing(Email::getDate, Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        return emails;
    }

    /**
     * Processes a list of emails by filtering invalid ones and sorting by date.
     * This is a common operation that combines filtering and sorting.
     *
     * @param emails the list of emails to process
     * @return the processed list of valid, sorted emails
     */
    public static List<Email> processEmails(List<Email> emails) {
        List<Email> validEmails = filterValidEmails(emails);
        return sortEmailsByDate(validEmails);
    }

    /**
     * Validates if an email has the minimum required information.
     *
     * @param email the email to validate
     * @return true if the email is valid, false otherwise
     */
    public static boolean isValidEmail(Email email) {
        if (email == null) {
            return false;
        }
        
        return (email.getSubject() != null && !email.getSubject().isEmpty()) || 
               (email.getFrom() != null && !email.getFrom().isEmpty());
    }

    /**
     * Gets a safe preview of email content for logging purposes.
     *
     * @param content the content to preview
     * @param maxLength maximum length of the preview
     * @return safe preview string
     */
    public static String getEmailContentPreview(String content, int maxLength) {
        if (content == null || content.isEmpty()) {
            return "empty";
        }
        
        int actualLength = Math.min(maxLength, content.length());
        return String.format("%s (length: %d)", 
            content.substring(0, actualLength), 
            content.length());
    }

    /**
     * Gets a safe preview of email content with a default length of 200 characters.
     *
     * @param content the content to preview
     * @return safe preview string
     */
    public static String getEmailContentPreview(String content) {
        return getEmailContentPreview(content, 200);
    }
    
    /**
     * Extract email address from string like "Name <email@domain.com>"
     * 
     * @param emailString email string có thể có format "Name <email@domain.com>" hoặc "email@domain.com"
     * @return email address (chỉ phần email, không có tên)
     */
    public static String extractEmailAddress(String emailString) {
        if (emailString == null || emailString.isEmpty()) {
            return "";
        }
        
        // Check if format is "Name <email@domain.com>"
        if (emailString.contains("<") && emailString.contains(">")) {
            int start = emailString.indexOf("<");
            int end = emailString.indexOf(">");
            if (start >= 0 && end > start) {
                return emailString.substring(start + 1, end).trim();
            }
        }
        
        return emailString.trim();
    }
    
    /**
     * Extract name from email address
     * "John Doe <john@example.com>" -> "John Doe"
     * "john@example.com" -> "john"
     * 
     * @param emailString email string
     * @return tên người gửi hoặc username nếu không có tên
     */
    public static String extractName(String emailString) {
        if (emailString == null || emailString.isEmpty()) {
            return "Unknown";
        }
        
        // Nếu có format "Name <email@domain.com>"
        if (emailString.contains("<")) {
            String name = emailString.substring(0, emailString.indexOf("<")).trim();
            return name.isEmpty() ? "Unknown" : name;
        }
        
        // Nếu chỉ có email, lấy phần trước @
        if (emailString.contains("@")) {
            return emailString.split("@")[0];
        }
        
        return emailString;
    }
    
    /**
     * Unwrap hard line breaks in plain text emails.
     * Email servers often insert line breaks at 76-78 characters per RFC 2822.
     * This method removes those hard breaks while preserving intentional paragraph breaks.
     * <p>
     * Rules:
     * - Keep line breaks after punctuation marks (. ! ? : ;)
     * - Keep line breaks before/after list items (starting with numbers or bullets)
     * - Remove line breaks in the middle of sentences (soft wrap)
     * 
     * @param text plain text email content
     * @return unwrapped text với formatting tự nhiên hơn
     */
    public static String unwrapPlainTextEmail(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        // Split into paragraphs (separated by double newlines or more)
        String[] paragraphs = text.split("\\n\\n+|\\r\\n\\r\\n+");
        
        StringBuilder result = new StringBuilder();
        
        for (int i = 0; i < paragraphs.length; i++) {
            String paragraph = paragraphs[i].trim();
            
            if (paragraph.isEmpty()) {
                continue;
            }
            
            // Process line by line trong paragraph
            StringBuilder unwrappedParagraph = unwrapParagraph(paragraph);

            // Dọn dẹp multiple spaces
            String cleaned = unwrappedParagraph.toString()
                    .replaceAll(" +", " ")  // Multiple spaces → single space
                    .trim();
            
            result.append(cleaned);
            
            // Add paragraph break (except for last paragraph)
            if (i < paragraphs.length - 1) {
                result.append("\n\n");
            }
        }
        
        return result.toString();
    }
    
    /**
     * Helper method to unwrap a single paragraph
     */
    private static StringBuilder unwrapParagraph(String paragraph) {
        String[] lines = paragraph.split("\\r?\\n");
        StringBuilder unwrappedParagraph = new StringBuilder();

        for (int j = 0; j < lines.length; j++) {
            String line = lines[j].trim();

            if (line.isEmpty()) {
                continue;
            }

            unwrappedParagraph.append(line);

            // Kiểm tra xem có nên giữ line break không
            if (j < lines.length - 1) {
                String nextLine = lines[j + 1].trim();

                // Giữ line break nếu:
                // 1. Dòng hiện tại kết thúc bằng dấu câu
                if (line.matches(".*[.!?:;]\\s*$")) {
                    unwrappedParagraph.append("\n");
                }
                // 2. Dòng tiếp theo bắt đầu bằng số hoặc bullet (list item)
                else if (nextLine.matches("^[\\d\\-*•]+[.)\\s].*")) {
                    unwrappedParagraph.append("\n");
                }
                // 3. Dòng hiện tại là list item
                else if (line.matches("^[\\d\\-*•]+[.)\\s].*")) {
                    unwrappedParagraph.append("\n");
                }
                // Ngược lại: unwrap (thêm space thay vì newline)
                else {
                    unwrappedParagraph.append(" ");
                }
            }
        }
        return unwrappedParagraph;
    }
    
    /**
     * Format file size from bytes to human-readable format
     * 
     * @param bytes file size in bytes
     * @return formatted string (e.g., "1.5 KB", "2.3 MB")
     */
    public static String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        }
    }
    
    /**
     * Check if email matches search query
     * Searches in sender (from), subject, body, and HTML body
     * 
     * @param email The email to check
     * @param query The search query (case-insensitive)
     * @return true if email matches the query, false otherwise
     */
    public static boolean matchesSearchQuery(Email email, String query) {
        if (query == null || query.isEmpty()) {
            return true;
        }
        
        String queryLower = query.toLowerCase();
        
        // Search in sender (from)
        if (email.getFrom() != null && email.getFrom().toLowerCase().contains(queryLower)) {
            return true;
        }
        
        // Search in subject
        if (email.getSubject() != null && email.getSubject().toLowerCase().contains(queryLower)) {
            return true;
        }
        
        // Search in body (if loaded)
        if (email.getBody() != null && email.getBody().toLowerCase().contains(queryLower)) {
            return true;
        }
        
        // Search in HTML body (if loaded)
        return email.getBodyHtml() != null && email.getBodyHtml().toLowerCase().contains(queryLower);
    }
    
    /**
     * Filter a list of emails by search query
     * 
     * @param emails List of emails to filter
     * @param query Search query
     * @return Filtered list of emails that match the query
     */
    public static List<Email> filterBySearchQuery(List<Email> emails, String query) {
        if (emails == null || emails.isEmpty()) {
            return new ArrayList<>();
        }
        
        if (query == null || query.isEmpty()) {
            return new ArrayList<>(emails);
        }
        
        List<Email> filtered = new ArrayList<>();
        for (Email email : emails) {
            if (matchesSearchQuery(email, query)) {
                filtered.add(email);
            }
        }
        
        logger.debug("Filtered {} emails by query '{}', found {} matches", 
                    emails.size(), query, filtered.size());
        return filtered;
    }
}
