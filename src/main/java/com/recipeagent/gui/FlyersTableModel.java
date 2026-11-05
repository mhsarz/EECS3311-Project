package com.recipeagent.gui;

import com.recipeagent.model.CartItem;
import com.recipeagent.model.Flyer;
import com.recipeagent.model.FlyerItem;

import javax.swing.table.AbstractTableModel;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Table data for the Flyers tab (F10): one row per sale item, with its store, price, the
 * last day of the sale, and the cart item it matched (F02), if any.
 */
public class FlyersTableModel extends AbstractTableModel {

    private static final long serialVersionUID = 1L;
    static final String[] COLUMNS = {"Store", "Item", "Price", "Ends", "In your cart"};
    public static final int PRICE_COLUMN = 2;
    public static final int ENDS_COLUMN = 3;
    public static final int CART_COLUMN = 4;

    /** One sale item and where it is from. */
    record Row(String store, FlyerItem item, LocalDate ends, String cartItem) {
    }

    private final transient List<Row> rows = new ArrayList<>();

    /** Shows the items of these flyers that match the store filter and the search text. */
    public void setFlyers(List<Flyer> flyers, List<CartItem> cart, String store, String search) {
        rows.clear();
        rows.addAll(rows(flyers, cart, store, search));
        fireTableDataChanged();
    }

    Row getRow(int row) {
        return rows.get(row);
    }

    /**
     * The rows to show. {@code store} null or blank means every store; {@code search} keeps
     * items whose name contains it (ignoring case).
     */
    static List<Row> rows(List<Flyer> flyers, List<CartItem> cart, String store, String search) {
        String word = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        List<Row> result = new ArrayList<>();
        for (Flyer f : flyers) {
            if (store != null && !store.isBlank() && !f.getStore().equalsIgnoreCase(store)) {
                continue;
            }
            for (FlyerItem i : f.getItems()) {
                if (!word.isEmpty() && !i.getName().toLowerCase(Locale.ROOT).contains(word)) {
                    continue;
                }
                result.add(new Row(f.getStore(), i, f.getEndDate(), cartItemFor(cart, f.getStore(), i)));
            }
        }
        return result;
    }

    /** The cart item marked on sale as this flyer item, or null. */
    static String cartItemFor(List<CartItem> cart, String store, FlyerItem item) {
        if (cart == null) {
            return null;
        }
        for (CartItem c : cart) {
            if (c.isOnSale() && store.equals(c.getSaleStore()) && item.getName().equals(c.getSaleItemName())) {
                return c.getName();
            }
        }
        return null;
    }

    /** "48 sale items from 2 stores", "No flyers yet". */
    static String summary(List<Flyer> flyers) {
        if (flyers.isEmpty()) {
            return "No flyers yet";
        }
        int items = flyers.stream().mapToInt(f -> f.getItems().size()).sum();
        return items + (items == 1 ? " sale item" : " sale items") + " from " + flyers.size()
                + (flyers.size() == 1 ? " store" : " stores");
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
            case PRICE_COLUMN:
                return Double.class;
            case ENDS_COLUMN:
                return LocalDate.class;
            default:
                return String.class;
        }
    }

    @Override
    public Object getValueAt(int row, int column) {
        Row r = rows.get(row);
        switch (column) {
            case 0:
                return r.store();
            case 1:
                return r.item().getName();
            case PRICE_COLUMN:
                return r.item().getSalePrice();
            case ENDS_COLUMN:
                return r.ends();
            case CART_COLUMN:
                return r.cartItem() == null ? "" : r.cartItem();
            default:
                return "";
        }
    }
}
