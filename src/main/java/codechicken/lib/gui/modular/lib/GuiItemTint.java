package codechicken.lib.gui.modular.lib;

import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;

/** Per-submission modulation of native premultiplied item atlas pixels. */
public interface GuiItemTint {
    int ccl$getTint();
    void ccl$setTint(int tint);

    static int premultiply(int argb){
        int a=argb>>>24;
        int r=((argb>>>16&255)*a+127)/255;
        int g=((argb>>>8&255)*a+127)/255;
        int b=((argb&255)*a+127)/255;
        return a<<24|r<<16|g<<8|b;
    }

    static BlitRenderState apply(GuiItemRenderState item,BlitRenderState blit){
        int tint=((GuiItemTint)(Object)item).ccl$getTint();
        if(tint==-1)return blit;
        int color=net.minecraft.util.ARGB.multiply(blit.color(),premultiply(tint));
        return new BlitRenderState(blit.pipeline(),blit.textureSetup(),blit.pose(),blit.x0(),blit.y0(),blit.x1(),blit.y1(),
                blit.u0(),blit.u1(),blit.v0(),blit.v1(),color,blit.scissorArea(),blit.bounds());
    }
}