package utils;

import models.Email;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.List;

/**
 * Helper class for composing emails (reply, forward, etc.)
 * Handles the logic of preparing email drafts for different scenarios
 */
public class EmailComposerHelper {
    private static final Logger logger = LoggerFactory.getLogger(EmailComposerHelper.class);
    
    /**
     * Prepare a reply draft from an original email
     * 
     * @param originalEmail The email to reply to
     * @return EmailDraft object containing reply data
     */
    public static EmailDraft prepareReply(Email originalEmail) {
        if (originalEmail == null) {
            logger.warn("Cannot prepare reply: original email is null");
            return new EmailDraft();
        }
        
        EmailDraft draft = new EmailDraft();
        
        // Set recipient (reply to sender)
        String replyTo = EmailUtils.extractEmailAddress(originalEmail.getFrom());
        draft.setTo(replyTo);
        
        // Set subject with "Re: " prefix
        String subject = originalEmail.getSubject() != null ? originalEmail.getSubject() : "";
        if (!subject.toLowerCase().startsWith("re:")) {
            subject = "Re: " + subject;
        }
        draft.setSubject(subject);
        
        // Set body with quoted original message
        draft.setBody(createQuotedBody(originalEmail, "--- Original Message ---"));
        
        logger.debug("Prepared reply to: {}", replyTo);
        return draft;
    }
    
    /**
     * Prepare a forward draft from an original email
     * 
     * @param originalEmail The email to forward
     * @return EmailDraft object containing forward data
     */
    public static EmailDraft prepareForward(Email originalEmail) {
        if (originalEmail == null) {
            logger.warn("Cannot prepare forward: original email is null");
            return new EmailDraft();
        }
        
        EmailDraft draft = new EmailDraft();
        
        // To field is empty for forward (user will fill it)
        draft.setTo("");
        
        // Set subject with "Fwd: " prefix
        String subject = originalEmail.getSubject() != null ? originalEmail.getSubject() : "";
        if (!subject.toLowerCase().startsWith("fwd:") && !subject.toLowerCase().startsWith("fw:")) {
            subject = "Fwd: " + subject;
        }
        draft.setSubject(subject);
        
        // Set body with forwarded message
        draft.setBody(createQuotedBody(originalEmail, "--- Forwarded Message ---"));
        
        // Copy attachments
        if (originalEmail.getAttachments() != null && !originalEmail.getAttachments().isEmpty()) {
            draft.setAttachments(originalEmail.getAttachments());
        }
        
        logger.debug("Prepared forward for email: {}", originalEmail.getSubject());
        return draft;
    }
    
    /**
     * Create quoted body with original message
     * 
     * @param email The original email
     * @param header The header text (e.g., "--- Original Message ---")
     * @return Quoted body string
     */
    private static String createQuotedBody(Email email, String header) {
        StringBuilder body = new StringBuilder();
        body.append("\n\n");
        body.append(header).append("\n");
        body.append("From: ").append(email.getFrom() != null ? email.getFrom() : "Unknown").append("\n");
        
        // Add date if available
        if (email.getDate() != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("EEE, MMM dd, yyyy 'at' hh:mm a");
            body.append("Date: ").append(sdf.format(email.getDate())).append("\n");
        }
        
        body.append("Subject: ").append(email.getSubject() != null ? email.getSubject() : "(No Subject)").append("\n");
        body.append("\n");
        
        // Add original body - strip HTML tags if present to get plain text
        String originalBody = email.getBody() != null ? email.getBody() : "";

        // If body contains HTML, strip tags and convert to plain text
        if (originalBody.trim().startsWith("<html>") || originalBody.contains("<body>")) {
            // First, remove script and style content completely
            originalBody = originalBody.replaceAll("(?i)<script[^>]*>.*?</script>", "");
            originalBody = originalBody.replaceAll("(?i)<style[^>]*>.*?</style>", "");

            // Replace <br> and <p> tags with newlines before stripping other tags
            originalBody = originalBody.replaceAll("(?i)<br\\s*/?>", "\n");
            originalBody = originalBody.replaceAll("(?i)<p[^>]*>", "\n");
            originalBody = originalBody.replaceAll("(?i)</p>", "\n");
            originalBody = originalBody.replaceAll("(?i)<div[^>]*>", "\n");
            originalBody = originalBody.replaceAll("(?i)</div>", "\n");

            // Remove all remaining HTML tags
            originalBody = originalBody.replaceAll("<[^>]+>", "");

            // Decode common HTML entities
            originalBody = originalBody.replace("&nbsp;", " ")
                                     .replace("&lt;", "<")
                                     .replace("&gt;", ">")
                                     .replace("&amp;", "&")
                                     .replace("&quot;", "\"")
                                     .replace("&#39;", "'")
                                     .replace("&apos;", "'");

            // Clean up multiple newlines
            originalBody = originalBody.replaceAll("\n{3,}", "\n\n");

            // Trim whitespace from each line
            String[] lines = originalBody.split("\n");
            StringBuilder cleaned = new StringBuilder();
            for (String line : lines) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    cleaned.append(trimmed).append("\n");
                }
            }
            originalBody = cleaned.toString().trim();
        }

        body.append(originalBody);
        
        return body.toString();
    }
    
    /**
     * Email draft data class
     */
    public static class EmailDraft {
        private String to;
        private String subject;
        private String body;
        private List<File> attachments;
        
        public EmailDraft() {
            this.to = "";
            this.subject = "";
            this.body = "";
        }
        
        public String getTo() {
            return to;
        }
        
        public void setTo(String to) {
            this.to = to;
        }
        
        public String getSubject() {
            return subject;
        }
        
        public void setSubject(String subject) {
            this.subject = subject;
        }
        
        public String getBody() {
            return body;
        }
        
        public void setBody(String body) {
            this.body = body;
        }
        
        public List<File> getAttachments() {
            return attachments;
        }
        
        public void setAttachments(List<File> attachments) {
            this.attachments = attachments;
        }
    }
}

