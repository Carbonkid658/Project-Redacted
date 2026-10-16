package dev.projectredacted.blockentities;

import com.geckolib.animatable.GeoBlockEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.util.GeckoLibUtil;
import dev.projectredacted.registry.ProjectRedactedBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NonNull;

/** Drives the Ventilation Fan's spinning blades (geckolib/animations/ventilation_fan.animation.json). */
public class VentilationFanBlockEntity extends BlockEntity implements GeoBlockEntity {
    private static final RawAnimation SPIN = RawAnimation.begin().thenLoop("animation.ventilation_fan.spin");

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    public VentilationFanBlockEntity(BlockPos pos, BlockState state) {
        super(ProjectRedactedBlockEntities.VENTILATION_FAN_BLOCK_ENTITY, pos, state);
    }

    @Override
    public void registerControllers(AnimatableManager.@NonNull ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>("spin", test -> test.setAndContinue(SPIN)));
    }

    @Override
    public @NonNull AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }
}
