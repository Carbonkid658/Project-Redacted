package dev.projectredacted.gui.screen;

import dev.projectredacted.gui.*;
import dev.projectredacted.raid.Faction;
import dev.projectredacted.raid.RaidManager;
import dev.projectredacted.registry.ModBlocks;
import dev.projectredacted.registry.ModItems;
import dev.projectredacted.room.Room;
import dev.projectredacted.room.RoomType;
import dev.projectredacted.room.RoomTypes;
import dev.projectredacted.site.*;
import dev.projectredacted.staff.Assignment;
import dev.projectredacted.staff.StaffRecord;
import dev.projectredacted.staff.StaffRole;
import dev.projectredacted.util.Cost;
import dev.projectredacted.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.*;

/**
 * The Mainframe Terminal and Portable Tablet (GUI design §4): Overview, Map, Rooms, Staff, Upgrades, Personnel,
 * Security, Site and Log tabs. While the Mainframe is offline it stays open read-only.
 */
public class TerminalScreen extends GuiScreen {
    private static final String[] JOBS = {"Personnel", "Security", "Researcher", "Engineer", "Medical", "Janitor", "Administration"};

    private final Site site;
    /** The terminal block, or null on the Portable Tablet. */
    private final BlockPos at;

    private String tab = "overview";
    private int room = -1;
    private String roomFilter = "All", roomSort = "Number";
    private UUID staff;
    private String staffFilter = "All";
    private Upgrade.Category upgradeCategory = Upgrade.Category.FACILITY;
    private Upgrade upgrade;
    private boolean useTome = true, useDocs = true;
    private UUID member;
    private Integer chunkX, chunkZ;
    private int floor;
    private int cardLevel = 1;
    private String logFilter = "All";
    /** Field Ops tab: the team being put together and the trip length (NPC revamp §4.5). */
    private final Set<UUID> team = new LinkedHashSet<>();
    private dev.projectredacted.staff.Expedition.Length tripLength = dev.projectredacted.staff.Expedition.Length.SHORT;

    public TerminalScreen(Site site, BlockPos at) {
        this.site = site;
        this.at = at;
    }

    public void showStaff(UUID id) {
        tab = "staff";
        staff = id;
    }

    @Override
    public String id() {
        return "terminal";
    }

    @Override
    public boolean live() {
        return true;
    }

    @Override
    public String invalid(ServerPlayer player) {
        if (SiteManager.byId(site.id) != site) return "The site no longer exists.";
        if (!site.canUseTerminal(player)) return "ACCESS DENIED.";
        if (at != null && (!Ui.near(player, at) || !player.level().getBlockState(at).is(ModBlocks.SITE_DIRECTOR_COMPUTER))) return "You stepped away from the terminal.";
        return null;
    }

    private boolean offline() {
        return !site.mainframeOnline();
    }

    // ================================================================ view

    @Override
    public View build(ServerPlayer p) {
        boolean director = site.isDirector(p);
        View v = new View("MAINFRAME TERMINAL · " + site.name()).badge(Ui.clr(site, p)).tag(at == null ? "REMOTE LINK" : null);
        v.tab("overview", "Overview", ModBlocks.MAINFRAME, null);
        v.tab("map", "Map", Items.FILLED_MAP, null);
        v.tab("rooms", "Rooms", Items.BRICKS, null);
        v.tab("staff", "Staff", Items.PLAYER_HEAD, null);
        v.tab("upgrades", "Upgrades", Items.ANVIL, null);
        v.tab("personnel", "Personnel", Items.NAME_TAG, director ? null : "Director only");
        v.tab("security", "Security", Items.SHIELD, null);
        v.tab("field", "Field Ops", Items.COMPASS, site.hasUpgrade(Upgrade.FIELD_OPERATIONS) ? null : "Needs the Field Operations upgrade");
        v.tab("site", "Site", Items.GRASS_BLOCK, null);
        v.tab("log", "Log", Items.WRITABLE_BOOK, null);
        if (!director && tab.equals("personnel")) tab = "overview";
        if (tab.equals("field") && !site.hasUpgrade(Upgrade.FIELD_OPERATIONS)) tab = "overview";
        v.activeTab(tab);
        if (site.raid != null) {
            List<String> names = new ArrayList<>();
            for (Faction f : site.raid.factions) names.add(f.displayName);
            v.banner("⚠ RAID IN PROGRESS: " + String.join(", ", names), "red");
        } else if (offline()) {
            v.banner(at == null ? "LINK LOST: the Mainframe is offline. Read-only." : "MAINFRAME OFFLINE: read-only until it's repaired.", "amber");
        }
        switch (tab) {
            case "map" -> map(v, p);
            case "rooms" -> rooms(v, p);
            case "staff" -> staff(v, p);
            case "upgrades" -> upgrades(v, p);
            case "personnel" -> personnel(v, p);
            case "security" -> security(v, p);
            case "field" -> field(v, p);
            case "site" -> siteTab(v, p);
            case "log" -> log(v);
            default -> overview(v, p);
        }
        return v;
    }

    private void overview(View v, ServerPlayer p) {
        v.add(El.heading("FACILITY LEVEL " + site.level + " · " + site.claimChunks() + "×" + site.claimChunks() + " chunks"));
        v.add(El.row(
                El.text("Mainframe ● " + (site.mainframeOnline() ? "ONLINE" : "OFFLINE"), site.mainframeOnline() ? "green" : "red"),
                El.text("C.A.S.S.I.E. ● " + (site.cassie == null ? "NOT INSTALLED" : site.cassieOperational() ? "ONLINE" : "OFFLINE"),
                        site.cassie == null ? "gray" : site.cassieOperational() ? "green" : "red")));
        v.add(El.usage("Power", site.powerDemand, site.powerSupply));
        v.add(El.usage("Water", site.waterDemand, site.waterSupply));
        v.add(El.usage("Beds", site.housedStaff(), site.beds).set("text", site.housedStaff() + " / " + site.beds + " staff"));
        double risk = RaidManager.chancePerMinute(site) * 100;
        v.add(El.row(El.text("SCPs contained: " + site.scpCount),
                El.bar("Raid risk", Math.min(risk, 5), 5, risk >= 2 ? "red" : risk >= 1 ? "amber" : "green").set("text", String.format("%.1f%%/min", risk))));
        if (site.level < 3) {
            String missing = FacilityLevels.missing(site, site.level + 1);
            v.add(El.row(El.text("NEXT LEVEL: " + (missing == null ? "requirements met" : missing.toLowerCase()), missing == null ? "green" : "amber"),
                    El.button("Upgrades ▸", "goto_upgrade").width(70)));
        }
        List<El> steps = checklist(site);
        if (!steps.isEmpty()) v.add(El.box("Getting started", steps));
        boolean nothing = site.coinsOwed <= 0 && site.documentsOwed <= 0;
        v.add(El.box("Waiting to collect", El.row(
                El.text(site.coinsOwed + " coins · " + site.documentsOwed + " Research Documents"),
                El.button("Collect", "collect").style("confirm").width(60).disabled(nothing ? "Nothing to collect." : null))));
    }

