package com.recipeagent.gui;

import com.recipeagent.model.CartItem;
import com.recipeagent.model.CartSource;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

/** Table data for the Cart tab: one row per item to buy (F02). */
public class CartTableModel extends AbstractTableModel implements CheckColumn.Model {

    private static final long serialVersionUID = 1L;
    static final String[] COLUMNS = {"", "Item", "Qty", "Unit", "Added from", "On sale"};
    public static final int CHECK_COLUMN = CheckColumn.COLUMN;
    public static final int NAME_COLUMN = 1;
    public static final int QTY_COLUMN = 2;
    public static final int UNIT_COLUMN = 3;
    public static final int SOURCE_COLUMN = 4;
    public static final int SALE_COLUMN = 5;

    private final transient List<CartItem> rows = new ArrayList<>();
    /** Checked items, by lower-case name (cart names are unique ignoring case). */
    private final transient CheckColumn.Keys<String> checked = new CheckColumn.Keys<>();

    /** Replaces the rows; checks are kept for items that are still there. */
    public void setItems(List<CartItem> items) {
        rows.clear();
        if (items != null) {
            rows.addAll(items);
        }
        checked.retain(rows.stream().map(CartTableModel::key).toList());
        fireTableDataChanged();
    }

    private static String key(CartItem i) {
        return i.getName().toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    public boolean isRowChecked(int row) {
        return checked.contains(key(rows.get(row)));
    }

    @Override
    public void setRowChecked(int row, boolean on) {
        checked.set(key(rows.get(row)), on);
        fireTableCellUpdated(row, CHECK_COLUMN);
    }

    @Override
    public int getCheckedCount() {
        return checked.size();
    }

    @Override
    public void setAllChecked(boolean on) {
        for (CartItem i : rows) {
            checked.set(key(i), on);
        }
        if (!rows.isEmpty()) {
            fireTableRowsUpdated(0, rows.size() - 1);       // keeps the selected row
        }
    }

    /** The checked items, in table order. */
    public List<CartItem> getCheckedItems() {
        return rows.stream().filter(i -> checked.contains(key(i))).toList();
    }

    public CartItem getItemAt(int row) {
        return rows.get(row);
    }

    /** "4 items · 1 on sale", or "1 item". */
    static String summary(List<CartItem> items) {
        int n = items.size();
        long sale = items.stream().filter(CartItem::isOnSale).count();
        String text = n + (n == 1 ? " item" : " items");
        return sale > 0 ? text + " · " + sale + " on sale" : text;
    }

    /** "No Frills · $5.49", "On sale" (store unknown) or "" when not on sale. */
    static String saleLabel(CartItem i) {
        if (!i.isOnSale()) {
            return "";
        }
        if (i.getSaleStore() == null || i.getSalePrice() == null) {
            return "On sale";
        }
        return i.getSaleStore() + " · " + String.format(java.util.Locale.CANADA, "$%.2f", i.getSalePrice());
    }

    /** "3 L", "12", "1.5 kg". */
    static String amount(CartItem i) {
        return (InputParser.formatQuantity(i.getQuantity()) + " " + i.getUnit()).trim();
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    @Override
    public Class<?> getColumnClass(int column) {
        switch (column) {
            case QTY_COLUMN:
                return Double.class;
            case SOURCE_COLUMN:
                return CartSource.class;
            case SALE_COLUMN:
                return String.class;
            case CHECK_COLUMN:
                return Boolean.class;
            default:
                return String.class;
        }
    }

    @Override
    public Object getValueAt(int row, int column) {
        CartItem i = rows.get(row);
        switch (column) {
            case CHECK_COLUMN:
                return checked.contains(key(i));
            case NAME_COLUMN:
                return i.getName();
            case QTY_COLUMN:
                return i.getQuantity();
            case UNIT_COLUMN:
                return i.getUnit();
            case SOURCE_COLUMN:
                return i.getSource();
            case SALE_COLUMN:
                return saleLabel(i);
            default:
                return "";
        }
    }
}
