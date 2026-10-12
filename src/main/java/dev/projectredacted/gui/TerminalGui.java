package dev.projectredacted.gui;

import dev.projectredacted.raid.RaidManager;
import dev.projectredacted.registry.ModBlocks;
import dev.projectredacted.registry.ModItems;
import dev.projectredacted.room.Construction;
import dev.projectredacted.room.Room;
import dev.projectredacted.room.RoomType;
import dev.projectredacted.site.*;
import dev.projectredacted.staff.StaffRecord;
import dev.projectredacted.staff.StaffEntity;
import dev.projectredacted.util.Cost;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.*;

/** The Mainframe Terminal (and Portable Tablet): the site's main interface. */
public final class TerminalGui {
    public static void openMain(ServerPlayer player, Site site) {
        ChestGui gui = new ChestGui("Mainframe Terminal: " + site.directorName + "'s Site", 6);
        gui.set(4, overview(site));
        gui.button(19, ChestGui.icon(Items.FILLED_MAP, "Facility Map", ChatFormatting.AQUA, "Rooms by chunk. Cells show their SCP,", "powered rooms show ⚡."),
                (p, r) -> ChestGui.later(() -> openMap(p, site, 0)));
        gui.button(21, ChestGui.icon(Items.BRICKS, "Rooms", ChatFormatting.AQUA, site.rooms.size() + " rooms", "Power, repair status, demolition."),
                (p, r) -> ChestGui.later(() -> openRooms(p, site, 0)));
        gui.button(23, ChestGui.icon(Items.PLAYER_HEAD, "Staff", ChatFormatting.AQUA, site.staff.size() + " staff, " + site.beds + " beds", "Promote and dismiss staff."),
                (p, r) -> ChestGui.later(() -> openStaff(p, site, 0)));
        gui.button(25, ChestGui.icon(Items.ANVIL, "Upgrades", ChatFormatting.AQUA, site.pendingUpgrades.size() + " in progress"),
                (p, r) -> ChestGui.later(() -> openUpgrades(p, site)));
        gui.button(29, ChestGui.icon(Items.NAME_TAG, "Personnel", ChatFormatting.AQUA, "Players at your site, their clearance", "and what they may do."),
                (p, r) -> ChestGui.later(() -> openPersonnel(p, site)));
        gui.button(31, ChestGui.icon(Items.SHIELD, "Security", ChatFormatting.AQUA, "Raid risk, MTF, keycards."),
                (p, r) -> ChestGui.later(() -> openSecurity(p, site)));
        gui.button(33, ChestGui.icon(site.generated ? Items.GRASS_BLOCK : Items.STRUCTURE_BLOCK, site.generated ? "Site Type" : "Generate Facility",
                        site.generated ? ChatFormatting.AQUA : ChatFormatting.GOLD,
                        site.generated ? "Current: " + site.type.displayName : "Choose a site type and build the Level 0 facility."),
                (p, r) -> ChestGui.later(() -> openSiteType(p, site)));
        gui.button(40, ChestGui.icon(Items.CHEST, "Collect", ChatFormatting.GOLD, site.coinsOwed + " coins from Administration",
                site.documentsOwed + " Research Documents waiting"), (p, r) -> {
            if (site.coinsOwed > 0) Cost.give(p, new ItemStack(ModItems.COIN, site.coinsOwed));
            if (site.documentsOwed > 0) Cost.give(p, new ItemStack(ModItems.RESEARCH_DOCUMENT, site.documentsOwed));
            site.coinsOwed = 0;
            site.documentsOwed = 0;
            ChestGui.later(() -> openMain(p, site));
        });
        List<String> log = new ArrayList<>();
        for (int i = Math.max(0, site.logLines.size() - 12); i < site.logLines.size(); i++) log.add(site.logLines.get(i));
        gui.set(49, ChestGui.icon(Items.WRITABLE_BOOK, "Site Log", ChatFormatting.WHITE, log.isEmpty() ? List.of("Nothing yet.") : log));
        gui.footer(null);
        gui.open(player);
    }

