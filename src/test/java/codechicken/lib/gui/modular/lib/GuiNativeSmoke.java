package codechicken.lib.gui.modular.lib;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import java.util.*;
public final class GuiNativeSmoke {
    public static void verify(){
        var state=new GuiRenderState();
        var recorder=new GuiVertexRecorder(()->new ScreenRectangle(2,2,8,8),state::addGuiElement,type->null);
        var v=recorder.getBuffer(GuiRender.SOLID);
        for(int i=0;i<2;i++){
            int p=i*3;
            v.addVertex(p,p,0).setColor(-1);
            v.addVertex(p+5,p,0).setColor(-1);
            v.addVertex(p+5,p+5,0).setColor(-1);
            v.addVertex(p,p+5,0).setColor(-1);
        }
        recorder.flush();
        var elements=new ArrayList<GuiElementRenderState>();
        state.forEachElement(elements::add,GuiRenderState.TraverseRange.ALL);
        if(elements.size()!=2||!elements.get(0).bounds().equals(new ScreenRectangle(2,2,3,3))||!elements.get(1).bounds().equals(new ScreenRectangle(3,3,5,5)))
            throw new IllegalStateException("Native GUI order or clipping changed");
        System.out.println("CCL_GUI_SMOKE_PASS: native overlapping quad state and clipping");
    }
    public static void verifyItemTint(){
        var pose=new org.joml.Matrix3x2f();
        var item=new net.minecraft.client.renderer.state.gui.GuiItemRenderState(pose,new net.minecraft.client.renderer.item.TrackingItemStackRenderState(),0,0,null);
        var tint=(GuiItemTint)(Object)item;
        if(tint.ccl$getTint()!=-1)throw new IllegalStateException("Default item tint not white");
        var blit=new net.minecraft.client.renderer.state.gui.BlitRenderState(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA,
            net.minecraft.client.gui.render.TextureSetup.noTexture(),pose,0,0,16,16,0,1,0,1,-1,null,null);
        if(GuiItemTint.apply(item,blit)!=blit)throw new IllegalStateException("Default tint changed blit");
        tint.ccl$setTint(0x8080FF40);
        var colored=GuiItemTint.apply(item,blit);
        if(colored.color()!=0x80408020||!java.util.Objects.equals(blit.bounds(),colored.bounds())||blit.color()!=-1)
            throw new IllegalStateException("Incorrect premultiplied tint or state mutation");
        for(var e:java.util.Map.of("net.minecraft.client.gui.render.GuiRenderer","ccl$tintItemBlit",
            "net.minecraft.client.gui.render.pip.PictureInPictureRenderer","ccl$tintOversizedBlit",
            "net.minecraft.client.gui.GuiGraphicsExtractor","ccl$getGuiRenderState").entrySet()){
            try{
                var type=Class.forName(e.getKey(),false,GuiNativeSmoke.class.getClassLoader());
                if(java.util.Arrays.stream(type.getDeclaredMethods()).noneMatch(m->m.getName().contains(e.getValue())))throw new IllegalStateException("Missing tint hook "+e);
            }catch(ClassNotFoundException ex){throw new IllegalStateException(ex);}
        }
        System.out.println("CCL_GUI_ITEM_TINT_SMOKE_PASS: premultiplied atlas tint, unchanged bounds and three transformed hooks");
    }
    public static void verifyEntityDepth(){
        var state=new net.minecraft.client.renderer.entity.state.EntityRenderState();
        var preview=new net.minecraft.client.renderer.state.gui.pip.GuiEntityRenderState(state,new org.joml.Vector3f(),new org.joml.Quaternionf(),null,0,0,32,32,8,null);
        var depth=(GuiEntityDepth)(Object)preview;
        if(depth.ccl$isFlat())throw new IllegalStateException("Vanilla entity unexpectedly flat");
        var pose=new com.mojang.blaze3d.vertex.PoseStack();
        GuiEntityDepth.scale(pose,16,16,-16,preview,2);
        if(pose.last().pose().m22()!=-16)throw new IllegalStateException("Normal depth changed");
        depth.ccl$setFlat(true);
        pose=new com.mojang.blaze3d.vertex.PoseStack();
        GuiEntityDepth.scale(pose,16,16,-16,preview,2);
        if(pose.last().pose().m00()!=16||pose.last().pose().m11()!=16||pose.last().pose().m22()!=-2)
            throw new IllegalStateException("Flat depth not DPI aware");
        try{
            var type=Class.forName("net.minecraft.client.gui.render.pip.PictureInPictureRenderer",false,GuiNativeSmoke.class.getClassLoader());
            if(Arrays.stream(type.getDeclaredMethods()).noneMatch(m->m.getName().contains("ccl$entityDepth")))throw new IllegalStateException("Missing depth hook");
        }catch(ClassNotFoundException e){throw new IllegalStateException(e);}
        System.out.println("CCL_GUI_ENTITY_DEPTH_SMOKE_PASS: native state flag, default depth and DPI-aware flat transform");
    }
}