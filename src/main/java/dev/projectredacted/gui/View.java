package dev.projectredacted.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.world.level.ItemLike;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything one screen shows: the frame (header, sidebar tabs, footer) and its content (GUI design §2.1).
 * Sizes: large is 320×200, medium 280×170, small 200×110 GUI pixels.
 */
public class View {
    final JsonObject o = new JsonObject();
    private final List<El> body = new ArrayList<>();
    private final JsonArray tabs = new JsonArray();

    public View(String title) {
        o.addProperty("title", title);
        size("large");
    }

    public View size(String size) {
        o.addProperty("size", size);
        return this;
    }

    /** terminal (default), paper (documents) or hazard (warhead). */
    public View style(String style) {
        o.addProperty("style", style);
        return this;
    }

    /** Right side of the header, e.g. "CLR 5". */
    public View badge(String badge) {
        if (badge != null) o.addProperty("badge", badge);
        return this;
    }

    /** A highlighted tag in the header, e.g. "REMOTE LINK". */
    public View tag(String tag) {
        if (tag != null) o.addProperty("tag", tag);
        return this;
    }

    /** NPC face in the header. */
    public View face(String skinTexture) {
        if (skinTexture != null) o.addProperty("face", skinTexture);
        return this;
    }

    public View tab(String id, String label, ItemLike icon, String lockedReason) {
        JsonObject t = new JsonObject();
        t.addProperty("id", id);
        t.addProperty("label", label);
        if (icon != null) t.addProperty("icon", El.itemId(icon));
        if (lockedReason != null) t.addProperty("locked", lockedReason);
        tabs.add(t);
        o.add("tabs", tabs);
        return this;
    }

    public View activeTab(String id) {
        o.addProperty("tab", id);
        return this;
    }

    /** A red strip across the footer, e.g. a raid in progress. */
    public View banner(String text, String color) {
        if (text != null) {
            JsonObject b = new JsonObject();
            b.addProperty("text", text);
            b.addProperty("color", color);
            o.add("banner", b);
        }
        return this;
    }

    public View footer(String hint) {
        if (hint != null) o.addProperty("footer", hint);
        return this;
    }

    /** A button in the footer's right corner (e.g. "Clear corners"). */
    public View footerButton(El.Button button) {
        o.add("footerButton", button.o);
        return this;
    }

    public View back(boolean back) {
        o.addProperty("back", back);
        return this;
    }

    public View add(El el) {
        if (el != null) body.add(el);
        return this;
    }

    public View addAll(List<El> els) {
        for (El e : els) add(e);
        return this;
    }

    JsonObject json() {
        o.add("body", El.array(body));
        return o;
    }
}
