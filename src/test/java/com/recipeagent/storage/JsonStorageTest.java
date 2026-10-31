package com.recipeagent.storage;

import com.recipeagent.model.PantryItem;
import com.recipeagent.model.StorageLocation;
import com.recipeagent.model.UserProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JsonStorageTest {

    @TempDir
    Path tmp;

    @Test
    void savesAndLoadsPantryWithDates() {
        JsonStorage storage = new JsonStorage(tmp.resolve("data"));
        PantryItem rice = new PantryItem("rice", 2, "kg", StorageLocation.PANTRY,
                LocalDate.of(2027, 3, 1));
        PantryItem salt = new PantryItem("salt", 1, "kg", StorageLocation.PANTRY, null);

        storage.save("pantry", List.of(rice, salt));
        List<PantryItem> loaded = storage.loadList("pantry", PantryItem.class);

        assertEquals(2, loaded.size());
        assertEquals(rice.getId(), loaded.get(0).getId());
        assertEquals("rice", loaded.get(0).getName());
        assertEquals(StorageLocation.PANTRY, loaded.get(0).getLocation());
        assertEquals(LocalDate.of(2027, 3, 1), loaded.get(0).getBestBefore());
        assertNull(loaded.get(1).getBestBefore());
    }

    @Test
    void savesAndLoadsSingleObject() {
        JsonStorage storage = new JsonStorage(tmp);
        UserProfile p = new UserProfile();
        p.setDiet("vegetarian");
        p.addAllergy("peanuts");
        storage.save("profile", p);

        UserProfile loaded = storage.load("profile", UserProfile.class);
        assertEquals("vegetarian", loaded.getDiet());
        assertEquals(List.of("peanuts"), loaded.getAllergies());
    }

    @Test
    void saveReplacesOldValue() {
        JsonStorage storage = new JsonStorage(tmp);
        storage.save("pantry", List.of(new PantryItem("a", 1, "", StorageLocation.FRIDGE, null)));
        storage.save("pantry", List.of());
        assertTrue(storage.loadList("pantry", PantryItem.class).isEmpty());
    }

    @Test
    void missingKeyGivesNullOrEmptyList() {
        JsonStorage storage = new JsonStorage(tmp.resolve("never-created"));
        assertFalse(storage.exists("pantry"));
        assertNull(storage.load("profile", UserProfile.class));
        assertTrue(storage.loadList("pantry", PantryItem.class).isEmpty());
    }

    @Test
    void rejectsUnsafeKeys() {
        JsonStorage storage = new JsonStorage(tmp);
        assertThrows(IllegalArgumentException.class, () -> storage.save("../secret", "x"));
        assertThrows(IllegalArgumentException.class, () -> storage.save("", "x"));
        assertThrows(IllegalArgumentException.class, () -> storage.load(null, String.class));
    }

    @Test
    void damagedFileThrowsStorageException() throws IOException {
        Files.writeString(tmp.resolve("pantry.json"), "{ this is not json");
        JsonStorage storage = new JsonStorage(tmp);
        assertThrows(StorageException.class, () -> storage.loadList("pantry", PantryItem.class));
    }

    @Test
    void invalidDateThrowsStorageException() throws IOException {
        Files.writeString(tmp.resolve("pantry.json"),
                "[{\"id\":\"1\",\"name\":\"milk\",\"quantity\":1,\"unit\":\"L\","
                        + "\"location\":\"FRIDGE\",\"bestBefore\":\"next week\"}]");
        JsonStorage storage = new JsonStorage(tmp);
        assertThrows(StorageException.class, () -> storage.loadList("pantry", PantryItem.class));
    }

    @Test
    void readsLocationIgnoringCase() throws IOException {
        Files.writeString(tmp.resolve("pantry.json"),
                "[{\"id\":\"1\",\"name\":\"milk\",\"quantity\":1,\"unit\":\"L\",\"location\":\"fridge\"}]");
        List<PantryItem> items = new JsonStorage(tmp).loadList("pantry", PantryItem.class);
        assertEquals(StorageLocation.FRIDGE, items.get(0).getLocation());
    }

    @Test
    void unknownLocationThrowsStorageException() throws IOException {
        Files.writeString(tmp.resolve("pantry.json"),
                "[{\"id\":\"1\",\"name\":\"milk\",\"quantity\":1,\"unit\":\"L\",\"location\":\"garage\"}]");
        JsonStorage storage = new JsonStorage(tmp);
        StorageException e = assertThrows(StorageException.class,
                () -> storage.loadList("pantry", PantryItem.class));
        assertTrue(e.getMessage().contains("garage"));
    }

    @Test
    void savesLocationAsUpperCaseName() throws IOException {
        JsonStorage storage = new JsonStorage(tmp);
        storage.save("pantry", List.of(new PantryItem("peas", 1, "bag", StorageLocation.FREEZER, null)));
        assertTrue(Files.readString(tmp.resolve("pantry.json")).contains("\"FREEZER\""));
    }

    @Test
    void leavesNoTempFilesBehind() throws IOException {
        JsonStorage storage = new JsonStorage(tmp);
        storage.save("cart", List.of("milk"));
        try (var files = Files.list(tmp)) {
            assertEquals(List.of(tmp.resolve("cart.json")), files.toList());
        }
    }
}
