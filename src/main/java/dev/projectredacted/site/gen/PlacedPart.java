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

    public PlacedPart() {}

    public SitePart part() {
        return PartKit.active().byId(kit, id);
    }

    public Box box() {
        SitePart p = part();
        return p == null ? null : p.box(origin, rot);
    }
}
