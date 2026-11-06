package com.recipeagent.controller;

import com.recipeagent.agent.AgentAction;
import com.recipeagent.agent.AgentTool;
import com.recipeagent.agent.RecipeAgent;
import com.recipeagent.agent.tools.CartTool;
import com.recipeagent.agent.tools.FlyerTool;
import com.recipeagent.agent.tools.PantryTool;
import com.recipeagent.agent.tools.ProfileTool;
import com.recipeagent.cart.ShoppingCart;
import com.recipeagent.command.CommandHistory;
import com.recipeagent.command.CookRecipeCommand;
import com.recipeagent.config.AppConfig;
import com.recipeagent.flyer.FileFlyerSource;
import com.recipeagent.flyer.FlyerException;
import com.recipeagent.flyer.FlyerService;
import com.recipeagent.flyer.FlyerSource;
import com.recipeagent.flyer.OnlineFlyerSource;
import com.recipeagent.history.RecipeHistory;
import com.recipeagent.llm.ClaudeClient;
import com.recipeagent.llm.LLMClient;
import com.recipeagent.llm.LLMClientProxy;
import com.recipeagent.llm.LLMException;
import com.recipeagent.llm.LLMUnavailableException;
import com.recipeagent.model.CartItem;
import com.recipeagent.model.Flyer;
import com.recipeagent.model.Ingredient;
import com.recipeagent.model.IngredientCheck;
import com.recipeagent.model.IngredientStatus;
import com.recipeagent.model.Notification;
import com.recipeagent.model.PantryItem;
import com.recipeagent.model.Recipe;
import com.recipeagent.model.RecipeRequest;
import com.recipeagent.model.SaleMatch;
import com.recipeagent.model.StorageLocation;
import com.recipeagent.model.Substitution;
import com.recipeagent.model.UserProfile;
import com.recipeagent.notify.NotificationCenter;
import com.recipeagent.pantry.PantryManager;
import com.recipeagent.prompt.ClaudePromptBuilder;
import com.recipeagent.storage.JsonStorage;

import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The single entry point the GUI (MainWindow) and the CLI (CLIApp) use.
 * <p>
 * Facade pattern: this is the <b>Facade</b>. It offers one simple method per feature and
 * coordinates the subsystems behind it (agent, pantry, cart, flyers, history, profile, undo,
 * notifications, storage), so both interfaces stay thin and depend on one class only.
 * Every method that changes data also saves it, so nothing is lost when the app closes.
 */
public class AgentController {

    static final String PANTRY_KEY = "pantry";
    static final String CART_KEY = "cart";
    static final String HISTORY_KEY = "history";
    static final String PROFILE_KEY = "profile";
    static final String FLYERS_KEY = "flyers";

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
    private final FlyerService flyers;
    /**
     * Ingredient name (lower case) -> pantry item it is stored as, from the agent's name
     * matching (F11); "" means the agent found no match. Asked once per name.
     */
    private final Map<String, String> nameMatches = new HashMap<>();

    /**
     * Wires all parts together. Use {@link #create(AppConfig)} in the app; this constructor
     * lets tests pass in a fake LLM and a temporary storage folder.
     *
     * @param realLlm the real LLM client, or null when there is no API key
     */
    public AgentController(LLMClient realLlm, int dailyLimit, JsonStorage storage, Clock clock) {
        this(realLlm, dailyLimit, storage, clock, new OnlineFlyerSource(null));
    }

    /** Same as above with the source used for online flyers (F10). */
    public AgentController(LLMClient realLlm, int dailyLimit, JsonStorage storage, Clock clock,
                           FlyerSource onlineFlyers) {
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
        this.flyers = new FlyerService(onlineFlyers, storage.load(FLYERS_KEY, FlyerService.Data.class));
        this.llm = new LLMClientProxy(realLlm, dailyLimit, storage, clock);
        List<AgentTool> tools = List.of(new PantryTool(pantry, clock), new ProfileTool(profile),
                new CartTool(cart), new FlyerTool(flyers, clock));
        this.agent = new RecipeAgent(llm, new ClaudePromptBuilder(), tools);
        clearEndedSales();
    }

