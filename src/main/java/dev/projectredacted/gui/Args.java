package dev.projectredacted.gui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** The arguments of a button press, read defensively: a missing or malformed value gives the fallback. */
public final class Args {
    private final JsonObject json;

    public Args(JsonObject json) {
        this.json = json == null ? new JsonObject() : json;
    }

    public String str(String key) {
        return str(key, "");
    }

    public String str(String key, String fallback) {
        try {
            JsonElement e = json.get(key);
            return e == null || e.isJsonNull() ? fallback : e.getAsString();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    public int num(String key, int fallback) {
        try {
            JsonElement e = json.get(key);
            return e == null || e.isJsonNull() ? fallback : e.getAsInt();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    public boolean bool(String key) {
        try {
            JsonElement e = json.get(key);
            return e != null && !e.isJsonNull() && e.getAsBoolean();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Shift was held while clicking: buy or hand over five at once. */
    public boolean shift() {
        return bool("shift");
    }

    /** The text typed into the input next to the button. */
    public String text() {
        String s = str("text").trim();
        return s.length() > 200 ? s.substring(0, 200) : s;
    }
}
