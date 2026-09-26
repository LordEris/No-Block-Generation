package fr.lorderis.noblockgeneration.mixin;

import fr.lorderis.noblockgeneration.NoBlockGeneration;
import fr.lorderis.noblockgeneration.gen.SpawnPlatform;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Server startup hooks.
 *
 * <p>{@code loadLevel} is where the world spawn gets picked and the spawn chunks get generated: its
 * head comes before any chunk, and its tail is the first moment both the spawn position and the
 * chunk holding it exist, which is when the spawn platform is placed.
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
    @Inject(method = "loadLevel", at = @At("HEAD"))
    private void nbg$onServerStarting(CallbackInfo ci) {
        NoBlockGeneration.onServerStarting((MinecraftServer) (Object) this);
    }

    @Inject(method = "loadLevel", at = @At("RETURN"))
    private void nbg$placeSpawnPlatform(CallbackInfo ci) {
        SpawnPlatform.placeIfMissing((MinecraftServer) (Object) this, NoBlockGeneration.config());
    }
}
