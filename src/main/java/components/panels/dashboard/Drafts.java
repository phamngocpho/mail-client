package components.panels.dashboard;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import components.menus.MainMenu;
import controllers.DraftsController;
import models.Email;
import net.miginfocom.swing.MigLayout;
import raven.toast.Notifications;
import utils.Constants;
import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Panel hiển thị danh sách thư nháp - sử dụng giao diện giống Inbox
 */
public class Drafts extends JPanel {
    private final DraftsController controller;
    private JTable emailTable;
    private DefaultTableModel tableModel;
    private Compose composePanel;
    private MainMenu.MenuItemClickListener menuItemClickListener;
    private List<Email> emails;

    private FlatSVGIcon starOutlineIcon;
    private FlatSVGIcon selectOutlineIcon;

    public Drafts() {
        this.controller = DraftsController.getInstance();
        this.emails = new ArrayList<>();
        loadIcons();
        init();
        loadDrafts();
    }

    private void loadIcons() {
        // Star icons
        int iconSize = Constants.defaultIconSize - 5;
        starOutlineIcon = new FlatSVGIcon("icons/inbox/star_outline.svg", iconSize, iconSize);
        selectOutlineIcon = new FlatSVGIcon("icons/inbox/select.svg", iconSize, iconSize);
    }

    private void init() {
        setLayout(new BorderLayout());

        // Header panel
        JPanel headerPanel = createHeaderPanel();
        add(headerPanel, BorderLayout.NORTH);

        // Email list panel với JTable (giống Inbox)
        add(createEmailListPanel(), BorderLayout.CENTER);
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

    private JScrollPane createEmailListPanel() {
        String[] columns = {"", "", "To", "Subject", "Time"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // Không cho edit trong draft table
            }

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                if (columnIndex == 0) return Boolean.class;
                if (columnIndex == 1) return ImageIcon.class; // Star column
                return String.class;
            }
        };

        emailTable = new JTable(tableModel);
        emailTable.setFocusable(false);
        emailTable.setRowHeight(48);
        emailTable.setShowGrid(false);
        emailTable.setIntercellSpacing(new Dimension(0, 0));
        emailTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Column widths
        emailTable.getColumnModel().getColumn(0).setMaxWidth(40);  // Checkbox
        emailTable.getColumnModel().getColumn(0).setHeaderRenderer((jTable, o, b, b1, i, i1) -> new JLabel(selectOutlineIcon));
        emailTable.getColumnModel().getColumn(1).setMaxWidth(40);  // Star
        emailTable.getColumnModel().getColumn(1).setHeaderRenderer((jTable, o, b, b1, i, i1) -> new JLabel(starOutlineIcon));
        emailTable.getColumnModel().getColumn(2).setPreferredWidth(200); // To
        emailTable.getColumnModel().getColumn(3).setPreferredWidth(400); // Subject
        emailTable.getColumnModel().getColumn(4).setPreferredWidth(100);  // Time

        // Custom header
        javax.swing.table.JTableHeader header = emailTable.getTableHeader();
        header.setPreferredSize(new Dimension(header.getWidth(), 40));

        // Custom cell renderer
        emailTable.setDefaultRenderer(String.class, new DraftCellRenderer());
        emailTable.setDefaultRenderer(ImageIcon.class, new StarCellRenderer());

        // Double-click để edit draft
        emailTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = emailTable.rowAtPoint(e.getPoint());
                    if (row >= 0 && row < emails.size()) {
                        editDraft(emails.get(row));
                    }
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(emailTable);
        scrollPane.setBorder(null);
        return scrollPane;
    }

    private void loadDrafts() {
        emails = controller.getAllDrafts();
        refreshTable();
    }

    private void refreshTable() {
        // Sort emails by date DESC (newest)
        emails.sort(Comparator.comparing(Email::getDate, Comparator.nullsLast(Comparator.naturalOrder())).reversed());

        tableModel.setRowCount(0);
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd");

        for (Email email : emails) {
            String to = email.getTo().isEmpty() ? "(No recipient)" : String.join(", ", email.getTo());
            String subject = email.getSubject() == null || email.getSubject().isEmpty() ? "(No subject)" : email.getSubject();
            String time = email.getDate() != null ? sdf.format(email.getDate()) : "";

            tableModel.addRow(new Object[]{
                    false,              // Checkbox
                    starOutlineIcon,   // Star icon
                    to,
                    subject,
                    time
            });
        }
    }

    private void editDraft(Email draft) {
        if (composePanel != null) {
            composePanel.loadDraft(draft);
            
            if (menuItemClickListener != null) {
                menuItemClickListener.onMenuItemClicked(composePanel);
            }
            
            Notifications.getInstance().show(Notifications.Type.INFO, "Editing draft");
        }
    }

    // Custom cell renderer for draft rows
    private static class DraftCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            
            if (column == 1) {
                setHorizontalAlignment(CENTER);
            } else {
                setHorizontalAlignment(LEFT);
            }
            
            return c;
        }
    }

    // Star cell renderer
    private static class StarCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int column) {
            JLabel label = new JLabel();
            label.setHorizontalAlignment(CENTER);
            
            if (value instanceof ImageIcon) {
                label.setIcon((ImageIcon) value);
            }
            
            label.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
            label.setOpaque(true);

            return label;
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