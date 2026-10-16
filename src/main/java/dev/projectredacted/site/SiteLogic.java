package dev.projectredacted.site;

import dev.projectredacted.api.ContainmentContext;
import dev.projectredacted.api.ScpApi;
import dev.projectredacted.api.ScpDefinition;
import dev.projectredacted.registry.ModBlocks;
import dev.projectredacted.registry.ModItems;
import dev.projectredacted.registry.ModTags;
import dev.projectredacted.room.Room;
import dev.projectredacted.room.RoomType;
import dev.projectredacted.staff.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

import java.util.*;

/** The site's simulation: power, water, containment, staff needs and work, upgrades and daily events. */
public final class SiteLogic {
    private static final Random RANDOM = new Random();
    private static int seconds;

    // ---------------------------------------------------------------- every second

    static void tickSecond(ServerLevel level, Site site) {
        seconds++;
        if (level.isLoaded(site.mainframe)) site.mainframePresent = level.getBlockState(site.mainframe).is(ModBlocks.MAINFRAME);
        if (site.cassie != null && level.isLoaded(site.cassie)) site.cassiePresent = level.getBlockState(site.cassie).is(ModBlocks.CASSIE);

        computePower(site);
        computeWater(site);
        if (seconds % 5 == 0) countScps(level, site);
        if (seconds % 30 == 0) countBeds(level, site);
        if (seconds % 60 == 0) checkContainment(level, site);
        finishUpgrades(level, site);
    }

    private static void computePower(Site site) {
        double eff = site.hasUpgrade(Upgrade.GENERATOR_EFFICIENCY) ? 1.25 : 1.0;
        double supply = 0;
        int breakers = 0;
        for (Room r : site.rooms) {
            if (!r.built || r.integrity <= 0) continue;
            if (r.type.equals("projectredacted:generator")) {
                int engineers = assignedTo(site, r.number, StaffRole.ENGINEER);
                supply += 12 * (r.integrity / 100.0) * (1 + 0.5 * Math.min(2, engineers)) * eff;
            } else if (r.type.equals("projectredacted:circuit_breaker")) {
                breakers++;
            }
        }
        site.powerSupply = (int) supply;
        site.connectCapacity = 4 + 6 * breakers;
        int remaining = site.powerSupply;
        int connected = 0;
        int demand = 0;
        List<Room> ordered = new ArrayList<>(site.rooms);
        ordered.sort(Comparator.comparingInt(r -> r.number));
        for (Room r : ordered) {
            RoomType t = r.typeDef();
            if (t == null) {
                r.poweredNow = false;
                continue;
            }
            if (t.powerDraw <= 0) {
                r.poweredNow = r.built && r.integrity > 0;
                continue;
            }
            r.poweredNow = false;
            if (!r.connected) continue;
            demand += t.powerDraw;
            if (!r.built || r.integrity <= 0 || !site.mainframeOnline()) continue;
            if (connected >= site.connectCapacity || remaining < t.powerDraw) continue;
            connected++;
            remaining -= t.powerDraw;
            r.poweredNow = true;
        }
        site.powerDemand = demand;
    }

    private static void computeWater(Site site) {
        double eff = site.hasUpgrade(Upgrade.WATER_RECYCLING) ? 1.25 : 1.0;
        int supply = 0;
        for (Room r : site.rooms) {
            if (r.type.equals("projectredacted:water_pump") && r.built && r.integrity > 0 && r.poweredNow) {
                int engineers = assignedTo(site, r.number, StaffRole.ENGINEER);
                supply += (int) (10 * (r.integrity / 100.0) * (1 + 0.25 * Math.min(2, engineers)) * eff);
            }
        }
        site.waterSupply = supply;
        int remaining = supply, demand = 0;
        for (Room r : site.rooms) {
            RoomType t = r.typeDef();
            if (t == null || t.waterDraw <= 0) {
                r.wateredNow = true;
                continue;
            }
            demand += t.waterDraw;
            // Water shortage default: the rooms that don't get water simply stop working.
            r.wateredNow = r.poweredNow && remaining >= t.waterDraw;
            if (r.wateredNow) remaining -= t.waterDraw;
        }
        site.waterDemand = demand;
    }

