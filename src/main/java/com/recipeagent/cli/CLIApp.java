package com.recipeagent.cli;

import com.recipeagent.agent.AgentAction;
import com.recipeagent.agent.PantryEmptyException;
import com.recipeagent.agent.tools.CartTool;
import com.recipeagent.agent.tools.ProfileTool;
import com.recipeagent.controller.AgentController;
import com.recipeagent.controller.FlyerRefresh;
import com.recipeagent.controller.SaleCheck;
import com.recipeagent.flyer.FlyerException;
import com.recipeagent.llm.LLMException;
import com.recipeagent.llm.LLMUnavailableException;
import com.recipeagent.model.CartItem;
import com.recipeagent.model.CartSource;
import com.recipeagent.model.Flyer;
import com.recipeagent.model.FlyerItem;
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
import com.recipeagent.model.UserProfile;
import com.recipeagent.notify.NotificationListener;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The command-line interface: every feature (F01-F11) from a terminal, using the same
 * AgentController (Facade) as the GUI.
 * <pre>
 *   cli                      interactive shell (type commands; "help" lists them)
 *   cli pantry list          one command, then exit
 * </pre>
 * It subscribes to the NotificationCenter (Singleton), so expiry and sale alerts are printed
 * as "[ALERT] ..." lines, as in the GUI's Alerts strip. Undo and the profile questions work
 * within one interactive session, because they are kept in memory.
 */
public class CLIApp implements NotificationListener {

