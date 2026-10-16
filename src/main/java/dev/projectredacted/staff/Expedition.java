package dev.projectredacted.staff;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A Field Agent team out on a trip (NPC revamp §4.5). Saved with the site. */
public class Expedition {
    public enum Length {
        SHORT("Short", 6000, 1, 0.05, 0.10, 0.02, 10),
        MEDIUM("Medium", 12000, 2, 0.15, 0.20, 0.05, 20),
        LONG("Long", 24000, 4, 0.30, 0.30, 0.10, 40);

        public final String displayName;
        /** Trip time in ticks, not counting the walk out. */
        public final int ticks;
        /** Research documents for a one-agent team. */
        public final int documents;
        /** Chance of an SCP for a one-agent team. */
        public final double scpChance;
        /** Each agent's chance of coming back hurt, or not at all, on a one-agent team. */
        public final double injury, death;
        public final int xp;

        Length(String displayName, int ticks, int documents, double scpChance, double injury, double death, int xp) {
            this.displayName = displayName;
            this.ticks = ticks;
            this.documents = documents;
            this.scpChance = scpChance;
            this.injury = injury;
            this.death = death;
            this.xp = xp;
        }

        /** Bigger teams find more... */
        public double scpChance(int team) {
            return Math.min(0.8, scpChance * (1 + 0.25 * (team - 1)));
        }

        public int documents(int team) {
            return (int) Math.round(documents * (1 + 0.5 * (team - 1)));
        }

        /** ...and look after each other. */
        public double teamFactor(int team) {
            return 1 / (1 + 0.3 * (team - 1));
        }
    }

    public int id;
    public List<UUID> agents = new ArrayList<>();
    public Length length = Length.SHORT;
    public long sentTick;
    /** Agents still walking out; the trip timer starts once the last one has left (or gave up walking). */
    public List<UUID> leaving = new ArrayList<>();
    /** When they're due back; 0 while agents are still walking out. */
    public long returnTick;

    public boolean away() {
        return returnTick > 0;
    }
}
