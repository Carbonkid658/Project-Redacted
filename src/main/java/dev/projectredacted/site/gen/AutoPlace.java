package dev.projectredacted.site.gen;

import dev.projectredacted.api.ScpApi;
import dev.projectredacted.api.ScpDefinition;
import dev.projectredacted.registry.ModTags;
import dev.projectredacted.room.Room;
import dev.projectredacted.room.RoomPlanner;
import dev.projectredacted.room.RoomType;
import dev.projectredacted.room.RoomTypes;
import dev.projectredacted.site.Cassie;
import dev.projectredacted.site.Site;
import dev.projectredacted.site.SiteLogic;
import dev.projectredacted.site.SiteManager;
import dev.projectredacted.util.Cost;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

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
    /** SCPs the site has or has on its way, for SCP-specific rooms (§5.3). */
    public final Set<String> scps;
    /** Index into {@link PartKit#sizes}. */
    public int size;
    public final List<SitePlan> spots = new ArrayList<>();
    public int index;
    public long seed = RANDOM.nextLong();
    public boolean nearMe;
    /** "Put it anywhere": any doorway, ignoring areas. */
    public boolean anywhere;
    public String error;
    private SiteGen.Survey survey;

    private AutoPlace(Site site, String typeId, Set<String> scps) {
        this.site = site;
        this.typeId = typeId;
        this.scps = scps;
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
        AutoPlace a = new AutoPlace(site, typeId, wantedScps((ServerLevel) player.level(), site));
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
        return PartKit.active().sizes(typeId, scps);
    }

    /**
     * The SCPs a site has or has on its way (§5.3): SCP creatures anywhere in the claim, SCP objects in its cells,
     * and SCP objects its online members are carrying in. Keys as in {@link PartKit#scpKey}.
     */
    public static Set<String> wantedScps(ServerLevel level, Site site) {
        Set<String> out = new HashSet<>();
        int x = site.minChunkX() << 4, z = site.minChunkZ() << 4, w = site.claimChunks() << 4;
        AABB claim = new AABB(x, level.getMinY(), z, x + w, level.getMaxY() + 1, z + w);
        for (Entity e : level.getEntitiesOfClass(Entity.class, claim, e -> e.getType().builtInRegistryHolder().is(ModTags.SCP_ENTITIES))) {
            ScpDefinition def = ScpApi.forEntity(e.getType());
            if (def != null) out.add(PartKit.scpKey(def));
        }
        List<ItemStack> stacks = new ArrayList<>();
        for (Room cell : site.cells()) if (level.isLoaded(cell.center())) stacks.addAll(SiteLogic.cellItems(level, cell));
        Set<UUID> people = new HashSet<>(site.members.keySet());
        if (site.director != null) people.add(site.director);
        for (UUID id : people) {
            ServerPlayer member = level.getServer().getPlayerList().getPlayer(id);
            if (member != null) for (int i = 0; i < member.getInventory().getContainerSize(); i++) stacks.add(member.getInventory().getItem(i));
        }
        for (ItemStack stack : stacks) {
            if (!stack.is(ModTags.SCP_ITEMS)) continue;
            ScpDefinition def = ScpApi.forItem(stack.getItem());
            if (def != null) out.add(PartKit.scpKey(def));
        }
        return out;
    }

    public SitePart sizePart() {
        List<SitePart> list = sizes();
        if (list.isEmpty()) return null;
        return list.get(Math.max(0, Math.min(size, list.size() - 1)));
    }

    public static String sizeLabel(SitePart p) {
        String scp = "";
        if (p.scp != null) {
            ScpDefinition def = PartKit.scpDefinition(p.scp);
            scp = " for " + (def != null && def.number != null ? def.number : p.scp.toUpperCase(Locale.ROOT).replace('_', '-'));
        }
        return p.sx + "×" + p.sz + (p.storeys() > 1 ? "×" + p.storeys() + " storeys" : "") + scp + groupLabel(p);
    }

    /** " + Observation Room" for a room that comes with companions (§5.5); optional ones say "if it fits". */
    public static String groupLabel(SitePart p) {
        StringBuilder sb = new StringBuilder();
        Set<String> seen = new HashSet<>();
        for (SitePart.Connector c : p.connectors) {
            SitePart.Link l = c.isLink() ? c.link() : null;
            if (l == null) continue;
            String name;
            if (l.roomType() != null) {
                RoomType t = RoomTypes.get(l.roomType());
                name = t == null ? l.roomType() : t.name;
            } else {
                name = l.exactId().substring(l.exactId().lastIndexOf('/') + 1);
                for (SitePart o : PartKit.active().parts) {
                    if (!l.accepts(o)) continue;
                    RoomType t = o.roomType == null ? null : RoomTypes.get(o.roomType);
                    name = t != null ? t.name : o.kind == SitePart.Kind.HALLWAY ? "its own hallway" : name;
                }
            }
            if (seen.add(name)) sb.append(" + ").append(name).append(l.required() ? "" : " (if it fits)");
        }
        return sb.toString();
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
        spots.addAll(Skeleton.spots(site, survey, typeId, part, MAX_SPOTS, anywhere));
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
            case HUB -> Cost.of(Items.STONE_BRICKS, Math.max(8, part.sx * part.sz / 3), Items.IRON_INGOT, 2);
            case GATE -> Cost.of(Items.STONE_BRICKS, Math.max(6, part.sx * part.sz / 3), Items.IRON_INGOT, 4, Items.REDSTONE, 4);
            default -> {
                // A room costs its key block (V23); a room type without one costs materials as before.
                Cost c = new Cost();
                List<String> types = new ArrayList<>();
                for (SitePart.PartRoom r : part.rooms) types.add(r.type());
                if (types.isEmpty()) types.addAll(part.roomTypes());
                for (String t : types) {
                    RoomType type = RoomTypes.get(t);
                    if (type == null) continue;
                    net.minecraft.world.level.block.Block key = type.keyBlockDef();
                    if (key != null && key.asItem() != Items.AIR) {
                        c.add(key.asItem(), 1);
                        continue;
                    }
                    double area = Math.max(1, (part.sx - 2) * (part.sz - 2)) / 49.0;
                    add(c, type.cost().scaled(Math.max(1.0, area)));
                }
                yield c;
            }
        };
    }

    /** Key blocks in a cost: they come from the player's inventory first, then the Storage Rooms and Supply Depot. */
    private static boolean isKey(Item item) {
        return item instanceof net.minecraft.world.item.BlockItem b && dev.projectredacted.room.KeyBlocks.of(b.getBlock()) != null;
    }

    /** "Key block: 1 in your inventory", "Key block: 2 in Storage Room Engineering-03", or "Missing: Bunk Bed" (§5.8). */
    public static String keyStatus(ServerPlayer player, Site site, RoomType type) {
        net.minecraft.world.level.block.Block key = type.keyBlockDef();
        if (key == null) return null;
        Item item = key.asItem();
        String name = key.getName().getString();
        if (player.isCreative()) return "Key block: " + name + " (free in creative)";
        int mine = Cost.count(player, item);
        if (mine > 0) return "Key block: " + mine + " " + name + " in your inventory";
        ServerLevel level = (ServerLevel) player.level();
        for (String t : List.of(Expansion.STORAGE_ROOM, Expansion.SUPPLY_DEPOT)) {
            for (Room r : site.rooms(t)) {
                if (!r.built) continue;
                int n = Expansion.count(Expansion.containers(level, r), item);
                if (n > 0) return "Key block: " + n + " " + name + " in " + r.displayName();
            }
        }
        return "Missing: " + name;
    }

    private static void add(Cost into, Cost c) {
        c.items.forEach(into::add);
    }

    /** Whether the Supply Depot alone could pay for this spot. */
    public boolean depotCanPay(ServerPlayer player, SitePlan plan) {
        return !site.rooms(Expansion.SUPPLY_DEPOT).isEmpty() && Expansion.canPay(Expansion.pool((ServerLevel) player.level(), site, true), cost(plan));
    }

    /** " + Observation Room" for the companions a spot actually got. */
    private String groupLabel(SitePlan plan) {
        StringBuilder sb = new StringBuilder();
        for (SitePlan.PlanPart pp : plan.parts) {
            if (pp.linkedFrom < 0 && !pp.part.hasLinks()) continue;
            for (String t : pp.part.roomTypes()) {
                RoomType rt = RoomTypes.get(t);
                if (rt != null && !t.equals(typeId)) sb.append(" + ").append(rt.name);
            }
        }
        return sb.toString();
    }

    public int hallways(SitePlan plan) {
        return plan.count(SitePart.Kind.HALLWAY) + plan.count(SitePart.Kind.STAIRWELL) + plan.count(SitePart.Kind.HUB) + plan.count(SitePart.Kind.GATE);
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
                if (b != null && Assembler.overlaps(pp.part, pp.origin, pp.rot, pp.box, other.part(), other.origin, other.rot, b)) return "Something was built there since. Find a spot again.";
            }
            for (Room r : site.rooms) {
                if (r.part >= 0) continue;
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
            // Key blocks: the player's own first, then the Storage Rooms and the Supply Depot (V23).
            Cost keys = new Cost();
            for (var e : new ArrayList<>(cost.items.entrySet())) if (isKey(e.getKey())) keys.add(e.getKey(), e.getValue());
            var stores = Expansion.pool((ServerLevel) player.level(), site, false);
            for (var e : keys.items.entrySet()) {
                int have = Cost.count(player, e.getKey()) + Expansion.count(stores, e.getKey());
                if (have < e.getValue()) return "Missing: " + new ItemStack(e.getKey()).getHoverName().getString() + ". Craft one, or keep one in a Storage Room.";
            }
            for (var e : keys.items.entrySet()) {
                int fromPlayer = Math.min(e.getValue(), Cost.count(player, e.getKey()));
                if (fromPlayer > 0) Cost.take(player, e.getKey(), fromPlayer);
                if (e.getValue() > fromPlayer) Expansion.pay(stores, Cost.of(e.getKey(), e.getValue() - fromPlayer));
                cost.items.remove(e.getKey());
            }
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
        String where = plan.area == null || anywhere ? "" : plan.newArea ? ", starting the " + Areas.displayName(site, plan.area) + " area" : " in " + Areas.displayName(site, plan.area);
        String msg = "Construction of a " + type().name + groupLabel(plan) + where + " has begun" + (hallways(plan) > 0 ? " with " + hallways(plan) + " connecting part(s)." : ".");
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
            RoomType t = a.type();
            p.sendOverlayMessage(Component.literal((t == null ? "" : t.name) + " · Spot " + (a.index + 1) + " of " + a.spots.size()
                    + " · N next spot · G new spots · R build panel").withStyle(ChatFormatting.AQUA));
        }
    }

    /** Room types the site would most likely want next, for the Suggested row (§8.1). */
    public static List<RoomType> suggested(Site site) {
        List<RoomType> list = Expansion.suggested(site);
        return list.size() > 4 ? list.subList(0, 4) : list;
    }
}
