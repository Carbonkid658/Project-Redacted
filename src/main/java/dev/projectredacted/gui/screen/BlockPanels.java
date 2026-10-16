package dev.projectredacted.gui.screen;

import dev.projectredacted.block.FacingBlock;
import dev.projectredacted.block.KeycardReaderBlock;
import dev.projectredacted.block.RuinedCassieBlock;
import dev.projectredacted.block.RuinedMainframeBlock;
import dev.projectredacted.gui.*;
import dev.projectredacted.registry.ModBlocks;
import dev.projectredacted.registry.ModItems;
import dev.projectredacted.site.*;
import dev.projectredacted.util.Cost;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/** The small panels opened by right-clicking site blocks (GUI design §10). */
public final class BlockPanels {
    /** Shared checks: the block is still there and the player is next to it. */
    abstract static class AtBlock extends GuiScreen {
        final BlockPos pos;
        final Block block;

        AtBlock(BlockPos pos, Block block) {
            this.pos = pos.immutable();
            this.block = block;
        }

        @Override
        public String invalid(ServerPlayer player) {
            if (!Ui.near(player, pos)) return "You stepped away.";
            return player.level().getBlockState(pos).is(block) ? null : "It's gone.";
        }

        Site site(ServerPlayer p) {
            return SiteManager.at(p.level(), pos);
        }
    }

    // ---------------------------------------------------------------- §10.1 Mainframe status

    public static class Mainframe extends AtBlock {
        public Mainframe(BlockPos pos) {
            super(pos, ModBlocks.MAINFRAME);
        }

        @Override
        public String id() {
            return "mainframe";
        }

        @Override
        public boolean live() {
            return true;
        }

        private Site linked() {
            return SiteManager.byMainframe(pos);
        }

        @Override
        public View build(ServerPlayer p) {
            Site site = linked();
            View v = new View("MAINFRAME").size("small");
            if (site == null) return v.add(El.text("This Mainframe isn't linked to a site.", "gray"));
            v.title(site.name() + " · MAINFRAME").badge(Ui.clr(site, p));
            boolean online = site.mainframeOnline();
            v.add(El.text("Director " + site.directorName + " · " + (online ? "● ONLINE" : "● OFFLINE"), online ? "green" : "red"));
            v.add(El.need("Integrity", site.mainframeSabotaged ? 0 : 100));
            String terminal = site.terminal == null ? "No terminal is linked yet." : !site.canUseTerminal(p) ? "You don't have terminal access."
                    : !online ? "The Mainframe is offline." : null;
            int kits = Ui.have(p, ModItems.REPAIR_KIT);
            List<El> row = new ArrayList<>();
            row.add(El.button("Open terminal", "terminal").style("primary").disabled(terminal));
            if (site.mainframeSabotaged) {
                row.add(El.button("Repair (1 kit, have " + kits + ")", "repair").style("confirm")
                        .disabled(kits < 1 && !p.isCreative() ? "Needs a Repair Kit." : null));
            }
            return v.add(El.row(row));
        }

        @Override
        public void handle(ServerPlayer p, String action, Args a) {
            Site site = linked();
            if (site == null) return;
            switch (action) {
                case "terminal" -> {
                    if (site.terminal != null && site.canUseTerminal(p) && site.mainframeOnline()) Gui.openChild(p, new TerminalScreen(site, null));
                }
                case "repair" -> {
                    if (!site.mainframeSabotaged) return;
                    if (!p.isCreative() && Cost.count(p, ModItems.REPAIR_KIT) < 1) return;
                    if (!p.isCreative()) Cost.take(p, ModItems.REPAIR_KIT, 1);
                    site.mainframeSabotaged = false;
                    site.log("Mainframe repaired by " + p.getName().getString() + ".");
                    Gui.toast(p, "The Mainframe is back online.", false);
                }
                default -> {}
            }
        }
    }

    // ---------------------------------------------------------------- §10.2 C.A.S.S.I.E.

    public static class CassieConsole extends AtBlock {
        public CassieConsole(BlockPos pos) {
            super(pos, ModBlocks.CASSIE);
        }

        @Override
        public String id() {
            return "cassie";
        }

        @Override
        public boolean live() {
            return true;
        }

