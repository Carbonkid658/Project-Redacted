package dev.projectredacted.site.gen;

import net.minecraft.core.BlockPos;

/** An inclusive block box. */
public record Box(int x1, int y1, int z1, int x2, int y2, int z2) {
    public static Box of(BlockPos a, BlockPos b) {
        return new Box(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
    }

    /** Shares at least one block. Boxes that only touch side by side don't intersect. */
    public boolean intersects(Box o) {
        return x1 <= o.x2 && x2 >= o.x1 && y1 <= o.y2 && y2 >= o.y1 && z1 <= o.z2 && z2 >= o.z1;
    }

    public boolean contains(BlockPos p) {
        return p.getX() >= x1 && p.getX() <= x2 && p.getY() >= y1 && p.getY() <= y2 && p.getZ() >= z1 && p.getZ() <= z2;
    }

    public boolean containsXZ(Box o) {
        return o.x1 >= x1 && o.x2 <= x2 && o.z1 >= z1 && o.z2 <= z2;
    }

    public Box grow(int n) {
        return new Box(x1 - n, y1 - n, z1 - n, x2 + n, y2 + n, z2 + n);
    }

    public int width() {
        return x2 - x1 + 1;
    }

    public int height() {
        return y2 - y1 + 1;
    }

    public int length() {
        return z2 - z1 + 1;
    }

    public BlockPos min() {
        return new BlockPos(x1, y1, z1);
    }

    public BlockPos max() {
        return new BlockPos(x2, y2, z2);
    }

    public BlockPos center() {
        return new BlockPos(Math.floorDiv(x1 + x2, 2), y1 + 1, Math.floorDiv(z1 + z2, 2));
    }

    public int area() {
        return width() * length();
    }
}
