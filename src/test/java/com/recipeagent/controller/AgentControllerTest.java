package com.recipeagent.controller;

import com.recipeagent.agent.AgentTool;
import com.recipeagent.agent.PantryEmptyException;
import com.recipeagent.llm.LLMClient;
import com.recipeagent.llm.LLMResponse;
import com.recipeagent.llm.LLMUnavailableException;
import com.recipeagent.model.CartItem;
import com.recipeagent.model.Ingredient;
import com.recipeagent.model.IngredientStatus;
import com.recipeagent.model.Notification;
import com.recipeagent.model.NotificationType;
import com.recipeagent.model.PantryItem;
import com.recipeagent.model.Recipe;
import com.recipeagent.model.RecipeRequest;
import com.recipeagent.model.StorageLocation;
import com.recipeagent.model.Substitution;
import com.recipeagent.notify.NotificationListener;
import com.recipeagent.storage.JsonStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AgentControllerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-03T12:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 3);

    @TempDir
    Path tmp;

    /** Fake LLM that returns scripted replies and keeps the prompts. */
    private static class ScriptedLLM implements LLMClient {
        final Deque<LLMResponse> replies = new ArrayDeque<>();
        final List<String> prompts = new ArrayList<>();

        @Override
        public LLMResponse generate(String prompt, List<AgentTool> tools) {
            prompts.add(prompt);
            return replies.isEmpty() ? LLMResponse.text("") : replies.poll();
        }
    }

    private AgentController controller(LLMClient llm) {
        return new AgentController(llm, 100, new JsonStorage(tmp), CLOCK);
    }

    private static PantryItem item(String name, double qty, LocalDate date) {
        return new PantryItem(name, qty, "", StorageLocation.FRIDGE, date);
    }

    // ------------------------------------------------------------ pantry

    @Test
    void addPantryItemAsksBeforeMergingDuplicates() {
        AgentController c = controller(null);
        assertTrue(c.addPantryItem(item("milk", 1, null), false));
        assertFalse(c.addPantryItem(item("Milk", 2, null), false));   // asks the user first
        assertEquals(1, c.getPantryItems().get(0).getQuantity());
        assertTrue(c.addPantryItem(item("Milk", 2, null), true));      // user chose Merge
        assertEquals(3, c.getPantryItems().get(0).getQuantity());
        assertEquals(1, c.getPantryItems().size());
    }

    @Test
    void everythingIsSavedAndLoadedAgain() {
        AgentController c = controller(null);
        c.addPantryItem(item("rice", 2, null), false);
        c.addToCart(new CartItem("eggs", 12));
        Recipe r = new Recipe("Rice bowl", List.of(new Ingredient("rice", 1, "")), List.of("Cook"), 10, 1);
        c.saveRecipe(r);
        c.rateRecipe(r.getId(), 5);
        c.updateProfile("vegetarian", List.of("peanuts"), List.of(), 3);

        AgentController reopened = controller(null);
        assertEquals("rice", reopened.getPantryItems().get(0).getName());
        assertEquals(12, reopened.getCartItems().get(0).getQuantity());
        assertEquals(5, reopened.findRecipe(r.getId()).orElseThrow().getRating());
        assertEquals("vegetarian", reopened.getProfile().getDiet());
        assertEquals(3, reopened.getProfile().getDefaultPortions());
    }

    @Test
    void removePantryItemByIdOrName() {
        AgentController c = controller(null);
        c.addPantryItem(item("rice", 2, null), false);
        c.addPantryItem(item("pasta", 1, null), false);
        String riceId = c.getPantryItems().get(0).getId();
        assertTrue(c.removePantryItem(riceId));
        assertFalse(c.removePantryItem(riceId));
        assertTrue(c.removePantryItemByName("PASTA"));
        assertTrue(controller(null).getPantryItems().isEmpty());
    }

    @Test
    void invalidProfileChangesNothing() {
        AgentController c = controller(null);
        assertThrows(IllegalArgumentException.class,
                () -> c.updateProfile("vegan", List.of("soy"), List.of(), 0));
        assertEquals("none", c.getProfile().getDiet());
    }

    // ------------------------------------------------------------ expiry

    @Test
    void checkExpiryPublishesOneAlertPerExpiringItem() {
        AgentController c = controller(null);
        c.addPantryItem(item("milk", 1, TODAY.plusDays(1)), false);
        c.addPantryItem(item("yogurt", 1, TODAY.plusDays(3)), false);
        c.addPantryItem(item("cheese", 1, TODAY.plusDays(10)), false);
        c.addPantryItem(item("salt", 1, null), false);

        List<Notification> received = new ArrayList<>();
        NotificationListener l = received::add;
        c.getNotifications().subscribe(l);
        try {
            List<PantryItem> expiring = c.checkExpiry();
            assertEquals(List.of("milk", "yogurt"), expiring.stream().map(PantryItem::getName).toList());
            assertEquals(2, received.size());
            assertEquals(NotificationType.EXPIRY, received.get(0).getType());
            assertEquals("milk (fridge) expires tomorrow", received.get(0).getMessage());
        } finally {
            c.getNotifications().unsubscribe(l);
        }
    }

    @Test
    void addingAnItemAlertsOnlyForThatItemIfItExpiresSoon() {
        AgentController c = controller(null);
        c.addPantryItem(item("milk", 1, TODAY.plusDays(1)), false);
        List<Notification> received = new ArrayList<>();
        NotificationListener l = received::add;
        c.getNotifications().subscribe(l);
        try {
            c.addPantryItem(item("rice", 1, null), false);                 // no date: no alert
            c.addPantryItem(item("cheese", 1, TODAY.plusDays(20)), false);  // far away: no alert
            assertTrue(received.isEmpty());                                // milk is not repeated
            c.addPantryItem(item("cream", 1, TODAY.plusDays(2)), false);
            assertEquals(1, received.size());
            assertEquals("cream (fridge) expires in 2 days", received.get(0).getMessage());
            c.addPantryItem(item("milk", 2, null), true);                  // merge: no repeat
            assertEquals(1, received.size());
        } finally {
            c.getNotifications().unsubscribe(l);
        }
    }

    @Test
    void itemsToUseSoonLeaveOutExpiredFoodAndSendNoAlerts() {
        AgentController c = controller(null);
        c.addPantryItem(item("spinach", 1, TODAY.minusDays(1)), false);   // expired
        c.addPantryItem(item("milk", 1, TODAY.plusDays(2)), false);
        c.addPantryItem(item("cream", 1, TODAY), false);                 // last day
        c.addPantryItem(item("cheese", 1, TODAY.plusDays(9)), false);
        List<Notification> received = new ArrayList<>();
        NotificationListener l = received::add;
        c.getNotifications().subscribe(l);
        try {
            assertEquals(List.of("cream", "milk"),
                    c.getItemsToUseSoon().stream().map(PantryItem::getName).toList());
            assertTrue(received.isEmpty());
        } finally {
            c.getNotifications().unsubscribe(l);
        }
    }

    // ------------------------------------------------------------ cooking + undo

    @Test
    void markCookedDeductsAndUndoRestoresAndBothAreSaved() {
        AgentController c = controller(null);
        c.addPantryItem(item("eggs", 6, null), false);
        Recipe omelette = new Recipe("Omelette", List.of(
                new Ingredient("eggs", 2, ""), new Ingredient("chives", 1, "tbsp")), List.of("Whisk", "Fry"), 10, 1);

        List<String> skipped = c.markCooked(omelette);
        assertEquals(List.of("chives"), skipped);
        assertEquals(4, controller(null).getPantryItems().get(0).getQuantity());  // saved
        assertTrue(c.canUndo());

        assertEquals("Cooked Omelette", c.undo().orElseThrow());
        assertEquals(6, controller(null).getPantryItems().get(0).getQuantity());  // saved
        assertTrue(c.undo().isEmpty());
    }

    @Test
    void checkIngredientsListsMissing() {
        AgentController c = controller(null);
        c.addPantryItem(item("eggs", 1, null), false);
        Recipe r = new Recipe("Omelette", List.of(new Ingredient("eggs", 2, ""), new Ingredient("milk", 1, "cup")),
                List.of("Cook"), 10, 1);
        assertEquals(List.of("eggs", "milk"), c.checkIngredients(r).getMissingNames());
    }

    @Test
    void basicStaplesAreNotCountedAsMissing() {
        AgentController c = controller(null);
        c.addPantryItem(item("eggs", 6, null), false);
        Recipe r = new Recipe("Eggs", List.of(new Ingredient("eggs", 2, ""), new Ingredient("Salt", 0, ""),
                new Ingredient("black pepper", 0, ""), new Ingredient("cooking oil", 1, "tbsp")),
                List.of("Fry"), 10, 1);
        assertTrue(c.checkIngredients(r).allInStock());
    }

    // ------------------------------------------------------------ recipe + AI

    @Test
    void generateRecipeAddsTopRatedRecipesAsFavourites() {
        ScriptedLLM llm = new ScriptedLLM();
        llm.replies.add(LLMResponse.text("{\"title\":\"Egg Fried Rice\",\"cook_time_minutes\":20,\"portions\":2,"
                + "\"ingredients\":[{\"name\":\"rice\",\"quantity\":1,\"unit\":\"cup\"}],\"steps\":[\"Cook\"]}"));
        AgentController c = controller(llm);
        c.addPantryItem(item("rice", 2, null), false);
        Recipe old = new Recipe("Veggie Curry", List.of(new Ingredient("rice", 1, "")), List.of("Cook"), 30, 2);
        c.saveRecipe(old);
        c.rateRecipe(old.getId(), 5);

        Recipe r = c.generateRecipe(new RecipeRequest(30, 2));

        assertEquals("Egg Fried Rice", r.getTitle());
        assertTrue(llm.prompts.get(0).contains("Veggie Curry"));
    }

    @Test
    void emptyPantryIsReported() {
        AgentController c = controller(new ScriptedLLM());
        assertThrows(PantryEmptyException.class, () -> c.generateRecipe(new RecipeRequest(30, 2)));
    }

    @Test
    void withoutApiKeyAiFeaturesSayUnavailable() {
        AgentController c = controller(null);
        c.addPantryItem(item("rice", 2, null), false);
        assertFalse(c.isAiAvailable());
        assertTrue(c.getAiStatus().startsWith("AI unavailable"));
        assertThrows(LLMUnavailableException.class, () -> c.generateRecipe(new RecipeRequest(30, 2)));
        assertThrows(LLMUnavailableException.class, () -> c.chat("hi"));
    }

    @Test
    void aiStatusShowsRemainingCalls() {
        AgentController c = controller(new ScriptedLLM());
        assertEquals("AI ready (100 of 100 calls left today)", c.getAiStatus());
    }

    @Test
    void chatChangesToCartAreSaved() {
        ScriptedLLM llm = new ScriptedLLM();
        llm.replies.add(LLMResponse.toolCall("cart", "{\"action\":\"add\",\"name\":\"milk\",\"quantity\":2}"));
        llm.replies.add(LLMResponse.text("Added 2 milk."));
        AgentController c = controller(llm);

        assertEquals("Added 2 milk.", c.chat("add 2 milk to my cart"));
        assertEquals(1, c.getLastChatActions().size());
        assertEquals(2, controller(null).getCartItems().get(0).getQuantity());   // saved to disk
    }

    @Test
    void questionnaireProfileIsSaved() {
        ScriptedLLM llm = new ScriptedLLM();
        llm.replies.add(LLMResponse.toolCall("profile", "{\"action\":\"save\",\"diet\":\"halal\"}"));
        llm.replies.add(LLMResponse.text("Saved."));
        AgentController c = controller(llm);
        c.startProfileSetup();
        assertEquals("halal", controller(null).getProfile().getDiet());
    }

    // ------------------------------------------------------------ ingredient status + substitute

    @Test
    void ingredientStatusesMatchThePantry() {
        AgentController c = controller(null);
        c.addPantryItem(item("rice", 3, null), false);
        c.addPantryItem(item("milk", 1, TODAY.plusDays(2)), false);
        c.addPantryItem(item("yogurt", 1, TODAY.minusDays(1)), false);
        c.addPantryItem(item("eggs", 1, null), false);
        Recipe r = new Recipe("Bowl", List.of(new Ingredient("rice", 1, "cup"), new Ingredient("Milk", 1, "cup"),
                new Ingredient("yogurt", 1, ""), new Ingredient("eggs", 2, ""), new Ingredient("soy sauce", 1, "tbsp"),
                new Ingredient("salt", 0, "")), List.of("Cook"), 20, 2);

        assertEquals(List.of(IngredientStatus.IN_PANTRY, IngredientStatus.USE_SOON, IngredientStatus.EXPIRED,
                IngredientStatus.NOT_ENOUGH, IngredientStatus.MISSING, IngredientStatus.BASIC),
                c.getIngredientStatuses(r));
    }

    @Test
    void ingredientsThatNeedBuyingAreExactlyTheMissingOnes() {
        AgentController c = controller(null);
        c.addPantryItem(item("eggs", 1, TODAY.minusDays(3)), false);    // expired and not enough
        c.addPantryItem(item("salt", 1, null), false);
        c.addPantryItem(item("flour", 5, null), false);
        Recipe r = new Recipe("Crepes", List.of(new Ingredient("eggs", 2, ""), new Ingredient("salt", 3, "tsp"),
                new Ingredient("flour", 1, "cup"), new Ingredient("milk", 1, "cup")), List.of("Mix"), 20, 2);

        List<IngredientStatus> statuses = c.getIngredientStatuses(r);
        List<String> needBuying = new ArrayList<>();
        for (int i = 0; i < statuses.size(); i++) {
            if (statuses.get(i).needsBuying()) {
                needBuying.add(r.getIngredients().get(i).getName());
            }
        }
        assertEquals(c.checkIngredients(r).getMissingNames(), needBuying);
        assertEquals(List.of("eggs", "milk"), needBuying);
        assertEquals(IngredientStatus.IN_PANTRY, statuses.get(1));       // a basic never counts as short
    }

    @Test
    void useSubstituteChangesTheRecipeAndSavesItIfInHistory() {
        AgentController c = controller(null);
        Recipe r = new Recipe("Pancakes", List.of(new Ingredient("buttermilk", 1, "cup")),
                List.of("Whisk the buttermilk"), 15, 2);
        c.saveRecipe(r);
        assertTrue(c.useSubstitute(r, "buttermilk", new Substitution("milk with lemon", "", false)));
        assertFalse(c.useSubstitute(r, "cream", new Substitution("milk", "", false)));

        Recipe saved = controller(null).findRecipe(r.getId()).orElseThrow();
        assertEquals("milk with lemon", saved.getIngredients().get(0).getName());
        assertEquals("Whisk the milk with lemon", saved.getSteps().get(0));
    }

    @Test
    void substituteMadeFromPantryItemsCountsAsInThePantry() {
        AgentController c = controller(null);
        c.addPantryItem(item("milk", 1, TODAY.plusDays(1)), false);
        c.addPantryItem(item("lemon", 2, null), false);
        Recipe r = new Recipe("Pancakes", List.of(new Ingredient("buttermilk", 1, "cup"),
                new Ingredient("cream", 1, "cup")), List.of("Mix"), 15, 2);
        c.useSubstitute(r, "buttermilk", new Substitution("milk with lemon", "", true, List.of("milk", "lemon")));
        c.useSubstitute(r, "cream", new Substitution("coconut cream", "", false, List.of("coconut milk")));

        assertEquals(List.of(IngredientStatus.USE_SOON, IngredientStatus.MISSING), c.getIngredientStatuses(r));
        assertEquals(List.of("coconut cream"), c.checkIngredients(r).getMissingNames());

        c.removePantryItemByName("lemon");                              // a part is gone: now missing
        assertEquals(IngredientStatus.MISSING, c.getIngredientStatuses(r).get(0));
        assertEquals(List.of("milk with lemon", "coconut cream"), c.checkIngredients(r).getMissingNames());
    }

    @Test
    void updatePantryItemIsSavedAndAlertsWhenTheNewDateIsClose() {
        List<Notification> got = new ArrayList<>();
        NotificationListener listener = got::add;
        AgentController c = controller(null);
        c.getNotifications().subscribe(listener);
        try {
            c.addPantryItem(item("milk", 1, TODAY.plusDays(10)), false);
            String id = c.getPantryItems().get(0).getId();
            c.updatePantryItem(id, "milk", 2, "L", StorageLocation.FRIDGE, TODAY.plusDays(10));
            assertTrue(got.isEmpty());                                   // date not close
            c.updatePantryItem(id, "milk", 2, "L", StorageLocation.FRIDGE, TODAY.plusDays(1));
            assertEquals(1, got.size());
            c.updatePantryItem(id, "milk", 3, "L", StorageLocation.FRIDGE, TODAY.plusDays(1));
            assertEquals(1, got.size());                                 // same date: no repeat

            PantryItem saved = controller(null).getPantryItems().get(0);
            assertEquals(3, saved.getQuantity());
            assertEquals("L", saved.getUnit());
            assertEquals(TODAY.plusDays(1), saved.getBestBefore());
        } finally {
            c.getNotifications().unsubscribe(listener);
        }
    }

    @Test
    void cartEditClearAndHistoryRemoveAreSaved() {
        AgentController c = controller(null);
        c.addToCart(new CartItem("milk", 1));
        c.addToCart(new CartItem("eggs", 12));
        c.updateCartItem("Milk", 2, "L");
        assertEquals("L", controller(null).getCartItems().get(0).getUnit());
        assertEquals(2, controller(null).getCartItems().get(0).getQuantity());
        c.clearCart();
        assertTrue(controller(null).getCartItems().isEmpty());

        Recipe r = new Recipe("Soup", List.of(new Ingredient("leek", 1, "")), List.of("Boil"), 20, 2);
        c.saveRecipe(r);
        assertEquals(1, c.getHistory().size());
        assertTrue(c.removeRecipe(r.getId()));
        assertFalse(c.removeRecipe(r.getId()));
        assertTrue(controller(null).getHistory().isEmpty());
    }
}
