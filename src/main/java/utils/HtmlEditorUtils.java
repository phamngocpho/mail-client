package utils;

import com.formdev.flatlaf.fonts.roboto.FlatRobotoFont;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import raven.toast.Notifications;

import javax.swing.*;
import javax.swing.text.*;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;

/**
 * Utility class for HTML editor formatting operations.
 * Handles all text formatting, HTML manipulation, and style configuration
 * for JTextPane components used in email composition.
 */
public class HtmlEditorUtils {
    private static final Logger logger = LoggerFactory.getLogger(HtmlEditorUtils.class);

    /**
     * Tạo và cấu hình HTMLEditorKit với StyleSheet tùy chỉnh
     * 
     * @return HTMLEditorKit đã được cấu hình
     */
    public static HTMLEditorKit createConfiguredEditorKit() {
        HTMLEditorKit kit = new HTMLEditorKit();
        javax.swing.text.html.StyleSheet styleSheet = kit.getStyleSheet();

        // Get text color and font size
        Color textColor = UIUtils.getTextColor();
        String colorHex = UIUtils.colorToHex(textColor);
        int fontSize = Constants.systemFont.getSize();

        // Add CSS rules for consistent rendering
        styleSheet.addRule(String.format(
            "body { " +
            "color: %s; " +
            "font-family: '%s', Arial, sans-serif; " +
            "font-size: %dpx; " +
            "line-height: 1.5; " +
            "margin: 0; " +
            "padding: 0; " +
            "}",
            colorHex, FlatRobotoFont.FAMILY, fontSize
        ));

        styleSheet.addRule(String.format(
            "p { " +
            "color: %s; " +
            "font-family: '%s', Arial, sans-serif; " +
            "font-size: %dpx; " +
            "line-height: 1.5; " +
            "margin: 0 0 8px 0; " +
            "padding: 0; " +
            "}",
            colorHex, FlatRobotoFont.FAMILY, fontSize
        ));

        styleSheet.addRule(String.format(
            "div { " +
            "color: %s; " +
            "font-family: '%s', Arial, sans-serif; " +
            "font-size: %dpx; " +
            "line-height: 1.5; " +
            "margin: 0; " +
            "padding: 0; " +
            "}",
            colorHex, FlatRobotoFont.FAMILY, fontSize
        ));

        // Additional rules for lists
        styleSheet.addRule("ul, ol { margin: 8px 0; padding-left: 24px; }");
        styleSheet.addRule("li { margin: 4px 0; }");

        // Link styles
        styleSheet.addRule("a { color: " + UIUtils.colorToHex(Constants.sky_blue) + "; text-decoration: underline; }");

        logger.debug("Created configured HTMLEditorKit with font size: {}", fontSize);
        return kit;
    }

    /**
     * Toggle định dạng in đậm cho text được chọn
     * 
     * @param textPane JTextPane cần áp dụng định dạng
     */
    public static void toggleBold(JTextPane textPane) {
        try {
            StyledEditorKit.BoldAction boldAction = new StyledEditorKit.BoldAction();
            boldAction.actionPerformed(new java.awt.event.ActionEvent(
                textPane, java.awt.event.ActionEvent.ACTION_PERFORMED, ""
            ));
            textPane.requestFocus();
            logger.debug("Toggled bold formatting");
        } catch (Exception e) {
            logger.error("Failed to toggle bold", e);
            showError("Failed to apply bold formatting");
        }
    }

    /**
     * Toggle định dạng in nghiêng cho text được chọn
     * 
     * @param textPane JTextPane cần áp dụng định dạng
     */
    public static void toggleItalic(JTextPane textPane) {
        try {
            StyledEditorKit.ItalicAction italicAction = new StyledEditorKit.ItalicAction();
            italicAction.actionPerformed(new java.awt.event.ActionEvent(
                textPane, java.awt.event.ActionEvent.ACTION_PERFORMED, ""
            ));
            textPane.requestFocus();
            logger.debug("Toggled italic formatting");
        } catch (Exception e) {
            logger.error("Failed to toggle italic", e);
            showError("Failed to apply italic formatting");
        }
    }

    /**
     * Căn trái đoạn văn hiện tại
     * 
     * @param textPane JTextPane cần áp dụng căn lề
     */
    public static void alignLeft(JTextPane textPane) {
        try {
            StyledEditorKit.AlignmentAction alignAction = new StyledEditorKit.AlignmentAction(
                "left-align", StyleConstants.ALIGN_LEFT
            );
            alignAction.actionPerformed(new java.awt.event.ActionEvent(
                textPane, java.awt.event.ActionEvent.ACTION_PERFORMED, ""
            ));
            textPane.requestFocus();
            logger.debug("Applied left alignment");
        } catch (Exception e) {
            logger.error("Failed to align left", e);
            showError("Failed to apply alignment");
        }
    }

    /**
     * Chèn danh sách dấu đầu dòng tại vị trí con trỏ
     * 
     * @param textPane JTextPane cần chèn danh sách
     */
    public static void insertBulletList(JTextPane textPane) {
        try {
            HTMLDocument doc = (HTMLDocument) textPane.getDocument();
            HTMLEditorKit kit = (HTMLEditorKit) textPane.getEditorKit();
            int pos = textPane.getCaretPosition();

            String bulletHTML = "<ul><li>Item 1</li><li>Item 2</li><li>Item 3</li></ul>";
            kit.insertHTML(doc, pos, bulletHTML, 0, 0, HTML.Tag.UL);

            textPane.requestFocus();
            logger.debug("Inserted bullet list at position {}", pos);
        } catch (BadLocationException | IOException e) {
            logger.error("Failed to insert bullet list", e);
            showError("Failed to insert bullet list: " + e.getMessage());
        }
    }

