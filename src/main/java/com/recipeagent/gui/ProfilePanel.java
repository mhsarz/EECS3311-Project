package com.recipeagent.gui;

import com.recipeagent.agent.RecipeAgent;
import com.recipeagent.controller.AgentController;
import com.recipeagent.model.UserProfile;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * The Profile page (F04): diet, allergies, dislikes and default portions, used by every recipe
 * request. Fill in the form and click Save profile, or click Help Me Set Up and answer the
 * agent's questions in the Chat panel (the agent saves the profile when it has enough
 * answers). If the AI is unavailable, the page says so and the form is used instead.
 */
public class ProfilePanel extends JPanel {

    private static final long serialVersionUID = 1L;
    static final String[] DIETS = {UserProfile.NO_DIET, "vegetarian", "vegan", "pescatarian", "halal",
            "kosher", "gluten-free", "dairy-free", "low-carb"};
    private static final Color NOTICE_BG = new Color(0xFFF8E1);
    private static final Color DISLIKE_BG = new Color(0xF0F2EF);

    private final transient AgentController controller;
    private final transient Runnable dataChanged;
    private final transient Consumer<String> sendToChat;
    private final transient BooleanSupplier chatBusy;

    private final JComboBox<String> dietBox = new JComboBox<>(DIETS);
    private final TagInput allergies = new TagInput("Type an allergy and press Enter", Theme.DANGER_BG, Theme.DANGER_FG);
    private final TagInput dislikes = new TagInput("Type a food you dislike and press Enter", DISLIKE_BG, Theme.TEXT);
    private final JSpinner portions = new JSpinner(new SpinnerNumberModel(UserProfile.DEFAULT_PORTIONS, 1, 20, 1));
    private final JButton saveButton = Theme.primary("Save profile");
    private final JButton resetButton = Theme.secondary("Undo changes");
    private final JButton helpButton = Theme.ai("Help Me Set Up", false);
    private final JLabel message = Theme.label(" ", Font.PLAIN, 12f, Theme.PRIMARY);
    private boolean loading;
    /** A copy of the profile the form last showed; "unsaved changes" means the form differs from it. */
    private transient UserProfile loaded = new UserProfile();
    private String messageText = "";

    /**
     * @param sendToChat sends a message in the Chat panel as if the user typed it
     * @param chatBusy   true while the Chat panel is waiting for an answer
     */
    public ProfilePanel(AgentController controller, Runnable dataChanged,
                        Consumer<String> sendToChat, BooleanSupplier chatBusy) {
        super(new BorderLayout(0, 12));
        this.controller = controller;
        this.dataChanged = dataChanged;
        this.sendToChat = sendToChat;
        this.chatBusy = chatBusy;
        setBorder(BorderFactory.createEmptyBorder(16, 20, 12, 16));
        add(new PageHeader("Profile", "Tell the agent how you eat. Every recipe follows it."), BorderLayout.NORTH);

        JPanel column = new JPanel(new BorderLayout(0, 12));
        column.setOpaque(false);
        column.add(buildForm(), BorderLayout.NORTH);
        column.add(buildNotice(), BorderLayout.CENTER);
        add(column, BorderLayout.CENTER);
        loadFrom(controller.getProfile());
    }

    // ------------------------------------------------------------ building the page

