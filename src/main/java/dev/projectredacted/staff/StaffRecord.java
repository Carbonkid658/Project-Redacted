package dev.projectredacted.staff;

import java.util.UUID;

/** Everything the site remembers about a staff member, even while their chunk is unloaded. */
public class StaffRecord {
    public UUID id;
    public String name;
    public StaffRole role;
    public int clearance;
    public int level = 1;
    public double xp;
    public double morale = 70;
    public double hunger = 100;
    public double energy = 100;
    public boolean named;
    public Skill skill;
    public Assignment assignment = new Assignment();
    /** Steps spent with a need fully unmet. Too many and they abandon the site. */
    public int neglect;

    public transient double productivity = 1;
    public transient int bedIndex = -1;

    public String title() {
        return role.title(clearance);
    }

    public String fullName() {
        return title() + " " + name;
    }

    public void addXp(double amount) {
        xp += amount;
        int next = 1 + (int) Math.sqrt(xp / 40.0);
        level = Math.min(10, Math.max(level, next));
    }
}
