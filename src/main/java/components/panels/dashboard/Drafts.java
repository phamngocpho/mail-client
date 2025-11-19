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
import javax.swing.event.TableModelEvent;
import javax.swing.table.*;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Drafts extends JPanel {

    private final DraftsController controller;
    private JTable emailTable;
    private DefaultTableModel tableModel;
    private Compose composePanel;
    private MainMenu.MenuItemClickListener menuItemClickListener;

    private List<Email> emails;

    private FlatSVGIcon starOutlineIcon;

    private JCheckBox headerCheckbox;   // ⬅ Ô tổng Select All

    public Drafts() {
        this.controller = DraftsController.getInstance();
        this.emails = new ArrayList<>();

        loadIcons();
        init();
        loadDrafts();
    }

    private void loadIcons() {
        int iconSize = Constants.defaultIconSize - 5;
        starOutlineIcon = new FlatSVGIcon("icons/inbox/star_outline.svg", iconSize, iconSize);
    }

    private void init() {
        setLayout(new BorderLayout());
        add(createHeaderPanel(), BorderLayout.NORTH);
        add(createEmailListPanel(), BorderLayout.CENTER);
    }

    private JPanel createHeaderPanel() {
        JPanel panel = new JPanel(new MigLayout("fillx, insets 10", "[grow][]", "[]"));
        panel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Constants.dark_gray));

        JLabel titleLabel = new JLabel("Drafts");

        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.putClientProperty(FlatClientProperties.BUTTON_TYPE, "roundRect");
        refreshBtn.addActionListener(e -> loadDrafts());

        JButton deleteBtn = new JButton("Delete Selected");
        deleteBtn.putClientProperty(FlatClientProperties.BUTTON_TYPE, "roundRect");
        deleteBtn.addActionListener(e -> deleteSelectedDrafts());

        panel.add(titleLabel, "growx");
        panel.add(refreshBtn);
        panel.add(deleteBtn, "gapleft 10");

        return panel;
    }

    private JScrollPane createEmailListPanel() {
        String[] columns = {"", "", "To", "Subject", "Time"};

        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return col == 0;  // chỉ checkbox được tick
            }

            @Override
            public Class<?> getColumnClass(int col) {
                return switch (col) {
                    case 0 -> Boolean.class;
                    case 1 -> ImageIcon.class;
                    default -> String.class;
                };
            }
        };

        emailTable = new JTable(tableModel);
        emailTable.setRowHeight(48);
        emailTable.setShowGrid(false);
        emailTable.setIntercellSpacing(new Dimension(0, 0));
        emailTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // ============================================
        //  HEADER CHECKBOX (SELECT ALL)
        // ============================================
        headerCheckbox = new JCheckBox();
        headerCheckbox.setHorizontalAlignment(SwingConstants.CENTER);
        headerCheckbox.setOpaque(false);

        // Renderer dùng để hiển thị checkbox
        TableColumn tc = emailTable.getColumnModel().getColumn(0);
        tc.setMaxWidth(40);
        tc.setHeaderRenderer((table, value, isSelected, hasFocus, row, col) -> headerCheckbox);

        // Star column
        TableColumn starCol = emailTable.getColumnModel().getColumn(1);
        starCol.setMaxWidth(40);
        starCol.setHeaderRenderer((table, value, isSelected, hasFocus, row, col) -> {
            JLabel lbl = new JLabel(starOutlineIcon);
            lbl.setHorizontalAlignment(SwingConstants.CENTER);
            return lbl;
        });

        // ============================================
        // CLICK HEADER = SELECT ALL
        // ============================================
        JTableHeader tableHeader = emailTable.getTableHeader();
        tableHeader.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                int col = emailTable.columnAtPoint(e.getPoint());
                if (col == 0) {
                    boolean newState = !headerCheckbox.isSelected();
                    headerCheckbox.setSelected(newState);
                    toggleSelectAll(newState);
                    tableHeader.repaint();
                }
            }
        });

        // Column widths
        emailTable.getColumnModel().getColumn(2).setPreferredWidth(200);
        emailTable.getColumnModel().getColumn(3).setPreferredWidth(400);
        emailTable.getColumnModel().getColumn(4).setPreferredWidth(100);

        // Renderers
        emailTable.setDefaultRenderer(String.class, new DraftCellRenderer());
        emailTable.setDefaultRenderer(ImageIcon.class, new StarCellRenderer());

        // Double click => Edit draft
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

        // ============================================
        //  TỰ ĐỒNG BỘ HEADER CHECKBOX
        // ============================================
        tableModel.addTableModelListener(e -> {
            if (e.getType() == TableModelEvent.UPDATE) {
                boolean allChecked = true;
                for (int i = 0; i < tableModel.getRowCount(); i++) {
                    if (!(boolean) tableModel.getValueAt(i, 0)) {
                        allChecked = false;
                        break;
                    }
                }
                headerCheckbox.setSelected(allChecked);
                emailTable.getTableHeader().repaint();
            }
        });

        JScrollPane scroll = new JScrollPane(emailTable);
        scroll.setBorder(null);
        return scroll;
    }

    private void loadDrafts() {
        emails = controller.getAllDrafts();
        refreshTable();
    }

    private void refreshTable() {
        // sort newest → oldest
        emails.sort(Comparator.comparing(Email::getDate,
                Comparator.nullsLast(Comparator.naturalOrder())).reversed());

        tableModel.setRowCount(0);
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd");

        for (Email email : emails) {

            String to = email.getTo().isEmpty()
                    ? "(No recipient)"
                    : String.join(", ", email.getTo());

            String subject = (email.getSubject() == null || email.getSubject().isEmpty())
                    ? "(No subject)"
                    : email.getSubject();

            String time = email.getDate() != null
                    ? sdf.format(email.getDate())
                    : "";

            tableModel.addRow(new Object[]{
                    false,
                    starOutlineIcon,
                    to,
                    subject,
                    time
            });
        }

        headerCheckbox.setSelected(false);
        emailTable.getTableHeader().repaint();
    }

    // ============================================
    // SELECT ALL
    // ============================================
    private void toggleSelectAll(boolean selectAll) {
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            tableModel.setValueAt(selectAll, i, 0);
        }
        emailTable.repaint();
    }

    // ============================================
    // DELETE SELECTED DRAFTS
    // ============================================
    private void deleteSelectedDrafts() {
        List<Email> toDelete = new ArrayList<>();

        for (int i = 0; i < tableModel.getRowCount(); i++) {
            boolean selected = (boolean) tableModel.getValueAt(i, 0);
            if (selected) {
                toDelete.add(emails.get(i));
            }
        }

        if (toDelete.isEmpty()) {
            Notifications.getInstance().show(
                    Notifications.Type.WARNING,
                    "No drafts selected"
            );
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Delete " + toDelete.size() + " draft(s)?",
                "Confirm",
                JOptionPane.YES_NO_OPTION
        );

        if (confirm != JOptionPane.YES_OPTION) return;

        for (Email e : toDelete) {
            controller.deleteDraft(e);
        }

        Notifications.getInstance().show(
                Notifications.Type.SUCCESS,
                "Deleted " + toDelete.size() + " draft(s)"
        );

        loadDrafts();
    }

    private void editDraft(Email draft) {
        if (composePanel != null) {
            composePanel.loadDraft(draft);

            if (menuItemClickListener != null) {
                menuItemClickListener.onMenuItemClicked(composePanel);
            }

            Notifications.getInstance().show(
                    Notifications.Type.INFO,
                    "Editing draft"
            );
        }
    }

    // Renderers
    private static class DraftCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int col) {
            Component c = super.getTableCellRendererComponent(table, value,
                    isSelected, hasFocus, row, col);
            if (col == 1) setHorizontalAlignment(CENTER);
            else setHorizontalAlignment(LEFT);
            return c;
        }
    }

    private static class StarCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value,
                boolean isSelected, boolean hasFocus,
                int row, int col
        ) {
            JLabel lbl = new JLabel();
            lbl.setHorizontalAlignment(CENTER);
            if (value instanceof ImageIcon) lbl.setIcon((ImageIcon) value);

            lbl.setOpaque(true);
            lbl.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
            return lbl;
        }
    }

    public void setComposePanel(Compose panel) {
        this.composePanel = panel;
    }

    public void refresh() {
        loadDrafts();
    }

    public void setMenuItemClickListener(MainMenu.MenuItemClickListener listener) {
        this.menuItemClickListener = listener;
    }
}
