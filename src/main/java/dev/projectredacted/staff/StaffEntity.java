package dev.projectredacted.staff;

import dev.projectredacted.ai.ScanTargetGoal;
import dev.projectredacted.gui.Gui;
import dev.projectredacted.gui.screen.AssignScreen;
import dev.projectredacted.gui.screen.RecruitScreen;
import dev.projectredacted.gui.screen.StaffCardScreen;
import dev.projectredacted.gui.screen.StaffViews;
import dev.projectredacted.raid.ArmedNpcEntity;
import dev.projectredacted.registry.ModItems;
import dev.projectredacted.room.Room;
import dev.projectredacted.site.Cassie;
import dev.projectredacted.site.Site;
import dev.projectredacted.site.SiteManager;
import dev.projectredacted.util.Cost;
import dev.projectredacted.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.UUID;

/**
 * Site personnel and bunker survivors. A survivor stays put in its bunker until a Director recruits it; then
 * it belongs to a site, which keeps its {@link StaffRecord} (needs, level, assignment) even while unloaded.
 */
public class StaffEntity extends PathfinderMob {
    private static final EntityDataAccessor<String> ROLE = SynchedEntityData.defineId(StaffEntity.class, EntityDataSerializers.STRING);

    private boolean survivor = true;
    private String staffName = "";
    private Skill skill;
    private int recruitCost;
    private int tickCount2;