    private static ItemStack overview(Site site) {
        List<String> lines = new ArrayList<>();
        lines.add("Facility Level " + site.level + " (" + site.claimChunks() + "x" + site.claimChunks() + " chunks), " + site.type.displayName);
        lines.add("Mainframe: " + (site.mainframeOnline() ? "ONLINE" : "OFFLINE"));
        lines.add("C.A.S.S.I.E.: " + (site.cassie == null ? "not installed" : site.cassieOperational() ? "online" : "OFFLINE"));
        lines.add("Power " + site.powerDemand + "/" + site.powerSupply + ", water " + site.waterDemand + "/" + site.waterSupply);
        lines.add("Staff " + site.housedStaff() + "/" + site.beds + " beds, SCPs contained: " + site.scpCount);
        lines.add(String.format("Raid risk: %.1f%% per minute", RaidManager.chancePerMinute(site) * 100));
        if (site.raid != null) lines.add("RAID IN PROGRESS: " + site.raid.factions);
        if (site.level < 3) {
            String missing = FacilityLevels.missing(site, site.level + 1);
            lines.add("Next level: " + (missing == null ? "requirements met, buy it in Upgrades" : missing));
        }
        return ChestGui.icon(ModBlocks.MAINFRAME, "Site Overview", ChatFormatting.GOLD, lines);
    }

    // ---------------------------------------------------------------- map

    public static void openMap(ServerPlayer player, Site site, int page) {
        int size = site.claimChunks();
        int rowsPerPage = 5;
        int pages = (size + rowsPerPage - 1) / rowsPerPage;
        page = Math.max(0, Math.min(page, pages - 1));
        ChestGui gui = new ChestGui("Facility Map", 6);
        int minCX = site.minChunkX(), minCZ = site.minChunkZ();
        int coreCX = site.mainframe.getX() >> 4, coreCZ = site.mainframe.getZ() >> 4;
        for (int row = 0; row < rowsPerPage; row++) {
            int cz = page * rowsPerPage + row;
            if (cz >= size) break;
            for (int col = 0; col < Math.min(9, size); col++) {
                List<Room> here = new ArrayList<>();
                for (Room r : site.rooms) {
                    BlockPos c = r.center();
                    if ((c.getX() >> 4) == minCX + col && (c.getZ() >> 4) == minCZ + cz) here.add(r);
                }
                boolean core = minCX + col == coreCX && minCZ + cz == coreCZ;
                List<String> lore = new ArrayList<>();
                lore.add("Chunk " + (minCX + col) + ", " + (minCZ + cz));
                for (Room r : here) lore.add((r.poweredNow ? "⚡ " : "  ") + r.displayName() + (r.built ? "" : " (building)"));
                ItemStack icon;
                if (here.isEmpty()) icon = ChestGui.icon(core ? ModBlocks.MAINFRAME : Items.LIGHT_GRAY_STAINED_GLASS_PANE, core ? "Mainframe" : "Empty", ChatFormatting.DARK_GRAY, lore);
                else {
                    Room first = here.get(0);
                    for (Room r : here) if (r.typeDef() != null && r.typeDef().isContainment()) first = r;
                    RoomType t = first.typeDef();
                    String title = here.size() == 1 ? first.displayName() : here.size() + " rooms";
                    icon = ChestGui.icon(t != null ? t.iconItem() : Items.BRICKS, (anyPowered(here) ? "⚡ " : "") + title, ChatFormatting.AQUA, lore);
                }
                gui.set(row * 9 + col, icon);
            }
        }
        final int current = page;
        if (page > 0) gui.button(46, ChestGui.icon(Items.ARROW, "North", ChatFormatting.GRAY), (p, r) -> ChestGui.later(() -> openMap(p, site, current - 1)));
        if (page < pages - 1) gui.button(52, ChestGui.icon(Items.ARROW, "South", ChatFormatting.GRAY), (p, r) -> ChestGui.later(() -> openMap(p, site, current + 1)));
        gui.footer((p, r) -> ChestGui.later(() -> openMain(p, site)));
        gui.open(player);
    }

    private static boolean anyPowered(List<Room> rooms) {
        for (Room r : rooms) if (r.poweredNow && r.typeDef() != null && r.typeDef().powerDraw > 0) return true;
        return false;
    }

    // ---------------------------------------------------------------- rooms

