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
public class PantryTableModel extends AbstractTableModel implements CheckColumn.Model {

    private static final long serialVersionUID = 1L;
    static final String[] COLUMNS = {"", "Item", "Qty", "Unit", "Location", "Best before", "Days left"};
    public static final int CHECK_COLUMN = CheckColumn.COLUMN;
    public static final int NAME_COLUMN = 1;
    public static final int QTY_COLUMN = 2;
    public static final int UNIT_COLUMN = 3;
    public static final int LOCATION_COLUMN = 4;
    public static final int BEST_BEFORE_COLUMN = 5;
    public static final int DAYS_LEFT_COLUMN = 6;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);

    /** How close an item is to its best-before date. */
    public enum Status { FRESH, USE_SOON, EXPIRED, NO_DATE }

    private final transient List<PantryItem> rows = new ArrayList<>();
    /** Checked items, by pantry item id. */
    private final transient CheckColumn.Keys<String> checked = new CheckColumn.Keys<>();
    private transient LocalDate today = LocalDate.now();

    /** Replaces the rows; {@code today} is used for the "Days left" column. Checks are kept. */
    public void setItems(List<PantryItem> items, LocalDate today) {
        rows.clear();
        rows.addAll(items);
        checked.retain(rows.stream().map(PantryItem::getId).toList());
        this.today = today;
        fireTableDataChanged();
    }

    @Override
    public boolean isRowChecked(int row) {
        return checked.contains(rows.get(row).getId());
    }

    @Override
    public void setRowChecked(int row, boolean on) {
        checked.set(rows.get(row).getId(), on);
        fireTableCellUpdated(row, CHECK_COLUMN);
    }

    @Override
    public int getCheckedCount() {
        return checked.size();
    }

    @Override
    public void setAllChecked(boolean on) {
        for (PantryItem i : rows) {
            checked.set(i.getId(), on);
        }
        if (!rows.isEmpty()) {
            fireTableRowsUpdated(0, rows.size() - 1);       // keeps the selected row
        }
    }

    /** The checked items, in table order. */
    public List<PantryItem> getCheckedItems() {
        return rows.stream().filter(i -> checked.contains(i.getId())).toList();
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
            case CHECK_COLUMN:
                return Boolean.class;
            default:
                return String.class;
        }
    }

    @Override
    public Object getValueAt(int row, int column) {
        PantryItem i = rows.get(row);
        switch (column) {
            case CHECK_COLUMN:
                return checked.contains(i.getId());
            case NAME_COLUMN:
                return i.getName();
            case QTY_COLUMN:
                return i.getQuantity();
            case UNIT_COLUMN:
                return i.getUnit();
            case LOCATION_COLUMN:
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
