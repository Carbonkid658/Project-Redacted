package dev.projectredacted.site.gen;

import dev.projectredacted.room.Room;
import dev.projectredacted.room.RoomPlanner;
import dev.projectredacted.room.RoomType;
import dev.projectredacted.room.RoomTypes;
import dev.projectredacted.site.Cassie;
import dev.projectredacted.site.Site;
import dev.projectredacted.staff.Assignment;
import dev.projectredacted.staff.StaffRecord;
import dev.projectredacted.staff.StaffRole;
import dev.projectredacted.util.Cost;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.*;

/**
 * Growth after Level 0 (design §8): the Suggested room, auto-expand (the site orders its own rooms when it can
 * pay for them) and the Supply Depot, whose containers are the shared pool that pays for them.
 */
public final class Expansion {
    public static final String SUPPLY_DEPOT = "projectredacted:supply_depot";
    public static final String STORAGE_ROOM = "projectredacted:storage_room";
    /** How often auto-expand looks for something to build. */
    private static final int CHECK_TICKS = 20 * 60;
    /** Items one engineer moves from Storage Rooms into the Depot per staff tick. */
    private static final int RESTOCK_PER_ENGINEER = 4;

    public static final String FAVOR = "favor:", BLOCK = "block:";

    // ---------------------------------------------------------------- suggestions (§8.1)

    /** Room types the site needs most, biggest need first: beds, power, water, breakers, cells, then anything missing. */
    public static List<RoomType> suggested(Site site) {
        List<String> ids = new ArrayList<>();
        if (site.housedStaff() >= site.beds) ids.add(RoomTypes.BARRACKS);
        if (site.powerDemand > site.powerSupply) ids.add("projectredacted:generator");
        if (site.waterDemand > site.waterSupply) ids.add("projectredacted:water_pump");
        int connected = 0;
        for (Room r : site.rooms) if (r.connected) connected++;
        if (connected >= site.connectCapacity) ids.add("projectredacted:circuit_breaker");
        if (site.cells().size() <= site.scpCount) ids.add("projectredacted:light_cell");
        List<RoomType> out = new ArrayList<>();
        for (String id : ids) {
            RoomType t = RoomTypes.get(id);
            if (t != null && available(site, t) && !out.contains(t)) out.add(t);
        }
        List<RoomType> missing = new ArrayList<>();
        for (RoomType t : RoomTypes.all()) {
            if (out.contains(t) || !site.rooms(t.id).isEmpty() || !available(site, t)) continue;
            missing.add(t);
        }
        missing.sort(Comparator.comparingInt((RoomType t) -> t.requiredLevel).thenComparing(t -> t.powerDraw > 0 ? 1 : 0).thenComparing(t -> t.name));
        out.addAll(missing);
        return out;
    }

    private static boolean available(Site site, RoomType t) {
        if (t.id.equals(RoomTypes.ENTRANCE) || t.id.equals(RoomTypes.DIRECTORS_OFFICE)) return false;
        // The player decides where emergency exits go (D12).
        if (t.id.equals(RoomTypes.EMERGENCY_EXIT)) return false;
        return RoomPlanner.lockedReason(site, t) == null;
    }

    // ---------------------------------------------------------------- priorities (§8.2)

    public static String priority(Site site, String typeId) {
        if (site.expandPriority.contains(BLOCK + typeId)) return "never";
        if (site.expandPriority.contains(FAVOR + typeId)) return "favor";
        return "normal";
    }

    public static void setPriority(Site site, String typeId, String priority) {
        site.expandPriority.remove(BLOCK + typeId);
        site.expandPriority.remove(FAVOR + typeId);
        if (priority.equals("never")) site.expandPriority.add(BLOCK + typeId);
        if (priority.equals("favor")) site.expandPriority.add(FAVOR + typeId);
    }

