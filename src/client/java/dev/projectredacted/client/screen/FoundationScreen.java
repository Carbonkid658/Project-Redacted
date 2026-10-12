package dev.projectredacted.client.screen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.projectredacted.ProjectRedacted;
import dev.projectredacted.net.ActionPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The one client screen every Foundation GUI uses (GUI design §2). The server sends a view; this draws the
 * terminal frame around it, keeps scroll positions and typed text between refreshes, and sends clicks back.
 */
public class FoundationScreen extends Screen implements Ctx {
    private static final Identifier LOGO = ProjectRedacted.id("textures/gui/logo.png");
    private static final int HEADER = 18, FOOTER = 16, TAB_W = 78, TAB_H = 18, PAD = 5;

    static FoundationScreen current;
    /** The serial the player just closed, so a refresh already on its way doesn't reopen it. */
    static int lastClosed;

    private int serial;
    private String screenId;
    private JsonObject view = new JsonObject();
    private Nodes.Column body = new Nodes.Column(List.of(), "body");
    private final Map<String, Object> state = new HashMap<>();
    private String focus;
    private Map<String, Double> preview = new HashMap<>(), nextPreview = new HashMap<>();
    private boolean closedByServer;

    private GuiGraphicsExtractor g;
    private int mx, my;
    private List<String> tooltip;
    private final Deque<int[]> clips = new ArrayDeque<>();

    // panel layout, set every frame
    private int px, py, pw, ph, cx, cy, cw, ch;

    // dialog: a confirm, a typed confirm or an info popup
    private JsonObject modalButton;
    private boolean modalShift;
    private String modalTyped, modalInfo;
    private boolean drawingModal;

    private String toast;
    private boolean toastError;
    private long toastUntil;

    FoundationScreen(int serial, String screenId, JsonObject view) {
        super(Component.literal(Nodes.str(view, "title", "")));
        update(serial, screenId, view);
    }

    /** The open Foundation screen, if any. */
    public static FoundationScreen current() {
        return current;
    }

    public int serial() {
        return serial;
    }

    /** A fresh view from the server. Same screen: keep scroll and typed text. Different screen: start clean. */
    void update(int serial, String screenId, JsonObject view) {
        boolean sameScreen = screenId.equals(this.screenId) && serial == this.serial;
        String oldTab = Nodes.str(this.view, "tab");
        if (!sameScreen) {
            state.clear();
            focus = null;
            modalButton = null;
            modalInfo = null;
        } else if (oldTab != null && !oldTab.equals(Nodes.str(view, "tab"))) {
            state.keySet().removeIf(k -> k.startsWith("col") || k.startsWith("list:") || k.equals("body") || k.equals("page"));
        }
        this.serial = serial;
        this.screenId = screenId;
        this.view = view;
        this.body = new Nodes.Column(Nodes.children(Nodes.arr(view, "body")), "body");
        if (view.has("toast")) showToast(Nodes.str(view, "toast", ""), Nodes.bool(view, "toastError"));
    }

    static JsonObject parse(String json) {
        try {
            return JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException e) {
            return new JsonObject();
        }
    }

    void closeFromServer() {
        closedByServer = true;
        onClose();
    }

    private void showToast(String text, boolean error) {
        toast = text;
        toastError = error;
        toastUntil = System.currentTimeMillis() + 3500;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        if (!closedByServer) ClientPlayNetworking.send(new ActionPayload(serial, "_close", "{}"));
        lastClosed = serial;
        if (current == this) current = null;
        super.removed();
    }

    // ================================================================ layout

    private boolean paperStyle() {
        return "paper".equals(Nodes.str(view, "style"));
    }

    private boolean hazardStyle() {
        return "hazard".equals(Nodes.str(view, "style"));
    }

    private JsonArray tabs() {
        return Nodes.arr(view, "tabs");
    }

    private boolean hasBack() {
        return Nodes.bool(view, "hasParent") || Nodes.bool(view, "back");
    }