    public static void openRooms(ServerPlayer player, Site site, int page) {
        ChestGui gui = new ChestGui("Rooms", 6);
        List<Room> rooms = site.rooms;
        int perPage = 45, pages = Math.max(1, (rooms.size() + perPage - 1) / perPage);
        page = Math.max(0, Math.min(page, pages - 1));
        for (int i = 0; i < perPage && page * perPage + i < rooms.size(); i++) {
            Room r = rooms.get(page * perPage + i);
            RoomType t = r.typeDef();
            gui.button(i, ChestGui.icon(t != null ? t.iconItem() : Items.BARRIER, r.displayName(), r.working() ? ChatFormatting.GREEN : ChatFormatting.YELLOW,
                    status(r), "Integrity " + r.integrity + "%"), (p, right) -> ChestGui.later(() -> openRoom(p, site, r)));
        }
        final int current = page;
        if (page > 0) gui.button(45, ChestGui.icon(Items.ARROW, "Previous page", ChatFormatting.GRAY), (p, r) -> ChestGui.later(() -> openRooms(p, site, current - 1)));
        if (page < pages - 1) gui.button(53, ChestGui.icon(Items.ARROW, "Next page", ChatFormatting.GRAY), (p, r) -> ChestGui.later(() -> openRooms(p, site, current + 1)));
        gui.footer((p, r) -> ChestGui.later(() -> openMain(p, site)));
        gui.open(player);
    }

    private static String status(Room r) {
        RoomType t = r.typeDef();
        if (t == null) return "Unknown room type (was its datapack removed?)";
        if (!r.built) return "Under construction";
        if (r.integrity <= 0) return "Destroyed: needs repair";
        if (t.powerDraw > 0 && !r.connected) return "Dormant: not on the Circuit Breaker";
        if (t.powerDraw > 0 && !r.poweredNow) return "No power";
        if (t.waterDraw > 0 && !r.wateredNow) return "No water";
        return "Working";
    }

    public static void openRoom(ServerPlayer player, Site site, Room room) {
        RoomType t = room.typeDef();
        ChestGui gui = new ChestGui(room.displayName(), 3);
        gui.set(4, ChestGui.icon(t != null ? t.iconItem() : Items.BARRIER, room.displayName(), ChatFormatting.GOLD,
                status(room), "Integrity " + room.integrity + "%",
                "From " + room.x1 + " " + room.y1 + " " + room.z1 + " to " + room.x2 + " " + room.y2 + " " + room.z2,
                t != null && t.isContainment() ? "Contains: " + (room.scpCount > 0 ? room.scpLabel : "nothing") : ""));
        if (t != null && t.powerDraw > 0) {
            gui.button(11, ChestGui.icon(room.connected ? Items.REDSTONE_TORCH : Items.LEVER, room.connected ? "Disconnect power" : "Connect power", ChatFormatting.YELLOW),
                    (p, r) -> {
                        room.connected = !room.connected;
                        ChestGui.later(() -> openRoom(p, site, room));
                    });
        }
        if (t != null && t.isContainment()) {
            gui.button(13, ChestGui.icon(Items.NAME_TAG, "Clear SCP label", ChatFormatting.GRAY, "The label is set again automatically", "when an SCP is detected in the cell."),
                    (p, r) -> {
                        room.scpLabel = "";
                        ChestGui.later(() -> openRoom(p, site, room));
                    });
        }
        gui.button(15, ChestGui.icon(Items.TNT, "Demolish", ChatFormatting.RED, "Removes the room and clears its blocks.", "Not allowed while an SCP is inside."),
                (p, r) -> {
                    ServerLevel level = (ServerLevel) p.level();
                    if (SiteLogic.hasScp(level, room)) {
                        p.sendSystemMessage(Component.literal("An SCP is inside " + room.displayName() + ". Move it out before demolishing.").withStyle(ChatFormatting.RED));
                        return;
                    }
                    ChestGui.later(() -> ConfirmGui.open(p, "Demolish?", "Demolish " + room.displayName() + "? Its blocks will be cleared.", () -> demolish(level, site, room, p), null));
                });
        gui.footer((p, r) -> ChestGui.later(() -> openRooms(p, site, 0)));
        gui.open(player);
    }

