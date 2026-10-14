package dev.projectredacted.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;

import java.util.Collection;
import java.util.List;

/**
 * One widget of the shared kit (GUI design §2.2), as the JSON the client draws. Colors are names the client maps
 * to the Foundation palette: white, gray, dark, amber, green, red, blue, aqua, gold, purple.
 */
public class El {
    public final JsonObject o = new JsonObject();

    protected El(String type) {
        o.addProperty("t", type);
    }

    // ---------------------------------------------------------------- common setters

    public El id(String id) {
        o.addProperty("id", id);
        return this;
    }

    public El color(String color) {
        o.addProperty("color", color);
        return this;
    }

    public El tip(String... lines) {
        return tip(List.of(lines));
    }

    public El tip(Collection<String> lines) {
        if (lines.isEmpty()) return this;
        JsonArray a = new JsonArray();
        lines.forEach(a::add);
        o.add("tip", a);
        return this;
    }

    public El set(String key, String value) {
        if (value != null) o.addProperty(key, value);
        return this;
    }

    public El set(String key, Number value) {
        o.addProperty(key, value);
        return this;
    }

    public El set(String key, boolean value) {
        o.addProperty(key, value);
        return this;
    }

    public El set(String key, El child) {
        o.add(key, child.o);
        return this;
    }

    /** Takes the rest of the column's height (lists, documents). */
    public El fill() {
        return set("fill", true);
    }

    // ---------------------------------------------------------------- text

    public static El text(String text) {
        return new El("text").set("text", text);
    }

    public static El text(String text, String color) {
        return text(text).color(color);
    }

    public static El heading(String text) {
        return new El("heading").set("text", text);
    }

    /** Large centered text, e.g. the warhead countdown. */
    public static El big(String text, String color) {
        return new El("text").set("text", text).set("big", true).set("align", "center").color(color);
    }

    public static El center(String text, String color) {
        return text(text, color).set("align", "center");
    }

    public static El space(int height) {
        return new El("space").set("h", height);
    }

    public static El sep() {
        return new El("sep");
    }

    /** Small pills: object class, clearance, ⚡ powered, NAMED, LOCKED. */
    public static El badges(String... textColorPairs) {
        El e = new El("badges");
        JsonArray a = new JsonArray();
        for (int i = 0; i + 1 < textColorPairs.length; i += 2) {
            if (textColorPairs[i] == null) continue;
            JsonObject b = new JsonObject();
            b.addProperty("text", textColorPairs[i]);
            b.addProperty("color", textColorPairs[i + 1]);
            a.add(b);
        }
        e.o.add("items", a);
        return e;
    }

    // ---------------------------------------------------------------- bars

    public static El bar(String label, double value, double max, String color) {
        return new El("bar").set("label", label).set("value", value).set("max", Math.max(max, 0.0001))
                .set("text", fmt(value) + " / " + fmt(max)).color(color);
    }

    /** Demand against supply: green with headroom, amber when nearly full, red when over. */
    public static El usage(String label, double used, double supply) {
        double r = supply <= 0 ? (used > 0 ? 2 : 0) : used / supply;
        return bar(label, used, Math.max(supply, used), r > 1 ? "red" : r >= 0.85 ? "amber" : "green").set("text", fmt(used) + " / " + fmt(supply))
                .set("supply", supply);
    }

    /** A 0-100 need where high is good (morale, food, rest, integrity). */
    public static El need(String label, double value) {
        return bar(label, value, 100, value >= 60 ? "green" : value >= 30 ? "amber" : "red").set("text", fmt(value));
    }

    public static El progress(String label, double fraction, String text) {
        return bar(label, fraction, 1, "blue").set("text", text);
    }

    public static El segments(String label, int value, int max) {
        return new El("segments").set("label", label).set("value", value).set("max", max);
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format("%.1f", v);
    }

    // ---------------------------------------------------------------- buttons and inputs

    public static Button button(String label, String action) {
        return new Button(label, action);
    }

    /** A button that only shows an explanation in a small popup. */
    public static Button info(String label, String text) {
        Button b = new Button(label, "");
        b.set("info", text);
        return b;
    }

    public static El toggle(String label, boolean value, String action) {
        return new El("toggle").set("label", label).set("value", value).set("action", action);
    }

    public static El checkbox(String label, boolean value, String action) {
        return toggle(label, value, action).set("check", true);
    }

    /** {@code [–] 3 [+]}; the client sends {@code value} with the new number. */
    public static El stepper(String label, int value, int min, int max, String action) {
        return new El("stepper").set("label", label).set("value", value).set("min", min).set("max", max).set("action", action);
    }

