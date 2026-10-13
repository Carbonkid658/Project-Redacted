package dev.projectredacted.staff;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Staff roles at launch, plus D-Class prisoners. More roles (Field Agents...) can be added here later. */
public enum StaffRole {
    RESEARCHER("Researcher", 2, 30, new String[]{"Junior Researcher", "Researcher", "Senior Researcher", "Head Researcher", "Research Director"}),
    SECURITY("Security", 2, 30, new String[]{"Security Recruit", "Security Officer", "Senior Officer", "Security Captain", "Security Chief"}),
    ENGINEER("Engineer", 1, 18, new String[]{"Apprentice Engineer", "Engineer", "Senior Engineer", "Lead Engineer", "Chief Engineer"}),
    JANITOR("Janitor", 1, 12, new String[]{"Janitor", "Custodian", "Senior Custodian", "Head Custodian", "Facilities Manager"}),
    ADMINISTRATION("Administration", 3, 3, new String[]{"Clerk", "Administrator", "Senior Administrator", "Site Manager", "Overseer's Aide"}),
    MEDICAL("Medical", 2, 12, new String[]{"Nurse", "Doctor", "Senior Doctor", "Chief Medical Officer", "Medical Director"}),
    DCLASS("D-Class", 0, 0, new String[]{"D-Class", "D-Class", "D-Class", "D-Class", "D-Class"});

    public final String displayName;
    public final int baseClearance;
    /** Relative chance of meeting this role in a bunker. Administration is rare by design. */
    public final int recruitWeight;
    private final String[] titles;

    StaffRole(String displayName, int baseClearance, int recruitWeight, String[] titles) {
        this.displayName = displayName;
        this.baseClearance = baseClearance;
        this.recruitWeight = recruitWeight;
        this.titles = titles;
    }

    public String title(int clearance) {
        return titles[Math.max(0, Math.min(4, clearance - 1))];
    }

    public String skin() {
        return name().toLowerCase();
    }

    public Item icon() {
        return switch (this) {
            case RESEARCHER -> Items.WRITABLE_BOOK;
            case SECURITY -> Items.IRON_SWORD;
            case ENGINEER -> Items.IRON_PICKAXE;
            case JANITOR -> Items.BRUSH;
            case ADMINISTRATION -> Items.PAPER;
            case MEDICAL -> Items.GOLDEN_APPLE;
            case DCLASS -> Items.DYE.orange();
        };
    }
}