    private static void demolish(ServerLevel level, Site site, Room room, ServerPlayer player) {
        if (SiteLogic.hasScp(level, room)) return;
        site.rooms.remove(room);
        Construction.forget(site, room);
        if (!room.freeform) {
            for (BlockPos p : BlockPos.betweenClosed(room.x1, room.y1 + 1, room.z1, room.x2, room.y2, room.z2)) {
                if (p.equals(site.mainframe) || p.equals(site.terminal) || p.equals(site.cassie)) continue;
                level.setBlock(p, Blocks.AIR.defaultBlockState(), 2 | 16);
            }
        }
        for (StaffRecord s : site.staff.values()) if (s.assignment.room == room.number) s.assignment = new dev.projectredacted.staff.Assignment();
        player.sendSystemMessage(Component.literal(room.displayName() + " demolished.").withStyle(ChatFormatting.YELLOW));
    }

    // ---------------------------------------------------------------- staff

    public static void openStaff(ServerPlayer player, Site site, int page) {
        ChestGui gui = new ChestGui("Staff", 6);
        List<StaffRecord> staff = new ArrayList<>(site.staff.values());
        int perPage = 45, pages = Math.max(1, (staff.size() + perPage - 1) / perPage);
        page = Math.max(0, Math.min(page, pages - 1));
        for (int i = 0; i < perPage && page * perPage + i < staff.size(); i++) {
            StaffRecord s = staff.get(page * perPage + i);
            gui.button(i, ChestGui.icon(s.role.icon(), s.fullName(), s.named ? ChatFormatting.GOLD : ChatFormatting.WHITE,
                    s.role.displayName + ", level " + s.level + ", clearance " + s.clearance,
                    String.format("Morale %.0f, food %.0f, rest %.0f", s.morale, s.hunger, s.energy),
                    "Assignment: " + StaffEntity.describeAssignment(site, s.assignment),
                    s.skill != null ? "Skill: " + s.skill.displayName : ""), (p, r) -> ChestGui.later(() -> openStaffMember(p, site, s)));
        }
        final int current = page;
        if (page > 0) gui.button(45, ChestGui.icon(Items.ARROW, "Previous page", ChatFormatting.GRAY), (p, r) -> ChestGui.later(() -> openStaff(p, site, current - 1)));
        if (page < pages - 1) gui.button(53, ChestGui.icon(Items.ARROW, "Next page", ChatFormatting.GRAY), (p, r) -> ChestGui.later(() -> openStaff(p, site, current + 1)));
        gui.footer((p, r) -> ChestGui.later(() -> openMain(p, site)));
        gui.open(player);
    }

    public static void openStaffMember(ServerPlayer player, Site site, StaffRecord s) {
        ChestGui gui = new ChestGui(s.name, 3);
        gui.set(4, ChestGui.icon(s.role.icon(), s.fullName(), ChatFormatting.GOLD, s.role.displayName + ", level " + s.level,
                "Clearance " + s.clearance, String.format("XP %.0f", s.xp)));
        boolean director = site.isDirector(player);
        gui.button(11, ChestGui.icon(Items.EMERALD, "Promote", ChatFormatting.GREEN, "Raises clearance (max 5).", "New title: " + s.role.title(Math.min(5, s.clearance + 1)),
                "Promotions need the staff member to be level " + (s.clearance * 2) + "+."), (p, r) -> {
            if (!director) return;
            if (s.clearance >= 5) return;
            if (s.level < s.clearance * 2 && !p.isCreative()) {
                p.sendSystemMessage(Component.literal(s.name + " needs more experience first (level " + (s.clearance * 2) + ").").withStyle(ChatFormatting.RED));
                return;
            }
            s.clearance++;
            Cassie.announce(site, s.name + " has been promoted to " + s.title() + ".");
            ChestGui.later(() -> openStaffMember(p, site, s));
        });
        gui.button(13, ChestGui.icon(Items.REDSTONE, "Demote", ChatFormatting.YELLOW, "Lowers clearance. Hurts their morale."), (p, r) -> {
            if (!director || s.clearance <= 0) return;
            s.clearance--;
            s.morale = Math.max(0, s.morale - 20);
            ChestGui.later(() -> openStaffMember(p, site, s));
        });
        gui.button(15, ChestGui.icon(Items.IRON_DOOR, "Dismiss", ChatFormatting.RED, "They leave the site for good."), (p, r) -> {
            if (!director) return;
            ChestGui.later(() -> ConfirmGui.open(p, "Dismiss?", "Dismiss " + s.fullName() + "?", () -> {
                site.staff.remove(s.id);
                var e = ((ServerLevel) p.level()).getEntity(s.id);
                if (e != null) e.discard();
                site.log(s.fullName() + " was dismissed.");
            }, null));
        });
        gui.footer((p, r) -> ChestGui.later(() -> openStaff(p, site, 0)));
        gui.open(player);
    }

