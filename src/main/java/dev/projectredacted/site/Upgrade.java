package dev.projectredacted.site;

import dev.projectredacted.registry.ModBlocks;
import dev.projectredacted.registry.ModItems;
import dev.projectredacted.staff.StaffRole;
import dev.projectredacted.util.Cost;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Site upgrades bought at the terminal. Materials pay for upgrades; Administration upgrades also cost dollars.
 * Durations are in ticks (20 per second).
 */
public enum Upgrade {
    FACILITY_LEVEL_1(Category.FACILITY, "Facility Level 1", "Expands the claim to 6x6 chunks.", 1200, 0,
            () -> Cost.of(ModBlocks.PLASTIC_BLOCK.asItem(), 16, Items.IRON_BLOCK, 4, Items.REDSTONE_BLOCK, 2), s -> FacilityLevels.missing(s, 1)),
    FACILITY_LEVEL_2(Category.FACILITY, "Facility Level 2", "Expands the claim to 7x7 chunks.", 3600, 0,
            () -> Cost.of(ModBlocks.PLASTIC_BLOCK.asItem(), 48, Items.IRON_BLOCK, 12, Items.GOLD_BLOCK, 4, Items.REDSTONE_BLOCK, 6), s -> FacilityLevels.missing(s, 2)),
    FACILITY_LEVEL_3(Category.FACILITY, "Facility Level 3", "Expands the claim to 8x8 chunks.", 7200, 0,
            () -> Cost.of(ModBlocks.PLASTIC_BLOCK.asItem(), 96, Items.IRON_BLOCK, 24, Items.DIAMOND_BLOCK, 2, Items.REDSTONE_BLOCK, 12), s -> FacilityLevels.missing(s, 3)),

    CASSIE_PROGRAM(Category.ENGINEERING, "C.A.S.S.I.E. Program", "Lets you place, craft or repair C.A.S.S.I.E.", 2400, 0,
            () -> Cost.of(Items.REDSTONE_BLOCK, 4, Items.NOTE_BLOCK, 4, ModItems.PLASTIC, 32, Items.DIAMOND, 4), s -> s.level < 1 ? "Requires Facility Level 1" : null),
    GENERATOR_EFFICIENCY(Category.ENGINEERING, "Generator Efficiency", "Generators produce 25% more power.", 2400, 0,
            () -> Cost.of(Items.COPPER_BLOCK.asList().get(0), 8, Items.REDSTONE_BLOCK, 4), s -> null),
    WATER_RECYCLING(Category.ENGINEERING, "Water Recycling", "Water Pumps supply 25% more water.", 2400, 0,
            () -> Cost.of(Items.COPPER_BLOCK.asList().get(0), 8, Items.CAULDRON, 4), s -> null),

    ADVANCED_INSTRUMENTS(Category.RESEARCH, "Advanced Instruments", "Researchers work 25% faster.", 3600, 0,
            () -> Cost.of(Items.AMETHYST_SHARD, 16, Items.GLASS, 32, Items.GOLD_INGOT, 8), s -> s.countStaff(StaffRole.RESEARCHER) < 1 ? "Requires a Researcher" : null),
    CONTAINMENT_PROTOCOLS(Category.RESEARCH, "Containment Protocols", "Halves the chance of internal breaches.", 4800, 0,
            () -> Cost.of(ModItems.RESEARCH_DOCUMENT, 6, Items.IRON_BLOCK, 4), s -> s.level < 1 ? "Requires Facility Level 1" : null),

    SECURITY_INFRASTRUCTURE(Category.SECURITY, "Security Infrastructure", "Lowers raid chance by 20%.", 2400, 0,
            () -> Cost.of(Items.IRON_BLOCK, 8, Items.CROSSBOW, 2, ModBlocks.KEYCARD_READER.asItem(), 4), s -> null),
    MTF_CONTRACT(Category.SECURITY, "Mobile Task Force Contract", "MTF arrive 10 minutes into a raid, recontain SCPs afterwards, and can be called after a breach.", 6000, 0,
            () -> Cost.of(Items.IRON_BLOCK, 16, Items.DIAMOND, 8, Items.SHIELD, 4), s -> s.level < 1 ? "Requires Facility Level 1" : s.countStaff(StaffRole.SECURITY) < 2 ? "Requires 2 Security" : null),

