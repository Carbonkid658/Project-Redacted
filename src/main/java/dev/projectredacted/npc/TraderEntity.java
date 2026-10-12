package dev.projectredacted.npc;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** GOC quartermasters, the Wondertainment rep, MC&D contacts, the Librarian and Ways to the Library. */
public class TraderEntity extends PathfinderMob {
    private static final EntityDataAccessor<String> KIND = SynchedEntityData.defineId(TraderEntity.class, EntityDataSerializers.STRING);

    /** Game time this contact leaves, for roaming contacts and Ways. */
    public long leaveAt = Long.MAX_VALUE;
    /** Wondertainment restocks once per in-game day. */
    public long lastRestockDay = -1;
    public int[] stock = new int[0];

    public TraderEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 24)
                .add(Attributes.MOVEMENT_SPEED, 0.25);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(KIND, TraderKind.GOC_QUARTERMASTER.name());
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.5) {
            @Override
            public boolean canUse() {
                return kind() == TraderKind.MCD_CONTACT && super.canUse();
            }
        });
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    public void setKind(TraderKind kind) {
        entityData.set(KIND, kind.name());
        setCustomName(Component.literal(kind.displayName));
        if (kind == TraderKind.LIBRARY_ENTRANCE) {
            setNoGravity(true);
            setPermanentlyInvulnerable(true);
        }
    }

    public TraderKind kind() {
        try {
            return TraderKind.valueOf(entityData.get(KIND));
        } catch (IllegalArgumentException e) {
            return TraderKind.GOC_QUARTERMASTER;
        }
    }

    public String skinId() {
        return kind().skin;
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (level().isClientSide()) return InteractionResult.SUCCESS;
        ServerPlayer sp = (ServerPlayer) player;
        switch (kind()) {
            case LIBRARY_ENTRANCE -> Library.enter(sp);
            case LIBRARIAN -> LibraryGui.open(sp);
            default -> ShopGui.open(sp, this);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!(level() instanceof ServerLevel server)) return;
        if (kind() == TraderKind.LIBRARY_ENTRANCE && tickCount % 10 == 0) {
            server.sendParticles(ParticleTypes.PORTAL, getX(), getY() + 1, getZ(), 12, 0.4, 0.9, 0.4, 0.2);
        }
        if (server.getGameTime() > leaveAt) discard();
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return !kind().fixed && distance > 128 * 128;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return kind().fixed;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.putString("TraderKind", kind().name());
        out.putLong("LeaveAt", leaveAt);
        out.putLong("RestockDay", lastRestockDay);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        try {
            setKind(TraderKind.valueOf(in.getStringOr("TraderKind", TraderKind.GOC_QUARTERMASTER.name())));
        } catch (IllegalArgumentException ignored) {
        }
        leaveAt = in.getLongOr("LeaveAt", Long.MAX_VALUE);
        lastRestockDay = in.getLongOr("RestockDay", -1L);
    }
}
