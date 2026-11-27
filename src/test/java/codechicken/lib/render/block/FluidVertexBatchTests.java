package codechicken.lib.render.block;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import java.lang.reflect.Proxy;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FluidVertexBatchTests {
    @Test void recordsIndependentLayersAndReplaysInOrder(){
        var batch=new FluidVertexBatch();
        var opaque=batch.getBuilder(ChunkSectionLayer.SOLID);
        var translucent=batch.getBuilder(ChunkSectionLayer.TRANSLUCENT);
        for(int i=0;i<4;i++){
            opaque.addVertex(i,0,0).setColor(0xFF123456).setUv(i,0).setUv2(17,239);
            translucent.addVertex(i,1,0).setColor(0x80123456).setUv(i,1).setUv2(31,240);
        }
        var events=new ArrayList<String>();
        batch.commit(layer->{
            events.add(layer.toString());
            return (VertexConsumer)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{VertexConsumer.class},(proxy,method,args)->{
                events.add(method.getName()+Arrays.toString(args));
                return proxy;
            });
        });
        assertEquals(34,events.size());
        assertEquals(ChunkSectionLayer.SOLID.toString(),events.get(0));
        assertEquals("addVertex[0.0, 0.0, 0.0]",events.get(1));
        assertEquals("setUv2[17, 239]",events.get(4));
        assertEquals(ChunkSectionLayer.TRANSLUCENT.toString(),events.get(17));
        assertEquals("addVertex[0.0, 1.0, 0.0]",events.get(18));
        assertEquals("setUv2[31, 240]",events.getLast());
    }

    @Test void incompleteOrInvalidGeometryDoesNotTouchOutput(){
        var batch=new FluidVertexBatch();
        var builder=batch.getBuilder(ChunkSectionLayer.SOLID);
        assertThrows(IllegalStateException.class,()->builder.setUv(0,0));
        assertThrows(IllegalArgumentException.class,()->builder.addVertex(Float.NaN,0,0));
        builder.addVertex(0,0,0);
        assertThrows(IllegalArgumentException.class,()->batch.commit(layer->{fail("Invalid batch must not request native buffer");return null;}));
        var empty=new FluidVertexBatch();
        empty.getBuilder(ChunkSectionLayer.TRANSLUCENT);
        empty.commit(layer->{fail("Empty layer must not request buffer");return null;});
    }
    @Test void missingAttributesInAnyVertexRejectBeforeAnyLayerCommits(){
        for(String missing:new String[]{"Color","UV0","UV2"})for(int missingVertex:new int[]{0,2,3}){
            var batch=new FluidVertexBatch();
            var good=batch.getBuilder(ChunkSectionLayer.SOLID);
            var bad=batch.getBuilder(ChunkSectionLayer.TRANSLUCENT);
            for(int i=0;i<4;i++){
                good.addVertex(i,0,0,-1,0,0,0,123,0,1,0);
                bad.addVertex(i,0,0);
                if(i!=missingVertex||!missing.equals("Color"))bad.setColor(-1);
                if(i!=missingVertex||!missing.equals("UV0"))bad.setUv(0,0);
                if(i!=missingVertex||!missing.equals("UV2"))bad.setLight(123);
            }
            var error=assertThrows(IllegalArgumentException.class,()->batch.commit(layer->{fail("Invalid batch touched native layer");return null;}));
            assertTrue(error.getMessage().contains(missing));
        }
    }
    @Test void nativeBlockBuffersAcceptCompleteBatch(){
        for(var layer:ChunkSectionLayer.values()){
            assertSame(com.mojang.blaze3d.vertex.DefaultVertexFormat.BLOCK,layer.vertexFormat());
            var batch=new FluidVertexBatch();
            var consumer=batch.getBuilder(layer);
            for(int i=0;i<4;i++)consumer.addVertex(i&1,0,i>>1,-1,i&1,i>>1,0,123,0,1,0);
            try(var bytes=new com.mojang.blaze3d.vertex.ByteBufferBuilder(256)){
                var nativeBuffer=new com.mojang.blaze3d.vertex.BufferBuilder(bytes,com.mojang.renderpearl.api.pipeline.PrimitiveTopology.QUADS,layer.vertexFormat());
                batch.commit(l->nativeBuffer);
                try(var mesh=nativeBuffer.buildOrThrow()){assertEquals(4,mesh.drawState().vertexCount());}
            }
        }
    }
}