    /** A text field with a button; the button sends what was typed as {@code text}. */
    public static El input(String id, String placeholder, Button button) {
        return new El("input").id(id).set("placeholder", placeholder).set("button", button);
    }

    public static El chips(String action, String value, String... idLabelPairs) {
        El e = new El("chips").set("action", action).set("value", value);
        JsonArray a = new JsonArray();
        for (int i = 0; i + 1 < idLabelPairs.length; i += 2) {
            JsonObject c = new JsonObject();
            c.addProperty("id", idLabelPairs[i]);
            c.addProperty("label", idLabelPairs[i + 1]);
            a.add(c);
        }
        e.o.add("options", a);
        return e;
    }

    public static El chips(String action, String value, List<String> labels) {
        String[] pairs = new String[labels.size() * 2];
        for (int i = 0; i < labels.size(); i++) pairs[2 * i] = pairs[2 * i + 1] = labels.get(i);
        return chips(action, value, pairs);
    }

    public static El row(El... items) {
        El e = new El("row");
        JsonArray a = new JsonArray();
        for (El i : items) if (i != null) a.add(i.o);
        e.o.add("items", a);
        return e;
    }

    public static El row(List<? extends El> items) {
        return row(items.toArray(new El[0]));
    }

    // ---------------------------------------------------------------- containers

    /** A bordered group with a caption, like "Waiting to collect". */
    public static El box(String title, El... body) {
        El e = new El("box").set("title", title);
        e.o.add("body", array(List.of(body)));
        return e;
    }

    public static El box(String title, List<El> body) {
        El e = new El("box").set("title", title);
        e.o.add("body", array(body));
        return e;
    }

    /** Side-by-side columns, each scrolling on its own. */
    public static El cols(Col... cols) {
        El e = new El("cols");
        JsonArray a = new JsonArray();
        for (Col c : cols) a.add(c.o);
        e.o.add("cols", a);
        return e;
    }

    public static Col col(double width, List<El> body) {
        return new Col(width, body);
    }

    public static ListEl list(String id, String action) {
        return new ListEl(id, action);
    }

    // ---------------------------------------------------------------- pictures

    public static El cost(List<CostLine> lines) {
        El e = new El("cost");
        JsonArray a = new JsonArray();
        for (CostLine l : lines) {
            JsonObject c = new JsonObject();
            if (l.item != null) c.addProperty("item", itemId(l.item));
            c.addProperty("label", l.label);
            c.addProperty("have", l.have);
            c.addProperty("need", l.need);
            a.add(c);
        }
        e.o.add("items", a);
        return e;
    }

    public record CostLine(Item item, String label, int have, int need) {}

    public static El portrait(String skinTexture, String name, String badge, List<String> lines) {
        El e = new El("portrait").set("skin", skinTexture).set("name", name).set("badge", badge);
        e.o.add("lines", array2(lines));
        return e;
    }

    public static El doc(String title, String body, String stamp) {
        return new El("doc").set("title", title).set("body", body).set("stamp", stamp).fill();
    }

    public static Grid grid(int cols, String action) {
        return new Grid(cols, action);
    }

    public static Items items(String action) {
        return new Items(action);
    }

    public static Cards cards(String action) {
        return new Cards(action);
    }

    public static String itemId(ItemLike item) {
        return BuiltInRegistries.ITEM.getKey(item.asItem()).toString();
    }

    static JsonArray array(List<? extends El> els) {
        JsonArray a = new JsonArray();
        for (El e : els) if (e != null) a.add(e.o);
        return a;
    }

    static JsonArray array2(List<String> lines) {
        JsonArray a = new JsonArray();
        for (String s : lines) if (s != null) a.add(s);
        return a;
    }

    // ---------------------------------------------------------------- builders

    public static class Button extends El {
        Button(String label, String action) {
            super("button");
            set("label", label);
            set("action", action);
        }

        public Button arg(String key, String value) {
            argObj().addProperty(key, value);
            return this;
        }

        public Button arg(String key, Number value) {
            argObj().addProperty(key, value);
            return this;
        }

        private JsonObject argObj() {
            if (!o.has("arg")) o.add("arg", new JsonObject());
            return o.getAsJsonObject("arg");
        }

        /** primary (blue), confirm (green), danger (red with hazard stripe) or plain. */
        public Button style(String style) {
            set("style", style);
            return this;
        }

        /** Greyed out; the tooltip says why. Null keeps it enabled. */
        public Button disabled(String reason) {
            if (reason != null) set("disabled", reason);
            return this;
        }

