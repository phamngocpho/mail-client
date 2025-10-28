package components.panels.dashboard;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import components.forms.FormsManager;
import components.panels.welcome.Welcome;
import controllers.SmtpController;
import net.miginfocom.swing.MigLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import raven.toast.Notifications;
import utils.ConfigUtils;
import utils.EmailCacheManager;
import utils.PreferencesUtils;

import javax.swing.*;
import java.awt.*;

/**
 * Settings panel for application configuration
 * Provides options to clear cache and logout
 */
public class Settings extends JPanel {
    private static final Logger logger = LoggerFactory.getLogger(Settings.class);
    private final EmailCacheManager cacheManager;
    
    public Settings() {
        this.cacheManager = new EmailCacheManager();
        init();
    }
    
    private void init() {
        setLayout(new MigLayout("fill, insets 30", "[grow]", "[][grow]"));
        
        // Header
        JPanel header = createHeader();
        add(header, "wrap, growx");
        
        // Content area with settings options
        JScrollPane scrollPane = new JScrollPane();
        scrollPane.setBorder(null);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        
        JPanel content = createContent();
        scrollPane.setViewportView(content);
        
        add(scrollPane, "grow");
    }
    
    private JPanel createHeader() {
        JPanel panel = new JPanel(new MigLayout("insets 0", "[grow]", "[]5[]"));
        
        JLabel titleLabel = new JLabel("Settings");
        titleLabel.putClientProperty(FlatClientProperties.STYLE, 
            "font: bold +8");
        
        JLabel subtitleLabel = new JLabel("Manage your application preferences");
        subtitleLabel.putClientProperty(FlatClientProperties.STYLE, 
            "foreground: $Label.disabledForeground");
        
        panel.add(titleLabel, "wrap");
        panel.add(subtitleLabel);
        
        return panel;
    }
    
    private JPanel createContent() {
        JPanel panel = new JPanel(new MigLayout("fillx, insets 20 0 20 0", "[grow]", "[]20[]20[]20[]"));
        
        // Cache section
        JPanel cacheSection = createSection(
            "Clear Cache",
            "Clear cached email bodies and HTML (keeps downloaded attachments)",
            "icons/menu/drafts.svg",
            "Clear Cache",
            this::clearCache
        );
        panel.add(cacheSection, "wrap, growx");
        
        // Separator
        JSeparator separator1 = new JSeparator();
        panel.add(separator1, "wrap, growx, gapy 10 10");
        
        // Data section
        JPanel dataSection = createSection(
            "Clear All Data",
            "Delete all cached data including attachments and the .mailclient folder",
            "icons/dialog/warning.svg",
            "Clear Data",
            this::clearAllData
        );
        panel.add(dataSection, "wrap, growx");
        
        // Separator
        JSeparator separator2 = new JSeparator();
        panel.add(separator2, "wrap, growx, gapy 10 10");
        
        // Logout section
        JPanel logoutSection = createSection(
            "Logout",
            "Sign out from your current account",
            "icons/welcome/logout.svg",
            "Logout",
            this::logout
        );
        panel.add(logoutSection, "wrap, growx");
        
        return panel;
    }
    
