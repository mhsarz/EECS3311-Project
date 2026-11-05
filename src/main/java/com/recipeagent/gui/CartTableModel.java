package com.recipeagent.gui;

import com.recipeagent.model.CartItem;
import com.recipeagent.model.CartSource;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

/** Table data for the Cart tab: one row per item to buy (F02). */
public class CartTableModel extends AbstractTableModel {

    private static final long serialVersionUID = 1L;
    static final String[] COLUMNS = {"Item", "Qty", "Unit", "Added from", "On sale"};
    public static final int QTY_COLUMN = 1;
    public static final int SOURCE_COLUMN = 3;
    public static final int SALE_COLUMN = 4;

    private final transient List<CartItem> rows = new ArrayList<>();

    public void setItems(List<CartItem> items) {
        rows.clear();
        if (items != null) {
            rows.addAll(items);
        }
        fireTableDataChanged();
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
                return Boolean.class;
            default:
                return String.class;
        }
    }

    @Override
    public Object getValueAt(int row, int column) {
        CartItem i = rows.get(row);
        switch (column) {
            case 0:
                return i.getName();
            case QTY_COLUMN:
                return i.getQuantity();
            case 2:
                return i.getUnit();
            case SOURCE_COLUMN:
                return i.getSource();
            case SALE_COLUMN:
                return i.isOnSale();
            default:
                return "";
        }
    }
}
