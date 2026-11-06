package com.recipeagent.flyer;

import java.util.Arrays;

/**
 * A store's flyer as it came from its source (F10): either text (a .txt file or a web page,
 * already cleaned) or a PDF file that the AI reads as a document, pages and all. PDF flyers
 * are often just pictures of the printed flyer, so they are sent as they are, not as text.
 */
public final class FlyerContent {

    private final String text;
    private final byte[] pdf;
    private final String fileName;
    private final int pageCount;

    private FlyerContent(String text, byte[] pdf, String fileName) {
        this.text = text;
        this.pdf = pdf;
        this.fileName = fileName;
        this.pageCount = pdf == null ? -1 : countPages(pdf);
    }

    /** A flyer as text. */
    public static FlyerContent text(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Flyer text must not be empty");
        }
        return new FlyerContent(text, null, null);
    }

    /** A flyer as a PDF file; {@code fileName} is used in messages, e.g. "flyer.pdf". */
    public static FlyerContent pdf(byte[] bytes, String fileName) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("PDF must not be empty");
        }
        return new FlyerContent(null, bytes.clone(), fileName == null || fileName.isBlank() ? "flyer.pdf" : fileName);
    }

    public boolean isPdf() {
        return pdf != null;
    }

    /** The flyer text, or null for a PDF. */
    public String getText() {
        return text;
    }

    /** A copy of the PDF bytes, or null for text. */
    public byte[] getPdf() {
        return pdf == null ? null : pdf.clone();
    }

    /** The PDF's file name, or null for text. */
    public String getFileName() {
        return fileName;
    }

    /** The number of pages in the PDF, or -1 if it could not be counted (or this is text). */
    public int getPageCount() {
        return pageCount;
    }

    /**
     * Counts the pages of a PDF without a PDF library: each page is an object with
     * "/Type /Page"; if those are hidden in compressed object streams, the page tree's
     * "/Count n" is used instead. Returns -1 if neither is found.
     */
    static int countPages(byte[] b) {
        int pages = 0;
        int maxCount = -1;
        for (int i = 0; i < b.length; i++) {
            if (matches(b, i, "/Type")) {
                int j = skipSpaces(b, i + 5);
                if (matches(b, j, "/Page") && !isLetter(b, j + 5)) {
                    pages++;
                }
            } else if (matches(b, i, "/Count")) {
                int j = skipSpaces(b, i + 6);
                int n = 0;
                int digits = 0;
                while (j < b.length && b[j] >= '0' && b[j] <= '9' && digits < 6) {
                    n = n * 10 + (b[j] - '0');
                    j++;
                    digits++;
                }
                if (digits > 0) {
                    maxCount = Math.max(maxCount, n);
                }
            }
        }
        if (pages > 0) {
            return pages;
        }
        return maxCount > 0 ? maxCount : -1;
    }

    private static boolean matches(byte[] b, int at, String word) {
        if (at < 0 || at + word.length() > b.length) {
            return false;
        }
        for (int k = 0; k < word.length(); k++) {
            if (b[at + k] != word.charAt(k)) {
                return false;
            }
        }
        return true;
    }

    private static int skipSpaces(byte[] b, int at) {
        while (at < b.length && (b[at] == ' ' || b[at] == '\r' || b[at] == '\n' || b[at] == '\t')) {
            at++;
        }
        return at;
    }

    private static boolean isLetter(byte[] b, int at) {
        return at < b.length && Character.isLetter((char) (b[at] & 0xff));
    }

    @Override
    public String toString() {
        return isPdf() ? "PDF " + fileName + " (" + pdf.length + " bytes, "
                + (pageCount < 0 ? "pages unknown" : pageCount + (pageCount == 1 ? " page" : " pages")) + ")"
                : "text (" + text.length() + " characters)";
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof FlyerContent other)) {
            return false;
        }
        return java.util.Objects.equals(text, other.text) && Arrays.equals(pdf, other.pdf)
                && java.util.Objects.equals(fileName, other.fileName);
    }

    @Override
    public int hashCode() {
        return 31 * java.util.Objects.hash(text, fileName) + Arrays.hashCode(pdf);
    }
}