        @Override
        public View build(ServerPlayer p) {
            Site site = site(p);
            View v = new View("C.A.S.S.I.E. CONSOLE").size("medium");
            if (site == null || !pos.equals(site.cassie)) return v.add(El.text("C.A.S.S.I.E. is not linked to a site.", "gray"));
            v.badge(Ui.clr(site, p));
            boolean ok = site.cassieOperational();
            v.add(El.text("Status: " + (site.cassieDisabled ? "DISABLED" : ok ? "ONLINE" : "COMPROMISED"), ok ? "green" : "red"));
            v.add(El.segments("Intrusion stage", site.cassieHackStage(), 4));
            El.ListEl recent = El.list("announcements", "").empty("No announcements yet.").rows(4);
            List<String> lines = site.logLines;
            int shown = 0;
            for (int i = lines.size() - 1; i >= 0 && shown < 12; i--) {
                String line = lines.get(i);
                int at = line.indexOf("[C.A.S.S.I.E.] ");
                if (at < 0) continue;
                String text = line.substring(at + 15);
                recent.add(String.valueOf(i), text).tip(line);
                shown++;
            }
            v.add(El.text("Recent announcements:", "gray"));
            v.add(recent);
            if (site.cassieDisabled || site.cassieHack > 0) {
                int kits = Ui.have(p, ModItems.REPAIR_KIT);
                v.add(El.button("Repair (1 kit, have " + kits + ")", "repair").style("confirm").disabled(kits < 1 && !p.isCreative() ? "Needs a Repair Kit." : null));
            }
            String announce = !site.isDirector(p) ? "Only the Site Director can make announcements." : !ok ? "C.A.S.S.I.E. is offline." : null;
            v.add(El.input("announce", "Custom announcement", El.button("Announce", "announce").style("primary").disabled(announce)));
            return v;
        }

        @Override
        public void handle(ServerPlayer p, String action, Args a) {
            Site site = site(p);
            if (site == null || !pos.equals(site.cassie)) return;
            switch (action) {
                case "repair" -> {
                    if (!site.cassieDisabled && site.cassieHack <= 0) return;
                    if (!p.isCreative() && Cost.count(p, ModItems.REPAIR_KIT) < 1) return;
                    if (!p.isCreative()) Cost.take(p, ModItems.REPAIR_KIT, 1);
                    site.cassieDisabled = false;
                    site.cassieHack = 0;
                    Cassie.announce(site, "Systems restored. Security protocols re-engaged.");
                }
                case "announce" -> {
                    String text = a.text();
                    if (!site.isDirector(p) || text.isEmpty()) return;
                    if (!Cassie.announce(site, text)) Gui.toast(p, "C.A.S.S.I.E. is offline.", true);
                }
                default -> {}
            }
        }
    }

    // ---------------------------------------------------------------- §10.3 Alpha Warhead

    public static class WarheadPanel extends AtBlock {
        public WarheadPanel(BlockPos pos) {
            super(pos, ModBlocks.ALPHA_WARHEAD);
        }

        @Override
        public String id() {
            return "warhead";
        }

        @Override
        public boolean live() {
            return true;
        }

        @Override
        public View build(ServerPlayer p) {
            Site site = site(p);
            View v = new View("ALPHA WARHEAD").size("medium").style("hazard");
            if (site == null || !site.isDirector(p)) return v.add(El.text("Only the Site Director can operate the Alpha Warhead.", "red"));
            v.badge(Ui.clr(site, p));
            if (!site.hasUpgrade(Upgrade.ALPHA_WARHEAD)) {
                return v.add(El.text("Status: INERT", "gray")).add(El.text("Complete the Alpha Warhead upgrade at the terminal to arm it.", "gray"));
            }
            long left = Warhead.ticksLeft(site);
            if (left >= 0) {
                v.add(El.text("Status: ARMED", "red"));
                v.add(El.big("T-MINUS " + (left + 19) / 20, "red"));
                v.add(El.button("ABORT", "abort").style("confirm"));
                return v;
            }
            v.add(El.text("Status: STANDBY", "amber"));
            v.add(El.text("Blast radius: the whole facility. This cannot be undone.", "white"));
            v.add(El.button("ARM WARHEAD", "arm").style("danger")
                    .typed("Arm the Alpha Warhead? The countdown is 90 seconds. Type the site name to confirm: " + site.name(), site.name()));
            return v;
        }

        @Override
        public void handle(ServerPlayer p, String action, Args a) {
            Site site = site(p);
            if (site == null || !site.isDirector(p) || !site.hasUpgrade(Upgrade.ALPHA_WARHEAD)) return;
            switch (action) {
                case "arm" -> {
                    if (!a.text().equalsIgnoreCase(site.name())) {
                        Gui.toast(p, "The site name didn't match. Nothing happened.", true);
                        return;
                    }
                    Warhead.arm(site, pos);
                }
                case "abort" -> Warhead.abort(site);
                default -> {}
            }
        }
    }

    // ---------------------------------------------------------------- §10.4 Keycard reader setup

