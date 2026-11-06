package com.recipeagent.flyer;

import com.recipeagent.model.Flyer;
import com.recipeagent.model.FlyerItem;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FlyerServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 5);

    private static FlyerSource fixed(String text) {
        return new FlyerSource() {
            @Override
            public FlyerContent fetch(String store) {
                return FlyerContent.text(text + " for " + store);
            }

            @Override
            public String describe() {
                return "test";
            }
        };
    }

    private static Flyer flyer(String store, LocalDate ends) {
        return new Flyer(store, ends, List.of(new FlyerItem("Milk 4L", 5.49)));
    }

    @Test
    void startsWithDefaultStoresNoneSelected() {
        FlyerService s = new FlyerService(fixed("online"));
        assertEquals(FlyerService.DEFAULT_STORES, s.getStores());
        assertTrue(s.getSelectedStores().isEmpty());
        assertTrue(s.setSelected("no frills", true));                 // any case
        assertTrue(s.setSelected("Metro", true));
        assertFalse(s.setSelected("Costco", true));                    // not in the list
        assertEquals(List.of("No Frills", "Metro"), s.getSelectedStores());   // list order
        s.setSelected("Metro", false);
        assertEquals(List.of("No Frills"), s.getSelectedStores());
    }

    @Test
    void addStoreSelectsItAndRejectsBadNames() {
        FlyerService s = new FlyerService(fixed("online"));
        assertEquals("Costco", s.addStore("  Costco "));
        assertEquals("Costco", s.addStore("costco"));                 // already there
        assertEquals(1, s.getStores().stream().filter(x -> x.equals("Costco")).count());
        assertTrue(s.isSelected("COSTCO"));
        assertThrows(IllegalArgumentException.class, () -> s.addStore(" "));
        assertThrows(IllegalArgumentException.class, () -> s.addStore("x".repeat(41)));
    }

    @Test
    void eachStoreUsesTheOnlineSourceUnlessAFileWasUploaded() throws Exception {
        FlyerService s = new FlyerService(fixed("online"));
        assertEquals("online for Metro", s.fetchRaw("Metro").getText());
        FileFlyerSource file = new FileFlyerSource(Path.of("nofrills.txt"));
        s.setSource("No Frills", file);
        assertSame(file, s.getSource("no frills"));
        assertEquals("online for Metro", s.fetchRaw("Metro").getText());
        assertThrows(IllegalArgumentException.class, () -> s.setSource("Costco", file));
    }

    @Test
    void savingReplacesTheStoresOldFlyerAndKeepsCurrentOnes() {
        FlyerService s = new FlyerService(fixed("online"));
        s.save(flyer("Metro", TODAY.minusDays(1)));                    // ended yesterday
        s.save(flyer("No Frills", TODAY));
        s.save(flyer("No Frills", null));                             // replaces it
        assertEquals(2, s.getFlyers().size());
        assertEquals("No Frills", s.getFlyers().get(0).getStore());   // store-list order
        assertNull(s.getFlyer("no frills").orElseThrow().getEndDate());
        assertEquals(List.of("No Frills"), s.getCurrentFlyers(TODAY).stream().map(Flyer::getStore).toList());
        s.save(flyer("Costco", null));                                // unknown store is added
        assertTrue(s.getStores().contains("Costco"));
    }

    @Test
    void savedDataComesBack() {
        FlyerService s = new FlyerService(fixed("online"));
        s.addStore("Costco");
        s.setSelected("Metro", true);
        s.save(flyer("Metro", TODAY));
        FlyerService again = new FlyerService(fixed("online"), s.toData());
        assertEquals(s.getStores(), again.getStores());
        assertEquals(List.of("Metro", "Costco"), again.getSelectedStores());
        assertEquals(1, again.getFlyers().size());
        assertEquals(FlyerService.DEFAULT_STORES, new FlyerService(fixed("x"), null).getStores());
    }
}
