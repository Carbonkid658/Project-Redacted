package dev.projectredacted.site;

import dev.projectredacted.raid.RaidState;
import dev.projectredacted.room.Room;
import dev.projectredacted.room.RoomType;
import dev.projectredacted.staff.Skill;
import dev.projectredacted.staff.StaffRecord;
import dev.projectredacted.staff.StaffRole;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;

import java.util.*;

/** One player's Foundation site. Saved as JSON in the world folder. */
public class Site {
    public String id;
    /** Shown in screen headers and typed to arm the Alpha Warhead. The Director can rename it. */
    public String name;
    public UUID director;
    public String directorName;
    public BlockPos mainframe;
    /** Where the site was founded. The claim stays centered here even after the Mainframe moves into the office. */
    public BlockPos anchor;
    public BlockPos terminal;
    public BlockPos cassie;
    public int level;
    public SiteType type = SiteType.SURFACE_FLAT;
    public boolean generated;
    public boolean mainframeSabotaged;
    public boolean cassieDisabled;
    /** Hack progress on C.A.S.S.I.E.; every 30 points is one stage, up to 4. */
    public int cassieHack;

    public List<Room> rooms = new ArrayList<>();
    /** Parts the facility is built from (site generation), in placement order. */
    public List<dev.projectredacted.site.gen.PlacedPart> parts = new ArrayList<>();
    /** Sealed doorways that growth can open up. */
    public List<dev.projectredacted.site.gen.Doorway> doorways = new ArrayList<>();
    /** Floor height of the Director's Office storey; storeys are 6 blocks from here. */
    public Integer baseFloor;
    public long genSeed;
    /** How wings join (§4A.4); null for the site type's default. Changing it only affects future growth. */
    public dev.projectredacted.site.gen.Wings.Connection connection;
    /** Wing letter -> founded, clearance overrides, sections, room numbering (§4A). */
    public Map<String, dev.projectredacted.site.gen.Wings.WingState> wings = new LinkedHashMap<>();
    /** Surface-Flat: the containment layer goes one storey down (D14). */
    public boolean containmentBelow;
    /** Wings in lockdown after a breach. */
    public Set<String> lockdown = new TreeSet<>();
    /** Inside readers of Emergency Exit hatches. */
    public List<BlockPos> hatchReaders = new ArrayList<>();
    /** Chunks kept loaded while Level 0 builds, released when it finishes. */
    public List<Long> forcedChunks = new ArrayList<>();
    /** From part markers: where raiders arrive and where security stands. */
    public List<BlockPos> raiderEntries = new ArrayList<>();
    public List<BlockPos> staffPosts = new ArrayList<>();
    /** Auto-expand (Director only, off by default). */
    public boolean autoExpand;
    public List<String> expandPriority = new ArrayList<>();
    public long nextExpandCheck;
    /** What auto-expand is waiting on, shown in the Add Room section. */
    public transient String expandStatus;
    public int nextRoomNumber = 1;
    public Map<UUID, Member> members = new LinkedHashMap<>();
    public Map<UUID, StaffRecord> staff = new LinkedHashMap<>();
    public Set<Upgrade> upgrades = EnumSet.noneOf(Upgrade.class);
    public Map<Upgrade, Long> pendingUpgrades = new EnumMap<>(Upgrade.class);

    public RaidState raid;
    public long raidCooldownUntil;
    public int defections;
    public int replacementsOwed;
    public double researchPoints;
    /** SCP entity id -> the room number of the cell it belongs in, for MTF recontainment. */
    public Map<UUID, Integer> scpHomes = new HashMap<>();
    public List<String> logLines = new ArrayList<>();
    public long lastDay = -1;
    /** Coins from Administration waiting to be collected at the terminal. */
    public int coinsOwed;
    /** Research Documents waiting to be collected when no lab barrel had room. */
    public int documentsOwed;

    // Recomputed every second by SiteLogic.
    public transient boolean mainframePresent = true;
    public transient boolean cassiePresent = true;
    public transient int powerSupply, powerDemand, connectCapacity, waterSupply, waterDemand, beds, scpCount, scpWeight;

    public String name() {
        if (name == null || name.isBlank()) name = "Site-" + (10 + Math.floorMod(id == null ? 0 : id.hashCode(), 90));
        return name;
    }

    // ---- Membership and permissions ----

    public boolean isDirector(Player player) {
        return player.getUUID().equals(director);
    }

    public boolean isMember(Player player) {
        return isDirector(player) || members.containsKey(player.getUUID());
    }

