package codechicken.lib.render.block;

import com.mojang.blaze3d.vertex.QuadInstance;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Stage a renderer's output until its complete layer has been validated. */
final class LayerQuadBatch implements BlockQuadOutput {
    private record Entry(float x,float y,float z,BakedQuad quad,QuadInstance instance){}
    private final ChunkSectionLayer layer;
    private final List<Entry> entries=new ArrayList<>();
    LayerQuadBatch(ChunkSectionLayer layer){this.layer=Objects.requireNonNull(layer);}
    @Override public void put(float x,float y,float z,BakedQuad quad,QuadInstance instance){
        if(quad.materialInfo().layer()!=layer)throw new IllegalArgumentException("Expected "+layer+" but renderer emitted "+quad.materialInfo().layer());
        if(!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(z))throw new IllegalArgumentException("Non-finite quad offset");
        var copy=new QuadInstance();
        for(int v=0;v<4;v++){copy.setColor(v,instance.getColor(v));copy.setLightCoords(v,instance.getLightCoords(v));}
        copy.setOverlayCoords(instance.overlayCoords());
        entries.add(new Entry(x,y,z,quad,copy));
    }
    void commit(BlockQuadOutput output){
        for(var e:entries)output.put(e.x,e.y,e.z,e.quad,e.instance);
    }
}