package dev.projectredacted.site.gen;

import dev.projectredacted.registry.ModBlocks;
import dev.projectredacted.room.Room;
import dev.projectredacted.room.RoomPlanner;
import dev.projectredacted.room.RoomType;
import dev.projectredacted.room.RoomTypes;
import dev.projectredacted.site.Site;
import dev.projectredacted.site.SiteType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;

/**
 * Connects the assembler to the world and to a site (design §5 and §6): surveys the terrain, builds the
 * assembler's input from the site's parts, rooms and doorways, and commits a finished plan to the site so
 * {@link dev.projectredacted.room.Construction} builds it.
 */
public final class SiteGen {
    /** Blocks kept clear between the claim edge and any part. */
    public static final int MARGIN = 2;
    /** Blocks per tick when Level 0 builds itself. */
    public static final int FAST_BLOCKS_PER_TICK = 2000;

    /** The assembler's input and where each doorway in it came from. */
    public record Survey(Assembler.Input input, List<Doorway> sources) {}

    // ---------------------------------------------------------------- survey

    /** The typical ground height around the site: the median of the heightmap sampled every 2 blocks over 40×40. */
    public static int surfaceFloor(ServerLevel level, BlockPos center) {
        List<Integer> heights = new ArrayList<>();
        for (int dx = -20; dx < 20; dx += 2)
            for (int dz = -20; dz < 20; dz += 2)
                heights.add(height(level, center.getX() + dx, center.getZ() + dz));
        heights.sort(Integer::compare);
        return heights.get(heights.size() / 2);
    }

