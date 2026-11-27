package codechicken.lib.internal.mixin;

import codechicken.lib.gui.modular.ModularGuiContainer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
abstract class ContainerHighlightMixin {
    @Inject(method={"extractSlotHighlightBack","extractSlotHighlightFront"},at=@At("HEAD"),cancellable=true)
    private void ccl$hideNativeHighlight(GuiGraphicsExtractor graphics,CallbackInfo ci){
        if((Object)this instanceof ModularGuiContainer<?> screen && screen.usesModularSlotRendering()) ci.cancel();
    }
}