    private JPanel buildForm() {
        JPanel card = new JPanel(new BorderLayout(0, 10));
        card.setBackground(Theme.CARD);
        card.setBorder(Theme.card(14));

        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        JPanel titles = new JPanel(new BorderLayout(0, 2));
        titles.setOpaque(false);
        titles.add(Theme.label("Your preferences", Font.BOLD, 13f, Theme.TEXT), BorderLayout.NORTH);
        titles.add(Theme.muted("Fill in the form, or let the agent ask you a few questions."), BorderLayout.SOUTH);
        head.add(titles, BorderLayout.WEST);
        helpButton.setToolTipText("The agent asks about 6 short questions in the Chat panel, then saves your profile");
        helpButton.addActionListener(e -> helpMeSetUp());
        head.add(helpButton, BorderLayout.EAST);
        card.add(head, BorderLayout.NORTH);

        dietBox.setEditable(true);
        dietBox.setToolTipText("Pick one, or type your own (e.g. keto)");
        dietBox.setRenderer(new javax.swing.DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index,
                                                          boolean selected, boolean focus) {
                return super.getListCellRendererComponent(list, dietLabel(String.valueOf(value)), index, selected, focus);
            }
        });
        dietBox.addActionListener(e -> changed());
        allergies.onChange(this::changed);
        dislikes.onChange(this::changed);
        allergies.onTyping(this::changed);              // so Save turns on while a word is being typed
        dislikes.onTyping(this::changed);
        portions.addChangeListener(e -> changed());
        ((JSpinner.DefaultEditor) portions.getEditor()).getTextField().setColumns(3);

        JPanel grid = new JPanel(new GridBagLayout());
        grid.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 0, 5, 12);
        c.anchor = GridBagConstraints.NORTHWEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        String[] labels = {"Diet", "Allergies", "Dislikes", "Default portions"};
        JComponent[] fields = {left(dietBox), allergies, dislikes, left(portions)};
        String[] hints = {"", "The agent never uses these, and every recipe is checked for them.",
                "The agent avoids these when it can.", "The starting number of portions in the Recipe tab."};
        for (int i = 0; i < labels.length; i++) {
            c.gridy = i * 2;
            c.gridx = 0;
            c.weightx = 0;
            JLabel l = Theme.label(labels[i], Font.BOLD, 12f, Theme.TEXT);
            l.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
            grid.add(l, c);
            c.gridx = 1;
            c.weightx = 1;
            grid.add(fields[i], c);
            if (!hints[i].isEmpty()) {
                c.gridy = i * 2 + 1;
                c.insets = new Insets(0, 0, 5, 12);
                grid.add(Theme.muted(hints[i]), c);
                c.insets = new Insets(5, 0, 5, 12);
            }
        }
        card.add(grid, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(8, 0));
        bottom.setOpaque(false);
        bottom.add(message, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        resetButton.setToolTipText("Go back to the saved profile");
        resetButton.addActionListener(e -> {
            loadFrom(controller.getProfile());
            say("Back to your saved profile.", Theme.MUTED);
        });
        saveButton.addActionListener(e -> save());
        buttons.add(resetButton);
        buttons.add(saveButton);
        bottom.add(buttons, BorderLayout.EAST);
        card.add(bottom, BorderLayout.SOUTH);
        return card;
    }

    private static JPanel left(JComponent c) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        p.setOpaque(false);
        p.add(c);
        return p;
    }

    private JPanel buildNotice() {
        JPanel notice = new JPanel(new BorderLayout(0, 4));
        notice.setBackground(NOTICE_BG);
        notice.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(0xF1DFA6)),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        notice.add(Theme.label("About allergies", Font.BOLD, 12f, Theme.WARN_FG), BorderLayout.NORTH);
        notice.add(Theme.label("<html>The agent is told never to use your allergies, and the app also checks each "
                + "recipe's ingredient names for them. This check can miss hidden allergens (for example in sauces "
                + "or mixes), so always read the labels yourself.</html>", Font.PLAIN, 12f, Theme.WARN_FG),
                BorderLayout.CENTER);
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.add(notice, BorderLayout.NORTH);
        return wrap;
    }

    // ------------------------------------------------------------ form state

    /**
     * Shows the saved profile (the chat questionnaire may have saved a new one). If the user is
     * in the middle of editing, their changes are kept and a note says the profile changed.
     */
    public void refresh() {
        UserProfile saved = controller.getProfile();
        boolean savedChanged = differs(loaded, saved.getDiet(), saved.getAllergies(), saved.getDislikes(),
                saved.getDefaultPortions());
        if (!hasUnsavedChanges()) {
            if (savedChanged) {
                loadFrom(saved);
            }
        } else if (savedChanged) {
            say("Your profile was also changed in the chat. Save to keep this version, or click Undo changes "
                    + "to see the new one.", Theme.MUTED);
        }
        updateButtons();
    }

    /** Shows a profile in the form and remembers it as the saved state. */
    void loadFrom(UserProfile p) {
        UserProfile copy = new UserProfile();
        copy.setDiet(p.getDiet());
        copy.setAllergies(p.getAllergies());
        copy.setDislikes(p.getDislikes());
        copy.setDefaultPortions(p.getDefaultPortions());
        loaded = copy;
        loadForm(p.getDiet(), p.getAllergies(), p.getDislikes(), p.getDefaultPortions());
    }

    /** Fills the form (also used by tests). */
    void loadForm(String diet, List<String> allergyList, List<String> dislikeList, int defaultPortions) {
        loading = true;
        try {
            dietBox.setSelectedItem(diet == null ? UserProfile.NO_DIET : diet);
            allergies.setTags(allergyList);
            dislikes.setTags(dislikeList);
            portions.setValue(Math.max(1, Math.min(20, defaultPortions)));
        } finally {
            loading = false;
        }
        updateButtons();
    }

    private String formDiet() {
        Object v = dietBox.isEditable() ? dietBox.getEditor().getItem() : dietBox.getSelectedItem();
        return v == null ? "" : v.toString();
    }

    private void changed() {
        if (loading) {
            return;
        }
        updateButtons();
        if (hasUnsavedChanges()) {
            say("You have unsaved changes.", Theme.MUTED);
        } else {
            say(" ", Theme.MUTED);
        }
    }

    private void updateButtons() {
        boolean dirty = hasUnsavedChanges();
        saveButton.setEnabled(dirty);
        resetButton.setEnabled(dirty);
    }

    /** True if the form differs from the profile it last showed (typed but not yet added words count too). */
    boolean hasUnsavedChanges() {
        List<String> a = new ArrayList<>(allergies.getTags());
        a.addAll(TagInput.splitTags(allergies.getPendingText()));
        List<String> d = new ArrayList<>(dislikes.getTags());
        d.addAll(TagInput.splitTags(dislikes.getPendingText()));
        return differs(loaded, formDiet(), a, d, (Integer) portions.getValue());
    }

    // ------------------------------------------------------------ actions

    void save() {
        allergies.commitPending();
        dislikes.commitPending();
        try {
            controller.updateProfile(formDiet(), allergies.getTags(), dislikes.getTags(), (Integer) portions.getValue());
        } catch (IllegalArgumentException ex) {
            say(ex.getMessage(), Theme.DANGER_FG);
            return;
        }
        loadFrom(controller.getProfile());          // shows the saved, cleaned-up values
        dataChanged.run();
        say("Profile saved. New recipes will follow it.", Theme.PRIMARY);
    }

    /** Help Me Set Up: the questionnaire in the Chat panel, or the form if the AI is unavailable (UC04 2a). */
    void helpMeSetUp() {
        if (!controller.isAiAvailable()) {
            say(unavailableMessage(controller.getAiStatus()), Theme.WARN_FG);
            dietBox.requestFocusInWindow();
            return;
        }
        if (chatBusy.getAsBoolean()) {
            say("The chat is still answering. Try again in a moment.", Theme.MUTED);
            return;
        }
        sendToChat.accept(RecipeAgent.PROFILE_SETUP_MESSAGE);
        say("Answer the agent's questions in the Chat panel. It saves your profile at the end.", Theme.PRIMARY);
    }

    /** The message under the form, as plain text. */
    String getMessage() {
        return messageText;
    }

    private void say(String text, Color colour) {
        message.setForeground(colour);
        message.setText(text.isBlank() ? " "
                : "<html><div style='width:420px'>" + ChatFormat.escape(text) + "</div></html>");
        messageText = text;
    }

    // ------------------------------------------------------------ helpers (tested)

    /** "none" is shown as "No special diet"; others are capitalised, e.g. "Gluten-free". */
    static String dietLabel(String diet) {
        if (diet == null || diet.isBlank() || diet.equalsIgnoreCase(UserProfile.NO_DIET)) {
            return "No special diet";
        }
        String d = diet.trim();
        return Character.toUpperCase(d.charAt(0)) + d.substring(1);
    }

    /** True if the form values are different from the saved profile (compared the way UserProfile stores them). */
    static boolean differs(UserProfile saved, String diet, List<String> allergyList, List<String> dislikeList,
                           int defaultPortions) {
        UserProfile form = new UserProfile();
        form.setDiet(diet);
        form.setAllergies(allergyList);
        form.setDislikes(dislikeList);
        form.setDefaultPortions(Math.max(1, defaultPortions));
        return !form.getDiet().equals(saved.getDiet())
                || !form.getAllergies().equals(saved.getAllergies())
                || !form.getDislikes().equals(saved.getDislikes())
                || form.getDefaultPortions() != saved.getDefaultPortions();
    }

    /** "The AI is unavailable, so please fill in the form instead. Reason: ..." */
    static String unavailableMessage(String aiStatus) {
        String reason = aiStatus == null ? "" : aiStatus.replaceFirst("^AI unavailable:\\s*", "").trim();
        String msg = "The AI is unavailable, so please fill in the form instead.";
        return reason.isEmpty() ? msg : msg + " Reason: " + reason;
    }
}