    /** What auto-expand would order next, or null. Favored types jump the queue; blocked ones never come up. */
    public static RoomType next(Site site) {
        List<RoomType> list = suggested(site);
        list.removeIf(t -> priority(site, t.id).equals("never"));
        if (list.isEmpty()) return null;
        RoomType first = list.get(0);
        // A real need (beds, power...) comes first; otherwise a favored type before the rest.
        boolean need = suggestedNeeds(site).contains(first.id);
        if (!need) for (RoomType t : list) if (priority(site, t.id).equals("favor")) return t;
        return first;
    }

    private static Set<String> suggestedNeeds(Site site) {
        Set<String> needs = new HashSet<>();
        if (site.housedStaff() >= site.beds) needs.add(RoomTypes.BARRACKS);
        if (site.powerDemand > site.powerSupply) needs.add("projectredacted:generator");
        if (site.waterDemand > site.waterSupply) needs.add("projectredacted:water_pump");
        return needs;
    }

    // ---------------------------------------------------------------- the pool (§8.3, D8)

    /** Containers that pay for auto-expand: the Supply Depot first, then Storage Rooms. */
    public static List<Container> pool(ServerLevel level, Site site, boolean depotOnly) {
        List<Container> out = new ArrayList<>();
        for (String type : depotOnly ? List.of(SUPPLY_DEPOT) : List.of(SUPPLY_DEPOT, STORAGE_ROOM)) {
            for (Room r : site.rooms(type)) {
                if (!r.built) continue;
                out.addAll(containers(level, r));
            }
        }
        return out;
    }

