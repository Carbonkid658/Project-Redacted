package dev.projectredacted.raid;

import net.minecraft.core.BlockPos;

import java.util.*;

/** A raid in progress against one site. */
public class RaidState {
    public List<Faction> factions = new ArrayList<>();
    public List<UUID> raiders = new ArrayList<>();
    public long startTick;
    /** When raiders arrive. With C.A.S.S.I.E. online the site gets a warning before this. */
    public long arriveTick;
    public boolean spawned;
    public boolean alarmed;
    public boolean mtfSent;
    public BlockPos spawn;
    /** Objective stage reached per faction. */
    public Map<Faction, Integer> stage = new EnumMap<>(Faction.class);
    /** Rooms each faction already sabotaged. */
    public Set<Integer> hitRooms = new HashSet<>();
    public int captured;

    public int stage(Faction f) {
        return stage.getOrDefault(f, 0);
    }
}
