package codechicken.lib.internal.mixin;
import codechicken.lib.gui.modular.lib.GuiItemTint;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.state.gui.*;
import net.minecraft.client.renderer.state.gui.pip.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(PictureInPictureRenderer.class)
abstract class GuiPipTintMixin {
    @Redirect(method="blitTexture",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/state/gui/GuiRenderState;addBlitToCurrentLayer(Lnet/minecraft/client/renderer/state/gui/BlitRenderState;)V"))
    private void ccl$tintOversizedBlit(GuiRenderState state,BlitRenderState blit,PictureInPictureRenderState pip,GuiRenderState original){
        state.addBlitToCurrentLayer(pip instanceof OversizedItemRenderState item?GuiItemTint.apply(item.guiItemRenderState(),blit):blit);
    }
    @Redirect(method="prepare",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V"))
    private void ccl$entityDepth(com.mojang.blaze3d.vertex.PoseStack pose,float x,float y,float z,
            PictureInPictureRenderState state,GuiRenderState gui,
            net.minecraft.client.renderer.feature.FeatureRenderDispatcher dispatcher,int guiScale){
        codechicken.lib.gui.modular.lib.GuiEntityDepth.scale(pose,x,y,z,state,guiScale);
    }
}