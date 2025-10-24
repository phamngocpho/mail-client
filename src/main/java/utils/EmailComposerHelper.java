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
        
        // Add original body
        String originalBody = email.getBody() != null ? email.getBody() : "";
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

