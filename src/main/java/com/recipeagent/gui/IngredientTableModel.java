package com.recipeagent.gui;

import com.recipeagent.model.Ingredient;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

/** Table data for the ingredients of the recipe shown in the Recipe tab. */
public class IngredientTableModel extends AbstractTableModel {

    private static final long serialVersionUID = 1L;
    private static final String[] COLUMNS = {"Ingredient", "Amount"};

    private final List<Ingredient> rows = new ArrayList<>();

    public void setIngredients(List<Ingredient> ingredients) {
        rows.clear();
        if (ingredients != null) {
            rows.addAll(ingredients);
        }
        fireTableDataChanged();
    }

    public Ingredient getIngredientAt(int row) {
        return rows.get(row);
    }

    /** "1 cup", "2", or "to taste" when the quantity is zero. */
    static String amount(Ingredient i) {
        if (i.getQuantity() == 0) {
            return "to taste";
        }
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
    public Object getValueAt(int row, int column) {
        Ingredient i = rows.get(row);
        return column == 0 ? i.getName() : amount(i);
    }
}