    private boolean hasFooter() {
        return hasBack() || view.has("footer") || view.has("footerButton");
    }

    private void layout() {
        String size = Nodes.str(view, "size", "large");
        int w = switch (size) {
            case "small" -> 200;
            case "medium" -> 280;
            default -> 330;
        };
        int h = switch (size) {
            case "small" -> 120;
            case "medium" -> 180;
            default -> 210;
        };
        pw = Math.min(w, width - 8);
        ph = Math.min(h, height - 8);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        int left = px + 4 + (tabs().isEmpty() ? 0 : TAB_W + 2);
        int bottom = py + ph - 4 - (hasFooter() ? FOOTER + 2 : 0) - (view.has("banner") ? 12 : 0);
        cx = left + PAD;
        cy = py + HEADER + 2 + PAD;
        cw = px + pw - 4 - PAD - cx;
        ch = bottom - PAD - cy;
    }

    // ================================================================ drawing

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        g = graphics;
        mx = mouseX;
        my = mouseY;
        tooltip = null;
        preview = nextPreview;
        nextPreview = new HashMap<>();
        layout();
        drawingModal = false;

        drawFrame();
        body.place(this, cx, cy, cw, ch);
        body.render(this);
        drawFooter();
        if (modalButton != null || modalInfo != null) {
            drawingModal = true;
            drawModal();
        }
        drawToast();
        if (tooltip != null && !tooltip.isEmpty()) {
            List<Component> lines = new ArrayList<>();
            for (String s : tooltip) lines.add(Component.literal(s));
            g.setComponentTooltipForNextFrame(font, lines, mx, my);
        }
    }

    private void drawFrame() {
        boolean paper = paperStyle();
        // case
        Theme.bevel(g, px, py, pw, ph, Theme.CASE);
        g.outline(px - 1, py - 1, pw + 2, ph + 2, 0xFF000000);
        if (hazardStyle()) {
            Theme.hazard(g, px + 2, py + 2, pw - 4, 3);
            Theme.hazard(g, px + 2, py + ph - 5, pw - 4, 3);
        }
        // header
        int hy = py + 3;
        g.blit(RenderPipelines.GUI_TEXTURED, LOGO, px + 5, hy + 2, 0f, 0f, 12, 12, 12, 12);
        int tx = px + 21;
        if (view.has("face")) {
            Theme.inset(g, tx, hy, 16, 16, 0xFF06090A);
            Nodes.face(g, Nodes.str(view, "face"), tx, hy, 16);
            tx += 20;
        }
        int right = px + pw - 18;
        String badge = Nodes.str(view, "badge");
        if (badge != null) {
            right -= Theme.pillWidth(font, badge) + 4;
            Theme.pill(g, font, badge, right, hy + 3, 0xFF26241F, Theme.AMBER);
        }
        String tag = Nodes.str(view, "tag");
        if (tag != null) {
            right -= Theme.pillWidth(font, tag) + 4;
            Theme.pill(g, font, tag, right, hy + 3, 0xFF2F5FA8, 0xFFFFFFFF);
        }
        g.text(font, Theme.fit(font, tr(Nodes.str(view, "title", "")), right - tx - 4), tx, hy + 4, Theme.CASE_TEXT, false);
        // close
        boolean closeHover = hovered(px + pw - 16, hy + 1, 13, 13);
        Theme.bevel(g, px + pw - 16, hy + 1, 13, 13, closeHover ? 0xFFB33A3A : Theme.CASE_DARK);
        g.centeredText(font, "✕", px + pw - 10, hy + 4, 0xFFFFFFFF);
        if (closeHover) tooltip(List.of("Close (Esc)"));

        // tabs
        JsonArray tabs = tabs();
        int top = py + HEADER + 2;
        int bottom = py + ph - 4 - (hasFooter() ? FOOTER + 2 : 0);
        if (!tabs.isEmpty()) {
            Theme.inset(g, px + 4, top, TAB_W, bottom - top, 0xFF3B3A34);
            String active = Nodes.str(view, "tab", "");
            for (int i = 0; i < tabs.size(); i++) {
                JsonObject t = tabs.get(i).getAsJsonObject();
                int ty = top + 2 + i * TAB_H;
                if (ty + TAB_H > bottom) break;
                boolean sel = active.equals(Nodes.str(t, "id")), locked = t.has("locked");
                boolean hover = hovered(px + 5, ty, TAB_W - 2, TAB_H - 1);
                int bg = sel ? Theme.SCREEN : hover && !locked ? 0xFF4B4A42 : 0xFF3B3A34;
                g.fill(px + 5, ty, px + 3 + TAB_W, ty + TAB_H - 1, bg);
                if (sel) g.fill(px + 5, ty, px + 7, ty + TAB_H - 1, Theme.AMBER);
                if (t.has("icon")) g.item(Nodes.item(Nodes.str(t, "icon")), px + 8, ty + 1);
                String label = (locked ? "🔒 " : "") + tr(Nodes.str(t, "label", ""));
                g.text(font, Theme.fit(font, label, TAB_W - 28), px + 26, ty + 5, locked ? Theme.MUTED : sel ? Theme.AMBER : Theme.TEXT, false);
                if (hover && locked) tooltip(List.of("🔒 " + Nodes.str(t, "locked")));
            }
        }
        // the screen itself
        int left = px + 4 + (tabs.isEmpty() ? 0 : TAB_W + 2);
        int screenBottom = bottom - (view.has("banner") ? 12 : 0);
        Theme.inset(g, left, top, px + pw - 4 - left, screenBottom - top, paper ? Theme.PAPER : Theme.SCREEN);
        if (!paper) {
            for (int sy = top + 2; sy < screenBottom - 1; sy += 3) g.fill(left + 1, sy, px + pw - 5, sy + 1, 0x0CFFFFFF);
        }
        if (view.has("banner")) {
            JsonObject b = view.getAsJsonObject("banner");
            int col = Theme.color(Nodes.str(b, "color", "red"), false);
            g.fill(left, screenBottom + 1, px + pw - 4, screenBottom + 11, Theme.shade(col, 0.45f));
            if ("red".equals(Nodes.str(b, "color", "red"))) Theme.hazard(g, left, screenBottom + 1, 4, 10);
            g.centeredText(font, Theme.fit(font, tr(Nodes.str(b, "text", "")), px + pw - 4 - left - 12), (left + px + pw - 4) / 2, screenBottom + 2, 0xFFFFFFFF);
        }
    }

    private int footerY() {
        return py + ph - 4 - FOOTER;
    }

    private JsonObject backButton() {
        JsonObject b = new JsonObject();
        b.addProperty("label", "◂ Back");
        b.addProperty("action", "_back");
        return b;
    }

    private void drawFooter() {
        if (!hasFooter()) return;
        int fy = footerY(), fx = px + 4;
        if (hasBack()) {
            Nodes.drawButton(this, backButton(), fx, fy, 50, FOOTER);
            fx += 54;
        }
        int right = px + pw - 4;
        if (view.has("footerButton")) {
            JsonObject b = view.getAsJsonObject("footerButton");
            int bw = Nodes.buttonWidth(this, b);
            Nodes.drawButton(this, b, right - bw, fy, bw, FOOTER);
            right -= bw + 4;
        }
        String hint = Nodes.str(view, "footer");
        if (hint != null) g.text(font, Theme.fit(font, tr(hint), right - fx - 2), fx + 2, fy + 4, 0xFF4A473F, false);
    }

    private int[] modalBox() {
        int w = Math.min(220, pw - 20);
        List<FormattedCharSequence> lines = Nodes.wrap(font, modalText(), w - 16);
        int h = 16 + lines.size() * 10 + (modalTyped != null ? 22 : 0) + 22;
        return new int[]{px + (pw - w) / 2, py + (ph - h) / 2, w, h};
    }

    private String modalText() {
        if (modalInfo != null) return tr(modalInfo);
        String q = tr(Nodes.str(modalButton, "confirm", "Are you sure?"));
        String typed = Nodes.str(modalButton, "typed");
        return typed == null ? q : q + "\n\nType \"" + typed + "\" to confirm.";
    }

    private void drawModal() {
        g.fill(px, py, px + pw, py + ph, 0xB0000000);
        int[] b = modalBox();
        int x = b[0], y = b[1], w = b[2], h = b[3];
        boolean danger = modalButton != null && "danger".equals(Nodes.str(modalButton, "style"));
        Theme.bevel(g, x, y, w, h, Theme.CASE);
        Theme.inset(g, x + 3, y + 3, w - 6, h - 6, Theme.SCREEN);
        if (danger) Theme.hazard(g, x + 4, y + 4, w - 8, 3);
        int ly = y + 10;
        for (FormattedCharSequence line : Nodes.wrap(font, modalText(), w - 16)) {
            g.text(font, line, x + 8, ly, Theme.TEXT, false);
            ly += 10;
        }
        if (modalTyped != null) {
            Nodes.drawField(this, "modal", "Type here", x + 8, ly + 4, w - 16, 16, null);
        }
        int by = y + h - 22;
        if (modalInfo != null) {
            Nodes.drawButton(this, label("OK", "primary", null), x + w / 2 - 30, by, 60, 16);
        } else {
            Nodes.drawButton(this, label("Cancel", null, null), x + 8, by, 70, 16);
            JsonObject yes = label(Nodes.str(modalButton, "label", "Yes"), danger ? "danger" : "confirm", null);
            String typed = Nodes.str(modalButton, "typed");
            if (typed != null && !typed.equalsIgnoreCase(modalTyped.trim())) yes.addProperty("disabled", "Type \"" + typed + "\" first.");
            Nodes.drawButton(this, yes, x + w - 78, by, 70, 16);
        }
    }

    private static JsonObject label(String text, String style, String action) {
        JsonObject b = new JsonObject();
        b.addProperty("label", text);
        if (style != null) b.addProperty("style", style);
        if (action != null) b.addProperty("action", action);
        return b;
    }

    private void drawToast() {
        if (toast == null || System.currentTimeMillis() > toastUntil) return;
        List<FormattedCharSequence> lines = Nodes.wrap(font, toast, 150);
        int w = 8;
        for (FormattedCharSequence l : lines) w = Math.max(w, font.width(l) + 12);
        int h = lines.size() * 10 + 6;
        int x = px + pw - w - 6, y = py + HEADER + 4;
        int col = toastError ? 0xFF7A1F1F : 0xFF1F5A2E;
        Theme.bevel(g, x, y, w, h, col);
        int ly = y + 4;
        for (FormattedCharSequence l : lines) {
            g.text(font, l, x + 6, ly, 0xFFFFFFFF, false);
            ly += 10;
        }
    }

    // ================================================================ Ctx

    @Override
    public GuiGraphicsExtractor g() {
        return g;
    }

    @Override
    public Font font() {
        return font;
    }

    @Override
    public int mx() {
        return mx;
    }

    @Override
    public int my() {
        return my;
    }

    @Override
    public boolean paper() {
        return paperStyle();
    }

    @Override
    public boolean hovered(int x, int y, int w, int h) {
        if ((modalButton != null || modalInfo != null) && !drawingModal) return false;
        if (mx < x || mx >= x + w || my < y || my >= y + h) return false;
        int[] clip = clips.peek();
        return clip == null || (mx >= clip[0] && mx < clip[2] && my >= clip[1] && my < clip[3]);
    }

    @Override
    public void tooltip(List<String> lines) {
        if (lines != null && !lines.isEmpty()) tooltip = lines;
    }

    @Override
    public void send(String action, JsonObject args) {
        if (action == null || action.isEmpty()) return;
        click();
        if (action.equals("_close") || action.equals("close")) {
            onClose();
            return;
        }
        ClientPlayNetworking.send(new ActionPayload(serial, action, args.toString()));
    }

    private void click() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    @Override
    public void press(JsonObject button, boolean shift, String typedText) {
        if (button.has("disabled")) {
            showToast(Nodes.str(button, "disabled", "Not available."), true);
            return;
        }
        if (button.has("info")) {
            click();
            modalInfo = Nodes.str(button, "info");
            return;
        }
        if (button.has("confirm")) {
            click();
            modalButton = button;
            modalShift = shift;
            modalTyped = button.has("typed") ? "" : null;
            state.put("modal", "");
            state.put("modalPending", typedText == null ? "" : typedText);
            focus = button.has("typed") ? "modal" : null;
            return;
        }
        fire(button, shift, typedText);
    }

    private void fire(JsonObject button, boolean shift, String typedText) {
        JsonObject args = button.has("arg") && button.get("arg").isJsonObject() ? button.getAsJsonObject("arg").deepCopy() : new JsonObject();
        if (shift) args.addProperty("shift", true);
        if (typedText != null) args.addProperty("text", typedText);
        send(Nodes.str(button, "action", ""), args);
    }

    private void closeModal() {
        modalButton = null;
        modalInfo = null;
        modalTyped = null;
        if ("modal".equals(focus)) focus = null;
    }

    private void confirmModal() {
        if (modalInfo != null) {
            closeModal();
            return;
        }
        JsonObject b = modalButton;
        String typed = Nodes.str(b, "typed");
        if (typed != null && !typed.equalsIgnoreCase(modalTyped.trim())) return;
        String text = typed != null ? modalTyped.trim() : (String) state.get("modalPending");
        boolean shift = modalShift;
        closeModal();
        fire(b, shift, text == null || text.isEmpty() ? null : text);
    }

    @Override
    public Map<String, Object> state() {
        return state;
    }

    @Override
    public String focus() {
        return focus;
    }

    @Override
    public void focus(String key) {
        focus = key;
    }

    @Override
    public void pushClip(int x, int y, int w, int h) {
        int[] parent = clips.peek();
        int x1 = x, y1 = y, x2 = x + w, y2 = y + h;
        if (parent != null) {
            x1 = Math.max(x1, parent[0]);
            y1 = Math.max(y1, parent[1]);
            x2 = Math.min(x2, parent[2]);
            y2 = Math.min(y2, parent[3]);
        }
        x2 = Math.max(x1, x2);
        y2 = Math.max(y1, y2);
        clips.push(new int[]{x1, y1, x2, y2});
        g.enableScissor(x1, y1, x2, y2);
    }

    @Override
    public void popClip() {
        clips.pop();
        g.disableScissor();
        int[] parent = clips.peek();
        if (parent != null) g.enableScissor(parent[0], parent[1], parent[2], parent[3]);
    }

    @Override
    public Map<String, Double> preview() {
        return preview;
    }

    @Override
    public void previewNext(String id, double value) {
        nextPreview.put(id, value);
    }

    @Override
    public String tr(String text) {
        if (text == null) return "";
        return text.startsWith("tr:") ? I18n.get(text.substring(3)) : text;
    }

    // ================================================================ input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return false;
        double x = event.x(), y = event.y();
        boolean shift = event.hasShiftDown();
        if (modalButton != null || modalInfo != null) {
            int[] b = modalBox();
            int by = b[1] + b[3] - 22;
            if (y >= by && y < by + 16) {
                if (modalInfo != null) {
                    if (x >= b[0] + b[2] / 2 - 30 && x < b[0] + b[2] / 2 + 30) confirmModal();
                } else if (x >= b[0] + 8 && x < b[0] + 78) {
                    click();
                    closeModal();
                } else if (x >= b[0] + b[2] - 78 && x < b[0] + b[2] - 8) {
                    confirmModal();
                }
            } else if (x < b[0] || x >= b[0] + b[2] || y < b[1] || y >= b[1] + b[3]) {
                closeModal();
            }
            return true;
        }
        // close
        if (x >= px + pw - 16 && x < px + pw - 3 && y >= py + 4 && y < py + 17) {
            click();
            onClose();
            return true;
        }
        // tabs
        JsonArray tabs = tabs();
        int top = py + HEADER + 2;
        if (!tabs.isEmpty() && x >= px + 5 && x < px + 3 + TAB_W && y >= top + 2) {
            int i = (int) ((y - top - 2) / TAB_H);
            if (i >= 0 && i < tabs.size()) {
                JsonObject t = tabs.get(i).getAsJsonObject();
                if (t.has("locked")) showToast(Nodes.str(t, "locked", "Locked."), true);
                else {
                    JsonObject args = new JsonObject();
                    args.addProperty("id", Nodes.str(t, "id", ""));
                    send("tab", args);
                }
            }
            return true;
        }
        // footer
        if (hasFooter() && y >= footerY() && y < footerY() + FOOTER) {
            if (hasBack() && x >= px + 4 && x < px + 54) {
                send("_back", new JsonObject());
                return true;
            }
            if (view.has("footerButton")) {
                JsonObject b = view.getAsJsonObject("footerButton");
                int bw = Nodes.buttonWidth(this, b);
                if (x >= px + pw - 4 - bw && x < px + pw - 4) {
                    press(b, shift, null);
                    return true;
                }
            }
        }
        focus = null;
        if (body.inside(x, y)) {
            body.click(this, x, y, shift);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (modalButton != null || modalInfo != null) return true;
        if (body.scroll(this, x, y, scrollY)) return true;
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (focus == null || !event.isAllowedChatCharacter()) return super.charTyped(event);
        if ("modal".equals(focus)) {
            if (modalTyped != null && modalTyped.length() < 64) modalTyped += event.codepointAsString();
            state.put("modal", modalTyped);
            return true;
        }
        String text = (String) state.getOrDefault(focus, "");
        if (text.length() < 64) state.put(focus, text + event.codepointAsString());
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (modalButton != null || modalInfo != null) {
            if (event.isEscape()) closeModal();
            else if (event.isConfirmation()) confirmModal();
            else if (key == 259 && modalTyped != null && !modalTyped.isEmpty()) {
                modalTyped = modalTyped.substring(0, modalTyped.length() - 1);
                state.put("modal", modalTyped);
            }
            return true;
        }
        if (focus != null) {
            String text = (String) state.getOrDefault(focus, "");
            if (event.isEscape()) {
                focus = null;
                return true;
            }
            if (key == 259) { // backspace
                if (!text.isEmpty()) state.put(focus, text.substring(0, text.length() - 1));
                return true;
            }
            if (event.isConfirmation() && focus.startsWith("input:")) {
                Nodes.Input input = findInput(body, focus);
                if (input != null) input.submit(this, event.hasShiftDown());
                return true;
            }
            if (key == 258 && focus.startsWith("input:")) { // tab completes the suggestion
                Nodes.Input input = findInput(body, focus);
                String s = input == null ? null : input.suggestion(this);
                if (s != null) state.put(focus, s);
                return true;
            }
            // swallow everything else so typing "e" doesn't do anything else
            return !event.isEscape();
        }
        return super.keyPressed(event);
    }

    private static Nodes.Input findInput(Nodes.Node node, String key) {
        if (node instanceof Nodes.Input in && in.key().equals(key)) return in;
        List<? extends Nodes.Node> kids = switch (node) {
            case Nodes.Column c -> c.kids;
            case Nodes.Cols c -> c.cols;
            case Nodes.Row r -> r.kids;
            case Nodes.Box b -> b.kids;
            default -> List.of();
        };
        for (Nodes.Node n : kids) {
            Nodes.Input found = findInput(n, key);
            if (found != null) return found;
        }
        return null;
    }
}