    public static class ReaderSetup extends AtBlock {
        public ReaderSetup(BlockPos pos) {
            super(pos, ModBlocks.KEYCARD_READER);
        }

        @Override
        public String id() {
            return "reader";
        }

        @Override
        public View build(ServerPlayer p) {
            BlockState state = p.level().getBlockState(pos);
            int level = state.hasProperty(KeycardReaderBlock.LEVEL) ? state.getValue(KeycardReaderBlock.LEVEL) : 1;
            View v = new View("KEYCARD READER SETUP").size("small");
            v.add(El.text("Required clearance level:", "gray"));
            List<El> row = new ArrayList<>();
            for (int i = 1; i <= 5; i++) row.add(El.button(String.valueOf(i), "set").arg("level", i).style(i == level ? "primary" : "plain"));
            v.add(El.row(row));
            v.add(El.text("Staff with enough clearance pass automatically.", "gray"));
            return v;
        }

        @Override
        public void handle(ServerPlayer p, String action, Args a) {
            Site site = site(p);
            if (site == null || !site.isDirector(p) || !action.equals("set")) return;
            BlockState state = p.level().getBlockState(pos);
            int level = Math.max(1, Math.min(5, a.num("level", 1)));
            p.level().setBlock(pos, state.setValue(KeycardReaderBlock.LEVEL, level), 3);
            SiteManager.addReader(pos);
            Gui.toast(p, "Reader now requires Level " + level + ".", false);
        }
    }

    // ---------------------------------------------------------------- §10.5 Ruined Mainframe / C.A.S.S.I.E.

    public static class Repair extends AtBlock {
        private final boolean mainframe;

        public Repair(BlockPos pos, boolean mainframe) {
            super(pos, mainframe ? ModBlocks.RUINED_MAINFRAME : ModBlocks.RUINED_CASSIE);
            this.mainframe = mainframe;
        }

        @Override
        public String id() {
            return "repair";
        }

        private int kits() {
            return mainframe ? RuinedMainframeBlock.REPAIR_KITS : RuinedCassieBlock.REPAIR_KITS;
        }

        private String blocker(ServerPlayer p) {
            if (!mainframe) {
                Site site = site(p);
                if (site == null || !site.isDirector(p)) return "Claim this area as your site first.";
                if (!site.hasUpgrade(Upgrade.CASSIE_PROGRAM) && !p.isCreative()) return "Unlock the C.A.S.S.I.E. Program upgrade first.";
            }
            if (!p.isCreative() && Cost.count(p, ModItems.REPAIR_KIT) < kits()) return "Needs " + kits() + " Repair Kits.";
            return null;
        }

        @Override
        public View build(ServerPlayer p) {
            View v = new View(mainframe ? "RUINED MAINFRAME" : "RUINED C.A.S.S.I.E.").size("small");
            v.add(El.text(mainframe ? "A dead Foundation Mainframe. Repairing it founds your site inside the old facility."
                    : "A broken C.A.S.S.I.E. from an abandoned facility. Cheaper to repair than to build.", "gray"));
            v.add(El.cost(Ui.costLines(p, Cost.of(ModItems.REPAIR_KIT, kits()))));
            String blocker = blocker(p);
            return v.add(El.button("Repair", "repair").style("confirm").disabled(blocker));
        }

        @Override
        public void handle(ServerPlayer p, String action, Args a) {
            if (!action.equals("repair") || blocker(p) != null) return;
            ServerLevel level = (ServerLevel) p.level();
            if (mainframe) {
                if (SiteFounding.repairRuinedMainframe(level, pos, p) && !p.isCreative()) Cost.take(p, ModItems.REPAIR_KIT, kits());
                Gui.close(p);
                return;
            }
            Site site = site(p);
            if (!p.isCreative()) Cost.take(p, ModItems.REPAIR_KIT, kits());
            BlockState old = level.getBlockState(pos);
            BlockState fresh = ModBlocks.CASSIE.defaultBlockState();
            // Keeps the way the ruined C.A.S.S.I.E. was turned.
            if (old.hasProperty(dev.projectredacted.block.CassieBlock.ROTATION))
                fresh = fresh.setValue(dev.projectredacted.block.CassieBlock.ROTATION, old.getValue(dev.projectredacted.block.CassieBlock.ROTATION));
            level.setBlock(pos, fresh, 3);
            site.cassie = pos.immutable();
            site.cassieHack = 0;
            site.cassieDisabled = false;
            Cassie.announce(site, "C.A.S.S.I.E. restored. Hello again, Director.");
            Gui.close(p);
        }
    }

    private BlockPanels() {}
}
