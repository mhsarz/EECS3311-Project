package com.recipeagent.gui;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** The top bar: logo, app name, subtitle and today's date. */
public class HeaderBar extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.ENGLISH);

    private final JLabel date = Theme.muted("");

    public HeaderBar(LocalDate today) {
        super(new BorderLayout());
        setBackground(Theme.CARD);
        setBorder(BorderFactory.createEmptyBorder(10, 16, 4, 16));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);
        left.add(new Logo());
        left.add(Theme.label("Smart Pantry", Font.BOLD, 15f, Theme.TEXT));
        JLabel sep = Theme.muted("  AI Recipe & Grocery Agent");
        left.add(sep);
        add(left, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        right.setOpaque(false);
        right.add(date);
        add(right, BorderLayout.EAST);
        setDate(today);
    }

    public void setDate(LocalDate today) {
        date.setText(formatDate(today));
    }

    static String formatDate(LocalDate d) {
        return d.format(DATE);
    }

    /** A small leaf in a rounded square, drawn in code. */
    static class Logo extends JComponent {
        private static final long serialVersionUID = 1L;

        Logo() {
            setPreferredSize(new Dimension(26, 26));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Theme.PRIMARY_LIGHT);
            g2.fillRoundRect(0, 0, 26, 26, 8, 8);
            g2.setColor(Theme.PRIMARY);
            AffineTransform old = g2.getTransform();
            g2.rotate(Math.toRadians(-35), 13, 13);
            g2.fill(new Ellipse2D.Double(7, 9, 14, 8));
            g2.setTransform(old);
            g2.drawLine(8, 19, 15, 12);
            g2.dispose();
        }
    }
}