        /** Asks Yes/No in a dialog before sending. */
        public Button confirm(String question) {
            set("confirm", question);
            return this;
        }

        /** Asks the player to type this exact text before sending (case doesn't matter). */
        public Button typed(String question, String mustType) {
            set("confirm", question);
            set("typed", mustType);
            return this;
        }

        public Button width(int w) {
            set("w", w);
            return this;
        }

        @Override
        public Button tip(String... lines) {
            super.tip(lines);
            return this;
        }

        @Override
        public Button tip(Collection<String> lines) {
            super.tip(lines);
            return this;
        }
    }

    public static class Col {
        final JsonObject o = new JsonObject();

        Col(double width, List<El> body) {
            o.addProperty("w", width);
            o.add("body", array(body));
        }
    }

    public static class ListEl extends El {
        private final JsonArray rows = new JsonArray();

        ListEl(String id, String action) {
            super("list");
            id(id);
            set("action", action);
            o.add("rows", rows);
        }

        public ListEl searchable() {
            set("search", true);
            return this;
        }

        public ListEl empty(String text) {
            set("empty", text);
            return this;
        }

        /** Visible rows when not filling the column. */
        public ListEl rows(int visible) {
            set("rows", visible);
            return this;
        }

        public Row add(String id, String title) {
            Row r = new Row(id, title);
            rows.add(r.o);
            return r;
        }

        @Override
        public ListEl fill() {
            super.fill();
            return this;
        }
    }

    public static class Row {
        final JsonObject o = new JsonObject();

        Row(String id, String title) {
            o.addProperty("id", id);
            o.addProperty("title", title);
        }

        public Row title(String title) {
            o.addProperty("title", title);
            return this;
        }

        public Row icon(ItemLike item) {
            if (item != null) o.addProperty("icon", itemId(item));
            return this;
        }

        public Row sub(String text, String color) {
            if (text != null) {
                o.addProperty("sub", text);
                if (color != null) o.addProperty("subColor", color);
            }
            return this;
        }

        public Row color(String color) {
            o.addProperty("color", color);
            return this;
        }

        public Row badge(String text, String color) {
            if (text != null) {
                o.addProperty("badge", text);
                o.addProperty("badgeColor", color);
            }
            return this;
        }

        public Row selected(boolean selected) {
            if (selected) o.addProperty("selected", true);
            return this;
        }

        public Row warn(boolean warn) {
            if (warn) o.addProperty("warn", true);
            return this;
        }

        public Row dim(boolean dim) {
            if (dim) o.addProperty("dim", true);
            return this;
        }

        /** A button or toggle on the right side of the row. */
        public Row right(El el) {
            o.add("right", el.o);
            return this;
        }

        public Row tip(String... lines) {
            o.add("tip", array2(List.of(lines)));
            return this;
        }

        public Row tip(List<String> lines) {
            o.add("tip", array2(lines));
            return this;
        }

        /** Extra words the search box matches besides the title. */
        public Row search(String words) {
            o.addProperty("search", words);
            return this;
        }

        /** A progress bar under the title (upgrades in progress). */
        public Row progress(double fraction) {
            o.addProperty("progress", fraction);
            return this;
        }
    }

    public static class Grid extends El {
        private final JsonArray cells = new JsonArray();

        Grid(int cols, String action) {
            super("grid");
            set("cols", cols);
            set("action", action);
            o.add("cells", cells);
        }

        public JsonObject cell(String id, String label, String color) {
            JsonObject c = new JsonObject();
            c.addProperty("id", id);
            c.addProperty("label", label);
            c.addProperty("color", color);
            cells.add(c);
            return c;
        }
    }

    public static class Items extends El {
        private final JsonArray entries = new JsonArray();

        Items(String action) {
            super("items");
            set("action", action);
            o.add("entries", entries);
        }

        public JsonObject add(String id, ItemLike item, int count, String label) {
            JsonObject c = new JsonObject();
            c.addProperty("id", id);
            c.addProperty("item", itemId(item));
            c.addProperty("count", count);
            c.addProperty("label", label);
            entries.add(c);
            return c;
        }
    }

    public static class Cards extends El {
        private final JsonArray cards = new JsonArray();

        Cards(String action) {
            super("cards");
            set("action", action);
            o.add("cards", cards);
        }

        public JsonObject add(String id, ItemLike icon, String title, String text, boolean selected) {
            JsonObject c = new JsonObject();
            c.addProperty("id", id);
            c.addProperty("icon", itemId(icon));
            c.addProperty("title", title);
            c.addProperty("text", text);
            if (selected) c.addProperty("selected", true);
            cards.add(c);
            return c;
        }
    }
}
