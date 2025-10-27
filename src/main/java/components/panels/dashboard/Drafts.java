package components.panels.dashboard;

import com.formdev.flatlaf.FlatClientProperties;
import components.menus.MainMenu;
import controllers.DraftsController;
import models.Email;
import net.miginfocom.swing.MigLayout;
import raven.toast.Notifications;
import utils.Constants;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Panel hiển thị danh sách thư nháp
 */
public class Drafts extends JPanel {
    private final DraftsController controller;
    private JPanel emailListPanel;
    private JScrollPane scrollPane;
    private Compose composePanel;
    private Email currentDraft;
    private MainMenu.MenuItemClickListener menuItemClickListener;

    public Drafts() {
        this.controller = DraftsController.getInstance();
        init();
        loadDrafts();
    }

    private void init() {
        setLayout(new BorderLayout());

        // Header panel
        JPanel headerPanel = createHeaderPanel();
        add(headerPanel, BorderLayout.NORTH);

        // Email list panel
        emailListPanel = new JPanel();
        emailListPanel.setLayout(new BoxLayout(emailListPanel, BoxLayout.Y_AXIS));
        emailListPanel.setOpaque(false);

        scrollPane = new JScrollPane(emailListPanel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        add(scrollPane, BorderLayout.CENTER);
    }

    private JPanel createHeaderPanel() {
        JPanel panel = new JPanel(new MigLayout("fillx, insets 10", "[grow][]", "[]"));
        panel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Constants.dark_gray));

        JLabel titleLabel = new JLabel("Drafts");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));

        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.putClientProperty(FlatClientProperties.BUTTON_TYPE, "roundRect");
        refreshBtn.addActionListener(e -> loadDrafts());

        panel.add(titleLabel, "growx");
        panel.add(refreshBtn);

        return panel;
    }

    private void loadDrafts() {
        emailListPanel.removeAll();
        List<Email> drafts = controller.getAllDrafts();

        if (drafts.isEmpty()) {
            JLabel emptyLabel = new JLabel("No drafts");
            emptyLabel.setHorizontalAlignment(SwingConstants.CENTER);
            emptyLabel.setForeground(Color.GRAY);
            emailListPanel.add(emptyLabel);
        } else {
            for (Email draft : drafts) {
                emailListPanel.add(createDraftItem(draft));
            }
        }

        revalidate();
        repaint();
    }

    private JPanel createDraftItem(Email draft) {
        JPanel panel = new JPanel(new MigLayout("fillx, insets 10", "[grow][]", "[]5[]"));
        panel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Constants.dark_gray));
        panel.setCursor(new Cursor(Cursor.HAND_CURSOR));

        // Subject
        JLabel subjectLabel = new JLabel(draft.getSubject().isEmpty() ? "(No subject)" : draft.getSubject());
        subjectLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));

        // To
        String toText = draft.getTo().isEmpty() ? "(No recipient)" : String.join(", ", draft.getTo());
        JLabel toLabel = new JLabel("To: " + toText);
        toLabel.setForeground(Color.GRAY);

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        buttonPanel.setOpaque(false);

        JButton editBtn = new JButton("Edit");
        editBtn.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_BORDERLESS);
        editBtn.addActionListener(e -> editDraft(draft));

        JButton deleteBtn = new JButton("Delete");
        deleteBtn.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_BORDERLESS);
        deleteBtn.addActionListener(e -> deleteDraft(draft));

        buttonPanel.add(editBtn);
        buttonPanel.add(deleteBtn);

        panel.add(subjectLabel, "growx, wrap");
        panel.add(toLabel, "growx");
        panel.add(buttonPanel, "east");

        // Click to edit
        panel.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                editDraft(draft);
            }
        });

        return panel;
    }

    private void editDraft(Email draft) {
        if (composePanel != null) {
            currentDraft = draft;

            // ════════════════════════════════════════════════════
            // CHỈ GỌI loadDraft() - nó sẽ load tất cả (to, cc, subject, body, attachments)
            // ════════════════════════════════════════════════════
            composePanel.loadDraft(draft);

            // ════════════════════════════════════════════════════
            // CHUYỂN SANG COMPOSE PANEL
            // ════════════════════════════════════════════════════
            if (menuItemClickListener != null) {
                menuItemClickListener.onMenuItemClicked(composePanel);
            }

            // ════════════════════════════════════════════════════
            // HIỂN THỊ NOTIFICATION (optional - có thể bỏ)
            // ════════════════════════════════════════════════════
            Notifications.getInstance().show(Notifications.Type.INFO, "Editing draft");
        }
    }

    private void deleteDraft(Email draft) {
        int result = JOptionPane.showConfirmDialog(
                this,
                "Are you sure you want to delete this draft?",
                "Confirm Delete",
                JOptionPane.YES_NO_OPTION
        );

        if (result == JOptionPane.YES_OPTION) {
            controller.deleteDraft(draft);
            loadDrafts();
            Notifications.getInstance().show(Notifications.Type.SUCCESS, "Draft deleted");
        }
    }

    public void setComposePanel(Compose composePanel) {
        this.composePanel = composePanel;
    }

    public void refresh() {
        loadDrafts();
    }
    public void setMenuItemClickListener(MainMenu.MenuItemClickListener listener) {
        this.menuItemClickListener = listener;
    }
}