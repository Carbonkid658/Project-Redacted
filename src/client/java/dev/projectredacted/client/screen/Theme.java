package dev.projectredacted.client.screen;

import net.minecraft.client.gui.Font;
import dev.projectredacted.ProjectRedacted;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * The Foundation look (GUI design §2): an off-white terminal case around a dark screen with amber and green
 * text, hazard stripes on danger actions. Colors are ARGB.
 */
public final class Theme {
    public static final int CASE = 0xFFC9C5B9, CASE_LIGHT = 0xFFE4E0D4, CASE_DARK = 0xFF7E7A6E, CASE_TEXT = 0xFF26241F;
    public static final int SCREEN = 0xFF0D110F, SCREEN_EDGE = 0xFF2B3830, ROW = 0xFF151B18, ROW_HOVER = 0xFF1F2924, ROW_SELECTED = 0xFF3A3018;
    public static final int TEXT = 0xFFE6E0C8, MUTED = 0xFF8E8B80, AMBER = 0xFFFFB347;
    public static final int PAPER = 0xFFEDE3C8, INK = 0xFF2B2418, INK_MUTED = 0xFF6E6250, INK_RED = 0xFF8B1E1E;
    public static final int HAZARD_YELLOW = 0xFFF2C21B, HAZARD_BLACK = 0xFF1A1A1A;

    /** Named colors from the server. In paper mode light text becomes ink. */
    public static int color(String name, boolean paper) {
        if (name == null) return paper ? INK : TEXT;
        return switch (name) {
            case "gray" -> paper ? INK_MUTED : MUTED;
            case "dark" -> 0xFF26302B;
            case "amber" -> paper ? 0xFF8A5A00 : AMBER;
            case "green" -> paper ? 0xFF2E6B2E : 0xFF6BD97A;
            case "red" -> paper ? INK_RED : 0xFFFF5F57;
            case "blue" -> paper ? 0xFF1F4F8F : 0xFF6AA8FF;
            case "aqua" -> paper ? 0xFF1E6E6E : 0xFF5FE0E0;
            case "gold" -> paper ? 0xFF7A5A00 : 0xFFFFD24A;
            case "purple" -> paper ? 0xFF5E2E8E : 0xFFC58BFF;
            case "pink" -> paper ? 0xFF8E2E5E : 0xFFFF8FBF;
            case "teal" -> paper ? 0xFF1E6E5E : 0xFF45C9A6;
            case "yellow" -> 0xFFFFF15A;
            default -> paper ? INK : TEXT;
        };
    }

    /** A darker version of a color, for fills behind text. */
    public static int shade(int argb, float factor) {
        int r = (int) (((argb >> 16) & 0xFF) * factor), g = (int) (((argb >> 8) & 0xFF) * factor), b = (int) ((argb & 0xFF) * factor);
        return 0xFF000000 | Math.min(255, r) << 16 | Math.min(255, g) << 8 | Math.min(255, b);
    }

    public static int lighten(int argb, int amount) {
        int r = Math.min(255, ((argb >> 16) & 0xFF) + amount), g = Math.min(255, ((argb >> 8) & 0xFF) + amount), b = Math.min(255, (argb & 0xFF) + amount);
        return (argb & 0xFF000000) | r << 16 | g << 8 | b;
    }

    /** A raised bevelled box, like a key on an old terminal. */
    public static void bevel(GuiGraphicsExtractor g, int x, int y, int w, int h, int fill) {
        g.fill(x, y, x + w, y + h, fill);
        g.fill(x, y, x + w, y + 1, lighten(fill, 40));
        g.fill(x, y, x + 1, y + h, lighten(fill, 25));
        g.fill(x, y + h - 1, x + w, y + h, shade(fill, 0.55f));
        g.fill(x + w - 1, y, x + w, y + h, shade(fill, 0.65f));
    }

    /** A sunken box: the dark screen area, text fields. */
    public static void inset(GuiGraphicsExtractor g, int x, int y, int w, int h, int fill) {
        g.fill(x, y, x + w, y + h, fill);
        g.fill(x, y, x + w, y + 1, shade(fill, 0.5f));
        g.fill(x, y, x + 1, y + h, shade(fill, 0.5f));
        g.fill(x, y + h - 1, x + w, y + h, lighten(fill, 30));
        g.fill(x + w - 1, y, x + w, y + h, lighten(fill, 30));
    }

    private static final Map<String, Identifier> SPRITES = new HashMap<>();

    /**
     * Draws a GUI sprite from assets/projectredacted/textures/gui/sprites/foundation/. Each PNG can have a .mcmeta
     * that says how it stretches (nine-slice keeps the borders crisp at any size).
     */
    public static void sprite(GuiGraphicsExtractor g, String name, int x, int y, int w, int h) {
        if (w > 0 && h > 0) g.blitSprite(RenderPipelines.GUI_TEXTURED, SPRITES.computeIfAbsent(name, n -> ProjectRedacted.id("foundation/" + n)), x, y, w, h);
    }

    /** A white sprite tinted with an ARGB color, for things colored by data (bars, pills, map cells). */
    public static void sprite(GuiGraphicsExtractor g, String name, int x, int y, int w, int h, int color) {
        if (w > 0 && h > 0) g.blitSprite(RenderPipelines.GUI_TEXTURED, SPRITES.computeIfAbsent(name, n -> ProjectRedacted.id("foundation/" + n)), x, y, w, h, color);
    }

    /** Yellow and black blocks along a strip. */
    public static void hazard(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        sprite(g, "hazard", x, y, w, h);
    }

    public static void pill(GuiGraphicsExtractor g, Font font, String text, int x, int y, int bg, int fg) {
        sprite(g, "pill", x, y, font.width(text) + 6, 10, bg);
        g.text(font, text, x + 3, y + 1, fg, false);
    }

    public static int pillWidth(Font font, String text) {
        return font.width(text) + 6;
    }

    /** Text cut to fit, with an ellipsis. */
    public static String fit(Font font, String text, int width) {
        if (text == null) return "";
        if (font.width(text) <= width) return text;
        return font.plainSubstrByWidth(text, Math.max(0, width - font.width("…"))) + "…";
    }

    private Theme() {}
}
