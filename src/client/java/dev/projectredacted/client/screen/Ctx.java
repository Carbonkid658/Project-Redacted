package dev.projectredacted.client.screen;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;
import java.util.Map;

/** What widgets need from the screen while drawing and handling input. */
public interface Ctx {
    GuiGraphicsExtractor g();

    Font font();

    int mx();

    int my();

    /** Paper style: dark ink on light paper instead of the terminal's light text on a dark screen. */
    boolean paper();

    /** The mouse is over this rectangle and not clipped away. */
    boolean hovered(int x, int y, int w, int h);

    void tooltip(List<String> lines);

    /** Sends a button press to the server. */
    void send(String action, JsonObject args);

    /** Presses a JSON button: shows its disabled reason, info popup or confirmation first if it has one. */
    void press(JsonObject button, boolean shift, String typedText);

    /** Client-side state that survives view refreshes: scroll positions, typed text, the open page. */
    Map<String, Object> state();

    String focus();

    void focus(String key);

    void pushClip(int x, int y, int w, int h);

    void popClip();

    /** Bar values to show while a toggle that would change them is hovered. */
    Map<String, Double> preview();

    void previewNext(String id, double value);

    /** Sends mouse drags and the release to this node until the button is let go (the color wheel). */
    void capture(Nodes.Node node);

    /** Resolves "tr:" translation keys. */
    String tr(String text);
}
