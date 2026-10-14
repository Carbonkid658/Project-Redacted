package dev.projectredacted.site.gen;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/** A part placed in a site (saved with the site). Room parts also have Room records pointing back at it. */
public class PlacedPart {
    public String kit;
    public String id;
    public BlockPos origin;
    public int rot;
    public int storey;
    /** Connector indexes joined to something. */
    public int used;
    public boolean built;
    /** Demolished; its space is free again. */
    public boolean removed;
    public int progress;
    /** Built at the fast generation speed with forced chunks (Level 0), not by normal construction. */
    public boolean fast;
    /** Room numbers this part registered. */
    public List<Integer> rooms = new ArrayList<>();
    /** Doorways of older parts this part opens when it finishes. */
    public List<Doorway> reopen = new ArrayList<>();
    public long seed;
    /** Its job in the layout (v4): hub, link, spine, tunnel, checkpoint, main_hall, side_hall, room. */
    public String role;
    /** The area it belongs to ("living-2"), if any, and its side hall number (0 for the main hall). */
    public String area;
    public int hall;
    /** The connector it was entered from (checkpoints read their sides from it); -1 if unknown. */
    public int entry = -1;
    /** A checkpoint built from its area's side (the Admin checkpoint grows out of the office). */
    public boolean entryInside;

    /** A hallway a player built by hand (v4 §5.8, V25): its box, and the Area Hall Marker that made it. */
    public int[] hand;
    public BlockPos marker;

    public PlacedPart() {}

    public SitePart part() {
        return hand != null ? null : PartKit.active().byId(kit, id);
    }

    public Box box() {
        if (hand != null) return new Box(hand[0], hand[1], hand[2], hand[3], hand[4], hand[5]);
        SitePart p = part();
        return p == null ? null : p.box(origin, rot);
    }
}
