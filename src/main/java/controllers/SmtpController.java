package controllers;

import models.Email;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import protocols.smtp.SmtpException;
import services.SmtpService;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

/**
 * The SmtpController class is responsible for managing SMTP configurations
 * and sending emails. It provides methods to configure SMTP settings either
 * explicitly or based on IMAP credentials, and facilitates email-sending
 * operations with additional functionalities such as CC and quick send.
 * <p>
 * The SmtpController uses an underlying SmtpService for handling actual SMTP
 * operations. It maintains the state related to SMTP configuration and supports
 * property change listeners for monitoring changes to its properties.
 */
public class SmtpController {
    private static SmtpController instance;
    private final SmtpService smtpService;
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    private String smtpHost;
    private int smtpPort;
    private boolean useTLS;
    private String username;
    private String password;

    private boolean isConfigured = false;
    private static final Logger logger = LoggerFactory.getLogger(SmtpController.class);

    private SmtpController() {
        this.smtpService = new SmtpService();
    }

    public static SmtpController getInstance() {
        if (instance == null) {
            instance = new SmtpController();
        }
        return instance;
    }
    
    /**
     * Reset instance (for logout)
     */
    public static void resetInstance() {
        if (instance != null) {
            instance.isConfigured = false;
            instance.smtpHost = null;
            instance.username = null;
            instance.password = null;
            instance = null;
            logger.info("SMTP controller has been reset");
        }
    }

    /**
     * Configures SMTP settings based on the provided IMAP server, username, and password.
     * Automatically derives the SMTP host from the IMAP host and uses secure communication via TLS.
     * Fires property change events for username and configuration status updates.
     *
     * @param imapHost the IMAP host to derive the SMTP host from
     * @param username the user account for SMTP authentication
     * @param password the password associated with the user account
     */
    public void configureFromImap(String imapHost, String username, String password) {
        String oldUsername = this.username;
        boolean wasConfigured = this.isConfigured;

        this.username = username;
        this.password = password;
        this.useTLS = true;
        this.smtpPort = 587;

        // Convert IMAP host to SMTP host
        if (imapHost.startsWith("imap.")) {
            this.smtpHost = imapHost.replace("imap.", "smtp.");
        } else if (imapHost.equals("outlook.office365.com")) {
            this.smtpHost = "smtp.office365.com";
        } else {
            this.smtpHost = imapHost.replace("imap", "smtp");
        }

        this.isConfigured = true;

        pcs.firePropertyChange("username", oldUsername, username);
        pcs.firePropertyChange("configured", wasConfigured, true);

        logger.info("SMTP auto-configured from IMAP: {} @ {}", username, smtpHost);
    }

    /**
     * Configure SMTP settings manually (nếu cần customize)
     */
    public void configure(String host, int port, boolean useTLS, String username, String password) {
        this.smtpHost = host;
        this.smtpPort = port;
        this.useTLS = useTLS;
        this.username = username;
        this.password = password;
        this.isConfigured = true;

        logger.info("SMTP configured: {} @ {}", username, host);
    }

    /**
     * Configure with default TLS port (587)
     */
    public void configure(String host, String username, String password) {
        configure(host, 587, true, username, password);
    }

    /**
     * Sends an email using the configured SMTP settings. If the "from" field is not set in the email object,
     * the configured username is used as the sender's email address.
     *
     * @param email the {@link Email} object containing details of the email to be sent,
     *              including recipient(s), subject, and message body.
     * @return {@code true} if the email was sent successfully; {@code false} if the operation failed
     *         (e.g., due to missing SMTP configuration or an exception during sending).
     */
    public boolean sendEmail(Email email) {
        if (!isConfigured) {
            logger.error("SMTP not configured. Please call configure() first.");
            return false;
        }

        try {
            // Connect and authenticate
            smtpService.connect(smtpHost, smtpPort, useTLS, username, password);

            // Set from if not set
            if (email.getFrom() == null || email.getFrom().isEmpty()) {
                email.setFrom(username);
            }

            // Send email
            smtpService.sendEmail(email);

            logger.info("Email sent successfully to {}", email.getTo());

            return true;

        } catch (SmtpException e) {
            logger.error("Failed to send email: {}", e.getMessage());
            return false;
        } finally {
            smtpService.disconnect();
        }
    }

    /**
     * Send simple email
     */
    public boolean sendEmail(String to, String subject, String body) {
        Email email = new Email(username, to, subject, body);
        return sendEmail(email);
    }

    /**
     * Send email with CC
     */
    public boolean sendEmailWithCC(String to, String[] cc, String subject, String body) {
        Email email = new Email(username, to, subject, body);
        for (String ccAddr : cc) {
            email.addCc(ccAddr);
        }
        return sendEmail(email);
    }

    /**
     * Quick send (for testing)
     */
    public static boolean quickSend(String host, String username, String password,
                                    String to, String subject, String body) {
        try {
            SmtpService.quickSend(host, username, password, to, subject, body);
            return true;
        } catch (SmtpException e) {
            logger.error("Quick send failed: {}", e.getMessage());
            return false;
        }
    }

    // Getters
    public boolean isConfigured() {
        return isConfigured;
    }

    public String getSmtpHost() {
        return smtpHost;
    }

    public String getUsername() {
        return username;
    }

    public boolean isConnected() {
        return smtpService.isConnected();
    }

    /**
     * Adds a {@link PropertyChangeListener} to the listener list.
     * The listener will be notified whenever a property change event is fired.
     * This method allows external objects to react to changes in the object's
     * observable properties.
     *
     * @param listener the {@link PropertyChangeListener} to add; must not be null
     */
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    /**
     * Removes a {@link PropertyChangeListener} from the listener list.
     * If the specified listener is not currently registered, no action is taken.
     * This method prevents the specified listener from receiving further property change events.
     *
     * @param listener the {@link PropertyChangeListener} to remove; must not be null
     */
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
    }
}