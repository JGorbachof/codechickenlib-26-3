package codechicken.lib.model;

import com.mojang.blaze3d.vertex.QuadInstance;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import java.util.Objects;

/** Static baked geometry plus immutable per-draw light and overlay coordinates. */
public record LitQuad(BakedQuad quad,int light0,int light1,int light2,int light3,int overlay) {
    public LitQuad { Objects.requireNonNull(quad); }
    public int light(int vertex){
        return switch(vertex){case 0->light0;case 1->light1;case 2->light2;case 3->light3;default->throw new IndexOutOfBoundsException(vertex);};
    }
    public QuadInstance instance(){
        var instance=new QuadInstance();
        for(int v=0;v<4;v++)instance.setLightCoords(v,light(v));
        instance.setOverlayCoords(overlay);
        return instance;
    }
    public void emit(BlockQuadOutput output,float x,float y,float z){
        output.put(x,y,z,quad,instance());
    }
}