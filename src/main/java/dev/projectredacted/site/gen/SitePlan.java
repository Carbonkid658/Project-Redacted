package dev.projectredacted.site.gen;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/** What the assembler decided (design §5 step 7): parts with rotation and position. Nothing is placed yet. */
public class SitePlan {
    public static class PlanPart {
        public final SitePart part;
        public final BlockPos origin;
        public final int rot;
        public final Box box;
        public final int storey;
        /** Connector indexes joined to something. */
        public int used;
        /** See {@link PlacedPart#role}. */
        public String role;
        public String area;
        public int hall;
        public int entry = -1;
        public boolean entryInside;
        /** For a companion (v4 §5.5): the plan index of the part whose link it joined, else -1. */
        public int linkedFrom = -1;

        PlanPart(SitePart part, BlockPos origin, int rot, int storey) {
            this.part = part;
            this.origin = origin;
            this.rot = rot;
            this.box = part.box(origin, rot);
            this.storey = storey;
        }

        public SitePart.Connector connector(int i) {
            return part.worldConnector(i, origin, rot);
        }
    }

    public final List<PlanPart> parts = new ArrayList<>();
    /** Existing doorways (indexes into the site's doorway list) the plan opens up. */
    public final List<Integer> openedDoorways = new ArrayList<>();
    public long seed;
    public int overwrites;
    /** The area the room goes into (v4 §3.1), and whether this plan starts it. */
    public String area;
    public boolean newArea;
    /** "Lab → Research 2, new side hall", for the preview. */
    public String note;
    /** Why no plan was found, or null. */
    public String error;

    public boolean ok() {
        return error == null && !parts.isEmpty();
    }

    public int count(SitePart.Kind kind) {
        int n = 0;
        for (PlanPart p : parts) if (p.part.kind == kind) n++;
        return n;
    }

    public Box bounds() {
        if (parts.isEmpty()) return null;
        int x1 = Integer.MAX_VALUE, y1 = Integer.MAX_VALUE, z1 = Integer.MAX_VALUE, x2 = Integer.MIN_VALUE, y2 = Integer.MIN_VALUE, z2 = Integer.MIN_VALUE;
        for (PlanPart p : parts) {
            x1 = Math.min(x1, p.box.x1());
            y1 = Math.min(y1, p.box.y1());
            z1 = Math.min(z1, p.box.z1());
            x2 = Math.max(x2, p.box.x2());
            y2 = Math.max(y2, p.box.y2());
            z2 = Math.max(z2, p.box.z2());
        }
        return new Box(x1, y1, z1, x2, y2, z2);
    }

    /** A short key that tells two plans apart, for cycling spots. */
    public String key() {
        StringBuilder sb = new StringBuilder();
        for (PlanPart p : parts) sb.append(p.part.id).append('@').append(p.origin.asLong()).append('r').append(p.rot).append(';');
        return sb.toString();
    }
}
