package fr.lorderis.noblockgeneration.mixin;

import fr.lorderis.noblockgeneration.NoBlockGeneration;
import fr.lorderis.noblockgeneration.config.NbgConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stops water and lava from spreading.
 *
 * <p>A stripped world has nothing to hold a fluid, so any source that survives &mdash; a bucket, a
 * structure left flooded by config, an ocean in a chunk generated before the mod was installed
 * &mdash; drains endlessly into the void and drags the server down with it.
 *
 * <p>{@code spreadTo} is the single point where a fluid actually writes itself into a neighbouring
 * block, downward flow included, so cancelling it stops every kind of spreading at once. Existing
 * source blocks are untouched: this prevents movement, it does not delete anything.
 */
@Mixin(FlowingFluid.class)
public abstract class FlowingFluidMixin {
    @Inject(method = "spreadTo", at = @At("HEAD"), cancellable = true)
    private void nbg$stopSpreading(LevelAccessor level, BlockPos pos, BlockState blockState,
                                   Direction direction, FluidState fluidState, CallbackInfo ci) {
        NbgConfig config = NoBlockGeneration.config();
        if (!config.enabled || !config.preventFluidSpread) {
            return;
        }
        // Only in the dimensions the mod is responsible for; a modded dimension left alone should
        // keep working like vanilla.
        if (level instanceof Level actual && !config.appliesToDimension(actual.dimension().location())) {
            return;
        }
        ci.cancel();
    }
}
