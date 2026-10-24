package com.recipeagent.storage;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/** Stores LocalDate as an ISO string such as "2027-03-01" (null stays null). */
class LocalDateAdapter extends TypeAdapter<LocalDate> {

    @Override
    public void write(JsonWriter out, LocalDate value) throws IOException {
        if (value == null) {
            out.nullValue();
        } else {
            out.value(value.toString());
        }
    }

    @Override
    public LocalDate read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return null;
        }
        String text = in.nextString();
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException e) {
            throw new IOException("Invalid date '" + text + "' (expected YYYY-MM-DD)", e);
        }
    }
}
