package com.recipeagent.flyer;

import com.recipeagent.model.Flyer;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * This week's flyers (F10): the stores the user shops at, which of them are selected, where
 * each store's flyer comes from, and the flyers read so far (one per store).
 * <p>
 * Each store uses the online source unless the user uploaded a file for it
 * ({@link #setSource}). Saving to disk is done by the caller, like the other managers.
 */
public class FlyerService {

    /** Stores offered at first (Toronto area); the user can add others. */
    public static final List<String> DEFAULT_STORES = List.of("No Frills", "FreshCo", "Food Basics",
            "Metro", "Loblaws", "Walmart");
    static final int MAX_STORE_NAME = 40;

    private final List<String> stores = new ArrayList<>();
    private final List<String> selected = new ArrayList<>();
    private final Map<String, FlyerSource> sources = new LinkedHashMap<>();
    private final Map<String, Flyer> flyers = new LinkedHashMap<>();
    private final FlyerSource onlineSource;

    /** Starts with the default stores, none selected. */
    public FlyerService(FlyerSource onlineSource) {
        this(onlineSource, null);
    }

    /** Starts from saved data (may be null). */
    public FlyerService(FlyerSource onlineSource, Data saved) {
        if (onlineSource == null) {
            throw new IllegalArgumentException("An online flyer source is required");
        }
        this.onlineSource = onlineSource;
        List<String> known = saved == null || saved.stores == null || saved.stores.isEmpty()
                ? DEFAULT_STORES : saved.stores;
        for (String s : known) {
            addKnown(s);
        }
        if (saved != null && saved.selected != null) {
            for (String s : saved.selected) {
                find(s).ifPresent(name -> {
                    if (!selected.contains(name)) {
                        selected.add(name);
                    }
                });
            }
        }
        if (saved != null && saved.flyers != null) {
            for (Flyer f : saved.flyers) {
                if (f != null && f.getStore() != null) {
                    save(f);
                }
            }
        }
    }

    // ------------------------------------------------------------ stores

    /** Every store in the list, in order. */
    public List<String> getStores() {
        return Collections.unmodifiableList(stores);
    }

    /** The stores the user picked (the ones Refresh reads). */
    public List<String> getSelectedStores() {
        List<String> result = new ArrayList<>();
        for (String s : stores) {
            if (selected.contains(s)) {
                result.add(s);
            }
        }
        return result;
    }

    public boolean isSelected(String store) {
        return find(store).map(selected::contains).orElse(false);
    }

    /** Picks or unpicks a store; returns false if the store is not in the list. */
    public boolean setSelected(String store, boolean on) {
        Optional<String> name = find(store);
        if (name.isEmpty()) {
            return false;
        }
        if (on && !selected.contains(name.get())) {
            selected.add(name.get());
        } else if (!on) {
            selected.remove(name.get());
        }
        return true;
    }

    /**
     * Adds a store to the list and selects it (does nothing more if it is already there).
     *
     * @return the store's name as stored in the list
     * @throws IllegalArgumentException if the name is blank or too long
     */
    public String addStore(String store) {
        String name = addKnown(store);
        setSelected(name, true);
        return name;
    }

    private String addKnown(String store) {
        String name = store == null ? "" : store.trim().replaceAll("\\s+", " ");
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Enter a store name.");
        }
        if (name.length() > MAX_STORE_NAME) {
            throw new IllegalArgumentException("Store names can be at most " + MAX_STORE_NAME + " characters.");
        }
        Optional<String> existing = find(name);
        if (existing.isPresent()) {
            return existing.get();
        }
        stores.add(name);
        return name;
    }

    /** The store's name as written in the list (ignoring case), if it is there. */
    public Optional<String> find(String store) {
        if (store == null) {
            return Optional.empty();
        }
        String key = store.trim().toLowerCase(Locale.ROOT);
        return stores.stream().filter(s -> s.toLowerCase(Locale.ROOT).equals(key)).findFirst();
    }

    // ------------------------------------------------------------ sources

    /** Uses this source for one store from now on (e.g. a FileFlyerSource after Upload Flyer). */
    public void setSource(String store, FlyerSource source) {
        if (source == null) {
            throw new IllegalArgumentException("Source must not be null");
        }
        String name = find(store).orElseThrow(() -> new IllegalArgumentException(store + " is not in the store list"));
        sources.put(name, source);
    }

    /** The source used for a store: an uploaded file, or the online source. */
    public FlyerSource getSource(String store) {
        return find(store).map(sources::get).orElse(onlineSource);
    }

    public FlyerSource getOnlineSource() {
        return onlineSource;
    }

    /** The flyer text for a store, from its source. */
    public String fetchRaw(String store) throws FlyerException {
        return getSource(store).fetch(store);
    }

    // ------------------------------------------------------------ flyers

    /** Stores a flyer, replacing the store's older one. */
    public void save(Flyer flyer) {
        if (flyer == null) {
            throw new IllegalArgumentException("Flyer must not be null");
        }
        String name = find(flyer.getStore()).orElseGet(() -> addKnown(flyer.getStore()));
        flyers.put(name, flyer);
    }

    /** Every saved flyer, in store order. */
    public List<Flyer> getFlyers() {
        List<Flyer> result = new ArrayList<>();
        for (String s : stores) {
            Flyer f = flyers.get(s);
            if (f != null) {
                result.add(f);
            }
        }
        return result;
    }

    /** Flyers whose sale is still running on {@code today} (F02 uses only these). */
    public List<Flyer> getCurrentFlyers(LocalDate today) {
        List<Flyer> result = new ArrayList<>();
        for (Flyer f : getFlyers()) {
            if (f.isCurrent(today)) {
                result.add(f);
            }
        }
        return result;
    }

    public Optional<Flyer> getFlyer(String store) {
        return find(store).map(flyers::get);
    }

    /** What is saved to disk: the store list, the selection and the flyers. */
    public Data toData() {
        Data d = new Data();
        d.stores = new ArrayList<>(stores);
        d.selected = getSelectedStores();
        d.flyers = getFlyers();
        return d;
    }

    /** The saved form of a FlyerService (read and written by JsonStorage). */
    public static class Data {
        List<String> stores;
        List<String> selected;
        List<Flyer> flyers;
    }
}
