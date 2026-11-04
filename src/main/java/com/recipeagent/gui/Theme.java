package com.recipeagent.gui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.UIManager;
import javax.swing.border.Border;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Colours, fonts and small building blocks for the "Smart Pantry" design.
 * Buttons and badges are painted here, so they look the same with any Swing look and feel.
 */
public final class Theme {

    public static final Color BACKGROUND = new Color(0xF6F7F5);
    public static final Color CARD = Color.WHITE;
    public static final Color BORDER = new Color(0xE2E5E0);
    public static final Color TEXT = new Color(0x1F2A22);
    public static final Color MUTED = new Color(0x6B7280);
    public static final Color PRIMARY = new Color(0x2F5D3F);
    public static final Color PRIMARY_HOVER = new Color(0x264D34);
    public static final Color PRIMARY_LIGHT = new Color(0xE6F0E8);
    public static final Color WARN_BG = new Color(0xFFF3CD);
    public static final Color WARN_FG = new Color(0x8A5A00);
    public static final Color DANGER_BG = new Color(0xFDE2E1);
    public static final Color DANGER_FG = new Color(0xB42318);
    public static final Color SALE_FG = new Color(0x1E6B3A);

    static final String FLATLAF_CLASS = "com.formdev.flatlaf.FlatLightLaf";

    private Theme() {
    }

    /**
     * Uses the FlatLaf look and feel if it is on the classpath (it is a Maven dependency),
     * otherwise the system look and feel, then applies the design's colours.
     *
     * @return true if FlatLaf was used
     */
    public static boolean install() {
        boolean flat = false;
        try {
            UIManager.setLookAndFeel(FLATLAF_CLASS);
            flat = true;
        } catch (Exception e) {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // keep the default look and feel
            }
        }
        UIManager.put("Panel.background", BACKGROUND);
        UIManager.put("Viewport.background", CARD);
        UIManager.put("Table.background", CARD);
        UIManager.put("Table.selectionBackground", PRIMARY_LIGHT);
        UIManager.put("Table.selectionForeground", TEXT);
        UIManager.put("Table.gridColor", BORDER);
        UIManager.put("Table.showHorizontalLines", true);
        UIManager.put("Table.showVerticalLines", false);
        UIManager.put("TableHeader.background", new Color(0xF0F2EF));
        UIManager.put("Component.arc", 8);
        UIManager.put("TextComponent.arc", 8);
        UIManager.put("Component.focusColor", PRIMARY);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("CheckBox.icon.checkmarkColor", PRIMARY);
        return flat;
    }

    public static Font font(int style, float size) {
        Font base = UIManager.getFont("Label.font");
        if (base == null) {
            base = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
        }
        return base.deriveFont(style, size);
    }

    public static JLabel label(String text, int style, float size, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(font(style, size));
        l.setForeground(color);
        return l;
    }

    public static JLabel muted(String text) {
        return label(text, Font.PLAIN, 12f, MUTED);
    }

    /** White card with a light border and padding. */
    public static Border card(int padding) {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(padding, padding, padding, padding));
    }

    /** Filled green button, e.g. "Add item". */
    public static JButton primary(String text) {
        return new FlatButton(text, true, false);
    }

    /** White button with a border, e.g. "Cancel". */
    public static JButton secondary(String text) {
        return new FlatButton(text, false, false);
    }

    /** Button for a feature that uses the AI; shows a small "AI" badge. */
    public static JButton ai(String text, boolean primary) {
        return new FlatButton(text, primary, true);
    }

    /** A rounded button painted by hand so it looks the same with every look and feel. */
    static class FlatButton extends JButton {
        private static final long serialVersionUID = 1L;
        private static final int BADGE_W = 22;
        private final boolean primary;
        private final boolean aiBadge;

        FlatButton(String text, boolean primary, boolean aiBadge) {
            super(text);
            this.primary = primary;
            this.aiBadge = aiBadge;
            setFont(font(Font.BOLD, 12f));
            setForeground(primary ? Color.WHITE : TEXT);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(BorderFactory.createEmptyBorder(6, 12, 6, aiBadge ? 12 + BADGE_W + 6 : 12));
        }

        boolean hasAiBadge() {
            return aiBadge;
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            return new Dimension(d.width, Math.max(d.height, 30));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean on = isEnabled();
            Color fill = primary ? (getModel().isRollover() && on ? PRIMARY_HOVER : PRIMARY) : CARD;
            if (!on) {
                fill = primary ? new Color(0xA7B8AC) : new Color(0xF3F4F2);
            }
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
            if (!primary) {
                g2.setColor(BORDER);
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
            }
            if (aiBadge) {
                int h = 16;
                int x = getWidth() - 12 - BADGE_W;
                int y = (getHeight() - h) / 2;
                g2.setColor(primary ? new Color(255, 255, 255, 60) : PRIMARY_LIGHT);
                g2.fillRoundRect(x, y, BADGE_W, h, 6, 6);
                g2.setFont(font(Font.BOLD, 10f));
                g2.setColor(primary ? Color.WHITE : PRIMARY);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString("AI", x + (BADGE_W - fm.stringWidth("AI")) / 2, y + (h + fm.getAscent()) / 2 - 2);
            }
            g2.dispose();
            super.paintComponent(g);
        }

        @Override
        public void setEnabled(boolean enabled) {
            super.setEnabled(enabled);
            setForeground(enabled ? (primary ? Color.WHITE : TEXT) : MUTED);
        }
    }
}