    /**
     * Chèn danh sách đánh số tại vị trí con trỏ
     * 
     * @param textPane JTextPane cần chèn danh sách
     */
    public static void insertNumberedList(JTextPane textPane) {
        try {
            HTMLDocument doc = (HTMLDocument) textPane.getDocument();
            HTMLEditorKit kit = (HTMLEditorKit) textPane.getEditorKit();
            int pos = textPane.getCaretPosition();

            String numberedHTML = "<ol><li>Item 1</li><li>Item 2</li><li>Item 3</li></ol>";
            kit.insertHTML(doc, pos, numberedHTML, 0, 0, HTML.Tag.OL);

            textPane.requestFocus();
            logger.debug("Inserted numbered list at position {}", pos);
        } catch (BadLocationException | IOException e) {
            logger.error("Failed to insert numbered list", e);
            showError("Failed to insert numbered list: " + e.getMessage());
        }
    }

    /**
     * Chèn hình ảnh dưới dạng Base64 vào vị trí con trỏ
     *
     * @param textPane  JTextPane cần chèn hình ảnh
     * @param imageFile File hình ảnh cần chèn
     */
    public static void insertImage(JTextPane textPane, File imageFile) {
        if (imageFile == null || !imageFile.exists()) {
            logger.warn("Image file is null or does not exist");
            showError("Invalid image file");
            return;
        }

        try {
            // Read image file and convert to Base64
            byte[] imageBytes = Files.readAllBytes(imageFile.toPath());
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);

            // Determine MIME type
            String mimeType = getMimeTypeFromFileName(imageFile.getName());

            // Insert image as Base64 data URL
            HTMLDocument doc = (HTMLDocument) textPane.getDocument();
            HTMLEditorKit kit = (HTMLEditorKit) textPane.getEditorKit();
            int pos = textPane.getCaretPosition();

            String imageHTML = String.format(
                "<img src='data:%s;base64,%s' style='max-width: 100%%; height: auto;' alt='%s'/>",
                mimeType, base64Image, imageFile.getName()
            );

            kit.insertHTML(doc, pos, imageHTML, 0, 0, HTML.Tag.IMG);

            textPane.requestFocus();
            logger.debug("Inserted image: {} at position {}", imageFile.getName(), pos);
            Notifications.getInstance().show(Notifications.Type.SUCCESS, "Image inserted successfully!");
        } catch (Exception e) {
            logger.error("Failed to insert image: {}", imageFile.getName(), e);
            showError("Failed to insert image: " + e.getMessage());
        }
    }

    /**
     * Chèn liên kết hyperlink tại vị trí con trỏ hoặc text được chọn
     *
     * @param textPane JTextPane cần chèn liên kết
     * @param url      URL của liên kết
     */
    public static void insertLink(JTextPane textPane, String url) {
        if (url == null || url.trim().isEmpty()) {
            logger.warn("URL is empty");
            return;
        }

        try {
            String selectedText = textPane.getSelectedText();
            String linkText = (selectedText != null && !selectedText.isEmpty()) 
                ? selectedText 
                : url;

            HTMLDocument doc = (HTMLDocument) textPane.getDocument();
            HTMLEditorKit kit = (HTMLEditorKit) textPane.getEditorKit();

            int start = textPane.getSelectionStart();
            int end = textPane.getSelectionEnd();

            // Remove selected text if any
            if (start != end) {
                doc.remove(start, end - start);
            }

            // Insert link
            String linkHTML = String.format("<a href='%s'>%s</a>", url, linkText);
            kit.insertHTML(doc, start, linkHTML, 0, 0, HTML.Tag.A);

            textPane.requestFocus();
            logger.debug("Inserted link: {} with text: {}", url, linkText);
        } catch (BadLocationException | IOException e) {
            logger.error("Failed to insert link: {}", url, e);
            showError("Failed to insert link: " + e.getMessage());
        }
    }

    /**
     * Xóa tất cả định dạng khỏi text được chọn
     *
     * @param textPane JTextPane cần xóa định dạng
     */
    public static void clearFormatting(JTextPane textPane) {
        String selectedText = textPane.getSelectedText();
        
        if (selectedText == null || selectedText.isEmpty()) {
            logger.debug("No text selected for clearing formatting");
            Notifications.getInstance().show(
                Notifications.Type.INFO, 
                "Please select text to clear formatting"
            );
            return;
        }

        try {
            int start = textPane.getSelectionStart();
            int end = textPane.getSelectionEnd();

            HTMLDocument doc = (HTMLDocument) textPane.getDocument();
            doc.remove(start, end - start);
            doc.insertString(start, selectedText, null);
            
            textPane.requestFocus();
            logger.debug("Cleared formatting for selected text");
        } catch (BadLocationException e) {
            logger.error("Failed to clear formatting", e);
            showError("Failed to clear formatting: " + e.getMessage());
        }
    }

    /**
     * Xác định MIME type dựa trên tên file
     * 
     * @param fileName Tên file
     * @return MIME type string
     */
    private static String getMimeTypeFromFileName(String fileName) {
        String lowerFileName = fileName.toLowerCase();
        
        if (lowerFileName.endsWith(".png")) {
            return "image/png";
        } else if (lowerFileName.endsWith(".gif")) {
            return "image/gif";
        } else if (lowerFileName.endsWith(".bmp")) {
            return "image/bmp";
        } else if (lowerFileName.endsWith(".webp")) {
            return "image/webp";
        } else {
            return "image/jpeg"; // default
        }
    }

    /**
     * Hiển thị thông báo lỗi
     * 
     * @param message Nội dung thông báo
     */
    private static void showError(String message) {
        Notifications.getInstance().show(Notifications.Type.ERROR, message);
    }
}

