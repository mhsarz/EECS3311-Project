package com.recipeagent.gui;

import com.recipeagent.agent.AgentAction;
import com.recipeagent.agent.tools.CartTool;
import com.recipeagent.agent.tools.ProfileTool;

import java.util.ArrayList;
import java.util.List;

/** Text helpers for the Chat panel. No Swing code, so they can be tested without a screen. */
final class ChatFormat {

    static final int BUBBLE_WIDTH = 150;

    private ChatFormat() {
    }

    /**
     * Short markers for what the agent changed, shown under its answer, e.g. "Cart updated".
     * Read-only tool calls (looking at the pantry) are not shown.
     */
    static List<String> changeMarkers(List<AgentAction> actions) {
        List<String> markers = new ArrayList<>();
        for (AgentAction a : actions) {
            String marker = null;
            if (a.succeededWith(CartTool.NAME) && !a.args().contains("\"list\"") && !a.args().equals("list")) {
                marker = "Cart updated";
            } else if (a.succeededWith(ProfileTool.NAME) && a.args().contains("save")) {
                marker = "Profile saved";
            }
            if (marker != null && !markers.contains(marker)) {
                markers.add(marker);
            }
        }
        return markers;
    }

    /**
     * Wraps text for a chat bubble (Swing labels wrap HTML at a fixed width). The agent is told
     * to write plain text, but if it still uses Markdown, **bold** is shown in bold and "# "
     * headings and "* " bullets are tidied, instead of showing the symbols.
     */
    static String bubbleHtml(String text) {
        return "<html><div style='width:" + BUBBLE_WIDTH + "px'>"
                + markdownToHtml(escape(text)).replace("\n", "<br>") + "</div></html>";
    }

    /** **bold** -> <b>bold</b>; "# Title" -> "Title"; "* item" -> "- item" (text already escaped). */
    static String markdownToHtml(String escaped) {
        return escaped.replaceAll("\\*\\*(.+?)\\*\\*", "<b>$1</b>")
                .replaceAll("(?m)^#{1,6}\\s+", "")
                .replaceAll("(?m)^(\\s*)\\*\\s+", "$1- ");
    }

    static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
