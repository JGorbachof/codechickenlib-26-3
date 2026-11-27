package codechicken.lib.internal.mixin;

import codechicken.lib.render.block.BreakingFrame;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
abstract class BlockBreakingMixin {
    @Inject(method="submitBlockDestroyAnimation",at=@At("HEAD"))
    private void ccl$breaking(PoseStack pose,SubmitNodeCollector collector,LevelRenderState state,CallbackInfo ci){
        var frame=state.getRenderData(BreakingFrame.KEY);
        if(frame!=null){
            frame.submit(pose,collector,state);
            state.setRenderData(BreakingFrame.KEY,null);
        }
        // The native method submits all remaining (noncustom) breaking states.
    }
}