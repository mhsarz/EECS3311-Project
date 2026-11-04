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
}
