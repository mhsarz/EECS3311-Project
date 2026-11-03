package com.recipeagent.controller;

import com.recipeagent.agent.AgentAction;
import com.recipeagent.agent.AgentTool;
import com.recipeagent.agent.RecipeAgent;
import com.recipeagent.agent.tools.CartTool;
import com.recipeagent.agent.tools.PantryTool;
import com.recipeagent.agent.tools.ProfileTool;
import com.recipeagent.cart.ShoppingCart;
import com.recipeagent.command.CommandHistory;
import com.recipeagent.command.CookRecipeCommand;
import com.recipeagent.config.AppConfig;
import com.recipeagent.history.RecipeHistory;
import com.recipeagent.llm.ClaudeClient;
import com.recipeagent.llm.LLMClient;
import com.recipeagent.llm.LLMClientProxy;
import com.recipeagent.model.CartItem;
import com.recipeagent.model.IngredientCheck;
import com.recipeagent.model.Notification;
import com.recipeagent.model.PantryItem;
import com.recipeagent.model.Recipe;
import com.recipeagent.model.RecipeRequest;
import com.recipeagent.model.Substitution;
import com.recipeagent.model.UserProfile;
import com.recipeagent.notify.NotificationCenter;
import com.recipeagent.pantry.PantryManager;
import com.recipeagent.prompt.ClaudePromptBuilder;
import com.recipeagent.storage.JsonStorage;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The single entry point the GUI (MainWindow) and the CLI (CLIApp) use.
 * <p>
 * Facade pattern: this is the <b>Facade</b>. It offers one simple method per feature and
 * coordinates the subsystems behind it (agent, pantry, cart, history, profile, undo,
 * notifications, storage), so both interfaces stay thin and depend on one class only.
 * Every method that changes data also saves it, so nothing is lost when the app closes.
 */
public class AgentController {

    static final String PANTRY_KEY = "pantry";
    static final String CART_KEY = "cart";
    static final String HISTORY_KEY = "history";
    static final String PROFILE_KEY = "profile";

    /** Pantry items that expire within this many days get an alert (F05). */
    public static final int EXPIRY_DAYS = 3;
    /** How many top-rated recipes are passed to the agent as favourites (F01). */
    public static final int FAVOURITES_FOR_AGENT = 5;

    private final RecipeAgent agent;
    private final PantryManager pantry;
    private final ShoppingCart cart;
    private final RecipeHistory history;
    private final UserProfile profile;
    private final NotificationCenter notifications;
    private final CommandHistory commands;
    private final JsonStorage storage;
    private final LLMClientProxy llm;
    private final Clock clock;

    /**
     * Wires all parts together. Use {@link #create(AppConfig)} in the app; this constructor
     * lets tests pass in a fake LLM and a temporary storage folder.
     *
     * @param realLlm the real LLM client, or null when there is no API key
     */
    public AgentController(LLMClient realLlm, int dailyLimit, JsonStorage storage, Clock clock) {
        if (storage == null || clock == null) {
            throw new IllegalArgumentException("Storage and clock are required");
        }
        this.storage = storage;
        this.clock = clock;
        this.pantry = new PantryManager(storage.loadList(PANTRY_KEY, PantryItem.class));
        this.cart = new ShoppingCart(storage.loadList(CART_KEY, CartItem.class));
        this.history = new RecipeHistory(storage.loadList(HISTORY_KEY, Recipe.class));
        UserProfile saved = storage.load(PROFILE_KEY, UserProfile.class);
        this.profile = saved != null ? saved : new UserProfile();
        this.notifications = NotificationCenter.getInstance();
        this.commands = new CommandHistory();
        this.llm = new LLMClientProxy(realLlm, dailyLimit, storage, clock);
        List<AgentTool> tools = List.of(
                new PantryTool(pantry, clock), new ProfileTool(profile), new CartTool(cart));
        this.agent = new RecipeAgent(llm, new ClaudePromptBuilder(), tools);
    }

    /** Builds the controller from the app settings (.env). */
    public static AgentController create(AppConfig config) {
        LLMClient real = config.getApiKey()
                .map(key -> (LLMClient) new ClaudeClient(key, config.getModel()))
                .orElse(null);
        return new AgentController(real, config.getDailyLimit(),
                new JsonStorage(config.getDataDir()), Clock.systemDefaultZone());
    }

