package dev.projectredacted.gui.screen;

import dev.projectredacted.gui.*;
import dev.projectredacted.site.Site;
import dev.projectredacted.site.SiteFounding;
import dev.projectredacted.site.SiteManager;
import dev.projectredacted.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Screens shown once, at a moment (GUI design §11). */
public final class OneOffScreens {
    /** §11.1: after placing your first Mainframe. */
    public static class SiteFounded extends GuiScreen {
        private final Site site;

        public SiteFounded(Site site) {
            this.site = site;
        }

        @Override
        public String id() {
            return "site_founded";
        }

        @Override
        public View build(ServerPlayer p) {
            View v = new View("SITE FOUNDED · " + site.name()).size("medium");
            v.add(El.heading("Welcome, Director " + site.directorName + "."));
            v.add(El.text("The Foundation is gone. This site is yours to rebuild.", "gray"));
            v.add(El.text("You've been issued a Level 4 keycard. It opens Foundation Bunkers, where survivors wait to be recruited.", "amber"));
            v.add(El.box("Next steps", TerminalScreen.checklist(site)));
            v.add(El.button("Got it", "close").style("confirm"));
            return v;
        }

        @Override
        public void handle(ServerPlayer p, String action, Args args) {
            Gui.close(p);
        }
    }

    /** §11.2: founding a site whose claim holds a ruined Mainframe. */
    public static class OldFacility extends GuiScreen {
        private final Site site;
        private final SiteManager.RuinRecord ruin;

        public OldFacility(Site site, SiteManager.RuinRecord ruin) {
            this.site = site;
            this.ruin = ruin;
        }

        @Override
        public String id() {
            return "old_facility";
        }

        @Override
        public String invalid(ServerPlayer player) {
            return ruin.claimed || SiteManager.byId(site.id) != site ? "The old facility was already claimed." : null;
        }

        @Override
        public View build(ServerPlayer p) {
            return new View("OLD FACILITY DETECTED").size("small")
                    .add(El.text("A ruined Foundation Mainframe is inside your claim, at " + ruin.mainframe.toShortString()
                            + ". Its rooms are damaged but can be repaired.", "white"))
                    .add(El.row(El.button("Build new", "new"), El.button("Move into the old facility", "adopt").style("primary")));
        }

        @Override
        public void handle(ServerPlayer p, String action, Args args) {
            Gui.close(p);
            if (action.equals("adopt")) SiteFounding.adoptRuin((ServerLevel) p.level(), site, ruin, p);
            else if (action.equals("new")) {
                Msg.send(p, "Understood. Building a new facility instead.", ChatFormatting.GRAY);
                Gui.open(p, new SiteFounded(site));
            }
        }
    }

    /** §11.3: Lore Documents and Research Documents, on paper with a REDACTED stamp. */
    public static class Document extends GuiScreen {
        private final String title, body;

        /** Text starting with "tr:" is a translation key the client looks up. */
        public Document(String title, String body) {
            this.title = title;
            this.body = body;
        }

        @Override
        public String id() {
            return "document";
        }

        @Override
        public View build(ServerPlayer p) {
            return new View(title).style("paper").add(El.doc(title, body, "REDACTED"));
        }
    }

    private OneOffScreens() {}
}