    private JPanel createSection(String title, String description, String iconPath, 
                                 String buttonText, Runnable action) {
        JPanel panel = new JPanel(new MigLayout("fillx, insets 15", "[]15[grow]15[]", "[]5[]"));
        
        // Icon
        try {
            FlatSVGIcon icon = new FlatSVGIcon(iconPath);
            icon = icon.derive(32, 32);
            JLabel iconLabel = new JLabel(icon);
            panel.add(iconLabel, "spany 2, aligny top");
        } catch (Exception e) {
            logger.warn("Could not load icon: {}", iconPath);
            panel.add(new JLabel(), "spany 2, w 32!, h 32!");
        }
        
        // Title
        JLabel titleLabel = new JLabel(title);
        titleLabel.putClientProperty(FlatClientProperties.STYLE, "font: bold +2");
        panel.add(titleLabel, "wrap");
        
        // Description
        JLabel descLabel = new JLabel(description);
        descLabel.putClientProperty(FlatClientProperties.STYLE, 
            "foreground: $Label.disabledForeground");
        panel.add(descLabel);
        
        // Button
        JButton button = new JButton(buttonText);
        button.putClientProperty(FlatClientProperties.BUTTON_TYPE, 
            FlatClientProperties.BUTTON_TYPE_BORDERLESS);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setFocusPainted(false);
        button.addActionListener(e -> action.run());
        
        panel.add(button, "spany 2, aligny center");
        
        // Border and background
        panel.putClientProperty(FlatClientProperties.STYLE, 
            "arc: 10; background: darken($Panel.background,3%)");
        panel.setBorder(BorderFactory.createLineBorder(
            UIManager.getColor("Component.borderColor"), 1, true));
        
        return panel;
    }
    
    private void clearCache() {
        int result = JOptionPane.showConfirmDialog(
            this,
                """
                        Are you sure you want to clear cached email data?
                        This will clear email bodies and HTML, but keep downloaded attachments.
                        This action cannot be undone.""",
            "Clear Cache",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE
        );
        
        if (result == JOptionPane.YES_OPTION) {
            try {
                cacheManager.clearAll();
                Notifications.getInstance().show(
                    Notifications.Type.SUCCESS,
                    "Cache cleared successfully!"
                );
                logger.info("User cleared email cache from Settings");
            } catch (Exception e) {
                logger.error("Error clearing cache: {}", e.getMessage(), e);
                Notifications.getInstance().show(
                    Notifications.Type.ERROR,
                    "Failed to clear cache: " + e.getMessage()
                );
            }
        }
    }
    
    private void clearAllData() {
        int result = JOptionPane.showConfirmDialog(
            this,
                """
                        WARNING: This will permanently delete ALL cached data!
                        
                        This includes:
                        • All cached email bodies and HTML
                        • All downloaded attachments
                        • The entire .mailclient folder
                        
                        This action CANNOT be undone!
                        
                        Are you absolutely sure?""",
            "Clear All Data",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        );
        
        if (result == JOptionPane.YES_OPTION) {
            try {
                cacheManager.deleteAllCacheFiles();
                Notifications.getInstance().show(
                    Notifications.Type.SUCCESS,
                    "All cached data has been permanently deleted!"
                );
                logger.info("User cleared ALL cache files from Settings (including .mailclient directory)");
            } catch (Exception e) {
                logger.error("Error clearing all data: {}", e.getMessage(), e);
                Notifications.getInstance().show(
                    Notifications.Type.ERROR,
                    "Failed to clear data: " + e.getMessage()
                );
            }
        }
    }
    
    private void logout() {
        int result = JOptionPane.showConfirmDialog(
            this,
            "Are you sure you want to logout?\nYou will need to login again to use the application.",
            "Logout",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        );
        
        if (result == JOptionPane.YES_OPTION) {
            try {
                // Disconnect IMAP and reset shared controller
                Inbox.resetSharedController();
                
                // Reset SMTP controller
                SmtpController.resetInstance();
                
                // Clear credentials
                ConfigUtils.clearCredentials();
                
                // Reset preferences
                PreferencesUtils.setHasSeenWelcome(false);
                
                // Show welcome screen
                FormsManager.getInstance().showForm(new Welcome());
                
                Notifications.getInstance().show(
                    Notifications.Type.SUCCESS,
                    "Logged out successfully!"
                );
                
                logger.info("User logged out from Settings - All controllers reset");
            } catch (Exception e) {
                logger.error("Error during logout: {}", e.getMessage(), e);
                Notifications.getInstance().show(
                    Notifications.Type.ERROR,
                    "Failed to logout: " + e.getMessage()
                );
            }
        }
    }
}

