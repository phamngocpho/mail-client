import models.Email;
import models.Folder;
import protocols.imap.ImapException;
import protocols.smtp.SmtpException;
import services.ImapService;
import services.SmtpService;

import java.util.List;
import java.util.Scanner;

public class MailDemo {

    private static final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        System.out.println("=================================");
        System.out.println("   Mail Client Demo (IMAP/SMTP)");
        System.out.println("=================================\n");

        while (true) {
            System.out.println("\n--- MAIN MENU ---");
            System.out.println("1. Test IMAP (Read Emails)");
            System.out.println("2. Test SMTP (Send Email)");
            System.out.println("3. Full Demo (Read + Send)");
            System.out.println("4. Quick Gmail Test");
            System.out.println("0. Exit");
            System.out.print("Choose option: ");

            String choice = scanner.nextLine().trim();

            try {
                switch (choice) {
                    case "1":
                        testImap();
                        break;
                    case "2":
                        testSmtp();
                        break;
                    case "3":
                        fullDemo();
                        break;
                    case "4":
                        quickGmailTest();
                        break;
                    case "0":
                        System.out.println("Goodbye!");
                        return;
                    default:
                        System.out.println("Invalid option!");
                }
            } catch (Exception e) {
                System.err.println("\n❌ Error: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * Test IMAP - đọc emails
     */
    private static void testImap() throws ImapException {
        System.out.println("\n=== IMAP TEST ===");

        // Input credentials
        System.out.print("IMAP Host (e.g., imap.gmail.com): ");
        String host = scanner.nextLine().trim();

        System.out.print("Email: ");
        String email = scanner.nextLine().trim();

        System.out.print("Password: ");
        String password = scanner.nextLine().trim();

        // Connect
        ImapService imapService = new ImapService();
        System.out.println("\nConnecting to " + host + "...");
        imapService.connect(host, email, password);

        System.out.println("✓ Connected successfully!\n");

        // List folders
        System.out.println("--- Available Folders ---");
        List<Folder> folders = imapService.listFolders();
        for (int i = 0; i < Math.min(folders.size(), 10); i++) {
            System.out.println((i + 1) + ". " + folders.get(i).getName());
        }

        // Fetch emails
        System.out.print("\nHow many recent emails to fetch? (default: 5): ");
        String countStr = scanner.nextLine().trim();
        int count = countStr.isEmpty() ? 5 : Integer.parseInt(countStr);

        System.out.println("\nFetching " + count + " recent emails from INBOX...");
        List<Email> emails = imapService.fetchRecentEmails("INBOX", count);

        System.out.println("\n--- Emails (" + emails.size() + ") ---");
        for (int i = 0; i < emails.size(); i++) {
            Email e = emails.get(i);
            System.out.println((i + 1) + ". " + e.getFrom());
            System.out.println("   Subject: " + e.getSubject());
            System.out.println("   Date: " + e.getDate());
            System.out.println("   Flags: " + e.getFlags());
            System.out.println();
        }

        // Disconnect
        imapService.disconnect();
        System.out.println("✓ IMAP test completed!");
    }

    /**
     * Test SMTP - gửi email
     */
    private static void testSmtp() throws SmtpException {
        System.out.println("\n=== SMTP TEST ===");

        // Input credentials
        System.out.print("SMTP Host (e.g., smtp.gmail.com): ");
        String host = scanner.nextLine().trim();

        System.out.print("Your Email: ");
        String fromEmail = scanner.nextLine().trim();

        System.out.print("Password: ");
        String password = scanner.nextLine().trim();

        // Connect
        SmtpService smtpService = new SmtpService();
        System.out.println("\nConnecting to " + host + "...");
        smtpService.connect(host, fromEmail, password);

        System.out.println("✓ Connected successfully!\n");

        // Compose email
        System.out.print("To: ");
        String to = scanner.nextLine().trim();

        System.out.print("Subject: ");
        String subject = scanner.nextLine().trim();

        System.out.println("Body (type 'END' on new line to finish):");
        StringBuilder body = new StringBuilder();
        while (true) {
            String line = scanner.nextLine();
            if (line.equals("END")) break;
            body.append(line).append("\n");
        }

        // Send
        System.out.println("\nSending email...");
        smtpService.sendEmail(to, subject, body.toString());

        // Disconnect
        smtpService.disconnect();
        System.out.println("✓ SMTP test completed!");
    }

    /**
     * Full demo - đọc và gửi
     */
    private static void fullDemo() throws ImapException, SmtpException {
        System.out.println("\n=== FULL DEMO ===");

        // Input credentials (same for IMAP and SMTP)
        System.out.print("Email Provider (gmail/yahoo/outlook): ");
        String provider = scanner.nextLine().trim().toLowerCase();

        String imapHost, smtpHost;
        switch (provider) {
            case "gmail":
                imapHost = "imap.gmail.com";
                smtpHost = "smtp.gmail.com";
                break;
            case "yahoo":
                imapHost = "imap.mail.yahoo.com";
                smtpHost = "smtp.mail.yahoo.com";
                break;
            case "outlook":
                imapHost = "outlook.office365.com";
                smtpHost = "smtp.office365.com";
                break;
            default:
                System.out.print("IMAP Host: ");
                imapHost = scanner.nextLine().trim();
                System.out.print("SMTP Host: ");
                smtpHost = scanner.nextLine().trim();
        }

        System.out.print("Email: ");
        String email = scanner.nextLine().trim();

        System.out.print("Password: ");
        String password = scanner.nextLine().trim();

        // Test IMAP
        System.out.println("\n--- Testing IMAP ---");
        ImapService imapService = new ImapService();
        imapService.connect(imapHost, email, password);

        List<Email> emails = imapService.fetchRecentEmails("INBOX", 3);
        System.out.println("✓ Fetched " + emails.size() + " emails");

        if (!emails.isEmpty()) {
            System.out.println("\nLatest email:");
            Email latest = emails.get(emails.size() - 1);
            System.out.println("  From: " + latest.getFrom());
            System.out.println("  Subject: " + latest.getSubject());
        }

        imapService.disconnect();

        // Test SMTP
        System.out.println("\n--- Testing SMTP ---");
        SmtpService smtpService = new SmtpService();
        smtpService.connect(smtpHost, email, password);

        System.out.print("Send test email to yourself? (y/n): ");
        if (scanner.nextLine().trim().equalsIgnoreCase("y")) {
            smtpService.sendEmail(
                    email,
                    "Test Email from Mail Client",
                    "This is a test email sent at " + new java.util.Date()
            );
            System.out.println("✓ Test email sent!");
        }

        smtpService.disconnect();

        System.out.println("\n✓ Full demo completed!");
    }

    /**
     * Quick Gmail test với hardcoded values (cho development)
     */
    private static void quickGmailTest() {
        System.out.println("\n=== QUICK GMAIL TEST ===");
        System.out.println("Note: You need App Password for Gmail");
        System.out.println("Generate at: https://myaccount.google.com/apppasswords\n");

        System.out.print("Gmail address: ");
        String email = scanner.nextLine().trim();

        System.out.print("App Password (16 chars): ");
        String password = scanner.nextLine().trim();

        try {
            // Test IMAP
            System.out.println("\n1. Testing IMAP connection...");
            ImapService imapService = new ImapService();
            imapService.connect("imap.gmail.com", email, password);

            List<Email> emails = imapService.fetchRecentEmails("INBOX", 5);
            System.out.println("   ✓ IMAP OK - Found " + emails.size() + " recent emails");

            if (!emails.isEmpty()) {
                System.out.println("\n   Recent emails:");
                for (int i = 0; i < Math.min(3, emails.size()); i++) {
                    Email e = emails.get(emails.size() - 1 - i);
                    System.out.println("   - " + e.getSubject() + " (from: " + e.getFrom() + ")");
                }
            }

            imapService.disconnect();

            // Test SMTP
            System.out.println("\n2. Testing SMTP connection...");
            SmtpService smtpService = new SmtpService();
            smtpService.connect("smtp.gmail.com", email, password);
            System.out.println("   ✓ SMTP OK");

            System.out.print("\n   Send test email to yourself? (y/n): ");
            if (scanner.nextLine().trim().equalsIgnoreCase("y")) {
                smtpService.sendEmail(
                        email,
                        "✅ Mail Client Test Successful",
                        "Hello!\n\nYour mail client is working correctly.\n\n" +
                                "IMAP: ✓ Connected\n" +
                                "SMTP: ✓ Connected\n\n" +
                                "Sent at: " + new java.util.Date() + "\n\n" +
                                "You can now use this client to manage your emails!"
                );
                System.out.println("   ✓ Email sent! Check your inbox.");
            }

            smtpService.disconnect();

            System.out.println("\n✅ Quick test completed successfully!");

        } catch (Exception e) {
            System.err.println("\n❌ Test failed: " + e.getMessage());
            System.err.println("\nTroubleshooting:");
            System.err.println("- Make sure you're using App Password, not regular password");
            System.err.println("- Enable 2-Step Verification first");
            System.err.println("- Check if 'Less secure app access' is enabled (if using old account)");
            e.printStackTrace();
        }
    }

    /**
     * Demo với sample data (không cần credentials)
     */
    public static void offlineDemo() {
        System.out.println("\n=== OFFLINE DEMO ===");
        System.out.println("This demo shows the data structures without connecting to server.\n");

        // Create sample emails
        Email email1 = new Email("john@example.com", "you@example.com",
                "Hello from John", "Hi there! How are you?");
        email1.addFlag("Seen");

        Email email2 = new Email("alice@example.com", "you@example.com",
                "Meeting Tomorrow", "Don't forget our meeting at 10am!");
        email2.addFlag("Flagged");

        System.out.println("Sample Email 1:");
        System.out.println(email1);
        System.out.println("\nSample Email 2:");
        System.out.println(email2);

        // Create sample folder
        Folder inbox = new Folder("INBOX");
        inbox.setMessageCount(42);
        inbox.setUnreadCount(5);

        System.out.println("\nSample Folder:");
        System.out.println(inbox);

        System.out.println("\n✓ Offline demo completed!");
    }
}