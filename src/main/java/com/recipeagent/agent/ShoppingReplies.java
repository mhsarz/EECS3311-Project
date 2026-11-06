package com.recipeagent.agent;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.recipeagent.model.Flyer;
import com.recipeagent.model.FlyerItem;
import com.recipeagent.model.SaleMatch;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Reads and checks the LLM's replies for flyers (F10), sale matching (F02) and ingredient
 * name matching (F11). Nothing the LLM says is used without checking: prices must be numbers,
 * matches must point at real cart items, flyer items and pantry items.
 */
final class ShoppingReplies {

    /** Most sale items kept from one flyer (a 24-page flyer has about 600). */
    static final int MAX_FLYER_ITEMS = 1000;
    /** Most pages a PDF can have (the Messages API's limit for one request is higher). */
    static final int MAX_PAGES = 600;
    static final int MAX_ITEM_NAME = 80;
    static final double MAX_PRICE = 1000;

    private ShoppingReplies() {
    }

    // ------------------------------------------------------------ F10 flyer

    /** Reads {"end_date": ..., "items": [{"name", "price"}]} into a Flyer for {@code store}. */
    static Flyer toFlyer(JsonObject o, String store) throws ReplyParser.ReplyException {
        List<FlyerItem> items = items(o);
        if (items.isEmpty()) {
            throw new ReplyParser.ReplyException("no sale items with a price were found");
        }
        return new Flyer(store, date(ReplyParser.optionalString(o, "end_date", null)), items);
    }

    /**
     * The sale items found on some pages of a PDF flyer, with its end date and page count if
     * the reply gave them. Pages with no sale items (covers, ads) give an empty list.
     */
    record FlyerPart(List<FlyerItem> items, LocalDate endDate, Integer pageCount) {

        /** The items of both parts without repeats (the first price is kept), and the first known date and count. */
        FlyerPart plus(FlyerPart other) {
            return new FlyerPart(merge(items, other.items), endDate != null ? endDate : other.endDate,
                    pageCount != null ? pageCount : other.pageCount);
        }
    }

    /** Reads {"page_count": n, "end_date": ..., "items": [...]} for some pages of a PDF flyer. */
    static FlyerPart toFlyerPart(JsonObject o) throws ReplyParser.ReplyException {
        Integer pages = null;
        JsonElement pc = o.get("page_count");
        if (pc != null && pc.isJsonPrimitive()) {
            try {
                int n = (int) Math.round(pc.getAsDouble());
                pages = n >= 1 && n <= MAX_PAGES ? n : null;
            } catch (NumberFormatException e) {
                pages = null;                                    // an unreadable count is ignored
            }
        }
        return new FlyerPart(items(o), date(ReplyParser.optionalString(o, "end_date", null)), pages);
    }

    /** Items from two lists without repeats (by name, ignoring case), at most MAX_FLYER_ITEMS. */
    static List<FlyerItem> merge(List<FlyerItem> a, List<FlyerItem> b) {
        List<FlyerItem> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (List<FlyerItem> list : List.of(a, b)) {
            for (FlyerItem i : list) {
                if (result.size() < MAX_FLYER_ITEMS && seen.add(i.getName().toLowerCase(Locale.ROOT))) {
                    result.add(i);
                }
            }
        }
        return result;
    }

