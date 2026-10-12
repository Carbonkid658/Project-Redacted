package dev.projectredacted.room;

import dev.projectredacted.ProjectRedacted;
import dev.projectredacted.block.FacingBlock;
import dev.projectredacted.block.KeycardReaderBlock;
import dev.projectredacted.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;

import java.util.*;

/**
 * Turns a room box into a list of block placements. Layouts are procedural: the seed picks the palette
 * (retro office, modern clinical or a mix) and furniture variations, so "reroll" gives a different look.
 */
public final class RoomBuilder {
    /** Flags 2|16: update clients but skip shape updates so doors and beds keep both halves while placing. */
    public static final int MULTIPART_FLAGS = 18;

    public record Placement(BlockPos pos, BlockState state, boolean multipart, boolean structural) {}

    public static class Plan {
        public final List<Placement> placements = new ArrayList<>();
        public BlockPos mainframeSpot;
        public BlockPos terminalSpot;
        public final List<BlockPos> readers = new ArrayList<>();
    }

    private record Palette(BlockState wall, BlockState floor, BlockState trim, BlockState ceiling, BlockState light) {}

    private static final Palette[] PALETTES = {
            // Modern clinical
            new Palette(ModBlocks.FACILITY_WALL.defaultBlockState(), ModBlocks.FACILITY_FLOOR.defaultBlockState(),
                    Blocks.CONCRETE.lightGray().defaultBlockState(), ModBlocks.FACILITY_WALL.defaultBlockState(), ModBlocks.CEILING_LIGHT.defaultBlockState()),
            // Retro office
            new Palette(Blocks.DYED_TERRACOTTA.white().defaultBlockState(), Blocks.WOOL.gray().defaultBlockState(),
                    Blocks.STRIPPED_SPRUCE_WOOD.defaultBlockState(), Blocks.SMOOTH_STONE.defaultBlockState(), ModBlocks.CEILING_LIGHT.defaultBlockState()),
            // Mixed
            new Palette(Blocks.SMOOTH_QUARTZ.defaultBlockState(), Blocks.POLISHED_ANDESITE.defaultBlockState(),
                    Blocks.CONCRETE.gray().defaultBlockState(), ModBlocks.FACILITY_WALL.defaultBlockState(), ModBlocks.CEILING_LIGHT.defaultBlockState()),
    };

    /** Rooms with less than this much floor inside the walls (on either axis) get a bare shell, no furniture. */
    private static final int MIN_FURNISHED_INTERIOR = 3;

    public static Plan plan(Room room, RoomType type) {
        Ctx c = new Ctx(room, type);
        c.shell();
        if (room.x2 - room.x1 - 1 < MIN_FURNISHED_INTERIOR || room.z2 - room.z1 - 1 < MIN_FURNISHED_INTERIOR) return c.plan;
        try {
            furnish(c, type);
        } catch (RuntimeException e) {
            // A layout that doesn't fit must never take the server down; fall back to the empty shell.
            ProjectRedacted.LOG.warn("Couldn't furnish {} room at {}, {}, {}", type.id, room.x1, room.y1, room.z1, e);
            Ctx bare = new Ctx(room, type);
            bare.shell();
            return bare.plan;
        }
        return c.plan;
    }

    private static void furnish(Ctx c, RoomType type) {
        switch (type.style) {
            case "entrance" -> c.entrance();
            case "directors_office" -> c.directorsOffice();
            case "office" -> c.office(c.r.type.endsWith("researcher_office"));
            case "water_pump" -> c.waterPump();
            case "generator" -> c.generator();
            case "circuit_breaker" -> c.circuitBreaker();
            case "storage" -> c.storage();
            case "medbay" -> c.medbay();
            case "checkup" -> c.checkup();
            case "waiting" -> c.waiting();
            case "cafeteria" -> c.cafeteria();
            case "kitchen" -> c.kitchen();
            case "lounge" -> c.lounge();
            case "bathroom" -> c.bathroom();
            case "lab" -> c.lab();
            case "observation" -> c.observation();
            case "light_cell", "medium_cell", "heavy_cell" -> c.cell();
            case "hallway_checkpoint", "transition_checkpoint" -> c.checkpoint(type.style.startsWith("transition"));
            case "armory" -> c.armory();
            case "prison" -> c.prison();
            case "barracks" -> c.barracks();
            case "single_bedroom" -> c.singleBedroom();
            default -> {}
        }
    }

