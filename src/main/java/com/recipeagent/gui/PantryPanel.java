package com.recipeagent.gui;

import com.recipeagent.controller.AgentController;
import com.recipeagent.model.PantryItem;
import com.recipeagent.model.StorageLocation;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.util.Locale;

/**
 * The Pantry page (F03, F05), as in the Smart Pantry design: the inventory table with
 * "Use soon" / "Expired" chips and a colour legend, an empty state, and an "Add item" form
 * with a unit list, a best-before date picker (a week from today by default) and inline errors.
 * Adding an item that already exists opens a merge dialog showing the new total. A selected
 * item can be changed with Edit (or a double-click) and removed with Remove selected.
 */
public class PantryPanel extends JPanel {

    private static final long serialVersionUID = 1L;
    static final String[] UNITS = {"pcs", "g", "kg", "ml", "L", "cup", "tbsp", "tsp", "can", "bag",
            "bottle", "pack", "bunch"};
    static final Color ROW_USE_SOON = new Color(0xFFF8E1);
    static final Color ROW_EXPIRED = new Color(0xFFF0EF);
    private static final String TABLE = "table";
    private static final String EMPTY = "empty";

    private final transient AgentController controller;
    private final PageHeader header = new PageHeader("Pantry", "Know what you have. Use it before it goes.");
    private final PantryTableModel model = new PantryTableModel();
    private final JTable table = new JTable(model);
    private final CardLayout cards = new CardLayout();
    private final JPanel tableArea = new JPanel(cards);
    private final JButton editButton = Theme.secondary("Edit");
    private final JButton removeButton = Theme.secondary("Remove selected");

    private final JTextField nameField = new JTextField(16);
    private final JTextField qtyField = new JTextField("1", 4);
    private final JComboBox<String> unitBox = new JComboBox<>(UNITS);
    private final JComboBox<StorageLocation> locationBox = new JComboBox<>(StorageLocation.values());
    private final DatePicker datePicker;
    private final JCheckBox noDate = new JCheckBox("No date");
    private final JLabel errorLabel = Theme.label(" ", Font.PLAIN, 12f, Theme.DANGER_FG);
    private final transient Border nameBorder = nameField.getBorder();
    private final transient Border qtyBorder = qtyField.getBorder();

    public PantryPanel(AgentController controller) {
        super(new BorderLayout(0, 12));
        this.controller = controller;
        this.datePicker = new DatePicker(controller.today().plusDays(7), controller::today);
        setBorder(BorderFactory.createEmptyBorder(16, 20, 12, 16));
        add(header, BorderLayout.NORTH);
        add(buildInventory(), BorderLayout.CENTER);
        add(buildForm(), BorderLayout.SOUTH);
        refresh();
    }

    // ------------------------------------------------------------ inventory