    STREAMLINED_BUREAUCRACY(Category.ADMINISTRATION, "Streamlined Bureaucracy", "All upgrades cost 10% less.", 3600, 20,
            () -> Cost.of(Items.PAPER, 64, Items.BOOK, 8), s -> s.countStaff(StaffRole.ADMINISTRATION) < 1 ? "Requires an Administrator" : null),
    PERSONNEL_REPLACEMENT(Category.ADMINISTRATION, "Personnel Replacement Program", "Staff who die or leave are replaced over time.", 6000, 40,
            () -> Cost.of(Items.PAPER, 64, Items.EMERALD, 8), s -> s.level < 2 ? "Requires Facility Level 2" : null),
    PORTABLE_TABLET(Category.ENGINEERING, "Portable Tablet", "Use the terminal from anywhere with a Facility Tablet.", 3600, 0,
            () -> Cost.of(ModItems.PLASTIC, 16, Items.GLASS_PANE, 8, Items.REDSTONE, 16, Items.GOLD_INGOT, 4), s -> s.level < 2 ? "Requires Facility Level 2" : null),
    ALPHA_WARHEAD(Category.SECURITY, "Alpha Warhead", "Arms the on-site nuke. It's your call.", 12000, 50,
            () -> Cost.of(Items.TNT, 64, Items.NETHERITE_INGOT, 2, Items.REDSTONE_BLOCK, 16), s -> s.level < 3 ? "Requires Facility Level 3" : null);

    public enum Category {
        FACILITY("Facility"), ENGINEERING("Engineering"), RESEARCH("Research"), SECURITY("Security"), ADMINISTRATION("Administration");
        public final String displayName;

        Category(String displayName) {
            this.displayName = displayName;
        }
    }

    public final Category category;
    public final String displayName;
    public final String description;
    public final int baseTicks;
    public final int dollars;
    private final Supplier<Cost> cost;
    private final Function<Site, String> requirement;

    Upgrade(Category category, String displayName, String description, int baseTicks, int dollars, Supplier<Cost> cost, Function<Site, String> requirement) {
        this.category = category;
        this.displayName = displayName;
        this.description = description;
        this.baseTicks = baseTicks;
        this.dollars = dollars;
        this.cost = cost;
        this.requirement = requirement;
    }

    /** Why the upgrade can't be bought yet, or null if it can. */
    public String blocker(Site site) {
        if (site.hasUpgrade(this)) return "Already complete";
        if (site.pendingUpgrades.containsKey(this)) return "In progress";
        if (this == FACILITY_LEVEL_2 && site.level < 1) return "Requires Facility Level 1";
        if (this == FACILITY_LEVEL_3 && site.level < 2) return "Requires Facility Level 2";
        return requirement.apply(site);
    }

    /** Material cost after Administration, Storage Room and bureaucracy discounts. */
    public Cost cost(Site site) {
        double factor = 1.0 - site.adminDiscount();
        if (category == Category.ENGINEERING && site.hasFunctionalRoom("projectredacted:storage_room")) factor -= 0.2;
        return cost.get().scaled(Math.max(0.4, factor));
    }

    public int dollarCost(Site site) {
        return (int) Math.ceil(dollars * Math.max(0.4, 1.0 - site.adminDiscount()));
    }

    public int ticks(Site site) {
        return (int) (baseTicks * Math.max(0.3, 1.0 - site.adminDiscount() * 1.5));
    }

    public Item icon() {
        return switch (category) {
            case FACILITY -> ModBlocks.MAINFRAME.asItem();
            case ENGINEERING -> ModBlocks.GENERATOR_CORE.asItem();
            case RESEARCH -> ModItems.RESEARCH_DOCUMENT;
            case SECURITY -> Items.IRON_SWORD;
            case ADMINISTRATION -> Items.WRITABLE_BOOK;
        };
    }
}
