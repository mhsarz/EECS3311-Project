package com.recipeagent.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Saves and loads application data as JSON files in one folder, one file per key
 * (e.g. key "pantry" is stored in data/pantry.json). Used by AgentController to keep
 * the pantry, cart, recipe history and profile between runs.
 */
public class JsonStorage {

    private static final Pattern VALID_KEY = Pattern.compile("[A-Za-z0-9_-]+");

    private final Path folder;
    private final Gson gson;

    public JsonStorage(Path folder) {
        if (folder == null) {
            throw new IllegalArgumentException("Storage folder must not be null");
        }
        this.folder = folder;
        this.gson = createGson();
    }

    /** A Gson instance that handles LocalDate; also usable for parsing LLM JSON replies. */
    public static Gson createGson() {
        return new GsonBuilder()
                .registerTypeAdapter(java.time.LocalDate.class, new LocalDateAdapter().nullSafe())
                .registerTypeAdapter(com.recipeagent.model.StorageLocation.class,
                        new StorageLocationAdapter().nullSafe())
                .setPrettyPrinting()
                .create();
    }

    public Path getFolder() {
        return folder;
    }

    /**
     * Writes {@code data} as JSON under {@code key}, replacing any previous value.
     * The file is written to a temporary file first and then moved into place, so a
     * crash during saving does not leave a half-written file.
     */
    public void save(String key, Object data) {
        Path target = fileFor(key);
        Path tmp = null;
        try {
            Files.createDirectories(folder);
            tmp = Files.createTempFile(folder, key, ".tmp");
            Files.writeString(tmp, gson.toJson(data), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            deleteQuietly(tmp);
            throw new StorageException("Could not save '" + key + "' to " + target, e);
        }
    }

    /**
     * Reads the value saved under {@code key}.
     *
     * @return the value, or null if nothing has been saved under this key yet
     * @throws StorageException if the file exists but cannot be read or is not valid JSON
     */
    public <T> T load(String key, Type type) {
        Path file = fileFor(key);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            return gson.fromJson(json, type);
        } catch (IOException | JsonParseException e) {
            throw new StorageException("Saved data for '" + key + "' in " + file
                    + " is damaged: " + e.getMessage(), e);
        }
    }

    /** Reads an object of the given class, or null if nothing is saved. */
    public <T> T load(String key, Class<T> type) {
        return load(key, (Type) type);
    }

    /** Reads a list of the given element type; returns an empty list if nothing is saved. */
    public <T> List<T> loadList(String key, Class<T> elementType) {
        Type listType = TypeToken.getParameterized(List.class, elementType).getType();
        List<T> result = load(key, listType);
        return result == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(result);
    }

    /** Removes a leftover temporary file after a failed save. */
    private static void deleteQuietly(Path tmp) {
        if (tmp == null) {
            return;
        }
        try {
            Files.deleteIfExists(tmp);
        } catch (IOException ignored) {
            // the failed save is already being reported
        }
    }

    public boolean exists(String key) {
        return Files.isRegularFile(fileFor(key));
    }

    private Path fileFor(String key) {
        if (key == null || !VALID_KEY.matcher(key).matches()) {
            throw new IllegalArgumentException(
                    "Invalid storage key '" + key + "' (use letters, digits, '-' or '_')");
        }
        return folder.resolve(key + ".json");
    }
}
