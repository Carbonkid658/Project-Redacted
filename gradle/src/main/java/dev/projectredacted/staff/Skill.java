package dev.projectredacted.staff;

/** Special skills that only named staff have. */
public enum Skill {
    GENIUS("Genius", "Researches 50% faster.", StaffRole.RESEARCHER),
    SHARPSHOOTER("Sharpshooter", "Hits much harder in a fight.", StaffRole.SECURITY),
    HANDY("Handy", "Repairs rooms twice as fast.", StaffRole.ENGINEER),
    CHEERFUL("Cheerful", "Lifts everyone's morale.", StaffRole.JANITOR),
    BUREAUCRAT("Bureaucrat", "Counts double for upgrade discounts.", StaffRole.ADMINISTRATION),
    MIRACLE_WORKER("Miracle Worker", "Heals twice as fast.", StaffRole.MEDICAL),
    TIRELESS("Tireless", "Needs half as much sleep.", null);

    public final String displayName;
    public final String description;
    /** The role this skill belongs to, or null for any role. */
    public final StaffRole role;

    Skill(String displayName, String description, StaffRole role) {
        this.displayName = displayName;
        this.description = description;
        this.role = role;
    }

    public static Skill forRole(StaffRole role, java.util.Random random) {
        if (random.nextInt(4) == 0) return TIRELESS;
        for (Skill s : values()) if (s.role == role) return s;
        return TIRELESS;
    }
}
