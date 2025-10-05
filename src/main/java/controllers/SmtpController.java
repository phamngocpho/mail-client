package controllers;

import models.Email;
import protocols.smtp.SmtpException;
import services.SmtpService;

public class SmtpController {
    private static SmtpController instance;
    private final SmtpService smtpService;

    private String smtpHost;
    private int smtpPort;
    private boolean useTLS;
    private String username;
    private String password;

    private boolean isConfigured = false;

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
     * Configure SMTP từ IMAP credentials
     * Tự động convert IMAP host thành SMTP host
     */
    public void configureFromImap(String imapHost, String username, String password) {
        this.username = username;
        this.password = password;
        this.useTLS = true;
        this.smtpPort = 587; // Default TLS port

        // Convert IMAP host to SMTP host
        if (imapHost.startsWith("imap.")) {
            this.smtpHost = imapHost.replace("imap.", "smtp.");
        } else if (imapHost.equals("outlook.office365.com")) {
            this.smtpHost = "smtp.office365.com";
        } else {
            // Fallback: try replacing imap with smtp
            this.smtpHost = imapHost.replace("imap", "smtp");
        }

        this.isConfigured = true;
        System.out.println("SMTP auto-configured: " + username + " @ " + smtpHost);
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

        System.out.println("SMTP configured: " + username + "@" + host);
    }

    /**
     * Configure with default TLS port (587)
     */
    public void configure(String host, String username, String password) {
        configure(host, 587, true, username, password);
    }

    /**
     * Send email
     */
    public boolean sendEmail(Email email) {
        if (!isConfigured) {
            System.err.println("SMTP not configured. Please call configure() first.");
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

            System.out.println("✓ Email sent successfully to " + email.getTo());

            return true;

        } catch (SmtpException e) {
            System.err.println("Failed to send email: " + e.getMessage());
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
     * Test SMTP connection
     */
    public boolean testConnection() {
        if (!isConfigured) {
            System.err.println("SMTP not configured. Please call configure() first.");
            return false;
        }

        return smtpService.testConnection(smtpHost, smtpPort, useTLS, username, password);
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
            System.err.println("Quick send failed: " + e.getMessage());
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
}