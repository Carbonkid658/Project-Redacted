package dev.projectredacted.staff;

import dev.projectredacted.api.ScpApi;
import dev.projectredacted.api.ScpDefinition;
import dev.projectredacted.registry.ModEntities;
import dev.projectredacted.room.Room;
import dev.projectredacted.room.Stations;
import dev.projectredacted.site.Cassie;
import dev.projectredacted.site.Site;
import dev.projectredacted.site.SiteLogic;
import dev.projectredacted.site.Upgrade;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;

import java.util.*;

/**
 * Field Agent expeditions (NPC revamp §4.5). The team walks to the site entrance and leaves; while away they
 * exist only as records. When the timer ends they come back through the entrance with research documents,
 * maybe an SCP (delivered to the Field Operations room like an MC&D flare), and maybe fewer people.
 */
public final class Expeditions {
    public static final String FIELD_OPERATIONS = "projectredacted:field_operations";
    public static final int MAX_TEAM = 4;
    /** Agents who can't reach the entrance in this long are counted as gone anyway. */
    private static final int WALK_TICKS = 1200;
    private static final Random RANDOM = new Random();

    /** Why a team can't be sent right now, or null. */
    public static String blocker(Site site, Collection<UUID> team) {
        if (!site.hasUpgrade(Upgrade.FIELD_OPERATIONS)) return "Needs the Field Operations upgrade.";
        if (!site.mainframeOnline()) return "The Mainframe is offline.";
        if (operationsRoom(site) == null) return "Build and power a Field Operations room.";
        if (team.isEmpty()) return "Pick at least one Field Agent.";
        if (team.size() > MAX_TEAM) return "A team is at most " + MAX_TEAM + " agents.";
        for (UUID id : team) {
            StaffRecord r = site.staff.get(id);
            if (r == null || r.role != StaffRole.FIELD_AGENT) return "Only Field Agents can go.";
            if (onTrip(site, id) != null) return r.name + " is already on a trip.";
        }
        return null;
    }

    /** A working Field Operations room, or null. */
    public static Room operationsRoom(Site site) {
        for (Room r : site.rooms(FIELD_OPERATIONS)) if (r.working()) return r;
        return null;
    }

    public static Expedition onTrip(Site site, UUID agent) {
        for (Expedition e : site.expeditions) if (e.agents.contains(agent)) return e;
        return null;
    }

    /** Whether this agent is out of the world on a trip (not just walking out). */
    public static boolean away(Site site, UUID agent) {
        Expedition e = onTrip(site, agent);
        return e != null && !e.leaving.contains(agent);
    }

    public static void send(ServerLevel level, Site site, List<UUID> team, Expedition.Length length) {
        Expedition e = new Expedition();
        e.id = site.expeditions.stream().mapToInt(x -> x.id).max().orElse(0) + 1;
        e.agents.addAll(team);
        e.leaving.addAll(team);
        e.length = length;
        e.sentTick = level.getGameTime();
        site.expeditions.add(e);
        List<String> names = new ArrayList<>();
        for (UUID id : team) names.add(site.staff.get(id).name);
        site.log("Expedition " + e.id + " (" + length.displayName.toLowerCase() + ") sent out: " + String.join(", ", names) + ".");
    }

    /** Where agents leave from and come back to: the Entrance room (its marker, else its middle). */
    public static BlockPos gate(Site site) {
        Room entrance = site.firstEntrance();
        if (entrance == null) return site.mainframe.above();
        return entrance.entrance != null ? entrance.entrance : entrance.center();
    }

    /** Every second, from the site tick. */
    public static void tick(ServerLevel level, Site site) {
        if (site.expeditions.isEmpty()) return;
        long now = level.getGameTime();
        for (Iterator<Expedition> it = site.expeditions.iterator(); it.hasNext(); ) {
            Expedition e = it.next();
            e.agents.removeIf(id -> !site.staff.containsKey(id));
            e.leaving.removeIf(id -> !site.staff.containsKey(id));
            if (e.agents.isEmpty()) {
                it.remove();
                continue;
            }
            if (!e.away()) {
                boolean late = now - e.sentTick > WALK_TICKS;
                for (UUID id : new ArrayList<>(e.leaving)) {
                    Entity agent = level.getEntity(id);
                    if (agent == null || late) {
                        // Unloaded, or stuck: they've found their own way out.
                        if (agent != null) agent.discard();
                        e.leaving.remove(id);
                    }
                }
                if (e.leaving.isEmpty()) e.returnTick = now + e.length.ticks;
                continue;
            }
            if (now >= e.returnTick) {
                it.remove();
                finish(level, site, e);
            }
        }
    }

