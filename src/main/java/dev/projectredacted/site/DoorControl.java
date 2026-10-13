package dev.projectredacted.site;

import dev.projectredacted.block.KeycardReaderBlock;
import dev.projectredacted.raid.ArmedNpcEntity;
import dev.projectredacted.raid.Faction;
import dev.projectredacted.registry.ModBlocks;
import dev.projectredacted.staff.StaffEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/** Keycard doors: opens doors next to readers for keycards and cleared staff, then closes them again. */
public final class DoorControl {
    /** Lower door half -> game time when it should close. */
    private static final Map<Long, Long> CLOSE_AT = new HashMap<>();

    /** Opens every door within 2 blocks of a reader for the given number of ticks. */
    public static void openNear(Level level, BlockPos reader, int ticks) {
        for (BlockPos p : BlockPos.betweenClosed(reader.offset(-2, -2, -2), reader.offset(2, 1, 2))) {
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof DoorBlock door && s.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
                door.setOpen(null, level, s, p, true);
                CLOSE_AT.put(p.asLong(), level.getGameTime() + ticks);
            }
        }
    }

    public static void setDoorsInBox(Level level, AABB box, boolean open) {
        for (BlockPos p : BlockPos.betweenClosed((int) box.minX - 1, (int) box.minY, (int) box.minZ - 1, (int) box.maxX, (int) box.maxY, (int) box.maxZ)) {
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof DoorBlock door && s.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER && s.getValue(BlockStateProperties.OPEN) != open) {
                door.setOpen(null, level, s, p, open);
            }
        }
    }

    static void tick(ServerLevel level, Set<Long> readers) {
        long now = level.getGameTime();
        Iterator<Map.Entry<Long, Long>> it = CLOSE_AT.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, Long> e = it.next();
            if (e.getValue() > now) continue;
            BlockPos p = BlockPos.of(e.getKey());
            if (level.isLoaded(p)) {
                BlockState s = level.getBlockState(p);
                if (s.getBlock() instanceof DoorBlock door && !anyoneInDoorway(level, p)) door.setOpen(null, level, s, p, false);
                else if (s.getBlock() instanceof DoorBlock) continue;
            }
            it.remove();
        }

        Iterator<Long> readerIt = readers.iterator();
        while (readerIt.hasNext()) {
            BlockPos pos = BlockPos.of(readerIt.next());
            if (!level.isLoaded(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (!state.is(ModBlocks.KEYCARD_READER)) {
                readerIt.remove();
                continue;
            }
            int required = state.getValue(KeycardReaderBlock.LEVEL);
            AABB area = new AABB(pos).inflate(2.5, 1.5, 2.5);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, area, DoorControl::usesReaders)) {
                if (clearanceOf(e) >= required) {
                    openNear(level, pos, 40);
                    break;
                }
            }
        }
    }

    private static boolean usesReaders(LivingEntity e) {
        return e instanceof StaffEntity || (e instanceof ArmedNpcEntity npc && npc.faction() == Faction.MTF);
    }

    private static int clearanceOf(LivingEntity e) {
        if (e instanceof StaffEntity staff) return staff.clearance();
        return 5;
    }

    private static boolean anyoneInDoorway(Level level, BlockPos lower) {
        return !level.getEntitiesOfClass(LivingEntity.class, new AABB(lower).expandTowards(0, 1, 0)).isEmpty();
    }

    private DoorControl() {}
}
