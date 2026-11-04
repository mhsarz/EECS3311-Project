package com.recipeagent.gui;

import com.recipeagent.model.Recipe;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

/** Table data for the History tab: saved recipes with rating, favourite and cook record. */
public class HistoryTableModel extends AbstractTableModel {

    private static final long serialVersionUID = 1L;
    private static final String[] COLUMNS =
            {"Recipe", "Rating", "Favourite", "Times cooked", "Last cooked", "Time (min)"};

    private final List<Recipe> rows = new ArrayList<>();
    private boolean fancySymbols = true;

    public void setRecipes(List<Recipe> recipes) {
        rows.clear();
        rows.addAll(recipes);
        fireTableDataChanged();
    }

    /** Use plain text instead of star and heart symbols (for fonts that lack them). */
    public void setFancySymbols(boolean fancy) {
        this.fancySymbols = fancy;
        fireTableDataChanged();
    }

    public Recipe getRecipeAt(int row) {
        return rows.get(row);
    }

    /** "★★★★☆" for 4 stars, "not rated" for 0; plain "4/5" without symbols. */
    static String stars(int rating, boolean fancy) {
        if (rating <= 0) {
            return "not rated";
        }
        if (!fancy) {
            return rating + "/5";
        }
        return "★".repeat(rating) + "☆".repeat(5 - rating);
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
        return column == 3 || column == 5 ? Integer.class : String.class;
    }

    @Override
    public Object getValueAt(int row, int column) {
        Recipe r = rows.get(row);
        switch (column) {
            case 0:
                return r.getTitle();
            case 1:
                return stars(r.getRating(), fancySymbols);
            case 2:
                return r.isFavourite() ? (fancySymbols ? "♥" : "yes") : "";
            case 3:
                return r.getTimesCooked();
            case 4:
                return r.getLastCooked() == null ? "never" : r.getLastCooked().toString();
            case 5:
                return r.getCookTimeMinutes();
            default:
                return "";
        }
    }
}
