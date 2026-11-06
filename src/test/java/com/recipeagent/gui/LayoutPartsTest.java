package com.recipeagent.gui;

import com.recipeagent.agent.AgentAction;
import com.recipeagent.model.Notification;
import com.recipeagent.model.NotificationType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Tests the shared layout parts of the redesigned window. These run without a screen. */
class LayoutPartsTest {

    @Test
    void chatShowsMarkersOnlyForChanges() {
        List<AgentAction> actions = List.of(
                new AgentAction("pantry", "{\"action\":\"list\"}", "{\"items\":[]}"),
                new AgentAction("cart", "{\"action\":\"list\"}", "{\"items\":[]}"),
                new AgentAction("cart", "{\"action\":\"add\",\"name\":\"eggs\"}", "{\"added\":\"eggs\"}"),
                new AgentAction("cart", "{\"action\":\"add\",\"name\":\"milk\"}", "{\"added\":\"milk\"}"),
                new AgentAction("profile", "{\"action\":\"save\",\"diet\":\"vegan\"}", "{\"saved\":true}"));
        assertEquals(List.of("Cart updated", "Profile saved"), ChatFormat.changeMarkers(actions));
    }

    @Test
    void failedChangesGetNoMarker() {
        List<AgentAction> actions = List.of(
                new AgentAction("cart", "{\"action\":\"add\"}", "{\"error\":\"'add' needs a 'name'\"}"),
                new AgentAction("profile", "get", "{\"diet\":\"none\"}"));
        assertTrue(ChatFormat.changeMarkers(actions).isEmpty());
        assertTrue(ChatFormat.changeMarkers(List.of()).isEmpty());
    }

    @Test
    void chatBubbleTextIsEscapedAndWrapped() {
        String html = ChatFormat.bubbleHtml("Use <b>2</b> eggs & milk\nthen fry");
        assertTrue(html.startsWith("<html><div style='width:"));
        assertTrue(html.contains("Use &lt;b&gt;2&lt;/b&gt; eggs &amp; milk<br>then fry"));
    }

    @Test
    void markdownFromTheAgentIsShownAsText() {
        String html = ChatFormat.bubbleHtml("## Saved\n- **Diet:** none\n* **Portions:** 2 & more");
        assertTrue(html.contains("Saved<br>- <b>Diet:</b> none<br>- <b>Portions:</b> 2 &amp; more"));
        assertFalse(html.contains("**"));
        assertEquals("2 * 3 = 6", ChatFormat.markdownToHtml("2 * 3 = 6"));                // a lone star stays
    }

    @Test
    void headerShowsReadableDate() {
        assertEquals("Wed, Nov 4, 2026", HeaderBar.formatDate(LocalDate.of(2026, 11, 4)));
    }

    @Test
    void alertTextNamesTheKind() {
        assertEquals("Expiring: milk expires tomorrow",
                AlertsStrip.text(new Notification(NotificationType.EXPIRY, "milk expires tomorrow")));
        assertEquals("On sale: milk at No Frills",
                AlertsStrip.text(new Notification(NotificationType.SALE, "milk at No Frills")));
    }

    @Test
    void tabBarSelectsFirstTabAndNotifiesListeners() {
        TabBar bar = new TabBar();
        List<String> selected = new ArrayList<>();
        bar.onSelect(selected::add);
        bar.addTab("Pantry");
        bar.addTab("Recipe");
        assertEquals("Pantry", bar.getSelected());
        bar.select("Recipe");
        assertEquals("Recipe", bar.getSelected());
        assertEquals(List.of("Pantry", "Recipe"), selected);
        assertEquals(List.of("Pantry", "Recipe"), bar.getTabNames());
        assertThrows(IllegalArgumentException.class, () -> bar.select("Garage"));
    }

    @Test
    void aiButtonsCarryTheBadge() {
        assertTrue(((Theme.FlatButton) Theme.ai("Generate Recipe", true)).hasAiBadge());
        assertFalse(((Theme.FlatButton) Theme.primary("Add item")).hasAiBadge());
        Theme.FlatButton b = (Theme.FlatButton) Theme.secondary("Cancel");
        b.setEnabled(false);
        assertEquals(Theme.MUTED, b.getForeground());
        b.setEnabled(true);
        assertEquals(Theme.TEXT, b.getForeground());
    }

    @Test
    void pageHeaderAndFooterShowTheirText() {
        PageHeader h = new PageHeader("Pantry", "Know what you have.");
        h.setRight("6 items");
        assertEquals("6 items", h.getRightText());
        StatusFooter f = new StatusFooter();
        f.setAiStatus("AI ready (98 of 100 calls left today)", true);
        assertEquals("AI ready (98 of 100 calls left today)", f.getAiText());
    }

    @Test
    void themeInstallsWithOrWithoutFlatLaf() {
        boolean flat = Theme.install();
        boolean flatOnClasspath;
        try {
            Class.forName(Theme.FLATLAF_CLASS);
            flatOnClasspath = true;
        } catch (ClassNotFoundException e) {
            flatOnClasspath = false;
        }
        assertEquals(flatOnClasspath, flat);
        assertEquals(Theme.BACKGROUND, javax.swing.UIManager.getColor("Panel.background"));
    }

    @Test
    void datePickerCalendarShowsSixWeeksFromSunday() {
        java.util.List<java.time.LocalDate> nov = DatePicker.monthCells(java.time.YearMonth.of(2026, 11));
        assertEquals(42, nov.size());
        assertEquals(java.time.LocalDate.of(2026, 11, 1), nov.get(0));     // Nov 1, 2026 is a Sunday
        assertEquals(java.time.LocalDate.of(2026, 12, 12), nov.get(41));
        java.util.List<java.time.LocalDate> feb = DatePicker.monthCells(java.time.YearMonth.of(2027, 2));
        assertEquals(java.time.LocalDate.of(2027, 1, 31), feb.get(0));     // starts on the Sunday before
        assertEquals(java.time.DayOfWeek.SUNDAY, feb.get(0).getDayOfWeek());
        assertEquals("November 2026", DatePicker.monthTitle(java.time.YearMonth.of(2026, 11)));
        assertEquals(java.util.List.of("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa"), DatePicker.weekdayNames());
    }

    @Test
    void datePickerShowsTheChosenDate() {
        java.time.LocalDate today = java.time.LocalDate.of(2026, 11, 5);
        DatePicker p = new DatePicker(today.plusDays(7), () -> today);
        assertEquals(today.plusDays(7), p.getDate());
        assertEquals("Nov 12, 2026", p.getShownText());
        p.setDate(java.time.LocalDate.of(2027, 1, 3));
        assertEquals("Jan 3, 2027", p.getShownText());
        assertThrows(IllegalArgumentException.class, () -> p.setDate(null));
    }
}
