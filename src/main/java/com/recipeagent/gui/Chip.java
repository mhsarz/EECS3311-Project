package com.recipeagent.gui;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** A small rounded label with a coloured background, e.g. "Use soon" or "Uses 2 expiring items". */
class Chip extends JLabel {

    private static final long serialVersionUID = 1L;
    private Color fill;

    Chip(String text, Color fill, Color textColour) {
        super(text);
        setFont(Theme.font(Font.BOLD, 11f));
        setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
        setColours(fill, textColour);
    }

    void setColours(Color fill, Color textColour) {
        this.fill = fill;
        setForeground(textColour);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(fill);
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
        g2.dispose();
        super.paintComponent(g);
    }
}
