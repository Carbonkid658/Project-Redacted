package dev.projectredacted.api;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;

/** One thing an SCP needs from its cell. Unmet requirements can cause an internal breach. */
public interface ContainmentRequirement {
    String describe();

    boolean met(ContainmentContext ctx);

    static ContainmentRequirement powered() {
        return of("Cell must be powered", ctx -> ctx.cell().poweredNow);
    }

    static ContainmentRequirement unpowered() {
        return of("Cell must NOT be powered", ctx -> !ctx.cell().poweredNow);
    }

    /** "light", "medium" or "heavy" (or stronger). */
    static ContainmentRequirement cellAtLeast(String cellType) {
        int need = rank(cellType);
        return of("Needs a " + cellType + " cell or stronger", ctx -> rank(ctx.cell().type) >= need);
    }

    static ContainmentRequirement itemInCell(Item item) {
        return of("Cell must contain " + item, ctx -> {
            var cell = ctx.cell();
            for (BlockPos p : BlockPos.betweenClosed(cell.x1, cell.y1, cell.z1, cell.x2, cell.y2, cell.z2)) {
                if (ctx.level().getBlockEntity(p) instanceof Container c) {
                    for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).is(item)) return true;
                }
            }
            return false;
        });
    }

    /** No other SCP may share the cell. */
    static ContainmentRequirement alone() {
        return of("Must be contained alone", ctx -> ctx.cell().scpCount <= 1);
    }

    /** At least one researcher must be assigned to this SCP. */
    static ContainmentRequirement observed() {
        return of("Needs a researcher assigned", ctx -> ctx.site().staff.values().stream()
                .anyMatch(s -> s.assignment.kind == dev.projectredacted.staff.Assignment.Kind.SCP && s.assignment.room == ctx.cell().number));
    }

    static ContainmentRequirement of(String description, java.util.function.Predicate<ContainmentContext> test) {
        return new ContainmentRequirement() {
            @Override
            public String describe() {
                return description;
            }

            @Override
            public boolean met(ContainmentContext ctx) {
                return test.test(ctx);
            }
        };
    }

    private static int rank(String cellType) {
        if (cellType.contains("heavy")) return 3;
        if (cellType.contains("medium")) return 2;
        return 1;
    }

    /** Helper so requirement lambdas can check if an entity is still in its cell. */
    static boolean inCell(ContainmentContext ctx, Entity e) {
        return ctx.cell().contains(e.blockPosition());
    }
}
