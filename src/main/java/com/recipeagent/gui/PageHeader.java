package com.recipeagent.gui;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Font;

/** The title and one-line description at the top of each page, with an optional count on the right. */
public class PageHeader extends JPanel {

    private static final long serialVersionUID = 1L;

    private final JLabel right = Theme.muted("");

    public PageHeader(String title, String subtitle) {
        super(new BorderLayout());
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        JPanel text = new JPanel(new BorderLayout(0, 2));
        text.setOpaque(false);
        text.add(Theme.label(title, Font.BOLD, 22f, Theme.TEXT), BorderLayout.NORTH);
        text.add(Theme.muted(subtitle), BorderLayout.SOUTH);
        add(text, BorderLayout.WEST);
        add(right, BorderLayout.EAST);
    }

    /** Text on the right, e.g. "6 items". */
    public void setRight(String text) {
        right.setText(text);
    }

    String getRightText() {
        return right.getText();
    }
}
