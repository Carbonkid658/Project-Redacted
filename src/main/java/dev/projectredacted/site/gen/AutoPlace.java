package dev.projectredacted.site.gen;

import dev.projectredacted.room.Room;
import dev.projectredacted.room.RoomPlanner;
import dev.projectredacted.room.RoomType;
import dev.projectredacted.room.RoomTypes;
import dev.projectredacted.site.Cassie;
import dev.projectredacted.site.Site;
import dev.projectredacted.site.SiteManager;
import dev.projectredacted.util.Cost;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import java.util.*;

/**
 * Auto-place (design §8.1, D9): pick a room type and the mod finds free spots joined to the facility. The
 * player flips through spots as a ghost outline, then builds one at the normal construction speed.
 */
public class AutoPlace {
    private static final Map<UUID, AutoPlace> SESSIONS = new HashMap<>();
    private static final Random RANDOM = new Random();
    public static final int MAX_SPOTS = 8;

    public final Site site;
    public final String typeId;
    /** Index into {@link PartKit#sizes}. */
    public int size;
    public final List<SitePlan> spots = new ArrayList<>();
    public int index;
    public long seed = RANDOM.nextLong();
    public boolean nearMe;
    public String error;
    private SiteGen.Survey survey;

    private AutoPlace(Site site, String typeId) {
        this.site = site;
        this.typeId = typeId;
    }

    public static AutoPlace get(ServerPlayer player) {
        AutoPlace a = SESSIONS.get(player.getUUID());
        if (a != null && SiteManager.byId(a.site.id) != a.site) {
            SESSIONS.remove(player.getUUID());
            return null;
        }
        return a;
    }

    public static AutoPlace start(ServerPlayer player, Site site, String typeId, int size) {
        AutoPlace a = new AutoPlace(site, typeId);
        a.size = size;
        SESSIONS.put(player.getUUID(), a);
        return a;
    }

    public static void stop(ServerPlayer player) {
        SESSIONS.remove(player.getUUID());
        PlanPreview.clear(player);
    }

    public static void clear() {
        SESSIONS.clear();
    }

    public RoomType type() {
        return RoomTypes.get(typeId);
    }

    public List<SitePart> sizes() {
        return PartKit.active().sizes(typeId);
    }

    public SitePart sizePart() {
        List<SitePart> list = sizes();
        if (list.isEmpty()) return null;
        return list.get(Math.max(0, Math.min(size, list.size() - 1)));
    }

    public static String sizeLabel(SitePart p) {
        return p.sx + "×" + p.sz + (p.storeys() > 1 ? "×" + p.storeys() + " storeys" : "");
    }

    public SitePlan current() {
        return spots.isEmpty() ? null : spots.get(Math.floorMod(index, spots.size()));
    }

    // ---------------------------------------------------------------- searching

    /** Finds spots; returns false (with {@link #error}) if there are none. */
    public boolean search(ServerPlayer player) {
        spots.clear();
        index = 0;
        error = null;
        SitePart part = sizePart();
        if (part == null) {
            error = "There's no part for this room type.";
            return false;
        }
        survey = SiteGen.survey((ServerLevel) player.level(), site, seed);
        Assembler.Input in = survey.input();
        in.near = nearMe ? player.blockPosition() : null;
        in.exitOk = (p, d) -> true;
        Integer storey = SiteGen.preferredStorey(site, in);
        if (storey != null && !nearMe) spots.addAll(Assembler.spots(in, new Assembler.Goal(typeId, false, storey, part), MAX_SPOTS));
        if (spots.size() < MAX_SPOTS) {
            java.util.Set<String> have = new java.util.HashSet<>();
            for (SitePlan p : spots) have.add(p.key());
            for (SitePlan p : Assembler.spots(in, new Assembler.Goal(typeId, false, null, part), MAX_SPOTS)) {
                if (spots.size() >= MAX_SPOTS) break;
                if (have.add(p.key())) spots.add(p);
            }
        }
        if (spots.isEmpty()) {
            error = "No free space joined to the facility for a " + sizeLabel(part) + " " + type().name
                    + ". Try a smaller size, or upgrade the facility level for a bigger claim.";
            PlanPreview.clear(player);
            return false;
        }
        if (!nearMe) spots.sort(Comparator.comparingInt((SitePlan p) -> p.overwrites));
        PlanPreview.show(player, current());
        return true;
    }

    public void next(ServerPlayer player) {
        if (spots.isEmpty()) return;
        index = Math.floorMod(index + 1, spots.size());
        PlanPreview.show(player, current());
    }

    public boolean reroll(ServerPlayer player) {
        seed = RANDOM.nextLong();
        return search(player);
    }

    // ---------------------------------------------------------------- cost and building

