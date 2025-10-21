package components.panels.welcome;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import components.forms.FormsManager;
import components.panels.MainPanel;
import net.miginfocom.swing.MigLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Welcome screen with onboarding slides to introduce the application.
 * Features multiple slides with descriptions, navigation buttons, and progress indicators.
 */
public class Welcome extends JPanel {
    private int currentSlide = 0;
    private final List<SlideContent> slides = new ArrayList<>();
    
    private JPanel contentPanel;
    private JPanel dotsPanel;
    private JButton previousButton;
    private JButton nextButton;
    private JButton skipButton;
    private JButton getStartedButton;
    private static final Logger logger = LoggerFactory.getLogger(Welcome.class);
    
    public Welcome() {
        initSlides();
        init();
    }

    /**
     * Initialize slide contents with title, description, and icon
     */
    private void initSlides() {
        slides.add(new SlideContent(
            "Chào mừng đến với Mail Client",
            "Quản lý email đơn giản và hiệu quả",
            "Ứng dụng email client nhẹ, nhanh và bảo mật được xây dựng với Java.",
            new String[]{
                "Giao diện thân thiện, dễ sử dụng",
                "Kết nối an toàn với mã hóa SSL/TLS",
                "Xử lý email nhanh chóng, mượt mà",
                "Hoàn toàn miễn phí và mã nguồn mở"
            },
            "icons/welcome/mail_app.svg"
        ));
        
        slides.add(new SlideContent(
            "Hỗ trợ Mọi Tài khoản Email",
            "Kết nối với Gmail, Outlook, Yahoo và nhiều hơn nữa",
            "Quản lý nhiều tài khoản email từ các nhà cung cấp khác nhau tại một nơi.",
            new String[]{
                "Gmail & Google Workspace",
                "Outlook & Microsoft 365",
                "Yahoo Mail",
                "Và các email server khác hỗ trợ IMAP/SMTP"
            },
            "icons/welcome/cloud_sync.svg"
        ));
        
        slides.add(new SlideContent(
            "Quản lý File Đính kèm",
            "Gửi và nhận file dễ dàng",
            "Đính kèm và tải xuống file với vài thao tác đơn giản.",
            new String[]{
                "Đính kèm nhiều file cùng lúc khi soạn email",
                "Xem trước file: PDF, Word, Excel, hình ảnh",
                "Tải xuống toàn bộ hoặc từng file riêng lẻ",
                "Quản lý attachments rõ ràng, trực quan"
            },
            "icons/welcome/attachment.svg"
        ));
        
        slides.add(new SlideContent(
            "Giao diện Hiện đại",
            "Thiết kế đẹp mắt, dễ nhìn",
            "Giao diện tối giản với theme tối/sáng linh hoạt, phù hợp mọi sở thích.",
            new String[]{
                "Chuyển đổi Dark/Light theme dễ dàng",
                "Icon Material Design đẹp mắt",
                "Bố cục linh hoạt, responsive",
                "Thông báo toast gọn gàng, không làm phiền"
            },
            "icons/welcome/palette.svg"
        ));
        
        slides.add(new SlideContent(
            "Bảo mật & Hiệu suất",
            "An toàn và nhanh chóng",
            "Bảo vệ thông tin cá nhân với mã hóa mạnh mẽ và xử lý tối ưu.",
            new String[]{
                "Mã hóa SSL/TLS cho mọi kết nối",
                "Không lưu mật khẩu dạng văn bản thuần",
                "Tải email hàng loạt, tiết kiệm băng thông",
                "Logging chi tiết giúp theo dõi và khắc phục"
            },
            "icons/welcome/security.svg"
        ));
        
        slides.add(new SlideContent(
            "Sẵn sàng Bắt đầu!",
            "Chỉ mất vài giây để thiết lập",
            "Kết nối tài khoản email và bắt đầu sử dụng ngay lập tức.",
            new String[]{
                "Nhập email và mật khẩu ứng dụng",
                "Chọn nhà cung cấp hoặc tự cấu hình",
                "Đồng bộ email tự động",
                "Gửi và nhận email ngay!"
            },
            "icons/welcome/rocket_1.svg"
        ));
    }

    private void init() {
        setLayout(new MigLayout("fill, insets 0", "[fill]", "[30!]0[grow]0[shrink]"));
        
        // Skip button at top right
        skipButton = createSkipButton();
        JPanel topPanel = new JPanel(new MigLayout("fill, insets 30 30 0 30", "[grow, right]", "[]"));
        topPanel.setOpaque(false);
        topPanel.add(skipButton, "right");
        add(topPanel, "wrap, growx");
        
        // Content area
        contentPanel = new JPanel(new MigLayout(
            "fill, insets 50 60 30 60",
            "[center]",
            "[]15[]10[]25[]push"
        ));
        contentPanel.setOpaque(false);
        add(contentPanel, "wrap, grow");
        
        // Bottom navigation panel
        JPanel bottomPanel = createBottomPanel();
        add(bottomPanel, "alignx center, gap bottom 50");
        
        // Display first slide
        updateSlide();
    }