    // ------------------------------------------------------------ F03 pantry

    /**
     * Adds a pantry item (F03).
     *
     * @param merge false the first time; true after the user chose to merge with the
     *              existing item of the same name
     * @return false if an item with this name already exists and merge was false
     *         (the interface should then ask whether to merge)
     * @throws IllegalArgumentException for an invalid item (e.g. zero quantity)
     */
    public boolean addPantryItem(PantryItem item, boolean merge) {
        if (item == null) {
            throw new IllegalArgumentException("Item must not be null");
        }
        List<PantryItem> existing = pantry.findByName(item.getName());
        PantryItem stored;
        if (existing.isEmpty()) {
            pantry.addItem(item);
            stored = item;
        } else if (merge) {
            if (item.getQuantity() <= 0) {
                throw new IllegalArgumentException("Quantity must be more than zero");
            }
            stored = existing.get(0);
            pantry.mergeQuantity(stored.getId(), item.getQuantity());
        } else {
            return false;
        }
        savePantry();
        // Alert right away if a newly added item is already close to its date (F05).
        // Merging into an existing item does not repeat its alert.
        if (existing.isEmpty() && stored.isExpiringWithin(EXPIRY_DAYS, today())) {
            notifications.publish(Notification.expiry(stored, today()));
        }
        return true;
    }

    /** Removes a pantry item by id. Returns true if it was there. */
    public boolean removePantryItem(String id) {
        boolean removed = pantry.removeItem(id);
        if (removed) {
            savePantry();
        }
        return removed;
    }

    /** Removes a pantry item by name (CLI: pantry remove rice). */
    public boolean removePantryItemByName(String name) {
        boolean removed = pantry.removeByName(name);
        if (removed) {
            savePantry();
        }
        return removed;
    }

    public List<PantryItem> getPantryItems() {
        return pantry.getAll();
    }

    // ------------------------------------------------------------ F05 expiry

