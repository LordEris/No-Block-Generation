package fr.lorderis.noblockgeneration.mixin;

import fr.lorderis.noblockgeneration.gen.DecorationTracker;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Marks everything a structure writes as protected, including the features a structure places
 * itself (village trees and decorations, jigsaw feature pool elements, ...).
 */
@Mixin(StructureStart.class)
public abstract class StructureStartMixin {
    @Inject(method = "placeInChunk", at = @At("HEAD"))
    private void nbg$pushStructure(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator,
                                   RandomSource random, BoundingBox box, ChunkPos chunkPos, CallbackInfo ci) {
        DecorationTracker.Context context = DecorationTracker.active();
        if (context != null) {
            context.pushStructure();
        }
    }

    @Inject(method = "placeInChunk", at = @At("RETURN"))
    private void nbg$popStructure(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator,
                                  RandomSource random, BoundingBox box, ChunkPos chunkPos, CallbackInfo ci) {
        DecorationTracker.Context context = DecorationTracker.active();
        if (context != null) {
            context.popStructure();
        }
    }
}