    public static List<Container> containers(ServerLevel level, Room r) {
        List<Container> out = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(r.x1 + 1, r.y1 + 1, r.z1 + 1, r.x2 - 1, r.y2 - 1, r.z2 - 1)) {
            if (!level.isLoaded(pos)) continue;
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof Container c && !out.contains(c)) out.add(c);
        }
        return out;
    }

    public static int count(List<Container> pool, Item item) {
        int n = 0;
        for (Container c : pool)
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack s = c.getItem(i);
                if (s.is(item)) n += s.getCount();
            }
        return n;
    }

    public static boolean canPay(List<Container> pool, Cost cost) {
        for (Map.Entry<Item, Integer> e : cost.items.entrySet()) if (count(pool, e.getKey()) < e.getValue()) return false;
        return true;
    }

    /** Takes the cost out of the pool; call {@link #canPay} first. */
    public static void pay(List<Container> pool, Cost cost) {
        for (Map.Entry<Item, Integer> e : cost.items.entrySet()) {
            int left = e.getValue();
            for (Container c : pool) {
                for (int i = 0; i < c.getContainerSize() && left > 0; i++) {
                    ItemStack s = c.getItem(i);
                    if (!s.is(e.getKey())) continue;
                    int take = Math.min(left, s.getCount());
                    s.shrink(take);
                    left -= take;
                    c.setChanged();
                }
                if (left <= 0) break;
            }
        }
    }

    // ---------------------------------------------------------------- auto-expand (§8.2)

    /** Called every tick; does its work once a minute while auto-expand is on. */
    public static void tick(ServerLevel level, Site site) {
        if (!site.autoExpand || !site.generated || !site.mainframeOnline()) return;
        long now = level.getGameTime();
        if (now < site.nextExpandCheck) return;
        site.nextExpandCheck = now + CHECK_TICKS;
        for (PlacedPart p : site.parts) {
            if (!p.built && !p.removed) {
                site.expandStatus = "Waiting for construction to finish.";
                return; // one order at a time
            }
        }
        RoomType type = next(site);
        if (type == null) {
            site.expandStatus = "Nothing needed right now.";
            return;
        }
        List<SitePart> sizes = PartKit.active().sizes(type.id);
        if (sizes.isEmpty()) return;
        SiteGen.Survey survey = SiteGen.survey(level, site, level.getRandom().nextLong());
        survey.input().exitOk = (p, d) -> true;
        SitePlan plan = SiteGen.addRoom(site, survey, type.id, sizes.get(0));
        if (!plan.ok()) {
            site.expandStatus = plan.error;
            return;
        }
        if (plan.overwrites > 0) {
            // Never replaces player blocks; tries again later with another spot.
            site.expandStatus = "The only spot found for a " + type.name + " has player-built blocks in the way.";
            return;
        }
        Cost cost = new Cost();
        for (SitePlan.PlanPart pp : plan.parts) AutoPlace.partCost(pp.part).items.forEach(cost::add);
        List<Container> pool = pool(level, site, false);
        if (!canPay(pool, cost)) {
            List<String> short_ = new ArrayList<>();
            for (Map.Entry<Item, Integer> e : cost.items.entrySet()) {
                int have = count(pool, e.getKey());
                if (have < e.getValue()) short_.add(dev.projectredacted.gui.Ui.name(e.getKey()) + " " + have + "/" + e.getValue());
            }
            site.expandStatus = "Can't pay for a " + type.name + " yet: " + String.join(", ", short_) + ".";
            return;
        }
        site.expandStatus = null;
        pay(pool, cost);
        SiteGen.commit(level, site, plan, survey, false);
        Room room = null;
        for (int n : site.parts.get(site.parts.size() - plan.parts.size() + indexOfRoom(plan, type.id)).rooms) room = site.room(n);
        String msg = "Expansion ordered: " + (room != null ? room.displayName() : type.name) + ".";
        if (!Cassie.announce(site, msg)) Cassie.broadcast(site, Component.literal(msg).withStyle(ChatFormatting.AQUA), false);
    }

    private static int indexOfRoom(SitePlan plan, String type) {
        for (int i = 0; i < plan.parts.size(); i++) if (plan.parts.get(i).part.roomTypes().contains(type)) return i;
        for (int i = 0; i < plan.parts.size(); i++) if (plan.parts.get(i).part.kind == SitePart.Kind.ROOM) return i;
        return plan.parts.size() - 1;
    }

    // ---------------------------------------------------------------- Depot restocking (§8.3)

    /** Engineers assigned to a powered Depot move materials from Storage Rooms into it. Runs every staff tick. */
    public static void restock(ServerLevel level, Site site) {
        for (Room depot : site.rooms(SUPPLY_DEPOT)) {
            if (!depot.working()) continue;
            int engineers = 0;
            for (StaffRecord s : site.staff.values()) {
                if (s.role == StaffRole.ENGINEER && s.assignment != null && s.assignment.kind == Assignment.Kind.ROOM && s.assignment.room == depot.number) engineers++;
            }
            if (engineers == 0) continue;
            List<Container> into = containers(level, depot);
            if (into.isEmpty()) continue;
            int budget = engineers * RESTOCK_PER_ENGINEER;
            for (Room store : site.rooms(STORAGE_ROOM)) {
                if (!store.built) continue;
                for (Container from : containers(level, store)) {
                    for (int i = 0; i < from.getContainerSize() && budget > 0; i++) {
                        ItemStack s = from.getItem(i);
                        if (s.isEmpty()) continue;
                        int moved = insert(into, s.copyWithCount(Math.min(budget, s.getCount())));
                        if (moved > 0) {
                            s.shrink(moved);
                            from.setChanged();
                            budget -= moved;
                        }
                    }
                    if (budget <= 0) return;
                }
            }
        }
    }

    /** Puts as much of the stack as fits; returns how many went in. */
    private static int insert(List<Container> into, ItemStack stack) {
        int start = stack.getCount();
        for (Container c : into) {
            for (int i = 0; i < c.getContainerSize() && !stack.isEmpty(); i++) {
                ItemStack slot = c.getItem(i);
                if (slot.isEmpty()) {
                    c.setItem(i, stack.copy());
                    stack.setCount(0);
                } else if (ItemStack.isSameItemSameComponents(slot, stack) && slot.getCount() < slot.getMaxStackSize()) {
                    int add = Math.min(stack.getCount(), slot.getMaxStackSize() - slot.getCount());
                    slot.grow(add);
                    stack.shrink(add);
                }
            }
            c.setChanged();
            if (stack.isEmpty()) break;
        }
        return start - stack.getCount();
    }

    private Expansion() {}
}
