package com.recipeagent.gui;

import com.recipeagent.agent.PantryEmptyException;
import com.recipeagent.controller.AgentController;
import com.recipeagent.llm.LLMException;
import com.recipeagent.llm.LLMUnavailableException;
import com.recipeagent.model.CartItem;
import com.recipeagent.model.Ingredient;
import com.recipeagent.model.IngredientCheck;
import com.recipeagent.model.Recipe;
import com.recipeagent.model.RecipeRequest;
import com.recipeagent.model.Substitution;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;

/**
 * The Recipe tab: generate a recipe with the AI (F01), replace a missing ingredient (F07),
 * check what is missing and add it to the cart (F11), mark the recipe as cooked and undo
 * (F06), and save it to the history (F08).
 * <p>
 * AI calls run in the background, so the window stays usable while the agent is thinking.
 */
public class RecipePanel extends JPanel {

    private static final long serialVersionUID = 1L;
    static final String NO_RECIPE_HINT = "Enter your time limit and portions, then click Generate Recipe.\n\n"
            + "The agent uses what is in your pantry (items that expire soon first), your profile "
            + "(diet, allergies, dislikes) and the recipes you rated highly.";

    private final transient AgentController controller;
    private final transient Runnable dataChanged;

    private final JTextField minutesField = new JTextField("30", 4);
    private final JTextField portionsField = new JTextField(3);
    private final JCheckBox allowMissing = new JCheckBox("Allow ingredients I don't have");
    private final JButton generateButton = Theme.ai("Generate Recipe", true);
    private final JLabel busyLabel = new JLabel(" ");

    private final JLabel titleLabel = new JLabel("No recipe yet");
    private final JLabel metaLabel = new JLabel(" ");
    private final IngredientTableModel ingredients = new IngredientTableModel();
    private final JTable ingredientTable = new JTable(ingredients);
    private final JTextArea stepsArea = new JTextArea(NO_RECIPE_HINT);

    private final JButton substituteButton = Theme.ai("Substitute selected", false);
    private final JButton checkButton = Theme.primary("Cook This");
    private final JButton cookedButton = Theme.secondary("Mark as Cooked");
    private final JButton undoButton = Theme.secondary("Undo");
    private final JButton saveButton = Theme.secondary("Save");

    private transient Recipe recipe;

    /**
     * @param dataChanged called after anything that changes the pantry, cart or history,
     *                    so the other tabs can refresh
     */
    public RecipePanel(AgentController controller, Runnable dataChanged) {
        super(new BorderLayout(8, 8));
        this.controller = controller;
        this.dataChanged = dataChanged;
        setBorder(BorderFactory.createEmptyBorder(16, 20, 12, 16));

        JPanel north = new JPanel(new BorderLayout());
        north.add(new PageHeader("Recipe", "A practical meal, made from what you already have."), BorderLayout.NORTH);
        north.add(buildRequestForm(), BorderLayout.CENTER);
        add(north, BorderLayout.NORTH);
        add(buildRecipeView(), BorderLayout.CENTER);
        add(buildActions(), BorderLayout.SOUTH);

        portionsField.setText(String.valueOf(controller.getProfile().getDefaultPortions()));
        showRecipe(null);
    }

    private JPanel buildRequestForm() {
        JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        form.setBorder(BorderFactory.createTitledBorder("What do you want to cook?"));
        form.add(new JLabel("Max time (minutes)"));
        form.add(minutesField);
        form.add(new JLabel("Portions"));
        form.add(portionsField);
        form.add(allowMissing);
        generateButton.addActionListener(e -> generate(List.of()));
        form.add(generateButton);
        busyLabel.setFont(busyLabel.getFont().deriveFont(Font.ITALIC));
        form.add(busyLabel);
        return form;
    }

