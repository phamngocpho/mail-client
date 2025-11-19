package components.panels.dashboard;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import controllers.ImapController;
import models.Email;
import net.miginfocom.swing.MigLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import raven.toast.Notifications;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Panel hiển thị và quản lý thư mục Trash (Thùng rác)
 * Hỗ trợ:
 *  - Hiển thị các email đã xóa
 *  - Khôi phục (Restore)
 *  - Xóa vĩnh viễn (Delete Permanently)
 *  - Dọn sạch Trash (Empty Trash)
 */
public class Trash extends JPanel {
    private static final Logger logger = LoggerFactory.getLogger(Trash.class);

    private JTable emailTable;
    private DefaultTableModel tableModel;
    private List<Email> emails = new ArrayList<>();
    private ImapController controller;

    private JButton refreshButton, emptyButton;

    public Trash(ImapController controller) {
        this.controller = controller;
        setLayout(new MigLayout("fill, insets 0", "[grow]", "[][grow]"));
        initToolbar();
        initTable();

        // Chỉ load nếu đã kết nối IMAP
        if (controller != null && controller.getImapService() != null
                && controller.getImapService().isConnected()) {
            SwingUtilities.invokeLater(this::loadTrashEmails);
        }
    }



    private void initToolbar() {
        JPanel toolbar = new JPanel(new MigLayout("fillx, insets 5", "[]push[]5[]", "[]"));

        JLabel title = new JLabel("🗑️ Trash");
        title.putClientProperty(FlatClientProperties.STYLE, "font: bold 18");
        toolbar.add(title, "gap left 10");

        refreshButton = new JButton(new FlatSVGIcon("icons/inbox/refresh.svg", 18, 18));
        refreshButton.setToolTipText("Refresh Trash");
        refreshButton.addActionListener(e -> loadTrashEmails());
        toolbar.add(refreshButton, "gap right 5");

        emptyButton = new JButton("Empty Trash");
        emptyButton.addActionListener(e -> emptyTrash());
        toolbar.add(emptyButton, "");

        add(toolbar, "growx, wrap");
    }

    private void initTable() {
        String[] columns = {"Sender", "Subject", "Date"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        emailTable = new JTable(tableModel);
        emailTable.setRowHeight(42);

        // Click chuột phải hiển thị menu
        emailTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    showContextMenu(e);
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(emailTable);
        add(scrollPane, "grow");
    }

    /** Load danh sách email trong thư mục Trash */
    public void loadTrashEmails() {
        tableModel.setRowCount(0);

        SwingWorker<List<Email>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<Email> doInBackground() throws Exception {
                // Gmail IMAP uses "[Gmail]/Trash"
                return controller.getImapService().fetchAllEmails("[Gmail]/Trash");
            }
            @Override
            protected void done() {
                try {
                    emails = get();
                    for (Email email : emails) {
                        tableModel.addRow(new Object[]{
                                email.getFrom(), email.getSubject(), email.getDate()
                        });
                    }
                    logger.info("Loaded {} emails from Trash", emails.size());
                } catch (Exception e) {
                    logger.error("Failed to load Trash emails", e);
                    Notifications.getInstance().show(Notifications.Type.ERROR, "Failed to load Trash");
                }
            }
        };
        worker.execute();
    }

    /** Hiển thị menu khôi phục / xóa */
    private void showContextMenu(MouseEvent e) {
        int row = emailTable.rowAtPoint(e.getPoint());
        if (row < 0 || row >= emails.size()) return;

        emailTable.setRowSelectionInterval(row, row);
        Email selectedEmail = emails.get(row);

        JPopupMenu menu = new JPopupMenu();

        JMenuItem restoreItem = new JMenuItem("Restore to Inbox");
        restoreItem.addActionListener(ev -> restoreEmail(selectedEmail));
        menu.add(restoreItem);

        JMenuItem deleteItem = new JMenuItem("Delete Permanently");
        deleteItem.addActionListener(ev -> deleteForever(selectedEmail));
        menu.add(deleteItem);

        menu.show(emailTable, e.getX(), e.getY());
    }

    private void restoreEmail(Email email) {
        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Restore this email to Inbox?",
                "Confirm Restore",
                JOptionPane.YES_NO_OPTION
        );

        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        //LƯU INDEX TRƯỚC KHI XÓA
        int row = emails.indexOf(email);

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                controller.getImapService().moveEmail("[Gmail]/Trash", email.getMessageNumber(), "INBOX");
                controller.refresh();
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();

                    //XÓA KHỎI LIST VÀ TABLE NGAY LẬP TỨC
                    if (row >= 0) {
                        emails.remove(row);
                        tableModel.removeRow(row);
                    }

                    Notifications.getInstance().show(Notifications.Type.SUCCESS, "Email restored to Inbox");
                    logger.info("Email restored and removed from Trash view");

                } catch (Exception e) {
                    logger.error("Failed to restore email", e);
                    Notifications.getInstance().show(Notifications.Type.ERROR, "Failed to restore: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void deleteForever(Email email) {
        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Permanently delete this email? This cannot be undone.",
                "Confirm Delete",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        //LƯU INDEX TRƯỚC KHI XÓA
        int row = emails.indexOf(email);

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                controller.getImapService().deleteEmail("[Gmail]/Trash", email.getMessageNumber());
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();

                    //XÓA KHỎI LIST VÀ TABLE NGAY LẬP TỨC
                    if (row >= 0) {
                        emails.remove(row);
                        tableModel.removeRow(row);
                    }

                    Notifications.getInstance().show(Notifications.Type.SUCCESS, "Email permanently deleted");
                    logger.info("Email deleted permanently and removed from view");

                } catch (Exception e) {
                    logger.error("Failed to delete email", e);
                    Notifications.getInstance().show(Notifications.Type.ERROR, "Failed to delete: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

//    private void restoreEmail(Email email) {
//        controller.restoreEmail(email);
//        loadTrashEmails();
//    }
//
//    private void deleteForever(Email email) {
//        controller.deleteForever(email);
//        loadTrashEmails();
//    }

    private void emptyTrash() {
        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Are you sure you want to permanently delete all emails in Trash?",
                "Confirm Empty Trash",
                JOptionPane.YES_NO_OPTION
        );
        if (confirm == JOptionPane.YES_OPTION) {
            SwingWorker<Void, Void> worker = new SwingWorker<>() {
                @Override
                protected Void doInBackground() throws Exception {
                    controller.getImapService().expunge("[Gmail]/Trash");
                    return null;
                }

                @Override
                protected void done() {
                    Notifications.getInstance().show(Notifications.Type.SUCCESS, "Trash emptied");
                    loadTrashEmails();
                }
            };
            worker.execute();
        }
    }
}
