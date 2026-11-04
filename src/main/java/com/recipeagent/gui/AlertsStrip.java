package com.recipeagent.gui;

import com.recipeagent.model.Notification;
import com.recipeagent.model.NotificationType;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * The Alerts strip above the footer (F02 sale alerts, F05 expiry alerts), with the number
 * of alerts, a "Suggest a recipe" button for food that expires soon, and Clear.
 */
public class AlertsStrip extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final DefaultListModel<Alert> alerts = new DefaultListModel<>();
    private final JLabel count = new JLabel();

    public AlertsStrip(Runnable suggestRecipe) {
        super(new BorderLayout(12, 0));
        setBackground(Theme.CARD);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 1, 0, Theme.BORDER),
                BorderFactory.createEmptyBorder(6, 16, 6, 16)));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        left.setOpaque(false);
        left.add(Theme.label("Alerts", Font.BOLD, 12f, Theme.TEXT));
        count.setFont(Theme.font(Font.BOLD, 10f));
        count.setForeground(Theme.MUTED);
        left.add(count);
        add(left, BorderLayout.WEST);

        JList<Alert> list = new JList<>(alerts);
        list.setVisibleRowCount(2);
        list.setCellRenderer(new Renderer());
        list.setBackground(Theme.CARD);
        JScrollPane sp = new JScrollPane(list);
        sp.setBorder(null);
        add(sp, BorderLayout.CENTER);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 2));
        right.setOpaque(false);
        JButton suggest = Theme.ai("Suggest a recipe for expiring food", false);
        suggest.addActionListener(e -> suggestRecipe.run());
        JButton clear = Theme.secondary("Clear");
        clear.addActionListener(e -> clear());
        right.add(suggest);
        right.add(clear);
        add(right, BorderLayout.EAST);
        updateCount();
    }

    /** Adds an alert at the end (alerts arrive most urgent first). */
    public void addAlert(Notification n) {
        alerts.addElement(new Alert(n, LocalTime.now()));
        updateCount();
    }

    public void clear() {
        alerts.clear();
        updateCount();
    }

    public int getAlertCount() {
        return alerts.size();
    }

    private void updateCount() {
        count.setText(alerts.isEmpty() ? "" : String.valueOf(alerts.size()));
    }

    /** "Expiring: milk (fridge) expires tomorrow" / "On sale: milk at No Frills ...". */
    static String text(Notification n) {
        return (n.getType() == NotificationType.EXPIRY ? "Expiring: " : "On sale: ") + n.getMessage();
    }

    record Alert(Notification notification, LocalTime at) {
    }

    private static class Renderer extends DefaultListCellRenderer {
        private static final long serialVersionUID = 1L;

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean selected, boolean focus) {
            Alert a = (Alert) value;
            boolean expiry = a.notification().getType() == NotificationType.EXPIRY;
            String label = text(a.notification()) + "   · " + a.at().format(TIME);
            JLabel c = (JLabel) super.getListCellRendererComponent(list, label, index, selected, focus);
            c.setFont(Theme.font(Font.PLAIN, 12f));
            c.setIcon(new Dot(expiry ? Theme.WARN_FG : Theme.SALE_FG));
            if (!selected) {
                c.setForeground(expiry ? Theme.WARN_FG : Theme.SALE_FG);
                c.setBackground(Theme.CARD);
            }
            return c;
        }
    }

    /** A small coloured dot used as an alert icon. */
    static class Dot implements Icon {
        private final Color color;

        Dot(Color color) {
            this.color = color;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.fillOval(x + 1, y + 2, 8, 8);
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return 12;
        }

        @Override
        public int getIconHeight() {
            return 12;
        }
    }
}
