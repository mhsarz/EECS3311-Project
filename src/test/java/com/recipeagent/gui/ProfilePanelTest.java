package com.recipeagent.gui;

import com.recipeagent.agent.AgentTool;
import com.recipeagent.agent.RecipeAgent;
import com.recipeagent.controller.AgentController;
import com.recipeagent.llm.LLMClient;
import com.recipeagent.llm.LLMResponse;
import com.recipeagent.model.UserProfile;
import com.recipeagent.storage.JsonStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.SwingUtilities;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The Profile tab (F04) and its tag input. Runs without a screen. */
class ProfilePanelTest {

    @TempDir
    Path tmp;

    private AgentController controller(LLMClient llm) {
        return new AgentController(llm, 100, new JsonStorage(tmp), Clock.systemDefaultZone());
    }

    private static <T> T onEdt(java.util.function.Supplier<T> s) throws Exception {
        Object[] box = new Object[1];
        SwingUtilities.invokeAndWait(() -> box[0] = s.get());
        @SuppressWarnings("unchecked")
        T t = (T) box[0];
        return t;
    }

    private static void onEdt(Runnable r) throws Exception {
        SwingUtilities.invokeAndWait(r);
    }

    // ------------------------------------------------------------ tag input

    @Test
    void tagsAreSplitAtCommasCleanedAndUnique() {
        assertEquals(List.of("peanuts", "shell fish"), TagInput.splitTags(" Peanuts,  shell   fish ,,PEANUTS"));
        assertEquals(List.of(), TagInput.splitTags(" , "));
        assertEquals(List.of(), TagInput.splitTags(null));
        assertEquals("tree nuts", TagInput.normalize("  Tree   Nuts "));
    }

    @Test
    void tagInputAddsRemovesAndCommitsTypedText() throws Exception {
        TagInput tags = onEdt(() -> new TagInput("Type", Theme.DANGER_BG, Theme.DANGER_FG));
        List<String> changes = new ArrayList<>();
        onEdt(() -> {
            tags.onChange(() -> changes.add("x"));
            assertTrue(tags.addTag("Peanuts"));
            assertFalse(tags.addTag("peanuts "));                   // already there
            assertFalse(tags.addTag("  "));
            tags.setPendingText("milk, sesame");
            tags.commitPending();                                    // like pressing Enter or leaving the field
            tags.removeTag("milk");
        });
        assertEquals(List.of("peanuts", "sesame"), tags.getTags());
        assertEquals("", tags.getPendingText());
        assertEquals(3, changes.size());                             // add, commit, remove
        onEdt(() -> tags.setTags(List.of("Eggs", "eggs", "Soy")));
        assertEquals(List.of("eggs", "soy"), tags.getTags());
        assertEquals(3, changes.size());                             // setTags does not count as a change
    }

    // ------------------------------------------------------------ profile form

    @Test
    void savingTheFormUpdatesTheProfileOnDisk() throws Exception {
        AgentController c = controller(null);
        int[] refreshed = {0};
        ProfilePanel panel = onEdt(() -> new ProfilePanel(c, () -> refreshed[0]++, t -> { }, () -> false));
        assertFalse(onEdt(() -> panel.hasUnsavedChanges()));

        onEdt(() -> panel.loadForm("Vegetarian", List.of("Peanuts", "shellfish"), List.of("mushrooms"), 4));
        assertTrue(onEdt(() -> panel.hasUnsavedChanges()));
        onEdt(panel::save);

        UserProfile saved = controller(null).getProfile();            // read back from disk
        assertEquals("vegetarian", saved.getDiet());
        assertEquals(List.of("peanuts", "shellfish"), saved.getAllergies());
        assertEquals(List.of("mushrooms"), saved.getDislikes());
        assertEquals(4, saved.getDefaultPortions());
        assertEquals(1, refreshed[0]);
        assertTrue(panel.getMessage().startsWith("Profile saved"));
        assertFalse(onEdt(() -> panel.hasUnsavedChanges()));
    }

