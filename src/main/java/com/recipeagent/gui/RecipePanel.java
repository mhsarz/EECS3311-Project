package com.recipeagent.gui;

import com.recipeagent.agent.PantryEmptyException;
import com.recipeagent.controller.AgentController;
import com.recipeagent.llm.LLMException;
import com.recipeagent.llm.LLMUnavailableException;
import com.recipeagent.model.CartItem;
import com.recipeagent.model.Ingredient;
import com.recipeagent.model.IngredientCheck;
import com.recipeagent.model.IngredientStatus;
import com.recipeagent.model.Recipe;
import com.recipeagent.model.RecipeRequest;
import com.recipeagent.model.Substitution;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The Recipe page, as in the Smart Pantry design:
 * <ul>
 *   <li>a request card (time limit, portions, "allow ingredients I don't have") that asks the
 *       AI for a recipe (F01), or for one that uses the food expiring soon (F05);</li>
 *   <li>a loading state with Cancel, and a message state when the AI is unavailable
 *       (Try Again / Open History) or the pantry is empty;</li>
 *   <li>the recipe card: "Uses N expiring items", "3 in pantry · 2 missing", a status chip for
 *       each ingredient and a Substitute button for the ones you don't have (F07);</li>
 *   <li>Cook This (missing-ingredients dialog, Add Missing to Cart, F11), Mark as Cooked and
 *       Undo (F06), Save to History (F08).</li>
 * </ul>
 * AI calls run in the background, so the window stays usable while the agent is thinking.
 */
public class RecipePanel extends JPanel {

    private static final long serialVersionUID = 1L;
    static final String NO_RECIPE_HINT = "Set your time limit and portions, then click Generate Recipe. "
            + "The agent uses what is in your pantry (food that expires soon first), your profile "
            + "(diet, allergies, dislikes) and the recipes you rated highly.";

    private static final String EMPTY = "empty";
    private static final String LOADING = "loading";
    private static final String MESSAGE = "message";
    private static final String RECIPE = "recipe";
    private static final Color BASIC_BG = new Color(0xF0F2EF);
    private static final int NOTICE_WIDTH = 400;

    private final transient AgentController controller;
    private final transient Runnable dataChanged;
    private final transient Consumer<String> openTab;

    private final JTextField minutesField = new JTextField("30", 4);
    private final JTextField portionsField = new JTextField(3);
    private final JCheckBox allowMissing = new JCheckBox("Allow ingredients I don't have");
    private final JButton generateButton = Theme.ai("Generate Recipe", true);
    private final JLabel requestError = Theme.label(" ", Font.PLAIN, 12f, Theme.DANGER_FG);

    private final CardLayout states = new CardLayout();
    private final JPanel stateArea = new JPanel(states);
    private String shownState = EMPTY;

    private final JLabel loadingTitle = Theme.label("Thinking of a recipe…", Font.BOLD, 15f, Theme.TEXT);
    private final JButton cancelButton = Theme.secondary("Cancel");
    private transient Background.Task running;
    private String returnState = EMPTY;

