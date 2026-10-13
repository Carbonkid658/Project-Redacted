package dev.projectredacted.site.gen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** An open doorway in a site that growth can attach to (saved with the site). */
public class Doorway {
    public BlockPos pos;
    public Direction facing;
    public String type;
    public Direction top = Direction.UP;
    /** The placed part it belongs to, or -1 for a room designated by hand. */
    public int part = -1;
    /** The room it belongs to, or -1. */
    public int room = -1;

    public Doorway() {}

    public static Doorway of(SitePart.Connector c, int part) {
        Doorway d = new Doorway();
        d.pos = c.pos();
        d.facing = c.facing();
        d.type = c.type();
        d.top = c.top();
        d.part = part;
        return d;
    }

    public SitePart.Connector connector() {
        return new SitePart.Connector(pos, facing, type, top == null ? Direction.UP : top, null);
    }

    public boolean same(Doorway o) {
        return pos.equals(o.pos) && facing == o.facing;
    }
}
