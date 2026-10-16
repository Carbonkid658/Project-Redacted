package dev.projectredacted.raid;

import dev.projectredacted.ai.ScanTargetGoal;
import dev.projectredacted.registry.ModTags;
import dev.projectredacted.site.Site;
import dev.projectredacted.site.SiteManager;
import dev.projectredacted.staff.StaffEntity;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/** Raiders (GOC, Serpent's Hand, Chaos Insurgency) and Foundation MTF. Not saved: they only exist during a raid. */
public class ArmedNpcEntity extends PathfinderMob {
    private static final EntityDataAccessor<String> FACTION = SynchedEntityData.defineId(ArmedNpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> SKIN = SynchedEntityData.defineId(ArmedNpcEntity.class, EntityDataSerializers.STRING);

    public String siteId;
    /** SCP items a Serpent's Hand member is carrying off. Dropped on death for recontainment. */
    public final List<ItemStack> carried = new ArrayList<>();
    public long expireTick = Long.MAX_VALUE;
    private int checkTimer;

    public ArmedNpcEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        if (getNavigation() instanceof GroundPathNavigation nav) nav.setCanOpenDoors(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 30)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 5)
                .add(Attributes.ARMOR, 4)
                .add(Attributes.FOLLOW_RANGE, 48);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(FACTION, Faction.CHAOS_INSURGENCY.name());
        builder.define(SKIN, "");
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new dev.projectredacted.combat.GunAttackGoal(this, () -> true));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15, true));
        goalSelector.addGoal(3, new RaidObjectiveGoal(this));
        goalSelector.addGoal(4, new OpenDoorGoal(this, false));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new ScanTargetGoal(this, 20, this::isEnemy));
    }

    public void setup(Faction faction, Site site) {
        entityData.set(FACTION, faction.name());
        entityData.set(SKIN, dev.projectredacted.npc.Skins.pick(dev.projectredacted.npc.Skins.FACTION, faction.skin, 0, new java.util.Random()));
        this.siteId = site == null ? null : site.id;
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(faction.guns[getRandom().nextInt(faction.guns.length)].item()));
        setItemSlot(EquipmentSlot.HEAD, new ItemStack(switch (faction) {
            case GOC -> Items.IRON_HELMET;
            case MTF -> Items.DIAMOND_HELMET;
            case CHAOS_INSURGENCY -> Items.CHAINMAIL_HELMET;
            case SERPENTS_HAND -> Items.LEATHER_HELMET;
        }));
        setItemSlot(EquipmentSlot.CHEST, new ItemStack(faction == Faction.MTF ? Items.IRON_CHESTPLATE : Items.CHAINMAIL_CHESTPLATE));
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST}) setDropChance(slot, 0f);
        setCustomName(net.minecraft.network.chat.Component.literal(faction.displayName));
    }

    public Faction faction() {
        try {
            return Faction.valueOf(entityData.get(FACTION));
        } catch (IllegalArgumentException e) {
            return Faction.CHAOS_INSURGENCY;
        }
    }

    /** The skin pool (NPC revamp §4.1). */
    public String skinPool() {
        return faction().skin;
    }

    public String skin() {
        return entityData.get(SKIN);
    }

    public Site site() {
        return siteId == null ? null : SiteManager.byId(siteId);
    }

    /** Who this NPC fights. Raiding factions fight the site and each other; the GOC only goes after SCPs and hostiles. */
    private boolean isEnemy(LivingEntity e) {
        Faction f = faction();
        if (e instanceof ArmedNpcEntity other) {
            if (other.faction() == f) return false;
            return f == Faction.MTF ? other.faction().raider : true;
        }
        boolean scp = e.getType().builtInRegistryHolder().is(ModTags.SCP_ENTITIES);
        if (f == Faction.MTF) return e instanceof Enemy && !scp;
        if (f == Faction.GOC) return scp || e instanceof Enemy;
        if (scp) return false; // The Hand and the Insurgency want SCPs alive.
        Site site = site();
        if (e instanceof StaffEntity staff) return site != null && site.staff.containsKey(staff.getUUID());
        if (e instanceof Player p) return !p.isCreative() && !p.isSpectator() && (site == null || site.contains(p.blockPosition()) || site.isMember(p));
        return false;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide() || ++checkTimer % 100 != 0) return;
        Site site = site();
        boolean keep = faction() == Faction.MTF ? level().getGameTime() < expireTick
                : site != null && site.raid != null && site.raid.raiders.contains(getUUID());
        if (!keep) discard();
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }
}
