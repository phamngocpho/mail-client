package services;

import models.Email;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import protocols.smtp.SmtpException;
import protocols.smtp.SmtpSender;

public class SmtpService {
    private final SmtpSender sender;
    private String currentHost;
    private String currentUser;
    private boolean isConnected = false;
    private static final Logger logger = LoggerFactory.getLogger(SmtpService.class);

    public SmtpService() {
        this.sender = new SmtpSender();
    }

    /**
     * Connect và authenticate
     */
    public void connect(String host, String username, String password) throws SmtpException {
        connect(host, 587, true, username, password);
    }

    /**
     * Connect với custom port và TLS option
     */
    public void connect(String host, int port, boolean useTLS, String username, String password) throws SmtpException {
        try {
            sender.connect(host, port, useTLS);
            sender.authenticate(username, password);

            this.currentHost = host;
            this.currentUser = username;
            this.isConnected = true;
        } catch (SmtpException e) {
            isConnected = false;
            throw e;
        }
    }

    /**
     * Send email với Email object
     */
    public void sendEmail(Email email) throws SmtpException {
        if (!isConnected) {
            throw new SmtpException("Not connected. Call connect() first.");
        }

        try {
            sender.sendEmail(email);
        } catch (SmtpException e) {
            throw new SmtpException("Failed to send email: " + e.getMessage(), e);
        }
    }

    /**
     * Send simple email
     */
    public void sendEmail(String to, String subject, String body) throws SmtpException {
        if (!isConnected) {
            throw new SmtpException("Not connected. Call connect() first.");
        }

        if (currentUser == null) {
            throw new SmtpException("Sender email not set. Connect first.");
        }

        Email email = new Email(currentUser, to, subject, body);
        sendEmail(email);
    }

    /**
     * Send email với multiple recipients
     */
    public void sendEmail(String[] toAddresses, String subject, String body) throws SmtpException {
        if (!isConnected) {
            throw new SmtpException("Not connected. Call connect() first.");
        }

        Email email = new Email();
        email.setFrom(currentUser);
        for (String to : toAddresses) {
            email.addTo(to);
        }
        email.setSubject(subject);
        email.setBody(body);

        sendEmail(email);
    }

    /**
     * Send email với CC
     */
    public void sendEmailWithCC(String to, String[] ccAddresses, String subject, String body) throws SmtpException {
        if (!isConnected) {
            throw new SmtpException("Not connected. Call connect() first.");
        }

        Email email = new Email(currentUser, to, subject, body);
        for (String cc : ccAddresses) {
            email.addCc(cc);
        }

        sendEmail(email);
    }

    /**
     * Disconnect
     */
    public void disconnect() {
        try {
            if (isConnected) {
                sender.quit();
            }
        } catch (SmtpException e) {
            logger.error(e.getMessage(), e);
        } finally {
            sender.close();
            isConnected = false;
        }
    }

    /**
     * Test connection
     */
    public boolean testConnection(String host, int port, boolean useTLS, String username, String password) {
        try {
            SmtpSender testSender = new SmtpSender();
            testSender.connect(host, port, useTLS);
            testSender.authenticate(username, password);
            testSender.quit();
            testSender.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Quick send - connect, send, disconnect
     */
    public static void quickSend(String host, String username, String password,
                                 String to, String subject, String body) throws SmtpException {
        SmtpService service = new SmtpService();
        try {
            service.connect(host, username, password);
            service.sendEmail(to, subject, body);
        } finally {
            service.disconnect();
        }
    }

    // Getters
    public boolean isConnected() {
        return isConnected && sender.isAuthenticated();
    }

    public String getCurrentHost() {
        return currentHost;
    }

    public String getCurrentUser() {
        return currentUser;
    }
}