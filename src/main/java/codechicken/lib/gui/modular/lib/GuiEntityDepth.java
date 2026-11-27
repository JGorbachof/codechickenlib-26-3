package codechicken.lib.gui.modular.lib;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;

/** Per-preview depth setting, independent of live entity or renderer state. */
public interface GuiEntityDepth {
    boolean ccl$isFlat();
    void ccl$setFlat(boolean flat);

    static void scale(PoseStack pose,float x,float y,float z,PictureInPictureRenderState state,int guiScale){
        // The native scale includes GUI DPI; preserve the old logical -1 depth.
        pose.scale(x,y,state instanceof GuiEntityDepth depth&&depth.ccl$isFlat()?-guiScale:z);
    }
}