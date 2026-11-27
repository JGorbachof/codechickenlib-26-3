package codechicken.lib.internal.mixin;
import codechicken.lib.gui.modular.lib.GuiEntityDepth;
import net.minecraft.client.renderer.state.gui.pip.GuiEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
@Mixin(GuiEntityRenderState.class)
abstract class GuiEntityRenderStateMixin implements GuiEntityDepth {
    @Unique private boolean ccl$flat;
    public boolean ccl$isFlat(){return ccl$flat;}
    public void ccl$setFlat(boolean flat){ccl$flat=flat;}
}