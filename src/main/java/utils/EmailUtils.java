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
}