    /** Called by a leaving agent's AI when it reaches the entrance. */
    public static void leftSite(StaffEntity agent, Site site) {
        Expedition e = onTrip(site, agent.getUUID());
        if (e == null) return;
        e.leaving.remove(agent.getUUID());
        Stations.release(site, agent.getUUID(), -1);
        agent.discard();
    }

    private static void finish(ServerLevel level, Site site, Expedition e) {
        int team = e.agents.size();
        Expedition.Length len = e.length;
        double factor = len.teamFactor(team);
        List<String> lost = new ArrayList<>(), hurt = new ArrayList<>(), back = new ArrayList<>();
        BlockPos gate = gate(site);
        for (UUID id : e.agents) {
            StaffRecord r = site.staff.get(id);
            if (r == null) continue;
            double roll = RANDOM.nextDouble();
            if (roll < len.death * factor) {
                lost.add(r.name);
                SiteLogic.removeStaff(level, site, id, " didn't come back from expedition " + e.id + ".", false);
                continue;
            }
            boolean injured = roll < (len.death + len.injury) * factor;
            r.addXp(len.xp);
            StaffEntity agent = respawn(level, site, r, gate);
            if (injured && agent != null) {
                agent.setHealth(agent.getMaxHealth() * (0.25f + RANDOM.nextFloat() * 0.25f));
                hurt.add(r.name);
            } else {
                back.add(r.name);
            }
        }
        int docs = len.documents(team) + (RANDOM.nextInt(3) == 0 ? 1 : 0);
        site.documentsOwed += docs;
        String scp = null;
        if (back.size() + hurt.size() > 0 && RANDOM.nextDouble() < len.scpChance(team)) {
            List<ScpDefinition> found = new ArrayList<>();
            for (ScpDefinition def : ScpApi.all()) if (def.entityType != null || def.item != null) found.add(def);
            Room ops = operationsRoom(site);
            if (ops == null) for (Room r : site.rooms(FIELD_OPERATIONS)) ops = r;
            if (!found.isEmpty() && ops != null) {
                ScpDefinition def = found.get(RANDOM.nextInt(found.size()));
                Stations.Station spot = Stations.claim(level, site, ops, UUID.randomUUID());
                def.deliver(level, spot != null ? spot.stand() : ops.center());
                scp = def.number;
            }
        }
        StringBuilder msg = new StringBuilder("Expedition " + e.id + " has returned with " + docs + " research document" + (docs == 1 ? "" : "s"));
        if (scp != null) msg.append(" and ").append(scp).append(", delivered to Field Operations");
        msg.append(".");
        if (!hurt.isEmpty()) msg.append(" Injured: ").append(String.join(", ", hurt)).append(".");
        if (!lost.isEmpty()) msg.append(" Lost: ").append(String.join(", ", lost)).append(".");
        site.log(msg.toString());
        if (!Cassie.announce(site, msg.toString())) Cassie.broadcast(site, net.minecraft.network.chat.Component.literal(msg.toString())
                .withStyle(net.minecraft.ChatFormatting.AQUA), false);
    }

    /** Brings an agent back into the world, with the same UUID so their record still points at them. */
    private static StaffEntity respawn(ServerLevel level, Site site, StaffRecord r, BlockPos at) {
        Entity old = level.getEntity(r.id);
        if (old != null) old.discard();
        StaffEntity e = ModEntities.STAFF.create(level, EntitySpawnReason.EVENT);
        if (e == null) return null;
        String skin = r.skin;
        e.setUUID(r.id);
        e.setupSurvivor(r.role, r.name, r.skill, 0);
        e.setSkin(skin);
        e.joinSite(site);
        e.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        level.addFreshEntity(e);
        return e;
    }

    private Expeditions() {}
}
