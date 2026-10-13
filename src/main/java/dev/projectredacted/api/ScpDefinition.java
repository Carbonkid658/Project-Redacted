package dev.projectredacted.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Describes one SCP to the site systems. Each SCP still has its own code for its behavior; this only tells the
 * site how to count it, contain it, trade it and recontain it.
 *
 * <pre>
 * ScpApi.register(new ScpDefinition("scp_173", "SCP-173", ObjectClass.EUCLID)
 *         .entity(MyEntities.SCP_173)
 *         .require(ContainmentRequirement.cellAtLeast("medium"))
 *         .require(ContainmentRequirement.observed())
 *         .breachChance(0.25)
 *         .mcdPrice(60));
 * </pre>
 * Also add the entity to the {@code projectredacted:scp} entity tag (or the item to {@code projectredacted:scp_items}).
 */
public class ScpDefinition {
    public final String id;
    public final String number;
    public ObjectClass objectClass;
    public EntityType<?> entityType;
    public Item item;
    public final List<ContainmentRequirement> requirements = new ArrayList<>();
    /** Chance per minute of breaching while any requirement is unmet. */
    public double breachChance = 0.1;
    /** Dollars Marshall, Carter & Dark charge for this SCP; 0 means they don't sell it. */
    public int mcdPrice;
    /** Whether a sentient SCP will follow the Serpent's Hand out during a raid. */
    public boolean followsHand;

    public ScpDefinition(String id, String number, ObjectClass objectClass) {
        this.id = id;
        this.number = number;
        this.objectClass = objectClass;
    }

    public ScpDefinition entity(EntityType<?> type) {
        this.entityType = type;
        return this;
    }

    public ScpDefinition item(Item item) {
        this.item = item;
        return this;
    }

    public ScpDefinition require(ContainmentRequirement requirement) {
        requirements.add(requirement);
        return this;
    }

    public ScpDefinition breachChance(double chance) {
        this.breachChance = chance;
        return this;
    }

    public ScpDefinition mcdPrice(int dollars) {
        this.mcdPrice = dollars;
        return this;
    }

    public ScpDefinition followsHand(boolean follows) {
        this.followsHand = follows;
        return this;
    }

    /** Requirements that aren't met right now. */
    public List<ContainmentRequirement> unmet(ContainmentContext ctx) {
        List<ContainmentRequirement> list = new ArrayList<>();
        for (ContainmentRequirement r : requirements) if (!r.met(ctx)) list.add(r);
        return list;
    }

    /** Places a purchased SCP (entity or item) at a delivery flare. Override for anything fancier. */
    public void deliver(ServerLevel level, BlockPos pos) {
        if (entityType != null) {
            Entity e = entityType.create(level, EntitySpawnReason.TRIGGERED);
            if (e != null) {
                e.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
                level.addFreshEntity(e);
            }
        } else if (item != null) {
            level.addFreshEntity(new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, new ItemStack(item)));
        }
    }
}
