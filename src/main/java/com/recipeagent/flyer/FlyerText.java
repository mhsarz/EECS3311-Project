package com.recipeagent.flyer;

/** Turns flyer content into plain text for the LLM: HTML tags and scripts removed, spaces tidied. */
final class FlyerText {

    /** Longest flyer text kept; longer text is cut (a flyer is a few thousand words). */
    static final int MAX_CHARS = 60_000;

    private FlyerText() {
    }

    static boolean looksLikeHtml(String s) {
        String head = s.substring(0, Math.min(s.length(), 2000)).toLowerCase(java.util.Locale.ROOT);
        return head.contains("<html") || head.contains("<!doctype html") || head.contains("<body")
                || head.contains("<div") || head.contains("<p>") || head.contains("<table");
    }

    /** Plain text from HTML: drops script/style blocks and tags, decodes common entities. */
    static String fromHtml(String html) {
        String s = html.replaceAll("(?is)<(script|style|noscript)[^>]*>.*?</\\1\\s*>", " ");
        s = s.replaceAll("(?i)<br\\s*/?>|</(p|div|li|tr|h[1-6])\\s*>", "\n");
        s = s.replaceAll("(?s)<[^>]*>", " ");
        s = s.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&dollar;", "$");
        return tidy(s);
    }

    /** Collapses runs of spaces and blank lines, removes a byte order mark, and cuts very long text. */
    static String tidy(String s) {
        String t = s.replace("﻿", "").replace("\r\n", "\n").replace('\r', '\n');
        t = t.replaceAll("[ \\t\\x0B\\f]+", " ").replaceAll(" *\n *", "\n").replaceAll("\n{3,}", "\n\n").trim();
        return t.length() > MAX_CHARS ? t.substring(0, MAX_CHARS) : t;
    }

    static String clean(String raw) {
        return looksLikeHtml(raw) ? fromHtml(raw) : tidy(raw);
    }
}
