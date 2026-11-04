package com.recipeagent.gui;

import com.recipeagent.controller.AgentController;
import com.recipeagent.model.PantryItem;
import com.recipeagent.model.StorageLocation;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.time.LocalDate;

/**
 * The Pantry tab (F03, F05): a table of what is at home, a form to add items, and a button
 * to remove the selected item. Items close to their best-before date are highlighted.
 */
public class PantryPanel extends JPanel {

    private static final Color EXPIRING = new Color(255, 236, 179);
    private static final Color EXPIRED = new Color(255, 205, 210);

    private final AgentController controller;
    private final PantryTableModel model = new PantryTableModel();
    private final JTable table = new JTable(model);

    private final PageHeader header = new PageHeader("Pantry", "Know what you have. Use it before it goes.");
    private final JTextField nameField = new JTextField(14);
    private final JTextField qtyField = new JTextField(5);
    private final JTextField unitField = new JTextField(6);
    private final JComboBox<StorageLocation> locationBox = new JComboBox<>(StorageLocation.values());
    private final JTextField dateField = new JTextField(10);

    public PantryPanel(AgentController controller) {
        super(new BorderLayout(8, 8));
        this.controller = controller;
        setBorder(BorderFactory.createEmptyBorder(16, 20, 12, 16));
        add(header, BorderLayout.NORTH);

        locationBox.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index,
                                                          boolean selected, boolean focus) {
                String text = value == null ? "" : capitalize(value.toString());
                return super.getListCellRendererComponent(list, text, index, selected, focus);
            }
        });
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(24);
        table.setAutoCreateRowSorter(false);
        table.setRowSorter(new TableRowSorter<>(model));
        table.setDefaultRenderer(Object.class, new ExpiryRenderer());
        table.setDefaultRenderer(Double.class, new ExpiryRenderer());
        table.setDefaultRenderer(Integer.class, new ExpiryRenderer());
        add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(buildForm(), BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton remove = Theme.secondary("Remove selected");
        remove.addActionListener(e -> removeSelected());
        actions.add(remove);
        bottom.add(actions, BorderLayout.SOUTH);
        add(bottom, BorderLayout.SOUTH);

        refresh();
    }

    private JPanel buildForm() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createTitledBorder("Add item"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;

        String[] labels = {"Name", "Quantity", "Unit", "Location", "Best before (YYYY-MM-DD, optional)"};
        java.awt.Component[] fields = {nameField, qtyField, unitField, locationBox, dateField};
        for (int i = 0; i < labels.length; i++) {
            c.gridx = i;
            c.gridy = 0;
            form.add(new JLabel(labels[i]), c);
            c.gridy = 1;
            form.add(fields[i], c);
        }
        JButton add = Theme.primary("+ Add item");
        add.addActionListener(e -> addItem());
        c.gridx = labels.length;
        c.gridy = 1;
        form.add(add, c);
        dateField.addActionListener(e -> addItem());   // Enter in the last field adds the item
        return form;
    }

    /** Reloads the table from the controller. */
    public void refresh() {
        model.setItems(controller.getPantryItems(), controller.today());
        int n = model.getRowCount();
        header.setRight(n + (n == 1 ? " item" : " items"));
    }

    private void addItem() {
        PantryItem item;
        try {
            String name = InputParser.name(nameField.getText(), "Name");
            double qty = InputParser.quantity(qtyField.getText());
            LocalDate date = InputParser.optionalDate(dateField.getText());
            item = new PantryItem(name, qty, unitField.getText(),
                    (StorageLocation) locationBox.getSelectedItem(), date);
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
            return;
        }

        boolean added = controller.addPantryItem(item, false);
        if (!added) {
            int choice = JOptionPane.showOptionDialog(this,
                    "\"" + item.getName() + "\" is already in your pantry.\n"
                            + "Add " + InputParser.formatQuantity(item.getQuantity())
                            + " to the existing quantity, or was this a mistake?",
                    "Item already exists", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE,
                    null, new String[]{"Merge", "Cancel"}, "Merge");
            if (choice != 0) {
                return;
            }
            controller.addPantryItem(item, true);
        }
        clearForm();
        refresh();
    }

    private void removeSelected() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            showError("Select an item in the table first.");
            return;
        }
        PantryItem item = model.getItemAt(table.convertRowIndexToModel(viewRow));
        int ok = JOptionPane.showConfirmDialog(this, "Remove \"" + item.getName() + "\" from the pantry?",
                "Remove item", JOptionPane.OK_CANCEL_OPTION);
        if (ok == JOptionPane.OK_OPTION) {
            controller.removePantryItem(item.getId());
            refresh();
        }
    }

    private void clearForm() {
        nameField.setText("");
        qtyField.setText("");
        unitField.setText("");
        dateField.setText("");
        locationBox.setSelectedIndex(0);
        nameField.requestFocusInWindow();
    }

    static String capitalize(String upper) {
        String lower = upper.toLowerCase(java.util.Locale.ROOT);
        return lower.isEmpty() ? lower : Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Please check the form", JOptionPane.WARNING_MESSAGE);
    }

    /** Colours rows that expire within 3 days (yellow) or have expired (red). */
    private class ExpiryRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean selected,
                                                       boolean focus, int row, int column) {
            Object shown = value;
            if (value instanceof Double) {
                shown = InputParser.formatQuantity((Double) value);
            }
            Component comp = super.getTableCellRendererComponent(t, shown, selected, focus, row, column);
            if (!selected) {
                Object days = model.getValueAt(t.convertRowIndexToModel(row), PantryTableModel.DAYS_LEFT_COLUMN);
                if (days instanceof Integer && (Integer) days < 0) {
                    comp.setBackground(EXPIRED);
                } else if (days instanceof Integer && (Integer) days <= AgentController.EXPIRY_DAYS) {
                    comp.setBackground(EXPIRING);
                } else {
                    comp.setBackground(t.getBackground());
                }
            }
            return comp;
        }
    }
}
