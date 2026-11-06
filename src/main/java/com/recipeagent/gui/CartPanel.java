package com.recipeagent.gui;

import com.recipeagent.controller.AgentController;
import com.recipeagent.model.CartItem;
import com.recipeagent.model.CartSource;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * The Cart page (F02): the shopping list, with an "Add to cart" form, Edit, Remove selected
 * and Clear cart. Each item shows where it came from ("You", "Recipe" when added from a
 * recipe's missing ingredients, "Chat" when the agent added it) and, once this week's flyers
 * are loaded, whether it is on sale.
 */
public class CartPanel extends JPanel {

    private static final long serialVersionUID = 1L;
    static final String[] UNITS;
    private static final String TABLE = "table";
    private static final String EMPTY = "empty";
    private static final Color OTHER_BG = new Color(0xF0F2EF);

    static {
        UNITS = new String[PantryPanel.UNITS.length + 1];
        UNITS[0] = "";                                   // most shopping items need no unit
        System.arraycopy(PantryPanel.UNITS, 0, UNITS, 1, PantryPanel.UNITS.length);
    }

    private final transient AgentController controller;
    private final transient Runnable dataChanged;
    private final PageHeader header = new PageHeader("Cart", "What to buy next. Items on sale this week are marked.");
    private final CartTableModel model = new CartTableModel();
    private final JTable table = new JTable(model);
    private final CardLayout cards = new CardLayout();
    private final JPanel tableArea = new JPanel(cards);
    private final JButton editButton = Theme.secondary("Edit");
    private final JButton removeButton = Theme.secondary("Remove selected");
    private final JButton clearButton = Theme.secondary("Clear cart");
    private final JButton salesButton = Theme.ai("Check for sales", false);

    private final JTextField nameField = new JTextField(16);
    private final JTextField qtyField = new JTextField("1", 4);
    private final JComboBox<String> unitBox = new JComboBox<>(UNITS);
    private final JLabel message = Theme.label(" ", Font.PLAIN, 12f, Theme.DANGER_FG);
    private final transient Border nameBorder = nameField.getBorder();
    private final transient Border qtyBorder = qtyField.getBorder();
    private boolean checking;

    /** @param dataChanged called after the cart changes, so other pages can refresh */
    public CartPanel(AgentController controller, Runnable dataChanged) {
        super(new BorderLayout(0, 12));
        this.controller = controller;
        this.dataChanged = dataChanged;
        setBorder(BorderFactory.createEmptyBorder(16, 20, 12, 16));
        add(header, BorderLayout.NORTH);
        add(buildList(), BorderLayout.CENTER);
        add(buildForm(), BorderLayout.SOUTH);
        refresh();
    }

    // ------------------------------------------------------------ list

    private JPanel buildList() {
        JPanel card = new JPanel(new BorderLayout(0, 8));
        card.setOpaque(false);

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(Theme.label("Your shopping list", Font.BOLD, 13f, Theme.TEXT), BorderLayout.WEST);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        editButton.setToolTipText("Change the quantity or unit (or double-click the item)");
        editButton.addActionListener(e -> editSelected());
        removeButton.addActionListener(e -> removeSelected());
        clearButton.addActionListener(e -> clearCart());
        salesButton.setToolTipText("Look for every item in this week's flyers (Flyers tab)");
        salesButton.addActionListener(e -> checkSales(cartNames(), true));
        buttons.add(salesButton);
        buttons.add(editButton);
        buttons.add(removeButton);
        buttons.add(clearButton);
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
        table.setDefaultRenderer(CartSource.class, renderer);
        table.setDefaultRenderer(Boolean.class, renderer);
        CheckColumn.install(table, model, this::updateButtons);
        table.getSelectionModel().addListSelectionListener(e -> updateButtons());
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && table.rowAtPoint(e.getPoint()) >= 0
                        && !CheckColumn.isCheckColumn(table, e)) {
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
        return card;
    }

    private JPanel buildEmptyState() {
        JPanel empty = new JPanel(new GridBagLayout());
        empty.setBackground(Theme.CARD);
        empty.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        JPanel box = new JPanel();
        box.setOpaque(false);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        JLabel title = Theme.label("Your cart is empty", Font.BOLD, 15f, Theme.TEXT);
        JLabel text = Theme.muted("<html><div style='text-align:center;width:300px'>Add items below, use "
                + "Cook This on a recipe, or ask the Chat: \"Add a dozen eggs to my cart.\"</div></html>");
        text.setHorizontalAlignment(SwingConstants.CENTER);
        for (JComponent c : new JComponent[]{title, text}) {
            c.setAlignmentX(CENTER_ALIGNMENT);
            box.add(c);
            box.add(Box.createVerticalStrut(8));
        }
        empty.add(box);
        return empty;
    }

    // ------------------------------------------------------------ form

    private JPanel buildForm() {
        JPanel card = new JPanel(new BorderLayout(0, 6));
        card.setBackground(Theme.CARD);
        card.setBorder(Theme.card(12));
        card.add(Theme.label("Add to cart", Font.BOLD, 13f, Theme.TEXT), BorderLayout.NORTH);

        unitBox.setEditable(true);
        unitBox.setSelectedItem("");
        unitBox.setToolTipText("Optional, e.g. L or pack");
        JPanel row = new JPanel(new GridBagLayout());
        row.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 0, 2, 10);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        String[] labels = {"Item", "Qty", "Unit (optional)"};
        JComponent[] fields = {nameField, qtyField, unitBox};
        double[] weights = {1.0, 0, 0};
        for (int i = 0; i < labels.length; i++) {
            c.gridx = i;
            c.gridy = 0;
            c.weightx = weights[i];
            row.add(Theme.muted(labels[i]), c);
            c.gridy = 1;
            row.add(fields[i], c);
        }
        JButton add = Theme.primary("+ Add to cart");
        add.addActionListener(e -> addItem());
        nameField.addActionListener(e -> addItem());
        qtyField.addActionListener(e -> addItem());
        c.gridx = labels.length;
        c.gridy = 1;
        c.weightx = 0;
        c.insets = new Insets(2, 0, 2, 0);
        row.add(add, c);
        card.add(row, BorderLayout.CENTER);
        card.add(message, BorderLayout.SOUTH);
        return card;
    }

    /** Reloads the list from the controller and shows the empty state when there is nothing. */
    public void refresh() {
        List<CartItem> items = controller.getCartItems();
        model.setItems(items);
        header.setRight(CartTableModel.summary(items));
        cards.show(tableArea, items.isEmpty() ? EMPTY : TABLE);
        clearButton.setEnabled(!items.isEmpty());
        salesButton.setEnabled(!items.isEmpty() && !checking);
        updateButtons();
    }

    private void updateButtons() {
        boolean selected = model.getRowCount() > 0 && table.getSelectedRow() >= 0;
        int checked = model.getCheckedCount();
        editButton.setEnabled(selected);
        removeButton.setEnabled(selected || checked > 0);
        removeButton.setText(CheckColumn.removeLabel("Remove selected", checked));
        removeButton.setToolTipText(checked > 0 ? "Remove the checked items"
                : "Remove the selected item (tick the boxes to remove several)");
    }

    // ------------------------------------------------------------ actions

    private void addItem() {
        PantryPanel.FormCheck check = PantryPanel.checkForm(nameField.getText(), qtyField.getText());
        PantryPanel.markError(nameField, nameBorder, check.nameBad());
        PantryPanel.markError(qtyField, qtyBorder, check.qtyBad());
        if (check.message() != null) {
            message.setForeground(Theme.DANGER_FG);
            message.setText(check.message());
            (check.nameBad() ? nameField : qtyField).requestFocusInWindow();
            return;
        }
        String name = nameField.getText().trim();
        double qty = InputParser.quantity(qtyField.getText());
        String unit = String.valueOf(unitBox.getEditor().getItem()).trim();
        boolean wasThere = controller.getCartItems().stream().anyMatch(i -> i.hasName(name));
        CartItem inCart = controller.addToCart(new CartItem(name, qty, unit, CartSource.MANUAL));
        message.setForeground(Theme.PRIMARY);
        message.setText(addedMessage(inCart, wasThere));
        nameField.setText("");
        qtyField.setText("1");
        unitBox.setSelectedItem("");
        nameField.requestFocusInWindow();
        refresh();
        dataChanged.run();
        if (!wasThere) {
            checkSales(List.of(inCart.getName()), false);
        }
    }

    private List<String> cartNames() {
        return controller.getCartItems().stream().map(CartItem::getName).toList();
    }

    /**
     * Looks for items in this week's flyers in the background (F02); sale alerts appear in the
     * Alerts strip. {@code manual} means the user clicked Check for sales, so say what happened.
     */
    private void checkSales(List<String> names, boolean manual) {
        if (names.isEmpty() || checking) {
            return;
        }
        if (!manual && controller.getCurrentFlyers().isEmpty()) {
            return;                                              // nothing to check against
        }
        String before = message.getText();
        Background.run(busy -> {
                    checking = busy;
                    if (busy && manual) {
                        message.setForeground(Theme.MUTED);
                        message.setText("Looking for your items in this week's flyers…");
                    }
                    refresh();
                },
                () -> controller.findSales(names),
                check -> {
                    controller.applySales(check);
                    refresh();
                    dataChanged.run();
                    String text = saleMessage(check, manual);
                    if (text != null) {
                        message.setForeground(check.getMatches().isEmpty() ? Theme.MUTED : Theme.SALE_FG);
                        message.setText(manual || before.isBlank() ? text : before + " " + text);
                    }
                },
                ex -> {
                    message.setForeground(Theme.DANGER_FG);
                    message.setText("Sales could not be checked: " + ex.getMessage());
                });
    }

    /** What to tell the user after a sale check (null to say nothing). */
    static String saleMessage(com.recipeagent.controller.SaleCheck check, boolean manual) {
        if (!check.wasRun()) {
            return manual ? check.getSkippedReason() : null;
        }
        int n = check.getMatches().size();
        if (n == 0) {
            return manual ? "None of your items are on sale this week." : null;
        }
        if (n == 1) {
            com.recipeagent.model.SaleMatch m = check.getMatches().get(0);
            return m.getCartItem() + " is on sale at " + m.getStore() + " for " + m.getFlyerItem().formattedPrice() + "!";
        }
        return n + " items are on sale this week. See the On sale column.";
    }

    /** "Added milk." or "milk was already in your cart; it now has 3 L." */
    static String addedMessage(CartItem inCart, boolean merged) {
        if (!merged) {
            return "Added " + inCart.getName() + ".";
        }
        return inCart.getName() + " was already in your cart; it now has " + CartTableModel.amount(inCart) + ".";
    }

    private CartItem selected() {
        int viewRow = table.getSelectedRow();
        return viewRow < 0 ? null : model.getItemAt(table.convertRowIndexToModel(viewRow));
    }

    private void editSelected() {
        CartItem item = selected();
        if (item == null) {
            return;
        }
        if (new EditDialog(item).ask()) {
            refresh();
            dataChanged.run();
            reselect(item.getName());
        }
    }

    private void reselect(String name) {
        for (int r = 0; r < model.getRowCount(); r++) {
            if (model.getItemAt(r).hasName(name)) {
                int view = table.convertRowIndexToView(r);
                table.getSelectionModel().setSelectionInterval(view, view);
                return;
            }
        }
    }

    private void removeSelected() {
        List<CartItem> checked = model.getCheckedItems();
        if (!checked.isEmpty()) {
            List<String> names = checked.stream().map(CartItem::getName).toList();
            int ok = JOptionPane.showConfirmDialog(this, CheckColumn.removeQuestion(names, "item", "your cart"),
                    "Remove items", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (ok == JOptionPane.OK_OPTION) {
                int n = controller.removeCartItems(names);
                message.setForeground(Theme.PRIMARY);
                message.setText("Removed " + n + (n == 1 ? " item." : " items."));
                refresh();
                dataChanged.run();
            }
            return;
        }
        CartItem item = selected();
        if (item == null) {
            return;
        }
        int ok = JOptionPane.showConfirmDialog(this, removeQuestion(item), "Remove item",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) {
            return;
        }
        controller.removeFromCart(item.getName());
        message.setForeground(Theme.PRIMARY);
        message.setText("Removed " + item.getName() + ".");
        refresh();
        dataChanged.run();
    }

    /** "Are you sure you want to remove milk (2 L) from your cart?" */
    static String removeQuestion(CartItem item) {
        return "Are you sure you want to remove " + item.getName() + " (" + CartTableModel.amount(item)
                + ") from your cart?";
    }

    private void clearCart() {
        int n = model.getRowCount();
        int ok = JOptionPane.showConfirmDialog(this, "Remove all " + n + (n == 1 ? " item" : " items")
                + " from your cart?", "Clear cart", JOptionPane.OK_CANCEL_OPTION);
        if (ok == JOptionPane.OK_OPTION) {
            controller.clearCart();
            message.setForeground(Theme.PRIMARY);
            message.setText("Your cart is empty.");
            refresh();
            dataChanged.run();
        }
    }

    /** Edit the quantity and unit of one cart item. */
    private final class EditDialog extends JDialog {
        private static final long serialVersionUID = 1L;
        private final JTextField qty = new JTextField(5);
        private final JComboBox<String> unit = new JComboBox<>(UNITS);
        private final JLabel error = Theme.label(" ", Font.PLAIN, 12f, Theme.DANGER_FG);
        private final transient Border qtyNormal = qty.getBorder();
        private boolean saved;

        EditDialog(CartItem item) {
            super(SwingUtilities.getWindowAncestor(CartPanel.this), "Edit cart item",
                    java.awt.Dialog.ModalityType.APPLICATION_MODAL);
            qty.setText(InputParser.formatQuantity(item.getQuantity()));
            unit.setEditable(true);
            unit.setSelectedItem(item.getUnit());

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
            c.gridy = 0;
            grid.add(Theme.muted("Qty"), c);
            c.gridx = 1;
            c.weightx = 1;
            grid.add(qty, c);
            c.gridy = 1;
            c.gridx = 0;
            c.weightx = 0;
            grid.add(Theme.muted("Unit"), c);
            c.gridx = 1;
            c.weightx = 1;
            grid.add(unit, c);
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
            save.addActionListener(e -> {
                PantryPanel.FormCheck check = PantryPanel.checkForm(item.getName(), qty.getText());
                PantryPanel.markError(qty, qtyNormal, check.qtyBad());
                if (check.message() != null) {
                    error.setText(check.message());
                    return;
                }
                controller.updateCartItem(item.getName(), InputParser.quantity(qty.getText()),
                        String.valueOf(unit.getEditor().getItem()).trim());
                saved = true;
                dispose();
            });
            buttons.add(cancel);
            buttons.add(save);
            body.add(buttons, BorderLayout.SOUTH);
            setContentPane(body);
            getRootPane().setDefaultButton(save);
            pack();
            setMinimumSize(new Dimension(340, getHeight()));
            setLocationRelativeTo(CartPanel.this);
        }

        /** Shows the dialog; true if the item was changed. */
        boolean ask() {
            setVisible(true);
            return saved;
        }
    }

    // ------------------------------------------------------------ painting

    /** Bold item names, plain quantities, and chips for "Chat" / "Recipe" and "On sale". */
    private class CellRenderer extends DefaultTableCellRenderer {
        private static final long serialVersionUID = 1L;
        private Color chipBg;
        private Color chipFg;

        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean selected,
                                                       boolean focus, int row, int column) {
            int modelCol = t.convertColumnIndexToModel(column);
            Object shown = value;
            chipBg = null;
            if (value instanceof Double) {
                shown = InputParser.formatQuantity((Double) value);
            } else if (value instanceof CartSource) {
                CartSource s = (CartSource) value;
                shown = s.getLabel();
                if (s == CartSource.CHAT) {
                    chipBg = Theme.PRIMARY_LIGHT;
                    chipFg = Theme.PRIMARY;
                } else if (s == CartSource.RECIPE) {
                    chipBg = OTHER_BG;
                    chipFg = Theme.TEXT;
                }
            } else if (modelCol == CartTableModel.SALE_COLUMN && value != null && !value.toString().isEmpty()) {
                chipBg = Theme.PRIMARY_LIGHT;
                chipFg = Theme.SALE_FG;
            }
            super.getTableCellRendererComponent(t, shown, selected, false, row, column);
            if (modelCol == CartTableModel.SALE_COLUMN) {
                CartItem item = model.getItemAt(t.convertRowIndexToModel(row));
                setToolTipText(item.getSaleItemName() == null ? null : "In the flyer as " + item.getSaleItemName());
            } else {
                setToolTipText(null);
            }
            setHorizontalAlignment(LEFT);
            setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
            setFont(Theme.font(modelCol == CartTableModel.NAME_COLUMN ? Font.BOLD : Font.PLAIN, 12f));
            setForeground(modelCol == CartTableModel.SOURCE_COLUMN && chipBg == null ? Theme.MUTED : Theme.TEXT);
            setBackground(selected ? Theme.PRIMARY_LIGHT : Theme.CARD);
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
}