    /** Room cost for the part's size, plus the hallways and stairwells leading to it. */
    public Cost cost(SitePlan plan) {
        Cost total = new Cost();
        for (SitePlan.PlanPart pp : plan.parts) add(total, partCost(pp.part));
        return total;
    }

    public static Cost partCost(SitePart part) {
        return switch (part.kind) {
            case HALLWAY -> Cost.of(Items.STONE_BRICKS, Math.max(4, part.sx * part.sz / 3), Items.IRON_INGOT, 1);
            case STAIRWELL -> Cost.of(Items.STONE_BRICKS, Math.max(6, part.sx * part.sz / 2), Items.LADDER, 6, Items.IRON_INGOT, 1);
            default -> {
                Cost c = new Cost();
                for (String t : part.roomTypes()) {
                    RoomType type = RoomTypes.get(t);
                    if (type == null) continue;
                    double area = Math.max(1, (part.sx - 2) * (part.sz - 2)) / 49.0;
                    add(c, type.cost().scaled(Math.max(1.0, area)));
                }
                yield c;
            }
        };
    }

    private static void add(Cost into, Cost c) {
        c.items.forEach(into::add);
    }

    /** Whether the Supply Depot alone could pay for this spot. */
    public boolean depotCanPay(ServerPlayer player, SitePlan plan) {
        return !site.rooms(Expansion.SUPPLY_DEPOT).isEmpty() && Expansion.canPay(Expansion.pool((ServerLevel) player.level(), site, true), cost(plan));
    }

    public int hallways(SitePlan plan) {
        return plan.count(SitePart.Kind.HALLWAY) + plan.count(SitePart.Kind.STAIRWELL);
    }

    /** Why the current spot can't be built now, or null. */
    public String problem() {
        SitePlan plan = current();
        if (plan == null) return error == null ? "Find a spot first." : error;
        RoomType type = type();
        if (type == null) return "Unknown room type.";
        String locked = RoomPlanner.lockedReason(site, type);
        if (locked != null) return locked;
        for (SitePlan.PlanPart pp : plan.parts) {
            for (PlacedPart other : site.parts) {
                Box b = other.removed ? null : other.box();
                if (b != null && b.intersects(pp.box)) return "Something was built there since. Find a spot again.";
            }
            for (Room r : site.rooms) {
                if (new Box(r.x1, r.y1, r.z1, r.x2, r.y2, r.z2).intersects(pp.box)) return "Something was built there since. Find a spot again.";
            }
        }
        return null;
    }

    /** Pays and starts construction. Returns an error, or null when building started. */
    public String build(ServerPlayer player) {
        String problem = problem();
        if (problem != null) return problem;
        SitePlan plan = current();
        Cost cost = cost(plan);
        if (!player.isCreative()) {
            if (cost.canAfford(player)) {
                cost.pay(player);
            } else {
                // The Supply Depot is a shared pool for auto-placed rooms (§8.3).
                var depot = Expansion.pool((ServerLevel) player.level(), site, true);
                if (!Expansion.canPay(depot, cost)) return "Not enough materials. Needs: " + String.join(", ", cost.describe());
                Expansion.pay(depot, cost);
            }
        }
        SiteGen.commit((ServerLevel) player.level(), site, plan, survey, false);
        String msg = "Construction of a " + type().name + " has begun" + (hallways(plan) > 0 ? " with " + hallways(plan) + " connecting part(s)." : ".");
        site.log(msg);
        if (!Cassie.announce(site, msg)) Cassie.broadcast(site, Component.literal(msg).withStyle(ChatFormatting.GREEN), false);
        stop(player);
        return null;
    }

    // ---------------------------------------------------------------- HUD

    /** Runs every 20 ticks: the action bar hint while a spot is shown. */
    public static void tickHud(MinecraftServer server) {
        for (Map.Entry<UUID, AutoPlace> e : SESSIONS.entrySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            AutoPlace a = e.getValue();
            if (p == null || a.current() == null || PlanPreview.shown(p) == null) continue;
            if (!p.getMainHandItem().is(dev.projectredacted.registry.ModItems.ROOM_DESIGNATOR)) continue;
            RoomType t = a.type();
            p.sendOverlayMessage(Component.literal((t == null ? "" : t.name) + " · Spot " + (a.index + 1) + " of " + a.spots.size()
                    + " · N next spot · G new spots · Use device for the build panel").withStyle(ChatFormatting.AQUA));
        }
    }

    /** Room types the site would most likely want next, for the Suggested row (§8.1). */
    public static List<RoomType> suggested(Site site) {
        List<RoomType> list = Expansion.suggested(site);
        return list.size() > 4 ? list.subList(0, 4) : list;
    }
}
