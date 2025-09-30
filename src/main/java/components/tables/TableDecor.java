package components.tables;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.geom.Rectangle2D;

public class TableDecor extends DefaultTableCellRenderer {
    private boolean isSelected;

    public void setSelected(boolean selected) {
        this.isSelected = selected;
    }

    public TableDecor () {
        this (Color.decode("#009FFF"), Color.decode("#ec2F4B"));
        this.isSelected = false;
    }
    public TableDecor (Color A, Color B) {
        this.color1 = A;
        this.color2 = B;
        setOpaque(false);
    }
    private final Color color1;
    private final Color color2;
    private int x;
    private int width;

    private int row;
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        Component cpn =  super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        Rectangle cellRect = table.getCellRect(row, column, true);
        width = table.getWidth() - cellRect.x;
        this.isSelected = isSelected;
        this.row = row;
        return cpn;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D)g.create();
        if (isSelected) {
            g2.setPaint(new GradientPaint(x, 0, color1, width,0,  color2));
            g2.fill(new Rectangle2D.Double(0, 0, getWidth(), getHeight()));
        } else if (row % 2 == 0) {
            g2.setPaint(new GradientPaint(x, 0, Color.decode("#000000"), width, 0, Color.decode("#434343")));
            g2.fill(new Rectangle2D.Double(0, 0, getWidth(), getHeight()));
        }

        g2.dispose();
        super.paintComponent(g);
    }
}