    /** The site-founded checklist (GUI design §11.1); empty once every step is done. */
    static List<El> checklist(Site site) {
        boolean[] done = {site.terminal != null, site.firstEntrance() != null, !site.rooms("projectredacted:barracks").isEmpty()
                || !site.rooms("projectredacted:single_bedroom").isEmpty(), !site.staff.isEmpty()};
        String[] steps = {"Place the Site Director's Computer in a closed room", "Build an Entrance (a closed room with a Reception Desk)",
                "Build housing (a closed room with a Bunk Bed or Personal Locker)", "Recruit staff from Foundation Bunkers"};
        List<El> out = new ArrayList<>();
        boolean all = true;
        for (int i = 0; i < steps.length; i++) {
            out.add(El.text((done[i] ? "✔ " : "○ ") + steps[i], done[i] ? "green" : "white"));
            all &= done[i];
        }
        return all ? List.of() : out;
    }

    // ---------------------------------------------------------------- map

    private List<Integer> floors() {
        TreeSet<Integer> ys = new TreeSet<>();
        for (Room r : site.rooms) ys.add(r.y1);
        return new ArrayList<>(ys);
    }

    private void map(View v, ServerPlayer p) {
        List<Integer> floors = floors();
        floor = Math.max(0, Math.min(floor, floors.size() - 1));
        boolean multi = floors.size() > 1;
        Integer y = multi ? floors.get(floor) : null;
        int size = site.claimChunks(), minX = site.minChunkX(), minZ = site.minChunkZ();
        int coreX = site.mainframe.getX() >> 4, coreZ = site.mainframe.getZ() >> 4;
        int meX = p.blockPosition().getX() >> 4, meZ = p.blockPosition().getZ() >> 4;
        El.Grid grid = El.grid(size, "select_chunk");
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                int cx = minX + x, cz = minZ + z;
                List<Room> here = roomsInChunk(cx, cz, y);
                Room main = null;
                for (Room r : here) if (main == null || r.typeDef() != null && r.typeDef().isContainment()) main = r;
                String label = "", color = "dark";
                if (main != null) {
                    RoomType t = main.typeDef();
                    label = cellLabel(main);
                    color = Ui.categoryColor(t == null ? "" : t.category);
                } else if (cx == coreX && cz == coreZ) {
                    label = "MF";
                    color = "gold";
                }
                var cell = grid.cell(cx + "," + cz, label, color);
                boolean powered = false;
                List<String> tip = new ArrayList<>();
                tip.add("Chunk " + cx + ", " + cz);
                for (Room r : here) {
                    tip.add((r.poweredNow ? "⚡ " : "") + r.displayName() + (r.built ? "" : " (building)"));
                    powered |= r.poweredNow && r.typeDef() != null && r.typeDef().powerDraw > 0;
                }
                if (here.isEmpty()) tip.add(cx == coreX && cz == coreZ ? "Mainframe" : "Empty");
                if (powered) cell.addProperty("powered", true);
                if (cx == meX && cz == meZ && p.level() == SiteManager.overworld()) cell.addProperty("marker", true);
                if (chunkX != null && chunkX == cx && chunkZ == cz) cell.addProperty("selected", true);
                com.google.gson.JsonArray a = new com.google.gson.JsonArray();
                tip.forEach(a::add);
                cell.add("tip", a);
            }
        }
        List<El> left = new ArrayList<>();
        if (multi) {
            left.add(El.row(El.button("◂", "floor").arg("d", -1).width(16).disabled(floor == 0 ? "Lowest floor." : null),
                    El.center("Floor " + (floor + 1) + " of " + floors.size() + " (Y " + y + ")", "white"),
                    El.button("▸", "floor").arg("d", 1).width(16).disabled(floor == floors.size() - 1 ? "Highest floor." : null)));
        }
        left.add(grid);
        List<El> right = new ArrayList<>();
        if (chunkX == null) {
            right.add(El.text("Click a chunk to see its rooms.", "gray"));
        } else {
            right.add(El.heading("Chunk (" + chunkX + ", " + chunkZ + ")"));
            List<Room> here = roomsInChunk(chunkX, chunkZ, y);
            if (here.isEmpty()) right.add(El.text("No rooms here.", "gray"));
            for (Room r : here) {
                right.add(El.row(El.text((r.poweredNow ? "⚡ " : "• ") + r.displayName()),
                        El.button("Open ▸", "open_room").arg("id", r.number).width(44)));
            }
        }
        right.add(El.space(4));
        right.add(El.badges("Core", "gold", "Research", "aqua", "Containment", "purple", "Engineering", "amber", "Housing", "teal",
                "Common", "green", "Medical", "pink", "Offices", "blue", "Security", "red"));
        right.add(El.text("▲ marks where you are.", "gray"));
        v.add(El.cols(El.col(0.5, left), El.col(0.5, right)));
    }

    private List<Room> roomsInChunk(int cx, int cz, Integer y) {
        List<Room> list = new ArrayList<>();
        for (Room r : site.rooms) {
            BlockPos c = r.center();
            if ((c.getX() >> 4) == cx && (c.getZ() >> 4) == cz && (y == null || r.y1 == y)) list.add(r);
        }
        return list;
    }

    private static String cellLabel(Room r) {
        RoomType t = r.typeDef();
        if (t != null && t.isContainment() && !r.scpLabel.isEmpty()) {
            String digits = r.scpLabel.replaceAll("[^0-9]", "");
            if (!digits.isEmpty()) return digits.length() > 4 ? digits.substring(0, 4) : digits;
        }
        String name = t == null ? r.type : t.name;
        String[] words = name.split("[ '-]+");
        if (words.length >= 2) return ("" + words[0].charAt(0) + words[1].charAt(0)).toUpperCase();
        return name.length() >= 2 ? name.substring(0, 1).toUpperCase() + name.charAt(1) : name;
    }

    // ---------------------------------------------------------------- rooms

    static String status(Room r) {
        RoomType t = r.typeDef();
        if (t == null) return "Unknown room type";
        if (!r.built) return "Under construction";
        if (r.integrity <= 0) return "Destroyed: needs repair";
        if (t.powerDraw > 0 && !r.connected) return "Dormant: not on the Circuit Breaker";
        if (t.powerDraw > 0 && !r.poweredNow) return "No power";
        if (t.waterDraw > 0 && !r.wateredNow) return "No water";
        return "Working";
    }

    static String statusColor(Room r) {
        String s = status(r);
        return s.equals("Working") ? "green" : s.startsWith("Destroyed") || s.startsWith("Unknown") ? "red" : "amber";
    }

    private boolean matchesFilter(Room r) {
        String s = status(r);
        return switch (roomFilter) {
            case "Working" -> s.equals("Working");
            case "Dormant" -> s.startsWith("Dormant") || s.startsWith("No ");
            case "Damaged" -> r.integrity < 100;
            case "Building" -> !r.built;
            default -> true;
        };
    }

    private void rooms(View v, ServerPlayer p) {
        List<Room> list = new ArrayList<>();
        for (Room r : site.rooms) if (matchesFilter(r)) list.add(r);
        Comparator<Room> order = switch (roomSort) {
            case "Name" -> Comparator.comparing(Room::displayName);
            case "Type" -> Comparator.comparing((Room r) -> r.typeDef() == null ? "" : r.typeDef().category).thenComparingInt(r -> r.number);
            default -> Comparator.comparingInt(r -> r.number);
        };
        list.sort(order);
        El.ListEl rows = El.list("rooms", "select_room").searchable().empty("No rooms match.").fill();
        for (Room r : list) {
            RoomType t = r.typeDef();
            rows.add(String.valueOf(r.number), r.displayName()).icon(t != null ? t.iconItem() : Items.BARRIER)
                    .sub(status(r), statusColor(r)).badge(r.poweredNow && t != null && t.powerDraw > 0 ? "⚡" : null, "amber")
                    .selected(r.number == room).search(t == null ? "" : t.category);
        }
        List<El> left = List.of(
                El.chips("room_filter", roomFilter, List.of("All", "Working", "Dormant", "Damaged")),
                El.chips("room_sort", roomSort, "Number", "No.", "Name", "Name", "Type", "Type"),
                rows);
        v.add(El.cols(El.col(0.46, left), El.col(0.54, roomDetail(p))));
    }

    private List<El> roomDetail(ServerPlayer p) {
        Room r = site.room(room);
        if (r == null) return List.of(El.text("Select a room.", "gray"));
        RoomType t = r.typeDef();
        boolean canEdit = site.canBuild(p) && !offline();
        List<El> out = new ArrayList<>();
        out.add(El.heading(r.displayName()));
        out.add(El.text(r.label() + (r.area != null ? " (#" + r.number + ", " + dev.projectredacted.site.gen.Areas.displayName(site, r.area) + " area)" : "") + " · " + (t == null ? "?" : t.category) + " · " + (r.x2 - r.x1 + 1) + "×" + (r.z2 - r.z1 + 1) + "×" + (r.y2 - r.y1 + 1), "gray"));
        out.add(El.text("From " + r.x1 + " " + r.y1 + " " + r.z1 + " to " + r.x2 + " " + r.y2 + " " + r.z2, "gray"));
        out.add(El.text(status(r), statusColor(r)));
        out.add(El.need("Integrity", r.integrity));
        if (t != null && t.isContainment()) {
            out.add(El.row(El.text("Contains: " + (r.scpLabel.isEmpty() ? "nothing" : r.scpLabel)),
                    El.button("Clear label", "clear_label").width(60).disabled(r.scpLabel.isEmpty() ? "Nothing to clear." : canEdit ? null : "You need build rights.")
                            .tip("It's set again automatically when", "an SCP is detected in the cell.")));
        }
        if (t != null && t.powerDraw > 0) {
            El toggle = El.toggle("Power (draws " + t.powerDraw + ")", r.connected, "power");
            if (!canEdit) toggle.set("disabled", "You need build rights.");
            else if (!r.connected && connected() >= site.connectCapacity) toggle.set("disabled", "No free breaker slots. Build another Circuit Breaker room.");
            out.add(toggle);
        }
        out.add(El.input("rename", "New name", El.button("Rename", "rename").disabled(canEdit ? null : "You need build rights.")));
        ServerLevel level = SiteManager.overworld();
        boolean scpInside = level != null && SiteLogic.hasScp(level, r);
        String cantDemolish = !canEdit ? "You need build rights." : scpInside ? "An SCP is inside." : null;
        List<Room> companions = site.companions(r);
        if (!companions.isEmpty()) {
            // Companion rooms (v4 §5.5): removing them too is the first choice.
            List<String> names = new ArrayList<>();
            for (Room c : companions) names.add(c.displayName());
            out.add(El.button("Unregister with " + String.join(", ", names), "demolish_group").style("danger").disabled(cantDemolish)
                    .confirm("Stop " + r.displayName() + " and " + String.join(", ", names) + " being rooms? Their blocks stay."));
            out.add(El.button("Unregister only this room", "demolish").style("danger").disabled(cantDemolish)
                    .confirm("Stop " + r.displayName() + " being a room and keep " + String.join(", ", names) + "? Its blocks stay."));
        } else {
            out.add(El.button("Unregister", "demolish").style("danger").disabled(cantDemolish)
                    .confirm("Stop " + r.displayName() + " being a room? Its blocks stay; place the key block again to re-register it."));
        }
        return out;
    }

    private int connected() {
        int n = 0;
        for (Room r : site.rooms) if (r.connected) n++;
        return n;
    }

    // ---------------------------------------------------------------- staff

    private boolean matchesRole(StaffRecord s) {
        return switch (staffFilter) {
            case "All" -> true;
            case "D-Class" -> s.role == StaffRole.DCLASS;
            default -> s.role.displayName.equals(staffFilter);
        };
    }

    private void staff(View v, ServerPlayer p) {
        El.ListEl rows = El.list("staff", "select_staff").searchable().empty("No staff match.").fill();
        for (StaffRecord s : site.staff.values()) {
            if (!matchesRole(s)) continue;
            boolean noBed = s.bedIndex < 0 && s.role != StaffRole.DCLASS;
            boolean warn = noBed || s.morale < 30 || s.hunger < 20 || s.energy < 20;
            rows.add(s.id.toString(), s.fullName()).icon(s.role.icon()).color(s.named ? "gold" : "white")
                    .sub(s.role.displayName + " · Lv " + s.level + " · CLR " + s.clearance, "gray")
                    .badge(warn ? "⚠" : null, "red").warn(warn).selected(s.id.equals(staff)).search(s.role.displayName)
                    .tip(warn ? List.of(noBed ? "No bed" : "Low morale, food or rest") : List.of());
        }
        List<El> left = List.of(El.chips("staff_filter", staffFilter, List.of("All", "Researcher", "Security", "Engineer", "Janitor", "Administration", "Medical", "D-Class")), rows);
        StaffRecord s = staff == null ? null : site.staff.get(staff);
        List<El> right = s == null ? List.of(El.text("Select a staff member.", "gray"))
                : StaffViews.card(site, s, p, StaffViews.Buttons.MANAGE);
        v.add(El.cols(El.col(0.46, left), El.col(0.54, right)));
    }

    // ---------------------------------------------------------------- upgrades

    private void upgrades(View v, ServerPlayer p) {
        String[] cats = new String[Upgrade.Category.values().length * 2];
        for (Upgrade.Category c : Upgrade.Category.values()) {
            cats[c.ordinal() * 2] = c.name();
            cats[c.ordinal() * 2 + 1] = c.displayName;
        }
        v.add(El.chips("upgrade_category", upgradeCategory.name(), cats));
        long now = p.level().getGameTime();
        El.ListEl rows = El.list("upgrades", "select_upgrade").fill();
        for (Upgrade u : Upgrade.values()) {
            if (u.category != upgradeCategory) continue;
            String blocker = u.blocker(site);
            El.Row row = rows.add(u.name(), u.displayName).icon(u.icon()).selected(u == upgrade);
            if (site.hasUpgrade(u)) row.title("✔ " + u.displayName).color("green").sub("Complete", "green");
            else if (site.pendingUpgrades.containsKey(u)) {
                long left = Math.max(0, site.pendingUpgrades.get(u) - now);
                row.title("⌛ " + u.displayName).sub(Ui.duration(left) + " left", "blue").progress(1 - left / (double) Math.max(1, u.ticks(site)));
            } else if (blocker != null) row.dim(true).badge("LOCKED", "gray").sub(blocker, "gray").tip(blocker);
            else row.sub("Available", "aqua");
        }
        v.add(El.cols(El.col(0.42, List.of(rows)), El.col(0.58, upgradeDetail(p, now))));
    }

    private List<El> upgradeDetail(ServerPlayer p, long now) {
        Upgrade u = upgrade;
        if (u == null || u.category != upgradeCategory) return List.of(El.text("Select an upgrade.", "gray"));
        List<El> out = new ArrayList<>();
        out.add(El.heading(u.displayName.toUpperCase()));
        out.add(El.text(u.description, "gray"));
        if (site.hasUpgrade(u)) {
            out.add(El.text("✔ Complete", "green"));
            return out;
        }
        if (site.pendingUpgrades.containsKey(u)) {
            long left = Math.max(0, site.pendingUpgrades.get(u) - now);
            out.add(El.progress("Progress", 1 - left / (double) Math.max(1, u.ticks(site)), Ui.duration(left) + " left"));
            return out;
        }
        Purchase buy = purchase(p, u);
        List<El.CostLine> lines = new ArrayList<>(Ui.costLines(p, buy.cost));
        if (buy.dollars > 0) lines.add(new El.CostLine(ModItems.DOLLAR, "Dollars", p.isCreative() ? buy.dollars : Ui.have(p, ModItems.DOLLAR), buy.dollars));
        out.add(El.text("Cost:", "white"));
        out.add(El.cost(lines));
        out.add(El.text("Time: " + Ui.duration(buy.ticks), "white"));
        int tomes = Ui.have(p, ModItems.LIBRARY_TOME);
        El tome = El.checkbox("Library Tome: −25% materials (have " + tomes + ")", useTome, "use_tome");
        if (tomes == 0) tome.set("disabled", "You aren't carrying a Library Tome.");
        out.add(tome);
        if (u.category == Upgrade.Category.RESEARCH) {
            int docs = Math.min(4, Ui.have(p, ModItems.RESEARCH_DOCUMENT));
            El doc = El.checkbox("Research Documents ×" + docs + ": −" + docs * 20 + "% time", useDocs, "use_docs");
            if (docs == 0) doc.set("disabled", "You aren't carrying Research Documents.");
            out.add(doc);
        }
        out.add(El.button("Start upgrade", "start_upgrade").style("confirm").disabled(buyBlocker(p, u, buy)));
        return out;
    }

    private record Purchase(Cost cost, int dollars, int ticks, boolean tome, int docs) {}

    private Purchase purchase(ServerPlayer p, Upgrade u) {
        Cost cost = u.cost(site);
        boolean tome = useTome && Ui.have(p, ModItems.LIBRARY_TOME) > 0 && !p.isCreative();
        if (tome) cost = cost.scaled(0.75);
        int ticks = u.ticks(site);
        int docs = 0;
        if (u.category == Upgrade.Category.RESEARCH && useDocs && !p.isCreative()) {
            docs = Math.min(4, Ui.have(p, ModItems.RESEARCH_DOCUMENT));
            ticks = (int) (ticks * (1 - 0.2 * docs));
        }
        if (p.isCreative()) ticks = 20;
        return new Purchase(cost, u.dollarCost(site), ticks, tome, docs);
    }

    private String buyBlocker(ServerPlayer p, Upgrade u, Purchase buy) {
        if (offline()) return "The Mainframe is offline.";
        if (!site.isDirector(p)) return "Only the Site Director can buy upgrades.";
        String blocker = u.blocker(site);
        if (blocker != null) return blocker;
        String shortfall = Ui.shortfall(p, buy.cost);
        if (shortfall != null) return shortfall;
        if (!p.isCreative() && Ui.have(p, ModItems.DOLLAR) < buy.dollars) return "Needs $" + buy.dollars + " (you have $" + Ui.have(p, ModItems.DOLLAR) + ").";
        return null;
    }

    private void startUpgrade(ServerPlayer p, Upgrade u) {
        Purchase buy = purchase(p, u);
        String blocker = buyBlocker(p, u, buy);
        if (blocker != null) {
            Gui.toast(p, blocker, true);
            return;
        }
        if (!buy.cost.pay(p)) return;
        if (!p.isCreative()) {
            Cost.take(p, ModItems.DOLLAR, buy.dollars);
            if (buy.tome) Cost.take(p, ModItems.LIBRARY_TOME, 1);
            if (buy.docs > 0) Cost.take(p, ModItems.RESEARCH_DOCUMENT, buy.docs);
        }
        site.pendingUpgrades.put(u, p.level().getGameTime() + buy.ticks);
        site.log(u.displayName + " started by " + p.getName().getString() + ".");
        Gui.toast(p, u.displayName + " started. Ready in " + Ui.duration(buy.ticks) + ".", false);
    }

    // ---------------------------------------------------------------- personnel

    private void personnel(View v, ServerPlayer p) {
        El.ListEl rows = El.list("members", "select_member").fill();
        rows.add("director", "★ Director " + site.directorName).color("gold").sub("Clearance 5 · everything allowed", "gray").icon(Items.GOLDEN_HELMET);
        for (Map.Entry<UUID, Member> e : site.members.entrySet()) {
            Member m = e.getValue();
            rows.add(e.getKey().toString(), m.name).icon(Items.PLAYER_HEAD).sub("CLR " + m.clearance + " · " + m.job, "gray").selected(e.getKey().equals(member));
        }
        List<String> suggest = new ArrayList<>();
        for (ServerPlayer o : p.level().getServer().getPlayerList().getPlayers()) {
            if (!site.isMember(o)) suggest.add(o.getName().getString());
        }
        El invite = El.input("invite", "Player name", El.button("Invite", "invite").style("primary"));
        com.google.gson.JsonArray s = new com.google.gson.JsonArray();
        suggest.forEach(s::add);
        invite.o.add("suggest", s);
        List<El> left = List.of(rows, El.text("Invite an online player:", "gray"), invite);
        v.add(El.cols(El.col(0.46, left), El.col(0.54, memberDetail())));
    }

    private List<El> memberDetail() {
        Member m = member == null ? null : site.members.get(member);
        if (m == null) return List.of(El.text("Select a member to change their clearance, job and permissions.", "gray"));
        List<El> out = new ArrayList<>();
        out.add(El.heading(m.name));
        out.add(El.stepper("Clearance", m.clearance, 0, 4, "clearance"));
        out.add(El.text("Job:", "gray"));
        out.add(El.chips("job", m.job, List.of(JOBS)));
        out.add(El.toggle("Build", m.build, "perm_build"));
        out.add(El.toggle("Terminal", m.terminal, "perm_terminal"));
        out.add(El.toggle("Assign staff", m.assign, "perm_assign"));
        out.add(El.row(
                El.button("Remove from site", "remove_member").style("danger").confirm("Remove " + m.name + " from the site?"),
                El.button("Make Director", "transfer").typed("Hand the whole site to " + m.name + "? You become a Level 4 member. Type their name to confirm.", m.name)));
        return out;
    }

    // ---------------------------------------------------------------- security

    private void security(View v, ServerPlayer p) {
        long now = p.level().getGameTime();
        double risk = RaidManager.chancePerMinute(site) * 100;
        List<El> threat = new ArrayList<>();
        threat.add(El.bar("Raid risk", Math.min(risk, 5), 5, risk >= 2 ? "red" : risk >= 1 ? "amber" : "green").set("text", String.format("%.1f%%/min", risk)));
        if (site.raid == null && now < site.raidCooldownUntil) threat.add(El.text("Cooldown: " + Ui.duration(site.raidCooldownUntil - now), "green"));
        threat.add(El.text("Defections on record: " + site.defections, "gray"));
        threat.add(El.text(site.cassieOperational() ? "C.A.S.S.I.E. will warn you before a raid." : "No early warning: the alarm is the first sign.",
                site.cassieOperational() ? "green" : "amber"));
        v.add(El.box("Threat", threat));
        if (site.raid != null) {
            List<El> raid = new ArrayList<>();
            List<String> names = new ArrayList<>();
            for (Faction f : site.raid.factions) names.add(f.displayName);
            raid.add(El.text("Factions: " + String.join(", ", names), "red"));
            raid.add(El.text("Raiders remaining: " + raidersLeft(), "red"));
            if (site.hasUpgrade(Upgrade.MTF_CONTRACT) && !site.raid.mtfSent) {
                long eta = site.raid.arriveTick + RaidManager.MTF_DELAY - now;
                raid.add(El.text(site.raid.spawned ? "MTF arrive in " + Ui.duration(eta) : "MTF arrive 10 minutes after the raiders.", "blue"));
            }
            raid.add(El.segments("C.A.S.S.I.E. hack", site.cassieHackStage(), 4));
            v.add(El.box("Raid in progress", raid));
        }
        String mtf = offline() ? "The Mainframe is offline." : !site.hasUpgrade(Upgrade.MTF_CONTRACT) ? "Needs the Mobile Task Force Contract upgrade."
                : RaidManager.mtfCooldown(site, now) > 0 ? "The MTF are regrouping (" + Ui.duration(RaidManager.mtfCooldown(site, now)) + ")."
                : escaped() == 0 ? "Nothing has breached containment." : null;
        v.add(El.row(El.button("Call the MTF", "call_mtf").style("primary").disabled(mtf)
                .tip("An MTF unit brings escaped SCPs", "back to their cells.")));
        int max = Math.max(1, site.clearance(p));
        cardLevel = Math.max(1, Math.min(cardLevel, max));
        List<El> card = new ArrayList<>();
        card.add(El.stepper("Level", cardLevel, 1, max, "card_level"));
        card.add(El.cost(Ui.costLines(p, Cost.of(ModItems.PLASTIC, 2))));
        card.add(El.button("Print keycard", "print_card").style("confirm")
                .disabled(offline() ? "The Mainframe is offline." : Ui.shortfall(p, Cost.of(ModItems.PLASTIC, 2))));
        v.add(El.box("Print keycard (up to your clearance)", card));
    }

    private void field(View v, ServerPlayer p) {
        long now = p.level().getGameTime();
        team.removeIf(id -> {
            StaffRecord r = site.staff.get(id);
            return r == null || r.role != StaffRole.FIELD_AGENT || dev.projectredacted.staff.Expeditions.onTrip(site, id) != null;
        });
        if (dev.projectredacted.staff.Expeditions.operationsRoom(site) == null) {
            v.banner("Build and power a Field Operations room before sending anyone out.", "amber");
        }
        List<El> pick = new ArrayList<>();
        int agents = 0;
        for (StaffRecord r : site.staff.values()) {
            if (r.role != StaffRole.FIELD_AGENT) continue;
            agents++;
            var trip = dev.projectredacted.staff.Expeditions.onTrip(site, r.id);
            if (trip != null) {
                pick.add(El.text(r.fullName() + " · on expedition " + trip.id, "gray"));
                continue;
            }
            boolean in = team.contains(r.id);
            pick.add(El.button((in ? "☑ " : "☐ ") + r.fullName() + " · Lv " + r.level, "team_toggle").arg("id", r.id.toString())
                    .disabled(!in && team.size() >= dev.projectredacted.staff.Expeditions.MAX_TEAM ? "A team is at most 4 agents." : null));
        }
        if (agents == 0) pick.add(El.text("No Field Agents yet. Recruit them from bunkers.", "gray"));
        v.add(El.box("Team (1–4 Field Agents)", pick));

        var len = tripLength;
        int n = Math.max(1, team.size());
        double f = len.teamFactor(n);
        List<El> plan = new ArrayList<>();
        plan.add(El.chips("trip_length", len.name(), "SHORT", "Short", "MEDIUM", "Medium", "LONG", "Long"));
        plan.add(El.text("Away for about " + Ui.duration(len.ticks) + ".", "gray"));
        plan.add(El.text("Expect about " + len.documents(n) + " research document" + (len.documents(n) == 1 ? "" : "s")
                + " · " + Math.round(len.scpChance(n) * 100) + "% chance of an SCP", "green"));
        plan.add(El.text("Each agent: " + Math.round(len.injury * f * 100) + "% hurt · " + Math.round(len.death * f * 100) + "% lost", "amber"));
        String err = dev.projectredacted.staff.Expeditions.blocker(site, team);
        if (err == null && !site.canAssign(p)) err = "You can't give staff orders here.";
        plan.add(El.button("Send expedition", "send_expedition").style("confirm").disabled(err));
        v.add(El.box("Trip", plan));

        List<El> out = new ArrayList<>();
        for (var e : site.expeditions) {
            List<String> names = new ArrayList<>();
            for (UUID id : e.agents) {
                StaffRecord r = site.staff.get(id);
                if (r != null) names.add(r.name);
            }
            String when = e.away() ? "back in " + Ui.duration(Math.max(0, e.returnTick - now)) : "heading out";
            out.add(El.text("Expedition " + e.id + " · " + e.length.displayName + " · " + String.join(", ", names) + " · " + when));
        }
        if (out.isEmpty()) out.add(El.text("Nobody is out in the field.", "gray"));
        v.add(El.box("Under way", out));
    }

    private int raidersLeft() {
        ServerLevel level = SiteManager.overworld();
        if (level == null || site.raid == null) return 0;
        int n = 0;
        for (UUID id : site.raid.raiders) {
            Entity e = level.getEntity(id);
            if (e != null && e.isAlive()) n++;
        }
        return n;
    }

    private int escaped() {
        ServerLevel level = SiteManager.overworld();
        if (level == null) return 0;
        int n = 0;
        for (Map.Entry<UUID, Integer> e : site.scpHomes.entrySet()) {
            Entity scp = level.getEntity(e.getKey());
            Room cell = site.room(e.getValue());
            if (scp != null && scp.isAlive() && cell != null && !cell.contains(scp.blockPosition())) n++;
        }
        return n;
    }

    // ---------------------------------------------------------------- site

    private void siteTab(View v, ServerPlayer p) {
        boolean director = site.isDirector(p);
        v.add(El.heading("BUILDING"));
        v.add(El.text("Your facility is built by hand. Wall in a space, then place a room's key block inside it and the space becomes that room.", "white"));
        v.add(El.text("Rooms must be closed (no gaps) and inside your claim. Some room types need staff, an upgrade or a Facility Level first.", "gray"));
        El.ListEl keys = El.list("key_blocks", "").searchable().empty("No room types.");
        for (RoomType t : RoomTypes.all()) {
            String block = dev.projectredacted.room.KeyBlocks.name(t);
            String locked = dev.projectredacted.room.RoomPlanner.lockedReason(site, t);
            keys.add(t.id, t.name).icon(t.iconItem()).sub(block == null ? "No key block" : "Key block: " + block, block == null ? "gray" : "white")
                    .badge(locked != null ? "LOCKED" : null, "amber").search(t.category + " " + (block == null ? "" : block));
        }
        v.add(El.box("Room types and their key blocks", keys));
        areasSection(v, p);
        v.add(El.text("Site name: " + site.name(), "white"));
        v.add(El.input("site_name", "New site name", El.button("Rename", "rename_site").disabled(director ? null : "Only the Site Director can rename the site.")));
    }

    /** Areas: rooms, halls, clearance, names and lockdowns (v4 §2, D15). */
    private void areasSection(View v, ServerPlayer p) {
        boolean director = site.isDirector(p);
        String blocked = director ? null : "Only the Site Director can change areas.";
        v.add(El.heading("AREAS"));
        boolean any = false;
        for (var e : site.areas.entrySet()) {
            String key = e.getKey();
            var as = e.getValue();
            var def = dev.projectredacted.site.gen.Areas.get(as.kind);
            int rooms = dev.projectredacted.site.gen.Areas.rooms(site, key);
            if (rooms == 0) continue;
            any = true;
            boolean locked = site.lockdown.contains(key);
            String name = dev.projectredacted.site.gen.Areas.displayName(site, key);
            List<El> row = new ArrayList<>();
            row.add(El.text(name + " · " + rooms + " room(s)" + (locked ? " · LOCKDOWN" : ""), locked ? "red" : def == null ? "white" : def.color));
            if (locked) row.add(El.button("Lift lockdown", "lift_lockdown").arg("area", key).style("danger").disabled(blocked));
            v.add(El.row(row));
            if (director) v.add(El.input("area_name_" + key, "Rename " + name + " (blank for the default)", El.button("Rename", "rename_area").arg("area", key)));
        }
        if (!any) v.add(El.text("No areas yet. Each room you build joins the area of its kind (Living, Research, Containment...).", "gray"));
        else v.add(El.text("A breach locks down its area: the doors of its rooms shut and their readers stop until you lift it.", "gray"));
    }

    // ---------------------------------------------------------------- log

    private static String logCategory(String line) {
        String l = line.toLowerCase();
        if (l.contains("raid") || l.contains("raider") || l.contains("insurgen") || l.contains("serpent") || l.contains("coalition") || l.contains("mtf")
                || l.contains("mobile task force")) return "Raids";
        if (l.contains("scp") || l.contains("breach") || l.contains("contain") || l.contains("anomal")) return "Containment";
        if (l.contains("construction") || l.contains("built") || l.contains("demolish") || l.contains("upgrade") || l.contains("registered")
                || l.contains("repair")) return "Construction";
        if (l.contains("staff") || l.contains("joined") || l.contains("promoted") || l.contains("dismiss") || l.contains("defect") || l.contains("left the site")
                || l.contains("welcome")) return "Staff";
        return "Other";
    }

    private void log(View v) {
        v.add(El.chips("log_filter", logFilter, List.of("All", "Staff", "Raids", "Containment", "Construction")));
        El.ListEl rows = El.list("log", "").empty("Nothing logged yet.").fill();
        for (int i = site.logLines.size() - 1; i >= 0; i--) {
            String line = site.logLines.get(i);
            if (!logFilter.equals("All") && !logCategory(line).equals(logFilter)) continue;
            rows.add(String.valueOf(i), line).tip(line);
        }
        v.add(rows);
    }

    // ================================================================ actions

    @Override
    public void handle(ServerPlayer p, String action, Args a) {
        boolean director = site.isDirector(p);
        switch (action) {
            case "tab" -> tab = a.str("id", "overview");
            case "goto_upgrade" -> {
                tab = "upgrades";
                upgradeCategory = Upgrade.Category.FACILITY;
                upgrade = site.level >= 3 ? null : Upgrade.values()[Math.min(2, site.level)];
            }
            case "collect" -> {
                if (offline()) return;
                if (site.coinsOwed > 0) Cost.give(p, new ItemStack(ModItems.COIN, site.coinsOwed));
                if (site.documentsOwed > 0) Cost.give(p, new ItemStack(ModItems.RESEARCH_DOCUMENT, site.documentsOwed));
                if (site.coinsOwed + site.documentsOwed > 0) Gui.toast(p, "Collected.", false);
                site.coinsOwed = 0;
                site.documentsOwed = 0;
            }
            // map
            case "select_chunk" -> {
                String[] xz = a.str("id").split(",");
                try {
                    chunkX = Integer.parseInt(xz[0]);
                    chunkZ = Integer.parseInt(xz[1]);
                } catch (RuntimeException e) {
                    chunkX = chunkZ = null;
                }
            }
            case "floor" -> floor += a.num("d", 0);
            case "open_room" -> {
                tab = "rooms";
                room = a.num("id", -1);
                roomFilter = "All";
            }
            // rooms
            case "select_room" -> room = parseInt(a.str("id"), -1);
            case "room_filter" -> roomFilter = a.str("id", "All");
            case "room_sort" -> roomSort = a.str("id", "Number");
            case "power", "clear_label", "rename", "demolish", "demolish_group" -> roomAction(p, action, a);
            // staff
            case "select_staff" -> staff = parseUuid(a.str("id"));
            case "staff_filter" -> staffFilter = a.str("id", "All");
            // upgrades
            case "upgrade_category" -> {
                try {
                    upgradeCategory = Upgrade.Category.valueOf(a.str("id"));
                } catch (IllegalArgumentException ignored) {
                }
            }
            case "select_upgrade" -> {
                try {
                    upgrade = Upgrade.valueOf(a.str("id"));
                } catch (IllegalArgumentException ignored) {
                }
            }
            case "use_tome" -> useTome = !useTome;
            case "use_docs" -> useDocs = !useDocs;
            case "start_upgrade" -> {
                if (upgrade != null) startUpgrade(p, upgrade);
            }
            // personnel
            case "select_member" -> member = parseUuid(a.str("id"));
            case "clearance", "job", "perm_build", "perm_terminal", "perm_assign", "remove_member", "transfer" -> {
                if (director) memberAction(p, action, a);
            }
            case "invite" -> {
                if (director) invite(p, a.text());
            }
            // security
            case "call_mtf" -> {
                if (offline()) return;
                String err = RaidManager.callMtf((ServerLevel) p.level(), site);
                Gui.toast(p, err != null ? err : "The MTF are on their way.", err != null);
            }
            case "card_level" -> cardLevel = a.num("value", cardLevel);
            // field ops
            case "team_toggle" -> {
                UUID id = parseUuid(a.str("id"));
                if (id != null && !team.remove(id) && team.size() < dev.projectredacted.staff.Expeditions.MAX_TEAM) team.add(id);
            }
            case "trip_length" -> {
                try {
                    tripLength = dev.projectredacted.staff.Expedition.Length.valueOf(a.str("id", "SHORT"));
                } catch (IllegalArgumentException ignored) {
                }
            }
            case "send_expedition" -> {
                String err = dev.projectredacted.staff.Expeditions.blocker(site, team);
                if (err == null && !site.canAssign(p)) err = "You can't give staff orders here.";
                if (err != null) {
                    Gui.toast(p, err, true);
                    return;
                }
                dev.projectredacted.staff.Expeditions.send((ServerLevel) p.level(), site, new ArrayList<>(team), tripLength);
                team.clear();
                Gui.toast(p, "The team is heading out.", false);
            }
            case "print_card" -> {
                if (offline()) return;
                int lvl = Math.max(1, Math.min(cardLevel, site.clearance(p)));
                if (!p.isCreative() && Cost.count(p, ModItems.PLASTIC) < 2) {
                    Gui.toast(p, "Printing a keycard takes 2 Plastic.", true);
                    return;
                }
                if (!p.isCreative()) Cost.take(p, ModItems.PLASTIC, 2);
                Cost.give(p, new ItemStack(ModItems.keycardFor(lvl)));
                Gui.toast(p, "Printed a Level " + lvl + " keycard.", false);
            }
            // site
            case "rename_area" -> {
                if (!director) return;
                String key = a.str("area", "");
                if (!site.areas.containsKey(key)) return;
                String name = a.text();
                site.areas.get(key).name = name.isEmpty() ? null : name.length() > 24 ? name.substring(0, 24) : name;
                dev.projectredacted.site.gen.Areas.relabel(site);
                Gui.toast(p, "Renamed to " + dev.projectredacted.site.gen.Areas.displayName(site, key) + ".", false);
            }
            case "lift_lockdown" -> {
                if (director) dev.projectredacted.site.gen.Areas.lift((ServerLevel) p.level(), site, a.str("area", ""));
            }
            case "rename_site" -> {
                String name = a.text();
                if (!director || name.isEmpty()) return;
                site.name = name.length() > 32 ? name.substring(0, 32) : name;
                site.log("The site was renamed " + site.name + ".");
                Gui.toast(p, "Renamed to " + site.name + ".", false);
            }
            case "log_filter" -> logFilter = a.str("id", "All");
            default -> {
                StaffRecord s = staff == null ? null : site.staff.get(staff);
                if (s != null && !offline()) StaffViews.handle(this, site, s, p, action);
            }
        }
    }

    private void roomAction(ServerPlayer p, String action, Args a) {
        Room r = site.room(room);
        if (r == null || offline() || !site.canBuild(p)) return;
        RoomType t = r.typeDef();
        switch (action) {
            case "power" -> {
                if (t == null || t.powerDraw <= 0) return;
                if (!r.connected && connected() >= site.connectCapacity) {
                    Gui.toast(p, "No free breaker slots. Build another Circuit Breaker room.", true);
                    return;
                }
                r.connected = !r.connected;
            }
            case "clear_label" -> r.scpLabel = "";
            case "rename" -> {
                String name = a.text();
                r.name = name.length() > 32 ? name.substring(0, 32) : name;
                Gui.toast(p, name.isEmpty() ? "Name cleared." : "Renamed to " + r.name + ".", false);
            }
            case "demolish" -> {
                ServerLevel level = (ServerLevel) p.level();
                if (SiteLogic.hasScp(level, r)) {
                    Gui.toast(p, "An SCP is inside " + r.displayName() + ". Move it out first.", true);
                    return;
                }
                demolish(level, r);
                Gui.toast(p, r.displayName() + " is no longer a room.", false);
                room = -1;
            }
            case "demolish_group" -> {
                ServerLevel level = (ServerLevel) p.level();
                if (SiteLogic.hasScp(level, r)) {
                    Gui.toast(p, "An SCP is inside " + r.displayName() + ". Move it out first.", true);
                    return;
                }
                List<String> kept = new ArrayList<>();
                for (Room c : site.companions(r)) {
                    // A cell with an SCP inside still can't be demolished.
                    if (SiteLogic.hasScp(level, c)) kept.add(c.displayName());
                    else demolish(level, c);
                }
                demolish(level, r);
                Gui.toast(p, r.displayName() + " and its companions are no longer rooms." + (kept.isEmpty() ? "" : " Kept " + String.join(", ", kept) + ": an SCP is inside."), !kept.isEmpty());
                room = -1;
            }
            default -> {}
        }
    }

    /** Takes the room off the books. Its blocks stay: the player built them and takes them down by hand. */
    private void demolish(ServerLevel level, Room r) {
        site.removeRoom(r);
        site.log(r.displayName() + " is no longer a room.");
    }

    private void memberAction(ServerPlayer p, String action, Args a) {
        Member m = member == null ? null : site.members.get(member);
        if (m == null) return;
        switch (action) {
            case "clearance" -> m.clearance = Math.max(0, Math.min(4, a.num("value", m.clearance)));
            case "job" -> {
                String job = a.str("id");
                if (Arrays.asList(JOBS).contains(job)) m.job = job;
            }
            case "perm_build" -> m.build = !m.build;
            case "perm_terminal" -> m.terminal = !m.terminal;
            case "perm_assign" -> m.assign = !m.assign;
            case "remove_member" -> {
                site.members.remove(member);
                Gui.toast(p, m.name + " removed from the site.", false);
                member = null;
            }
            case "transfer" -> transfer(p, member, m);
            default -> {}
        }
    }

    private void invite(ServerPlayer p, String name) {
        ServerPlayer target = p.level().getServer().getPlayerList().getPlayerByName(name);
        if (target == null) {
            Gui.toast(p, "No online player called " + name + ".", true);
            return;
        }
        if (site.isMember(target)) {
            Gui.toast(p, target.getName().getString() + " is already part of the site.", true);
            return;
        }
        site.members.put(target.getUUID(), new Member(target.getName().getString()));
        site.log(target.getName().getString() + " joined the site.");
        Msg.send(target, "You're now part of " + site.directorName + "'s site, " + site.name() + ".", ChatFormatting.GREEN);
        Gui.toast(p, target.getName().getString() + " joined with Level 1 clearance.", false);
    }

    private void transfer(ServerPlayer p, UUID id, Member m) {
        if (SiteManager.directedBy(id) != null) {
            Gui.toast(p, m.name + " already directs a site.", true);
            return;
        }
        site.members.remove(id);
        Member old = new Member(p.getName().getString());
        old.clearance = 4;
        old.terminal = true;
        old.assign = true;
        site.members.put(p.getUUID(), old);
        site.director = id;
        site.directorName = m.name;
        site.log("Directorship transferred to " + site.directorName + ".");
        member = null;
        tab = "overview";
        Msg.send(p, "You've handed the site to " + site.directorName + ".", ChatFormatting.GOLD);
        ServerPlayer target = p.level().getServer().getPlayerList().getPlayer(id);
        if (target != null) Msg.send(target, "You are now the Site Director of " + site.name() + ".", ChatFormatting.GOLD);
    }

    static int parseInt(String s, int fallback) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    static UUID parseUuid(String s) {
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