    /** Building context: the box, the palette and helpers for placing furniture away from doors. */
    private static final class Ctx {
        final Room r;
        final RoomType t;
        final Plan plan = new Plan();
        final Random rand;
        final Palette pal;
        final int fy; // furniture level (one above the floor)
        final Map<Long, BlockState> placed = new HashMap<>();
        final Set<Long> keepClear = new HashSet<>();

        Ctx(Room r, RoomType t) {
            this.r = r;
            this.t = t;
            this.rand = new Random(r.seed);
            this.pal = PALETTES[Math.floorMod(r.seed, PALETTES.length)];
            this.fy = r.y1 + 1;
        }

        // ---------------------------------------------------------------- shell

        BlockState wallBlock() {
            return switch (t.style) {
                case "light_cell" -> Blocks.SMOOTH_STONE.defaultBlockState();
                case "medium_cell" -> Blocks.TUFF_BRICKS.defaultBlockState();
                case "heavy_cell" -> ModBlocks.CONTAINMENT_CONCRETE.defaultBlockState();
                case "prison" -> Blocks.STONE_BRICKS.defaultBlockState();
                case "generator", "water_pump", "circuit_breaker" -> Blocks.POLISHED_DEEPSLATE.defaultBlockState();
                default -> pal.wall;
            };
        }

        BlockState floorBlock() {
            return switch (t.style) {
                case "light_cell" -> Blocks.SMOOTH_STONE.defaultBlockState();
                case "medium_cell" -> Blocks.POLISHED_TUFF.defaultBlockState();
                case "heavy_cell" -> ModBlocks.CONTAINMENT_CONCRETE.defaultBlockState();
                case "generator", "water_pump", "circuit_breaker" -> Blocks.IRON_BLOCK.defaultBlockState();
                case "medbay", "checkup", "lab", "bathroom", "kitchen" -> Blocks.CONCRETE.white().defaultBlockState();
                default -> pal.floor;
            };
        }

        void shell() {
            BlockState wall = wallBlock(), floor = floorBlock();
            boolean cell = t.isContainment();
            BlockState trim = cell || t.style.equals("prison") ? wall : pal.trim;
            BlockState ceiling = cell ? wall : pal.ceiling;
            // Clear the inside first.
            for (int y = r.y1 + 1; y < r.y2; y++)
                for (int x = r.x1 + 1; x < r.x2; x++)
                    for (int z = r.z1 + 1; z < r.z2; z++) add(x, y, z, Blocks.AIR.defaultBlockState(), false, false);
            for (int x = r.x1; x <= r.x2; x++)
                for (int z = r.z1; z <= r.z2; z++) add(x, r.y1, z, floor, false, true);
            for (int y = r.y1 + 1; y < r.y2; y++) {
                for (int x = r.x1; x <= r.x2; x++) {
                    add(x, y, r.z1, y == r.y1 + 1 ? trim : wall, false, true);
                    add(x, y, r.z2, y == r.y1 + 1 ? trim : wall, false, true);
                }
                for (int z = r.z1 + 1; z < r.z2; z++) {
                    add(r.x1, y, z, y == r.y1 + 1 ? trim : wall, false, true);
                    add(r.x2, y, z, y == r.y1 + 1 ? trim : wall, false, true);
                }
            }
            for (int x = r.x1; x <= r.x2; x++)
                for (int z = r.z1; z <= r.z2; z++) {
                    boolean light = (x - r.x1) % 4 == 2 && (z - r.z1) % 4 == 2 && x < r.x2 && z < r.z2;
                    add(x, r.y2, z, light ? pal.light : ceiling, false, true);
                }
            doors();
        }

        void doors() {
            boolean cell = t.isContainment();
            boolean secured = cell || t.isCheckpoint() || t.style.equals("directors_office") || t.style.equals("prison") || t.style.equals("armory");
            Block door = switch (t.style) {
                case "medium_cell", "heavy_cell" -> ModBlocks.REINFORCED_DOOR;
                case "entrance" -> Blocks.DARK_OAK_DOOR;
                default -> secured ? Blocks.IRON_DOOR : Blocks.OAK_DOOR;
            };
            int mask;
            if (r.doors >= 0) {
                mask = r.doors & 15;
            } else {
                mask = 1 << (r.rotation & 3);
                mask |= remap(r.extraDoors);
                if (t.isCheckpoint()) mask |= 1 << ((r.rotation + 2) & 3);
            }
            for (int side = 0; side < 4; side++) {
                if ((mask & (1 << side)) == 0) continue;
                Direction d = sideDir(side);
                boolean wide = t.style.equals("entrance") && side == (r.rotation & 3);
                doorOn(d, door, wide, secured && t.clearance > 0);
            }
        }

