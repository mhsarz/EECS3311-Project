package com.recipeagent.cli;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One CLI command split into words and options, e.g.
 * {@code pantry add green onions --qty 2 --unit bunch} gives the words
 * [pantry, add, green, onions] and the options qty=2, unit=bunch.
 * <ul>
 *   <li>Quotes group words: {@code flyers upload ./nofrills.txt --store "No Frills"}.</li>
 *   <li>An option without a value is a flag: {@code --save}.</li>
 *   <li>An option can be given more than once: {@code --allergy peanuts --allergy milk}.</li>
 * </ul>
 */
public final class CommandLine {

    private final List<String> words;
    private final Map<String, List<String>> options;

    private CommandLine(List<String> words, Map<String, List<String>> options) {
        this.words = words;
        this.options = options;
    }

    /** Parses a typed line. */
    public static CommandLine parse(String line) {
        return parse(split(line));
    }

    /** Parses words that were already split (program arguments). */
    public static CommandLine parse(List<String> tokens) {
        List<String> words = new ArrayList<>();
        Map<String, List<String>> options = new LinkedHashMap<>();
        for (int i = 0; i < tokens.size(); i++) {
            String t = tokens.get(i);
            if (t.startsWith("--") && t.length() > 2) {
                String name = t.substring(2).toLowerCase(Locale.ROOT);
                String value = "";
                int eq = name.indexOf('=');
                if (eq > 0) {
                    value = t.substring(2 + eq + 1);
                    name = name.substring(0, eq);
                } else if (i + 1 < tokens.size() && !tokens.get(i + 1).startsWith("--")) {
                    value = tokens.get(++i);
                }
                options.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
            } else {
                words.add(t);
            }
        }
        return new CommandLine(words, options);
    }

    /** Splits a line at spaces, keeping "quoted text" together (single or double quotes). */
    static List<String> split(String line) {
        List<String> result = new ArrayList<>();
        if (line == null) {
            return result;
        }
        StringBuilder current = new StringBuilder();
        char quote = 0;
        boolean inToken = false;
        for (char c : line.toCharArray()) {
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                } else {
                    current.append(c);
                }
            } else if (c == '"' || c == '\'') {
                quote = c;
                inToken = true;
            } else if (Character.isWhitespace(c)) {
                if (inToken) {
                    result.add(current.toString());
                    current.setLength(0);
                    inToken = false;
                }
            } else {
                current.append(c);
                inToken = true;
            }
        }
        if (inToken) {
            result.add(current.toString());
        }
        return result;
    }

    public List<String> words() {
        return Collections.unmodifiableList(words);
    }

    /** The word at {@code index} in lower case, or "" if there is none. */
    public String word(int index) {
        return index < words.size() ? words.get(index).toLowerCase(Locale.ROOT) : "";
    }

    /** The words from {@code index} on, joined with spaces (e.g. a name of several words). */
    public String rest(int index) {
        return index < words.size() ? String.join(" ", words.subList(index, words.size())).trim() : "";
    }

    public boolean has(String option) {
        return options.containsKey(option);
    }

    /** The option's last value, or {@code fallback} if it was not given (a flag has the value ""). */
    public String option(String option, String fallback) {
        List<String> v = options.get(option);
        return v == null || v.isEmpty() ? fallback : v.get(v.size() - 1);
    }

    /** Every value of an option; values with commas are split ("peanuts,milk"). */
    public List<String> optionList(String option) {
        List<String> result = new ArrayList<>();
        for (String v : options.getOrDefault(option, List.of())) {
            for (String part : v.split(",")) {
                if (!part.isBlank()) {
                    result.add(part.trim());
                }
            }
        }
        return result;
    }

    /** Option names that are not in {@code allowed}, to catch typos like --qyt. */
    public List<String> unknownOptions(String... allowed) {
        List<String> ok = List.of(allowed);
        List<String> result = new ArrayList<>();
        for (String o : options.keySet()) {
            if (!ok.contains(o)) {
                result.add("--" + o);
            }
        }
        return result;
    }
}