    public StaffEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        if (getNavigation() instanceof GroundPathNavigation nav) nav.setCanOpenDoors(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 3)
                .add(Attributes.FOLLOW_RANGE, 32);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ROLE, StaffRole.RESEARCHER.name());
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.2, true) {
            @Override
            public boolean canUse() {
                return role() == StaffRole.SECURITY && super.canUse();
            }
        });
        goalSelector.addGoal(2, new PanicGoal(this, 1.4) {
            @Override
            public boolean canUse() {
                return role() != StaffRole.SECURITY && super.canUse();
            }
        });
        goalSelector.addGoal(3, new OpenDoorGoal(this, true));
        goalSelector.addGoal(4, new StaffWorkGoal(this));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        targetSelector.addGoal(1, new HurtByTargetGoal(this) {
            @Override
            public boolean canUse() {
                return role() == StaffRole.SECURITY && super.canUse();
            }
        });
        targetSelector.addGoal(2, new ScanTargetGoal(this, 16, e -> role() == StaffRole.SECURITY && isThreat(e)));
    }

    private boolean isThreat(LivingEntity e) {
        if (e instanceof Enemy) return true;
        return e instanceof ArmedNpcEntity npc && npc.faction().raider;
    }

    // ---------------------------------------------------------------- identity

    public void setupSurvivor(StaffRole role, String name, Skill skill, int recruitCost) {
        this.staffName = name;
        this.skill = skill;
        this.recruitCost = recruitCost;
        setRole(role);
        refreshName();
    }

    public void setRole(StaffRole role) {
        entityData.set(ROLE, role.name());
        ItemStack weapon = switch (role) {
            case SECURITY -> new ItemStack(Items.IRON_SWORD);
            case ENGINEER -> new ItemStack(Items.IRON_PICKAXE);
            case JANITOR -> new ItemStack(Items.BRUSH);
            case RESEARCHER -> new ItemStack(ModItems.CLIPBOARD);
            default -> ItemStack.EMPTY;
        };
        setItemSlot(EquipmentSlot.MAINHAND, weapon);
        if (role == StaffRole.SECURITY) setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        setDropChance(EquipmentSlot.MAINHAND, 0f);
        setDropChance(EquipmentSlot.HEAD, 0f);
    }

    public StaffRole role() {
        try {
            return StaffRole.valueOf(entityData.get(ROLE));
        } catch (IllegalArgumentException e) {
            return StaffRole.RESEARCHER;
        }
    }

    /** Texture name for the renderer. */
    public String skinId() {
        return role().skin();
    }

    public boolean isSurvivor() {
        return survivor;
    }

    public StaffRecord record() {
        Site site = SiteManager.staffSite(getUUID());
        return site == null ? null : site.staff.get(getUUID());
    }

    public Site site() {
        return SiteManager.staffSite(getUUID());
    }

    public int clearance() {
        StaffRecord r = record();
        return r != null ? r.clearance : role().baseClearance;
    }

    public void refreshName() {
        StaffRecord r = record();
        String title = r != null ? r.title() : role().title(role().baseClearance);
        String n = r != null ? r.name : staffName;
        boolean named = r != null ? r.named : skill != null;
        setCustomName(Component.literal(title + " " + n).withStyle(named ? ChatFormatting.GOLD : ChatFormatting.WHITE));
        setCustomNameVisible(false);
    }

    /** Called when recruited or spawned for a site. */
    public void joinSite(Site site) {
        survivor = false;
        refreshName();
    }

    // ---------------------------------------------------------------- interaction

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (level().isClientSide()) return InteractionResult.SUCCESS;
        ServerPlayer sp = (ServerPlayer) player;
        if (survivor) {
            Gui.open(sp, new RecruitScreen(this));
            return InteractionResult.SUCCESS;
        }
        Site site = site();
        StaffRecord r = record();
        if (site == null || r == null) return InteractionResult.PASS;
        if (player.getItemInHand(hand).is(ModItems.CLIPBOARD)) {
            String blocker = StaffViews.orderBlocker(site, r, player);
            if (blocker != null) Msg.actionBar(player, r.name + ": " + blocker, ChatFormatting.RED);
            else Gui.open(sp, new AssignScreen(site, r));
            return InteractionResult.SUCCESS;
        }
        Gui.open(sp, new StaffCardScreen(this));
        return InteractionResult.SUCCESS;
    }

    public static String describeAssignment(Site site, Assignment a) {
        if (a.kind == Assignment.Kind.NONE) return "none";
        if (a.kind == Assignment.Kind.FOLLOW) return "following";
        Room room = site.room(a.room);
        return room == null ? "a demolished room" : room.displayName();
    }

    // ---------------------------------------------------------------- recruiting

    public String survivorName() {
        return staffName;
    }

    public Skill survivorSkill() {
        return skill;
    }

    public int recruitCost() {
        return recruitCost;
    }

    /** Why this player can't recruit this survivor right now, or null. */
    public String recruitBlocker(ServerPlayer player) {
        Site site = SiteManager.directedBy(player.getUUID());
        if (site == null) return "You need to direct a site first.";
        if (role() != StaffRole.DCLASS && site.housedStaff() >= site.beds) return "No free bed at your site. Build more housing.";
        if (recruitCost > 0 && !player.isCreative() && Cost.count(player, Items.DIAMOND) < recruitCost) {
            return "Needs " + recruitCost + " diamond" + (recruitCost > 1 ? "s" : "") + ".";
        }
        return null;
    }

    /** What the survivor says, depending on whether the player can take them. */
    public String recruitLine(ServerPlayer player) {
        Site site = SiteManager.directedBy(player.getUUID());
        if (site == null) return "A Foundation survivor? Come back when you have a site to take me to.";
        if (role() != StaffRole.DCLASS && site.housedStaff() >= site.beds) return "Where would I even sleep? Build more housing first.";
        if (recruitCost > 0) return "Times are hard. " + recruitCost + " diamond" + (recruitCost > 1 ? "s" : "") + " and I'm yours.";
        return "Ready when you are, Director.";
    }

    /** Recruits the survivor for the player's site. Returns false (with nothing taken) if they can't. */
    public boolean recruit(ServerPlayer player) {
        if (!survivor || recruitBlocker(player) != null) return false;
        Site site = SiteManager.directedBy(player.getUUID());
        if (recruitCost > 0 && !player.isCreative()) Cost.take(player, Items.DIAMOND, recruitCost);
        StaffRecord r = new StaffRecord();
        r.id = getUUID();
        r.name = staffName;
        r.role = role();
        r.clearance = role().baseClearance;
        r.skill = skill;
        r.named = skill != null;
        site.staff.put(r.id, r);
        joinSite(site);
        // The survivor heads to the site.
        Room entrance = site.firstEntrance();
        var dest = entrance != null ? entrance.center() : site.mainframe.above();
        ServerLevel overworld = SiteManager.overworld();
        if (overworld != null && level() == overworld) teleportTo(dest.getX() + 0.5, dest.getY(), dest.getZ() + 0.5);
        Msg.send(player, r.fullName() + " has joined your site.", ChatFormatting.GREEN);
        if (!Cassie.announce(site, "Welcome to the site, " + r.fullName() + ".")) site.log(r.fullName() + " joined the site.");
        return true;
    }

    // ---------------------------------------------------------------- ticking

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide() || ++tickCount2 % 40 != 0) return;
        Site site = site();
        StaffRecord r = site == null ? null : site.staff.get(getUUID());
        if (r == null) {
            // Their site is gone; they're a survivor again and can be recruited by someone else.
            if (!survivor) {
                survivor = true;
                refreshName();
            }
            return;
        }
        if (tickCount2 % 200 == 0) refreshName();
        Room here = site.roomAt(blockPosition());
        // Armory: boosts damage and armor of nearby security.
        if (r.role == StaffRole.SECURITY) {
            boolean armory = false;
            for (Room a : site.rooms("projectredacted:armory")) {
                if (a.working() && a.center().distSqr(blockPosition()) < 24 * 24) armory = true;
            }
            int strength = (armory ? 1 : 0) + (r.skill == Skill.SHARPSHOOTER ? 1 : 0) + (r.level >= 5 ? 1 : 0);
            if (strength > 0) addEffect(new MobEffectInstance(MobEffects.STRENGTH, 60, strength - 1, false, false));
            if (armory) addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 60, 0, false, false));
        }
        // Medical staff heal the injured in their ward.
        if (r.role == StaffRole.MEDICAL && here != null && here.working()
                && (here.type.equals("projectredacted:medbay") || here.type.equals("projectredacted:checkup_room") || here.type.equals("projectredacted:waiting_room"))) {
            float amount = (r.skill == Skill.MIRACLE_WORKER ? 4f : 2f) * (float) Math.max(0.5, r.productivity);
            for (LivingEntity patient : level().getEntitiesOfClass(LivingEntity.class, here.box().inflate(1),
                    e -> (e instanceof StaffEntity || e instanceof Player) && e.getHealth() < e.getMaxHealth())) {
                patient.heal(amount);
                r.addXp(0.5);
            }
        }
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    // ---------------------------------------------------------------- saving

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.putString("StaffRole", role().name());
        out.putBoolean("Survivor", survivor);
        out.putString("StaffName", staffName);
        out.putString("Skill", skill == null ? "" : skill.name());
        out.putInt("RecruitCost", recruitCost);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        try {
            entityData.set(ROLE, StaffRole.valueOf(in.getStringOr("StaffRole", "RESEARCHER")).name());
        } catch (IllegalArgumentException ignored) {
        }
        survivor = in.getBooleanOr("Survivor", true);
        staffName = in.getStringOr("StaffName", "");
        String s = in.getStringOr("Skill", "");
        try {
            skill = s.isEmpty() ? null : Skill.valueOf(s);
        } catch (IllegalArgumentException e) {
            skill = null;
        }
        recruitCost = in.getIntOr("RecruitCost", 0);
    }

    public UUID leaderId() {
        StaffRecord r = record();
        return r != null && r.assignment.kind == Assignment.Kind.FOLLOW ? r.assignment.leader : null;
    }
}
