package dev.projectredacted.staff;

import dev.projectredacted.registry.ModEntities;
import dev.projectredacted.site.Site;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.ServerLevelAccessor;

import java.util.Random;

/** Creates staff: bunker survivors waiting to be recruited, and staff that arrive straight at a site. */
public final class StaffSpawner {
    public static StaffRole randomRole(Random random, boolean includeAdmin) {
        int total = 0;
        for (StaffRole r : StaffRole.values()) if (r.recruitWeight > 0 && (includeAdmin || r != StaffRole.ADMINISTRATION)) total += r.recruitWeight;
        int roll = random.nextInt(total);
        for (StaffRole r : StaffRole.values()) {
            if (r.recruitWeight <= 0 || (!includeAdmin && r == StaffRole.ADMINISTRATION)) continue;
            roll -= r.recruitWeight;
            if (roll < 0) return r;
        }
        return StaffRole.SECURITY;
    }

    /** A survivor in a bunker. Some want diamonds before they'll join. Works during world generation too. */
    public static StaffEntity spawnSurvivor(ServerLevelAccessor level, BlockPos pos, Random random) {
        StaffEntity e = ModEntities.STAFF.create(level.getLevel(), EntitySpawnReason.STRUCTURE);
        if (e == null) return null;
        StaffRole role = randomRole(random, true);
        boolean named = random.nextInt(12) == 0;
        e.setupSurvivor(role, named ? StaffNames.named(random) : StaffNames.random(random), named ? Skill.forRole(role, random) : null,
                random.nextInt(3) == 0 ? 1 + random.nextInt(named ? 5 : 3) : 0);
        e.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360f, 0);
        level.addFreshEntity(e);
        return e;
    }

    /** Staff that join a site directly (replacements, D-Class). */
    public static StaffRecord spawnForSite(ServerLevel level, Site site, StaffRole role, BlockPos pos, boolean named) {
        Random random = new Random();
        StaffEntity e = ModEntities.STAFF.create(level, EntitySpawnReason.EVENT);
        String name = role == StaffRole.DCLASS ? StaffNames.dclass(random) : named ? StaffNames.named(random) : StaffNames.random(random);
        Skill skill = named && role != StaffRole.DCLASS ? Skill.forRole(role, random) : null;
        StaffRecord record = new StaffRecord();
        record.name = name;
        record.role = role;
        record.clearance = role.baseClearance;
        record.named = skill != null;
        record.skill = skill;
        if (e != null) {
            e.setupSurvivor(role, name, skill, 0);
            e.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
            record.id = e.getUUID();
            site.staff.put(record.id, record);
            e.joinSite(site);
            level.addFreshEntity(e);
        }
        return record;
    }

    private StaffSpawner() {}
}
