package com.recipeagent.gui;

import com.recipeagent.controller.AgentController;
import com.recipeagent.model.PantryItem;

import javax.swing.table.AbstractTableModel;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Table data for the Pantry tab: one row per pantry item. The "Best before" column holds
 * dates and "Days left" holds numbers, so sorting works; the panel's renderer shows them as
 * "Nov 4, 2026" and as status chips such as "1 day · Use soon" or "Expired · 2 days".
 */
public class PantryTableModel extends AbstractTableModel {

    private static final long serialVersionUID = 1L;
    static final String[] COLUMNS = {"Item", "Qty", "Unit", "Location", "Best before", "Days left"};
    public static final int QTY_COLUMN = 1;
    public static final int BEST_BEFORE_COLUMN = 4;
    public static final int DAYS_LEFT_COLUMN = 5;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);

    /** How close an item is to its best-before date. */
    public enum Status { FRESH, USE_SOON, EXPIRED, NO_DATE }

    private final transient List<PantryItem> rows = new ArrayList<>();
    private transient LocalDate today = LocalDate.now();

    /** Replaces the rows; {@code today} is used for the "Days left" column. */
    public void setItems(List<PantryItem> items, LocalDate today) {
        rows.clear();
        rows.addAll(items);
        this.today = today;
        fireTableDataChanged();
    }

    public PantryItem getItemAt(int row) {
        return rows.get(row);
    }

    /** Status of the item in this row (model index). */
    public Status getStatusAt(int row) {
        PantryItem i = rows.get(row);
        return i.hasBestBefore() ? status(i.daysUntilExpiry(today)) : Status.NO_DATE;
    }

    static Status status(int daysLeft) {
        if (daysLeft < 0) {
            return Status.EXPIRED;
        }
        return daysLeft <= AgentController.EXPIRY_DAYS ? Status.USE_SOON : Status.FRESH;
    }

    /** "Today · Use soon", "1 day · Use soon", "3 days · Use soon", "Expired · 2 days", "7 days". */
    static String daysLabel(Integer daysLeft) {
        if (daysLeft == null) {
            return "";
        }
        int d = daysLeft;
        if (d < 0) {
            return "Expired · " + plural(-d);
        }
        String days = d == 0 ? "Today" : plural(d);
        return status(d) == Status.USE_SOON ? days + " · Use soon" : days;
    }

    private static String plural(int d) {
        return d + (d == 1 ? " day" : " days");
    }

    /** "Nov 4, 2026", or "" for no date. */
    static String formatDate(LocalDate d) {
        return d == null ? "" : d.format(DATE);
    }

    static String location(PantryItem i) {
        String loc = i.getLocation().name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(loc.charAt(0)) + loc.substring(1);
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
            case BEST_BEFORE_COLUMN:
                return LocalDate.class;
            case DAYS_LEFT_COLUMN:
                return Integer.class;
            default:
                return String.class;
        }
    }

    @Override
    public Object getValueAt(int row, int column) {
        PantryItem i = rows.get(row);
        switch (column) {
            case 0:
                return i.getName();
            case QTY_COLUMN:
                return i.getQuantity();
            case 2:
                return i.getUnit();
            case 3:
                return location(i);
            case BEST_BEFORE_COLUMN:
                return i.getBestBefore();
            case DAYS_LEFT_COLUMN:
                return i.hasBestBefore() ? i.daysUntilExpiry(today) : null;
            default:
                return "";
        }
    }
}
