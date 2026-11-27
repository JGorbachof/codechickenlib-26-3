package codechicken.lib.render.block;
import com.mojang.blaze3d.vertex.QuadInstance;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.neoforged.neoforge.client.textures.UnitTextureAtlasSprite;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LayerQuadBatchTests {
    @Test void stagedGeometryOwnsInstanceDataAndRejectsWrongLayer(){
        var builder=new codechicken.lib.render.buffer.BakedQuadVertexBuilder();
        builder.sprite(UnitTextureAtlasSprite.INSTANCE);
        for(int v=0;v<4;v++)builder.addVertex(v&1,v>>1,0).setNormal(0,0,1);
        var quad=builder.bake().getFirst();
        var layer=quad.materialInfo().layer();
        var batch=new LayerQuadBatch(layer);
        var input=new QuadInstance();
        input.setColor(0,0xFF123456);input.setLightCoords(0,123);input.setOverlayCoords(42);
        batch.put(1,2,3,quad,input);
        input.setColor(0,0);input.setLightCoords(0,0);input.setOverlayCoords(0);
        var out=new ArrayList<QuadInstance>();
        batch.commit((x,y,z,q,instance)->{
            assertEquals(1F,x);assertEquals(2F,y);assertEquals(3F,z);assertSame(quad,q);out.add(instance);
        });
        assertEquals(1,out.size());assertEquals(0xFF123456,out.getFirst().getColor(0));
        assertEquals(123,out.getFirst().getLightCoords(0));assertEquals(42,out.getFirst().overlayCoords());
        var wrong=Arrays.stream(ChunkSectionLayer.values()).filter(l->l!=layer).findFirst().orElseThrow();
        assertThrows(IllegalArgumentException.class,()->new LayerQuadBatch(wrong).put(0,0,0,quad,input));
        assertThrows(IllegalArgumentException.class,()->batch.put(Float.NaN,0,0,quad,input));
    }
}