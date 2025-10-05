package protocols.smtp;

import models.Email;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.Constants;
import utils.NetworkUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SmtpSender {
    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private boolean connected = false;
    private boolean authenticated = false;
    private String serverName;
    private static final Logger logger = LoggerFactory.getLogger(SmtpSender.class);

    /**
     * Connect to SMTP server với TLS (port 587)
     */
    public void connect(String host, int port, boolean useTLS) throws SmtpException {
        try {
            this.serverName = host;
            System.out.println("Connecting to " + host + ":" + port);

            if (port == Constants.SMTP_SSL_PORT) {
                // SSL connection (port 465)
                socket = NetworkUtils.createSSLSocket(host, port);
            } else {
                // Plain connection (port 587 hoặc 25)
                socket = new Socket(host, port);
                socket.setSoTimeout(Constants.SOCKET_TIMEOUT);
            }

            reader = NetworkUtils.createReader(socket);
            writer = NetworkUtils.createWriter(socket);

            // Đọc greeting
            String greeting = readResponse();
            System.out.println("← " + greeting);

            if (!greeting.startsWith(Constants.SMTP_READY)) {
                throw new SmtpException("Invalid server greeting: " + greeting);
            }

            // EHLO
            sendEhlo();

            // STARTTLS nếu cần
            if (useTLS && port == Constants.SMTP_TLS_PORT) {
                startTLS(host);
            }

            connected = true;
        } catch (IOException e) {
            throw new SmtpException("Connection failed: " + e.getMessage(), e);
        }
    }

    /**
     * Connect với default TLS port (587)
     */
    public void connect(String host) throws SmtpException {
        connect(host, Constants.SMTP_TLS_PORT, true);
    }

    /**
     * EHLO command
     */
    private void sendEhlo() throws SmtpException {
        String command = "EHLO " + serverName;
        System.out.println("→ " + command);
        writer.println(command);

        String response = readMultilineResponse();
        if (!response.startsWith(Constants.SMTP_OK)) {
            throw new SmtpException(command, response, "EHLO failed");
        }
        System.out.println("✓ EHLO successful");
    }

    /**
     * STARTTLS - upgrade connection to TLS
     */
    private void startTLS(String host) throws SmtpException {
        try {
            String command = Constants.SMTP_STARTTLS;
            System.out.println("→ " + command);
            writer.println(command);

            String response = readResponse();
            System.out.println("← " + response);

            if (!response.startsWith(Constants.SMTP_READY)) {
                throw new SmtpException(command, response, "STARTTLS failed");
            }

            // Upgrade socket to TLS
            socket = NetworkUtils.upgradeToTLS(socket, host);
            reader = NetworkUtils.createReader(socket);
            writer = NetworkUtils.createWriter(socket);

            // Send EHLO again after TLS
            sendEhlo();

            System.out.println("✓ TLS established");
        } catch (IOException e) {
            throw new SmtpException("STARTTLS failed: " + e.getMessage(), e);
        }
    }

    /**
     * Authenticate với username và password (AUTH LOGIN)
     */
    public void authenticate(String username, String password) throws SmtpException {
        if (!connected) {
            throw new SmtpException("Not connected to server");
        }

        try {
            // AUTH LOGIN
            String command = Constants.SMTP_AUTH_LOGIN;
            System.out.println("→ " + command);
            writer.println(command);

            String response = readResponse();
            System.out.println("← " + response);

            if (!response.startsWith(Constants.SMTP_AUTH_CONTINUE)) {
                throw new SmtpException(command, response, "AUTH LOGIN not accepted");
            }

            // Send base64 encoded username
            String encodedUser = NetworkUtils.base64Encode(username);
            System.out.println("→ " + encodedUser + " (username)");
            writer.println(encodedUser);

            response = readResponse();
            System.out.println("← " + response);

            if (!response.startsWith("334")) {
                throw new SmtpException("Username rejected");
            }

            // Send base64 encoded password
            String encodedPass = NetworkUtils.base64Encode(password);
            System.out.println("→ **** (password)");
            writer.println(encodedPass);

            response = readResponse();
            System.out.println("← " + response);

            if (!response.startsWith("235")) {
                throw new SmtpException("Authentication failed");
            }

            authenticated = true;
            System.out.println("✓ Authentication successful");
        } catch (SmtpException e) {
            throw e;
        } catch (Exception e) {
            throw new SmtpException("Authentication failed: " + e.getMessage(), e);
        }
    }

    /**
     * Send email
     */
    public void sendEmail(Email email) throws SmtpException {
        if (!authenticated) {
            throw new SmtpException("Not authenticated");
        }

        try {
            // MAIL FROM
            String mailFrom = "MAIL FROM:<" + email.getFrom() + ">";
            System.out.println("→ " + mailFrom);
            writer.println(mailFrom);

            String response = readResponse();
            System.out.println("← " + response);
            if (!response.startsWith(Constants.SMTP_OK)) {
                throw new SmtpException(mailFrom, response, "MAIL FROM rejected");
            }

            // RCPT TO
            for (String to : email.getTo()) {
                String rcptTo = "RCPT TO:<" + to + ">";
                System.out.println("→ " + rcptTo);
                writer.println(rcptTo);

                response = readResponse();
                System.out.println("← " + response);
                if (!response.startsWith(Constants.SMTP_OK)) {
                    throw new SmtpException(rcptTo, response, "RCPT TO rejected: " + to);
                }
            }

            // DATA
            System.out.println("→ DATA");
            writer.println("DATA");

            response = readResponse();
            System.out.println("← " + response);
            if (!response.startsWith(Constants.SMTP_START_MAIL)) {
                throw new SmtpException("DATA", response, "DATA command rejected");
            }

            // Send email content
            sendEmailContent(email);

            // End with .
            System.out.println("→ .");
            writer.println(".");

            response = readResponse();
            System.out.println("← " + response);
            if (!response.startsWith(Constants.SMTP_OK)) {
                throw new SmtpException("Email rejected by server");
            }

            System.out.println("✓ Email sent successfully");
        } catch (SmtpException e) {
            throw e;
        } catch (Exception e) {
            throw new SmtpException("Failed to send email: " + e.getMessage(), e);
        }
    }

    /**
     * Send email với parameters đơn giản
     */
    public void sendEmail(String from, String to, String subject, String body) throws SmtpException {
        Email email = new Email(from, to, subject, body);
        sendEmail(email);
    }

    /**
     * Send email content (headers + body)
     */
    private void sendEmailContent(Email email) {
        // Date header
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH);
        writer.println("Date: " + dateFormat.format(email.getDate() != null ? email.getDate() : new Date()));

        // From header
        writer.println("From: " + email.getFrom());

        // To header
        writer.println("To: " + String.join(", ", email.getTo()));

        // CC header (if any)
        if (email.getCc() != null && !email.getCc().isEmpty()) {
            writer.println("Cc: " + String.join(", ", email.getCc()));
        }

        // Subject header
        writer.println("Subject: " + (email.getSubject() != null ? email.getSubject() : "(No Subject)"));

        // MIME headers
        writer.println("MIME-Version: 1.0");
        writer.println("Content-Type: text/plain; charset=UTF-8");
        writer.println("Content-Transfer-Encoding: 8bit");

        // Empty line between headers and body
        writer.println();

        // Body
        String body = email.getBody() != null ? email.getBody() : "";
        // Escape lines starting with . (SMTP stuffing)
        String[] lines = body.split("\r?\n");
        for (String line : lines) {
            if (line.startsWith(".")) {
                writer.println("." + line);
            } else {
                writer.println(line);
            }
        }
    }

    /**
     * Quit và đóng kết nối
     */
    public void quit() throws SmtpException {
        if (!connected) return;

        try {
            System.out.println("→ QUIT");
            writer.println("QUIT");

            String response = readResponse();
            System.out.println("← " + response);

            close();
            System.out.println("✓ Connection closed");
        } catch (Exception e) {
            throw new SmtpException("QUIT failed: " + e.getMessage(), e);
        }
    }

    /**
     * Đóng kết nối
     */
    public void close() {
        try {
            if (reader != null) reader.close();
            if (writer != null) writer.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            logger.error("Socket close failed: {}", e.getMessage());
        }
        connected = false;
        authenticated = false;
    }

    // Helper Methods

    private String readResponse() throws SmtpException {
        try {
            return reader.readLine();
        } catch (IOException e) {
            throw new SmtpException("Failed to read response: " + e.getMessage(), e);
        }
    }

    private String readMultilineResponse() throws SmtpException {
        StringBuilder response = new StringBuilder();
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line).append("\n");
                System.out.println("← " + line);

                // Multiline response có format: "250-..." hoặc "250 ..." (dòng cuối)
                if (line.length() >= 4 && line.charAt(3) == ' ') {
                    break;
                }
            }
        } catch (IOException e) {
            throw new SmtpException("Failed to read response: " + e.getMessage(), e);
        }
        return response.toString();
    }

    // Getters
    public boolean isConnected() {
        return connected;
    }

    public boolean isAuthenticated() {
        return authenticated;
    }
}