    private JPanel buildRecipeView() {
        JPanel view = new JPanel(new BorderLayout(6, 6));
        JPanel header = new JPanel(new BorderLayout());
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 20f));
        header.add(titleLabel, BorderLayout.NORTH);
        header.add(metaLabel, BorderLayout.SOUTH);
        view.add(header, BorderLayout.NORTH);

        ingredientTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        ingredientTable.setRowHeight(24);
        JPanel left = new JPanel(new BorderLayout(4, 4));
        left.setBorder(BorderFactory.createTitledBorder("Ingredients"));
        left.add(new JScrollPane(ingredientTable), BorderLayout.CENTER);
        substituteButton.addActionListener(e -> substitute());
        JPanel subRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        subRow.add(substituteButton);
        left.add(subRow, BorderLayout.SOUTH);

        stepsArea.setEditable(false);
        stepsArea.setLineWrap(true);
        stepsArea.setWrapStyleWord(true);
        stepsArea.setMargin(new java.awt.Insets(8, 8, 8, 8));
        JPanel right = new JPanel(new BorderLayout());
        right.setBorder(BorderFactory.createTitledBorder("Steps"));
        right.add(new JScrollPane(stepsArea), BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        split.setResizeWeight(0.4);
        split.setBorder(null);
        view.add(split, BorderLayout.CENTER);
        return view;
    }

    private JPanel buildActions() {
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        checkButton.addActionListener(e -> checkIngredients());
        cookedButton.addActionListener(e -> markCooked());
        undoButton.addActionListener(e -> undo());
        saveButton.addActionListener(e -> save());
        actions.add(checkButton);
        actions.add(cookedButton);
        actions.add(undoButton);
        actions.add(saveButton);
        return actions;
    }

    // ------------------------------------------------------------ showing a recipe

    /** Shows a recipe (or the empty state for null), e.g. one opened from the History tab. */
    public void showRecipe(Recipe r) {
        this.recipe = r;
        if (r == null) {
            titleLabel.setText("No recipe yet");
            metaLabel.setText(" ");
            ingredients.setIngredients(List.of());
            stepsArea.setText(NO_RECIPE_HINT);
        } else {
            titleLabel.setText(r.getTitle());
            metaLabel.setText(r.getCookTimeMinutes() + " minutes  ·  " + r.getPortions() + " portions"
                    + (r.getTimesCooked() > 0 ? "  ·  cooked " + r.getTimesCooked() + " time(s)" : ""));
            ingredients.setIngredients(r.getIngredients());
            stepsArea.setText(formatSteps(r.getSteps()));
            stepsArea.setCaretPosition(0);
        }
        refresh();
    }

    static String formatSteps(List<String> steps) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < steps.size(); i++) {
            if (i > 0) {
                sb.append("\n\n");
            }
            sb.append(i + 1).append(". ").append(steps.get(i));
        }
        return sb.toString();
    }

    /** Updates which buttons can be used. */
    public void refresh() {
        boolean has = recipe != null;
        substituteButton.setEnabled(has);
        checkButton.setEnabled(has);
        cookedButton.setEnabled(has);
        saveButton.setEnabled(has);
        undoButton.setEnabled(controller.canUndo());
    }

    // ------------------------------------------------------------ F01 generate

    /**
     * Generates a recipe; {@code requiredItems} are ingredients it must use, e.g. items that
     * expire soon (F05 "Suggest Recipes").
     */
    public void generate(List<String> requiredItems) {
        RecipeRequest req;
        try {
            int minutes = InputParser.wholeNumber(minutesField.getText(), "Max time", 1, 600);
            int portions = InputParser.wholeNumber(portionsField.getText(), "Portions", 1, 50);
            req = new RecipeRequest(minutes, portions);
            for (String item : requiredItems) {
                req.addRequiredItem(item);
            }
            req.setAllowMissing(allowMissing.isSelected());
        } catch (IllegalArgumentException ex) {
            warn(ex.getMessage());
            return;
        }
        String busyText = requiredItems.isEmpty() ? "Thinking of a recipe…"
                : "Finding a recipe that uses " + String.join(", ", requiredItems) + "…";
        Background.run(b -> setBusy(b, busyText),
                () -> controller.generateRecipe(req),
                r -> {
                    showRecipe(r);
                    dataChanged.run();
                },
                ex -> generateFailed(ex, requiredItems));
    }

    private void generateFailed(RuntimeException ex, List<String> requiredItems) {
        dataChanged.run();
        if (ex instanceof PantryEmptyException) {
            int choice = JOptionPane.showConfirmDialog(this,
                    "Your pantry is empty.\nAdd items in the Pantry tab first, or generate a recipe "
                            + "that uses ingredients you don't have?",
                    "Pantry is empty", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (choice == JOptionPane.YES_OPTION) {
                allowMissing.setSelected(true);
                generate(requiredItems);
            }
        } else {
            showAiError(ex);
        }
    }

    // ------------------------------------------------------------ F07 substitute

    private void substitute() {
        int row = ingredientTable.getSelectedRow();
        if (recipe == null || row < 0) {
            warn("Select the ingredient you are missing in the Ingredients list first.");
            return;
        }
        Ingredient ing = ingredients.getIngredientAt(row);
        Recipe current = recipe;
        Background.run(b -> setBusy(b, "Looking for substitutes for " + ing.getName() + "…"),
                () -> controller.suggestSubstitutes(current, ing.getName()),
                subs -> {
                    dataChanged.run();
                    showSubstitutes(ing, subs);
                },
                ex -> {
                    dataChanged.run();
                    showAiError(ex);
                });
    }

    private void showSubstitutes(Ingredient ing, List<Substitution> subs) {
        if (subs.isEmpty()) {
            int choice = JOptionPane.showConfirmDialog(this,
                    "No good substitute was found for " + ing.getName() + ".\nAdd it to your cart?",
                    "No substitute", JOptionPane.YES_NO_OPTION);
            if (choice == JOptionPane.YES_OPTION) {
                addToCart(List.of(ing));
            }
            return;
        }
        JOptionPane.showMessageDialog(this, formatSubstitutes(ing.getName(), subs),
                "Substitutes for " + ing.getName(), JOptionPane.INFORMATION_MESSAGE);
    }

    static String formatSubstitutes(String ingredient, List<Substitution> subs) {
        StringBuilder sb = new StringBuilder("Instead of " + ingredient + " you can use:\n");
        int n = 1;
        for (Substitution s : subs) {
            sb.append("\n").append(n++).append(". ").append(s.getSubstitute());
            if (s.isInStock()) {
                sb.append("  (in your pantry)");
            }
            if (!s.getNote().isEmpty()) {
                sb.append("\n    ").append(s.getNote());
            }
        }
        return sb.toString();
    }

    // ------------------------------------------------------------ F11 check

    private void checkIngredients() {
        if (recipe == null) {
            return;
        }
        IngredientCheck check = controller.checkIngredients(recipe);
        if (check.allInStock()) {
            int choice = JOptionPane.showConfirmDialog(this,
                    "You have everything for this recipe.\nMark it as cooked now?",
                    "All in stock", JOptionPane.YES_NO_OPTION);
            if (choice == JOptionPane.YES_OPTION) {
                markCooked();
            }
            return;
        }
        int choice = JOptionPane.showOptionDialog(this, formatMissing(check.getMissing()),
                "Missing ingredients", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE,
                null, new String[]{"Add Missing to Cart", "Close"}, "Add Missing to Cart");
        if (choice == 0) {
            addToCart(check.getMissing());
        }
    }

    static String formatMissing(List<Ingredient> missing) {
        StringBuilder sb = new StringBuilder("You are missing:\n");
        for (Ingredient i : missing) {
            sb.append("\n•  ").append(i.getName()).append(" — ").append(IngredientTableModel.amount(i));
        }
        return sb.toString();
    }

    private void addToCart(List<Ingredient> items) {
        List<String> added = new ArrayList<>();
        for (Ingredient i : items) {
            double qty = i.getQuantity() > 0 ? i.getQuantity() : 1;
            controller.addToCart(new CartItem(i.getName(), qty));
            added.add(i.getName());
        }
        dataChanged.run();
        JOptionPane.showMessageDialog(this, "Added to your cart: " + String.join(", ", added) + ".");
    }

    // ------------------------------------------------------------ F06 cook + undo

    private void markCooked() {
        if (recipe == null) {
            return;
        }
        List<String> skipped;
        try {
            skipped = controller.markCooked(recipe);
        } catch (IllegalStateException ex) {
            warn(ex.getMessage());
            return;
        }
        showRecipe(recipe);
        dataChanged.run();
        String msg = "Enjoy your meal! The ingredients were taken out of your pantry.";
        if (!skipped.isEmpty()) {
            msg += "\nNot in your pantry, so skipped: " + String.join(", ", skipped) + ".";
        }
        JOptionPane.showMessageDialog(this, msg + "\n\nClick Undo if this was a mistake.",
                "Marked as cooked", JOptionPane.INFORMATION_MESSAGE);
    }

    private void undo() {
        String msg = controller.undo().map(d -> "Undone: " + d + ". Your pantry is back as it was.")
                .orElse("There is nothing to undo.");
        if (recipe != null) {
            showRecipe(recipe);
        }
        dataChanged.run();
        JOptionPane.showMessageDialog(this, msg, "Undo", JOptionPane.INFORMATION_MESSAGE);
    }

    // ------------------------------------------------------------ F08 save

    private void save() {
        if (recipe == null) {
            return;
        }
        boolean saved = controller.saveRecipe(recipe);
        dataChanged.run();
        JOptionPane.showMessageDialog(this, saved
                ? "Saved to your History. You can rate it there."
                : "This recipe is already in your History.");
    }

    // ------------------------------------------------------------ helpers

    private void setBusy(boolean busy, String text) {
        busyLabel.setText(busy ? text : " ");
        generateButton.setEnabled(!busy);
        substituteButton.setEnabled(!busy && recipe != null);
    }

    private void showAiError(RuntimeException ex) {
        String title = ex instanceof LLMUnavailableException ? "AI unavailable" : "AI problem";
        String msg = ex instanceof LLMException ? ex.getMessage()
                : "Something went wrong: " + ex.getMessage();
        JOptionPane.showMessageDialog(this, msg, title, JOptionPane.WARNING_MESSAGE);
    }

    private void warn(String message) {
        JOptionPane.showMessageDialog(this, message, "Please check", JOptionPane.WARNING_MESSAGE);
    }

    Recipe getRecipe() {
        return recipe;
    }
}
