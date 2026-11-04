package com.recipeagent.gui;

import com.recipeagent.controller.AgentController;
import com.recipeagent.model.Recipe;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.awt.GridBagLayout;
import java.util.function.Consumer;

/**
 * The History tab (F08): saved recipes with 1-5 star ratings, favourites, how often they
 * were cooked, and a search box. The top-rated recipes are passed to the agent when a new
 * recipe is generated (F01).
 */
public class HistoryPanel extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final String TABLE = "table";
    private static final String EMPTY = "empty";

    private final transient AgentController controller;
    private final transient Runnable dataChanged;
    private final transient Consumer<Recipe> openRecipe;

    private final HistoryTableModel model = new HistoryTableModel();
    private final JTable table = new JTable(model);
    private final JTextField searchField = new JTextField(20);
    private final JLabel countLabel = new JLabel();
    private final JLabel emptyLabel = new JLabel("", SwingConstants.CENTER);
    private final CardLayout cards = new CardLayout();
    private final JPanel center = new JPanel(cards);
    private final JComboBox<Integer> starsBox = new JComboBox<>(new Integer[]{5, 4, 3, 2, 1});

    /**
     * @param openRecipe   shows a recipe in the Recipe tab (and switches to it)
     * @param goToGenerate switches to the Recipe tab to generate a new recipe
     */
    public HistoryPanel(AgentController controller, Runnable dataChanged,
                        Consumer<Recipe> openRecipe, Runnable goToGenerate) {
        super(new BorderLayout(8, 8));
        this.controller = controller;
        this.dataChanged = dataChanged;
        this.openRecipe = openRecipe;
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        top.add(new JLabel("Search by name or ingredient:"));
        top.add(searchField);
        top.add(countLabel);
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                refresh();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                refresh();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                refresh();
            }
        });
        add(top, BorderLayout.NORTH);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(24);
        table.setRowSorter(new TableRowSorter<>(model));
        model.setFancySymbols(table.getFont().canDisplay('★') && table.getFont().canDisplay('♥'));
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openSelected();
                }
            }
        });
        center.add(new JScrollPane(table), TABLE);
        JPanel empty = new JPanel(new GridBagLayout());
        JPanel emptyBox = new JPanel(new BorderLayout(6, 10));
        emptyBox.add(emptyLabel, BorderLayout.CENTER);
        JButton generate = new JButton("Generate Recipe");
        generate.addActionListener(e -> goToGenerate.run());
        JPanel btnRow = new JPanel();
        btnRow.add(generate);
        emptyBox.add(btnRow, BorderLayout.SOUTH);
        empty.add(emptyBox);
        center.add(empty, EMPTY);
        add(center, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.add(new JLabel("Stars:"));
        actions.add(starsBox);
        JButton rate = new JButton("Rate");
        rate.addActionListener(e -> rateSelected());
        actions.add(rate);
        JButton fav = new JButton("Favourite on/off");
        fav.addActionListener(e -> toggleFavouriteSelected());
        actions.add(fav);
        JButton open = new JButton("Open in Recipe tab");
        open.addActionListener(e -> openSelected());
        actions.add(open);
        add(actions, BorderLayout.SOUTH);

        refresh();
    }

    /** Reloads the list using the current search text. */
    public void refresh() {
        String query = searchField.getText();
        java.util.List<Recipe> found = controller.searchHistory(query);
        model.setRecipes(found);
        countLabel.setText(found.size() + (found.size() == 1 ? " recipe" : " recipes"));
        if (found.isEmpty()) {
            emptyLabel.setText(query.isBlank()
                    ? "No saved recipes yet. Generate one and click Save to History."
                    : "No recipes match \"" + query.trim() + "\".");
            cards.show(center, EMPTY);
        } else {
            cards.show(center, TABLE);
        }
    }

    private Recipe selected() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            JOptionPane.showMessageDialog(this, "Select a recipe in the list first.",
                    "Please check", JOptionPane.WARNING_MESSAGE);
            return null;
        }
        return model.getRecipeAt(table.convertRowIndexToModel(viewRow));
    }

    private void rateSelected() {
        Recipe r = selected();
        if (r == null) {
            return;
        }
        controller.rateRecipe(r.getId(), (Integer) starsBox.getSelectedItem());
        dataChanged.run();
        reselect(r);
    }

    private void toggleFavouriteSelected() {
        Recipe r = selected();
        if (r == null) {
            return;
        }
        controller.toggleFavourite(r.getId());
        dataChanged.run();
        reselect(r);
    }

    private void openSelected() {
        Recipe r = selected();
        if (r != null) {
            openRecipe.accept(r);
        }
    }

    /** Keeps the same recipe selected after the table reloads. */
    private void reselect(Recipe r) {
        for (int i = 0; i < model.getRowCount(); i++) {
            if (model.getRecipeAt(i).getId().equals(r.getId())) {
                int view = table.convertRowIndexToView(i);
                table.setRowSelectionInterval(view, view);
                return;
            }
        }
    }
}
