package com.recipeagent.flyer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A flyer the user uploaded as a file (F10), e.g. the text copied from a store's flyer page
 * into nofrills.txt. Plain text and saved web pages (.html) both work. Files that are empty,
 * too large or not text (a PDF or an image) are rejected with a message.
 */
public class FileFlyerSource implements FlyerSource {

    /** Largest file accepted. */
    public static final long MAX_BYTES = 2_000_000;

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
    public String fetch(String store) throws FlyerException {
        String name = file.getFileName() == null ? file.toString() : file.getFileName().toString();
        if (!Files.isRegularFile(file)) {
            throw new FlyerException("The file " + name + " was not found.", false);
        }
        byte[] bytes;
        try {
            if (Files.size(file) > MAX_BYTES) {
                throw new FlyerException("The file " + name + " is too large (over 2 MB). Copy just the flyer text into a .txt file.", false);
            }
            bytes = Files.readAllBytes(file);
        } catch (IOException e) {
            throw new FlyerException("The file " + name + " could not be read.", false);
        }
        if (isBinary(bytes)) {
            throw new FlyerException("The file " + name + " is not a text file. Save or copy the flyer as text (.txt) first.", false);
        }
        String text = FlyerText.clean(decode(bytes));
        if (text.isBlank()) {
            throw new FlyerException("The file " + name + " is empty.", false);
        }
        return text;
    }

    @Override
    public String describe() {
        return "the file " + (file.getFileName() == null ? file : file.getFileName());
    }

    /** A PDF, image or other binary file has zero bytes or starts like one. */
    static boolean isBinary(byte[] bytes) {
        if (bytes.length >= 4 && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F') {
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
