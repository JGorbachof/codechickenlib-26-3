package codechicken.lib.internal.mixin;
import codechicken.lib.gui.modular.lib.GuiItemTint;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.GuiItemAtlas;
import net.minecraft.client.renderer.state.gui.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(GuiRenderer.class)
abstract class GuiItemAtlasTintMixin {
    @Redirect(method="submitBlitFromItemAtlas",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/state/gui/GuiRenderState;addBlitToCurrentLayer(Lnet/minecraft/client/renderer/state/gui/BlitRenderState;)V"))
    private void ccl$tintItemBlit(GuiRenderState state,BlitRenderState blit,GuiItemRenderState item,GuiItemAtlas.SlotView slot){
        state.addBlitToCurrentLayer(GuiItemTint.apply(item,blit));
    }
}