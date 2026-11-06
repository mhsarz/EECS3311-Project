package com.recipeagent.gui;

import javax.swing.AbstractAction;
import javax.swing.DefaultRowSorter;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The check box column on the left of the Pantry, Cart and History tables: tick several rows
 * and remove them in one go. The header box checks or unchecks every row, and Space toggles
 * the selected row. Checks are kept by a key (item id, cart item name, recipe id), so they
 * stay after the table is refreshed.
 */
final class CheckColumn {

    /** The model column of the check boxes in all three tables. */
    static final int COLUMN = 0;
    static final int WIDTH = 36;
    /** Most names listed in the "remove these items?" question. */
    static final int MAX_NAMES = 8;

    private CheckColumn() {
    }

    /** What a table model with a check column provides. */
    interface Model {
        int getRowCount();

        boolean isRowChecked(int modelRow);

        void setRowChecked(int modelRow, boolean checked);

        int getCheckedCount();

        /** Checks or unchecks every row. */
        void setAllChecked(boolean checked);
    }

    /** The checked keys, in the order they were checked. */
    static final class Keys<K> {
        private final Set<K> keys = new LinkedHashSet<>();

        boolean contains(K key) {
            return keys.contains(key);
        }

        void set(K key, boolean checked) {
            if (checked) {
                keys.add(key);
            } else {
                keys.remove(key);
            }
        }

        /** Forgets keys that are no longer in the table (removed, or hidden by a search). */
        void retain(Collection<K> present) {
            keys.retainAll(new LinkedHashSet<>(present));
        }

        int size() {
            return keys.size();
        }

        void clear() {
            keys.clear();
        }
    }

    /**
     * Sets up the check column: a fixed narrow column with check boxes (using the row colours of
     * the table's own renderer), a "check all" box in the header, clicks and Space to toggle.
     *
     * @param onChange runs after any check changes (to update the Remove button)
     */
    static void install(JTable table, Model model, Runnable onChange) {
        TableColumn col = table.getColumnModel().getColumn(COLUMN);
        col.setMinWidth(WIDTH);
        col.setMaxWidth(WIDTH);
        col.setResizable(false);
        col.setCellRenderer(new BoxRenderer());
        col.setHeaderRenderer(new HeaderRenderer(model));
        if (table.getRowSorter() instanceof DefaultRowSorter<?, ?> sorter) {
            sorter.setSortable(COLUMN, false);
        }

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int viewRow = table.rowAtPoint(e.getPoint());
                if (SwingUtilities.isLeftMouseButton(e) && viewRow >= 0 && isCheckColumn(table, e)) {
                    int modelRow = table.convertRowIndexToModel(viewRow);
                    model.setRowChecked(modelRow, !model.isRowChecked(modelRow));
                    changed(table, onChange);
                }
            }
        });
        table.getTableHeader().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int viewCol = table.getTableHeader().columnAtPoint(e.getPoint());
                if (viewCol >= 0 && table.convertColumnIndexToModel(viewCol) == COLUMN && model.getRowCount() > 0) {
                    model.setAllChecked(!allChecked(model));
                    changed(table, onChange);
                }
            }
        });
        table.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "toggleCheck");
        table.getActionMap().put("toggleCheck", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                int viewRow = table.getSelectedRow();
                if (viewRow >= 0) {
                    int modelRow = table.convertRowIndexToModel(viewRow);
                    model.setRowChecked(modelRow, !model.isRowChecked(modelRow));
                    changed(table, onChange);
                }
            }
        });
    }

    /** True if the mouse is over the check column (the panels ignore double-clicks there). */
    static boolean isCheckColumn(JTable table, MouseEvent e) {
        int viewCol = table.columnAtPoint(e.getPoint());
        return viewCol >= 0 && table.convertColumnIndexToModel(viewCol) == COLUMN;
    }

    static boolean allChecked(Model model) {
        return model.getRowCount() > 0 && model.getCheckedCount() == model.getRowCount();
    }

    private static void changed(JTable table, Runnable onChange) {
        table.repaint();
        table.getTableHeader().repaint();
        onChange.run();
    }

    /** "Remove selected", or "Remove checked (3)" when rows are checked. */
    static String removeLabel(String plain, int checked) {
        return checked == 0 ? plain : "Remove checked (" + checked + ")";
    }

    /**
     * "Are you sure you want to remove these 3 items from your pantry?" followed by the names
     * (at most {@link #MAX_NAMES}, then "and 2 more").
     *
     * @param things "item"/"items" style word, e.g. "item" or "recipe"
     * @param where  "your pantry", "your cart", "your history"
     */
    static String removeQuestion(List<String> names, String things, String where) {
        int n = names.size();
        String what = n == 1 ? "this " + things : "these " + n + " " + things + "s";
        StringBuilder sb = new StringBuilder("Are you sure you want to remove " + what + " from " + where + "?\n\n");
        List<String> shown = names.subList(0, Math.min(MAX_NAMES, n));
        sb.append(String.join(", ", shown));
        if (n > shown.size()) {
            sb.append(" and ").append(n - shown.size()).append(" more");
        }
        return sb.toString();
    }

    /** A check box with the background of the row (so the Pantry's expiry tints show). */
    private static final class BoxRenderer implements TableCellRenderer {
        private final JCheckBox box = new JCheckBox();

        BoxRenderer() {
            box.setHorizontalAlignment(SwingConstants.CENTER);
            box.setBorderPainted(false);
            box.setOpaque(true);
        }

        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean selected,
                                                       boolean focus, int row, int column) {
            // Ask the table's own renderer for this row's colours, as if it were the next cell.
            int other = Math.min(t.getColumnCount() - 1, column + 1);
            Component c = t.getDefaultRenderer(Object.class)
                    .getTableCellRendererComponent(t, "", selected, false, row, other);
            box.setBackground(c.getBackground());
            box.setSelected(Boolean.TRUE.equals(value));
            box.setToolTipText(box.isSelected() ? "Checked (click to uncheck)" : "Check to remove with others");
            return box;
        }
    }

    /** The header box: checked when every row is checked; click to check or uncheck all. */
    private static final class HeaderRenderer implements TableCellRenderer {
        private final JCheckBox box = new JCheckBox();
        private final Model model;

        HeaderRenderer(Model model) {
            this.model = model;
            box.setHorizontalAlignment(SwingConstants.CENTER);
            box.setOpaque(false);
            box.setToolTipText("Check or uncheck all");
        }

        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean selected,
                                                       boolean focus, int row, int column) {
            box.setSelected(allChecked(model));
            return box;
        }
    }
}