    // ---------------------------------------------------------------- upgrades

    public static void openUpgrades(ServerPlayer player, Site site) {
        ChestGui gui = new ChestGui("Upgrades", 6);
        long now = player.level().getGameTime();
        int slot = 0;
        for (Upgrade u : Upgrade.values()) {
            String blocker = u.blocker(site);
            List<String> lore = new ArrayList<>(List.of(ConfirmGui.wrap(u.description)));
            lore.add("Category: " + u.category.displayName);
            lore.add("Cost: " + String.join(", ", u.cost(site).describe()) + (u.dollarCost(site) > 0 ? ", $" + u.dollarCost(site) : ""));
            lore.add("Time: " + u.ticks(site) / 20 + "s");
            if (site.hasUpgrade(u)) lore.add("COMPLETE");
            else if (site.pendingUpgrades.containsKey(u)) lore.add("In progress: " + Math.max(0, site.pendingUpgrades.get(u) - now) / 20 + "s left");
            else if (blocker != null) lore.add("LOCKED: " + blocker);
            else lore.add("Click to buy.");
            if (u.category == Upgrade.Category.RESEARCH) lore.add("Research Documents you carry speed it up.");
            lore.add("A Library Tome you carry cuts materials by 25%.");
            ChatFormatting color = site.hasUpgrade(u) ? ChatFormatting.GREEN : blocker == null ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY;
            gui.button(slot++, ChestGui.icon(site.hasUpgrade(u) ? Items.LIME_DYE : u.icon(), u.displayName, color, lore), (p, r) -> {
                if (!site.isDirector(p)) {
                    p.sendSystemMessage(Component.literal("Only the Site Director can buy upgrades.").withStyle(ChatFormatting.RED));
                    return;
                }
                String reason = buy(p, site, u);
                if (reason != null) p.sendSystemMessage(Component.literal(reason).withStyle(ChatFormatting.RED));
                ChestGui.later(() -> openUpgrades(p, site));
            });
        }
        gui.footer((p, r) -> ChestGui.later(() -> openMain(p, site)));
        gui.open(player);
    }

    private static String buy(ServerPlayer player, Site site, Upgrade u) {
        String blocker = u.blocker(site);
        if (blocker != null) return blocker;
        Cost cost = u.cost(site);
        boolean tome = Cost.count(player, ModItems.LIBRARY_TOME) > 0 && !player.isCreative();
        if (tome) cost = cost.scaled(0.75);
        int dollars = u.dollarCost(site);
        if (!player.isCreative() && Cost.count(player, ModItems.DOLLAR) < dollars) return "Needs $" + dollars + " in Dollars.";
        if (!cost.pay(player)) return "Not enough materials. Needs: " + String.join(", ", cost.describe());
        if (!player.isCreative()) {
            Cost.take(player, ModItems.DOLLAR, dollars);
            if (tome) Cost.take(player, ModItems.LIBRARY_TOME, 1);
        }
        int ticks = u.ticks(site);
        if (u.category == Upgrade.Category.RESEARCH && !player.isCreative()) {
            int docs = Math.min(4, Cost.count(player, ModItems.RESEARCH_DOCUMENT));
            Cost.take(player, ModItems.RESEARCH_DOCUMENT, docs);
            ticks = (int) (ticks * (1 - 0.2 * docs));
        }
        if (player.isCreative()) ticks = 20;
        site.pendingUpgrades.put(u, player.level().getGameTime() + ticks);
        player.sendSystemMessage(Component.literal(u.displayName + " started. Ready in " + ticks / 20 + "s.").withStyle(ChatFormatting.GREEN));
        return null;
    }

    // ---------------------------------------------------------------- personnel