    static final String PROMPT = "smart-pantry> ";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);

    private final AgentController controller;
    private final BufferedReader in;
    private final PrintStream out;
    private boolean interactive;
    /** The recipe shown last ("last" in commands); may not be saved yet. */
    private Recipe lastRecipe;

    public CLIApp(AgentController controller, InputStream in, PrintStream out) {
        this(controller, new BufferedReader(new InputStreamReader(in, Charset.defaultCharset())), out);
    }

    public CLIApp(AgentController controller, BufferedReader in, PrintStream out) {
        if (controller == null || in == null || out == null) {
            throw new IllegalArgumentException("Controller, input and output are required");
        }
        this.controller = controller;
        this.in = in;
        this.out = out;
    }

    /**
     * Runs one command given as program arguments, or the interactive shell when there are none.
     *
     * @return 0 if it worked, 1 if the command failed
     */
    public int run(String[] args) {
        controller.getNotifications().subscribe(this);
        try {
            if (args == null || args.length == 0) {
                return shell();
            }
            return execute(CommandLine.parse(List.of(args))) ? 0 : 1;
        } finally {
            controller.getNotifications().unsubscribe(this);
        }
    }

    /** The interactive shell: reads commands until "exit" or the end of the input. */
    int shell() {
        interactive = true;
        out.println("Smart Pantry - AI Recipe & Grocery Agent (CLI)");
        out.println(controller.getAiStatus());
        out.println("Type help to see the commands, exit to quit.");
        controller.checkExpiry();                                  // alerts on start, like the GUI (F05)
        while (true) {
            out.print(PROMPT);
            out.flush();
            String line = readLine();
            if (line == null) {
                out.println();
                break;
            }
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.equalsIgnoreCase("exit") || line.equalsIgnoreCase("quit")) {
                break;
            }
            execute(CommandLine.parse(line));
        }
        out.println("Bye. Everything is saved.");
        return 0;
    }

    /** Runs one command. Returns false (after printing why) if it did not work. */
    boolean execute(CommandLine cmd) {
        try {
            switch (cmd.word(0)) {
                case "help":
                    return help(cmd.word(1));
                case "pantry":
                    return pantry(cmd);
                case "recipe":
                    return recipe(cmd);
                case "undo":
                    return undo();
                case "cart":
                    return cart(cmd);
                case "flyers":
                    return flyers(cmd);
                case "history":
                    return history(cmd);
                case "profile":
                    return profile(cmd);
                case "chat":
                    return chat(cmd);
                case "status":
                    return status();
                default:
                    return error("Unknown command '" + cmd.rest(0) + "'. Type help to see the commands.");
            }
        } catch (LLMUnavailableException e) {
            return error("AI unavailable: " + e.getMessage() + " Features without AI still work.");
        } catch (LLMException e) {
            return error("AI problem: " + e.getMessage());
        } catch (IllegalArgumentException | IllegalStateException e) {
            return error(e.getMessage());
        }
    }

    // ------------------------------------------------------------ help

    static final Map<String, String> HELP = Map.of(
            "pantry", String.join(System.lineSeparator(),
                    "pantry add <name> [--qty 2] [--unit kg] [--location pantry|fridge|freezer] [--expires 2027-03-01] [--merge]",
                    "pantry list",
                    "pantry edit <name> [--name new] [--qty 3] [--unit L] [--location fridge] [--expires 2026-11-20|none]",
                    "pantry remove <name>",
                    "pantry expiring                 food that expires within 3 days (F05)"),
            "recipe", String.join(System.lineSeparator(),
                    "recipe generate [--time 30] [--portions 2] [--use milk,spinach] [--use-expiring] [--allow-missing] [--save]",
                    "recipe show <id>                <id> is from history list, or 'last' for the last recipe",
                    "recipe check <id> [--add-missing]   what is missing (F11)",
                    "recipe substitute <id> <ingredient> [--use 1]   replacements (F07)",
                    "recipe cooked <id>              takes the ingredients out of the pantry (F06); then: undo",
                    "recipe save [<id>]              save to history (F08)"),
            "cart", String.join(System.lineSeparator(),
                    "cart add <name> [--qty 2] [--unit L]   checks this week's flyers for a sale (F02)",
                    "cart list",
                    "cart remove <name>",
                    "cart clear",
                    "cart sales                      check the whole cart for sales"),
            "flyers", String.join(System.lineSeparator(),
                    "flyers stores                   the store list ([x] = picked)",
                    "flyers pick \"No Frills\" Metro   pick stores (new names are added)",
                    "flyers unpick Metro",
                    "flyers refresh                  read the picked stores' flyers (F10)",
                    "flyers upload ./nofrills.txt --store \"No Frills\"",
                    "flyers list [--store \"No Frills\"] [--search chicken]"),
            "history", String.join(System.lineSeparator(),
                    "history list [--favourites]",
                    "history rate <id> 4",
                    "history fav <id>",
                    "history search chicken",
                    "history remove <id>"),
            "profile", String.join(System.lineSeparator(),
                    "profile show",
                    "profile set [--diet vegetarian] [--allergy peanuts,milk] [--dislike olives] [--portions 2]",
                    "            (--allergy none clears the list)",
                    "profile setup                   the agent asks you questions (interactive CLI only)"),
            "chat", String.join(System.lineSeparator(),
                    "chat \"What can I make with chicken?\"   one message",
                    "chat                            a conversation; type exit to leave it"));

    private boolean help(String topic) {
        if (!topic.isEmpty() && HELP.containsKey(topic)) {
            out.println(HELP.get(topic));
            return true;
        }
        out.println("Commands (type help <topic> for the options):");
        for (String t : List.of("pantry", "recipe", "cart", "flyers", "history", "profile", "chat")) {
            out.println();
            out.println(HELP.get(t));
        }
        out.println();
        out.println("undo                            undo the last Mark as Cooked (same session)");
        out.println("status                          AI status and calls left today");
        out.println("exit                            leave the interactive CLI");
        return true;
    }

    // ------------------------------------------------------------ F03 / F05 pantry

    private boolean pantry(CommandLine cmd) {
        switch (cmd.word(1)) {
            case "add":
                return pantryAdd(cmd);
            case "list":
                return pantryList();
            case "edit":
                return pantryEdit(cmd);
            case "remove":
                String name = need(cmd.rest(2), "Which item? e.g. pantry remove rice");
                return controller.removePantryItemByName(name) ? say("Removed " + name + ".")
                        : error(name + " is not in your pantry.");
            case "expiring":
                return pantryExpiring();
            default:
                return usage("pantry");
        }
    }

    private boolean pantryAdd(CommandLine cmd) {
        checkOptions(cmd, "qty", "unit", "location", "expires", "merge");
        String name = need(cmd.rest(2), "Which item? e.g. pantry add rice --qty 2 --unit kg");
        double qty = quantity(cmd.option("qty", "1"));
        StorageLocation location = location(cmd.option("location", "pantry"));
        LocalDate expires = date(cmd.option("expires", ""));
        PantryItem item = new PantryItem(name, qty, cmd.option("unit", ""), location, expires);
        if (controller.addPantryItem(item, false)) {
            return say("Added " + describe(item) + ".");
        }
        PantryItem existing = findPantry(name).orElseThrow();
        boolean merge = cmd.has("merge") || (interactive && ask(existing.getName() + " is already in your pantry ("
                + amount(existing.getQuantity(), existing.getUnit()) + "). Add " + amount(qty, item.getUnit())
                + " to it? (y/n) "));
        if (!merge) {
            return say(existing.getName() + " is already in your pantry. Nothing was added. "
                    + "Use --merge to add the amounts together.");
        }
        controller.addPantryItem(item, true);
        PantryItem now = findPantry(name).orElseThrow();
        return say("Merged: " + now.getName() + " is now " + amount(now.getQuantity(), now.getUnit()) + ".");
    }

    private boolean pantryList() {
        List<PantryItem> items = controller.getPantryItems();
        if (items.isEmpty()) {
            return say("Your pantry is empty. Add food with: pantry add rice --qty 2 --unit kg");
        }
        TextTable t = new TextTable("Item", "Qty", "Unit", "Location", "Best before", "Days left");
        for (PantryItem i : items) {
            t.row(i.getName(), fmt(i.getQuantity()), i.getUnit(), capitalize(i.getLocation().name()),
                    i.hasBestBefore() ? i.getBestBefore().format(DATE) : "-",
                    i.hasBestBefore() ? daysLabel(i.daysUntilExpiry(controller.today())) : "");
        }
        out.print(t.render());
        return true;
    }

    private boolean pantryEdit(CommandLine cmd) {
        checkOptions(cmd, "name", "qty", "unit", "location", "expires");
        String name = need(cmd.rest(2), "Which item? e.g. pantry edit milk --qty 2");
        PantryItem item = findPantry(name).orElseThrow(() -> new IllegalArgumentException(name + " is not in your pantry."));
        LocalDate expires = cmd.has("expires") ? date(cmd.option("expires", "")) : item.getBestBefore();
        PantryItem changed = controller.updatePantryItem(item.getId(), cmd.option("name", item.getName()),
                cmd.has("qty") ? quantity(cmd.option("qty", "")) : item.getQuantity(),
                cmd.option("unit", item.getUnit()),
                cmd.has("location") ? location(cmd.option("location", "")) : item.getLocation(), expires);
        return say("Changed: " + describe(changed) + ".");
    }

    private boolean pantryExpiring() {
        List<PantryItem> soon = controller.checkExpiry();            // prints one [ALERT] per item
        if (soon.isEmpty()) {
            return say("Nothing in your pantry expires in the next " + AgentController.EXPIRY_DAYS + " days.");
        }
        return say(soon.size() + (soon.size() == 1 ? " item expires" : " items expire") + " soon. Get a recipe "
                + "that uses them: recipe generate --use-expiring");
    }

    // ------------------------------------------------------------ F01 / F06 / F07 / F11 recipes

    private boolean recipe(CommandLine cmd) {
        switch (cmd.word(1)) {
            case "generate":
                return recipeGenerate(cmd);
            case "show":
                printRecipe(findRecipe(cmd.word(2)));
                return true;
            case "check":
                return recipeCheck(cmd);
            case "substitute":
                return recipeSubstitute(cmd);
            case "cooked":
                return recipeCooked(cmd);
            case "save":
                Recipe r = findRecipe(cmd.word(2));
                return controller.saveRecipe(r) ? say("Saved \"" + r.getTitle() + "\" to your history (id " + shortId(r) + ").")
                        : say("\"" + r.getTitle() + "\" is already in your history.");
            default:
                return usage("recipe");
        }
    }

    private boolean recipeGenerate(CommandLine cmd) {
        checkOptions(cmd, "time", "portions", "use", "use-expiring", "allow-missing", "save");
        int minutes = whole(cmd.option("time", "30"), "Time", 1, 600);
        int portions = whole(cmd.option("portions", String.valueOf(controller.getProfile().getDefaultPortions())),
                "Portions", 1, 50);
        RecipeRequest req = new RecipeRequest(minutes, portions);
        List<String> required = new ArrayList<>(cmd.optionList("use"));
        if (cmd.has("use-expiring")) {
            List<PantryItem> soon = controller.getItemsToUseSoon();
            if (soon.isEmpty()) {
                return error("Nothing in your pantry is about to expire.");
            }
            soon.forEach(i -> required.add(i.getName()));
        }
        required.forEach(req::addRequiredItem);
        req.setAllowMissing(cmd.has("allow-missing"));
        out.println(required.isEmpty() ? "Thinking of a recipe..." : "Finding a recipe that uses " + String.join(", ", required) + "...");
        Recipe r;
        try {
            r = controller.generateRecipe(req);
        } catch (PantryEmptyException e) {
            return error("Your pantry is empty. Add food with pantry add, or get a recipe to shop for: "
                    + "recipe generate --allow-missing");
        }
        lastRecipe = r;
        if (cmd.has("save")) {
            controller.saveRecipe(r);
            printRecipe(r);
            return say("Saved to your history (id " + shortId(r) + ").");
        }
        printRecipe(r);
        return say("Not saved yet. Save it with: recipe save" + (interactive ? "" : " (or add --save next time)"));
    }

    private boolean recipeCheck(CommandLine cmd) {
        checkOptions(cmd, "add-missing");
        Recipe r = findRecipe(cmd.word(2));
        IngredientCheck check = controller.checkIngredients(r);        // may ask the AI to match names
        if (check.allInStock()) {
            return say("You have everything for \"" + r.getTitle() + "\". Cook it with: recipe cooked " + idFor(r));
        }
        List<IngredientStatus> statuses = controller.getIngredientStatuses(r);
        TextTable t = new TextTable("Missing", "Amount needed", "Why");
        for (Ingredient i : check.getMissing()) {
            IngredientStatus s = statusOf(r, statuses, i.getName());
            t.row(i.getName(), amountOf(i), s == IngredientStatus.NOT_ENOUGH ? "Not enough in pantry" : "Not in pantry");
        }
        out.print(t.render());
        boolean add = cmd.has("add-missing") || (interactive && ask("Add the missing items to your cart? (y/n) "));
        if (!add) {
            return say("Add them to your cart with: recipe check " + idFor(r) + " --add-missing");
        }
        List<CartItem> items = new ArrayList<>();
        for (Ingredient i : check.getMissing()) {
            items.add(new CartItem(i.getName(), i.getQuantity() > 0 ? i.getQuantity() : 1,
                    i.getQuantity() > 0 ? i.getUnit() : "", CartSource.RECIPE));
        }
        controller.addToCart(items);
        say("Added to your cart: " + String.join(", ", check.getMissingNames()) + ".");
        reportSales(controller.checkSales(check.getMissingNames()), false);
        return true;
    }

    private boolean recipeSubstitute(CommandLine cmd) {
        checkOptions(cmd, "use");
        Recipe r = findRecipe(cmd.word(2));
        String wanted = need(cmd.rest(3), "Which ingredient? e.g. recipe substitute " + idFor(r) + " buttermilk");
        Ingredient ing = r.getIngredients().stream().filter(i -> i.getName().equalsIgnoreCase(wanted)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException(wanted + " is not an ingredient of \"" + r.getTitle() + "\"."));
        out.println("Looking for substitutes for " + ing.getName() + "...");
        List<Substitution> subs = controller.suggestSubstitutes(r, ing.getName());
        if (subs.isEmpty()) {
            return say("No good substitute for " + ing.getName() + ". Add it to your cart with: cart add " + ing.getName());
        }
        out.println("Instead of " + ing.getName() + " (" + amountOf(ing) + ") you can use:");
        for (int i = 0; i < subs.size(); i++) {
            Substitution s = subs.get(i);
            out.println("  " + (i + 1) + ". " + s.getSubstitute() + (s.isInStock() ? "  (in your pantry)" : ""));
            if (!s.getNote().isEmpty()) {
                out.println("     " + s.getNote());
            }
        }
        String choice = cmd.option("use", null);
        if (choice == null && interactive) {
            out.print("Use one? Type its number, or press Enter to keep " + ing.getName() + ": ");
            out.flush();
            choice = readLine();
        }
        if (choice == null || choice.isBlank()) {
            return true;
        }
        int n = whole(choice.trim(), "The choice", 1, subs.size());
        Substitution s = subs.get(n - 1);
        controller.useSubstitute(r, ing.getName(), s);
        return say("Using " + s.getSubstitute() + " instead of " + ing.getName() + " in \"" + r.getTitle() + "\".");
    }

    private boolean recipeCooked(CommandLine cmd) {
        Recipe r = findRecipe(cmd.word(2));
        List<String> skipped = controller.markCooked(r);
        StringBuilder sb = new StringBuilder("Marked \"" + r.getTitle() + "\" as cooked. The ingredients were taken out of your pantry.");
        if (!skipped.isEmpty()) {
            sb.append(" Not in your pantry, so skipped: ").append(String.join(", ", skipped)).append(".");
        }
        sb.append(interactive ? " Type undo if this was a mistake." : " (Undo works in the interactive CLI, in the same session.)");
        return say(sb.toString());
    }

    private boolean undo() {
        Optional<String> undone = controller.undo();
        if (undone.isPresent()) {
            return say("Undone: " + undone.get() + ". Your pantry is back as it was.");
        }
        return say("There is nothing to undo." + (interactive ? "" : " (Undo works within one interactive session.)"));
    }

    private void printRecipe(Recipe r) {
        lastRecipe = r;
        out.println();
        out.println(r.getTitle());
        String cooked = r.getTimesCooked() == 0 ? "" : r.getTimesCooked() == 1 ? ", cooked once" : ", cooked " + r.getTimesCooked() + " times";
        out.println(r.getCookTimeMinutes() + " min, " + r.getPortions() + (r.getPortions() == 1 ? " portion" : " portions") + cooked);
        out.println();
        out.println("Ingredients:");
        List<IngredientStatus> statuses = controller.getIngredientStatuses(r);
        for (int i = 0; i < r.getIngredients().size(); i++) {
            Ingredient ing = r.getIngredients().get(i);
            String match = controller.getPantryMatch(ing.getName()).map(p -> " (your " + p + ")").orElse("");
            out.println("  - " + ing.getName() + ": " + amountOf(ing) + match + "  [" + statusLabel(statuses.get(i)) + "]");
        }
        out.println();
        out.println("Steps:");
        int n = 1;
        for (String s : r.getSteps()) {
            out.println("  " + n++ + ". " + s);
        }
        out.println();
        boolean saved = controller.findRecipe(r.getId()).isPresent();
        out.println("Id: " + shortId(r) + (saved ? "" : " (not saved yet; use 'last' in commands)"));
    }

    // ------------------------------------------------------------ F02 cart

    private boolean cart(CommandLine cmd) {
        switch (cmd.word(1)) {
            case "add": {
                checkOptions(cmd, "qty", "unit");
                String name = need(cmd.rest(2), "Which item? e.g. cart add milk --qty 2");
                double qty = quantity(cmd.option("qty", "1"));
                boolean was = controller.getCartItems().stream().anyMatch(i -> i.hasName(name));
                CartItem inCart = controller.addToCart(new CartItem(name, qty, cmd.option("unit", ""), CartSource.MANUAL));
                say(was ? inCart.getName() + " was already in your cart; it now has " + amount(inCart.getQuantity(), inCart.getUnit()) + "."
                        : "Added " + inCart.getName() + " (" + amount(inCart.getQuantity(), inCart.getUnit()) + ") to your cart.");
                reportSales(controller.checkSales(List.of(inCart.getName())), false);
                return true;
            }
            case "list":
                return cartList();
            case "remove": {
                String name = need(cmd.rest(2), "Which item? e.g. cart remove milk");
                return controller.removeFromCart(name) ? say("Removed " + name + " from your cart.")
                        : error(name + " is not in your cart.");
            }
            case "clear":
                controller.clearCart();
                return say("Your cart is empty.");
            case "sales":
                if (controller.getCartItems().isEmpty()) {
                    return say("Your cart is empty.");
                }
                out.println("Looking for your items in this week's flyers...");
                reportSales(controller.checkSales(controller.getCartItems().stream().map(CartItem::getName).toList()), true);
                return true;
            default:
                return usage("cart");
        }
    }

    private boolean cartList() {
        List<CartItem> items = controller.getCartItems();
        if (items.isEmpty()) {
            return say("Your cart is empty. Add items with: cart add milk --qty 2");
        }
        TextTable t = new TextTable("Item", "Qty", "Unit", "Added from", "On sale");
        for (CartItem i : items) {
            String sale = !i.isOnSale() ? "" : i.getSaleStore() == null || i.getSalePrice() == null ? "on sale"
                    : i.getSaleStore() + " " + money(i.getSalePrice());
            t.row(i.getName(), fmt(i.getQuantity()), i.getUnit(), i.getSource().getLabel(), sale);
        }
        out.print(t.render());
        return true;
    }

    /** Prints a sale check result; sale alerts themselves are printed by onNotify. */
    private void reportSales(SaleCheck check, boolean manual) {
        if (!check.wasRun()) {
            out.println("(" + check.getSkippedReason() + ")");
        } else if (manual && check.getMatches().isEmpty()) {
            out.println("None of your items are on sale this week.");
        } else if (manual) {
            out.println(check.getMatches().size() + (check.getMatches().size() == 1 ? " item is" : " items are")
                    + " on sale. See: cart list");
        }
    }

    // ------------------------------------------------------------ F10 flyers

    private boolean flyers(CommandLine cmd) {
        switch (cmd.word(1)) {
            case "stores":
                for (String s : controller.getFlyerStores()) {
                    out.println((controller.isStoreSelected(s) ? "[x] " : "[ ] ") + s);
                }
                if (!controller.isOnlineFlyersSetUp()) {
                    out.println("(Online flyers are not set up, so use: flyers upload <file> --store <name>)");
                }
                return true;
            case "pick":
            case "unpick": {
                boolean on = cmd.word(1).equals("pick");
                List<String> names = cmd.words().subList(Math.min(2, cmd.words().size()), cmd.words().size());
                if (names.isEmpty()) {
                    return error("Which store? e.g. flyers " + cmd.word(1) + " \"No Frills\"");
                }
                List<String> done = new ArrayList<>();
                for (String n : names) {
                    if (on) {
                        done.add(controller.addFlyerStore(n));             // adds new stores, picks it
                    } else {
                        Optional<String> s = controller.getFlyerStores().stream().filter(x -> x.equalsIgnoreCase(n)).findFirst();
                        if (s.isEmpty()) {
                            return error(n + " is not in your store list.");
                        }
                        controller.setStoreSelected(s.get(), false);
                        done.add(s.get());
                    }
                }
                return say((on ? "Picked: " : "Unpicked: ") + String.join(", ", done) + ".");
            }
            case "refresh": {
                out.println("Reading the flyers of " + String.join(", ", controller.getSelectedStores()) + "...");
                printRefresh(controller.refreshFlyers());
                return true;
            }
            case "upload":
                return flyersUpload(cmd);
            case "list":
                return flyersList(cmd);
            default:
                return usage("flyers");
        }
    }

    private boolean flyersUpload(CommandLine cmd) {
        checkOptions(cmd, "store");
        String file = need(cmd.rest(2), "Which file? e.g. flyers upload ./nofrills.txt --store \"No Frills\"");
        String store = cmd.option("store", "").trim();
        if (store.isEmpty()) {
            return error("Which store is it from? Add --store, e.g. --store \"No Frills\"");
        }
        out.println("Reading the " + store + " flyer...");
        try {
            printRefresh(controller.uploadFlyer(Path.of(file), store));
            return true;
        } catch (FlyerException e) {
            return error(e.getMessage());
        } catch (java.nio.file.InvalidPathException e) {
            return error("That is not a valid file path: " + file);
        }
    }

    private void printRefresh(FlyerRefresh r) {
        for (Flyer f : r.getUpdated()) {
            out.println("Read " + f.getItems().size() + (f.getItems().size() == 1 ? " sale item" : " sale items") + " from "
                    + f.getStore() + (f.getEndDate() == null ? "" : " (until " + f.getEndDate().format(DATE) + ")") + ".");
        }
        for (Map.Entry<String, String> p : r.getProblems().entrySet()) {
            out.println(p.getKey() + ": " + p.getValue());
        }
        if (!r.getUpdated().isEmpty()) {
            reportSales(r.getSales(), true);
            out.println("See the sale items with: flyers list");
        }
    }

    private boolean flyersList(CommandLine cmd) {
        checkOptions(cmd, "store", "search");
        String store = cmd.option("store", "");
        String word = cmd.option("search", "").toLowerCase(Locale.ROOT).trim();
        List<Flyer> flyers = controller.getCurrentFlyers();
        if (flyers.isEmpty()) {
            return say("No flyers for this week yet. Upload one with: flyers upload ./nofrills.txt --store \"No Frills\"");
        }
        TextTable t = new TextTable("Store", "Item", "Price", "Ends", "In your cart");
        for (Flyer f : flyers) {
            if (!store.isBlank() && !f.getStore().equalsIgnoreCase(store.trim())) {
                continue;
            }
            for (FlyerItem i : f.getItems()) {
                if (!word.isEmpty() && !i.getName().toLowerCase(Locale.ROOT).contains(word)) {
                    continue;
                }
                String inCart = controller.getCartItems().stream()
                        .filter(c -> c.isOnSale() && f.getStore().equals(c.getSaleStore()) && i.getName().equals(c.getSaleItemName()))
                        .map(CartItem::getName).findFirst().orElse("");
                t.row(f.getStore(), i.getName(), i.formattedPrice(),
                        f.getEndDate() == null ? "-" : f.getEndDate().format(DATE), inCart);
            }
        }
        if (t.isEmpty()) {
            return say("No sale items match.");
        }
        out.print(t.render());
        return true;
    }

    // ------------------------------------------------------------ F08 history

    private boolean history(CommandLine cmd) {
        switch (cmd.word(1)) {
            case "list": {
                checkOptions(cmd, "favourites", "favorites");
                List<Recipe> all = controller.getHistory();
                boolean favs = cmd.has("favourites") || cmd.has("favorites");
                List<Recipe> shown = all.stream().filter(r -> !favs || r.isFavourite()).toList();
                if (shown.isEmpty()) {
                    return say(favs ? "No favourites yet. Mark one with: history fav <id>"
                            : "No saved recipes yet. Generate one with: recipe generate --save");
                }
                printHistory(shown);
                return true;
            }
            case "search": {
                String q = need(cmd.rest(2), "Search for what? e.g. history search chicken");
                List<Recipe> found = controller.searchHistory(q);
                if (found.isEmpty()) {
                    return say("No recipes match \"" + q + "\". Generate one with: recipe generate");
                }
                printHistory(found);
                return true;
            }
            case "rate": {
                Recipe r = findSaved(cmd.word(2));
                int stars = whole(cmd.word(3), "The rating", 1, 5);
                controller.rateRecipe(r.getId(), stars);
                return say("Rated \"" + r.getTitle() + "\" " + stars + " of 5.");
            }
            case "fav": {
                Recipe r = findSaved(cmd.word(2));
                boolean fav = controller.toggleFavourite(r.getId());
                return say("\"" + r.getTitle() + "\" is " + (fav ? "now a favourite." : "no longer a favourite."));
            }
            case "remove": {
                Recipe r = findSaved(cmd.word(2));
                if (interactive && !ask("Remove \"" + r.getTitle() + "\" from your history? (y/n) ")) {
                    return say("Kept it.");
                }
                controller.removeRecipe(r.getId());
                return say("Removed \"" + r.getTitle() + "\" from your history.");
            }
            case "show":
                printRecipe(findSaved(cmd.word(2)));
                return true;
            default:
                return usage("history");
        }
    }

    private void printHistory(List<Recipe> recipes) {
        TextTable t = new TextTable("Id", "Recipe", "Rating", "Fav", "Cooked", "Last cooked", "Time");
        for (Recipe r : recentFirst(recipes)) {
            t.row(shortId(r), r.getTitle(), r.isRated() ? r.getRating() + "/5" : "-", r.isFavourite() ? "yes" : "",
                    r.getTimesCooked(), r.getLastCooked() == null ? "never" : r.getLastCooked().format(DATE),
                    r.getCookTimeMinutes() + " min");
        }
        out.print(t.render());
        out.println(controller.getHistory().size() + " saved. Use the id in: history rate <id> 5, recipe show <id>");
    }

    /** Most recently cooked first, then never-cooked recipes, newest saved first (like the GUI). */
    static List<Recipe> recentFirst(List<Recipe> recipes) {
        List<Recipe> cooked = new ArrayList<>(recipes.stream().filter(r -> r.getLastCooked() != null).toList());
        cooked.sort(Comparator.comparing(Recipe::getLastCooked).reversed());
        List<Recipe> never = new ArrayList<>(recipes.stream().filter(r -> r.getLastCooked() == null).toList());
        java.util.Collections.reverse(never);
        cooked.addAll(never);
        return cooked;
    }

    // ------------------------------------------------------------ F04 profile

    private boolean profile(CommandLine cmd) {
        switch (cmd.word(1)) {
            case "show":
            case "":
                return profileShow();
            case "set": {
                checkOptions(cmd, "diet", "allergy", "allergies", "dislike", "dislikes", "portions");
                UserProfile p = controller.getProfile();
                List<String> allergies = listOption(cmd, p.getAllergies(), "allergy", "allergies");
                List<String> dislikes = listOption(cmd, p.getDislikes(), "dislike", "dislikes");
                int portions = cmd.has("portions") ? whole(cmd.option("portions", ""), "Portions", 1, 50)
                        : p.getDefaultPortions();
                controller.updateProfile(cmd.option("diet", p.getDiet()), allergies, dislikes, portions);
                out.println("Profile saved.");
                return profileShow();
            }
            case "setup":
                return profileSetup();
            default:
                return usage("profile");
        }
    }

    private static List<String> listOption(CommandLine cmd, List<String> current, String... names) {
        boolean given = false;
        List<String> values = new ArrayList<>();
        for (String n : names) {
            if (cmd.has(n)) {
                given = true;
                values.addAll(cmd.optionList(n));
            }
        }
        if (!given) {
            return current;
        }
        values.removeIf(v -> v.equalsIgnoreCase("none"));
        return values;
    }

    private boolean profileShow() {
        UserProfile p = controller.getProfile();
        out.println("Diet:             " + (p.getDiet().equals(UserProfile.NO_DIET) ? "no special diet" : p.getDiet()));
        out.println("Allergies:        " + (p.getAllergies().isEmpty() ? "none" : String.join(", ", p.getAllergies())));
        out.println("Dislikes:         " + (p.getDislikes().isEmpty() ? "none" : String.join(", ", p.getDislikes())));
        out.println("Default portions: " + p.getDefaultPortions());
        return true;
    }

    private boolean profileSetup() {
        if (!controller.isAiAvailable()) {
            return error("profile setup needs the AI, which is unavailable: "
                    + controller.getAiStatus().replaceFirst("^AI unavailable:\\s*", "")
                    + " Set your profile with: profile set --diet vegetarian --allergy peanuts");
        }
        if (!interactive) {
            return error("profile setup asks you questions, so run it in the interactive CLI "
                    + "(start the CLI with no command, then type: profile setup).");
        }
        printAnswer(controller.startProfileSetup());
        out.println("(Answer the questions; type exit to stop.)");
        chatLoop();
        return true;
    }

    // ------------------------------------------------------------ F09 chat

    private boolean chat(CommandLine cmd) {
        String message = cmd.rest(1);
        if (!message.isEmpty()) {
            printAnswer(controller.chat(message));
            return true;
        }
        out.println("Chat with the agent (type exit to leave the chat).");
        chatLoop();
        return true;
    }

    private void chatLoop() {
        while (true) {
            out.print("you> ");
            out.flush();
            String line = readLine();
            if (line == null) {
                out.println();
                return;
            }
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.equalsIgnoreCase("exit") || line.equalsIgnoreCase("quit") || line.equalsIgnoreCase("back")) {
                return;
            }
            try {
                printAnswer(controller.chat(line));
            } catch (LLMException e) {
                error((e instanceof LLMUnavailableException ? "AI unavailable: " : "AI problem: ") + e.getMessage());
                if (e instanceof LLMUnavailableException) {
                    return;
                }
            }
        }
    }

    private void printAnswer(String answer) {
        out.println("agent> " + answer.replace("\n", System.lineSeparator() + "       "));
        List<String> markers = changeMarkers(controller.getLastChatActions());
        if (!markers.isEmpty()) {
            out.println("       (" + String.join(", ", markers) + ")");
        }
    }

    /** "cart updated" / "profile saved" for what the agent changed (reading is not shown). */
    static List<String> changeMarkers(List<AgentAction> actions) {
        List<String> result = new ArrayList<>();
        for (AgentAction a : actions) {
            String m = null;
            if (a.succeededWith(CartTool.NAME) && !a.args().contains("\"list\"") && !a.args().equals("list")) {
                m = "cart updated";
            } else if (a.succeededWith(ProfileTool.NAME) && a.args().contains("save")) {
                m = "profile saved";
            }
            if (m != null && !result.contains(m)) {
                result.add(m);
            }
        }
        return result;
    }

    private boolean status() {
        out.println(controller.getAiStatus());
        out.println("Pantry: " + plural(controller.getPantryItems().size(), "item") + ", cart: "
                + plural(controller.getCartItems().size(), "item") + ", history: "
                + plural(controller.getHistory().size(), "recipe") + ", current flyers: " + controller.getCurrentFlyers().size());
        return true;
    }

    // ------------------------------------------------------------ alerts (Singleton observer)

    /** Prints alerts from the NotificationCenter as they happen (F02, F05). */
    @Override
    public void onNotify(Notification n) {
        out.println("[ALERT] " + (n.getType() == NotificationType.EXPIRY ? "Expiring: " : "On sale: ") + n.getMessage());
        out.flush();
    }

    // ------------------------------------------------------------ finding things

    private Optional<PantryItem> findPantry(String name) {
        return controller.getPantryItems().stream().filter(p -> p.hasName(name)).findFirst();
    }

    /** "last" (or nothing) for the recipe shown last, else a saved recipe's id (the first 4+ characters). */
    Recipe findRecipe(String token) {
        if (token == null || token.isBlank() || token.equalsIgnoreCase("last")) {
            if (lastRecipe == null) {
                throw new IllegalArgumentException("No recipe yet. Run recipe generate first, or use an id from history list.");
            }
            return lastRecipe;
        }
        if (lastRecipe != null && lastRecipe.getId().toLowerCase(Locale.ROOT).startsWith(token.toLowerCase(Locale.ROOT))
                && token.length() >= 4) {
            return lastRecipe;
        }
        return findSaved(token);
    }

    private Recipe findSaved(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Which recipe? Give its id from: history list");
        }
        if (token.equalsIgnoreCase("last") && lastRecipe != null && controller.findRecipe(lastRecipe.getId()).isPresent()) {
            return lastRecipe;
        }
        if (token.length() < 4) {
            throw new IllegalArgumentException("Give at least the first 4 characters of the recipe id (see history list).");
        }
        String t = token.toLowerCase(Locale.ROOT);
        List<Recipe> hits = controller.getHistory().stream().filter(r -> r.getId().toLowerCase(Locale.ROOT).startsWith(t)).toList();
        if (hits.isEmpty()) {
            throw new IllegalArgumentException("No saved recipe has the id " + token + ". See: history list");
        }
        if (hits.size() > 1) {
            throw new IllegalArgumentException("More than one recipe starts with " + token + "; type more of the id.");
        }
        lastRecipe = hits.get(0);
        return hits.get(0);
    }

    private String idFor(Recipe r) {
        return controller.findRecipe(r.getId()).isPresent() ? shortId(r) : "last";
    }

    static String shortId(Recipe r) {
        return r.getId().length() <= 8 ? r.getId() : r.getId().substring(0, 8);
    }

    private static IngredientStatus statusOf(Recipe r, List<IngredientStatus> statuses, String name) {
        for (int i = 0; i < r.getIngredients().size() && i < statuses.size(); i++) {
            if (r.getIngredients().get(i).getName().equalsIgnoreCase(name)) {
                return statuses.get(i);
            }
        }
        return IngredientStatus.MISSING;
    }

    // ------------------------------------------------------------ parsing and text (tested)

    static double quantity(String text) {
        try {
            double q = Double.parseDouble(text.trim().replace(',', '.'));
            if (q <= 0 || Double.isNaN(q) || Double.isInfinite(q)) {
                throw new IllegalArgumentException("Quantity must be more than zero.");
            }
            return q;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Quantity must be a number, like 2 or 1.5.");
        }
    }

    static int whole(String text, String what, int min, int max) {
        int n;
        try {
            n = Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(what + " must be a whole number" + (min == 1 && max == 5 ? " from 1 to 5." : "."));
        }
        if (n < min || n > max) {
            throw new IllegalArgumentException(what + " must be between " + min + " and " + max + ".");
        }
        return n;
    }

    /** A date like 2026-11-20; "" or "none" means no date. */
    static LocalDate date(String text) {
        if (text == null || text.isBlank() || text.trim().equalsIgnoreCase("none")) {
            return null;
        }
        try {
            return LocalDate.parse(text.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Dates are year-month-day, like 2026-11-20.");
        }
    }

    static StorageLocation location(String text) {
        try {
            return StorageLocation.valueOf(text.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Location must be pantry, fridge or freezer.");
        }
    }

    /** "1 day - use soon", "Today - use soon", "Expired 2 days ago", "7 days". */
    static String daysLabel(int d) {
        if (d < 0) {
            return "Expired " + (-d) + (d == -1 ? " day ago" : " days ago");
        }
        String days = d == 0 ? "Today" : d + (d == 1 ? " day" : " days");
        return d <= AgentController.EXPIRY_DAYS ? days + " - use soon" : days;
    }

    static String statusLabel(IngredientStatus s) {
        switch (s) {
            case IN_PANTRY:
                return "in pantry";
            case USE_SOON:
                return "use soon";
            case EXPIRED:
                return "expired";
            case NOT_ENOUGH:
                return "not enough";
            case MISSING:
                return "missing";
            default:
                return "basic";
        }
    }

    static String plural(int n, String word) {
        return n + " " + word + (n == 1 ? "" : "s");
    }

    static String fmt(double q) {
        return q == Math.rint(q) ? String.valueOf((long) q) : String.valueOf(q);
    }

    static String amount(double q, String unit) {
        return (fmt(q) + " " + (unit == null ? "" : unit)).trim();
    }

    static String amountOf(Ingredient i) {
        return i.getQuantity() == 0 ? "to taste" : amount(i.getQuantity(), i.getUnit());
    }

    static String money(double price) {
        return String.format(Locale.CANADA, "$%.2f", price);
    }

    private String describe(PantryItem i) {
        return i.getName() + " (" + amount(i.getQuantity(), i.getUnit()) + ", " + i.getLocation().name().toLowerCase(Locale.ROOT)
                + (i.hasBestBefore() ? ", best before " + i.getBestBefore().format(DATE) : "") + ")";
    }

    private static String capitalize(String upper) {
        String l = upper.toLowerCase(Locale.ROOT);
        return l.isEmpty() ? l : Character.toUpperCase(l.charAt(0)) + l.substring(1);
    }

    private static String need(String value, String hint) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(hint);
        }
        return value;
    }

    private static void checkOptions(CommandLine cmd, String... allowed) {
        List<String> bad = cmd.unknownOptions(allowed);
        if (!bad.isEmpty()) {
            throw new IllegalArgumentException("Unknown option " + String.join(", ", bad) + ". Type help for the options.");
        }
    }

    // ------------------------------------------------------------ output helpers

    private boolean say(String text) {
        out.println(text);
        return true;
    }

    private boolean error(String text) {
        out.println("Error: " + text);
        return false;
    }

    private boolean usage(String topic) {
        out.println("Usage:");
        out.println(HELP.get(topic));
        return false;
    }

    private boolean ask(String question) {
        out.print(question);
        out.flush();
        String a = readLine();
        return a != null && (a.trim().equalsIgnoreCase("y") || a.trim().equalsIgnoreCase("yes"));
    }

    private String readLine() {
        try {
            return in.readLine();
        } catch (IOException e) {
            return null;
        }
    }
}
