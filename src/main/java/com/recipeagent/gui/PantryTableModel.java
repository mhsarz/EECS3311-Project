package com.recipeagent.gui;

import com.recipeagent.model.PantryItem;

import javax.swing.table.AbstractTableModel;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Table data for the Pantry tab: one row per pantry item. */
public class PantryTableModel extends AbstractTableModel {

    static final String[] COLUMNS = {"Item", "Quantity", "Unit", "Location", "Best before", "Days left"};
    public static final int DAYS_LEFT_COLUMN = 5;

    private final List<PantryItem> rows = new ArrayList<>();
    private LocalDate today = LocalDate.now();

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
        return column == 1 ? Double.class : column == DAYS_LEFT_COLUMN ? Integer.class : String.class;
    }

    @Override
    public Object getValueAt(int row, int column) {
        PantryItem i = rows.get(row);
        switch (column) {
            case 0:
                return i.getName();
            case 1:
                return i.getQuantity();
            case 2:
                return i.getUnit();
            case 3:
                String loc = i.getLocation().name().toLowerCase(Locale.ROOT);
                return Character.toUpperCase(loc.charAt(0)) + loc.substring(1);
            case 4:
                return i.hasBestBefore() ? i.getBestBefore().toString() : "";
            case DAYS_LEFT_COLUMN:
                return i.hasBestBefore() ? i.daysUntilExpiry(today) : null;
            default:
                return "";
        }
    }
}