    public static void openPersonnel(ServerPlayer player, Site site) {
        ChestGui gui = new ChestGui("Personnel", 6);
        gui.set(0, ChestGui.icon(Items.GOLDEN_HELMET, "Director " + site.directorName, ChatFormatting.GOLD, "Clearance 5. Everything allowed."));
        int slot = 1;
        for (Map.Entry<UUID, Member> e : site.members.entrySet()) {
            Member m = e.getValue();
            UUID id = e.getKey();
            gui.button(slot++, ChestGui.icon(Items.PLAYER_HEAD, m.name, ChatFormatting.WHITE, "Clearance " + m.clearance + ", job: " + m.job,
                    "Build " + yes(m.build) + ", terminal " + yes(m.terminal) + ", assign staff " + yes(m.assign)),
                    (p, r) -> ChestGui.later(() -> openMember(p, site, id)));
            if (slot >= 45) break;
        }
        gui.set(49, ChestGui.icon(Items.WRITABLE_BOOK, "Adding players", ChatFormatting.GRAY, "Use /site invite <player>.", "Directorship: /site transfer <player>."));
        gui.footer((p, r) -> ChestGui.later(() -> openMain(p, site)));
        gui.open(player);
    }

    private static String yes(boolean b) {
        return b ? "yes" : "no";
    }

    private static final String[] JOBS = {"Personnel", "Security", "Researcher", "Engineer", "Medical", "Janitor", "Administration"};

    public static void openMember(ServerPlayer player, Site site, UUID id) {
        Member m = site.members.get(id);
        if (m == null) {
            openPersonnel(player, site);
            return;
        }
        boolean director = site.isDirector(player);
        ChestGui gui = new ChestGui(m.name, 3);
        gui.set(4, ChestGui.icon(Items.PLAYER_HEAD, m.name, ChatFormatting.GOLD, "Clearance " + m.clearance, "Job: " + m.job));
        gui.button(9, ChestGui.icon(Items.EMERALD, "Clearance +1", ChatFormatting.GREEN), (p, r) -> {
            if (director) m.clearance = Math.min(4, m.clearance + 1);
            ChestGui.later(() -> openMember(p, site, id));
        });
        gui.button(10, ChestGui.icon(Items.REDSTONE, "Clearance -1", ChatFormatting.YELLOW), (p, r) -> {
            if (director) m.clearance = Math.max(0, m.clearance - 1);
            ChestGui.later(() -> openMember(p, site, id));
        });
        gui.button(12, ChestGui.icon(Items.BRICKS, "Build: " + yes(m.build), ChatFormatting.AQUA), (p, r) -> {
            if (director) m.build = !m.build;
            ChestGui.later(() -> openMember(p, site, id));
        });
        gui.button(13, ChestGui.icon(ModBlocks.SITE_DIRECTOR_COMPUTER, "Terminal: " + yes(m.terminal), ChatFormatting.AQUA), (p, r) -> {
            if (director) m.terminal = !m.terminal;
            ChestGui.later(() -> openMember(p, site, id));
        });
        gui.button(14, ChestGui.icon(ModItems.CLIPBOARD, "Assign staff: " + yes(m.assign), ChatFormatting.AQUA), (p, r) -> {
            if (director) m.assign = !m.assign;
            ChestGui.later(() -> openMember(p, site, id));
        });
        gui.button(16, ChestGui.icon(Items.IRON_SWORD, "Job: " + m.job, ChatFormatting.AQUA, "Players can do NPC-style jobs.", "Click to change."), (p, r) -> {
            if (director) {
                int i = Arrays.asList(JOBS).indexOf(m.job);
                m.job = JOBS[(i + 1) % JOBS.length];
            }
            ChestGui.later(() -> openMember(p, site, id));
        });
        gui.button(22, ChestGui.icon(Items.BARRIER, "Remove from site", ChatFormatting.RED), (p, r) -> {
            if (!director) return;
            site.members.remove(id);
            ChestGui.later(() -> openPersonnel(p, site));
        });
        gui.footer((p, r) -> ChestGui.later(() -> openPersonnel(p, site)));
        gui.open(player);
    }

    // ---------------------------------------------------------------- security