    private JPanel buildInventory() {
        JPanel card = new JPanel(new BorderLayout(0, 8));
        card.setOpaque(false);

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(Theme.label("Your inventory", Font.BOLD, 13f, Theme.TEXT), BorderLayout.WEST);
        editButton.setToolTipText("Change the selected item (or double-click it)");
        editButton.addActionListener(e -> editSelected());
        removeButton.addActionListener(e -> removeSelected());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(editButton);
        buttons.add(removeButton);
        top.add(buttons, BorderLayout.EAST);
        card.add(top, BorderLayout.NORTH);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(30);
        table.setShowVerticalLines(false);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.setRowSorter(new TableRowSorter<>(model));
        table.getTableHeader().setReorderingAllowed(false);
        CellRenderer renderer = new CellRenderer();
        table.setDefaultRenderer(Object.class, renderer);
        table.setDefaultRenderer(Double.class, renderer);
        table.setDefaultRenderer(Integer.class, renderer);
        table.setDefaultRenderer(LocalDate.class, renderer);
        table.getSelectionModel().addListSelectionListener(e -> updateButtons());
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && table.rowAtPoint(e.getPoint()) >= 0) {
                    editSelected();
                }
            }
        });
        JScrollPane sp = new JScrollPane(table);
        sp.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        sp.getViewport().setBackground(Theme.CARD);
        tableArea.setOpaque(false);
        tableArea.add(sp, TABLE);
        tableArea.add(buildEmptyState(), EMPTY);
        card.add(tableArea, BorderLayout.CENTER);

        JPanel legend = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        legend.setOpaque(false);
        legend.add(Theme.muted("Best before dates"));
        legend.add(legendItem(Theme.WARN_BG, Theme.WARN_FG, "Expires within " + AgentController.EXPIRY_DAYS + " days"));
        legend.add(legendItem(Theme.DANGER_BG, Theme.DANGER_FG, "Expired"));
        card.add(legend, BorderLayout.SOUTH);
        return card;
    }

    private static JLabel legendItem(Color bg, Color fg, String text) {
        JLabel l = Theme.label(text, Font.PLAIN, 12f, fg);
        l.setIcon(new Swatch(bg, fg));
        return l;
    }

    private JPanel buildEmptyState() {
        JPanel empty = new JPanel(new GridBagLayout());
        empty.setBackground(Theme.CARD);
        empty.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        JPanel box = new JPanel();
        box.setOpaque(false);
        box.setLayout(new javax.swing.BoxLayout(box, javax.swing.BoxLayout.Y_AXIS));
        JLabel icon = new JLabel(new BoxIcon());
        JLabel title = Theme.label("Your pantry is ready to fill", Font.BOLD, 15f, Theme.TEXT);
        JLabel text = Theme.muted("<html><div style='text-align:center;width:260px'>Add your first item below. "
                + "Start with the food in your fridge, then check your cupboards.</div></html>");
        for (JComponent c : new JComponent[]{icon, title, text}) {
            c.setAlignmentX(CENTER_ALIGNMENT);
            ((JLabel) c).setHorizontalAlignment(SwingConstants.CENTER);
            box.add(c);
            box.add(javax.swing.Box.createVerticalStrut(8));
        }
        empty.add(box);
        return empty;
    }

    // ------------------------------------------------------------ form

    private JPanel buildForm() {
        JPanel card = new JPanel(new BorderLayout(0, 6));
        card.setBackground(Theme.CARD);
        card.setBorder(Theme.card(12));
        card.add(Theme.label("Add item", Font.BOLD, 13f, Theme.TEXT), BorderLayout.NORTH);

        unitBox.setEditable(true);
        unitBox.setSelectedItem("pcs");
        locationBox.setSelectedItem(StorageLocation.FRIDGE);
        locationBox.setRenderer(new LocationRenderer());
        noDate.setOpaque(false);
        noDate.setToolTipText("For food without a best-before date, like salt or rice");
        noDate.addActionListener(e -> datePicker.setEnabled(!noDate.isSelected()));

        JPanel row = new JPanel(new GridBagLayout());
        row.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 0, 2, 10);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        String[] labels = {"Name", "Qty", "Unit", "Location", "Best before"};
        JComponent[] fields = {nameField, qtyField, unitBox, locationBox, datePanel(datePicker, noDate)};
        double[] weights = {1.0, 0, 0, 0, 0};
        for (int i = 0; i < labels.length; i++) {
            c.gridx = i;
            c.gridy = 0;
            c.weightx = weights[i];
            row.add(Theme.muted(labels[i]), c);
            c.gridy = 1;
            row.add(fields[i], c);
        }
        JButton add = Theme.primary("+ Add item");
        add.addActionListener(e -> addItem());
        nameField.addActionListener(e -> addItem());
        qtyField.addActionListener(e -> addItem());
        c.gridx = labels.length;
        c.gridy = 1;
        c.weightx = 0;
        c.insets = new Insets(2, 0, 2, 0);
        row.add(add, c);
        card.add(row, BorderLayout.CENTER);
        card.add(errorLabel, BorderLayout.SOUTH);
        return card;
    }

    private static JPanel datePanel(DatePicker picker, JCheckBox none) {
        JPanel p = new JPanel(new BorderLayout(6, 0));
        p.setOpaque(false);
        p.add(picker, BorderLayout.CENTER);
        p.add(none, BorderLayout.EAST);
        return p;
    }

    private void resetDate() {
        datePicker.setDate(controller.today().plusDays(7));
    }

    private LocalDate selectedDate() {
        return noDate.isSelected() ? null : datePicker.getDate();
    }

    /** Reloads the table from the controller and shows the empty state when there is nothing. */
    public void refresh() {
        model.setItems(controller.getPantryItems(), controller.today());
        int n = model.getRowCount();
        header.setRight(n + (n == 1 ? " item" : " items"));
        cards.show(tableArea, n == 0 ? EMPTY : TABLE);
        updateButtons();
    }

    private void updateButtons() {
        boolean selected = model.getRowCount() > 0 && table.getSelectedRow() >= 0;
        editButton.setEnabled(selected);
        removeButton.setEnabled(selected);
    }

    // ------------------------------------------------------------ actions

    private void addItem() {
        String unit = String.valueOf(unitBox.getEditor().getItem()).trim();
        FormCheck check = checkForm(nameField.getText(), qtyField.getText());
        markError(nameField, nameBorder, check.nameBad());
        markError(qtyField, qtyBorder, check.qtyBad());
        errorLabel.setText(check.message() == null ? " " : check.message());
        if (check.message() != null) {
            (check.nameBad() ? nameField : qtyField).requestFocusInWindow();
            return;
        }
        PantryItem item = new PantryItem(nameField.getText().trim(), InputParser.quantity(qtyField.getText()),
                unit, (StorageLocation) locationBox.getSelectedItem(), selectedDate());

        if (!controller.addPantryItem(item, false)) {
            PantryItem existing = controller.getPantryItems().stream()
                    .filter(p -> p.hasName(item.getName())).findFirst().orElse(null);
            if (existing == null || !askMerge(existing, item)) {
                return;
            }
            controller.addPantryItem(item, true);
        }
        clearForm();
        refresh();
    }

    /** The custom merge dialog from the design. Returns true if the user chose Merge. */
    private boolean askMerge(PantryItem existing, PantryItem adding) {
        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(owner, "Duplicate item", java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        JPanel body = new JPanel(new BorderLayout(0, 10));
        body.setBackground(Theme.CARD);
        body.setBorder(BorderFactory.createEmptyBorder(16, 18, 14, 18));
        JPanel text = new JPanel(new BorderLayout(0, 4));
        text.setOpaque(false);
        text.add(Theme.label(existing.getName() + " already in pantry", Font.BOLD, 15f, Theme.TEXT), BorderLayout.NORTH);
        text.add(Theme.muted("Merge this quantity with the " + existing.getName() + " in your "
                + PantryTableModel.location(existing).toLowerCase(Locale.ROOT) + "?"), BorderLayout.SOUTH);
        body.add(text, BorderLayout.NORTH);

        JPanel summary = new JPanel(new FlowLayout(FlowLayout.LEFT, 18, 6));
        summary.setBackground(new Color(0xF6F7F5));
        summary.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        String[] parts = mergeSummary(existing, adding);
        summary.add(Theme.label(parts[0], Font.PLAIN, 12f, Theme.TEXT));
        summary.add(Theme.label(parts[1], Font.PLAIN, 12f, Theme.TEXT));
        summary.add(Theme.label(parts[2], Font.BOLD, 12f, Theme.TEXT));
        JPanel middle = new JPanel(new BorderLayout(0, 4));
        middle.setOpaque(false);
        middle.add(summary, BorderLayout.NORTH);
        String warning = unitWarning(existing, adding);
        if (warning != null) {
            middle.add(Theme.label(warning, Font.PLAIN, 11f, Theme.WARN_FG), BorderLayout.SOUTH);
        }
        body.add(middle, BorderLayout.CENTER);

        boolean[] merged = {false};
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        JButton cancel = Theme.secondary("Cancel");
        JButton merge = Theme.primary("Merge");
        cancel.addActionListener(e -> dialog.dispose());
        merge.addActionListener(e -> {
            merged[0] = true;
            dialog.dispose();
        });
        buttons.add(cancel);
        buttons.add(merge);
        body.add(buttons, BorderLayout.SOUTH);

        dialog.setContentPane(body);
        dialog.getRootPane().setDefaultButton(merge);
        dialog.pack();
        dialog.setMinimumSize(new Dimension(380, dialog.getHeight()));
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
        return merged[0];
    }

    /** The "Edit item" dialog for the selected row (F03). */
    private void editSelected() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            return;
        }
        PantryItem item = model.getItemAt(table.convertRowIndexToModel(viewRow));
        new EditDialog(item).setVisible(true);
        refresh();
        selectItem(item.getId());
    }

    private void selectItem(String id) {
        for (int r = 0; r < model.getRowCount(); r++) {
            if (model.getItemAt(r).getId().equals(id)) {
                int view = table.convertRowIndexToView(r);
                table.getSelectionModel().setSelectionInterval(view, view);
                return;
            }
        }
    }

    private final class EditDialog extends JDialog {
        private static final long serialVersionUID = 1L;
        private final transient PantryItem item;
        private final JTextField name = new JTextField(16);
        private final JTextField qty = new JTextField(5);
        private final JComboBox<String> unit = new JComboBox<>(UNITS);
        private final JComboBox<StorageLocation> location = new JComboBox<>(StorageLocation.values());
        private final DatePicker date;
        private final JCheckBox none = new JCheckBox("No date");
        private final JLabel error = Theme.label(" ", Font.PLAIN, 12f, Theme.DANGER_FG);
        private final transient Border nameNormal = name.getBorder();
        private final transient Border qtyNormal = qty.getBorder();

        EditDialog(PantryItem item) {
            super(SwingUtilities.getWindowAncestor(PantryPanel.this), "Edit item",
                    java.awt.Dialog.ModalityType.APPLICATION_MODAL);
            this.item = item;
            date = new DatePicker(item.hasBestBefore() ? item.getBestBefore() : controller.today().plusDays(7),
                    controller::today);
            name.setText(item.getName());
            qty.setText(InputParser.formatQuantity(item.getQuantity()));
            unit.setEditable(true);
            unit.setSelectedItem(item.getUnit());
            location.setRenderer(new LocationRenderer());
            location.setSelectedItem(item.getLocation());
            none.setOpaque(false);
            none.setSelected(!item.hasBestBefore());
            date.setEnabled(item.hasBestBefore());
            none.addActionListener(e -> date.setEnabled(!none.isSelected()));

            JPanel body = new JPanel(new BorderLayout(0, 10));
            body.setBackground(Theme.CARD);
            body.setBorder(BorderFactory.createEmptyBorder(16, 18, 14, 18));
            body.add(Theme.label("Edit " + item.getName(), Font.BOLD, 15f, Theme.TEXT), BorderLayout.NORTH);

            JPanel grid = new JPanel(new GridBagLayout());
            grid.setOpaque(false);
            GridBagConstraints c = new GridBagConstraints();
            c.insets = new Insets(4, 0, 4, 10);
            c.anchor = GridBagConstraints.WEST;
            c.fill = GridBagConstraints.HORIZONTAL;
            String[] labels = {"Name", "Qty", "Unit", "Location", "Best before"};
            JComponent[] fields = {name, qty, unit, location, datePanel(date, none)};
            for (int i = 0; i < labels.length; i++) {
                c.gridy = i;
                c.gridx = 0;
                c.weightx = 0;
                grid.add(Theme.muted(labels[i]), c);
                c.gridx = 1;
                c.weightx = 1;
                grid.add(fields[i], c);
            }
            JPanel middle = new JPanel(new BorderLayout(0, 6));
            middle.setOpaque(false);
            middle.add(grid, BorderLayout.CENTER);
            middle.add(error, BorderLayout.SOUTH);
            body.add(middle, BorderLayout.CENTER);

            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            buttons.setOpaque(false);
            JButton cancel = Theme.secondary("Cancel");
            JButton save = Theme.primary("Save changes");
            cancel.addActionListener(e -> dispose());
            save.addActionListener(e -> save());
            buttons.add(cancel);
            buttons.add(save);
            body.add(buttons, BorderLayout.SOUTH);

            setContentPane(body);
            getRootPane().setDefaultButton(save);
            pack();
            setMinimumSize(new Dimension(400, getHeight()));
            setLocationRelativeTo(PantryPanel.this);
        }

        private void save() {
            FormCheck check = checkForm(name.getText(), qty.getText());
            markError(name, nameNormal, check.nameBad());
            markError(qty, qtyNormal, check.qtyBad());
            if (check.message() != null) {
                error.setText(check.message());
                return;
            }
            try {
                controller.updatePantryItem(item.getId(), name.getText().trim(), InputParser.quantity(qty.getText()),
                        String.valueOf(unit.getEditor().getItem()).trim(), (StorageLocation) location.getSelectedItem(),
                        none.isSelected() ? null : date.getDate());
                dispose();
            } catch (IllegalArgumentException ex) {
                markError(name, nameNormal, ex.getMessage().startsWith("There is already"));
                error.setText(ex.getMessage());
            }
        }
    }

    /** Shows FRIDGE as "Fridge" in the location lists. */
    private static final class LocationRenderer extends javax.swing.DefaultListCellRenderer {
        private static final long serialVersionUID = 1L;

        @Override
        public Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index,
                                                      boolean selected, boolean focus) {
            String text = value == null ? "" : capitalize(value.toString());
            return super.getListCellRendererComponent(list, text, index, selected, focus);
        }
    }

    private void removeSelected() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            return;
        }
        PantryItem item = model.getItemAt(table.convertRowIndexToModel(viewRow));
        int ok = JOptionPane.showConfirmDialog(this, "Remove \"" + item.getName() + "\" from your pantry?",
                "Remove item", JOptionPane.OK_CANCEL_OPTION);
        if (ok == JOptionPane.OK_OPTION) {
            controller.removePantryItem(item.getId());
            refresh();
        }
    }

    private void clearForm() {
        nameField.setText("");
        qtyField.setText("1");
        noDate.setSelected(false);
        datePicker.setEnabled(true);
        resetDate();
        errorLabel.setText(" ");
        nameField.requestFocusInWindow();
    }

    // ------------------------------------------------------------ helpers (tested)

    /** Which fields are wrong and the message to show under the form (null if all fine). */
    record FormCheck(boolean nameBad, boolean qtyBad, String message) {
    }

    static FormCheck checkForm(String name, String qty) {
        boolean nameBad = name == null || name.isBlank();
        boolean qtyBad;
        try {
            InputParser.quantity(qty);
            qtyBad = false;
        } catch (IllegalArgumentException e) {
            qtyBad = true;
        }
        String msg = null;
        if (nameBad && qtyBad) {
            msg = "Enter an item name and a quantity greater than 0.";
        } else if (nameBad) {
            msg = "Enter an item name.";
        } else if (qtyBad) {
            msg = "Enter a quantity greater than 0, like 2 or 1.5.";
        }
        return new FormCheck(nameBad, qtyBad, msg);
    }

    /** {"In pantry: 1 L", "Adding: 1 L", "Total: 2 L"}. */
    static String[] mergeSummary(PantryItem existing, PantryItem adding) {
        double total = existing.getQuantity() + adding.getQuantity();
        return new String[]{
                "In pantry: " + amount(existing.getQuantity(), existing.getUnit()),
                "Adding: " + amount(adding.getQuantity(), adding.getUnit()),
                "Total: " + amount(total, existing.getUnit())};
    }

    /** A note when the units differ, since quantities are added as plain numbers. */
    static String unitWarning(PantryItem existing, PantryItem adding) {
        String a = existing.getUnit().trim();
        String b = adding.getUnit().trim();
        if (a.equalsIgnoreCase(b) || b.isEmpty()) {
            return null;
        }
        return "Units differ (" + (a.isEmpty() ? "none" : a) + " vs " + b + "). The amounts are added as numbers, in "
                + (a.isEmpty() ? "no unit" : a) + ".";
    }

    private static String amount(double q, String unit) {
        return (InputParser.formatQuantity(q) + " " + unit).trim();
    }

    static String capitalize(String upper) {
        String lower = upper.toLowerCase(Locale.ROOT);
        return lower.isEmpty() ? lower : Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static boolean flatLafActive() {
        return UIManager.getLookAndFeel() != null && UIManager.getLookAndFeel().getName().startsWith("Flat");
    }

    /** Red outline on a field with an error (FlatLaf style, or a red border otherwise). */
    static void markError(JTextField field, Border normal, boolean error) {
        if (flatLafActive()) {
            field.putClientProperty("JComponent.outline", error ? "error" : null);
        } else {
            field.setBorder(error ? BorderFactory.createLineBorder(Theme.DANGER_FG, 1) : normal);
        }
    }

    // ------------------------------------------------------------ painting

    /** Tints expiring/expired rows and draws the "Days left" chips. */
    private class CellRenderer extends DefaultTableCellRenderer {
        private static final long serialVersionUID = 1L;
        private Color chipBg;
        private Color chipFg;

        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean selected,
                                                       boolean focus, int row, int column) {
            int modelRow = t.convertRowIndexToModel(row);
            int modelCol = t.convertColumnIndexToModel(column);
            Object shown = value;
            if (value instanceof Double) {
                shown = InputParser.formatQuantity((Double) value);
            } else if (value instanceof LocalDate) {
                shown = PantryTableModel.formatDate((LocalDate) value);
            } else if (modelCol == PantryTableModel.DAYS_LEFT_COLUMN) {
                shown = PantryTableModel.daysLabel((Integer) value);
            }
            super.getTableCellRendererComponent(t, shown, selected, false, row, column);
            setHorizontalAlignment(LEFT);
            setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
            setFont(Theme.font(modelCol == 0 ? Font.BOLD : Font.PLAIN, 12f));

            PantryTableModel.Status status = model.getStatusAt(modelRow);
            chipBg = null;
            if (modelCol == PantryTableModel.DAYS_LEFT_COLUMN) {
                if (status == PantryTableModel.Status.USE_SOON) {
                    chipBg = Theme.WARN_BG;
                    chipFg = Theme.WARN_FG;
                } else if (status == PantryTableModel.Status.EXPIRED) {
                    chipBg = Theme.DANGER_BG;
                    chipFg = Theme.DANGER_FG;
                }
            }
            setForeground(chipBg != null ? chipFg : Theme.TEXT);
            if (selected) {
                setBackground(Theme.PRIMARY_LIGHT);
            } else if (status == PantryTableModel.Status.USE_SOON) {
                setBackground(ROW_USE_SOON);
            } else if (status == PantryTableModel.Status.EXPIRED) {
                setBackground(ROW_EXPIRED);
            } else {
                setBackground(Theme.CARD);
            }
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (chipBg == null || getText().isEmpty()) {
                super.paintComponent(g);
                return;
            }
            g.setColor(getBackground());
            g.fillRect(0, 0, getWidth(), getHeight());
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setFont(Theme.font(Font.BOLD, 11f));
            int w = g2.getFontMetrics().stringWidth(getText()) + 16;
            int h = 20;
            int y = (getHeight() - h) / 2;
            g2.setColor(chipBg);
            g2.fillRoundRect(8, y, w, h, 10, 10);
            g2.setColor(chipFg);
            g2.drawString(getText(), 16, y + (h + g2.getFontMetrics().getAscent()) / 2 - 2);
            g2.dispose();
        }
    }

    /** A small coloured square for the legend. */
    private static class Swatch implements javax.swing.Icon {
        private final Color fill;
        private final Color edge;

        Swatch(Color fill, Color edge) {
            this.fill = fill;
            this.edge = edge;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            g.setColor(fill);
            g.fillRect(x, y + 2, 10, 10);
            g.setColor(edge);
            g.drawRect(x, y + 2, 10, 10);
        }

        @Override
        public int getIconWidth() {
            return 12;
        }

        @Override
        public int getIconHeight() {
            return 14;
        }
    }

    /** The empty-state picture: a simple box drawn in code. */
    private static class BoxIcon implements javax.swing.Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Theme.PRIMARY_LIGHT);
            g2.fillRoundRect(x, y, 40, 40, 10, 10);
            g2.setColor(Theme.PRIMARY);
            g2.setStroke(new java.awt.BasicStroke(1.8f));
            g2.drawRect(x + 11, y + 15, 18, 14);
            g2.drawLine(x + 11, y + 15, x + 15, y + 10);
            g2.drawLine(x + 29, y + 15, x + 25, y + 10);
            g2.drawLine(x + 15, y + 10, x + 25, y + 10);
            g2.drawLine(x + 17, y + 20, x + 23, y + 20);
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return 40;
        }

        @Override
        public int getIconHeight() {
            return 40;
        }
    }
}
