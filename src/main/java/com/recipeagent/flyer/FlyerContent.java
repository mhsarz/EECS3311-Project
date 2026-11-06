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

    private FlyerContent(String text, byte[] pdf, String fileName) {
        this.text = text;
        this.pdf = pdf;
        this.fileName = fileName;
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

    @Override
    public String toString() {
        return isPdf() ? "PDF " + fileName + " (" + pdf.length + " bytes)" : "text (" + text.length() + " characters)";
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
