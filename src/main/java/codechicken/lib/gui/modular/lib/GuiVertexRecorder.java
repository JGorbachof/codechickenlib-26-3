package codechicken.lib.gui.modular.lib;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.resources.Identifier;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.jetbrains.annotations.Nullable;

/** Collects transformed GUI quads during extraction; GPU submission uses immutable snapshots. */
final class GuiVertexRecorder {
    private final java.util.function.Supplier<ScreenRectangle> scissor;
    private final java.util.function.Consumer<GuiElementRenderState> submit;
    private record Snapshot(float x,float y,int color,float u,float v){}
    private final Function<RenderType, Identifier> textureLookup;
    private RenderType type;
    private final List<Vertex> vertices = new ArrayList<>();
    private int tint = -1;
    private long generation;

    private static final class Vertex {
        final float x,y,z;
        int color = -1;
        float u,v;
        Vertex(float x,float y,float z) { this.x=x;this.y=y;this.z=z; }
    }

    GuiVertexRecorder(GuiGraphicsExtractor graphics, Function<RenderType, Identifier> textureLookup) {
        this(graphics::peekScissorStack,graphics::submitGuiElementRenderState,textureLookup);
    }
    GuiVertexRecorder(java.util.function.Supplier<ScreenRectangle> scissor,java.util.function.Consumer<GuiElementRenderState> submit,Function<RenderType,Identifier> textureLookup){
        this.scissor=scissor;this.submit=submit;this.textureLookup=textureLookup;
    }

    void setTint(int tint) { flush();this.tint=tint; }

    VertexConsumer getBuffer(RenderType requested) {
        if (requested.primitiveTopology()!=com.mojang.renderpearl.api.pipeline.PrimitiveTopology.QUADS) throw new IllegalArgumentException("GUI recorder requires quad topology.");
        if (type != requested) { flush();type=requested; }
        long epoch=generation;
        return new VertexConsumer() {
            private Vertex current;
            private void check(){if(epoch!=generation)throw new IllegalStateException("GUI consumer used after flush.");}
            private void checkVertex(){check();if(current==null)throw new IllegalStateException("No current GUI vertex.");}
            @Override public VertexConsumer addVertex(float x,float y,float z) {
                check();
                if(!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(z))throw new IllegalArgumentException("Non-finite GUI vertex.");
                current=new Vertex(x,y,z);current.color=tint;vertices.add(current);return this;
            }
            @Override public VertexConsumer setColor(int color) {
                checkVertex();current.color=net.minecraft.util.ARGB.multiply(color,tint);return this;
            }
            @Override public VertexConsumer setColor(int r,int g,int b,int a) { return setColor(a<<24|r<<16|g<<8|b); }
            @Override public VertexConsumer setUv(float u,float v) { checkVertex();if(!Float.isFinite(u)||!Float.isFinite(v))throw new IllegalArgumentException("Non-finite GUI UV.");current.u=u;current.v=v;return this; }
            @Override public VertexConsumer setUv1(int u,int v) { throw new UnsupportedOperationException("GUI geometry has no overlay UV."); }
            @Override public VertexConsumer setUv2(int u,int v) { throw new UnsupportedOperationException("GUI geometry has no lightmap UV."); }
            @Override public VertexConsumer setUv3(float u,float v) { throw new UnsupportedOperationException("GUI geometry has no glint UV."); }
            @Override public VertexConsumer setNormal(float x,float y,float z) { throw new UnsupportedOperationException("GUI geometry has no normal."); }
            @Override public VertexConsumer setLineWidth(float width) { throw new UnsupportedOperationException("GUI geometry has no line width."); }
        };
    }

    void flush() {
        if (vertices.isEmpty()) { type=null;generation++;return; }
        if (vertices.size()%4!=0) throw new IllegalStateException("Incomplete GUI quad.");
        var pipeline=type.pipeline();
        boolean textured=type.format().contains("UV0");
        Identifier texture=textureLookup.apply(type);
        if (textured && texture==null) throw new IllegalArgumentException("Unregistered GUI texture for "+type);
        TextureSetup setup;
        if (texture!=null) {
            var tex=Minecraft.getInstance().getTextureManager().getTexture(texture);
            setup=TextureSetup.singleTexture(tex.getTextureView(),tex.getSampler());
        } else setup=TextureSetup.noTexture();
        var clip=scissor.get();
        // Submit separate quads so the native overlap sorter can maintain their local order.
        for (int i=0;i<vertices.size();i+=4) {
            var quad=vertices.subList(i,i+4).stream().map(v->new Snapshot(v.x,v.y,v.color,v.u,v.v)).toList();
            float minX=Float.POSITIVE_INFINITY,minY=minX,maxX=Float.NEGATIVE_INFINITY,maxY=maxX;
            for (var v:quad) {minX=Math.min(minX,v.x);minY=Math.min(minY,v.y);maxX=Math.max(maxX,v.x);maxY=Math.max(maxY,v.y);}
            if(maxX<=minX||maxY<=minY)continue;
            int x=(int)Math.floor(minX),y=(int)Math.floor(minY);
            ScreenRectangle bounds=new ScreenRectangle(x,y,Math.max(0,(int)Math.ceil(maxX)-x),Math.max(0,(int)Math.ceil(maxY)-y));
            if (clip!=null) bounds=bounds.intersection(clip);
            if (bounds==null || bounds.width()==0 || bounds.height()==0) continue;
            var finalBounds=bounds;
            submit.accept(new GuiElementRenderState() {
                @Override public void buildVertices(VertexConsumer out) {
                    for (var v:quad) {
                        var consumer=out.addVertex(v.x,v.y,0).setColor(v.color);
                        if (textured) consumer.setUv(v.u,v.v);
                    }
                }
                @Override public com.mojang.renderpearl.api.pipeline.RenderPipeline pipeline(){return pipeline;}
                @Override public TextureSetup textureSetup(){return setup;}
                @Override public @Nullable ScreenRectangle scissorArea(){return clip;}
                @Override public ScreenRectangle bounds(){return finalBounds;}
            });
        }
        vertices.clear();type=null;generation++;
    }
}