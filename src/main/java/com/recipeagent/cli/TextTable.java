package com.recipeagent.cli;

import java.util.ArrayList;
import java.util.List;

/** Prints rows as columns padded with spaces, with a header and a dashed line under it. */
final class TextTable {

    private final List<String[]> rows = new ArrayList<>();
    private final String[] header;
    /** Longest text in a column before it is cut with "..." (keeps lines readable). */
    static final int MAX_WIDTH = 40;

    TextTable(String... header) {
        this.header = header;
    }

    TextTable row(Object... cells) {
        String[] r = new String[header.length];
        for (int i = 0; i < header.length; i++) {
            Object c = i < cells.length ? cells[i] : "";
            r[i] = cut(c == null ? "" : c.toString());
        }
        rows.add(r);
        return this;
    }

    boolean isEmpty() {
        return rows.isEmpty();
    }

    static String cut(String s) {
        return s.length() <= MAX_WIDTH ? s : s.substring(0, MAX_WIDTH - 3) + "...";
    }

    /** The table as text, one line per row, without trailing spaces. */
    String render() {
        int[] width = new int[header.length];
        for (int i = 0; i < header.length; i++) {
            width[i] = header[i].length();
            for (String[] r : rows) {
                width[i] = Math.max(width[i], r[i].length());
            }
        }
        StringBuilder sb = new StringBuilder();
        line(sb, header, width);
        String[] dashes = new String[header.length];
        for (int i = 0; i < header.length; i++) {
            dashes[i] = "-".repeat(width[i]);
        }
        line(sb, dashes, width);
        for (String[] r : rows) {
            line(sb, r, width);
        }
        return sb.toString();
    }

    private static void line(StringBuilder sb, String[] cells, int[] width) {
        StringBuilder l = new StringBuilder();
        for (int i = 0; i < cells.length; i++) {
            l.append(cells[i]);
            if (i < cells.length - 1) {
                l.append(" ".repeat(width[i] - cells[i].length() + 2));
            }
        }
        sb.append(l.toString().replaceAll("\\s+$", "")).append(System.lineSeparator());
    }
}
