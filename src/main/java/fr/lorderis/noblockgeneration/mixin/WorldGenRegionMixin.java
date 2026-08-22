package fr.lorderis.noblockgeneration.mixin;

import fr.lorderis.noblockgeneration.gen.DecorationTracker;
import fr.lorderis.noblockgeneration.gen.KeepMask;
import fr.lorderis.noblockgeneration.gen.TerrainStripper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
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
            at = @At("RETURN")
    )
    private void nbg$recordDecorationWrite(BlockPos pos, BlockState state, int flags, int recursionLeft,
                                           CallbackInfoReturnable<Boolean> cir) {
        DecorationTracker.Context context = DecorationTracker.active();
        if (context == null) {
            return;
        }
        // Injected on return so that only writes vanilla actually accepted get handled. A feature
        // reaching past the region's write radius is refused and must stay unmarked, otherwise the
        // terrain block sitting at that position would be spared for no reason.
        if (!cir.getReturnValueZ()) {
            return;
        }

        ChunkAccess chunk = ((WorldGenRegion) (Object) this).getChunk(
                SectionPos.blockToSectionCoord(pos.getX()),
                SectionPos.blockToSectionCoord(pos.getZ()));

        // An ImposterProtoChunk fronts a chunk that is already FULL: its writes go nowhere and it is
        // held for as long as the chunk stays loaded, so giving it a mask would pin a bitset for
        // nothing. Anything that is not a ProtoChunk at all is out of scope for the same reason.
        if (!(chunk instanceof ProtoChunk) || chunk instanceof ImposterProtoChunk) {
            return;
        }
        // A write above or below the build limits still reports success, but nothing was stored.
        if (chunk.isOutsideBuildHeight(pos.getY())) {
            return;
        }

        boolean keep = context.shouldKeepWrites();
        KeepMask mask = (KeepMask) chunk;

        if (mask.nbg$isStripped()) {
            // This neighbour was wiped at the end of its own decoration and has no pass left. Ground
            // material spilling into it now would float there forever, so undo it here instead.
            if (!keep) {
                TerrainStripper.undoLateWrite(chunk, pos, context.config());
            }
            return;
        }

        int index = KeepMask.index(chunk, pos.getX() & 15, pos.getY(), pos.getZ() & 15);
        if (keep && context.isInStructure()) {
            mask.nbg$markStructure(index);
        }
        mask.nbg$mark(index, keep);
    }
}
