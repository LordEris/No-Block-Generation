package fr.lorderis.noblockgeneration.mixin;

import fr.lorderis.noblockgeneration.gen.DecorationTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Tracks which feature is writing blocks right now.
 *
 * <p>Container features ({@code random_selector}, {@code root_system}, {@code vegetation_patch},
 * ...) call back into this method for the feature they wrap, so the stack keeps the innermost one
 * on top: stripping {@code root_system} drops its rooted dirt while the azalea tree it plants,
 * being a feature of its own, is kept.
 */
@Mixin(ConfiguredFeature.class)
public abstract class ConfiguredFeatureMixin {
    @Inject(method = "place", at = @At("HEAD"))
    private void nbg$pushFeature(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random,
                                 BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        DecorationTracker.Context context = DecorationTracker.active();
        if (context == null) {
            return;
        }
        ConfiguredFeature<?, ?> self = (ConfiguredFeature<?, ?>) (Object) this;
        context.pushFeature(context.config().keepsFeature(self.feature()));
    }

    @Inject(method = "place", at = @At("RETURN"))
    private void nbg$popFeature(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random,
                                BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        DecorationTracker.Context context = DecorationTracker.active();
        if (context != null) {
            context.popFeature();
        }
    }
}
