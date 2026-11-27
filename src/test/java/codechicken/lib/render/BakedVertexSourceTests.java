package codechicken.lib.render;

import codechicken.lib.model.CachedFormat;
import codechicken.lib.model.Quad;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.client.textures.UnitTextureAtlasSprite;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Created by covers1624 on 1/1/22.
 */
public class BakedVertexSourceTests {

    static {
        // Init vertex attributes.
        CCRenderState.instance();
    }

    @Test
    public void testExpand() {
        BakedVertexSource source = new BakedVertexSource();
        assertEquals(0, source.getVertexCount());
        assertEquals(24, source.getVertices().length);
        source.ensureSpace(32);
        assertEquals(32, source.getVertices().length);
        source.ensureSpace(2048);
        assertEquals(2048, source.getVertices().length);
        source.ensureSpace(2048);
        assertEquals(2048, source.getVertices().length);
    }

    @Test
    public void testExpandWithQuad() {
        Quad unbaked = new Quad();
        unbaked.reset(CachedFormat.BLOCK);
        unbaked.setQuadOrientation(Direction.NORTH);
        unbaked.setTexture(UnitTextureAtlasSprite.INSTANCE);
        BakedQuad quad = unbaked.bake();

        BakedVertexSource source = new BakedVertexSource();
        source.reset(CachedFormat.BLOCK);
        assertEquals(0, source.getVertexCount());
        assertEquals(24, source.getVertices().length);

        source.put(quad);
        assertEquals(4, source.getVertexCount());
        assertEquals(24, source.getVertices().length);

        source.put(quad);
        assertEquals(8, source.getVertexCount());
        assertEquals(24, source.getVertices().length);

        source.put(quad);
        assertEquals(12, source.getVertexCount());
        assertEquals(24, source.getVertices().length);

        source.put(quad);
        assertEquals(16, source.getVertexCount());
        assertEquals(24, source.getVertices().length);

        source.put(quad);
        assertEquals(20, source.getVertexCount());
        assertEquals(24, source.getVertices().length);

        source.put(quad);
        assertEquals(24, source.getVertexCount());
        assertEquals(24, source.getVertices().length);

        source.put(quad);
        assertEquals(28, source.getVertexCount());
        assertEquals(28, source.getVertices().length);

        source.reset(CachedFormat.BLOCK);
        assertEquals(0, source.getVertexCount());
        assertEquals(28, source.getVertices().length);
    }
    @Test
    public void instanceCoordinatesStayScopedAcrossMixedQuads(){
        var builder=new codechicken.lib.render.buffer.BakedQuadVertexBuilder();
        builder.sprite(UnitTextureAtlasSprite.INSTANCE);
        for(int i=0;i<4;i++)builder.addVertex(i&1,i>>1,0).setColor(-1).setUv2(16+i,32+i).setUv1(3,10);
        var lit=builder.bakeWithInstances().getFirst();
        var source=new BakedVertexSource();
        source.put(lit.quad());source.put(lit);source.put(lit.quad());
        var state=CCRenderState.instance();state.reset();
        var lights=new java.util.ArrayList<Integer>();
        var overlays=new java.util.ArrayList<Integer>();
        var consumer=(com.mojang.blaze3d.vertex.VertexConsumer)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{com.mojang.blaze3d.vertex.VertexConsumer.class},(p,m,a)->{
            if(m.getName().equals("setLight"))lights.add((int)a[0]);
            if(m.getName().equals("setOverlay"))overlays.add((int)a[0]);
            return p;
        });
        state.bind(consumer,com.mojang.blaze3d.vertex.DefaultVertexFormat.ENTITY);
        state.brightness=123;state.overlay=456;
        state.setModel(source);state.render();
        assertEquals(12,lights.size());assertEquals(12,overlays.size());
        for(int i=0;i<12;i++){
            assertEquals(i>=4&&i<8?lit.light(i-4):123,lights.get(i));
            assertEquals(i>=4&&i<8?lit.overlay():456,overlays.get(i));
        }
        assertEquals(123,state.brightness);assertEquals(456,state.overlay);
        source.reset();source.put(lit.quad());
        org.junit.jupiter.api.Assertions.assertFalse(source.hasInstanceCoordinates(0));
        state.reset();
    }
}
