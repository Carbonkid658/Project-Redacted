package dev.projectredacted.client.screen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The widget kit (GUI design §2.2). Each node is built from the JSON the server sends. */
public final class Nodes {
    static final int GAP = 3;

    // ================================================================ JSON helpers

    static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }

    static String str(JsonObject o, String key, String fallback) {
        String s = str(o, key);
        return s == null ? fallback : s;
    }

    static double num(JsonObject o, String key, double fallback) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? fallback : e.getAsDouble();
    }

    static boolean bool(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && !e.isJsonNull() && e.getAsBoolean();
    }

    static JsonArray arr(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonArray() ? e.getAsJsonArray() : new JsonArray();
    }

    static List<String> lines(JsonObject o, String key) {
        List<String> out = new ArrayList<>();
        for (JsonElement e : arr(o, key)) out.add(e.getAsString());
        return out;
    }

    private static final Map<String, ItemStack> ITEMS = new HashMap<>();

    static ItemStack item(String id) {
        if (id == null) return ItemStack.EMPTY;
        return ITEMS.computeIfAbsent(id, k -> {
            Identifier rl = Identifier.tryParse(k);
            Item item = rl == null ? Items.BARRIER : BuiltInRegistries.ITEM.getOptional(rl).orElse(Items.BARRIER);
            return new ItemStack(item);
        });
    }

    static List<FormattedCharSequence> wrap(Font font, String text, int width) {
        return font.split(Component.literal(text == null ? "" : text), Math.max(10, width));
    }

    /** Builds the node for one JSON widget. */
    public static Node of(JsonObject o) {
        return switch (str(o, "t", "")) {
            case "text" -> new Text(o);
            case "heading" -> new Heading(o);
            case "space" -> new Space(o);
            case "sep" -> new Sep(o);
            case "badges" -> new Badges(o);
            case "bar" -> new Bar(o);
            case "segments" -> new Segments(o);
            case "button" -> new Button(o);
            case "toggle" -> new Toggle(o);
            case "stepper" -> new Stepper(o);
            case "input" -> new Input(o);
            case "chips" -> new Chips(o);
            case "row" -> new Row(o);
            case "box" -> new Box(o);
            case "cols" -> new Cols(o);
            case "list" -> new ListNode(o);
            case "cost" -> new Cost(o);
            case "grid" -> new Grid(o);
            case "items" -> new ItemGrid(o);
            case "portrait" -> new Portrait(o);
            case "cards" -> new Cards(o);
            case "doc" -> new Doc(o);
            default -> new Space(new JsonObject());
        };
    }

    static List<Node> children(JsonArray a) {
        List<Node> out = new ArrayList<>();
        for (JsonElement e : a) if (e.isJsonObject()) out.add(of(e.getAsJsonObject()));
        return out;
    }

    // ================================================================ base

    public abstract static class Node {
        final JsonObject o;
        int x, y, w, h;

        Node(JsonObject o) {
            this.o = o;
        }

        /** Height needed at this width. Fill nodes return their minimum. */
        abstract int measure(Ctx c, int width);

        void place(Ctx c, int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        abstract void render(Ctx c);

        boolean click(Ctx c, double mx, double my, boolean shift) {
            return false;
        }

        boolean scroll(Ctx c, double mx, double my, double amount) {
            return false;
        }

        boolean fill() {
            return bool(o, "fill");
        }

        /** A fixed width requested by the server (buttons in rows). */
        int fixedWidth() {
            return (int) num(o, "w", 0);
        }

        boolean inside(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }

        void tip(Ctx c) {
            if (o.has("tip") && c.hovered(x, y, w, h)) c.tooltip(lines(o, "tip"));
        }

        int color(Ctx c) {
            return Theme.color(str(o, "color"), c.paper());
        }
    }

    // ================================================================ containers

    /** A vertical stack that scrolls when its content is taller than its box. */
    public static class Column extends Node {
        final List<Node> kids;
        final String key;
        int content;

        Column(List<Node> kids, String key) {
            super(new JsonObject());
            this.kids = kids;
            this.key = key;
        }

        @Override
        int measure(Ctx c, int width) {
            int total = 0;
            for (Node n : kids) total += n.measure(c, width) + GAP;
            return Math.max(0, total - GAP);
        }

        int scrollY(Ctx c) {
            Object v = c.state().get(key);
            return v instanceof Integer i ? i : 0;
        }

        @Override
        void place(Ctx c, int x, int y, int w, int h) {
            super.place(c, x, y, w, h);
            int inner = w - 4;
            int[] heights = new int[kids.size()];
            int fixed = 0, fills = 0;
            for (int i = 0; i < kids.size(); i++) {
                Node n = kids.get(i);
                heights[i] = n.measure(c, inner);
                if (n.fill() || n instanceof Cols) fills++;
                else fixed += heights[i];
            }
            fixed += GAP * Math.max(0, kids.size() - 1);
            int share = fills == 0 ? 0 : Math.max(0, (h - fixed) / fills);
            int total = 0;
            for (int i = 0; i < kids.size(); i++) {
                Node n = kids.get(i);
                if (n.fill() || n instanceof Cols) heights[i] = Math.max(heights[i], share);
                total += heights[i] + (i > 0 ? GAP : 0);
            }
            content = total;
            int max = Math.max(0, content - h);
            int scroll = Math.max(0, Math.min(scrollY(c), max));
            c.state().put(key, scroll);
            int width = content > h ? inner : w;
            int cy = y - scroll;
            for (int i = 0; i < kids.size(); i++) {
                kids.get(i).place(c, x, cy, width, heights[i]);
                cy += heights[i] + GAP;
            }
        }

        @Override
        void render(Ctx c) {
            c.pushClip(x, y, w, h);
            for (Node n : kids) if (n.y + n.h >= y && n.y <= y + h) n.render(c);
            c.popClip();
            if (content > h) {
                int barH = Math.max(10, h * h / content);
                int barY = y + (int) ((h - barH) * (scrollY(c) / (double) Math.max(1, content - h)));
                c.g().fill(x + w - 2, y, x + w, y + h, 0x40FFFFFF);
                c.g().fill(x + w - 2, barY, x + w, barY + barH, c.paper() ? Theme.INK_MUTED : Theme.AMBER);
            }
        }

        @Override
        boolean click(Ctx c, double mx, double my, boolean shift) {
            if (!inside(mx, my)) return false;
            for (Node n : kids) if (n.inside(mx, my) && n.click(c, mx, my, shift)) return true;
            return false;
        }

        @Override
        boolean scroll(Ctx c, double mx, double my, double amount) {
            if (!inside(mx, my)) return false;
            for (Node n : kids) if (n.inside(mx, my) && n.scroll(c, mx, my, amount)) return true;
            if (content <= h) return false;
            c.state().put(key, Math.max(0, Math.min(content - h, scrollY(c) - (int) (amount * 14))));
            return true;
        }
    }

    /** Side-by-side columns with width weights; each scrolls on its own. */
    static class Cols extends Node {
        final List<Column> cols = new ArrayList<>();
        final List<Double> weights = new ArrayList<>();

        Cols(JsonObject o) {
            super(o);
            int i = 0;
            for (JsonElement e : arr(o, "cols")) {
                JsonObject col = e.getAsJsonObject();
                weights.add(num(col, "w", 1));
                cols.add(new Column(children(arr(col, "body")), "col" + i++));
            }
        }

        @Override
        int measure(Ctx c, int width) {
            return 60;
        }

        @Override
        void place(Ctx c, int x, int y, int w, int h) {
            super.place(c, x, y, w, h);
            double total = 0;
            for (double d : weights) total += d;
            int cx = x, avail = w - 6 * (cols.size() - 1);
            for (int i = 0; i < cols.size(); i++) {
                int cw = i == cols.size() - 1 ? x + w - cx : (int) (avail * weights.get(i) / total);
                cols.get(i).place(c, cx, y, cw, h);
                cx += cw + 6;
            }
        }

        @Override
        void render(Ctx c) {
            for (int i = 0; i < cols.size(); i++) {
                Column col = cols.get(i);
                col.render(c);
                if (i > 0) c.g().fill(col.x - 4, y, col.x - 3, y + h, c.paper() ? 0x40000000 : Theme.SCREEN_EDGE);
            }
        }

        @Override
        boolean click(Ctx c, double mx, double my, boolean shift) {
            for (Column col : cols) if (col.click(c, mx, my, shift)) return true;
            return false;
        }

        @Override
        boolean scroll(Ctx c, double mx, double my, double amount) {
            for (Column col : cols) if (col.scroll(c, mx, my, amount)) return true;
            return false;
        }
    }

    /** Widgets side by side: fixed-width ones keep their width, the rest share what's left. */
    static class Row extends Node {
        final List<Node> kids;
        final int[] widths;

        Row(JsonObject o) {
            super(o);
            kids = children(arr(o, "items"));
            widths = new int[kids.size()];
        }

        private void split(int width) {
            int fixed = 0, flex = 0;
            for (Node n : kids) {
                if (n.fixedWidth() > 0) fixed += n.fixedWidth();
                else flex++;
            }
            int gaps = 4 * Math.max(0, kids.size() - 1);
            int share = flex == 0 ? 0 : Math.max(10, (width - fixed - gaps) / flex);
            for (int i = 0; i < kids.size(); i++) widths[i] = kids.get(i).fixedWidth() > 0 ? Math.min(kids.get(i).fixedWidth(), width) : share;
        }

        @Override
        int measure(Ctx c, int width) {
            split(width);
            int h = 0;
            for (int i = 0; i < kids.size(); i++) h = Math.max(h, kids.get(i).measure(c, widths[i]));
            return h;
        }

        @Override
        void place(Ctx c, int x, int y, int w, int h) {
            super.place(c, x, y, w, h);
            split(w);
            int cx = x;
            for (int i = 0; i < kids.size(); i++) {
                Node n = kids.get(i);
                n.place(c, cx, y, widths[i], n.measure(c, widths[i]));
                cx += widths[i] + 4;
            }
        }

        @Override
        void render(Ctx c) {
            for (Node n : kids) n.render(c);
        }

        @Override
        boolean click(Ctx c, double mx, double my, boolean shift) {
            for (Node n : kids) if (n.inside(mx, my) && n.click(c, mx, my, shift)) return true;
            return false;
        }
    }

    /** A bordered group with a caption. */
    static class Box extends Node {
        final List<Node> kids;

        Box(JsonObject o) {
            super(o);
            kids = children(arr(o, "body"));
        }

        @Override
        int measure(Ctx c, int width) {
            int total = 12;
            for (Node n : kids) total += n.measure(c, width - 8) + GAP;
            return total + 2;
        }

        @Override
        void place(Ctx c, int x, int y, int w, int h) {
            super.place(c, x, y, w, h);
            int cy = y + 12;
            for (Node n : kids) {
                int nh = n.measure(c, w - 8);
                n.place(c, x + 4, cy, w - 8, nh);
                cy += nh + GAP;
            }
        }

        @Override
        void render(Ctx c) {
            GuiGraphicsExtractor g = c.g();
            int line = c.paper() ? Theme.INK_MUTED : Theme.SCREEN_EDGE;
            g.outline(x, y + 4, w, h - 4, line);
            String title = c.tr(str(o, "title", ""));
            int tw = c.font().width(title);
            g.fill(x + 5, y, x + 9 + tw, y + 9, c.paper() ? Theme.PAPER : Theme.SCREEN);
            g.text(c.font(), title, x + 7, y, c.paper() ? Theme.INK_MUTED : Theme.AMBER, false);
            for (Node n : kids) n.render(c);
        }

        @Override
        boolean click(Ctx c, double mx, double my, boolean shift) {
            for (Node n : kids) if (n.inside(mx, my) && n.click(c, mx, my, shift)) return true;
            return false;
        }
    }

    // ================================================================ text

    static class Text extends Node {
        List<FormattedCharSequence> lines = List.of();

        Text(JsonObject o) {
            super(o);
        }

        boolean big() {
            return bool(o, "big");
        }

        @Override
        int measure(Ctx c, int width) {
            int scale = big() ? 2 : 1;
            lines = wrap(c.font(), c.tr(str(o, "text", "")), width / scale);
            return Math.max(1, lines.size()) * (c.font().lineHeight + 1) * scale;
        }

        @Override
        void render(Ctx c) {
            GuiGraphicsExtractor g = c.g();
            boolean center = "center".equals(str(o, "align"));
            int scale = big() ? 2 : 1;
            int ly = y;
            for (FormattedCharSequence line : lines) {
                int lw = c.font().width(line) * scale;
                int lx = center ? x + (w - lw) / 2 : x;
                if (scale == 1) g.text(c.font(), line, lx, ly, color(c), false);
                else {
                    g.pose().pushMatrix();
                    g.pose().translate(lx, ly);
                    g.pose().scale(2f, 2f);
                    g.text(c.font(), line, 0, 0, color(c), true);
                    g.pose().popMatrix();
                }
                ly += (c.font().lineHeight + 1) * scale;
            }
            tip(c);
        }
    }

    static class Heading extends Node {
        Heading(JsonObject o) {
            super(o);
        }

        @Override
        int measure(Ctx c, int width) {
            return c.font().lineHeight + 3;
        }

        @Override
        void render(Ctx c) {
            String text = Theme.fit(c.font(), c.tr(str(o, "text", "")), w);
            int color = c.paper() ? Theme.INK_RED : Theme.AMBER;
            c.g().text(c.font(), text, x, y + 1, color, !c.paper());
            c.g().fill(x, y + h - 1, x + Math.min(w, c.font().width(text)), y + h, Theme.shade(color, 0.6f));
        }
    }

    static class Space extends Node {
        Space(JsonObject o) {
            super(o);
        }

        @Override
        int measure(Ctx c, int width) {
            return (int) num(o, "h", 2);
        }

        @Override
        void render(Ctx c) {}
    }

    static class Sep extends Node {
        Sep(JsonObject o) {
            super(o);
        }

        @Override
        int measure(Ctx c, int width) {
            return 3;
        }

        @Override
        void render(Ctx c) {
            c.g().fill(x, y + 1, x + w, y + 2, c.paper() ? 0x40000000 : Theme.SCREEN_EDGE);
        }
    }

    static class Badges extends Node {
        Badges(JsonObject o) {
            super(o);
        }

        @Override
        int measure(Ctx c, int width) {
            int lx = 0, rows = 1;
            for (JsonElement e : arr(o, "items")) {
                int pw = Theme.pillWidth(c.font(), str(e.getAsJsonObject(), "text", "")) + 3;
                if (lx + pw > width && lx > 0) {
                    rows++;
                    lx = 0;
                }
                lx += pw;
            }
            return rows * 12;
        }

        @Override
        void render(Ctx c) {
            int lx = x, ly = y;
            for (JsonElement e : arr(o, "items")) {
                JsonObject b = e.getAsJsonObject();
                String text = str(b, "text", "");
                int pw = Theme.pillWidth(c.font(), text) + 3;
                if (lx + pw > x + w && lx > x) {
                    lx = x;
                    ly += 12;
                }
                int col = Theme.color(str(b, "color"), false);
                Theme.pill(c.g(), c.font(), text, lx, ly, Theme.shade(col, 0.45f), Theme.lighten(col, 30));
                lx += pw;
            }
        }
    }

    // ================================================================ bars

    static class Bar extends Node {
        Bar(JsonObject o) {
            super(o);
        }

        @Override
        int measure(Ctx c, int width) {
            return 11;
        }

        @Override
        void render(Ctx c) {
            GuiGraphicsExtractor g = c.g();
            String label = c.tr(str(o, "label", ""));
            int lw = label.isEmpty() ? 0 : Math.min(54, Math.max(40, c.font().width(label) + 4));
            if (lw > 0) g.text(c.font(), Theme.fit(c.font(), label, lw - 2), x, y + 2, c.paper() ? Theme.INK : Theme.TEXT, false);
            double value = num(o, "value", 0), max = Math.max(0.0001, num(o, "max", 1));
            String text = str(o, "text", "");
            int color = color(c);
            String id = str(o, "id");
            if (id != null && c.preview().containsKey(id)) {
                double supply = num(o, "supply", max);
                value = c.preview().get(id);
                max = Math.max(supply, value);
                double r = supply <= 0 ? 2 : value / supply;
                color = Theme.color(r > 1 ? "red" : r >= 0.85 ? "amber" : "green", c.paper());
                text = "→ " + (long) value + " / " + (long) supply;
            }
            int bx = x + lw, bw = w - lw;
            Theme.inset(g, bx, y, bw, 11, c.paper() ? 0xFFD8CCB0 : 0xFF06090A);
            int fw = (int) ((bw - 2) * Math.max(0, Math.min(1, value / max)));
            if (fw > 0) {
                g.fill(bx + 1, y + 1, bx + 1 + fw, y + 10, Theme.shade(color, 0.75f));
                g.fill(bx + 1, y + 1, bx + 1 + fw, y + 3, Theme.lighten(Theme.shade(color, 0.75f), 30));
            }
            if (!text.isEmpty()) {
                String t = Theme.fit(c.font(), text, bw - 4);
                g.text(c.font(), t, bx + bw - 3 - c.font().width(t), y + 2, 0xFFFFFFFF, true);
            }
            tip(c);
        }
    }

    static class Segments extends Node {
        Segments(JsonObject o) {
            super(o);
        }

        @Override
        int measure(Ctx c, int width) {
            return 11;
        }

        @Override
        void render(Ctx c) {
            String label = c.tr(str(o, "label", ""));
            int lw = c.font().width(label) + 6;
            c.g().text(c.font(), label, x, y + 2, c.paper() ? Theme.INK : Theme.TEXT, false);
            int value = (int) num(o, "value", 0), max = (int) num(o, "max", 4);
            for (int i = 0; i < max; i++) {
                int sx = x + lw + i * 17;
                Theme.inset(c.g(), sx, y, 15, 10, 0xFF06090A);
                if (i < value) c.g().fill(sx + 1, y + 1, sx + 14, y + 9, 0xFFD23C3C);
            }
            c.g().text(c.font(), value + "/" + max, x + lw + max * 17 + 3, y + 2, Theme.MUTED, false);
        }
    }

    // ================================================================ buttons

    static int buttonFill(String style, boolean disabled, boolean hover) {
        if (disabled) return 0xFF3A3D39;
        int base = switch (style == null ? "" : style) {
            case "primary" -> 0xFF2F5FA8;
            case "confirm" -> 0xFF2E7D46;
            case "danger" -> 0xFF9E2B2B;
            default -> 0xFF5A5E58;
        };
        return hover ? Theme.lighten(base, 25) : base;
    }

    /** Draws a button from its JSON; used by buttons, footer buttons and dialogs. */
    static void drawButton(Ctx c, JsonObject b, int x, int y, int w, int h) {
        GuiGraphicsExtractor g = c.g();
        boolean disabled = b.has("disabled");
        boolean hover = c.hovered(x, y, w, h);
        String style = str(b, "style");
        Theme.bevel(g, x, y, w, h, buttonFill(style, disabled, hover && !disabled));
        if ("danger".equals(style) && !disabled) Theme.hazard(g, x + 1, y + h - 3, w - 2, 2);
        String label = Theme.fit(c.font(), c.tr(str(b, "label", "")), w - 6);
        g.centeredText(c.font(), label, x + w / 2, y + (h - 8) / 2, disabled ? 0xFF85857C : 0xFFFFFFFF);
        if (hover) {
            List<String> tip = new ArrayList<>();
            if (disabled) tip.add("✖ " + str(b, "disabled"));
            tip.addAll(lines(b, "tip"));
            if (!tip.isEmpty()) c.tooltip(tip);
        }
    }

    static int buttonWidth(Ctx c, JsonObject b) {
        int fixed = (int) num(b, "w", 0);
        return fixed > 0 ? fixed : c.font().width(c.tr(str(b, "label", ""))) + 14;
    }

    static class Button extends Node {
        Button(JsonObject o) {
            super(o);
        }

        @Override
        int measure(Ctx c, int width) {
            return 16;
        }

        @Override
        void render(Ctx c) {
            drawButton(c, o, x, y, w, h);
        }

        @Override
        boolean click(Ctx c, double mx, double my, boolean shift) {
            c.press(o, shift, null);
            return true;
        }
    }

    static class Toggle extends Node {
        Toggle(JsonObject o) {
            super(o);
        }

        @Override
        int measure(Ctx c, int width) {
            return 12;
        }

        @Override
        int fixedWidth() {
            String label = str(o, "label", "");
            return label.isEmpty() ? 26 : 0;
        }

        @Override
        void render(Ctx c) {
            GuiGraphicsExtractor g = c.g();
            boolean on = bool(o, "value"), disabled = o.has("disabled");
            boolean hover = c.hovered(x, y, w, h);
            String label = c.tr(str(o, "label", ""));
            int textColor = disabled ? Theme.MUTED : c.paper() ? Theme.INK : Theme.TEXT;
            if (bool(o, "check")) {
                Theme.inset(g, x, y + 1, 10, 10, 0xFF06090A);
                if (on) g.text(c.font(), "✔", x + 2, y + 2, disabled ? Theme.MUTED : 0xFF6BD97A, false);
                g.text(c.font(), Theme.fit(c.font(), label, w - 14), x + 14, y + 2, textColor, false);
            } else {
                int sx = x + w - 24;
                if (!label.isEmpty()) g.text(c.font(), Theme.fit(c.font(), label, w - 28), x, y + 2, textColor, false);
                int track = disabled ? 0xFF33352F : on ? 0xFF2E7D46 : 0xFF4A4C46;
                Theme.inset(g, sx, y + 1, 24, 10, hover && !disabled ? Theme.lighten(track, 20) : track);
                int kx = on ? sx + 13 : sx + 1;
                Theme.bevel(g, kx, y + 1, 10, 10, disabled ? 0xFF55574F : 0xFFC9C5B9);
                g.text(c.font(), on ? "I" : "O", on ? sx + 4 : sx + 15, y + 2, 0xFFE6E0C8, false);
            }
            if (hover) {
                if (disabled) c.tooltip(List.of("✖ " + str(o, "disabled")));
                else if (o.has("preview")) {
                    JsonObject p = o.getAsJsonObject("preview");
                    c.previewNext(str(p, "id", ""), num(p, "value", 0));
                }
            }
        }

        @Override
        boolean click(Ctx c, double mx, double my, boolean shift) {
            if (o.has("disabled")) return true;
            JsonObject args = new JsonObject();
            if (o.has("arg")) args.add("arg", o.get("arg"));
            args.addProperty("value", !bool(o, "value"));
            c.send(str(o, "action", ""), args);
            return true;
        }
    }

    static class Stepper extends Node {
        Stepper(JsonObject o) {
            super(o);
        }

        @Override
        int measure(Ctx c, int width) {
            return 14;
        }

        private int bx(Ctx c) {
            String label = c.tr(str(o, "label", ""));
            return label.isEmpty() ? x : x + c.font().width(label) + 6;
        }

        @Override
        void render(Ctx c) {
            GuiGraphicsExtractor g = c.g();
            String label = c.tr(str(o, "label", ""));
            if (!label.isEmpty()) g.text(c.font(), label, x, y + 3, c.paper() ? Theme.INK : Theme.TEXT, false);
            int value = (int) num(o, "value", 0), min = (int) num(o, "min", 0), max = (int) num(o, "max", 99);
            int bx = bx(c);
            JsonObject minus = new JsonObject(), plus = new JsonObject();
            minus.addProperty("label", "–");
            plus.addProperty("label", "+");
            if (value <= min) minus.addProperty("disabled", "Minimum is " + min + ".");
            if (value >= max) plus.addProperty("disabled", "Maximum is " + max + ".");
            drawButton(c, minus, bx, y, 14, 14);
            Theme.inset(g, bx + 16, y, 24, 14, 0xFF06090A);
            g.centeredText(c.font(), String.valueOf(value), bx + 28, y + 3, Theme.AMBER);
            drawButton(c, plus, bx + 42, y, 14, 14);
        }

        @Override
        boolean click(Ctx c, double mx, double my, boolean shift) {
            int value = (int) num(o, "value", 0), min = (int) num(o, "min", 0), max = (int) num(o, "max", 99);
            int bx = bx(c);
            int next = value;
            if (mx >= bx && mx < bx + 14) next = Math.max(min, value - (shift ? 5 : 1));
            else if (mx >= bx + 42 && mx < bx + 56) next = Math.min(max, value + (shift ? 5 : 1));
            if (next != value) {
                JsonObject args = new JsonObject();
                args.addProperty("value", next);
                c.send(str(o, "action", ""), args);
            }
            return true;
        }
    }

    /** A text field with a button that sends what was typed. */
    static class Input extends Node {
        Input(JsonObject o) {
            super(o);
        }

        String key() {
            return "input:" + str(o, "id", "");
        }

        JsonObject button() {
            return o.has("button") ? o.getAsJsonObject("button") : new JsonObject();
        }

        @Override
        int measure(Ctx c, int width) {
            return 16;
        }

        @Override
        void render(Ctx c) {
            int bw = buttonWidth(c, button());
            drawField(c, key(), str(o, "placeholder", ""), x, y, w - bw - 4, 16, suggestion(c));
            drawButton(c, button(), x + w - bw, y, bw, 16);
        }

        String suggestion(Ctx c) {
            String text = (String) c.state().getOrDefault(key(), "");
            if (text.isEmpty()) return null;
            for (String s : lines(o, "suggest")) {
                if (s.toLowerCase().startsWith(text.toLowerCase()) && !s.equalsIgnoreCase(text)) return s;
            }
            return null;
        }

        @Override
        boolean click(Ctx c, double mx, double my, boolean shift) {
            int bw = buttonWidth(c, button());
            if (mx >= x + w - bw) {
                submit(c, shift);
            } else {
                c.focus(key());
            }
            return true;
        }

        void submit(Ctx c, boolean shift) {
            String text = (String) c.state().getOrDefault(key(), "");
            c.press(button(), shift, text);
            if (!button().has("disabled") && !button().has("confirm")) c.state().put(key(), "");
        }
    }

    /** Draws a text field whose text lives in the screen state under this key. */
    static void drawField(Ctx c, String key, String placeholder, int x, int y, int w, int h, String suggestion) {
        GuiGraphicsExtractor g = c.g();
        boolean focused = key.equals(c.focus());
        Theme.inset(g, x, y, w, h, c.paper() ? 0xFFF7F0DC : 0xFF06090A);
        if (focused) g.outline(x, y, w, h, Theme.AMBER);
        String text = (String) c.state().getOrDefault(key, "");
        int ty = y + (h - 8) / 2;
        if (text.isEmpty() && !focused) {
            g.text(c.font(), Theme.fit(c.font(), placeholder, w - 6), x + 4, ty, Theme.MUTED, false);
            return;
        }
        String shown = text;
        while (c.font().width(shown) > w - 10 && !shown.isEmpty()) shown = shown.substring(1);
        g.text(c.font(), shown, x + 4, ty, c.paper() ? Theme.INK : Theme.TEXT, false);
        int cx = x + 4 + c.font().width(shown);
        if (suggestion != null && focused) {
            g.text(c.font(), Theme.fit(c.font(), suggestion.substring(text.length()), x + w - cx - 4), cx, ty, Theme.MUTED, false);
        }
        if (focused && System.currentTimeMillis() / 500 % 2 == 0) g.fill(cx, ty - 1, cx + 1, ty + 9, Theme.AMBER);
    }

    static class Chips extends Node {
        final List<int[]> boxes = new ArrayList<>();

        Chips(JsonObject o) {
            super(o);
        }

        @Override
        int measure(Ctx c, int width) {
            int lx = 0, rows = 1;
            for (JsonElement e : arr(o, "options")) {
                int pw = c.font().width(c.tr(str(e.getAsJsonObject(), "label", ""))) + 10;
                if (lx + pw > width && lx > 0) {
                    rows++;
                    lx = 0;
                }
                lx += pw + 2;
            }
            return rows * 14 - 2;
        }

        @Override
        void render(Ctx c) {
            boxes.clear();
            String value = str(o, "value", "");
            int lx = x, ly = y;
            for (JsonElement e : arr(o, "options")) {
                JsonObject opt = e.getAsJsonObject();
                String label = c.tr(str(opt, "label", ""));
                int pw = c.font().width(label) + 10;
                if (lx + pw > x + w && lx > x) {
                    lx = x;
                    ly += 14;
                }
                boolean sel = value.equals(str(opt, "id"));
                boolean hover = c.hovered(lx, ly, pw, 12);
                int bg = sel ? Theme.AMBER : hover ? 0xFF3A4540 : 0xFF232B27;
                c.g().fill(lx, ly, lx + pw, ly + 12, bg);
                c.g().text(c.font(), label, lx + 5, ly + 2, sel ? 0xFF1A1408 : Theme.TEXT, false);
                boxes.add(new int[]{lx, ly, pw});
                lx += pw + 2;
            }
        }

        @Override
        boolean click(Ctx c, double mx, double my, boolean shift) {
            JsonArray opts = arr(o, "options");
            for (int i = 0; i < boxes.size() && i < opts.size(); i++) {
                int[] b = boxes.get(i);
                if (mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + 12) {
                    JsonObject args = new JsonObject();
                    args.addProperty("id", str(opts.get(i).getAsJsonObject(), "id", ""));
                    c.send(str(o, "action", ""), args);
                    return true;
                }
            }
            return true;
        }
    }

    // ================================================================ lists

    /** Rows with icon, title, status line, badge and an optional switch or button on the right. */
    static class ListNode extends Node {
        final List<JsonObject> rows = new ArrayList<>();
        final List<Node> rights = new ArrayList<>();
        int rowH = 12;
        int top;

        ListNode(JsonObject o) {
            super(o);
            for (JsonElement e : arr(o, "rows")) rows.add(e.getAsJsonObject());
            for (JsonObject r : rows) if (r.has("sub") || r.has("icon")) rowH = 20;
        }

        String id() {
            return str(o, "id", "list");
        }

        boolean searchable() {
            return bool(o, "search");
        }

        List<JsonObject> visible(Ctx c) {
            String q = ((String) c.state().getOrDefault("search:" + id(), "")).toLowerCase();
            if (q.isEmpty()) return rows;
            List<JsonObject> out = new ArrayList<>();
            for (JsonObject r : rows) {
                String hay = (str(r, "title", "") + " " + str(r, "sub", "") + " " + str(r, "search", "")).toLowerCase();
                if (hay.contains(q)) out.add(r);
            }
            return out;
        }

        @Override
        int measure(Ctx c, int width) {
            int search = searchable() ? 16 : 0;
            if (fill()) return search + rowH * 2;
            int n = (int) num(o, "rows", rows.size());
            return search + Math.max(1, Math.min(n, Math.max(1, rows.size()))) * rowH + 2;
        }

        int scroll(Ctx c) {
            Object v = c.state().get("list:" + id());
            return v instanceof Integer i ? i : 0;
        }

        @Override
        void place(Ctx c, int x, int y, int w, int h) {
            super.place(c, x, y, w, h);
            top = y + (searchable() ? 16 : 0);
            rights.clear();
            List<JsonObject> vis = visible(c);
            int max = Math.max(0, vis.size() * rowH - (y + h - top));
            int scroll = Math.max(0, Math.min(scroll(c), max));
            c.state().put("list:" + id(), scroll);
            for (int i = 0; i < vis.size(); i++) {
                JsonObject r = vis.get(i);
                if (!r.has("right")) {
                    rights.add(null);
                    continue;
                }
                Node n = of(r.getAsJsonObject("right"));
                int nw = n instanceof Toggle ? 26 : n instanceof Button ? buttonWidth(c, n.o) : 40;
                int ry = top + i * rowH - scroll;
                int nh = n.measure(c, nw);
                n.place(c, x + w - nw - 4, ry + (rowH - nh) / 2, nw, nh);
                rights.add(n);
            }
        }

        @Override
        void render(Ctx c) {
            GuiGraphicsExtractor g = c.g();
            Font font = c.font();
            if (searchable()) drawField(c, "search:" + id(), "🔍 Search", x, y, w, 14, null);
            int listH = y + h - top;
            Theme.inset(g, x, top, w, listH, c.paper() ? 0xFFE3D8BC : 0xFF0A0E0C);
            List<JsonObject> vis = visible(c);
            if (vis.isEmpty()) {
                g.text(font, Theme.fit(font, c.tr(str(o, "empty", "Nothing here.")), w - 8), x + 4, top + 4, Theme.MUTED, false);
                return;
            }
            int scroll = scroll(c);
            c.pushClip(x + 1, top + 1, w - 2, listH - 2);
            boolean clickable = !str(o, "action", "").isEmpty();
            for (int i = 0; i < vis.size(); i++) {
                int ry = top + i * rowH - scroll;
                if (ry + rowH < top || ry > top + listH) continue;
                JsonObject r = vis.get(i);
                boolean hover = c.hovered(x, ry, w, rowH);
                boolean sel = bool(r, "selected");
                int bg = sel ? Theme.ROW_SELECTED : hover && clickable ? Theme.ROW_HOVER : i % 2 == 0 ? Theme.ROW : 0xFF111613;
                if (c.paper()) bg = sel ? 0xFFD9C79A : hover ? 0xFFE9DFC6 : 0xFFE3D8BC;
                g.fill(x + 1, ry, x + w - 1, ry + rowH, bg);
                if (sel) g.fill(x + 1, ry, x + 3, ry + rowH, Theme.AMBER);
                int tx = x + 5;
                if (r.has("icon")) {
                    g.item(item(str(r, "icon")), x + 4, ry + (rowH - 16) / 2);
                    tx = x + 23;
                }
                Node right = i < rights.size() ? rights.get(i) : null;
                int rightW = right != null ? right.w + 6 : 0;
                String badge = str(r, "badge");
                int badgeW = badge != null ? Theme.pillWidth(font, badge) + 3 : 0;
                int titleW = x + w - tx - rightW - badgeW - 4;
                boolean dim = bool(r, "dim");
                int titleColor = dim ? Theme.MUTED : Theme.color(str(r, "color"), c.paper());
                g.text(font, Theme.fit(font, c.tr(str(r, "title", "")), titleW), tx, ry + (r.has("sub") ? 2 : (rowH - 8) / 2), titleColor, false);
                if (r.has("sub")) {
                    g.text(font, Theme.fit(font, c.tr(str(r, "sub", "")), x + w - tx - rightW - 4), tx, ry + 11,
                            dim ? Theme.shade(Theme.MUTED, 0.8f) : Theme.color(str(r, "subColor", "gray"), c.paper()), false);
                }
                if (badge != null) {
                    int col = Theme.color(str(r, "badgeColor"), false);
                    Theme.pill(g, font, badge, x + w - rightW - badgeW - 1, ry + 2, Theme.shade(col, 0.4f), Theme.lighten(col, 30));
                }
                if (r.has("progress")) {
                    double p = num(r, "progress", 0);
                    g.fill(tx, ry + rowH - 2, x + w - 4, ry + rowH - 1, 0xFF203040);
                    g.fill(tx, ry + rowH - 2, tx + (int) ((x + w - 4 - tx) * Math.max(0, Math.min(1, p))), ry + rowH - 1, 0xFF6AA8FF);
                }
                if (right != null) right.render(c);
                if (hover && r.has("tip") && (right == null || !right.inside(c.mx(), c.my()))) c.tooltip(lines(r, "tip"));
            }
            c.popClip();
            int content = vis.size() * rowH;
            if (content > listH) {
                int barH = Math.max(8, listH * listH / content);
                int barY = top + (int) ((listH - barH) * (scroll / (double) Math.max(1, content - listH)));
                g.fill(x + w - 3, barY, x + w - 1, barY + barH, Theme.AMBER);
            }
        }

        @Override
        boolean click(Ctx c, double mx, double my, boolean shift) {
            if (searchable() && my < top) {
                c.focus("search:" + id());
                return true;
            }
            for (Node r : rights) if (r != null && r.inside(mx, my) && my >= top) return r.click(c, mx, my, shift);
            List<JsonObject> vis = visible(c);
            int i = (int) ((my - top + scroll(c)) / rowH);
            if (i < 0 || i >= vis.size() || my < top) return true;
            String action = str(o, "action", "");
            if (!action.isEmpty()) {
                JsonObject args = new JsonObject();
                args.addProperty("id", str(vis.get(i), "id", ""));
                c.send(action, args);
            }
            return true;
        }

        @Override
        boolean scroll(Ctx c, double mx, double my, double amount) {
            int content = visible(c).size() * rowH, listH = y + h - top;
            if (content <= listH) return false;
            c.state().put("list:" + id(), Math.max(0, Math.min(content - listH, scroll(c) - (int) (amount * rowH))));
            return true;
        }
    }

    // ================================================================ pictures

    /** Have/need per item: green when covered, red when short. */
    static class Cost extends Node {
        Cost(JsonObject o) {
            super(o);
        }

        int columns() {
            return w >= 180 ? 2 : 1;
        }

        @Override
        int measure(Ctx c, int width) {
            int n = arr(o, "items").size();
            int cols = width >= 180 ? 2 : 1;
            return Math.max(1, (n + cols - 1) / cols) * 11;
        }

        @Override
        void render(Ctx c) {
            JsonArray items = arr(o, "items");
            int cols = columns(), cw = w / cols;
            for (int i = 0; i < items.size(); i++) {
                JsonObject it = items.get(i).getAsJsonObject();
                int have = (int) num(it, "have", 0), need = (int) num(it, "need", 0);
                boolean ok = have >= need;
                int cx = x + (i % cols) * cw, cy = y + (i / cols) * 11;
                String amount = " " + have + "/" + need;
                int aw = c.font().width(amount);
                String mark = ok ? "✔ " : "✖ ";
                int color = Theme.color(ok ? "green" : "red", c.paper());
                c.g().text(c.font(), mark + Theme.fit(c.font(), str(it, "label", ""), cw - aw - 14), cx, cy + 1, ok ? (c.paper() ? Theme.INK : Theme.TEXT) : color, false);
                c.g().text(c.font(), amount, cx + cw - aw - 4, cy + 1, color, false);
                if (c.hovered(cx, cy, cw, 11)) c.tooltip(List.of(str(it, "label", "") + ": you have " + have + ", need " + need));
            }
        }
    }

    /** The facility map: one cell per chunk, colored by room category. */
    static class Grid extends Node {
        Grid(JsonObject o) {
            super(o);
        }

        int cols() {
            return Math.max(1, (int) num(o, "cols", 5));
        }

        int cell(int width) {
            return Math.max(10, Math.min(24, width / cols()));
        }

        @Override
        int measure(Ctx c, int width) {
            int rows = (arr(o, "cells").size() + cols() - 1) / cols();
            return rows * cell(width) + 1;
        }

        int left() {
            return x + (w - cell(w) * cols()) / 2;
        }

        @Override
        void render(Ctx c) {
            GuiGraphicsExtractor g = c.g();
            int s = cell(w), lx = left();
            JsonArray cells = arr(o, "cells");
            for (int i = 0; i < cells.size(); i++) {
                JsonObject cell = cells.get(i).getAsJsonObject();
                int cx = lx + (i % cols()) * s, cy = y + (i / cols()) * s;
                String colorName = str(cell, "color", "dark");
                int base = "dark".equals(colorName) ? 0xFF1A211D : Theme.shade(Theme.color(colorName, false), 0.55f);
                boolean hover = c.hovered(cx, cy, s, s);
                g.fill(cx, cy, cx + s - 1, cy + s - 1, hover ? Theme.lighten(base, 25) : base);
                String label = str(cell, "label", "");
                if (!label.isEmpty()) {
                    String fit = Theme.fit(c.font(), label, s - 2);
                    g.centeredText(c.font(), fit, cx + s / 2, cy + (s - 8) / 2, 0xFFFFFFFF);
                }
                if (bool(cell, "powered")) g.fill(cx + s - 4, cy + 1, cx + s - 2, cy + 3, 0xFFFFE04A);
                if (bool(cell, "marker")) g.text(c.font(), "▲", cx + 1, cy + s - 9, 0xFFFFFFFF, true);
                if (bool(cell, "selected")) g.outline(cx - 1, cy - 1, s + 1, s + 1, Theme.AMBER);
                if (hover) c.tooltip(lines(cell, "tip"));
            }
        }

        @Override
        boolean click(Ctx c, double mx, double my, boolean shift) {
            int s = cell(w), lx = left();
            int col = (int) ((mx - lx) / s), row = (int) ((my - y) / s);
            JsonArray cells = arr(o, "cells");
            int i = row * cols() + col;
            if (mx < lx || col >= cols() || i < 0 || i >= cells.size()) return true;
            JsonObject args = new JsonObject();
            args.addProperty("id", str(cells.get(i).getAsJsonObject(), "id", ""));
            c.send(str(o, "action", ""), args);
            return true;
        }
    }

    /** Shop offers: item icons with the price under each. */
    static class ItemGrid extends Node {
        static final int CW = 26, CH = 30;

        ItemGrid(JsonObject o) {
            super(o);
        }

        int cols(int width) {
            return Math.max(1, width / CW);
        }

        @Override
        int measure(Ctx c, int width) {
            int n = arr(o, "entries").size();
            return Math.max(1, (n + cols(width) - 1) / cols(width)) * CH;
        }

        @Override
        void render(Ctx c) {
            GuiGraphicsExtractor g = c.g();
            JsonArray entries = arr(o, "entries");
            int cols = cols(w);
            for (int i = 0; i < entries.size(); i++) {
                JsonObject e = entries.get(i).getAsJsonObject();
                int cx = x + (i % cols) * CW, cy = y + (i / cols) * CH;
                boolean hover = c.hovered(cx, cy, CW - 2, CH - 2);
                Theme.inset(g, cx, cy, CW - 2, CH - 2, bool(e, "selected") ? Theme.ROW_SELECTED : hover ? Theme.ROW_HOVER : Theme.ROW);
                ItemStack stack = item(str(e, "item")).copyWithCount(Math.max(1, (int) num(e, "count", 1)));
                g.item(stack, cx + 4, cy + 2);
                g.itemDecorations(c.font(), stack, cx + 4, cy + 2);
                String label = str(e, "label", "");
                g.centeredText(c.font(), Theme.fit(c.font(), label, CW - 2), cx + (CW - 2) / 2, cy + 19, Theme.color("gold", false));
                if (bool(e, "selected")) g.outline(cx, cy, CW - 2, CH - 2, Theme.AMBER);
                if (bool(e, "dim")) g.fill(cx + 1, cy + 1, cx + CW - 3, cy + CH - 3, 0xA0000000);
                if (hover) c.tooltip(lines(e, "tip"));
            }
        }

        @Override
        boolean click(Ctx c, double mx, double my, boolean shift) {
            int cols = cols(w);
            int col = (int) ((mx - x) / CW), row = (int) ((my - y) / CH);
            int i = row * cols + col;
            JsonArray entries = arr(o, "entries");
            if (col >= cols || i < 0 || i >= entries.size()) return true;
            JsonObject args = new JsonObject();
            args.addProperty("id", str(entries.get(i).getAsJsonObject(), "id", ""));
            c.send(str(o, "action", ""), args);
            return true;
        }
    }

    /** Draws the face from a 64×64 skin texture at the given size. */
    static void face(GuiGraphicsExtractor g, String skin, int x, int y, int size) {
        Identifier tex = Identifier.tryParse(skin);
        if (tex == null) return;
        int scale = size / 8, tw = 64 * scale;
        g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, 8f * scale, 8f * scale, size, size, tw, tw);
        g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, 40f * scale, 8f * scale, size, size, tw, tw);
    }

    static class Portrait extends Node {
        Portrait(JsonObject o) {
            super(o);
        }

        @Override
        int measure(Ctx c, int width) {
            return Math.max(36, 12 + lines(o, "lines").size() * 10);
        }

        @Override
        void render(Ctx c) {
            GuiGraphicsExtractor g = c.g();
            Theme.inset(g, x, y, 36, 36, 0xFF06090A);
            face(g, str(o, "skin", ""), x + 2, y + 2, 32);
            int tx = x + 42;
            String badge = str(o, "badge");
            int badgeW = badge != null ? Theme.pillWidth(c.font(), badge) + 4 : 0;
            String name = Theme.fit(c.font(), c.tr(str(o, "name", "")), w - 42 - badgeW);
            g.text(c.font(), name, tx, y + 1, Theme.color("gold", c.paper()), true);
            if (badge != null) Theme.pill(g, c.font(), badge, tx + c.font().width(name) + 4, y, 0xFF5A4410, 0xFFFFD24A);
            int ly = y + 12;
            for (String line : lines(o, "lines")) {
                g.text(c.font(), Theme.fit(c.font(), c.tr(line), w - 42), tx, ly, Theme.MUTED, false);
                ly += 10;
            }
        }
    }

    /** Big selectable cards, e.g. the three site types. */
    static class Cards extends Node {
        Cards(JsonObject o) {
            super(o);
        }

        int cw(int width) {
            int n = Math.max(1, arr(o, "cards").size());
            return (width - 4 * (n - 1)) / n;
        }

        @Override
        int measure(Ctx c, int width) {
            int lines = 0;
            for (JsonElement e : arr(o, "cards")) lines = Math.max(lines, wrap(c.font(), str(e.getAsJsonObject(), "text", ""), cw(width) - 8).size());
            return 34 + lines * 10;
        }

        @Override
        void render(Ctx c) {
            GuiGraphicsExtractor g = c.g();
            JsonArray cards = arr(o, "cards");
            int cw = cw(w);
            for (int i = 0; i < cards.size(); i++) {
                JsonObject card = cards.get(i).getAsJsonObject();
                int cx = x + i * (cw + 4);
                boolean sel = bool(card, "selected"), hover = c.hovered(cx, y, cw, h);
                Theme.inset(g, cx, y, cw, h, sel ? Theme.ROW_SELECTED : hover ? Theme.ROW_HOVER : Theme.ROW);
                if (sel) g.outline(cx, y, cw, h, Theme.AMBER);
                g.item(item(str(card, "icon")), cx + 4, y + 4);
                g.text(c.font(), Theme.fit(c.font(), str(card, "title", ""), cw - 26), cx + 23, y + 5, sel ? Theme.AMBER : Theme.TEXT, false);
                String tag = str(card, "tag");
                if (tag != null) Theme.pill(g, c.font(), tag, cx + 23, y + 15, 0xFF5A4410, 0xFFFFD24A);
                int ly = y + 26;
                for (FormattedCharSequence line : wrap(c.font(), str(card, "text", ""), cw - 8)) {
                    g.text(c.font(), line, cx + 4, ly, Theme.MUTED, false);
                    ly += 10;
                }
                if (hover && card.has("disabled")) c.tooltip(List.of("✖ " + str(card, "disabled")));
            }
        }

        @Override
        boolean click(Ctx c, double mx, double my, boolean shift) {
            JsonArray cards = arr(o, "cards");
            int i = (int) ((mx - x) / (cw(w) + 4));
            if (i < 0 || i >= cards.size()) return true;
            JsonObject card = cards.get(i).getAsJsonObject();
            if (card.has("disabled")) return true;
            JsonObject args = new JsonObject();
            args.addProperty("id", str(card, "id", ""));
            c.send(str(o, "action", ""), args);
            return true;
        }
    }

    /** A paper page with a REDACTED stamp; long text is split into pages. */
    static class Doc extends Node {
        List<FormattedCharSequence> lines = List.of();
        int perPage = 1;

        Doc(JsonObject o) {
            super(o);
        }

        @Override
        int measure(Ctx c, int width) {
            return 60;
        }

        @Override
        void place(Ctx c, int x, int y, int w, int h) {
            super.place(c, x, y, w, h);
            lines = wrap(c.font(), c.tr(str(o, "body", "")), w - 16);
            perPage = Math.max(1, (h - 34) / 10);
        }

        int pages() {
            return Math.max(1, (lines.size() + perPage - 1) / perPage);
        }

        int page(Ctx c) {
            Object v = c.state().get("page");
            return Math.max(0, Math.min(pages() - 1, v instanceof Integer i ? i : 0));
        }

        @Override
        void render(Ctx c) {
            GuiGraphicsExtractor g = c.g();
            String title = c.tr(str(o, "title", ""));
            g.text(c.font(), Theme.fit(c.font(), title, w - 16), x + 8, y + 4, Theme.INK_RED, false);
            g.fill(x + 8, y + 14, x + w - 8, y + 15, 0x60000000);
            int page = page(c), ly = y + 19;
            for (int i = page * perPage; i < Math.min(lines.size(), (page + 1) * perPage); i++) {
                g.text(c.font(), lines.get(i), x + 8, ly, Theme.INK, false);
                ly += 10;
            }
            String stamp = str(o, "stamp");
            if (stamp != null) {
                g.pose().pushMatrix();
                g.pose().translate(x + w - 70, y + h - 40);
                g.pose().rotate(-0.25f);
                int sw = c.font().width(stamp) + 8;
                g.outline(-4, -3, sw, 14, 0xA0B01E1E);
                g.text(c.font(), stamp, 0, 1, 0xC0B01E1E, false);
                g.pose().popMatrix();
            }
            if (pages() > 1) {
                JsonObject prev = new JsonObject(), next = new JsonObject();
                prev.addProperty("label", "◂");
                next.addProperty("label", "▸");
                if (page == 0) prev.addProperty("disabled", "First page.");
                if (page == pages() - 1) next.addProperty("disabled", "Last page.");
                drawButton(c, prev, x + 8, y + h - 14, 16, 12);
                drawButton(c, next, x + w - 24, y + h - 14, 16, 12);
                g.centeredText(c.font(), "Page " + (page + 1) + " / " + pages(), x + w / 2, y + h - 12, Theme.INK_MUTED);
            }
        }

        @Override
        boolean click(Ctx c, double mx, double my, boolean shift) {
            if (my < y + h - 14) return true;
            int page = page(c);
            if (mx < x + 26) c.state().put("page", Math.max(0, page - 1));
            else if (mx > x + w - 26) c.state().put("page", Math.min(pages() - 1, page + 1));
            return true;
        }
    }

    private Nodes() {}
}
