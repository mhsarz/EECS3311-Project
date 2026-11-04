package com.recipeagent.gui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The row of page tabs under the header (Pantry, Recipe, Cart, ...). The selected tab is
 * bold with a green underline. Drawn by hand so it looks like the design on any look and feel.
 */
public class TabBar extends JPanel {

    private static final long serialVersionUID = 1L;

    private final Map<String, Tab> tabs = new LinkedHashMap<>();
    private final List<Consumer<String>> listeners = new ArrayList<>();
    private String selected;

    public TabBar() {
        super(new FlowLayout(FlowLayout.LEFT, 2, 0));
        setBackground(Theme.CARD);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
                BorderFactory.createEmptyBorder(0, 12, 0, 12)));
    }

    /** Adds a tab; the first one added is selected. */
    public void addTab(String name) {
        Tab t = new Tab(name);
        t.setFont(Theme.font(Font.PLAIN, 13f));
        t.setForeground(Theme.MUTED);
        t.addActionListener(e -> select(name));
        tabs.put(name, t);
        add(t);
        if (selected == null) {
            select(name);
        }
    }

    /** Called with the tab name whenever a tab is selected. */
    public void onSelect(Consumer<String> listener) {
        listeners.add(listener);
    }

    public void select(String name) {
        if (!tabs.containsKey(name)) {
            throw new IllegalArgumentException("No tab named " + name);
        }
        selected = name;
        for (Tab t : tabs.values()) {
            t.setFont(Theme.font(t.getText().equals(name) ? Font.BOLD : Font.PLAIN, 13f));
            t.setForeground(t.getText().equals(name) ? Theme.TEXT : Theme.MUTED);
            t.repaint();
        }
        for (Consumer<String> l : listeners) {
            l.accept(name);
        }
    }

    public String getSelected() {
        return selected;
    }

    public List<String> getTabNames() {
        return new ArrayList<>(tabs.keySet());
    }

    private class Tab extends JButton {
        private static final long serialVersionUID = 1L;

        Tab(String name) {
            super(name);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(9, 16, 9, 16));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (getText().equals(selected)) {
                g.setColor(Theme.PRIMARY_LIGHT);
                g.fillRoundRect(0, 2, getWidth(), getHeight() - 4, 8, 8);
                g.setColor(Theme.PRIMARY);
                g.fillRect(6, getHeight() - 2, getWidth() - 12, 2);
            }
            super.paintComponent(g);
        }
    }
}
