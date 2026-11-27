package codechicken.lib.internal.mixin;

import codechicken.lib.render.block.CCBlockRendererDispatcher;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockModelResolver.class)
abstract class BlockModelResolverMixin {
    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    private void ccl$renderEntity(BlockModelRenderState output, BlockState state, BlockDisplayContext display, CallbackInfo ci) {
        if (CCBlockRendererDispatcher.renderEntity(output,state,display)) ci.cancel();
    }
}