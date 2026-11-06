package com.recipeagent.flyer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A flyer the user uploaded as a file (F10): a PDF downloaded from the store's website, the
 * text copied from a flyer page into nofrills.txt, or a saved web page (.html). PDFs are
 * passed on as they are (the AI reads their pages, including pictures); text is cleaned.
 * Files that are empty, too large, password-protected or another binary kind (an image)
 * are rejected with a message.
 */
public class FileFlyerSource implements FlyerSource {

    /** Largest text or web page file accepted. */
    public static final long MAX_BYTES = 2_000_000;
    /**
     * Largest PDF accepted. The API takes requests up to 128 MB and base64 makes a file about
     * a third bigger, so 32 MB leaves plenty of room; flyer PDFs are usually 2-30 MB.
     */
    public static final long MAX_PDF_BYTES = 32_000_000;

    private final Path file;

    public FileFlyerSource(Path file) {
        if (file == null) {
            throw new IllegalArgumentException("File is required");
        }
        this.file = file;
    }

    public Path getFile() {
        return file;
    }

    @Override
    public FlyerContent fetch(String store) throws FlyerException {
        String name = file.getFileName() == null ? file.toString() : file.getFileName().toString();
        if (!Files.isRegularFile(file)) {
            throw new FlyerException("The file " + name + " was not found.", false);
        }
        byte[] bytes;
        try {
            long size = Files.size(file);
            boolean pdf = startsLikePdf(file);
            if (pdf && size > MAX_PDF_BYTES) {
                throw new FlyerException("The PDF " + name + " is too large (over 32 MB). Save only the pages "
                        + "with the sale items, or download a smaller version of the flyer.", false);
            }
            if (!pdf && size > MAX_BYTES) {
                throw new FlyerException("The file " + name + " is too large (over 2 MB). Copy just the flyer text into a .txt file.", false);
            }
            bytes = Files.readAllBytes(file);
        } catch (IOException e) {
            throw new FlyerException("The file " + name + " could not be read.", false);
        }
        if (isPdf(bytes)) {
            return pdfContent(bytes, name);
        }
        if (isBinary(bytes)) {
            throw new FlyerException("The file " + name + " is not a text or PDF file. Use the flyer's PDF, "
                    + "or save or copy the flyer as text (.txt) first.", false);
        }
        String text = FlyerText.clean(decode(bytes));
        if (text.isBlank()) {
            throw new FlyerException("The file " + name + " is empty.", false);
        }
        return FlyerContent.text(text);
    }

    /** Checks a PDF the AI can open: not password-protected, with at least one page. */
    static FlyerContent pdfContent(byte[] bytes, String name) throws FlyerException {
        if (isEncrypted(bytes)) {
            throw new FlyerException("The PDF " + name + " is password-protected, so the AI cannot read it. "
                    + "Download the flyer again without a password, or save it as text.", false);
        }
        return FlyerContent.pdf(bytes, name);
    }

    private static boolean startsLikePdf(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return isPdf(in.readNBytes(5));
        }
    }

    @Override
    public String describe() {
        return "the file " + (file.getFileName() == null ? file : file.getFileName());
    }

    /** True if the bytes start like a PDF file ("%PDF-"). */
    static boolean isPdf(byte[] bytes) {
        return bytes.length >= 5 && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F'
                && bytes[4] == '-';
    }

    /** True if the PDF has an /Encrypt entry (password-protected or restricted). */
    static boolean isEncrypted(byte[] bytes) {
        byte[] key = "/Encrypt".getBytes(StandardCharsets.US_ASCII);
        outer:
        for (int i = 0; i <= bytes.length - key.length; i++) {
            for (int k = 0; k < key.length; k++) {
                if (bytes[i + k] != key[k]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    /** An image or other binary file has zero bytes near the start, or starts like a PDF. */
    static boolean isBinary(byte[] bytes) {
        if (isPdf(bytes)) {
            return true;
        }
        int check = Math.min(bytes.length, 8000);
        for (int i = 0; i < check; i++) {
            if (bytes[i] == 0) {
                return true;
            }
        }
        return false;
    }

    /** UTF-8 if the bytes are valid UTF-8, otherwise Windows-1252 (what Notepad often saves). */
    static String decode(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, Charset.forName("windows-1252"));
        }
    }
}