        /** Room.extraDoors uses 1 north, 2 east, 4 south, 8 west, which already matches side indexes. */
        int remap(int bits) {
            return bits & 15;
        }

        Direction sideDir(int side) {
            return switch (side & 3) {
                case 0 -> Direction.NORTH;
                case 1 -> Direction.EAST;
                case 2 -> Direction.SOUTH;
                default -> Direction.WEST;
            };
        }

        void doorOn(Direction side, Block door, boolean wide, boolean reader) {
            int cx = (r.x1 + r.x2) / 2, cz = (r.z1 + r.z2) / 2;
            BlockPos base = switch (side) {
                case NORTH -> new BlockPos(cx, fy, r.z1);
                case SOUTH -> new BlockPos(cx, fy, r.z2);
                case EAST -> new BlockPos(r.x2, fy, cz);
                default -> new BlockPos(r.x1, fy, cz);
            };
            Direction along = side.getAxis() == Direction.Axis.Z ? Direction.EAST : Direction.SOUTH;
            placeDoor(base, side.getOpposite(), door, DoorHingeSide.LEFT);
            if (wide) placeDoor(base.relative(along), side.getOpposite(), door, DoorHingeSide.RIGHT);
            // Keep the inside of the doorway clear of furniture.
            BlockPos inside = base.relative(side.getOpposite());
            for (int i = -1; i <= (wide ? 2 : 1); i++) {
                BlockPos p = inside.relative(along, i);
                keepClear.add(key(p.getX(), p.getZ()));
                BlockPos p2 = p.relative(side.getOpposite());
                keepClear.add(key(p2.getX(), p2.getZ()));
            }
            if (reader) {
                BlockPos readerPos = base.relative(along, wide ? 2 : 1).above();
                BlockState rs = ModBlocks.KEYCARD_READER.defaultBlockState()
                        .setValue(FacingBlock.FACING, side)
                        .setValue(KeycardReaderBlock.LEVEL, Math.max(1, Math.min(5, t.clearance)));
                add(readerPos.getX(), readerPos.getY(), readerPos.getZ(), rs, false, true);
                plan.readers.add(readerPos);
            }
        }