    /** The checked items of a reply: each needs a name and a sensible price; repeats are dropped. */
    private static List<FlyerItem> items(JsonObject o) throws ReplyParser.ReplyException {
        List<FlyerItem> items = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonElement el : ReplyParser.requiredArray(o, "items")) {
            if (items.size() == MAX_FLYER_ITEMS) {
                break;
            }
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject item = el.getAsJsonObject();
            String name = ReplyParser.optionalString(item, "name", null);
            Double price = price(item.get("price"));
            if (name == null || price == null) {
                continue;                                        // no name or no usable price: skip it
            }
            name = name.replaceAll("\\s+", " ");
            if (name.length() > MAX_ITEM_NAME) {
                name = name.substring(0, MAX_ITEM_NAME).trim();
            }
            if (seen.add(name.toLowerCase(Locale.ROOT))) {
                items.add(new FlyerItem(name, price));
            }
        }
        return items;
    }

    /** A price as a number, or as text like "$3.99" or "3,99"; null if missing or not sensible. */
    static Double price(JsonElement el) {
        if (el == null || el.isJsonNull() || !el.isJsonPrimitive()) {
            return null;
        }
        double p;
        try {
            if (el.getAsJsonPrimitive().isNumber()) {
                p = el.getAsDouble();
            } else {
                String s = el.getAsString().replace("$", "").replace(",", ".").trim();
                p = Double.parseDouble(s);
            }
        } catch (NumberFormatException e) {
            return null;
        }
        if (Double.isNaN(p) || Double.isInfinite(p) || p < 0 || p > MAX_PRICE) {
            return null;
        }
        return Math.round(p * 100) / 100.0;
    }

    static LocalDate date(String s) {
        if (s == null || s.isBlank() || s.equalsIgnoreCase("null")) {
            return null;
        }
        try {
            return LocalDate.parse(s.trim());
        } catch (DateTimeParseException e) {
            return null;                                         // an unreadable date is ignored
        }
    }

    // ------------------------------------------------------------ F02 sale matching

    /** A numbered flyer item, so the LLM can point at it with an id instead of retyping it. */
    record Entry(int id, String store, FlyerItem item) {
    }

    /** Numbers the items of the flyers 1, 2, 3, ... (at most {@code max}). */
    static List<Entry> number(List<Flyer> flyers, int max) {
        List<Entry> result = new ArrayList<>();
        for (Flyer f : flyers) {
            for (FlyerItem i : f.getItems()) {
                if (result.size() == max) {
                    return result;
                }
                result.add(new Entry(result.size() + 1, f.getStore(), i));
            }
        }
        return result;
    }

    /**
     * Reads {"matches": [{"cart_item", "flyer_item_id"}]}. Unknown cart items and ids are
     * ignored; if several flyer items match one cart item, the cheapest is kept.
     */
    static List<SaleMatch> toSaleMatches(JsonObject o, List<String> cartNames, List<Entry> entries)
            throws ReplyParser.ReplyException {
        Map<Integer, Entry> byId = new LinkedHashMap<>();
        for (Entry e : entries) {
            byId.put(e.id(), e);
        }
        Map<String, Entry> best = new LinkedHashMap<>();
        for (JsonElement el : ReplyParser.requiredArray(o, "matches")) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject m = el.getAsJsonObject();
            Optional<String> cart = findIgnoringCase(cartNames, ReplyParser.optionalString(m, "cart_item", null));
            Entry entry = byId.get((int) Math.round(ReplyParser.optionalDouble(m, "flyer_item_id", -1)));
            if (cart.isEmpty() || entry == null) {
                continue;
            }
            Entry old = best.get(cart.get());
            if (old == null || entry.item().getSalePrice() < old.item().getSalePrice()) {
                best.put(cart.get(), entry);
            }
        }
        List<SaleMatch> result = new ArrayList<>();
        for (String name : cartNames) {
            Entry e = best.get(name);
            if (e != null) {
                result.add(new SaleMatch(name, e.item(), e.store()));
            }
        }
        return result;
    }

    // ------------------------------------------------------------ F11 name matching

    /**
     * Reads {"matches": [{"ingredient", "pantry_item"}]} into ingredient -> pantry item name,
     * both as written in the given lists. Names that are not in the lists are ignored.
     */
    static Map<String, String> toNameMatches(JsonObject o, List<String> ingredients, List<String> pantryNames)
            throws ReplyParser.ReplyException {
        Map<String, String> result = new LinkedHashMap<>();
        for (JsonElement el : ReplyParser.requiredArray(o, "matches")) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject m = el.getAsJsonObject();
            Optional<String> ing = findIgnoringCase(ingredients, ReplyParser.optionalString(m, "ingredient", null));
            Optional<String> pantry = findIgnoringCase(pantryNames, ReplyParser.optionalString(m, "pantry_item", null));
            if (ing.isPresent() && pantry.isPresent() && !result.containsKey(ing.get())) {
                result.put(ing.get(), pantry.get());
            }
        }
        return result;
    }

    static Optional<String> findIgnoringCase(List<String> names, String wanted) {
        if (wanted == null) {
            return Optional.empty();
        }
        String w = wanted.trim();
        return names.stream().filter(n -> n.equalsIgnoreCase(w)).findFirst();
    }
}