    public int clearance(Player player) {
        if (isDirector(player)) return 5;
        Member m = members.get(player.getUUID());
        return m == null ? 0 : m.clearance;
    }

    public boolean canBuild(Player player) {
        if (isDirector(player)) return true;
        Member m = members.get(player.getUUID());
        return m != null && m.build;
    }

    public boolean canUseTerminal(Player player) {
        if (isDirector(player)) return true;
        Member m = members.get(player.getUUID());
        return m != null && m.terminal;
    }

    public boolean canAssign(Player player) {
        if (isDirector(player)) return true;
        Member m = members.get(player.getUUID());
        return m != null && m.assign;
    }

    // ---- Claim ----

    public int claimChunks() {
        return 5 + Math.min(3, level);
    }

    public BlockPos center() {
        return anchor != null ? anchor : mainframe;
    }

    public int minChunkX() {
        return (center().getX() >> 4) - (claimChunks() - 1) / 2;
    }

    public int minChunkZ() {
        return (center().getZ() >> 4) - (claimChunks() - 1) / 2;
    }

    public boolean contains(BlockPos pos) {
        int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
        return cx >= minChunkX() && cx < minChunkX() + claimChunks() && cz >= minChunkZ() && cz < minChunkZ() + claimChunks();
    }

    public boolean containsRoom(Room room) {
        return contains(new BlockPos(room.x1, 0, room.z1)) && contains(new BlockPos(room.x2, 0, room.z2));
    }

    // ---- Rooms ----

    public Room roomAt(BlockPos pos) {
        for (Room r : rooms) if (r.contains(pos)) return r;
        return null;
    }

    public Room room(int number) {
        for (Room r : rooms) if (r.number == number) return r;
        return null;
    }

    public List<Room> rooms(String typeId) {
        List<Room> list = new ArrayList<>();
        for (Room r : rooms) if (r.type.equals(typeId)) list.add(r);
        return list;
    }

    public boolean hasFunctionalRoom(String typeId) {
        for (Room r : rooms) if (r.type.equals(typeId) && r.working()) return true;
        return false;
    }

    public int countFunctional(String typeId) {
        int n = 0;
        for (Room r : rooms) if (r.type.equals(typeId) && r.working()) n++;
        return n;
    }

    public List<Room> cells() {
        List<Room> list = new ArrayList<>();
        for (Room r : rooms) {
            RoomType t = r.typeDef();
            if (t != null && t.isContainment()) list.add(r);
        }
        return list;
    }

    public Room firstEntrance() {
        for (Room r : rooms) if (r.type.equals(dev.projectredacted.room.RoomTypes.ENTRANCE)) return r;
        return null;
    }

    // ---- Staff ----

    public int countStaff(StaffRole role) {
        int n = 0;
        for (StaffRecord s : staff.values()) if (s.role == role) n++;
        return n;
    }

    /** Staff that need a bed (D-Class sleep in the Prison). */
    public int housedStaff() {
        int n = 0;
        for (StaffRecord s : staff.values()) if (s.role != StaffRole.DCLASS) n++;
        return n;
    }

    /** Administration discount on upgrades, 0 to 0.3. */
    public double adminDiscount() {
        double d = 0;
        for (StaffRecord s : staff.values()) {
            if (s.role == StaffRole.ADMINISTRATION) d += 0.05 * (s.skill == Skill.BUREAUCRAT ? 2 : 1) * Math.max(0.5, s.productivity);
        }
        d += 0.03 * countFunctional("projectredacted:admin_office");
        if (hasUpgrade(Upgrade.STREAMLINED_BUREAUCRACY)) d += 0.1;
        return Math.min(0.3, d);
    }

    // ---- Systems ----

    public boolean hasUpgrade(Upgrade upgrade) {
        return upgrades.contains(upgrade);
    }

    public boolean mainframeOnline() {
        return mainframePresent && !mainframeSabotaged;
    }

    public boolean cassieOperational() {
        return cassie != null && cassiePresent && !cassieDisabled && cassieHack < 30;
    }

    public int cassieHackStage() {
        return Math.min(4, cassieHack / 30);
    }

    public void log(String line) {
        net.minecraft.server.level.ServerLevel level = SiteManager.overworld();
        logLines.add(level == null ? line : "[Day " + (dev.projectredacted.util.Time.day(level) + 1) + "] " + line);
        while (logLines.size() > 60) logLines.remove(0);
    }
}
