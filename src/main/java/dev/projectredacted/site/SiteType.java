package dev.projectredacted.site;

/** How the starting facility is laid out. Can be changed mid-game; it decides where generated rooms go. */
public enum SiteType {
    UNDERGROUND("Underground", "The Mainframe sits 20 blocks below the surface. Dig down as far as bedrock."),
    SURFACE_FLAT("Surface-Flat", "One sprawling ground floor."),
    SURFACE_TALL("Surface-Tall", "Stacked floors with no height cap.");

    public final String displayName;
    public final String description;

    SiteType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
}
