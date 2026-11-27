package codechicken.lib.internal.mixin;
import codechicken.lib.gui.modular.lib.GuiItemTint;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
@Mixin(GuiItemRenderState.class)
abstract class GuiItemRenderStateMixin implements GuiItemTint {
    @Unique private int ccl$tint=-1;
    public int ccl$getTint(){return ccl$tint;}
    public void ccl$setTint(int tint){ccl$tint=tint;}
}