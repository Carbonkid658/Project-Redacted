package dev.projectredacted.npc;

/** Faction contacts the player can deal with. Trading never changes raid odds: there's no reputation system. */
public enum TraderKind {
    GOC_QUARTERMASTER("GOC Quartermaster", "goc_trader", true),
    WONDERTAINMENT_REP("Dr. Wondertainment Sales Rep", "wondertainment", true),
    MCD_CONTACT("Marshall, Carter & Dark Contact", "mcd", false),
    LIBRARIAN("Librarian", "librarian", true),
    LIBRARY_ENTRANCE("A Way to the Library", "library_way", false);

    public final String displayName;
    public final String skin;
    /** Fixed-spot traders stay forever; roaming contacts and Ways come and go. */
    public final boolean fixed;

    TraderKind(String displayName, String skin, boolean fixed) {
        this.displayName = displayName;
        this.skin = skin;
        this.fixed = fixed;
    }
}
