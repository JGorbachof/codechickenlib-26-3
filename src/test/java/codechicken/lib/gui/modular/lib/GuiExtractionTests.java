package codechicken.lib.gui.modular.lib;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.*;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GuiExtractionTests{
    @Test void scissorRoundsOutwardButEmptyStaysEmpty(){
        assertEquals(new ScreenRectangle(1,2,4,5),ScissorHandler.clipBounds(1.25,2.5,3.25,4.1));
        assertEquals(ScreenRectangle.empty(),ScissorHandler.clipBounds(1.5,2.5,0,4));
        assertThrows(IllegalArgumentException.class,()->ScissorHandler.clipBounds(Double.NaN,0,1,1));
        assertThrows(IllegalArgumentException.class,()->ScissorHandler.clipBounds(0,0,Double.MAX_VALUE,1));
    }
    private static void quad(VertexConsumer v,float x,float y,float w,float h,int color){
        v.addVertex(x,y,0).setColor(color);v.addVertex(x+w,y,0).setColor(color);
        v.addVertex(x+w,y+h,0).setColor(color);v.addVertex(x,y+h,0).setColor(color);
    }
    @Test void nativeStateReceivesClippedIndependentImmutableQuads(){
        var elements=new ArrayList<GuiElementRenderState>();
        var clip=new ScreenRectangle(2,2,8,8);
        var recorder=new GuiVertexRecorder(()->clip,elements::add,type->null);
        recorder.setTint(0x80FFFFFF);
        var v=recorder.getBuffer(GuiRender.SOLID);
        quad(v,0,0,5,5,0xFFFFFFFF);quad(v,3,3,8,8,0xFF123456);
        quad(v,20,20,4,4,-1);quad(v,4.5F,4,0,5,-1);
        recorder.flush();
        assertThrows(IllegalStateException.class,()->v.setColor(0));

        assertEquals(2,elements.size());
        assertEquals(new ScreenRectangle(2,2,3,3),elements.get(0).bounds());
        assertEquals(new ScreenRectangle(3,3,7,7),elements.get(1).bounds());
        var colors=new ArrayList<Integer>();
        var target=(VertexConsumer)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{VertexConsumer.class},(p,m,a)->{
            if(m.getName().equals("setColor"))colors.add((int)a[0]);return p;
        });
        elements.get(0).buildVertices(target);elements.get(1).buildVertices(target);
        assertEquals(List.of(0x80FFFFFF,0x80FFFFFF,0x80FFFFFF,0x80FFFFFF,0x80123456,0x80123456,0x80123456,0x80123456),colors);
    }
}