    /** The top solid block at x, z, loading the chunk first (unloaded chunks report the bottom of the world). */
    public static int height(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4);
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
    }

    /** Floor height of the Director's Office storey for a new site of this type. */
    public static int officeFloor(ServerLevel level, Site site) {
        int surface = surfaceFloor(level, site.center());
        return site.type == SiteType.UNDERGROUND ? surface - SitePart.STOREY * SiteTypeRules.surfaceStorey(site.type) : surface;
    }

    /** Sites from before parts existed get their storey grid from the Director's Office. */
    public static int baseFloor(Site site) {
        if (site.baseFloor == null) {
            for (Room r : site.rooms) {
                if (r.type.equals(RoomTypes.DIRECTORS_OFFICE)) {
                    site.baseFloor = r.y1;
                    break;
                }
            }
            if (site.baseFloor == null) site.baseFloor = site.center().getY() - 1;
        }
        return site.baseFloor;
    }

    public static Survey survey(ServerLevel level, Site site, long seed) {
        Assembler.Input in = new Assembler.Input();
        in.kit = PartKit.active();
        in.type = site.type;
        in.seed = seed;
        in.basement = site.type == SiteType.SURFACE_FLAT && site.containmentBelow;
        int minX = site.minChunkX() * 16 + MARGIN, minZ = site.minChunkZ() * 16 + MARGIN;
        int maxX = (site.minChunkX() + site.claimChunks()) * 16 - 1 - MARGIN, maxZ = (site.minChunkZ() + site.claimChunks()) * 16 - 1 - MARGIN;
        in.bounds = new Box(minX, level.getMinY() + 5, minZ, maxX, level.getMaxY() - 2, maxZ);
        in.baseFloor = site.generated || site.baseFloor != null ? baseFloor(site) : officeFloor(level, site);
        for (PlacedPart p : site.parts) {
            if (p.removed) continue;
            Box b = p.box();
            if (b != null) in.occupied.add(b);
            SitePart part = p.part();
            if (part == null) continue;
            // Keep the ground in front of the Entrance clear so it stays a way in.
            for (int i = 0; i < part.connectors.size(); i++) {
                SitePart.Connector c = part.worldConnector(i, p.origin, p.rot);
                if (!c.type().equals(SitePart.SURFACE_EXIT)) continue;
                in.occupied.add(SitePart.exitClearance(c));
            }
        }
        List<Doorway> sources = new ArrayList<>();
        for (Room r : site.rooms) {
            in.occupied.add(new Box(r.x1, r.y1, r.z1, r.x2, r.y2, r.z2));
            if (r.part < 0) sources.addAll(legacyDoorways(r));
        }
        List<Doorway> all = new ArrayList<>(site.doorways);
        all.addAll(sources);
        for (Doorway d : all) in.doorways.add(d.connector());
        in.overwrites = box -> overwrites(level, site, box);
        in.exitOk = (pos, facing) -> exitOk(level, pos, facing);
        return new Survey(in, all);
    }

    /** A hand-designated room can grow from the middle of each wall. */
    private static List<Doorway> legacyDoorways(Room r) {
        List<Doorway> out = new ArrayList<>();
        int cx = (r.x1 + r.x2) / 2, cz = (r.z1 + r.z2) / 2, y = r.y1 + 1;
        out.add(legacy(new BlockPos(cx, y, r.z1), Direction.NORTH, r));
        out.add(legacy(new BlockPos(r.x2, y, cz), Direction.EAST, r));
        out.add(legacy(new BlockPos(cx, y, r.z2), Direction.SOUTH, r));
        out.add(legacy(new BlockPos(r.x1, y, cz), Direction.WEST, r));
        return out;
    }

    private static Doorway legacy(BlockPos pos, Direction facing, Room r) {
        Doorway d = new Doorway();
        d.pos = pos;
        d.facing = facing;
        d.type = SitePart.DOOR;
        d.room = r.number;
        return d;
    }

    /** An entrance needs open ground in front, no more than a step up and not a cliff down. */
    private static boolean exitOk(ServerLevel level, BlockPos pos, Direction facing) {
        BlockPos front = pos.relative(facing, 2);
        int ground = height(level, front.getX(), front.getZ());
        return ground <= pos.getY() && ground >= pos.getY() - 5;
    }

    // ---------------------------------------------------------------- the world

    /** Things the world makes by itself; anything else in the way was built by a player (D5). */
    public static boolean natural(BlockState s) {
        if (s.isAir() || !s.getFluidState().isEmpty() && s.liquid()) return true;
        if (s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(BlockTags.LOGS)
                || s.is(BlockTags.LEAVES) || s.is(BlockTags.REPLACEABLE) || s.is(BlockTags.SNOW) || s.is(BlockTags.ICE)
                || s.is(BlockTags.TERRACOTTA) || s.is(BlockTags.FLOWERS) || s.is(BlockTags.SAPLINGS)
                || s.is(BlockTags.STONE_ORE_REPLACEABLES) || s.is(BlockTags.DEEPSLATE_ORE_REPLACEABLES)
                || s.is(BlockTags.MOSS_REPLACEABLE) || s.is(BlockTags.SCULK_REPLACEABLE_WORLD_GEN)) return true;
        if (s.is(Blocks.GRAVEL) || s.is(Blocks.CLAY) || s.is(Blocks.BEDROCK) || s.is(Blocks.OBSIDIAN) || s.is(Blocks.WATER) || s.is(Blocks.LAVA)
                || s.is(Blocks.SANDSTONE) || s.is(Blocks.RED_SANDSTONE) || s.is(Blocks.CACTUS) || s.is(Blocks.SUGAR_CANE) || s.is(Blocks.BAMBOO)
                || s.is(Blocks.PUMPKIN) || s.is(Blocks.MELON) || s.is(Blocks.POINTED_DRIPSTONE) || s.is(Blocks.DRIPSTONE_BLOCK)
                || s.is(Blocks.AMETHYST_BLOCK) || s.is(Blocks.BUDDING_AMETHYST) || s.is(Blocks.CALCITE) || s.is(Blocks.SMOOTH_BASALT)
                || s.is(Blocks.MUD) || s.is(Blocks.MOSS_CARPET) || s.is(Blocks.GLOW_LICHEN) || s.is(Blocks.VINE) || s.is(Blocks.COBWEB)
                || s.is(Blocks.MAGMA_BLOCK) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.POWDER_SNOW) || s.is(Blocks.BROWN_MUSHROOM_BLOCK)
                || s.is(Blocks.RED_MUSHROOM_BLOCK) || s.is(Blocks.MUSHROOM_STEM)) return true;
        return BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath().endsWith("_ore");
    }

    /** Plants and trees cleared from above surface parts. */
    public static boolean vegetation(BlockState s) {
        return s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES) || s.is(BlockTags.REPLACEABLE) || s.is(BlockTags.FLOWERS) || s.is(BlockTags.SAPLINGS)
                || s.is(Blocks.SNOW) || s.is(Blocks.VINE) || s.is(Blocks.SUGAR_CANE) || s.is(Blocks.CACTUS) || s.is(Blocks.BAMBOO);
    }

    /** Player-built blocks inside a box (the site's own core blocks don't count; they move into the office). */
    public static int overwrites(ServerLevel level, Site site, Box box) {
        int n = 0;
        for (BlockPos pos : BlockPos.betweenClosed(box.x1(), box.y1(), box.z1(), box.x2(), box.y2(), box.z2())) {
            if (!level.isLoaded(pos)) continue;
            BlockState s = level.getBlockState(pos);
            if (natural(s)) continue;
            if (s.is(ModBlocks.MAINFRAME) || s.is(ModBlocks.SITE_DIRECTOR_COMPUTER) || s.is(ModBlocks.CASSIE)) continue;
            n++;
        }
        return n;
    }

    // ---------------------------------------------------------------- planning

    /** Plans the Level 0 facility for a site that hasn't generated yet. */
    public static SitePlan planLevel0(Survey s, Site site) {
        if (site.anchor == null) site.anchor = site.mainframe;
        SitePlan plan = Assembler.level0(s.input(), site.anchor, s.input().baseFloor);
        if (!plan.ok()) {
            // Steep ground: let the Entrance face anything rather than fail.
            s.input().exitOk = (p, d) -> true;
            plan = Assembler.level0(s.input(), site.anchor, s.input().baseFloor);
        }
        return plan;
    }

    /**
     * The storey growth should use next (design §4): Surface-Tall fills a storey up to the crowded share of the
     * claim and then goes up; Underground does the same going down. Null for Surface-Flat.
     */
    public static Integer preferredStorey(Site site, Assembler.Input in) {
        if (site.type == SiteType.SURFACE_FLAT) return null;
        double claim = (double) (in.bounds.x2() - in.bounds.x1() + 1) * (in.bounds.z2() - in.bounds.z1() + 1);
        double crowded = SiteTypeRules.of(site.type).crowded;
        java.util.Map<Integer, Integer> cover = new java.util.HashMap<>();
        for (PlacedPart p : site.parts) {
            Box b = p.removed ? null : p.box();
            if (b == null) continue;
            SitePart part = p.part();
            for (int k = 0; k < (part == null ? 1 : part.storeys()); k++) cover.merge(p.storey + k, b.area(), Integer::sum);
        }
        int surface = SiteTypeRules.surfaceStorey(site.type);
        int step = site.type == SiteType.UNDERGROUND ? -1 : 1;
        int start = site.type == SiteType.UNDERGROUND ? surface - 1 : 0;
        for (int s = start, n = 0; n < 64; s += step, n++) {
            if (cover.getOrDefault(s, 0) / claim < crowded) return s;
        }
        return null;
    }

    /** One spot for a room in its wing and section (§4A.5). */
    public static SitePlan addRoom(Site site, Survey survey, String roomType, SitePart size) {
        List<SitePlan> spots = Skeleton.spots(site, survey, roomType, size, 1, false);
        if (!spots.isEmpty()) return spots.get(0);
        SitePlan failed = new SitePlan();
        RoomType t = RoomTypes.get(roomType);
        failed.error = "No free space in the claim for a " + (t == null ? roomType : t.name) + ".";
        return failed;
    }

    /** One spot for a room joined to the facility anywhere, on the preferred storey if possible. */
    public static SitePlan addRoom(Site site, Assembler.Input in, String roomType, SitePart size) {
        Integer storey = preferredStorey(site, in);
        if (storey != null) {
            SitePlan plan = Assembler.addRoom(in, new Assembler.Goal(roomType, false, storey, size));
            if (plan.ok()) return plan;
        }
        return Assembler.addRoom(in, new Assembler.Goal(roomType, false, null, size));
    }

    // ---------------------------------------------------------------- committing (§6)

    /**
     * Adds a plan's parts to the site. Rooms are registered at once and built by construction; {@code fast}
     * builds at {@link #FAST_BLOCKS_PER_TICK} with the chunks kept loaded (Level 0).
     */
    public static void commit(ServerLevel level, Site site, SitePlan plan, Survey survey, boolean fast) {
        if (site.baseFloor == null) site.baseFloor = survey.input().baseFloor;
        site.genSeed = plan.seed;
        int first = site.parts.size();
        if (plan.wing != null) {
            Wings.WingState ws = Wings.state(site, plan.wing);
            if (plan.newWing) ws.founded = true;
            if (plan.newSection && ws.section(plan.section) == null) {
                String group = null;
                for (SitePlan.PlanPart pp : plan.parts) for (String t : pp.part.roomTypes()) if (group == null && Wings.ROLE_ROOM.equals(pp.role)) group = Wings.group(t);
                ws.sections.add(new Wings.Section(plan.section, group == null ? "Custom" : group));
            }
        }
        for (int i = 0; i < plan.parts.size(); i++) {
            SitePlan.PlanPart pp = plan.parts.get(i);
            PlacedPart placed = new PlacedPart();
            placed.kit = pp.part.kit;
            placed.id = pp.part.id;
            placed.origin = pp.origin;
            placed.rot = pp.rot;
            placed.storey = pp.storey;
            placed.used = pp.used;
            placed.fast = fast;
            placed.seed = plan.seed + i * 31L;
            placed.role = pp.role;
            placed.wing = pp.wing;
            placed.section = pp.section;
            placed.entry = pp.entry;
            site.parts.add(placed);
            int index = site.parts.size() - 1;
            addRooms(site, pp, placed, index);
            for (int c = 0; c < pp.part.connectors.size(); c++) {
                if ((pp.used & (1 << c)) != 0) continue;
                SitePart.Connector conn = pp.connector(c);
                if (conn.type().equals(SitePart.SURFACE_EXIT)) continue;
                Doorway d = Doorway.of(conn, index);
                d.room = roomAt(site, placed, conn.pos());
                site.doorways.add(d);
            }
        }
        // Doorways of the existing facility the plan grew from.
        for (int idx : plan.openedDoorways) {
            if (idx < 0 || idx >= survey.sources().size()) continue;
            Doorway d = survey.sources().get(idx);
            site.doorways.removeIf(o -> o.same(d));
            if (d.part >= 0 && d.part < site.parts.size()) {
                PlacedPart owner = site.parts.get(d.part);
                SitePart part = owner.part();
                if (part != null) {
                    for (int c = 0; c < part.connectors.size(); c++) {
                        if (part.worldConnector(c, owner.origin, owner.rot).pos().equals(d.pos)) owner.used |= 1 << c;
                    }
                }
            }
            if (d.room >= 0 && d.type.equals(SitePart.DOOR)) {
                Room r = site.room(d.room);
                if (r != null) {
                    int bits = r.doors >= 0 ? r.doors : (1 << (r.rotation & 3)) | r.extraDoors;
                    r.doors = bits | 1 << SitePart.side(d.facing);
                }
            }
            if (first < site.parts.size()) site.parts.get(first).reopen.add(d);
        }
        if (fast) {
            Box b = plan.bounds();
            if (b != null) {
                for (int cx = (b.x1() - 8) >> 4; cx <= (b.x2() + 8) >> 4; cx++)
                    for (int cz = (b.z1() - 8) >> 4; cz <= (b.z2() + 8) >> 4; cz++) {
                        long key = (long) cx << 32 | (cz & 0xFFFFFFFFL);
                        if (site.forcedChunks.contains(key)) continue;
                        level.setChunkForced(cx, cz, true);
                        site.forcedChunks.add(key);
                    }
            }
        }
    }

    /** The rooms a part holds where it's placed (numbers not assigned yet). */
    public static List<Room> roomsOf(SitePart part, BlockPos origin, int rot, int used, long seed) {
        List<Room> made = new ArrayList<>();
        if (part.procedural()) {
            if ("room".equals(part.shape.style())) made.add(ProceduralParts.roomFor(part, origin, rot, used, seed));
        } else {
            for (SitePart.PartRoom pr : part.rooms) {
                BlockPos a = origin.offset(SitePart.rotate(new BlockPos(pr.x1(), pr.y1(), pr.z1()), rot));
                BlockPos b = origin.offset(SitePart.rotate(new BlockPos(pr.x2(), pr.y2(), pr.z2()), rot));
                int side = SitePart.side(SitePart.rotate(SitePart.sideDir(pr.doorSide()), rot));
                Room r = new Room(0, pr.type(), a, b, side, seed);
                r.freeform = true; // hand-built: registered, never rebuilt by the room builder
                made.add(r);
            }
        }
        return made;
    }

    private static void addRooms(Site site, SitePlan.PlanPart pp, PlacedPart placed, int index) {
        List<Room> made = roomsOf(pp.part, pp.origin, pp.rot, pp.used, placed.seed);
        for (Room r : made) {
            RoomType type = RoomTypes.get(r.type);
            if (type == null) continue;
            r.number = site.nextRoomNumber++;
            r.part = index;
            r.wing = pp.wing != null && Wings.ROLE_ROOM.equals(pp.role) ? pp.wing : Wings.wingFor(site, r.type);
            r.section = pp.section;
            Wings.assign(site, r);
            r.built = false;
            RoomPlanner.name(site, r, type);
            site.rooms.add(r);
            placed.rooms.add(r.number);
        }
    }

    private static int roomAt(Site site, PlacedPart placed, BlockPos pos) {
        for (int n : placed.rooms) {
            Room r = site.room(n);
            if (r != null && r.contains(pos)) return n;
        }
        return -1;
    }

    /** Releases chunks kept loaded for a fast build once every fast part is done. */
    public static void releaseChunks(ServerLevel level, Site site) {
        for (long key : site.forcedChunks) level.setChunkForced((int) (key >> 32), (int) key, false);
        site.forcedChunks.clear();
    }

    /** A part's rooms, as Room records. */
    public static List<Room> rooms(Site site, PlacedPart p) {
        List<Room> out = new ArrayList<>();
        for (int n : p.rooms) {
            Room r = site.room(n);
            if (r != null) out.add(r);
        }
        return out;
    }

    private SiteGen() {}
}
