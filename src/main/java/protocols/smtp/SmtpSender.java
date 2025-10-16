package protocols.smtp;

import models.Email;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.Constants;
import utils.NetworkUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * The SmtpSender class provides functionality for communicating with an SMTP server to send emails.
 * It supports establishing secure connections using TLS, authenticating with credentials, and sending 
 * emails with attachments as well as plain text content. This class handles protocol-level operations 
 * including EHLO, STARTTLS, and QUIT commands.
 * <p>
 * Note: This class throws SmtpException for errors related to SMTP operations.
 */
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
            logger.info("Connecting to {}:{}", host, port);

            if (port == Constants.SMTP_SSL_PORT) {
                // SSL connection (port 465)
                socket = NetworkUtils.createSSLSocket(
                        host, port,
                        null,
                        0
                );
            } else {
                // Plain connection (port 587 hoặc 25)
                socket = new Socket(host, port);
                socket.setSoTimeout(Constants.SOCKET_TIMEOUT);
            }

            logger.info("Connected to {}:{}", host, port);

            reader = NetworkUtils.createReader(socket);
            writer = NetworkUtils.createWriter(socket);

            // Đọc greeting
            String greeting = readResponse();
            logger.debug("← Server greeting: {}", greeting);

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
        logger.debug("→ {}", command);
        writer.println(command);

        String response = readMultilineResponse();
        logger.debug("← {}", response);
        if (!response.startsWith(Constants.SMTP_OK)) {
            throw new SmtpException(command, response, "EHLO failed");
        }
        logger.info("EHLO successful");
    }

    /**
     * STARTTLS - upgrade connection to TLS
     */
    private void startTLS(String host) throws SmtpException {
        try {
            String command = Constants.SMTP_STARTTLS;
            logger.debug("→ {}", command);
            writer.println(command);

            String response = readResponse();
            logger.debug("← {}", response);

            if (!response.startsWith(Constants.SMTP_READY)) {
                throw new SmtpException(command, response, "STARTTLS failed");
            }

            // Upgrade socket to TLS
            socket = NetworkUtils.upgradeToTLS(socket, host);
            reader = NetworkUtils.createReader(socket);
            writer = NetworkUtils.createWriter(socket);

            // Send EHLO again after TLS
            sendEhlo();

            logger.info("TLS established");
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
            logger.debug("→ {}", command);
            writer.println(command);

            String response = readResponse();
            logger.debug("← {}", response);

            if (!response.startsWith(Constants.SMTP_AUTH_CONTINUE)) {
                throw new SmtpException(command, response, "AUTH LOGIN not accepted");
            }

            // Send base64 encoded username
            String encodedUser = NetworkUtils.base64Encode(username);
            logger.debug("→ {} (username)", encodedUser);
            writer.println(encodedUser);

            response = readResponse();
            logger.debug("← {}", response);

            if (!response.startsWith("334")) {
                throw new SmtpException("Username rejected");
            }

            // Send base64 encoded password
            String encodedPass = NetworkUtils.base64Encode(password);
            logger.debug("→ **** (password)");
            writer.println(encodedPass);

            response = readResponse();
            logger.debug("← {}", response);

            if (!response.startsWith("235")) {
                throw new SmtpException("Authentication failed");
            }

            authenticated = true;
            logger.info("Authentication successful");
        } catch (SmtpException e) {
            throw e;
        } catch (Exception e) {
            throw new SmtpException("Authentication failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Sends an email using the SMTP protocol. This method takes an Email object containing
     * the email's details such as sender, recipients, subject, and body content.
     *
     * @param email an Email object representing the email to be sent. It should include the sender's
     *              address, recipient(s), subject, and body. The recipients must be specified, or
     *              the email will fail to send it.
     * @throws SmtpException if there is any error during the email-sending process, such as lack of
     *                       authentication, incorrect command responses, or communication issues
     *                       with the SMTP server.
     */
    public void sendEmail(Email email) throws SmtpException {
        if (!authenticated) {
            throw new SmtpException("Not authenticated");
        }

        try {
            // MAIL FROM
            String mailFrom = "MAIL FROM:<" + email.getFrom() + ">";
            logger.debug("→ {}", mailFrom);
            writer.println(mailFrom);

            String response = readResponse();
            logger.debug("← {}", response);
            if (!response.startsWith(Constants.SMTP_OK)) {
                throw new SmtpException(mailFrom, response, "MAIL FROM rejected");
            }

            // RCPT TO
            for (String to : email.getTo()) {
                String rcptTo = "RCPT TO:<" + to + ">";
                logger.debug("→ {}", rcptTo);
                writer.println(rcptTo);

                response = readResponse();
                logger.debug("← {}", response);
                if (!response.startsWith(Constants.SMTP_OK)) {
                    throw new SmtpException(rcptTo, response, "RCPT TO rejected: " + to);
                }
            }

            // DATA
            logger.debug("→ DATA");
            writer.println("DATA");

            response = readResponse();
            logger.debug("← {}", response);
            if (!response.startsWith(Constants.SMTP_START_MAIL)) {
                throw new SmtpException("DATA", response, "DATA command rejected");
            }

            // Send email content
            sendEmailContent(email);

            // End with.
            logger.debug("→ .");
            writer.println(".");

            response = readResponse();
            logger.debug("← {}", response);
            if (!response.startsWith(Constants.SMTP_OK)) {
                throw new SmtpException("Email rejected by server");
            }

            logger.info("Email sent successfully");
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
     * Sends the content of the provided Email object to the output stream. This method handles
     * formatting the email headers and body according to the MIME format, including support for
     * plain text and attachments.
     *
     * @param email an Email object containing the details of the email to send, including headers
     *              (e.g., sender, recipients, subject) and content (e.g., body, attachments).
     * @throws IOException if an I/O error occurs during writing the email content.
     */
    private void sendEmailContent(Email email) throws IOException {
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH);
        writer.println("Date: " + dateFormat.format(email.getDate() != null ? email.getDate() : new Date()));
        writer.println("From: " + email.getFrom());
        writer.println("To: " + String.join(", ", email.getTo()));
        if (email.getCc() != null && !email.getCc().isEmpty()) {
            writer.println("Cc: " + String.join(", ", email.getCc()));
        }
        writer.println("Subject: " + (email.getSubject() != null ? email.getSubject() : "(No Subject)"));
        writer.println("MIME-Version: 1.0");

        // Nếu có file đính kèm
        if (email.getAttachments() != null && !email.getAttachments().isEmpty()) {
            String boundary = "BOUNDARY_" + System.currentTimeMillis();
            writer.println("Content-Type: multipart/mixed; boundary=\"" + boundary + "\"");
            writer.println();
            writer.println("This is a multipart message in MIME format.");
            writer.println();

            // ---- Phần body (text)
            writer.println("--" + boundary);
            writer.println("Content-Type: text/plain; charset=UTF-8");
            writer.println("Content-Transfer-Encoding: 8bit");
            writer.println();
            sendBodyText(email.getBody());
            writer.println();

            // ---- Các phần file đính kèm
            for (File file : email.getAttachments()) {
                writer.println("--" + boundary);
                writer.println("Content-Type: application/octet-stream; name=\"" + file.getName() + "\"");
                writer.println("Content-Transfer-Encoding: base64");
                writer.println("Content-Disposition: attachment; filename=\"" + file.getName() + "\"");
                writer.println();

                // Gửi nội dung file (base64)
                sendFileAsBase64(file);
                writer.println();
            }

            // Kết thúc multipart
            writer.println("--" + boundary + "--");

        } else {
            // Không có file đính kèm → gửi text bình thường
            writer.println("Content-Type: text/plain; charset=UTF-8");
            writer.println("Content-Transfer-Encoding: 8bit");
            writer.println();
            sendBodyText(email.getBody());
        }
    }

    /**
     * Sends the body text of an email, properly escaping lines that start with a dot
     * according to SMTP protocol rules.
     *
     * @param body the email body text to send
     */
    private void sendBodyText(String body) {
        String bodyText = body != null ? body : "";
        for (String line : bodyText.split("\r?\n")) {
            if (line.startsWith(".")) {
                writer.println("." + line);
            } else {
                writer.println(line);
            }
        }
    }

    /**
     * Encodes the content of the given file to Base64 format and writes the encoded data
     * to the output stream in chunks suitable for MIME-compliant encoding. This is often
     * used for transmitting file attachments in email messages.
     *
     * @param file the file to be encoded and sent in Base64 format; must not be null
     * @throws IOException if an I/O error occurs while reading the file or writing the encoded data
     */
    private void sendFileAsBase64(File file) throws IOException {
        try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
            byte[] buffer = new byte[57]; // 57 bytes → 76 ký tự base64 (chuẩn MIME)
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                String encoded = java.util.Base64.getEncoder()
                        .encodeToString(java.util.Arrays.copyOf(buffer, bytesRead));
                writer.println(encoded);
            }
        }
    }


    /**
     * Quit và đóng kết nối
     */
    public void quit() throws SmtpException {
        if (!connected) return;

        try {
            logger.debug("→ QUIT");
            writer.println("QUIT");

            String response = readResponse();
            logger.debug("← {}", response);

            close();
            logger.info("Connection closed");
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

    /**
     * Reads a multi-line response from the SMTP server until the end of the response is identified.
     * A multi-line response is formatted in such a way that lines begin with a status code,
     * and the last line of the response has a space following the status code.
     *
     * @return the complete multi-line response from the SMTP server as a single string,
     *         with each line separated by a newline character.
     * @throws SmtpException if there is an error while reading the response from the server.
     */
    private String readMultilineResponse() throws SmtpException {
        StringBuilder response = new StringBuilder();
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line).append("\n");
                logger.debug("← {}", line);

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