    /** Builds the controller from the app settings (.env). */
    public static AgentController create(AppConfig config) {
        LLMClient real = config.getApiKey()
                .map(key -> (LLMClient) new ClaudeClient(key, config.getModel()))
                .orElse(null);
        return new AgentController(real, config.getDailyLimit(), new JsonStorage(config.getDataDir()),
                Clock.systemDefaultZone(), new OnlineFlyerSource(config.getFlyerUrlTemplate().orElse(null)));
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
        forgetNoMatches();
        // Alert right away if a newly added item is already close to its date (F05).
        // Merging into an existing item does not repeat its alert.
        if (existing.isEmpty() && stored.isExpiringWithin(EXPIRY_DAYS, today())) {
            notifications.publish(Notification.expiry(stored, today()));
        }
        return true;
    }

    /**
     * Changes a pantry item (F03 Edit). If the new best-before date is within
     * {@link #EXPIRY_DAYS} days and the date was changed, an expiry alert is sent (F05).
     *
     * @throws IllegalArgumentException for invalid values or a name another item already has
     */
    public PantryItem updatePantryItem(String id, String name, double quantity, String unit,
                                       StorageLocation location, LocalDate bestBefore) {
        LocalDate oldDate = pantry.findById(id).map(PantryItem::getBestBefore).orElse(null);
        PantryItem updated = pantry.updateItem(id, name, quantity, unit, location, bestBefore);
        savePantry();
        forgetNoMatches();
        boolean dateChanged = !Objects.equals(oldDate, bestBefore);
        if (dateChanged && updated.isExpiringWithin(EXPIRY_DAYS, today())) {
            notifications.publish(Notification.expiry(updated, today()));
        }
        return updated;
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

    /**
     * Items that expire within {@link #EXPIRY_DAYS} days but have not expired yet, soonest
     * first: the food to cook first (F05 "Suggest Recipes"). Expired food is left out on
     * purpose, so the agent is never asked to cook with it. Publishes no alerts.
     */
    public List<PantryItem> getItemsToUseSoon() {
        LocalDate today = LocalDate.now(clock);
        List<PantryItem> result = new ArrayList<>();
        for (PantryItem item : pantry.getExpiringItems(EXPIRY_DAYS, today)) {
            if (item.daysUntilExpiry(today) >= 0) {
                result.add(item);
            }
        }
        return result;
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
     * Lists the recipe's missing ingredients (F11). The pantry is compared by exact name and
     * quantity first; for names with no exact match the agent asks the LLM whether a pantry
     * item is the same food ("scallions" = "green onions"), once per name. If the AI is
     * unavailable, only exact names are used. Basics such as salt and water are not counted
     * (see RecipeAgent.BASIC_STAPLES), nor a substitute whose pantry items are all at home (F07).
     * May call the LLM, so the GUI runs it in the background.
     */
    public IngredientCheck checkIngredients(Recipe recipe) {
        matchUnknownNames(recipe);
        IngredientCheck check = pantry.checkRecipe(recipe);
        for (Ingredient ing : recipe.getIngredients()) {
            String name = ing.getName();
            if (!check.getMissingNames().stream().anyMatch(n -> n.equalsIgnoreCase(name))) {
                continue;
            }
            List<String> parts = recipe.getSubstituteParts(name);
            boolean substituteAtHome = !parts.isEmpty() && pantry.findByName(name).isEmpty()
                    && substituteInPantry(parts);
            if (RecipeAgent.isBasicStaple(name) || substituteAtHome) {
                check.markFound(name);
                continue;
            }
            Optional<PantryItem> other = matchedPantryItem(name);
            if (other.isPresent() && pantry.findByName(name).isEmpty()) {
                double have = other.get().getQuantity();
                if (have >= ing.getQuantity()) {
                    check.markFound(name);
                } else {
                    check.replaceMissing(name, new Ingredient(name, ing.getQuantity() - have, ing.getUnit()));
                }
            }
        }
        return check;
    }

    /**
     * The pantry item an ingredient was matched to by name (F11), e.g. "green onions" for
     * "scallions"; empty if it has its own name in the pantry or no match is known.
     */
    public Optional<String> getPantryMatch(String ingredient) {
        if (ingredient == null || !pantry.findByName(ingredient).isEmpty()) {
            return Optional.empty();
        }
        return matchedPantryItem(ingredient).map(PantryItem::getName);
    }

    /** Asks the agent about ingredient names with no exact pantry match that were not asked before. */
    private void matchUnknownNames(Recipe recipe) {
        if (recipe == null) {
            throw new IllegalArgumentException("Recipe must not be null");
        }
        List<String> unknown = new ArrayList<>();
        for (Ingredient ing : recipe.getIngredients()) {
            String name = ing.getName();
            String key = key(name);
            if (pantry.findByName(name).isEmpty() && !RecipeAgent.isBasicStaple(name)
                    && recipe.getSubstituteParts(name).isEmpty() && !nameMatches.containsKey(key)
                    && unknown.stream().noneMatch(u -> u.equalsIgnoreCase(name))) {
                unknown.add(name);
            }
        }
        List<String> pantryNames = pantry.getAll().stream().map(PantryItem::getName).toList();
        if (unknown.isEmpty() || pantryNames.isEmpty() || !llm.isAvailable()) {
            return;                                              // UC11 2a: exact names only
        }
        try {
            Map<String, String> found = agent.matchNames(unknown, pantryNames);
            for (String name : unknown) {
                nameMatches.put(key(name), found.getOrDefault(name, ""));
            }
        } catch (LLMException e) {
            // keep exact matching; the names are asked again next time
        }
    }

    private Optional<PantryItem> matchedPantryItem(String ingredient) {
        String match = nameMatches.get(key(ingredient));
        if (match == null || match.isEmpty()) {
            return Optional.empty();
        }
        return pantry.findByName(match).stream().findFirst();
    }

    /** A new pantry item may match a name the agent could not match before, so ask again. */
    private void forgetNoMatches() {
        nameMatches.values().removeIf(String::isEmpty);
    }

    private static String key(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * The pantry status of each ingredient, in recipe order (F01, F11): in the pantry, use
     * soon, expired, not enough, missing, or a basic such as salt. The ones that "need buying"
     * are exactly the ones {@link #checkIngredients(Recipe)} lists as missing.
     */
    public List<IngredientStatus> getIngredientStatuses(Recipe recipe) {
        if (recipe == null) {
            throw new IllegalArgumentException("Recipe must not be null");
        }
        LocalDate today = today();
        List<IngredientStatus> result = new ArrayList<>();
        for (Ingredient ing : recipe.getIngredients()) {
            List<String> parts = recipe.getSubstituteParts(ing.getName());
            if (!parts.isEmpty() && pantry.findByName(ing.getName()).isEmpty()) {
                result.add(substituteStatus(parts, today));
                continue;
            }
            List<PantryItem> found = pantry.findByName(ing.getName());
            if (found.isEmpty()) {
                found = matchedPantryItem(ing.getName()).map(List::of).orElse(List.of());
            }
            boolean basic = RecipeAgent.isBasicStaple(ing.getName());
            if (found.isEmpty()) {
                result.add(basic ? IngredientStatus.BASIC : IngredientStatus.MISSING);
                continue;
            }
            PantryItem item = found.get(0);
            if (!basic && item.getQuantity() < ing.getQuantity()) {
                result.add(IngredientStatus.NOT_ENOUGH);       // same rule as checkIngredients
            } else if (item.hasBestBefore() && item.daysUntilExpiry(today) < 0) {
                result.add(IngredientStatus.EXPIRED);
            } else if (item.isExpiringWithin(EXPIRY_DAYS, today)) {
                result.add(IngredientStatus.USE_SOON);
            } else {
                result.add(IngredientStatus.IN_PANTRY);
            }
        }
        return result;
    }

    /**
     * Status of a substitute made from pantry items: missing if one of them is gone, else
     * the most urgent date among them. Amounts are not compared (the agent gives no amounts).
     */
    private IngredientStatus substituteStatus(List<String> parts, LocalDate today) {
        IngredientStatus status = IngredientStatus.IN_PANTRY;
        for (String part : parts) {
            List<PantryItem> found = pantry.findByName(part);
            if (found.isEmpty()) {
                return IngredientStatus.MISSING;
            }
            PantryItem item = found.get(0);
            if (item.hasBestBefore() && item.daysUntilExpiry(today) < 0) {
                status = IngredientStatus.EXPIRED;
            } else if (status == IngredientStatus.IN_PANTRY && item.isExpiringWithin(EXPIRY_DAYS, today)) {
                status = IngredientStatus.USE_SOON;
            }
        }
        return status;
    }

    /** True if every pantry item a substitute is made from is in the pantry. */
    private boolean substituteInPantry(List<String> parts) {
        return parts.stream().allMatch(part -> !pantry.findByName(part).isEmpty());
    }

    /**
     * Uses a substitute instead of an ingredient in this recipe (F07 "Use substitute").
     * If the recipe is in the history, the change is saved there too.
     *
     * @return false if the recipe has no such ingredient
     */
    public boolean useSubstitute(Recipe recipe, String ingredient, Substitution substitute) {
        if (recipe == null || substitute == null) {
            throw new IllegalArgumentException("Recipe and substitute are required");
        }
        boolean changed = recipe.replaceIngredient(ingredient, substitute.getSubstitute(),
                substitute.getPantryItems());
        if (changed && history.contains(recipe.getId())) {
            saveHistory();
        }
        return changed;
    }

    /**
     * Marks a recipe as cooked (F06): deducts its ingredients from the pantry and records
     * the cook. Can be undone with {@link #undo()}.
     *
     * @return ingredient names that were not in the pantry and so were skipped
     */
    public List<String> markCooked(Recipe recipe) {
        Map<String, String> pantryNames = new HashMap<>();
        for (Ingredient ing : recipe.getIngredients()) {
            getPantryMatch(ing.getName()).ifPresent(p -> pantryNames.put(key(ing.getName()), p));
        }
        CookRecipeCommand cmd = new CookRecipeCommand(recipe, pantry, history, today(), pantryNames);
        commands.run(cmd);
        savePantry();
        saveHistory();
        return cmd.getSkipped();
    }

    /**
     * What {@link #markCooked} would take out of the pantry, without changing anything (F06).
     * It uses the same names as markCooked: an exact pantry name, else the one the agent matched.
     */
    public CookPreview previewCooked(Recipe recipe) {
        if (recipe == null) {
            throw new IllegalArgumentException("Recipe must not be null");
        }
        Map<String, Double> left = new HashMap<>();
        List<CookPreview.Line> lines = new ArrayList<>();
        for (Ingredient ing : recipe.getIngredients()) {
            PantryItem item = pantry.findByName(ing.getName()).stream().findFirst()
                    .orElse(matchedPantryItem(ing.getName()).orElse(null));
            if (item == null) {
                lines.add(new CookPreview.Line(ing.getName(), null, ing.getQuantity(), ing.getUnit(), 0, 0, ""));
                continue;
            }
            // An ingredient listed twice uses up the same pantry item twice.
            double before = left.getOrDefault(key(item.getName()), item.getQuantity());
            double after = Math.max(0, before - ing.getQuantity());
            left.put(key(item.getName()), after);
            lines.add(new CookPreview.Line(ing.getName(), item.getName(), ing.getQuantity(), ing.getUnit(),
                    before, after, item.getUnit()));
        }
        return new CookPreview(lines);
    }

    /**
     * True if this recipe was never cooked or rated (it may not be in the history yet), so
     * the app asks for a rating after Mark as Cooked (F06, F08). Asked only this first time.
     */
    public boolean isFirstCook(Recipe recipe) {
        if (recipe == null) {
            return false;
        }
        return history.findById(recipe.getId())
                .map(r -> r.getTimesCooked() == 0 && r.getRating() == 0)
                .orElse(true);
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
     * Adds an item to the cart, merging with the same name (F02). This does not call the AI;
     * run {@link #checkSales} (or findSales + applySales) for the item afterwards to look for
     * it in this week's flyers.
     */
    public CartItem addToCart(CartItem item) {
        CartItem inCart = cart.add(item);
        saveCart();
        return inCart;
    }

    /** Adds several items at once, e.g. a recipe's missing ingredients (F11), saving once. */
    public List<CartItem> addToCart(List<CartItem> items) {
        List<CartItem> result = new ArrayList<>();
        for (CartItem i : items) {
            result.add(cart.add(i));
        }
        saveCart();
        return result;
    }

    /**
     * Checks cart items against this week's flyers and marks the ones on sale, sending a sale
     * alert for each new sale (F02). Skipped (with a reason) when there are no current flyers
     * or the AI is unavailable; the cart items are kept either way.
     */
    public SaleCheck checkSales(Collection<String> cartItemNames) {
        SaleCheck check = findSales(cartItemNames);
        applySales(check);
        return check;
    }

    /**
     * The slow part of {@link #checkSales}: asks the agent which items are on sale, without
     * changing anything. The GUI runs this in the background and then calls applySales.
     */
    public SaleCheck findSales(Collection<String> cartItemNames) {
        List<String> names = new ArrayList<>();
        if (cartItemNames != null) {
            for (String n : cartItemNames) {
                cart.find(n).ifPresent(i -> {
                    if (!names.contains(i.getName())) {
                        names.add(i.getName());
                    }
                });
            }
        }
        if (names.isEmpty()) {
            return new SaleCheck(names, List.of(), null);
        }
        List<Flyer> current = flyers.getCurrentFlyers(today());
        if (current.isEmpty()) {
            return SaleCheck.skipped(names, "No flyers for this week yet, so sales were not checked.");
        }
        if (!llm.isAvailable()) {
            return SaleCheck.skipped(names, "Sales were not checked because the AI is unavailable.");
        }
        try {
            return new SaleCheck(names, agent.matchSales(names, current), null);
        } catch (LLMException e) {
            return SaleCheck.skipped(names, "Sales were not checked: " + e.getMessage());
        }
    }

    /** Marks the checked cart items as on sale or not, and alerts about new sales (F02). */
    public void applySales(SaleCheck check) {
        if (check == null || !check.wasRun()) {
            return;                                              // keep what was known before
        }
        for (String name : check.getChecked()) {
            Optional<CartItem> item = cart.find(name);
            if (item.isEmpty()) {
                continue;                                        // removed meanwhile
            }
            CartItem c = item.get();
            Optional<SaleMatch> match = check.getMatches().stream()
                    .filter(m -> m.getCartItem().equalsIgnoreCase(name)).findFirst();
            if (match.isPresent()) {
                SaleMatch m = match.get();
                boolean same = c.isOnSale() && m.getStore().equals(c.getSaleStore())
                        && m.getFlyerItem().getName().equals(c.getSaleItemName())
                        && Objects.equals(m.getFlyerItem().getSalePrice(), c.getSalePrice());
                c.setSale(m.getStore(), m.getFlyerItem());
                if (!same) {
                    notifications.publish(Notification.sale(c, m.getFlyerItem(), m.getStore()));
                }
            } else {
                c.clearSale();
            }
        }
        saveCart();
    }

    /** Clears sale marks whose store has no current flyer any more (the sale ended). */
    private void clearEndedSales() {
        boolean changed = false;
        for (CartItem c : cart.getItems()) {
            if (!c.isOnSale()) {
                continue;
            }
            String store = c.getSaleStore();
            boolean running = store != null && flyers.getFlyer(store).map(f -> f.isCurrent(today())).orElse(false);
            if (!running) {
                c.clearSale();
                changed = true;
            }
        }
        if (changed) {
            saveCart();
        }
    }

    private List<String> cartNames() {
        return cart.getItems().stream().map(CartItem::getName).toList();
    }

    public boolean removeFromCart(String name) {
        boolean removed = cart.remove(name);
        if (removed) {
            saveCart();
        }
        return removed;
    }

    /** Changes the quantity and unit of a cart item (F02 Edit). */
    public CartItem updateCartItem(String name, double quantity, String unit) {
        CartItem item = cart.update(name, quantity, unit);
        saveCart();
        return item;
    }

    /** Empties the cart (F02 "Clear cart"). */
    public void clearCart() {
        cart.clear();
        saveCart();
    }

    public List<CartItem> getCartItems() {
        return cart.getItems();
    }

    // ------------------------------------------------------------ F10 flyers

    /** Every store in the list (F10). */
    public List<String> getFlyerStores() {
        return flyers.getStores();
    }

    /** The stores whose flyers Refresh reads. */
    public List<String> getSelectedStores() {
        return flyers.getSelectedStores();
    }

    public boolean isStoreSelected(String store) {
        return flyers.isSelected(store);
    }

    public void setStoreSelected(String store, boolean selected) {
        if (flyers.setSelected(store, selected)) {
            saveFlyers();
        }
    }

    /** Adds a store to the list and selects it; returns its name as stored. */
    public String addFlyerStore(String store) {
        String name = flyers.addStore(store);
        saveFlyers();
        return name;
    }

    /** True if FLYER_URL_TEMPLATE is set, so Refresh can read flyers from the web. */
    public boolean isOnlineFlyersSetUp() {
        FlyerSource online = flyers.getOnlineSource();
        return !(online instanceof OnlineFlyerSource) || ((OnlineFlyerSource) online).isConfigured();
    }

    /** Every saved flyer, also ones whose sale ended. */
    public List<Flyer> getFlyers() {
        return flyers.getFlyers();
    }

    /** Flyers whose sale is still running. */
    public List<Flyer> getCurrentFlyers() {
        return flyers.getCurrentFlyers(today());
    }

    /**
     * Upload Flyer (F10): reads a flyer file for a store (added to the list if new), asks the
     * agent for its sale items, saves the flyer and checks the cart for sales (F02).
     *
     * @throws FlyerException          if the file can't be used (missing, empty, not text)
     * @throws LLMUnavailableException if the AI is unavailable (the old flyer is kept)
     * @throws LLMException            if the AI could not read the flyer
     */
    public FlyerRefresh uploadFlyer(Path file, String store) throws FlyerException {
        String name = flyers.addStore(store);
        flyers.setSource(name, new FileFlyerSource(file));
        saveFlyers();
        String raw = flyers.fetchRaw(name);
        Flyer flyer = agent.extractFlyer(raw, name, today());
        flyers.save(flyer);
        saveFlyers();
        clearEndedSales();
        return new FlyerRefresh(List.of(flyer), Map.of(), false, checkSales(cartNames()));
    }

    /**
     * Refresh Flyers (F10): for each selected store, gets the flyer text (an uploaded file or
     * the online source), asks the agent for its sale items and saves the flyer; then checks
     * the cart for sales (F02). A store that fails is reported and keeps its old flyer.
     *
     * @throws IllegalStateException   if no store is selected
     * @throws LLMUnavailableException if the AI is unavailable (UC10 2a: old flyers are kept)
     */
    public FlyerRefresh refreshFlyers() {
        List<String> stores = flyers.getSelectedStores();
        if (stores.isEmpty()) {
            throw new IllegalStateException("Pick at least one store first.");
        }
        if (!llm.isAvailable()) {
            throw new LLMUnavailableException(llm.getUnavailableReason());
        }
        List<Flyer> updated = new ArrayList<>();
        Map<String, String> problems = new LinkedHashMap<>();
        boolean upload = false;
        try {
            for (String store : stores) {
                try {
                    Flyer flyer = agent.extractFlyer(flyers.fetchRaw(store), store, today());
                    flyers.save(flyer);
                    updated.add(flyer);
                } catch (FlyerException e) {
                    problems.put(store, e.getMessage());
                    upload |= e.isUploadInstead();
                } catch (LLMUnavailableException e) {
                    throw e;
                } catch (LLMException e) {
                    problems.put(store, e.getMessage());
                }
            }
        } finally {
            saveFlyers();
        }
        clearEndedSales();
        SaleCheck sales = updated.isEmpty() ? new SaleCheck(List.of(), List.of(), null) : checkSales(cartNames());
        return new FlyerRefresh(updated, problems, upload, sales);
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

    /** Removes a recipe from the history (F08). Returns true if it was there. */
    public boolean removeRecipe(String id) {
        boolean removed = history.remove(id);
        if (removed) {
            saveHistory();
        }
        return removed;
    }

    /** All saved recipes (F08), for the History tab's totals. */
    public List<Recipe> getHistory() {
        return history.getAll();
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
        List<String> before = cartNames();
        try {
            return agent.chat(message);
        } finally {
            saveAfterChat(agent.getLastActions(), before);
        }
    }

    /** Starts the profile questionnaire (F04). */
    public String startProfileSetup() {
        List<String> before = cartNames();
        try {
            return agent.startProfileSetup();
        } finally {
            saveAfterChat(agent.getLastActions(), before);
        }
    }

    public List<AgentAction> getLastChatActions() {
        return agent.getLastActions();
    }

    /** Saves what the agent changed; items it added to the cart are checked for sales (F02). */
    private void saveAfterChat(List<AgentAction> actions, List<String> cartBefore) {
        if (actions.stream().anyMatch(a -> a.succeededWith(CartTool.NAME))) {
            saveCart();
            List<String> added = new ArrayList<>();
            for (String name : cartNames()) {
                if (cartBefore.stream().noneMatch(b -> b.equalsIgnoreCase(name))) {
                    added.add(name);
                }
            }
            if (!added.isEmpty()) {
                checkSales(added);
            }
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

    private void saveFlyers() {
        storage.save(FLYERS_KEY, flyers.toData());
    }

    private void saveProfile() {
        storage.save(PROFILE_KEY, profile);
    }
}
