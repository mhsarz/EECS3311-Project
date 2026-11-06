package com.recipeagent.controller;

import com.recipeagent.agent.AgentTool;
import com.recipeagent.agent.PantryEmptyException;
import com.recipeagent.llm.LLMClient;
import com.recipeagent.llm.LLMResponse;
import com.recipeagent.llm.LLMUnavailableException;
import com.recipeagent.model.CartItem;
import com.recipeagent.model.Ingredient;
import com.recipeagent.model.IngredientCheck;
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
import java.util.Optional;

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

    // ------------------------------------------------------------ F10 flyers + F02 sales + F11 names

    private static final String MILK_FLYER = "{\"end_date\":\"2026-11-10\",\"items\":["
            + "{\"name\":\"2% Milk 4L\",\"price\":5.49},{\"name\":\"Large Eggs 12\",\"price\":3.99}]}";

    private AgentController controller(LLMClient llm, int limit) {
        return new AgentController(llm, limit, new JsonStorage(tmp), CLOCK);
    }

    private Path flyerFile() throws java.io.IOException {
        Path f = tmp.resolve("nofrills.txt");
        java.nio.file.Files.writeString(f, "No Frills flyer: 2% Milk 4L $5.49, Large Eggs 12 $3.99");
        return f;
    }

    @Test
    void uploadFlyerSendsAPdfToTheAi() throws Exception {
        List<List<com.recipeagent.llm.Attachment>> files = new ArrayList<>();
        ScriptedLLM llm = new ScriptedLLM() {
            @Override
            public LLMResponse generate(String prompt, List<AgentTool> tools, List<com.recipeagent.llm.Attachment> f) {
                files.add(f);
                return generate(prompt, tools);
            }
        };
        llm.replies.add(LLMResponse.text(MILK_FLYER));
        AgentController c = controller(llm);
        Path pdf = tmp.resolve("No Frills flyer.pdf");
        java.nio.file.Files.write(pdf, "%PDF-1.7 two pages of pictures".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        FlyerRefresh r = c.uploadFlyer(pdf, "No Frills");
        assertEquals(1, files.size());
        assertEquals("No Frills flyer.pdf", files.get(0).get(0).getName());
        assertEquals(2, r.getUpdated().get(0).getItems().size());
        assertTrue(llm.prompts.get(0).contains("attached PDF flyer"));

        Path locked = tmp.resolve("locked.pdf");
        java.nio.file.Files.write(locked, "%PDF-1.4 /Encrypt 3 0 R".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        assertThrows(com.recipeagent.flyer.FlyerException.class, () -> c.uploadFlyer(locked, "Metro"));
        assertEquals(1, files.size());                                          // nothing was sent
    }

    @Test
    void longPdfNeedsEnoughCallsLeftAndReportsProgress() throws Exception {
        StringBuilder sb = new StringBuilder("%PDF-1.7\n");
        for (int i = 0; i < 24; i++) {
            sb.append("<< /Type /Page >>\n");
        }
        Path pdf = tmp.resolve("No Frills.pdf");
        java.nio.file.Files.writeString(pdf, sb.toString());

        List<String> prompts = new ArrayList<>();
        LLMClient llm = new LLMClient() {
            @Override
            public LLMResponse generate(String prompt, List<AgentTool> tools) {
                return LLMResponse.text("{\"matches\":[]}");
            }

            @Override
            public LLMResponse generate(String prompt, List<AgentTool> tools, List<com.recipeagent.llm.Attachment> f) {
                prompts.add(prompt);
                return LLMResponse.text("{\"page_count\":24,\"items\":[{\"name\":\"Item " + prompts.size()
                        + "\",\"price\":1." + prompts.size() + "}]}");
            }
        };
        AgentController few = controller(llm, 5);                               // 24 pages need 6 calls
        LLMUnavailableException e = assertThrows(LLMUnavailableException.class, () -> few.uploadFlyer(pdf, "No Frills"));
        assertTrue(e.getMessage().contains("24-page PDF needs about 6 AI calls, but only 5 are left today"));
        assertTrue(prompts.isEmpty());                                          // nothing was sent

        AgentController c = controller(llm, 100);
        List<String> progress = new ArrayList<>();
        FlyerRefresh r = c.uploadFlyer(pdf, "No Frills", progress::add);
        assertEquals(6, prompts.size());
        assertEquals(6, r.getUpdated().get(0).getItems().size());
        assertEquals("Reading pages 1-4 of 24…", progress.get(0));
        assertEquals("Reading pages 21-24 of 24…", progress.get(5));

        progress.clear();
        c.refreshFlyers(progress::add);                                        // the uploaded file is read again
        assertEquals("No Frills: Reading pages 1-4 of 24…", progress.get(0));
    }

    @Test
    void uploadFlyerReadsTheFileSavesItAndChecksTheCart() throws Exception {
        ScriptedLLM llm = new ScriptedLLM();
        llm.replies.add(LLMResponse.text(MILK_FLYER));
        llm.replies.add(LLMResponse.text("{\"matches\":[{\"cart_item\":\"milk\",\"flyer_item_id\":1}]}"));
        AgentController c = controller(llm);
        c.addToCart(new CartItem("milk", 1));
        List<Notification> got = new ArrayList<>();
        NotificationListener listener = got::add;
        c.getNotifications().subscribe(listener);
        try {
            FlyerRefresh r = c.uploadFlyer(flyerFile(), "no frills");
            assertEquals(1, r.getUpdated().size());
            assertEquals(1, r.getSales().getMatches().size());
            assertTrue(llm.prompts.get(0).contains("Large Eggs 12 $3.99"));      // the file text was sent

            CartItem milk = c.getCartItems().get(0);
            assertEquals("No Frills", milk.getSaleStore());
            assertEquals(5.49, milk.getSalePrice().doubleValue());
            assertEquals("2% Milk 4L", milk.getSaleItemName());
            assertEquals(1, got.size());
            assertEquals(NotificationType.SALE, got.get(0).getType());
            assertTrue(c.isStoreSelected("No Frills"));

            AgentController reopened = controller(null);                          // saved to disk
            assertEquals(2, reopened.getFlyers().get(0).getItems().size());
            assertEquals("No Frills", reopened.getCartItems().get(0).getSaleStore());

            llm.replies.add(LLMResponse.text("{\"matches\":[{\"cart_item\":\"milk\",\"flyer_item_id\":1}]}"));
            c.checkSales(List.of("milk"));
            assertEquals(1, got.size());                                          // same sale: no second alert
        } finally {
            c.getNotifications().unsubscribe(listener);
        }
    }

    @Test
    void salesAreNotCheckedWithoutFlyersOrWithoutAi() throws Exception {
        ScriptedLLM llm = new ScriptedLLM();
        AgentController c = controller(llm, 2);
        c.addToCart(new CartItem("milk", 1));
        SaleCheck none = c.checkSales(List.of("milk", "not in cart"));
        assertFalse(none.wasRun());
        assertEquals(List.of("milk"), none.getChecked());
        assertTrue(none.getSkippedReason().startsWith("No flyers for this week"));
        assertTrue(llm.prompts.isEmpty());                                        // no AI call for nothing

        llm.replies.add(LLMResponse.text(MILK_FLYER));
        llm.replies.add(LLMResponse.text("{\"matches\":[]}"));
        c.uploadFlyer(flyerFile(), "No Frills");                                 // uses the 2 calls of the day
        SaleCheck noAi = c.checkSales(List.of("milk"));
        assertTrue(noAi.getSkippedReason().contains("AI is unavailable"));
        assertTrue(c.checkSales(List.of()).wasRun());                             // nothing to check is fine
    }

    @Test
    void refreshReportsStoresThatNeedAnUploadAndKeepsOldFlyersWithoutAi() throws Exception {
        ScriptedLLM llm = new ScriptedLLM();
        AgentController c = controller(llm);
        assertThrows(IllegalStateException.class, c::refreshFlyers);             // no store picked
        c.setStoreSelected("Metro", true);
        FlyerRefresh r = c.refreshFlyers();                                       // online flyers not set up
        assertTrue(r.getUpdated().isEmpty());
        assertTrue(r.isUploadSuggested());
        assertTrue(r.getProblems().get("Metro").contains("upload the Metro flyer"));
        assertTrue(llm.prompts.isEmpty());
        assertFalse(c.isOnlineFlyersSetUp());

        AgentController noAi = controller(null);
        assertThrows(LLMUnavailableException.class, noAi::refreshFlyers);

        llm.replies.add(LLMResponse.text(MILK_FLYER));
        c.uploadFlyer(flyerFile(), "No Frills");
        llm.replies.add(LLMResponse.text(MILK_FLYER));
        FlyerRefresh again = c.refreshFlyers();                                   // No Frills re-reads its file
        assertEquals(List.of("No Frills"), again.getUpdated().stream().map(f -> f.getStore()).toList());
        assertTrue(again.getProblems().containsKey("Metro"));
    }

    @Test
    void itemsTheChatAddsAreCheckedForSales() throws Exception {
        ScriptedLLM llm = new ScriptedLLM();
        AgentController c = controller(llm);
        llm.replies.add(LLMResponse.text(MILK_FLYER));
        c.uploadFlyer(flyerFile(), "No Frills");                                 // cart empty: no sale check call
        assertEquals(1, llm.prompts.size());

        llm.replies.add(LLMResponse.toolCall("cart", "{\"action\":\"add\",\"name\":\"eggs\",\"quantity\":12}"));
        llm.replies.add(LLMResponse.text("Added a dozen eggs."));
        llm.replies.add(LLMResponse.text("{\"matches\":[{\"cart_item\":\"eggs\",\"flyer_item_id\":2}]}"));
        c.chat("add a dozen eggs");
        CartItem eggs = c.getCartItems().get(0);
        assertTrue(eggs.isOnSale());
        assertEquals(3.99, eggs.getSalePrice().doubleValue());
        assertEquals(com.recipeagent.model.CartSource.CHAT, eggs.getSource());
        assertTrue(llm.prompts.get(3).contains("<shopping_list>\neggs"));
    }

    @Test
    void saleMarksFromAnEndedFlyerAreClearedWhenTheAppStarts() throws Exception {
        ScriptedLLM llm = new ScriptedLLM();
        llm.replies.add(LLMResponse.text(MILK_FLYER));                           // ends Nov 10
        llm.replies.add(LLMResponse.text("{\"matches\":[{\"cart_item\":\"milk\",\"flyer_item_id\":1}]}"));
        AgentController c = controller(llm);
        c.addToCart(new CartItem("milk", 1));
        c.uploadFlyer(flyerFile(), "No Frills");
        assertTrue(c.getCartItems().get(0).isOnSale());

        Clock later = Clock.fixed(Instant.parse("2026-11-12T12:00:00Z"), ZoneOffset.UTC);
        AgentController nextWeek = new AgentController(null, 100, new JsonStorage(tmp), later);
        assertFalse(nextWeek.getCartItems().get(0).isOnSale());
        assertTrue(nextWeek.getCurrentFlyers().isEmpty());
        assertEquals(1, nextWeek.getFlyers().size());                            // kept, but not current
    }

    @Test
    void ingredientsAreMatchedToPantryItemsWithAnotherName() {
        ScriptedLLM llm = new ScriptedLLM();
        llm.replies.add(LLMResponse.text("{\"matches\":[{\"ingredient\":\"scallions\",\"pantry_item\":\"green onions\"}]}"));
        AgentController c = controller(llm);
        c.addPantryItem(item("green onions", 2, null), false);
        c.addPantryItem(item("rice", 5, null), false);
        Recipe r = new Recipe("Fried rice", List.of(new Ingredient("rice", 1, "cup"), new Ingredient("scallions", 1, ""),
                new Ingredient("saffron", 1, "pinch"), new Ingredient("salt", 0, "")), List.of("Cook"), 20, 2);

        assertEquals(IngredientStatus.MISSING, c.getIngredientStatuses(r).get(1));   // not asked yet: no AI call
        assertTrue(llm.prompts.isEmpty());
        assertEquals(List.of("saffron"), c.checkIngredients(r).getMissingNames());
        assertEquals(1, llm.prompts.size());
        assertTrue(llm.prompts.get(0).contains("scallions\nsaffron"));              // rice and salt are not asked
        assertEquals(IngredientStatus.IN_PANTRY, c.getIngredientStatuses(r).get(1));
        assertEquals(Optional.of("green onions"), c.getPantryMatch("Scallions"));
        assertEquals(Optional.empty(), c.getPantryMatch("rice"));

        c.checkIngredients(r);
        assertEquals(1, llm.prompts.size());                                     // answers are remembered

        c.markCooked(r);
        double left = c.getPantryItems().stream().filter(p -> p.hasName("green onions")).findFirst().orElseThrow().getQuantity();
        assertEquals(1, left);                                                    // the matched item was used
        c.undo();
        assertEquals(2, c.getPantryItems().stream().filter(p -> p.hasName("green onions")).findFirst().orElseThrow().getQuantity());

        Recipe more = new Recipe("Big bowl", List.of(new Ingredient("scallions", 3, "")), List.of("Cook"), 20, 2);
        IngredientCheck check = c.checkIngredients(more);
        assertEquals(1, check.getMissing().get(0).getQuantity());                // needs 3, has 2
        assertEquals(IngredientStatus.NOT_ENOUGH, c.getIngredientStatuses(more).get(0));

        c.addPantryItem(item("saffron threads", 1, null), false);                // a new item: ask about saffron again
        llm.replies.add(LLMResponse.text("{\"matches\":[{\"ingredient\":\"saffron\",\"pantry_item\":\"saffron threads\"}]}"));
        assertTrue(c.checkIngredients(r).allInStock());
        assertEquals(2, llm.prompts.size());
    }

    @Test
    void withoutAiOnlyExactNamesAreUsed() {
        AgentController c = controller(null);
        c.addPantryItem(item("green onions", 2, null), false);
        Recipe r = new Recipe("Soup", List.of(new Ingredient("scallions", 1, "")), List.of("Cook"), 20, 2);
        assertEquals(List.of("scallions"), c.checkIngredients(r).getMissingNames());   // UC11 2a
    }
}
