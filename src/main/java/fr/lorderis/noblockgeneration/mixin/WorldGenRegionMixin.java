package fr.lorderis.noblockgeneration.mixin;

import fr.lorderis.noblockgeneration.gen.DecorationTracker;
import fr.lorderis.noblockgeneration.gen.KeepMask;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Every structure piece and every feature ends up here to write its blocks, so this is the single
 * choke point where "this block was placed by decoration" can be recorded.
 *
 * <p>Ore features are the one exception: vanilla writes them straight into the chunk sections
 * through {@code BulkSectionAccess}, bypassing this method. They are therefore never marked, and
 * are always stripped &mdash; which is exactly the wanted behaviour.
 */
@Mixin(WorldGenRegion.class)
public abstract class WorldGenRegionMixin {
    @Inject(
            method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("HEAD")
    )
    private void nbg$recordDecorationWrite(BlockPos pos, BlockState state, int flags, int recursionLeft,
                                           CallbackInfoReturnable<Boolean> cir) {
        DecorationTracker.Context context = DecorationTracker.active();
        if (context == null) {
            return;
        }

        WorldGenRegion self = (WorldGenRegion) (Object) this;
        int chunkX = SectionPos.blockToSectionCoord(pos.getX());
        int chunkZ = SectionPos.blockToSectionCoord(pos.getZ());
        // Features may spill into the neighbouring chunks of the region; anything further out is
        // refused by vanilla right after this injection point, so ignore it.
        if (!self.hasChunk(chunkX, chunkZ)) {
            return;
        }

        ChunkAccess chunk = self.getChunk(chunkX, chunkZ);
        if (chunk.isOutsideBuildHeight(pos.getY())) {
            return;
        }

        ((KeepMask) chunk).nbg$mark(
                KeepMask.index(chunk, pos.getX() & 15, pos.getY(), pos.getZ() & 15),
                context.shouldKeepWrites());
    }
}
