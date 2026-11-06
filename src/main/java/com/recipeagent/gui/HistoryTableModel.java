package com.recipeagent.gui;

import com.recipeagent.model.Recipe;

import javax.swing.table.AbstractTableModel;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Table data for the History tab (F08): favourite, recipe, rating, times cooked, last cooked
 * and cooking time. The columns hold booleans, numbers and dates, so clicking a header sorts
 * them properly; the panel draws the heart and the stars.
 */
public class HistoryTableModel extends AbstractTableModel implements CheckColumn.Model {

    private static final long serialVersionUID = 1L;
    static final String[] COLUMNS = {"", "", "Recipe", "Rating", "Cooked", "Last cooked", "Time"};
    public static final int CHECK_COLUMN = CheckColumn.COLUMN;
    public static final int FAV_COLUMN = 1;
    public static final int TITLE_COLUMN = 2;
    public static final int RATING_COLUMN = 3;
    public static final int COOKED_COLUMN = 4;
    public static final int LAST_COOKED_COLUMN = 5;
    public static final int TIME_COLUMN = 6;

    private final transient List<Recipe> rows = new ArrayList<>();
    /** Checked recipes, by id. Recipes hidden by the search or the favourites filter lose their check. */
    private final transient CheckColumn.Keys<String> checked = new CheckColumn.Keys<>();

    /** Shows the recipes, most recently cooked first (see {@link #recentFirst}). */
    public void setRecipes(List<Recipe> recipes) {
        rows.clear();
        rows.addAll(recentFirst(recipes == null ? List.of() : recipes));
        checked.retain(rows.stream().map(Recipe::getId).toList());
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
        for (Recipe r : rows) {
            checked.set(r.getId(), on);
        }
        if (!rows.isEmpty()) {
            fireTableRowsUpdated(0, rows.size() - 1);       // keeps the selected row
        }
    }

    /** The checked recipes, in table order. */
    public List<Recipe> getCheckedRecipes() {
        return rows.stream().filter(r -> checked.contains(r.getId())).toList();
    }

    public Recipe getRecipeAt(int row) {
        return rows.get(row);
    }

    /**
     * Most recently cooked first; recipes never cooked come after them, newest saved first
     * (the history keeps recipes in the order they were saved).
     */
    static List<Recipe> recentFirst(List<Recipe> recipes) {
        List<Recipe> cooked = new ArrayList<>();
        List<Recipe> never = new ArrayList<>();
        for (Recipe r : recipes) {
            (r.getLastCooked() == null ? never : cooked).add(r);
        }
        cooked.sort(Comparator.comparing(Recipe::getLastCooked).reversed());
        List<Recipe> result = new ArrayList<>(cooked);
        for (int i = never.size() - 1; i >= 0; i--) {
            result.add(never.get(i));
        }
        return result;
    }

    /** "6 recipes · 2 favourites · cooked 9 times · average rating 4.3" for the footer. */
    static String stats(List<Recipe> all) {
        int n = all.size();
        long favs = all.stream().filter(Recipe::isFavourite).count();
        int cooks = all.stream().mapToInt(Recipe::getTimesCooked).sum();
        StringBuilder sb = new StringBuilder()
                .append(n).append(n == 1 ? " recipe" : " recipes")
                .append(" · ").append(favs).append(favs == 1 ? " favourite" : " favourites")
                .append(" · cooked ").append(cooks).append(cooks == 1 ? " time" : " times");
        double avg = all.stream().filter(Recipe::isRated).mapToInt(Recipe::getRating).average().orElse(0);
        if (avg > 0) {
            sb.append(" · average rating ").append(String.format(Locale.ROOT, "%.1f", avg));
        }
        return sb.toString();
    }

    /** "Nov 4, 2026", or "Never". */
    static String lastCooked(LocalDate d) {
        return d == null ? "Never" : PantryTableModel.formatDate(d);
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
            case CHECK_COLUMN:
            case FAV_COLUMN:
                return Boolean.class;
            case RATING_COLUMN:
            case COOKED_COLUMN:
            case TIME_COLUMN:
                return Integer.class;
            case LAST_COOKED_COLUMN:
                return LocalDate.class;
            default:
                return String.class;
        }
    }

    @Override
    public Object getValueAt(int row, int column) {
        Recipe r = rows.get(row);
        switch (column) {
            case CHECK_COLUMN:
                return checked.contains(r.getId());
            case FAV_COLUMN:
                return r.isFavourite();
            case TITLE_COLUMN:
                return r.getTitle();
            case RATING_COLUMN:
                return r.getRating();
            case COOKED_COLUMN:
                return r.getTimesCooked();
            case LAST_COOKED_COLUMN:
                return r.getLastCooked();
            case TIME_COLUMN:
                return r.getCookTimeMinutes();
            default:
                return "";
        }
    }
}
