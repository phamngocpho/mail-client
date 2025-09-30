package components.menus;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;

public class PopupMenu extends JPopupMenu {
    private final JMenuItem show;
    private final JMenuItem edit;
    private final JMenuItem delete;
    private final JMenuItem add;

    public JMenuItem getShow() {
        return show;
    }

    public JMenuItem getDelete() {
        return delete;
    }

    public JMenuItem getAdd() {
        return add;
    }

    public PopupMenu (JTable table) {
        show = new JMenuItem("Show user information");
        setIconMenuItem(show, "show.png");
        edit = new JMenuItem("Edit user information");
        setIconMenuItem(edit, "edit.png");
        delete = new JMenuItem("Delete User");
        add = new JMenuItem("Add Student");
        setIconMenuItem(delete, "bin.png");
        show.addActionListener(e -> {
        });
        edit.addActionListener(e-> {
        });
        table.getSelectionModel().addListSelectionListener(e -> {
            int select = table.getSelectedRowCount();
            show.setEnabled(select == 1);
            edit.setEnabled(select == 1);
            delete.setEnabled(select > 0);
        });
        add(show);
        add(new JSeparator());
        add(edit);
        add(new JSeparator());
        add(delete);
        add(new JSeparator());
        add(add);
    }
    private void setIconMenuItem (JMenuItem menuItem, String image) {
        // size 22*22
        ImageIcon imageIcon = new ImageIcon(Objects.requireNonNull(getClass().getResource("/Pictures/Icon/" + image)));
        Image img = (imageIcon).getImage().getScaledInstance(22, 22, Image.SCALE_SMOOTH);
        imageIcon = new ImageIcon(img);
        menuItem.setIcon(imageIcon);
    }
}