    private final JLabel messageTitle = Theme.label(" ", Font.BOLD, 15f, Theme.TEXT);
    private final JTextArea messageText = new JTextArea();
    private final JPanel messageButtons = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));

    private final JLabel titleLabel = Theme.label(" ", Font.BOLD, 20f, Theme.TEXT);
    private final JLabel metaLabel = Theme.muted(" ");
    private final Chip expiringChip = new Chip(" ", Theme.WARN_BG, Theme.WARN_FG);
    private final JLabel summaryLabel = Theme.label(" ", Font.PLAIN, 12f, Theme.TEXT);
    private final JPanel ingredientRows = new JPanel(new GridBagLayout());
    private final JTextArea stepsArea = new JTextArea();
    private final JLabel notice = Theme.label(" ", Font.PLAIN, 12f, Theme.PRIMARY);

    private final JButton cookThisButton = Theme.primary("Cook This");
    private final JButton cookedButton = Theme.secondary("Mark as Cooked");
    private final JButton undoButton = Theme.secondary("Undo");
    private final JButton saveButton = Theme.secondary("Save to History");

    private transient Recipe recipe;
    private transient List<String> lastRequired = List.of();
    private boolean aiBusy;

    /**
     * @param dataChanged called after anything that changes the pantry, cart or history,
     *                    so the other tabs can refresh
     * @param openTab     switches to another tab by name (e.g. "History", "Pantry")
     */
    public RecipePanel(AgentController controller, Runnable dataChanged, Consumer<String> openTab) {
        super(new BorderLayout(0, 12));
        this.controller = controller;
        this.dataChanged = dataChanged;
        this.openTab = openTab;
        setBorder(BorderFactory.createEmptyBorder(16, 20, 12, 16));

        JPanel north = new JPanel(new BorderLayout(0, 4));
        north.setOpaque(false);
        north.add(new PageHeader("Recipe", "A practical meal, made from what you already have."), BorderLayout.NORTH);
        north.add(buildRequestCard(), BorderLayout.CENTER);
        add(north, BorderLayout.NORTH);

        stateArea.setOpaque(false);
        stateArea.add(buildEmptyState(), EMPTY);
        stateArea.add(buildLoadingState(), LOADING);
        stateArea.add(buildMessageState(), MESSAGE);
        stateArea.add(buildRecipeCard(), RECIPE);
        add(stateArea, BorderLayout.CENTER);

        portionsField.setText(String.valueOf(controller.getProfile().getDefaultPortions()));
        showRecipe(null);
    }

    // ------------------------------------------------------------ building the page

    private JPanel buildRequestCard() {
        JPanel card = new JPanel(new BorderLayout(0, 6));
        card.setBackground(Theme.CARD);
        card.setBorder(Theme.card(12));
        card.add(Theme.label("What do you want to cook?", Font.BOLD, 13f, Theme.TEXT), BorderLayout.NORTH);

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        row.setOpaque(false);
        row.add(Theme.muted("Max time (minutes)  "));
        row.add(minutesField);
        row.add(Box.createHorizontalStrut(16));
        row.add(Theme.muted("Portions  "));
        row.add(portionsField);
        row.add(Box.createHorizontalStrut(16));
        allowMissing.setOpaque(false);
        allowMissing.setToolTipText("The recipe may need things you have to buy; Cook This adds them to your cart");
        row.add(allowMissing);
        row.add(Box.createHorizontalStrut(16));
        generateButton.addActionListener(e -> generate(List.of()));
        minutesField.addActionListener(e -> generate(List.of()));
        portionsField.addActionListener(e -> generate(List.of()));
        row.add(generateButton);
        card.add(row, BorderLayout.CENTER);
        card.add(requestError, BorderLayout.SOUTH);
        return card;
    }

    private JPanel centredCard(JComponent... parts) {
        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(Theme.CARD);
        card.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        JPanel box = new JPanel();
        box.setOpaque(false);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        for (JComponent c : parts) {
            c.setAlignmentX(CENTER_ALIGNMENT);
            box.add(c);
            box.add(Box.createVerticalStrut(10));
        }
        card.add(box);
        return card;
    }

    private JPanel buildEmptyState() {
        JLabel title = Theme.label("No recipe yet", Font.BOLD, 15f, Theme.TEXT);
        JLabel text = Theme.muted("<html><div style='text-align:center;width:340px'>" + NO_RECIPE_HINT + "</div></html>");
        text.setHorizontalAlignment(SwingConstants.CENTER);
        return centredCard(title, text);
    }

    private JPanel buildLoadingState() {
        JLabel text = Theme.muted("The agent is checking your pantry, profile and favourite recipes.");
        JProgressBar bar = new JProgressBar();
        bar.setIndeterminate(true);
        bar.setMaximumSize(new Dimension(260, 6));
        bar.setPreferredSize(new Dimension(260, 6));
        bar.setForeground(Theme.PRIMARY);
        bar.setBorderPainted(false);
        cancelButton.addActionListener(e -> cancelGenerate());
        return centredCard(loadingTitle, text, bar, cancelButton);
    }

    private JPanel buildMessageState() {
        messageText.setEditable(false);
        messageText.setLineWrap(true);
        messageText.setWrapStyleWord(true);
        messageText.setOpaque(false);
        messageText.setFont(Theme.font(Font.PLAIN, 12f));
        messageText.setForeground(Theme.MUTED);
        messageText.setColumns(34);
        messageText.setBorder(null);
        messageButtons.setOpaque(false);
        return centredCard(messageTitle, messageText, messageButtons);
    }

    private JPanel buildRecipeCard() {
        JPanel card = new JPanel(new BorderLayout(0, 10));
        card.setBackground(Theme.CARD);
        card.setBorder(Theme.card(14));

        JPanel head = new JPanel(new BorderLayout(0, 4));
        head.setOpaque(false);
        head.add(titleLabel, BorderLayout.NORTH);
        JPanel facts = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        facts.setOpaque(false);
        facts.add(metaLabel);
        facts.add(Box.createHorizontalStrut(12));
        facts.add(expiringChip);
        facts.add(Box.createHorizontalStrut(12));
        facts.add(summaryLabel);
        head.add(facts, BorderLayout.SOUTH);
        card.add(head, BorderLayout.NORTH);

        JPanel columns = new JPanel(new GridLayout(1, 2, 16, 0));
        columns.setOpaque(false);
        ingredientRows.setBackground(Theme.CARD);
        JScrollPane ingScroll = new JScrollPane(ingredientRows);
        ingScroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        ingScroll.getVerticalScrollBar().setUnitIncrement(16);
        ingScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        columns.add(section("Ingredients", ingScroll));

        stepsArea.setEditable(false);
        stepsArea.setLineWrap(true);
        stepsArea.setWrapStyleWord(true);
        stepsArea.setFont(Theme.font(Font.PLAIN, 13f));
        stepsArea.setForeground(Theme.TEXT);
        stepsArea.setMargin(new Insets(8, 10, 8, 10));
        JScrollPane stepScroll = new JScrollPane(stepsArea);
        stepScroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        columns.add(section("Steps", stepScroll));
        card.add(columns, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(8, 0));
        bottom.setOpaque(false);
        bottom.add(notice, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        cookThisButton.setToolTipText("Check what you are missing before you cook");
        cookedButton.setToolTipText("Takes the ingredients out of your pantry (you can undo)");
        cookThisButton.addActionListener(e -> cookThis());
        cookedButton.addActionListener(e -> markCooked());
        undoButton.addActionListener(e -> undo());
        saveButton.addActionListener(e -> save());
        buttons.add(cookThisButton);
        buttons.add(cookedButton);
        buttons.add(undoButton);
        buttons.add(saveButton);
        bottom.add(buttons, BorderLayout.EAST);
        card.add(bottom, BorderLayout.SOUTH);
        return card;
    }

    private static JPanel section(String title, JComponent body) {
        JPanel p = new JPanel(new BorderLayout(0, 6));
        p.setOpaque(false);
        p.add(Theme.label(title, Font.BOLD, 13f, Theme.TEXT), BorderLayout.NORTH);
        p.add(body, BorderLayout.CENTER);
        return p;
    }

    private void showState(String name) {
        shownState = name;
        states.show(stateArea, name);
    }

    String getShownState() {
        return shownState;
    }

    // ------------------------------------------------------------ showing a recipe

    /** Shows a recipe (or the empty state for null), e.g. one opened from the History tab. */
    public void showRecipe(Recipe r) {
        if (r != recipe) {
            clearNotice();
        }
        this.recipe = r;
        if (r == null) {
            showState(EMPTY);
        } else {
            titleLabel.setText(r.getTitle());
            stepsArea.setText(formatSteps(r.getSteps()));
            stepsArea.setCaretPosition(0);
            showState(RECIPE);
        }
        refresh();
    }

    /** Re-reads the pantry (statuses change when food is added or used) and updates the buttons. */
    public void refresh() {
        if (recipe != null) {
            List<IngredientStatus> statuses = controller.getIngredientStatuses(recipe);
            metaLabel.setText(meta(recipe));
            String exp = expiringText(statuses);
            expiringChip.setVisible(exp != null);
            expiringChip.setText(exp == null ? " " : exp);
            summaryLabel.setText(summary(statuses));
            fillIngredients(recipe.getIngredients(), statuses);
        }
        boolean has = recipe != null && !aiBusy;
        cookThisButton.setEnabled(has);
        cookedButton.setEnabled(has);
        saveButton.setEnabled(has);
        undoButton.setEnabled(controller.canUndo() && !aiBusy);
        generateButton.setEnabled(!aiBusy);
    }

    private void fillIngredients(List<Ingredient> list, List<IngredientStatus> statuses) {
        ingredientRows.removeAll();
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;
        c.anchor = GridBagConstraints.WEST;
        for (int i = 0; i < list.size(); i++) {
            Ingredient ing = list.get(i);
            IngredientStatus st = statuses.get(i);
            c.insets = new Insets(6, 10, 6, 6);

            c.gridx = 0;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            ingredientRows.add(Theme.label(ing.getName(), Font.BOLD, 12f, Theme.TEXT), c);

            c.gridx = 1;
            c.weightx = 0;
            c.fill = GridBagConstraints.NONE;
            ingredientRows.add(Theme.muted(IngredientTableModel.amount(ing)), c);

            c.gridx = 2;
            ingredientRows.add(chipFor(st), c);

            c.gridx = 3;
            c.insets = new Insets(2, 4, 2, 10);
            if (canSubstitute(st)) {
                JButton sub = Theme.ai("Substitute", false);
                sub.setToolTipText("Ask the agent what you can use instead of " + ing.getName());
                sub.setEnabled(!aiBusy);
                sub.addActionListener(e -> substitute(ing));
                ingredientRows.add(sub, c);
            } else {
                ingredientRows.add(Box.createRigidArea(new Dimension(1, 30)), c);   // same row height
            }
            c.gridy++;
        }
        c.gridx = 0;
        c.weighty = 1;
        c.fill = GridBagConstraints.BOTH;
        ingredientRows.add(Box.createVerticalGlue(), c);
        ingredientRows.revalidate();
        ingredientRows.repaint();
    }

    private static Chip chipFor(IngredientStatus s) {
        String text = statusLabel(s);
        switch (s) {
            case IN_PANTRY:
                return new Chip(text, Theme.PRIMARY_LIGHT, Theme.PRIMARY);
            case USE_SOON:
            case NOT_ENOUGH:
                return new Chip(text, Theme.WARN_BG, Theme.WARN_FG);
            case EXPIRED:
            case MISSING:
                return new Chip(text, Theme.DANGER_BG, Theme.DANGER_FG);
            default:
                return new Chip(text, BASIC_BG, Theme.MUTED);
        }
    }

    // ------------------------------------------------------------ F01 generate

    /**
     * Generates a recipe; {@code requiredItems} are ingredients it must use, e.g. items that
     * expire soon (F05 "Suggest a recipe for expiring food").
     */
    public void generate(List<String> requiredItems) {
        if (aiBusy) {
            return;
        }
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
            requestError.setText(ex.getMessage());
            return;
        }
        requestError.setText(" ");
        lastRequired = List.copyOf(requiredItems);
        loadingTitle.setText(loadingText(requiredItems));
        returnState = recipe == null ? EMPTY : RECIPE;
        showState(LOADING);
        running = Background.run(this::setAiBusy,
                () -> controller.generateRecipe(req),
                r -> {
                    running = null;
                    showRecipe(r);
                    dataChanged.run();
                },
                ex -> {
                    running = null;
                    dataChanged.run();
                    generateFailed(ex);
                });
    }

    private void cancelGenerate() {
        if (running != null) {
            running.cancel();
            running = null;
        }
        showState(RECIPE.equals(returnState) && recipe != null ? RECIPE : EMPTY);
        clearNotice();
        dataChanged.run();
    }

    private void generateFailed(RuntimeException ex) {
        if (ex instanceof PantryEmptyException) {
            showMessage("Your pantry is empty",
                    "Add what you have in the Pantry tab first, or let the agent suggest a recipe "
                            + "you can shop for (the missing ingredients go to your cart).",
                    action("Go to Pantry", true, () -> openTab.accept(MainWindow.PANTRY)),
                    action("Suggest a recipe to shop for", false, () -> {
                        allowMissing.setSelected(true);
                        generate(lastRequired);
                    }));
        } else if (ex instanceof LLMUnavailableException) {
            showMessage("AI is unavailable", ex.getMessage()
                            + "\n\nYou can still open and cook a recipe you saved before.",
                    action("Try Again", true, () -> generate(lastRequired)),
                    action("Open History", false, () -> openTab.accept(MainWindow.HISTORY)));
        } else {
            String msg = ex instanceof LLMException ? ex.getMessage() : "Something went wrong: " + ex.getMessage();
            showMessage("The agent couldn't make a recipe", msg,
                    action("Try Again", true, () -> generate(lastRequired)),
                    action("Open History", false, () -> openTab.accept(MainWindow.HISTORY)));
        }
    }

    private static JButton action(String text, boolean primary, Runnable r) {
        JButton b = primary ? Theme.primary(text) : Theme.secondary(text);
        b.addActionListener(e -> r.run());
        return b;
    }

    private void showMessage(String title, String text, JButton... buttons) {
        messageTitle.setText(title);
        messageText.setText(text);
        messageButtons.removeAll();
        for (JButton b : buttons) {
            messageButtons.add(b);
        }
        messageButtons.revalidate();
        showState(MESSAGE);
    }

    // ------------------------------------------------------------ F07 substitute

    private void substitute(Ingredient ing) {
        if (recipe == null || aiBusy) {
            return;
        }
        Recipe current = recipe;
        say("Looking for substitutes for " + ing.getName() + "…", Theme.MUTED);
        Background.run(this::setAiBusy,
                () -> controller.suggestSubstitutes(current, ing.getName()),
                subs -> {
                    clearNotice();
                    dataChanged.run();
                    if (recipe == current) {
                        chooseSubstitute(ing, subs);
                    }
                },
                ex -> {
                    clearNotice();
                    dataChanged.run();
                    showAiError(ex);
                });
    }

    private void chooseSubstitute(Ingredient ing, List<Substitution> subs) {
        if (subs.isEmpty()) {
            int choice = JOptionPane.showConfirmDialog(this,
                    "The agent found no good substitute for " + ing.getName() + ".\nAdd it to your cart?",
                    "No substitute", JOptionPane.YES_NO_OPTION);
            if (choice == JOptionPane.YES_OPTION) {
                addToCart(List.of(ing));
            }
            return;
        }
        int choice = new SubstituteDialog(SwingUtilities.getWindowAncestor(this), ing, subs).ask();
        if (choice == SubstituteDialog.ADD_TO_CART) {
            addToCart(List.of(ing));
        } else if (choice >= 0) {
            Substitution s = subs.get(choice);
            controller.useSubstitute(recipe, ing.getName(), s);
            stepsArea.setText(formatSteps(recipe.getSteps()));
            stepsArea.setCaretPosition(0);
            refresh();
            dataChanged.run();
            say("Using " + s.getSubstitute() + " instead of " + ing.getName() + ".", Theme.PRIMARY);
        }
    }

    // ------------------------------------------------------------ F11 check

    private void cookThis() {
        if (recipe == null) {
            return;
        }
        IngredientCheck check = controller.checkIngredients(recipe);
        if (check.allInStock()) {
            int choice = JOptionPane.showConfirmDialog(this,
                    "You have everything for this recipe.\nMark it as cooked now? "
                            + "This takes the ingredients out of your pantry.",
                    "All in stock", JOptionPane.YES_NO_OPTION);
            if (choice == JOptionPane.YES_OPTION) {
                markCooked();
            }
            return;
        }
        Map<String, IngredientStatus> byName = statusByName(recipe.getIngredients(),
                controller.getIngredientStatuses(recipe));
        if (askAddMissing(check.getMissing(), byName)) {
            addToCart(check.getMissing());
        }
    }

    /** The "Missing ingredients" dialog. Returns true if the user chose Add Missing to Cart. */
    private boolean askAddMissing(List<Ingredient> missing, Map<String, IngredientStatus> byName) {
        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(owner, "Missing ingredients", java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        JPanel body = new JPanel(new BorderLayout(0, 10));
        body.setBackground(Theme.CARD);
        body.setBorder(BorderFactory.createEmptyBorder(16, 18, 14, 18));
        JPanel text = new JPanel(new BorderLayout(0, 4));
        text.setOpaque(false);
        text.add(Theme.label(missingTitle(missing.size()), Font.BOLD, 15f, Theme.TEXT), BorderLayout.NORTH);
        text.add(Theme.muted("Add them to your cart, or use Substitute on the recipe card."), BorderLayout.SOUTH);
        body.add(text, BorderLayout.NORTH);

        DefaultTableModel rows = new DefaultTableModel(new String[]{"Ingredient", "Amount needed", "Why"}, 0) {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        for (String[] r : missingRows(missing, byName)) {
            rows.addRow(r);
        }
        JTable table = new JTable(rows);
        table.setRowHeight(26);
        table.setFocusable(false);
        table.setRowSelectionAllowed(false);
        table.getTableHeader().setReorderingAllowed(false);
        JScrollPane sp = new JScrollPane(table);
        sp.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        sp.setPreferredSize(new Dimension(420, Math.min(220, 30 + 26 * missing.size())));
        body.add(sp, BorderLayout.CENTER);

        boolean[] add = {false};
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        JButton close = Theme.secondary("Close");
        JButton addButton = Theme.primary("Add Missing to Cart");
        close.addActionListener(e -> dialog.dispose());
        addButton.addActionListener(e -> {
            add[0] = true;
            dialog.dispose();
        });
        buttons.add(close);
        buttons.add(addButton);
        body.add(buttons, BorderLayout.SOUTH);

        dialog.setContentPane(body);
        dialog.getRootPane().setDefaultButton(addButton);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
        return add[0];
    }

    private void addToCart(List<Ingredient> items) {
        List<String> added = new ArrayList<>();
        for (Ingredient i : items) {
            double qty = i.getQuantity() > 0 ? i.getQuantity() : 1;
            controller.addToCart(new CartItem(i.getName(), qty));
            added.add(i.getName());
        }
        dataChanged.run();
        say("Added to your cart: " + String.join(", ", added) + ".", Theme.PRIMARY);
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
            say(ex.getMessage(), Theme.DANGER_FG);
            return;
        }
        refresh();
        dataChanged.run();
        say(cookedMessage(skipped), Theme.PRIMARY);
    }

    private void undo() {
        String msg = controller.undo().map(d -> "Undone: " + d + ". Your pantry is back as it was.")
                .orElse("There is nothing to undo.");
        refresh();
        dataChanged.run();
        say(msg, Theme.PRIMARY);
    }

    // ------------------------------------------------------------ F08 save

    private void save() {
        if (recipe == null) {
            return;
        }
        boolean saved = controller.saveRecipe(recipe);
        dataChanged.run();
        say(saved ? "Saved to your History. You can rate it there." : "This recipe is already in your History.",
                saved ? Theme.PRIMARY : Theme.MUTED);
    }

    // ------------------------------------------------------------ helpers

    private void setAiBusy(boolean busy) {
        aiBusy = busy;
        refresh();
    }

    private void clearNotice() {
        notice.setText(" ");
        notice.setToolTipText(null);
    }

    /** A short message under the recipe; long ones wrap onto a second line. */
    private void say(String text, Color colour) {
        notice.setForeground(colour);
        notice.setText("<html><div style='width:" + NOTICE_WIDTH + "px'>" + ChatFormat.escape(text) + "</div></html>");
        notice.setToolTipText(text);
    }

    private void showAiError(RuntimeException ex) {
        String title = ex instanceof LLMUnavailableException ? "AI unavailable" : "AI problem";
        String msg = ex instanceof LLMException ? ex.getMessage()
                : "Something went wrong: " + ex.getMessage();
        JOptionPane.showMessageDialog(this, msg, title, JOptionPane.WARNING_MESSAGE);
    }

    Recipe getRecipe() {
        return recipe;
    }

    // ------------------------------------------------------------ text helpers (tested)

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

    /** Chip text for an ingredient. */
    static String statusLabel(IngredientStatus s) {
        switch (s) {
            case IN_PANTRY:
                return "In pantry";
            case USE_SOON:
                return "Use soon";
            case EXPIRED:
                return "Expired";
            case NOT_ENOUGH:
                return "Not enough";
            case MISSING:
                return "Missing";
            default:
                return "Basic";
        }
    }

    /** Ingredients you could replace: the ones to buy and the expired ones. */
    static boolean canSubstitute(IngredientStatus s) {
        return s.needsBuying() || s == IngredientStatus.EXPIRED;
    }

    /** "3 in pantry · 2 missing", plus " · 1 basic" for salt, water and such. */
    static String summary(List<IngredientStatus> statuses) {
        int have = 0;
        int missing = 0;
        int basic = 0;
        for (IngredientStatus s : statuses) {
            if (s == IngredientStatus.BASIC) {
                basic++;
            } else if (s.needsBuying()) {
                missing++;
            } else {
                have++;
            }
        }
        String text = have + " in pantry · " + missing + " missing";
        return basic > 0 ? text + " · " + basic + " basic" : text;
    }

    /** "Uses 2 expiring items", "Uses 1 expiring item", or null when it uses none. */
    static String expiringText(List<IngredientStatus> statuses) {
        long n = statuses.stream().filter(s -> s == IngredientStatus.USE_SOON).count();
        if (n == 0) {
            return null;
        }
        return "Uses " + n + (n == 1 ? " expiring item" : " expiring items");
    }

    /** "30 min · 2 portions", plus " · Cooked once" or " · Cooked 3 times". */
    static String meta(Recipe r) {
        String s = r.getCookTimeMinutes() + " min · " + r.getPortions() + (r.getPortions() == 1 ? " portion" : " portions");
        int n = r.getTimesCooked();
        if (n == 1) {
            return s + " · Cooked once";
        }
        return n > 1 ? s + " · Cooked " + n + " times" : s;
    }

    static String loadingText(List<String> required) {
        return required.isEmpty() ? "Thinking of a recipe…"
                : "Finding a recipe that uses " + String.join(", ", required) + "…";
    }

    static String missingTitle(int n) {
        return n == 1 ? "You're missing 1 ingredient" : "You're missing " + n + " ingredients";
    }

    /** Rows for the missing dialog: name, amount still needed, and why. */
    static List<String[]> missingRows(List<Ingredient> missing, Map<String, IngredientStatus> byName) {
        List<String[]> rows = new ArrayList<>();
        for (Ingredient i : missing) {
            IngredientStatus s = byName.get(i.getName().toLowerCase(Locale.ROOT));
            String why = s == IngredientStatus.NOT_ENOUGH ? "Not enough in pantry" : "Not in pantry";
            rows.add(new String[]{i.getName(), IngredientTableModel.amount(i), why});
        }
        return rows;
    }

    static Map<String, IngredientStatus> statusByName(List<Ingredient> ingredients, List<IngredientStatus> statuses) {
        Map<String, IngredientStatus> map = new HashMap<>();
        for (int i = 0; i < ingredients.size() && i < statuses.size(); i++) {
            map.putIfAbsent(ingredients.get(i).getName().toLowerCase(Locale.ROOT), statuses.get(i));
        }
        return map;
    }

    static String cookedMessage(List<String> skipped) {
        String msg = "Marked as cooked and taken out of your pantry.";
        if (!skipped.isEmpty()) {
            msg += " Not in your pantry, so skipped: " + String.join(", ", skipped) + ".";
        }
        return msg + " Click Undo if this was a mistake.";
    }

    /** The substitute in the pantry is picked first; otherwise the agent's first suggestion. */
    static int defaultChoice(List<Substitution> subs) {
        for (int i = 0; i < subs.size(); i++) {
            if (subs.get(i).isInStock()) {
                return i;
            }
        }
        return 0;
    }

    // ------------------------------------------------------------ substitute dialog

    /** "Substitute for X": one radio button per suggestion, then Use substitute / Add to cart / Cancel. */
    private static final class SubstituteDialog {
        static final int CANCEL = -1;
        static final int ADD_TO_CART = -2;

        private final JDialog dialog;
        private int result = CANCEL;

        SubstituteDialog(Window owner, Ingredient ing, List<Substitution> subs) {
            dialog = new JDialog(owner, "Substitute for " + ing.getName(), java.awt.Dialog.ModalityType.APPLICATION_MODAL);
            JPanel body = new JPanel(new BorderLayout(0, 12));
            body.setBackground(Theme.CARD);
            body.setBorder(BorderFactory.createEmptyBorder(16, 18, 14, 18));
            JPanel text = new JPanel(new BorderLayout(0, 4));
            text.setOpaque(false);
            text.add(Theme.label("Instead of " + ing.getName(), Font.BOLD, 15f, Theme.TEXT), BorderLayout.NORTH);
            text.add(Theme.muted("Pick one. It replaces " + ing.getName() + " in this recipe ("
                    + IngredientTableModel.amount(ing) + ")."), BorderLayout.SOUTH);
            body.add(text, BorderLayout.NORTH);

            JPanel options = new JPanel();
            options.setOpaque(false);
            options.setLayout(new BoxLayout(options, BoxLayout.Y_AXIS));
            ButtonGroup group = new ButtonGroup();
            List<JRadioButton> radios = new ArrayList<>();
            for (Substitution s : subs) {
                JPanel opt = new JPanel(new BorderLayout(0, 2));
                opt.setOpaque(false);
                opt.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
                JPanel line = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
                line.setOpaque(false);
                JRadioButton radio = new JRadioButton(s.getSubstitute());
                radio.setOpaque(false);
                radio.setFont(Theme.font(Font.BOLD, 12f));
                group.add(radio);
                radios.add(radio);
                line.add(radio);
                if (s.isInStock()) {
                    line.add(Box.createHorizontalStrut(8));
                    line.add(new Chip("In your pantry", Theme.PRIMARY_LIGHT, Theme.PRIMARY));
                }
                opt.add(line, BorderLayout.NORTH);
                if (!s.getNote().isEmpty()) {
                    JLabel note = Theme.muted(s.getNote());
                    note.setBorder(BorderFactory.createEmptyBorder(0, 26, 0, 0));
                    opt.add(note, BorderLayout.SOUTH);
                }
                opt.setAlignmentX(LEFT_ALIGNMENT);
                options.add(opt);
            }
            radios.get(defaultChoice(subs)).setSelected(true);
            body.add(options, BorderLayout.CENTER);

            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            buttons.setOpaque(false);
            JButton cancel = Theme.secondary("Cancel");
            JButton cart = Theme.secondary("Add " + ing.getName() + " to cart");
            JButton use = Theme.primary("Use substitute");
            cancel.addActionListener(e -> dialog.dispose());
            cart.addActionListener(e -> {
                result = ADD_TO_CART;
                dialog.dispose();
            });
            use.addActionListener(e -> {
                for (int i = 0; i < radios.size(); i++) {
                    if (radios.get(i).isSelected()) {
                        result = i;
                    }
                }
                dialog.dispose();
            });
            buttons.add(cancel);
            buttons.add(cart);
            buttons.add(use);
            body.add(buttons, BorderLayout.SOUTH);

            dialog.setContentPane(body);
            dialog.getRootPane().setDefaultButton(use);
            dialog.pack();
            dialog.setMinimumSize(new Dimension(420, dialog.getHeight()));
            dialog.setLocationRelativeTo(owner);
        }

        /** Shows the dialog; returns the chosen index, {@link #ADD_TO_CART} or {@link #CANCEL}. */
        int ask() {
            dialog.setVisible(true);
            return result;
        }
    }
}
