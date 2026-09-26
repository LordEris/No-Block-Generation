package fr.lorderis.noblockgeneration.mixin;

import fr.lorderis.noblockgeneration.NoBlockGeneration;
import fr.lorderis.noblockgeneration.gen.SpawnPlatform;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Places the spawn platform once the levels are up.
 *
 * <p>{@code loadLevel} is where the world spawn gets picked and the spawn chunks get generated, so
 * its tail is the first moment both the position and the chunk holding it exist.
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
    @Inject(method = "loadLevel", at = @At("RETURN"))
    private void nbg$placeSpawnPlatform(CallbackInfo ci) {
        NoBlockGeneration.resetAnnouncements();
        SpawnPlatform.placeIfMissing((MinecraftServer) (Object) this, NoBlockGeneration.config());
    }
}
