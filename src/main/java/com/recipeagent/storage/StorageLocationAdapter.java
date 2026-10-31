package com.recipeagent.storage;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.recipeagent.model.StorageLocation;

import java.io.IOException;

/**
 * Reads a storage location ignoring case ("fridge" and "FRIDGE" both work) and rejects
 * unknown values. Without this, Gson silently turns an unknown or lower-case value
 * into null, which breaks the pantry later.
 */
class StorageLocationAdapter extends TypeAdapter<StorageLocation> {

    @Override
    public void write(JsonWriter out, StorageLocation value) throws IOException {
        if (value == null) {
            out.nullValue();
        } else {
            out.value(value.name());
        }
    }

    @Override
    public StorageLocation read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return null;
        }
        String text = in.nextString();
        try {
            return StorageLocation.parse(text);
        } catch (IllegalArgumentException e) {
            throw new IOException(e.getMessage(), e);
        }
    }
}