    private JButton createSkipButton() {
        JButton btn = new JButton("Bỏ qua");
        
        try {
            FlatSVGIcon icon = new FlatSVGIcon("icons/welcome/close.svg");
            icon = icon.derive(14, 14);
            btn.setIcon(icon);
        } catch (Exception e) {
            logger.error("Error loading icon: icons/welcome/close.svg", e);
        }
        
        btn.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_BORDERLESS);
        btn.putClientProperty(FlatClientProperties.STYLE, 
            "foreground: #9CA3AF; font: normal 14");
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setFocusPainted(false);
        btn.setFocusable(false);
        btn.addActionListener(e -> goToMainPanel());
        return btn;
    }

    private JPanel createBottomPanel() {
        JPanel panel = new JPanel(new MigLayout("insets 10, fillx", "[center]", "[]10[]"));
        panel.setOpaque(false);

        // Dots indicator
        dotsPanel = new JPanel(new MigLayout("insets 0", "[]10[]10[]10[]10[]10[]", "[]"));
        dotsPanel.setOpaque(false);
        updateDots();
        panel.add(dotsPanel, "wrap, alignx center");

        // Navigation buttons container
        JPanel buttonsPanel = new JPanel(new MigLayout("insets 0", "[]10[]10[]", "[]"));
        buttonsPanel.setOpaque(false);

        previousButton = createNavigationButton("Quay lại", "icons/welcome/arrow_back.svg", true);
        previousButton.addActionListener(e -> previousSlide());

        nextButton = createNavigationButton("Tiếp theo", "icons/welcome/arrow_forward.svg", false);
        nextButton.addActionListener(e -> nextSlide());

        getStartedButton = createGetStartedButton();
        getStartedButton.addActionListener(e -> goToMainPanel());
        getStartedButton.setVisible(false);

        buttonsPanel.add(previousButton, "gap left 3, growx");
        buttonsPanel.add(nextButton, "growx");
        buttonsPanel.add(getStartedButton, "w 100!, h 30!");

        panel.add(buttonsPanel, "alignx center, gap top 5");

        return panel;
    }

    private JButton createNavigationButton(String text, String iconPath, boolean iconLeading) {
        JButton btn = new JButton(text);

        if (iconLeading) {
            btn.setHorizontalTextPosition(SwingConstants.RIGHT);
            btn.setIconTextGap(8);
        } else {
            btn.setHorizontalTextPosition(SwingConstants.LEFT);
            btn.setIconTextGap(8);
        }

        try {
            FlatSVGIcon icon = new FlatSVGIcon(iconPath);
            btn.setIcon(icon);
        } catch (Exception e) {
            // Icon không load được, dùng text only
        }

        btn.setFocusPainted(false);
        btn.putClientProperty(FlatClientProperties.BUTTON_TYPE, "roundRect");
        btn.putClientProperty(FlatClientProperties.STYLE,
            "arc: 50; " +
            "focusWidth: 0; " +
            "borderWidth: 0; " +
            "iconTextGap: 8; ");

        return btn;
    }

    private JButton createGetStartedButton() {
        JButton btn = new JButton("Bắt đầu");
        btn.setFocusPainted(false);
        btn.putClientProperty(FlatClientProperties.BUTTON_TYPE, "roundRect");
        btn.putClientProperty(FlatClientProperties.STYLE, 
            "arc: 50; background: #3B82F6;");
        return btn;
    }

    private void updateSlide() {
        contentPanel.removeAll();
        SlideContent slide = slides.get(currentSlide);
        
        // Icon with colored background - căn giữa chính xác
        JPanel iconPanel = createIconLabel(slide);
        contentPanel.add(iconPanel, "wrap, alignx center");
        
        // Title
        JLabel titleLabel = new JLabel(slide.title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 34f));
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        contentPanel.add(titleLabel, "wrap, alignx center");
        
        // Subtitle
        JLabel subtitleLabel = new JLabel(slide.subtitle);
        subtitleLabel.setFont(subtitleLabel.getFont().deriveFont(Font.PLAIN, 16f));
        subtitleLabel.setForeground(new Color(156, 163, 175));
        subtitleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        contentPanel.add(subtitleLabel, "wrap, alignx center");
        
        // Description
        JLabel descLabel = new JLabel(slide.description);
        descLabel.setFont(descLabel.getFont().deriveFont(Font.PLAIN, 14f));
        descLabel.setForeground(new Color(156, 163, 175));
        descLabel.setHorizontalAlignment(SwingConstants.CENTER);
        contentPanel.add(descLabel, "wrap, alignx center, width 650!");
        
        // Features list
        JPanel featuresPanel = getFeaturesPanel(slide);

        contentPanel.add(featuresPanel, "wrap, alignx center");
        
        // Update navigation buttons visibility
        previousButton.setVisible(currentSlide > 0);
        nextButton.setVisible(currentSlide < slides.size() - 1);
        getStartedButton.setVisible(currentSlide == slides.size() - 1);
        skipButton.setVisible(currentSlide < slides.size() - 1);
        
        updateDots();
        
        contentPanel.revalidate();
        contentPanel.repaint();
    }

    private static JPanel getFeaturesPanel(SlideContent slide) {
        JPanel featuresPanel = new JPanel(new MigLayout("insets 0", "[]", "[]8[]8[]8[]"));
        featuresPanel.setOpaque(false);

        for (String feature : slide.features) {
            JPanel featureRow = new JPanel(new MigLayout("insets 0", "[]8[]", "[]"));
            featureRow.setOpaque(false);

            // Add check icon
            JLabel checkIconLabel = getCheckIconLabel();
            featureRow.add(checkIconLabel);

            // Add feature text
            JLabel featureLabel = new JLabel(feature);
            featureLabel.setFont(featureLabel.getFont().deriveFont(Font.PLAIN, 14f));
            featureLabel.setForeground(new Color(200, 200, 200));
            featureRow.add(featureLabel);

            featuresPanel.add(featureRow, "wrap, left");
        }
        return featuresPanel;
    }

    private static JLabel getCheckIconLabel() {
        JLabel checkIconLabel = new JLabel();

        try {
            FlatSVGIcon checkIcon = new FlatSVGIcon("icons/welcome/check.svg");
            checkIcon = checkIcon.derive(16, 16);
            checkIconLabel.setIcon(checkIcon);
        } catch (Exception e) {
            // Fallback to text if icon not found
            checkIconLabel.setText("✓");
            checkIconLabel.setFont(checkIconLabel.getFont().deriveFont(Font.BOLD, 14f));
            checkIconLabel.setForeground(new Color(34, 197, 94));
        }
        return checkIconLabel;
    }

    private JPanel createIconLabel(SlideContent slide) {
        JPanel container = new JPanel(new MigLayout("fill, insets 35"));
        container.setOpaque(true);
        container.putClientProperty(FlatClientProperties.STYLE, "arc: 30");
        
        JLabel label = new JLabel();
        label.setOpaque(false);
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setVerticalAlignment(SwingConstants.CENTER);
        
        try {
            FlatSVGIcon icon = new FlatSVGIcon(slide.iconPath);
            icon = icon.derive(100, 100); // Scale giữ tỷ lệ khung hình gốc
            label.setIcon(icon);
        } catch (Exception e) {
            logger.error("Error loading icon: {}", slide.iconPath, e);
            // Fallback to colored circle
            label.setText("●");
            label.setFont(label.getFont().deriveFont(100f));
            label.setForeground(Color.WHITE);
        }
        
        container.add(label);
        return container;
    }

    private void updateDots() {
        dotsPanel.removeAll();
        
        for (int i = 0; i < slides.size(); i++) {
            JLabel dot = new JLabel();
            
            // Set màu foreground trước khi load icon
            if (i == currentSlide) {
                dot.setForeground(new Color(59, 130, 246)); // Active: xanh dương
            } else {
                dot.setForeground(new Color(75, 85, 99)); // Inactive: xám
            }
            
            try {
                FlatSVGIcon icon;
                if (i == currentSlide) {
                    // Actively dot - filled
                    icon = new FlatSVGIcon("icons/welcome/fill_dot.svg");
                } else {
                    // Inactive dot - outline
                    icon = new FlatSVGIcon("icons/welcome/dot.svg");
                }
                icon = icon.derive(12, 12);
                dot.setIcon(icon);
            } catch (Exception e) {
                // Fallback to text dots if icons don't load
                dot.setText("●");
                dot.setFont(dot.getFont().deriveFont(16f));
            }
            
            // Add dot với constraint, không wrap ở dot cuối
            if (i < slides.size() - 1) {
                dotsPanel.add(dot);
            } else {
                dotsPanel.add(dot, "");
            }
        }
        
        dotsPanel.revalidate();
        dotsPanel.repaint();
    }

    private void previousSlide() {
        if (currentSlide > 0) {
            currentSlide--;
            updateSlide();
        }
    }

    private void nextSlide() {
        if (currentSlide < slides.size() - 1) {
            currentSlide++;
            updateSlide();
        }
    }

    private void goToMainPanel() {
        FormsManager.getInstance().showForm(new MainPanel());
    }

    /**
     * Data class to hold slide content information
     */
    private static class SlideContent {
        String title;
        String subtitle;
        String description;
        String[] features;
        String iconPath;
        
        SlideContent(String title, String subtitle, String description, String[] features,
                    String iconPath) {
            this.title = title;
            this.subtitle = subtitle;
            this.description = description;
            this.features = features;
            this.iconPath = iconPath;
        }
    }
}