    /** Counts SCPs inside containment cells: SCP-tagged entities, and SCP-tagged items in the cell's containers. */
    private static void countScps(ServerLevel level, Site site) {
        int count = 0, weight = 0;
        for (Room cell : site.cells()) {
            cell.scpCount = 0;
            if (!level.isLoaded(cell.center())) continue;
            String label = "";
            for (Entity e : level.getEntitiesOfClass(Entity.class, cell.box(), e -> e.getType().builtInRegistryHolder().is(ModTags.SCP_ENTITIES))) {
                ScpDefinition def = ScpApi.forEntity(e.getType());
                cell.scpCount++;
                weight += ScpApi.raidWeight(def);
                site.scpHomes.putIfAbsent(e.getUUID(), cell.number);
                if (label.isEmpty()) label = def != null ? def.number : e.getName().getString();
            }
            for (ItemStack stack : cellItems(level, cell)) {
                if (!stack.is(ModTags.SCP_ITEMS)) continue;
                ScpDefinition def = ScpApi.forItem(stack.getItem());
                cell.scpCount++;
                weight += ScpApi.raidWeight(def);
                if (label.isEmpty()) label = def != null ? def.number : stack.getHoverName().getString();
            }
            if (cell.scpCount > 0 && cell.scpLabel.isEmpty()) cell.scpLabel = label;
            count += cell.scpCount;
        }
        site.scpCount = count;
        site.scpWeight = weight;
    }

