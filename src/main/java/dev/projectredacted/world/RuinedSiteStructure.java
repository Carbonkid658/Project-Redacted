package dev.projectredacted.world;

import com.mojang.serialization.MapCodec;
import dev.projectredacted.room.Room;
import dev.projectredacted.room.RoomTypes;
import dev.projectredacted.site.SiteType;
import dev.projectredacted.site.gen.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A ruined Foundation site (design §9): a Level 0 plan from the same parts kit as player sites, plus a Light
 * Cell, built as a worldgen structure (so /locate finds it and it isn't cut off at chunk borders), then decayed.
 * Players can repair its Mainframe and move in.
 */
public class RuinedSiteStructure extends Structure {
    public static final MapCodec<RuinedSiteStructure> CODEC = simpleCodec(RuinedSiteStructure::new);
    public static StructureType<RuinedSiteStructure> TYPE;

    public RuinedSiteStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    public StructureType<?> type() {
        return TYPE;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext ctx) {
        int x = ctx.chunkPos().getMiddleBlockX(), z = ctx.chunkPos().getMiddleBlockZ();
        int floor = ctx.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, ctx.heightAccessor(), ctx.randomState());
        if (floor < ctx.chunkGenerator().getSeaLevel()) return Optional.empty();
        BlockPos center = new BlockPos(x, floor, z);
        long seed = ctx.seed() ^ ctx.chunkPos().toLong() * 0x9E3779B97F4A7C15L;
        SitePlan plan = plan(center, seed);
        if (!plan.ok()) return Optional.empty();
        return Optional.of(new GenerationStub(center, builder -> addPieces(builder, plan, seed)));
    }

    /** Level 0 (office, barracks, entrance) and a Light Cell, on flat ground around the center. */
    public static SitePlan plan(BlockPos center, long seed) {
        Assembler.Input in = new Assembler.Input();
        in.kit = PartKit.active();
        in.type = SiteType.SURFACE_FLAT;
        in.seed = seed;
        in.bounds = new Box(center.getX() - 40, center.getY() - 8, center.getZ() - 40, center.getX() + 40, center.getY() + 16, center.getZ() + 40);
        in.baseFloor = center.getY();
        in.budget = 6000;
        SitePlan plan = Assembler.level0(in, center, center.getY());
        if (!plan.ok()) return plan;
        // Grow a Light Cell from the plan's open doorways.
        Assembler.Input grow = new Assembler.Input();
        grow.kit = in.kit;
        grow.type = in.type;
        grow.seed = seed + 1;
        grow.bounds = in.bounds;
        grow.baseFloor = in.baseFloor;
        grow.budget = 4000;
        for (SitePlan.PlanPart pp : plan.parts) {
            grow.occupied.add(pp.box);
            for (int i = 0; i < pp.part.connectors.size(); i++) {
                if ((pp.used & (1 << i)) != 0) continue;
                SitePart.Connector c = pp.connector(i);
                if (c.type().equals(SitePart.SURFACE_EXIT)) grow.occupied.add(SitePart.exitClearance(c));
                else grow.doorways.add(c);
            }
        }
        List<SitePart> cells = in.kit.sizes("projectredacted:light_cell");
        if (!cells.isEmpty()) {
            SitePlan cell = Assembler.addRoom(grow, new Assembler.Goal("projectredacted:light_cell", false, 0, cells.get(0)));
            if (cell.ok()) {
                // Mark the doorways the cell grew from as used in the Level 0 parts.
                for (int idx : cell.openedDoorways) {
                    SitePart.Connector d = grow.doorways.get(idx);
                    for (SitePlan.PlanPart pp : plan.parts)
                        for (int i = 0; i < pp.part.connectors.size(); i++) if (pp.connector(i).pos().equals(d.pos())) pp.used |= 1 << i;
                }
                plan.parts.addAll(cell.parts);
            }
        }
        return plan;
    }

    private static void addPieces(StructurePiecesBuilder builder, SitePlan plan, long seed) {
        List<Room> rooms = new ArrayList<>();
        for (int i = 0; i < plan.parts.size(); i++) {
            SitePlan.PlanPart pp = plan.parts.get(i);
            rooms.addAll(SiteGen.roomsOf(pp.part, pp.origin, pp.rot, pp.used, seed + i * 31L));
        }
        for (int i = 0; i < plan.parts.size(); i++) {
            SitePlan.PlanPart pp = plan.parts.get(i);
            boolean office = pp.part.roomTypes().contains(RoomTypes.DIRECTORS_OFFICE);
            builder.addPiece(new RuinPiece(pp, seed + i * 31L, office ? rooms : List.of()));
        }
    }
}
