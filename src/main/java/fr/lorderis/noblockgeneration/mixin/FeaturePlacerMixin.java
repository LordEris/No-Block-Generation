package fr.lorderis.noblockgeneration.mixin;

import fr.lorderis.noblockgeneration.gen.DecorationTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.FeaturePlacer;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Tracks which feature is writing blocks right now.
 *
 * <p>Since 26.3 every placed feature goes through this method: the biome decoration of a chunk,
 * and every feature nested in another one ({@code overlay}, {@code sequence}, the random
 * selectors, {@code root_system}, {@code vegetation_patch}, ...), which all place their children as
 * placed features. The stack therefore always has the innermost feature on top.
 */
@Mixin(FeaturePlacer.class)
public abstract class FeaturePlacerMixin {
    @Inject(method = "place(Lnet/minecraft/world/level/levelgen/placement/PlacedFeature;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;Z)Z", at = @At("HEAD"))
    private void nbg$pushFeature(PlacedFeature placedFeature, RandomSource random, BlockPos origin, boolean biomeCheck,
                                 CallbackInfoReturnable<Boolean> cir) {
        DecorationTracker.Context context = DecorationTracker.active();
        if (context == null) {
            return;
        }
        Holder<Feature> feature = placedFeature.feature();
        // The type (minecraft:geode, minecraft:tree, ...) is always known. The id is only there when
        // the feature is registered (minecraft:amethyst_geode, minecraft:desert_well, ...); a feature
        // written inline inside another one has none.
        Identifier type = BuiltInRegistries.FEATURE_TYPE.getKey(feature.value().codec());
        Identifier id = feature.unwrapKey().map(ResourceKey::identifier).orElse(null);
        context.pushFeature(type, id);
    }

    @Inject(method = "place(Lnet/minecraft/world/level/levelgen/placement/PlacedFeature;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;Z)Z", at = @At("RETURN"))
    private void nbg$popFeature(PlacedFeature placedFeature, RandomSource random, BlockPos origin, boolean biomeCheck,
                                CallbackInfoReturnable<Boolean> cir) {
        DecorationTracker.Context context = DecorationTracker.active();
        if (context != null) {
            context.popFeature();
        }
    }
}
