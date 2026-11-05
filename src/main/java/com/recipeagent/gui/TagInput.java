package com.recipeagent.gui;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.text.DefaultEditorKit;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * A list of short words shown as removable chips, e.g. allergies "peanuts" and "shellfish"
 * (F04). Type a word and press Enter or a comma to add it (pasting "peanuts, milk" adds both);
 * click × or press Backspace in the empty field to remove one. Words are stored in lower case
 * without duplicates, like UserProfile does. A word still being typed is added by
 * {@link #commitPending()}, which the Profile tab calls when you click Save.
 */
public class TagInput extends JPanel {

    private static final long serialVersionUID = 1L;

    private final transient List<String> tags = new ArrayList<>();
    private final JTextField field = new JTextField(12);
    private final Color chipFill;
    private final Color chipText;
    private transient Runnable onChange = () -> { };
    private transient Runnable onTyping = () -> { };

    public TagInput(String placeholder, Color chipFill, Color chipText) {
        super(new WrapLayout(FlowLayout.LEFT, 6, 4));
        this.chipFill = chipFill;
        this.chipText = chipText;
        setBackground(Theme.CARD);
        setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.BORDER),
                BorderFactory.createEmptyBorder(2, 4, 2, 4)));
        field.putClientProperty("JTextField.placeholderText", placeholder);
        field.setToolTipText(placeholder);
        field.setBorder(BorderFactory.createEmptyBorder(3, 2, 3, 2));
        field.setOpaque(false);
        field.addActionListener(e -> commitPending());      // Enter
        field.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(','), "comma");
        field.getActionMap().put("comma", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                commitPending();
            }
        });
        javax.swing.Action deleteChar = field.getActionMap().get(DefaultEditorKit.deletePrevCharAction);
        field.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), "backOrRemove");
        field.getActionMap().put("backOrRemove", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                if (field.getText().isEmpty() && !tags.isEmpty()) {
                    removeTag(tags.get(tags.size() - 1));      // Backspace in the empty field removes the last word
                } else if (deleteChar != null) {
                    deleteChar.actionPerformed(e);              // otherwise the normal Backspace
                }
            }
        });
        field.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                onTyping.run();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                onTyping.run();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                onTyping.run();
            }
        });
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                field.requestFocusInWindow();
            }
        });
        setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
        rebuild();
    }

    /** Called whenever a word is added or removed. */
    public void onChange(Runnable r) {
        onChange = r == null ? () -> { } : r;
    }

    /** Called whenever the text being typed (not yet a word) changes. */
    public void onTyping(Runnable r) {
        onTyping = r == null ? () -> { } : r;
    }

    /** Adds what is typed in the field (split at commas). */
    public void commitPending() {
        String text = field.getText();
        if (text.isBlank()) {
            field.setText("");
            return;
        }
        field.setText("");
        boolean changed = false;
        for (String t : splitTags(text)) {
            changed |= add(t);
        }
        rebuild();
        if (changed) {
            onChange.run();
        }
    }

    public List<String> getTags() {
        return Collections.unmodifiableList(tags);
    }

    /** Replaces all words (does not call onChange). */
    public void setTags(List<String> values) {
        tags.clear();
        field.setText("");
        if (values != null) {
            for (String v : values) {
                add(v);
            }
        }
        rebuild();
    }

    /** Adds one word; returns false if it was blank or already there. */
    public boolean addTag(String value) {
        boolean added = add(value);
        if (added) {
            rebuild();
            onChange.run();
        }
        return added;
    }

    public void removeTag(String value) {
        if (tags.remove(value)) {
            rebuild();
            onChange.run();
        }
        field.requestFocusInWindow();
    }

    String getPendingText() {
        return field.getText();
    }

    void setPendingText(String text) {
        field.setText(text);
    }

    private boolean add(String value) {
        String v = normalize(value);
        if (v.isEmpty() || tags.contains(v)) {
            return false;
        }
        tags.add(v);
        return true;
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        field.setEnabled(enabled);
        rebuild();
    }

    /** Replaces the chips but keeps the text field (removing it would lose the keyboard focus). */
    private void rebuild() {
        for (Component c : getComponents()) {
            if (c != field) {
                remove(c);
            }
        }
        if (field.getParent() != this) {
            add(field);
        }
        for (String t : tags) {
            add(new Tag(t), getComponentCount() - 1);   // before the text field
        }
        revalidate();
        repaint();
    }

    // ------------------------------------------------------------ helpers (tested)

    /** "Peanuts, shell fish ,," gives ["peanuts", "shell fish"]. */
    static List<String> splitTags(String text) {
        List<String> result = new ArrayList<>();
        if (text == null) {
            return result;
        }
        for (String part : text.split(",")) {
            String v = normalize(part);
            if (!v.isEmpty() && !result.contains(v)) {
                result.add(v);
            }
        }
        return result;
    }

    static String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    // ------------------------------------------------------------ painting

    /** One rounded chip with the word and a small × button. */
    private final class Tag extends JPanel {
        private static final long serialVersionUID = 1L;

        Tag(String text) {
            super(new BorderLayout(4, 0));
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 4));
            JLabel label = Theme.label(text, Font.PLAIN, 12f, chipText);
            add(label, BorderLayout.CENTER);
            JButton x = new JButton("×");
            x.setFont(Theme.font(Font.BOLD, 12f));
            x.setForeground(chipText);
            x.setContentAreaFilled(false);
            x.setBorderPainted(false);
            x.setFocusable(false);
            x.setMargin(new Insets(0, 2, 0, 2));
            x.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            x.setToolTipText("Remove " + text);
            x.setEnabled(TagInput.this.isEnabled());
            x.addActionListener(e -> removeTag(text));
            add(x, BorderLayout.EAST);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(chipFill);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** A FlowLayout that wraps onto more lines and reports the height it needs. */
    static final class WrapLayout extends FlowLayout {
        private static final long serialVersionUID = 1L;

        WrapLayout(int align, int hgap, int vgap) {
            super(align, hgap, vgap);
        }

        @Override
        public Dimension preferredLayoutSize(Container target) {
            return size(target, true);
        }

        @Override
        public Dimension minimumLayoutSize(Container target) {
            Dimension d = size(target, false);
            d.width -= getHgap() + 1;
            return d;
        }

        private Dimension size(Container target, boolean preferred) {
            synchronized (target.getTreeLock()) {
                int width = target.getWidth();
                if (width == 0) {
                    width = Integer.MAX_VALUE;
                }
                Insets in = target.getInsets();
                int max = width - in.left - in.right - getHgap() * 2;
                int rowW = 0;
                int rowH = 0;
                int w = 0;
                int h = 0;
                for (Component c : target.getComponents()) {
                    if (!c.isVisible()) {
                        continue;
                    }
                    Dimension d = preferred ? c.getPreferredSize() : c.getMinimumSize();
                    if (rowW + d.width > max && rowW > 0) {
                        w = Math.max(w, rowW);
                        h += rowH + getVgap();
                        rowW = 0;
                        rowH = 0;
                    }
                    rowW += d.width + getHgap();
                    rowH = Math.max(rowH, d.height);
                }
                w = Math.max(w, rowW);
                h += rowH;
                return new Dimension(w + in.left + in.right + getHgap() * 2,
                        h + in.top + in.bottom + getVgap() * 2);
            }
        }
    }
}
