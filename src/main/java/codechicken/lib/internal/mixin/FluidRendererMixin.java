package codechicken.lib.internal.mixin;

import codechicken.lib.render.block.CCBlockRendererDispatcher;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FluidRenderer.class)
abstract class FluidRendererMixin {
    @Inject(method = "tesselate", at = @At("HEAD"), cancellable = true)
    private void ccl$renderFluid(BlockAndTintGetter world, BlockPos pos, FluidRenderer.Output output,
            BlockState state, FluidState fluid, CallbackInfo ci) {
        if (CCBlockRendererDispatcher.renderFluid(world,pos,output,state,fluid)) ci.cancel();
    }
}