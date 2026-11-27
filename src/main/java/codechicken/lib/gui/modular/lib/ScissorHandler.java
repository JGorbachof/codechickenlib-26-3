package codechicken.lib.gui.modular.lib;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Matrix3x2fStack;

/** Extraction-time clipping. Legacy GUI coordinates are screen-relative, not framebuffer pixels. */
public final class ScissorHandler {
    private final GuiGraphicsExtractor graphics;
    private int depth;
    public ScissorHandler(GuiGraphicsExtractor graphics) { this.graphics=graphics; }
    public void pushGuiScissor(double x,double y,double width,double height) {
        var bounds=clipBounds(x,y,width,height);
        Matrix3x2fStack pose=graphics.pose();
        pose.pushMatrix();
        try {
            pose.identity();
            graphics.enableScissor(bounds.left(),bounds.top(),bounds.right(),bounds.bottom());
            depth++;
        } finally { pose.popMatrix(); }
    }
    public void popScissor() {
        if (depth==0) throw new IllegalStateException("Scissor stack underflow.");
        graphics.disableScissor();depth--;
    }
    static net.minecraft.client.gui.navigation.ScreenRectangle clipBounds(double x,double y,double width,double height){
        if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(width)||!Double.isFinite(height))
            throw new IllegalArgumentException("Non-finite GUI scissor.");
        if(width<=0||height<=0)return net.minecraft.client.gui.navigation.ScreenRectangle.empty();
        double l=Math.floor(x),t=Math.floor(y),r=Math.ceil(x+width),b=Math.ceil(y+height);
        if(l<Integer.MIN_VALUE||t<Integer.MIN_VALUE||r>Integer.MAX_VALUE||b>Integer.MAX_VALUE||r-l>Integer.MAX_VALUE||b-t>Integer.MAX_VALUE)
            throw new IllegalArgumentException("GUI scissor outside integer bounds.");
        return new net.minecraft.client.gui.navigation.ScreenRectangle((int)l,(int)t,(int)(r-l),(int)(b-t));
    }
}