        void placeDoor(BlockPos lower, Direction facing, Block door, DoorHingeSide hinge) {
            BlockState s = door.defaultBlockState().setValue(DoorBlock.FACING, facing).setValue(DoorBlock.HINGE, hinge)
                    .setValue(DoorBlock.OPEN, false);
            add(lower.getX(), lower.getY(), lower.getZ(), s.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), true, true);
            add(lower.getX(), lower.getY() + 1, lower.getZ(), s.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), true, true);
        }

        // ---------------------------------------------------------------- helpers

        long key(int x, int z) {
            return BlockPos.asLong(x, 0, z);
        }

        void add(int x, int y, int z, BlockState s, boolean multipart, boolean structural) {
            BlockPos p = new BlockPos(x, y, z);
            plan.placements.add(new Placement(p, s, multipart, structural));
            placed.put(p.asLong(), s);
        }

        boolean free(int x, int z) {
            if (x <= r.x1 || x >= r.x2 || z <= r.z1 || z >= r.z2) return false;
            if (keepClear.contains(key(x, z))) return false;
            BlockState s = placed.get(BlockPos.asLong(x, fy, z));
            return s == null || s.isAir();
        }

        boolean put(int x, int z, BlockState s) {
            return put(x, fy, z, s);
        }

        boolean put(int x, int y, int z, BlockState s) {
            if (!free(x, z) && y == fy) return false;
            add(x, y, z, s, false, true);
            return true;
        }

        /** Places a bed with its head against the given wall side. */
        boolean bed(int headX, int headZ, Direction towardHead, Block bed) {
            int fx = headX - towardHead.getStepX(), fz = headZ - towardHead.getStepZ();
            if (!free(headX, headZ) || !free(fx, fz)) return false;
            BlockState s = bed.defaultBlockState().setValue(BedBlock.FACING, towardHead);
            add(fx, fy, fz, s.setValue(BedBlock.PART, BedPart.FOOT), true, true);
            add(headX, fy, headZ, s.setValue(BedBlock.PART, BedPart.HEAD), true, true);
            return true;
        }

        BlockState stairs(Block block, Direction facing) {
            return block.defaultBlockState().setValue(StairBlock.FACING, facing);
        }

        BlockState topSlab(Block block) {
            return block.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
        }

        /** Positions along the inside of the wall opposite the main door, left to right. */
        List<int[]> backWall() {
            List<int[]> list = new ArrayList<>();
            switch (r.rotation & 3) {
                case 0 -> { for (int x = r.x1 + 1; x < r.x2; x++) list.add(new int[]{x, r.z2 - 1}); }
                case 2 -> { for (int x = r.x1 + 1; x < r.x2; x++) list.add(new int[]{x, r.z1 + 1}); }
                case 1 -> { for (int z = r.z1 + 1; z < r.z2; z++) list.add(new int[]{r.x1 + 1, z}); }
                default -> { for (int z = r.z1 + 1; z < r.z2; z++) list.add(new int[]{r.x2 - 1, z}); }
            }
            return list;
        }

        /** Direction from the door toward the back wall. */
        Direction inward() {
            return sideDir(r.rotation).getOpposite();
        }

        List<int[]> sideWalls() {
            List<int[]> list = new ArrayList<>();
            if ((r.rotation & 1) == 0) {
                for (int z = r.z1 + 1; z < r.z2; z++) {
                    list.add(new int[]{r.x1 + 1, z});
                    list.add(new int[]{r.x2 - 1, z});
                }
            } else {
                for (int x = r.x1 + 1; x < r.x2; x++) {
                    list.add(new int[]{x, r.z1 + 1});
                    list.add(new int[]{x, r.z2 - 1});
                }
            }
            return list;
        }

        void alongWalls(BlockState s, int every) {
            int i = 0;
            for (int[] p : sideWalls()) if (i++ % every == 0) put(p[0], p[1], s);
        }

        void corners(BlockState s) {
            put(r.x1 + 1, r.z1 + 1, s);
            put(r.x2 - 1, r.z1 + 1, s);
            put(r.x1 + 1, r.z2 - 1, s);
            put(r.x2 - 1, r.z2 - 1, s);
        }

        void carpet(Block carpet, int inset) {
            for (int x = r.x1 + 1 + inset; x < r.x2 - inset; x++)
                for (int z = r.z1 + 1 + inset; z < r.z2 - inset; z++) put(x, z, carpet.defaultBlockState());
        }

        /** Beds in rows along both side walls, heads to the wall. Returns how many fit. */
        int bedRows(Block bed, int max) {
            int n = 0;
            if ((r.rotation & 1) == 0) {
                for (int z = r.z1 + 1; z < r.z2 && n < max; z += 2) {
                    if (bed(r.x1 + 1, z, Direction.WEST, bed)) n++;
                    if (n < max && bed(r.x2 - 1, z, Direction.EAST, bed)) n++;
                }
            } else {
                for (int x = r.x1 + 1; x < r.x2 && n < max; x += 2) {
                    if (bed(x, r.z1 + 1, Direction.NORTH, bed)) n++;
                    if (n < max && bed(x, r.z2 - 1, Direction.SOUTH, bed)) n++;
                }
            }
            return n;
        }

        // ---------------------------------------------------------------- layouts

        void entrance() {
            corners(Blocks.POTTED_FERN.defaultBlockState());
            List<int[]> back = backWall();
            for (int i = 1; i < back.size() - 1; i++) {
                int[] p = back.get(i);
                int[] d = {p[0] - inward().getStepX(), p[1] - inward().getStepZ()};
                if (i >= back.size() / 2 - 1 && i <= back.size() / 2 + 1) put(d[0], d[1], topSlab(Blocks.SMOOTH_STONE_SLAB));
            }
        }

        void directorsOffice() {
            List<int[]> back = backWall();
            Direction in = inward();
            // Mainframe and terminal positions go in the back corners and the desk.
            int[] left = back.get(0);
            plan.mainframeSpot = new BlockPos(left[0], fy, left[1]);
            put(left[0], left[1], Blocks.AIR.defaultBlockState());
            for (int i = 2; i < back.size() - 1; i++) {
                int[] p = back.get(i);
                put(p[0], p[1], Blocks.BOOKSHELF.defaultBlockState());
            }
            int mid = back.size() / 2;
            int deskOff = 2;
            for (int i = mid - 1; i <= mid + 1; i++) {
                int[] p = back.get(i);
                int dx = p[0] - in.getStepX() * deskOff, dz = p[1] - in.getStepZ() * deskOff;
                if (i == mid) {
                    plan.terminalSpot = new BlockPos(dx, fy, dz);
                    put(dx, dz, Blocks.AIR.defaultBlockState());
                } else {
                    put(dx, dz, topSlab(Blocks.DARK_OAK_SLAB));
                }
            }
            int[] chair = back.get(mid);
            put(chair[0] - in.getStepX(), chair[1] - in.getStepZ(), stairs(Blocks.DARK_OAK_STAIRS, in.getOpposite()));
            int[] right = back.get(back.size() - 1);
            put(right[0], right[1], Blocks.CHEST.defaultBlockState());
            carpet(Blocks.CARPET.red(), 2);
            corners(Blocks.POTTED_FERN.defaultBlockState());
        }

        void office(boolean research) {
            Direction in = inward();
            int i = 0;
            for (int x = r.x1 + 2; x < r.x2 - 1; x += 3) {
                for (int z = r.z1 + 2; z < r.z2 - 1; z += 3) {
                    if (!free(x, z)) continue;
                    put(x, z, topSlab(Blocks.SPRUCE_SLAB));
                    int cx = x - in.getStepX(), cz = z - in.getStepZ();
                    put(cx, cz, stairs(Blocks.SPRUCE_STAIRS, in.getOpposite()));
                    if (!research && i++ % 2 == 0) put(x + in.getStepZ(), z + in.getStepX(), Blocks.STAINED_GLASS_PANE.lightGray().defaultBlockState());
                }
            }
            for (int[] p : backWall()) put(p[0], p[1], research ? Blocks.BOOKSHELF.defaultBlockState() : Blocks.BARREL.defaultBlockState());
            if (research) put(r.x1 + 1, r.z1 + 1, Blocks.LECTERN.defaultBlockState());
        }

        void waterPump() {
            int cx = (r.x1 + r.x2) / 2, cz = (r.z1 + r.z2) / 2;
            put(cx, cz, ModBlocks.WATER_PUMP.defaultBlockState());
            put(cx, fy + 1, cz, Blocks.LIGHTNING_ROD.asList().get(0).defaultBlockState());
            alongWalls(Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3), 2);
            corners(Blocks.COPPER_BLOCK.asList().get(4).defaultBlockState());
        }

        void generator() {
            int cx = (r.x1 + r.x2) / 2, cz = (r.z1 + r.z2) / 2;
            put(cx, cz, ModBlocks.GENERATOR_CORE.defaultBlockState());
            for (int[] p : backWall()) put(p[0], p[1], Blocks.BLAST_FURNACE.defaultBlockState()
                    .setValue(AbstractFurnaceBlock.FACING, inward().getOpposite()));
            corners(Blocks.REDSTONE_BLOCK.defaultBlockState());
        }

        void circuitBreaker() {
            List<int[]> back = backWall();
            int[] p = back.get(back.size() / 2);
            put(p[0], p[1], ModBlocks.CIRCUIT_BREAKER.defaultBlockState().setValue(FacingBlock.FACING, inward().getOpposite()));
            for (int[] q : back) if (q != p) put(q[0], q[1], Blocks.IRON_TRAPDOOR.defaultBlockState());
        }

        void storage() {
            int i = 0;
            for (int[] p : sideWalls()) put(p[0], p[1], (i++ % 3 == 0) ? Blocks.CHEST.defaultBlockState() : Blocks.BARREL.defaultBlockState());
            for (int[] p : backWall()) put(p[0], p[1], Blocks.BARREL.defaultBlockState());
        }

        void medbay() {
            bedRows(Blocks.BED.white(), 8);
            for (int[] p : backWall()) {
                put(p[0], p[1], rand.nextBoolean() ? Blocks.BREWING_STAND.defaultBlockState() : Blocks.CONCRETE.white().defaultBlockState());
            }
        }

        void checkup() {
            bedRows(Blocks.BED.white(), 1);
            List<int[]> back = backWall();
            int[] p = back.get(back.size() - 1);
            put(p[0], p[1], Blocks.BREWING_STAND.defaultBlockState());
            put(back.get(0)[0], back.get(0)[1], Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
        }

        void waiting() {
            Direction in = inward();
            for (int[] p : sideWalls()) put(p[0], p[1], stairs(Blocks.OAK_STAIRS, sideFacing(p)));
            corners(Blocks.POTTED_BAMBOO.defaultBlockState());
            List<int[]> back = backWall();
            int[] desk = back.get(back.size() / 2);
            put(desk[0] - in.getStepX(), desk[1] - in.getStepZ(), topSlab(Blocks.SMOOTH_STONE_SLAB));
        }

        /** Stairs placed along a side wall should face away from it. */
        Direction sideFacing(int[] p) {
            if ((r.rotation & 1) == 0) return p[0] == r.x1 + 1 ? Direction.WEST : Direction.EAST;
            return p[1] == r.z1 + 1 ? Direction.NORTH : Direction.SOUTH;
        }

        void cafeteria() {
            for (int x = r.x1 + 2; x < r.x2 - 1; x += 4) {
                for (int z = r.z1 + 2; z < r.z2 - 1; z += 4) {
                    if (!free(x, z)) continue;
                    put(x, z, Blocks.OAK_FENCE.defaultBlockState());
                    put(x, fy + 1, z, Blocks.OAK_PRESSURE_PLATE.defaultBlockState());
                    put(x + 1, z, stairs(Blocks.OAK_STAIRS, Direction.EAST));
                    put(x - 1, z, stairs(Blocks.OAK_STAIRS, Direction.WEST));
                }
            }
            for (int[] p : backWall()) put(p[0], p[1], rand.nextInt(3) == 0
                    ? Blocks.SMOKER.defaultBlockState().setValue(AbstractFurnaceBlock.FACING, inward().getOpposite())
                    : topSlab(Blocks.SMOOTH_STONE_SLAB));
        }

        void kitchen() {
            int i = 0;
            for (int[] p : backWall()) {
                BlockState s = switch (i++ % 3) {
                    case 0 -> Blocks.SMOKER.defaultBlockState().setValue(AbstractFurnaceBlock.FACING, inward().getOpposite());
                    case 1 -> Blocks.FURNACE.defaultBlockState().setValue(AbstractFurnaceBlock.FACING, inward().getOpposite());
                    default -> Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3);
                };
                put(p[0], p[1], s);
            }
            alongWalls(Blocks.BARREL.defaultBlockState(), 3);
            put(r.x1 + 1, r.z1 + 1, Blocks.CRAFTING_TABLE.defaultBlockState());
        }

        void lounge() {
            carpet(rand.nextBoolean() ? Blocks.CARPET.orange() : Blocks.CARPET.brown(), 1);
            List<int[]> back = backWall();
            put(back.get(0)[0], back.get(0)[1], Blocks.JUKEBOX.defaultBlockState());
            put(back.get(back.size() - 1)[0], back.get(back.size() - 1)[1], Blocks.NOTE_BLOCK.defaultBlockState());
            for (int i = 1; i < back.size() - 1; i++) put(back.get(i)[0], back.get(i)[1], stairs(Blocks.SPRUCE_STAIRS, inward().getOpposite()));
            alongWalls(Blocks.BOOKSHELF.defaultBlockState(), 3);
        }

        void bathroom() {
            int i = 0;
            for (int[] p : backWall()) put(p[0], p[1], i++ % 2 == 0 ? Blocks.HOPPER.defaultBlockState() : Blocks.SPRUCE_TRAPDOOR.defaultBlockState());
            alongWalls(Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3), 3);
        }

        void lab() {
            int i = 0;
            for (int[] p : backWall()) {
                BlockState s = switch (i++ % 4) {
                    case 0 -> Blocks.BREWING_STAND.defaultBlockState();
                    case 1 -> Blocks.CONCRETE.white().defaultBlockState();
                    case 2 -> Blocks.CAULDRON.defaultBlockState();
                    default -> Blocks.CRAFTING_TABLE.defaultBlockState();
                };
                put(p[0], p[1], s);
            }
            // The first barrel is where finished Research Documents go.
            put(r.x1 + 1, r.z1 + 1, Blocks.BARREL.defaultBlockState());
            alongWalls(Blocks.CONCRETE.white().defaultBlockState(), 4);
        }

        void observation() {
            // A window in the back wall looks into the neighboring cell.
            Direction in = inward();
            for (int[] p : backWall()) {
                int wx = p[0] + in.getStepX(), wz = p[1] + in.getStepZ();
                for (int y = r.y1 + 2; y < r.y2 - 1; y++) add(wx, y, wz, ModBlocks.REINFORCED_GLASS.defaultBlockState(), false, true);
                put(p[0], p[1], topSlab(Blocks.SMOOTH_STONE_SLAB));
            }
            List<int[]> back = backWall();
            int[] mid = back.get(back.size() / 2);
            put(mid[0] - in.getStepX(), mid[1] - in.getStepZ(), stairs(Blocks.SPRUCE_STAIRS, in.getOpposite()));
            put(r.x1 + 1, r.z1 + 1, Blocks.LECTERN.defaultBlockState());
        }

        void cell() {
            // A containment locker for item SCPs.
            List<int[]> back = backWall();
            int[] p = back.get(back.size() / 2);
            put(p[0], p[1], Blocks.BARREL.defaultBlockState());
            if (t.style.equals("medium_cell")) {
                Direction side = sideDir(r.rotation);
                int cx = (r.x1 + r.x2) / 2, cz = (r.z1 + r.z2) / 2;
                Direction along = side.getAxis() == Direction.Axis.Z ? Direction.EAST : Direction.SOUTH;
                BlockPos base = switch (side) {
                    case NORTH -> new BlockPos(cx, fy, r.z1);
                    case SOUTH -> new BlockPos(cx, fy, r.z2);
                    case EAST -> new BlockPos(r.x2, fy, cz);
                    default -> new BlockPos(r.x1, fy, cz);
                };
                BlockPos w = base.relative(along, -2);
                for (int y = 0; y < 2; y++) add(w.getX(), fy + y, w.getZ(), ModBlocks.REINFORCED_GLASS.defaultBlockState(), false, true);
            }
        }

        void checkpoint(boolean transition) {
            if (!transition) return;
            for (int[] p : sideWalls()) {
                if ((p[0] + p[1]) % 3 == 0) put(p[0], p[1], topSlab(Blocks.SMOOTH_STONE_SLAB));
            }
        }

        void armory() {
            List<int[]> back = backWall();
            for (int i = 0; i < back.size(); i++) {
                BlockState s = switch (i % 4) {
                    case 0 -> Blocks.SMITHING_TABLE.defaultBlockState();
                    case 1 -> Blocks.ANVIL.defaultBlockState();
                    case 2 -> Blocks.GRINDSTONE.defaultBlockState();
                    default -> Blocks.CHEST.defaultBlockState();
                };
                put(back.get(i)[0], back.get(i)[1], s);
            }
            alongWalls(Blocks.IRON_BLOCK.defaultBlockState(), 4);
        }

        void prison() {
            // Cells along the back wall: iron bars in front, a bed inside each.
            Direction in = inward();
            List<int[]> back = backWall();
            for (int i = 0; i + 2 < back.size(); i += 3) {
                int[] a = back.get(i), b = back.get(i + 1), c2 = back.get(i + 2);
                bed(b[0], b[1], in, Blocks.BED.white());
                for (int[] q : new int[][]{a, b, c2}) {
                    int fx = q[0] - in.getStepX() * 2, fz = q[1] - in.getStepZ() * 2;
                    put(fx, fz, Blocks.IRON_BARS.defaultBlockState());
                    put(fx, fy + 1, fz, Blocks.IRON_BARS.defaultBlockState());
                }
                put(a[0], a[1], Blocks.IRON_BARS.defaultBlockState());
                put(a[0] - in.getStepX(), a[1] - in.getStepZ(), Blocks.IRON_BARS.defaultBlockState());
            }
        }

        void barracks() {
            int beds = bedRows(Blocks.BED.red(), Math.min(12, Math.max(2, r.floorArea() / 8)));
            if (beds > 0) {
                List<int[]> back = backWall();
                put(back.get(back.size() / 2)[0], back.get(back.size() / 2)[1], Blocks.CHEST.defaultBlockState());
            }
        }

        void singleBedroom() {
            bedRows(Blocks.BED.blue(), 1);
            List<int[]> back = backWall();
            put(back.get(back.size() - 1)[0], back.get(back.size() - 1)[1], Blocks.CHEST.defaultBlockState());
            put(r.x2 - 1, r.z1 + 1, Blocks.LANTERN.defaultBlockState());
        }
    }

    private RoomBuilder() {}
}