    @Test
    void refreshShowsAProfileSavedByTheChatUnlessTheFormWasChanged() throws Exception {
        AgentController c = controller(null);
        ProfilePanel panel = onEdt(() -> new ProfilePanel(c, () -> { }, t -> { }, () -> false));
        c.updateProfile("halal", List.of("sesame"), List.of(), 3);   // e.g. saved by the questionnaire
        onEdt(panel::refresh);
        assertFalse(onEdt(() -> panel.hasUnsavedChanges()));   // form now shows it

        assertEquals(3, controller(null).getProfile().getDefaultPortions());

        onEdt(() -> panel.loadForm("vegan", List.of("sesame"), List.of(), 3));   // the user starts editing
        c.updateProfile("halal", List.of("sesame", "eggs"), List.of(), 3);
        onEdt(panel::refresh);
        assertTrue(onEdt(() -> panel.hasUnsavedChanges()));    // the user's edits are kept
        assertTrue(panel.getMessage().startsWith("Your profile was also changed in the chat"));
    }

    @Test
    void helpMeSetUpStartsTheQuestionnaireOrFallsBackToTheForm() throws Exception {
        List<String> sent = new ArrayList<>();
        ProfilePanel noAi = onEdt(() -> new ProfilePanel(controller(null), () -> { }, sent::add, () -> false));
        onEdt(noAi::helpMeSetUp);
        assertTrue(sent.isEmpty());
        assertTrue(noAi.getMessage().startsWith("The AI is unavailable, so please fill in the form instead."));

        LLMClient fake = (String prompt, List<AgentTool> tools) -> LLMResponse.text("What do you like to eat?");
        boolean[] busy = {true};
        ProfilePanel withAi = onEdt(() -> new ProfilePanel(controller(fake), () -> { }, sent::add, () -> busy[0]));
        onEdt(withAi::helpMeSetUp);
        assertTrue(sent.isEmpty());                                  // chat still busy
        busy[0] = false;
        onEdt(withAi::helpMeSetUp);
        assertEquals(List.of(RecipeAgent.PROFILE_SETUP_MESSAGE), sent);
        assertTrue(withAi.getMessage().contains("Chat panel"));
    }

    @Test
    void textHelpers() {
        assertEquals("No special diet", ProfilePanel.dietLabel("none"));
        assertEquals("No special diet", ProfilePanel.dietLabel(" "));
        assertEquals("Gluten-free", ProfilePanel.dietLabel("gluten-free"));
        assertEquals("The AI is unavailable, so please fill in the form instead. Reason: No API key.",
                ProfilePanel.unavailableMessage("AI unavailable: No API key."));
        assertEquals("The AI is unavailable, so please fill in the form instead.",
                ProfilePanel.unavailableMessage(null));

        UserProfile p = new UserProfile();
        p.setDiet("vegan");
        p.setAllergies(List.of("peanuts"));
        assertFalse(ProfilePanel.differs(p, " Vegan ", List.of("PEANUTS"), List.of(), UserProfile.DEFAULT_PORTIONS));
        assertTrue(ProfilePanel.differs(p, "vegan", List.of("peanuts", "milk"), List.of(), UserProfile.DEFAULT_PORTIONS));
        assertTrue(ProfilePanel.differs(p, "vegan", List.of("peanuts"), List.of(), 5));
        assertTrue(ProfilePanel.differs(p, "none", List.of("peanuts"), List.of(), UserProfile.DEFAULT_PORTIONS));
    }

    @Test
    void recipeTabPicksUpANewDefaultPortionCount() throws Exception {
        AgentController c = controller(null);
        RecipePanel recipe = onEdt(() -> new RecipePanel(c, () -> { }, t -> { }));
        c.updateProfile("none", List.of(), List.of(), 5);
        onEdt(recipe::refresh);
        assertEquals("5", onEdt(recipe::getPortionsText));
    }
}