    public static List<ItemStack> cellItems(ServerLevel level, Room cell) {
        List<ItemStack> list = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(cell.x1, cell.y1, cell.z1, cell.x2, cell.y2, cell.z2)) {
            if (level.getBlockEntity(p) instanceof Container c) {
                for (int i = 0; i < c.getContainerSize(); i++) {
                    ItemStack s = c.getItem(i);
                    if (!s.isEmpty()) list.add(s);
                }
            }
        }
        return list;
    }

    /** True if an SCP (entity or item) is inside the room; such rooms can't be demolished. */
    public static boolean hasScp(ServerLevel level, Room room) {
        if (!level.getEntitiesOfClass(Entity.class, room.box(), e -> e.getType().builtInRegistryHolder().is(ModTags.SCP_ENTITIES)).isEmpty()) return true;
        for (ItemStack s : cellItems(level, room)) if (s.is(ModTags.SCP_ITEMS)) return true;
        return false;
    }

    private static void countBeds(ServerLevel level, Site site) {
        int housing = 0;
        for (Room r : site.rooms) {
            RoomType t = r.typeDef();
            if (t == null || !(t.isHousing() || r.type.equals("projectredacted:prison"))) continue;
            if (!level.isLoaded(r.center())) {
                if (r.beds > 0 && t.isHousing()) housing += r.beds;
                continue;
            }
            int beds = 0;
            for (BlockPos p : BlockPos.betweenClosed(r.x1, r.y1, r.z1, r.x2, r.y2, r.z2)) {
                BlockState s = level.getBlockState(p);
                if (s.getBlock() instanceof BedBlock && s.getValue(BedBlock.PART) == BedPart.HEAD) beds++;
            }
            r.beds = beds;
            if (t.isHousing() && r.built) housing += beds;
        }
        site.beds = housing;
    }

    public static List<BlockPos> bedPositions(ServerLevel level, Site site, boolean prison) {
        List<BlockPos> list = new ArrayList<>();
        for (Room r : site.rooms) {
            RoomType t = r.typeDef();
            if (t == null || !r.built) continue;
            boolean isPrison = r.type.equals("projectredacted:prison");
            if (prison != isPrison || (!prison && !t.isHousing())) continue;
            for (BlockPos p : BlockPos.betweenClosed(r.x1, r.y1, r.z1, r.x2, r.y2, r.z2)) {
                BlockState s = level.getBlockState(p);
                if (s.getBlock() instanceof BedBlock && s.getValue(BedBlock.PART) == BedPart.HEAD) list.add(p.immutable());
            }
        }
        return list;
    }

    /** Internal breaches: SCPs whose requirements aren't met may escape. */
    private static void checkContainment(ServerLevel level, Site site) {
        if (ScpApi.all().isEmpty()) return;
        double mult = site.hasUpgrade(Upgrade.CONTAINMENT_PROTOCOLS) ? 0.5 : 1.0;
        for (Room cell : site.cells()) {
            if (!level.isLoaded(cell.center())) continue;
            for (Entity e : level.getEntitiesOfClass(Entity.class, cell.box(), x -> x.getType().builtInRegistryHolder().is(ModTags.SCP_ENTITIES))) {
                ScpDefinition def = ScpApi.forEntity(e.getType());
                if (def == null) continue;
                var unmet = def.unmet(new ContainmentContext(level, site, cell, e));
                if (!unmet.isEmpty() && RANDOM.nextDouble() < def.breachChance * mult) {
                    breach(level, site, cell, def, e, unmet.get(0).describe());
                }
            }
        }
    }

    public static void breach(ServerLevel level, Site site, Room cell, ScpDefinition def, Entity scp, String reason) {
        DoorControl.setDoorsInBox(level, cell.box(), true);
        site.log("Containment breach: " + def.number + " (" + reason + ")");
        if (!Cassie.announce(site, "Attention all personnel. " + def.number + " has breached containment. " + reason + ".")) {
            Cassie.alarm(site, def.number + " has breached containment!");
        }
        // The breached wing's gate and section doors close (§4A.3).
        dev.projectredacted.site.gen.Wings.lockdown(level, site, cell.wing);
        ScpApi.fireBreach(level, site, cell, def, scp);
    }

    private static void finishUpgrades(ServerLevel level, Site site) {
        if (site.pendingUpgrades.isEmpty() || !site.mainframeOnline()) return;
        long now = level.getGameTime();
        Iterator<Map.Entry<Upgrade, Long>> it = site.pendingUpgrades.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Upgrade, Long> e = it.next();
            if (e.getValue() > now) continue;
            Upgrade u = e.getKey(); // an EnumMap entry can't be read after it's removed
            it.remove();
            site.upgrades.add(u);
            switch (u) {
                case FACILITY_LEVEL_1 -> site.level = Math.max(site.level, 1);
                case FACILITY_LEVEL_2 -> site.level = Math.max(site.level, 2);
                case FACILITY_LEVEL_3 -> site.level = Math.max(site.level, 3);
                default -> {}
            }
            String msg = "Upgrade complete: " + u.displayName + ".";
            if (!Cassie.announce(site, msg)) Cassie.broadcast(site, net.minecraft.network.chat.Component.literal(msg)
                    .withStyle(net.minecraft.ChatFormatting.GREEN), false);
        }
    }

    // ---------------------------------------------------------------- every 5 seconds

    static void tickStaff(ServerLevel level, Site site) {
        long dayTime = dev.projectredacted.util.Time.dayTime(level);
        boolean night = dayTime >= 13000 && dayTime < 23000;
        boolean mealtime = dayTime < 2000 || (dayTime >= 5000 && dayTime < 7000) || (dayTime >= 10000 && dayTime < 12000);
        boolean canEat = site.hasFunctionalRoom("projectredacted:cafeteria") && site.hasFunctionalRoom("projectredacted:kitchen");
        boolean running = site.mainframeOnline();

        int janitors = 0;
        double cheer = 0;
        for (StaffRecord s : site.staff.values()) {
            if (s.role == StaffRole.JANITOR) janitors++;
            if (s.skill == Skill.CHEERFUL) cheer += 5;
        }
        double siteMorale = 50 + (site.hasFunctionalRoom("projectredacted:bathroom") ? 15 : 0)
                + (site.hasFunctionalRoom("projectredacted:lounge") ? 10 : 0)
                + (site.hasFunctionalRoom("projectredacted:medbay") ? 5 : 0)
                + Math.min(20, janitors * 5) + Math.min(10, cheer)
                - (site.raid != null ? 15 : 0);

        int bedIndex = 0;
        List<UUID> leaving = new ArrayList<>();
        List<UUID> defecting = new ArrayList<>();
        double researchMult = site.hasUpgrade(Upgrade.ADVANCED_INSTRUMENTS) ? 1.25 : 1.0;

        for (StaffRecord s : site.staff.values()) {
            if (s.role == StaffRole.DCLASS) {
                s.productivity = 1;
                continue;
            }
            s.bedIndex = bedIndex < site.beds ? bedIndex : -1;
            bedIndex++;

            s.hunger = Math.max(0, s.hunger - 0.42);
            if (canEat && mealtime && s.hunger < 70) s.hunger = 100;
            if (night) s.energy = s.bedIndex >= 0 ? Math.min(100, s.energy + 2.5) : Math.max(0, s.energy - 0.15);
            else s.energy = Math.max(0, s.energy - (s.skill == Skill.TIRELESS ? 0.12 : 0.25));

            double target = siteMorale - (s.hunger < 20 ? 25 : 0) - (s.energy < 20 ? 20 : 0) - (s.bedIndex < 0 ? 20 : 0);
            s.morale += Math.signum(target - s.morale) * Math.min(0.5, Math.abs(target - s.morale));
            s.morale = Math.max(0, Math.min(100, s.morale));

            if (s.hunger <= 0 || s.energy <= 0) s.neglect++;
            else s.neglect = Math.max(0, s.neglect - 1);
            if (s.neglect > 240) {
                leaving.add(s.id);
                continue;
            }
            if (s.morale < 15 && RANDOM.nextDouble() < 0.002) {
                defecting.add(s.id);
                continue;
            }

            s.productivity = (s.hunger > 30 ? 1 : 0.5) * (s.energy > 30 ? 1 : 0.6) * (0.5 + s.morale / 100.0) * (1 + 0.1 * (s.level - 1));
            if (!running || (night && s.role != StaffRole.SECURITY)) continue;
            work(level, site, s, researchMult);
        }

        for (UUID id : leaving) removeStaff(level, site, id, " abandoned the site. Their needs weren't being met.", false);
        for (UUID id : defecting) removeStaff(level, site, id, " defected. Morale was too low.", true);

        long day = dev.projectredacted.util.Time.day(level);
        if (site.lastDay < 0) site.lastDay = day;
        if (day != site.lastDay) {
            site.lastDay = day;
            daily(level, site);
        }
    }

    private static void work(ServerLevel level, Site site, StaffRecord s, double researchMult) {
        Assignment a = s.assignment;
        switch (s.role) {
            case RESEARCHER -> {
                double rate = s.productivity * researchMult * (s.skill == Skill.GENIUS ? 1.5 : 1);
                Room target = a.kind == Assignment.Kind.SCP ? site.room(a.room) : null;
                boolean observing = target != null && target.scpCount > 0
                        && (site.hasFunctionalRoom("projectredacted:observation_room") || site.hasFunctionalRoom("projectredacted:lab"));
                if (observing) {
                    site.researchPoints += rate;
                    s.addXp(1);
                } else if (site.hasFunctionalRoom("projectredacted:lab") || site.hasFunctionalRoom("projectredacted:researcher_office")) {
                    site.researchPoints += 0.3 * rate;
                    s.addXp(0.4);
                }
                while (site.researchPoints >= 100) {
                    site.researchPoints -= 100;
                    depositDocument(level, site);
                }
            }
            case ENGINEER -> {
                Room worst = null;
                for (Room r : site.rooms) if (r.built && r.integrity < 100 && (worst == null || r.integrity < worst.integrity)) worst = r;
                if (worst != null) {
                    double rate = s.productivity * (s.skill == Skill.HANDY ? 2 : 1) * (site.hasFunctionalRoom("projectredacted:storage_room") ? 1.5 : 1);
                    worst.integrity = Math.min(100, worst.integrity + Math.max(1, (int) Math.round(rate)));
                    s.addXp(1);
                } else {
                    s.addXp(0.2);
                }
            }
            case SECURITY, JANITOR, ADMINISTRATION, MEDICAL -> s.addXp(a.kind != Assignment.Kind.NONE ? 0.5 : 0.25);
            default -> {}
        }
    }

    /** Completed research becomes a Research Document in a lab's barrel or chest. */
    private static void depositDocument(ServerLevel level, Site site) {
        for (Room lab : site.rooms("projectredacted:lab")) {
            if (!level.isLoaded(lab.center())) continue;
            for (BlockPos p : BlockPos.betweenClosed(lab.x1, lab.y1, lab.z1, lab.x2, lab.y2, lab.z2)) {
                if (level.getBlockEntity(p) instanceof Container c) {
                    for (int i = 0; i < c.getContainerSize(); i++) {
                        ItemStack st = c.getItem(i);
                        if (st.isEmpty()) {
                            c.setItem(i, new ItemStack(ModItems.RESEARCH_DOCUMENT));
                            return;
                        }
                        if (st.is(ModItems.RESEARCH_DOCUMENT) && st.getCount() < st.getMaxStackSize()) {
                            st.grow(1);
                            c.setChanged();
                            return;
                        }
                    }
                }
            }
        }
        site.documentsOwed++;
    }

    private static void daily(ServerLevel level, Site site) {
        // Administration brings in a stipend.
        for (StaffRecord s : site.staff.values()) if (s.role == StaffRole.ADMINISTRATION) site.coinsOwed += 2 + s.level / 3;

        // Default for the open D-Class question: a powered Prison takes in one D-Class per day while it has beds.
        int prisonBeds = 0;
        Room prison = null;
        for (Room r : site.rooms("projectredacted:prison")) {
            if (r.working()) {
                prisonBeds += Math.max(0, r.beds);
                prison = r;
            }
        }
        if (prison != null && site.countStaff(StaffRole.DCLASS) < prisonBeds) {
            StaffSpawner.spawnForSite(level, site, StaffRole.DCLASS, prison.center(), false);
            site.log("A new D-Class arrived at the Prison.");
        }

        // Replacements for staff lost, once the upgrade is bought.
        if (site.hasUpgrade(Upgrade.PERSONNEL_REPLACEMENT) && site.replacementsOwed > 0 && site.housedStaff() < site.beds) {
            Room entrance = site.firstEntrance();
            BlockPos at = entrance != null ? entrance.center() : site.mainframe.above();
            StaffRecord r = StaffSpawner.spawnForSite(level, site, StaffSpawner.randomRole(RANDOM, false), at, RANDOM.nextInt(20) == 0);
            site.replacementsOwed--;
            Cassie.announce(site, "Replacement personnel have arrived: " + r.fullName() + ".");
        }
    }

    public static void removeStaff(ServerLevel level, Site site, UUID id, String reason, boolean defected) {
        StaffRecord r = site.staff.remove(id);
        if (r == null) return;
        Entity e = level.getEntity(id);
        if (e != null) e.discard();
        if (defected) site.defections++;
        site.replacementsOwed++;
        String msg = r.fullName() + reason;
        if (!Cassie.announce(site, msg)) Cassie.broadcast(site, net.minecraft.network.chat.Component.literal(msg)
                .withStyle(net.minecraft.ChatFormatting.YELLOW), false);
    }

    public static int assignedTo(Site site, int room, StaffRole role) {
        int n = 0;
        for (StaffRecord s : site.staff.values()) {
            if (s.role == role && s.assignment.kind != Assignment.Kind.NONE && s.assignment.room == room) n++;
        }
        return n;
    }

    private SiteLogic() {}
}
