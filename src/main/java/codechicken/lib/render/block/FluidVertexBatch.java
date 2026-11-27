package codechicken.lib.render.block;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import java.util.*;
import java.util.function.Consumer;

/** Record a custom fluid renderer's vertex commands before touching a section buffer. */
final class FluidVertexBatch implements FluidRenderer.Output {
    private final Map<ChunkSectionLayer,Layer> layers=new LinkedHashMap<>();
    @Override public VertexConsumer getBuilder(ChunkSectionLayer layer){
        return layers.computeIfAbsent(Objects.requireNonNull(layer),key->new Layer());
    }
    void validate(){
        for(var entry:layers.entrySet())entry.getValue().validate(entry.getKey());
    }
    void commit(FluidRenderer.Output output){
        validate();
        layers.forEach((key,layer)->{
            if(layer.vertices==0)return;
            var consumer=output.getBuilder(key);
            layer.commands.forEach(command->command.accept(consumer));
        });
    }
    private static final class Layer implements VertexConsumer {
        final List<Consumer<VertexConsumer>> commands=new ArrayList<>();
        int vertices;
        private static final int COLOR=1,UV=2,LIGHT=4,REQUIRED=COLOR|UV|LIGHT;
        private int attributes,missing;
        void validate(ChunkSectionLayer layer){
            if(vertices%4!=0)throw new IllegalArgumentException("Incomplete fluid quad in "+layer);
            if(vertices==0)return;
            int absent=missing|(REQUIRED&~attributes);
            if(absent!=0)throw new IllegalArgumentException("Missing fluid vertex attributes in "+layer+":"+((absent&COLOR)!=0?" Color":"")+((absent&UV)!=0?" UV0":"")+((absent&LIGHT)!=0?" UV2":""));
        }
        private VertexConsumer attribute(int mask,Consumer<VertexConsumer> command){
            if(vertices==0)throw new IllegalStateException("No current fluid vertex.");
            attributes|=mask;commands.add(command);return this;
        }
        private static void finite(float... values){
            for(float v:values)if(!Float.isFinite(v))throw new IllegalArgumentException("Non-finite fluid vertex attribute.");
        }
        public VertexConsumer addVertex(float x,float y,float z){finite(x,y,z);if(vertices!=0)missing|=REQUIRED&~attributes;attributes=0;vertices++;commands.add(v->v.addVertex(x,y,z));return this;}
        public VertexConsumer setColor(int r,int g,int b,int a){return attribute(COLOR,v->v.setColor(r,g,b,a));}
        public VertexConsumer setColor(int argb){return attribute(COLOR,v->v.setColor(argb));}
        public VertexConsumer setUv(float u,float v){finite(u,v);return attribute(UV,c->c.setUv(u,v));}
        public VertexConsumer setUv1(int u,int v){return attribute(0,c->c.setUv1(u,v));}
        public VertexConsumer setUv2(int u,int v){return attribute(LIGHT,c->c.setUv2(u,v));}
        public VertexConsumer setUv3(float u,float v){finite(u,v);return attribute(0,c->c.setUv3(u,v));}
        public VertexConsumer setNormal(float x,float y,float z){finite(x,y,z);return attribute(0,c->c.setNormal(x,y,z));}
        public VertexConsumer setLineWidth(float width){finite(width);return attribute(0,c->c.setLineWidth(width));}
    }
}