    /**
     * Finds items that expire within {@link #EXPIRY_DAYS} days and publishes one expiry
     * alert for each to every subscribed interface (F05).
     *
     * @return the expiring items, soonest first
     */
    public List<PantryItem> checkExpiry() {
        LocalDate today = LocalDate.now(clock);
        List<PantryItem> expiring = pantry.getExpiringItems(EXPIRY_DAYS, today);
        for (PantryItem item : expiring) {
            notifications.publish(Notification.expiry(item, today));
        }
        return expiring;
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    // ------------------------------------------------------------ F01 / F07 / F11 / F06

    /**
     * Generates a recipe (F01). The user's top-rated recipes are added as favourites.
     *
     * @throws com.recipeagent.agent.PantryEmptyException if the pantry is empty and
     *         missing ingredients are not allowed
     * @throws com.recipeagent.llm.LLMException if the AI is unavailable or failed
     */
    public Recipe generateRecipe(RecipeRequest req) {
        req.setFavourites(history.getTopRatedTitles(FAVOURITES_FOR_AGENT));
        return agent.generateRecipe(req);
    }

    /** Suggests up to 3 substitutes for an ingredient (F07). */
    public List<Substitution> suggestSubstitutes(Recipe recipe, String ingredient) {
        return agent.suggestSubstitutes(recipe, ingredient);
    }

    /**
     * Lists the recipe's missing ingredients (F11). Currently compares names exactly;
     * AI name matching (e.g. "scallions" = "green onions") is added with the flyers work.
     */
    public IngredientCheck checkIngredients(Recipe recipe) {
        return pantry.checkRecipe(recipe);
    }

    /**
     * Marks a recipe as cooked (F06): deducts its ingredients from the pantry and records
     * the cook. Can be undone with {@link #undo()}.
     *
     * @return ingredient names that were not in the pantry and so were skipped
     */
    public List<String> markCooked(Recipe recipe) {
        CookRecipeCommand cmd = new CookRecipeCommand(recipe, pantry, history, today());
        commands.run(cmd);
        savePantry();
        saveHistory();
        return cmd.getSkipped();
    }

    /**
     * Undoes the last action (F06).
     *
     * @return a description of what was undone, or empty if there was nothing to undo
     */
    public Optional<String> undo() {
        Optional<String> undone = commands.undo().map(c -> c.getDescription());
        if (undone.isPresent()) {
            savePantry();
            saveHistory();
        }
        return undone;
    }

    public boolean canUndo() {
        return commands.canUndo();
    }

    // ------------------------------------------------------------ F02 cart

    /**
     * Adds an item to the cart, merging with the same name (F02). The sale check against
     * this week's flyers is added with the flyers work.
     */
    public CartItem addToCart(CartItem item) {
        CartItem inCart = cart.add(item);
        saveCart();
        return inCart;
    }

    public boolean removeFromCart(String name) {
        boolean removed = cart.remove(name);
        if (removed) {
            saveCart();
        }
        return removed;
    }

    public List<CartItem> getCartItems() {
        return cart.getItems();
    }

    // ------------------------------------------------------------ F08 history

    /** Saves a recipe to the history (F08). Returns false if it was already saved. */
    public boolean saveRecipe(Recipe r) {
        boolean saved = history.save(r);
        if (saved) {
            saveHistory();
        }
        return saved;
    }

    public void rateRecipe(String id, int stars) {
        history.rate(id, stars);
        saveHistory();
    }

    public boolean toggleFavourite(String id) {
        boolean fav = history.toggleFavourite(id);
        saveHistory();
        return fav;
    }

    public List<Recipe> searchHistory(String query) {
        return history.search(query);
    }

    public Optional<Recipe> findRecipe(String id) {
        return history.findById(id);
    }

    // ------------------------------------------------------------ F04 profile

    public UserProfile getProfile() {
        return profile;
    }

    /**
     * Saves the profile from the form (F04). The values are copied into the shared profile
     * object, which the agent's profile tool also uses.
     */
    public void updateProfile(String diet, List<String> allergies, List<String> dislikes,
                              int defaultPortions) {
        profile.setDefaultPortions(defaultPortions);   // checked first: may throw
        profile.setDiet(diet);
        profile.setAllergies(allergies);
        profile.setDislikes(dislikes);
        saveProfile();
    }

    // ------------------------------------------------------------ F09 chat

    /**
     * Sends a chat message to the agent (F09). Anything the agent changed (cart, profile)
     * is saved afterwards.
     */
    public String chat(String message) {
        try {
            return agent.chat(message);
        } finally {
            saveAfterChat(agent.getLastActions());
        }
    }

    /** Starts the profile questionnaire (F04). */
    public String startProfileSetup() {
        try {
            return agent.startProfileSetup();
        } finally {
            saveAfterChat(agent.getLastActions());
        }
    }

    public List<AgentAction> getLastChatActions() {
        return agent.getLastActions();
    }

    private void saveAfterChat(List<AgentAction> actions) {
        if (actions.stream().anyMatch(a -> a.succeededWith(CartTool.NAME))) {
            saveCart();
        }
        if (actions.stream().anyMatch(a -> a.succeededWith(ProfileTool.NAME))) {
            saveProfile();
        }
    }

    // ------------------------------------------------------------ AI status

    public boolean isAiAvailable() {
        return llm.isAvailable();
    }

    /** Short status for the interface, e.g. "AI ready (98 of 100 calls left today)". */
    public String getAiStatus() {
        String reason = llm.getUnavailableReason();
        if (reason != null) {
            return "AI unavailable: " + reason;
        }
        return "AI ready (" + llm.getRemainingCalls() + " of " + llm.getDailyLimit() + " calls left today)";
    }

    public NotificationCenter getNotifications() {
        return notifications;
    }

    // ------------------------------------------------------------ saving

    private void savePantry() {
        storage.save(PANTRY_KEY, new ArrayList<>(pantry.getAll()));
    }

    private void saveCart() {
        storage.save(CART_KEY, new ArrayList<>(cart.getItems()));
    }

    private void saveHistory() {
        storage.save(HISTORY_KEY, new ArrayList<>(history.getAll()));
    }

    private void saveProfile() {
        storage.save(PROFILE_KEY, profile);
    }
}
