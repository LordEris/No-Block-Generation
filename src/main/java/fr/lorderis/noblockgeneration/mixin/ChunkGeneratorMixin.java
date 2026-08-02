package fr.lorderis.noblockgeneration.mixin;

import fr.lorderis.noblockgeneration.NoBlockGeneration;
import fr.lorderis.noblockgeneration.gen.DecorationTracker;
import fr.lorderis.noblockgeneration.gen.TerrainStripper;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Opens a recording window around the decoration of a chunk, then wipes everything that was not
 * recorded.
 *
 * <p>{@code applyBiomeDecoration} is the FEATURES step: structures and features are placed here, on
 * top of terrain that still exists. Running the wipe at its tail means trees and villages have
 * already found their ground, while lighting and the final heightmaps &mdash; computed after this
 * step &mdash; still see the emptied world.
 */
@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorMixin {
    @Inject(method = "applyBiomeDecoration", at = @At("HEAD"))
    private void nbg$beginDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager,
                                     CallbackInfo ci) {
        // Always clear first: worker threads are pooled and a feature that threw halfway through a
        // previous chunk could have left an unbalanced context behind.
        DecorationTracker.clear();
        if (NoBlockGeneration.appliesTo(level)) {
            DecorationTracker.start(NoBlockGeneration.config());
        }
    }

    @Inject(method = "applyBiomeDecoration", at = @At("RETURN"))
    private void nbg$endDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager,
                                   CallbackInfo ci) {
        DecorationTracker.Context context = DecorationTracker.active();
        if (context == null) {
            return;
        }
        DecorationTracker.clear();
        TerrainStripper.strip(chunk, context.config());
    }
}