    public static void openSecurity(ServerPlayer player, Site site) {
        ChestGui gui = new ChestGui("Security", 3);
        long now = player.level().getGameTime();
        List<String> lore = new ArrayList<>();
        lore.add(String.format("Raid risk: %.1f%% per minute", RaidManager.chancePerMinute(site) * 100));
        lore.add("Defections on record: " + site.defections);
        if (site.raid != null) lore.add("RAID IN PROGRESS: " + site.raid.factions + ", " + site.raid.raiders.size() + " raiders");
        else if (now < site.raidCooldownUntil) lore.add("Cooldown: " + (site.raidCooldownUntil - now) / 20 + "s");
        lore.add(site.cassieOperational() ? "C.A.S.S.I.E. will warn you before raids." : "No early warning: the alarm is the first sign.");
        gui.set(4, ChestGui.icon(Items.BELL, "Threat Assessment", ChatFormatting.GOLD, lore));
        gui.button(11, ChestGui.icon(Items.DIAMOND_SWORD, "Call the MTF", ChatFormatting.BLUE, "After a breach, an MTF unit brings", "escaped SCPs back to their cells."), (p, r) -> {
            String err = RaidManager.callMtf((ServerLevel) p.level(), site);
            if (err != null) p.sendSystemMessage(Component.literal(err).withStyle(ChatFormatting.RED));
            p.closeContainer();
        });
        int clearance = site.clearance(player);
        gui.button(15, ChestGui.icon(ModItems.keycardFor(Math.max(1, clearance)), "Print keycard", ChatFormatting.AQUA, "Prints a Level " + Math.max(1, clearance) + " keycard,",
                "matching your clearance. Costs 2 Plastic."), (p, r) -> {
            int lvl = Math.max(1, site.clearance(p));
            if (!p.isCreative() && Cost.count(p, ModItems.PLASTIC) < 2) {
                p.sendSystemMessage(Component.literal("Printing a keycard takes 2 Plastic.").withStyle(ChatFormatting.RED));
                return;
            }
            if (!p.isCreative()) Cost.take(p, ModItems.PLASTIC, 2);
            Cost.give(p, new ItemStack(ModItems.keycardFor(lvl)));
        });
        gui.footer((p, r) -> ChestGui.later(() -> openMain(p, site)));
        gui.open(player);
    }

    // ---------------------------------------------------------------- site type

    public static void openSiteType(ServerPlayer player, Site site) {
        ChestGui gui = new ChestGui(site.generated ? "Site Type" : "Choose a Site Type", 3);
        int slot = 11;
        for (SiteType type : SiteType.values()) {
            boolean current = site.type == type;
            gui.button(slot, ChestGui.icon(switch (type) {
                        case UNDERGROUND -> Items.DEEPSLATE_BRICKS;
                        case SURFACE_FLAT -> Items.SMOOTH_STONE_SLAB;
                        case SURFACE_TALL -> Items.QUARTZ_PILLAR;
                    }, type.displayName + (current ? " (current)" : ""), current ? ChatFormatting.GREEN : ChatFormatting.AQUA, ConfirmGui.wrap(type.description)),
                    (p, r) -> {
                        if (!site.isDirector(p)) return;
                        site.type = type;
                        ChestGui.later(() -> openSiteType(p, site));
                    });
            slot += 2;
        }
        if (!site.generated) {
            gui.button(22, ChestGui.icon(Items.STRUCTURE_BLOCK, "Generate Level 0 Facility", ChatFormatting.GOLD,
                    "Builds an Entrance, a Barracks (4 beds) and", "the Director's Office, then moves the Mainframe", "and Terminal into the office.",
                    "Type: " + site.type.displayName), (p, r) -> {
                if (!site.isDirector(p)) return;
                ChestGui.later(() -> ConfirmGui.open(p, "Generate facility?", "Build the Level 0 " + site.type.displayName
                        + " facility here? Blocks in the way will be replaced.", () -> {
                    FacilityGenerator.generateLevel0((ServerLevel) p.level(), site);
                    p.sendSystemMessage(Component.literal("Facility generated. Now head out and recruit staff from Foundation Bunkers.").withStyle(ChatFormatting.GOLD));
                }, null));
            });
        } else {
            gui.set(22, ChestGui.icon(Items.PAPER, "Changing type", ChatFormatting.GRAY, "You can change type any time; it affects", "how you plan to grow (down or up)."));
        }
        gui.footer((p, r) -> ChestGui.later(() -> openMain(p, site)));
        gui.open(player);
    }

    private TerminalGui() {}
}
