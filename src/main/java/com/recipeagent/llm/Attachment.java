package com.recipeagent.llm;

import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

/**
 * A file sent to the model with a prompt, such as a store's PDF flyer (F10). The model reads
 * the file itself: for a PDF, the text and a picture of every page.
 */
public final class Attachment {

    public static final String PDF = "application/pdf";

    private final String mediaType;
    private final byte[] data;
    private final String name;

    private Attachment(String mediaType, byte[] data, String name) {
        this.mediaType = mediaType;
        this.data = data;
        this.name = name;
    }

    /** A PDF file; {@code name} is only used in messages. */
    public static Attachment pdf(byte[] data, String name) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("The PDF must not be empty");
        }
        return new Attachment(PDF, data.clone(), name == null || name.isBlank() ? "file.pdf" : name);
    }

    public String getMediaType() {
        return mediaType;
    }

    public String getName() {
        return name;
    }

    public int getSize() {
        return data.length;
    }

    /** The file as base64, the way the Messages API takes it. */
    public String toBase64() {
        return Base64.getEncoder().encodeToString(data);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Attachment a && mediaType.equals(a.mediaType) && Arrays.equals(data, a.data)
                && name.equals(a.name);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hash(mediaType, name) + Arrays.hashCode(data);
    }

    @Override
    public String toString() {
        return name + " (" + mediaType + ", " + data.length + " bytes)";
    }
}
