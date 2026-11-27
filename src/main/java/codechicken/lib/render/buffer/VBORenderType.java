package codechicken.lib.render.buffer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.oit.OitStage;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.function.*;

/** Cached GPU geometry; prepare outside a render pass, then draw within it. */
public class VBORenderType extends DelegateRenderType implements AutoCloseable {
    private final BiConsumer<VertexFormat,BufferBuilder> factory;
    private GpuBuffer vertices,indices;
    private MeshData.SortState sortState;
    private IndexType indexType;
    private int indexCount;
    private long generation;
    private boolean dirty=true,closed;
    public VBORenderType(RenderType parent,BiConsumer<VertexFormat,BufferBuilder> factory){
        super(parent);this.factory=Objects.requireNonNull(factory);
    }
    public void setDirty(){dirty=true;}

    public void rebuild(){
        RenderSystem.assertOnRenderThread();
        if(closed)throw new IllegalStateException("VBO is closed.");
        if(!dirty)return;
        GpuBuffer newVertices=null,newIndices=null;
        MeshData.SortState newSort=null;
        IndexType newType=IndexType.INT;
        int newCount=0;
        try(var builder=new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE)){
            var buffer=new BufferBuilder(builder,parent.primitiveTopology(),format());
            factory.accept(format(),buffer);
            try(var mesh=buffer.build()){
                if(mesh!=null){
                    var state=mesh.drawState();
                    newCount=state.indexCount();
                    newType=state.indexType();
                    var device=RenderSystem.getDevice();
                    newVertices=device.createBuffer(()->"CCL cached vertices",GpuBuffer.USAGE_VERTEX,mesh.vertexBuffer());
                    if(parent.sortOnUpload()&&state.primitiveTopology()==PrimitiveTopology.QUADS){
                        var centroids=new CompactVectorArray(state.vertexCount()/4);
                        MeshData.decodeQuadCentroids(mesh.vertexBuffer(),state.vertexCount(),state.format(),centroids,0);
                        newSort=new MeshData.SortState(centroids,newType);
                    }
                    ByteBuffer data=MemoryUtil.memAlloc(Math.multiplyExact(newCount,newType.bytes));
                    try{
                        if(mesh.indexBuffer()!=null)data.put(mesh.indexBuffer().duplicate());
                        else writeSequential(data,state.primitiveTopology(),newType,newCount);
                        data.flip();
                        newIndices=device.createBuffer(()->"CCL cached indices",GpuBuffer.USAGE_INDEX,data);
                    }finally{MemoryUtil.memFree(data);}
                }
            }
        }catch(Throwable t){
            if(newVertices!=null)newVertices.close();
            if(newIndices!=null)newIndices.close();
            throw t;
        }
        releaseBuffers();
        vertices=newVertices;indices=newIndices;sortState=newSort;indexType=newType;indexCount=newCount;
        generation++;dirty=false;
    }

    static void writeSequential(ByteBuffer data,PrimitiveTopology topology,IndexType type,int count){
        if(count<0||data.remaining()<Math.multiplyExact(count,type.bytes))throw new IllegalArgumentException("Invalid index buffer size.");
        if((topology==PrimitiveTopology.QUADS||topology==PrimitiveTopology.LINES)&&count%6!=0)throw new IllegalArgumentException("Incomplete expanded primitive.");
        int max=(topology==PrimitiveTopology.QUADS||topology==PrimitiveTopology.LINES)?(count/6)*4-1:count-1;
        if(type==IndexType.SHORT&&max>65535)throw new IllegalArgumentException("Index overflow.");
        IntConsumer emit=type==IndexType.SHORT?v->data.putShort((short)v):data::putInt;
        if(topology==PrimitiveTopology.QUADS||topology==PrimitiveTopology.LINES){
            for(int i=0;i<count/6;i++){
                int v=i*4;
                if(topology==PrimitiveTopology.QUADS){emit.accept(v);emit.accept(v+1);emit.accept(v+2);emit.accept(v+2);emit.accept(v+3);emit.accept(v);}
                else{emit.accept(v);emit.accept(v+1);emit.accept(v+2);emit.accept(v+3);emit.accept(v+2);emit.accept(v+1);}
            }
        }else for(int i=0;i<count;i++)emit.accept(i);
    }

    public PreparedDraw prepareDraw(@Nullable VertexSorting sorting){
        rebuild();
        if(sortState!=null){
            Objects.requireNonNull(sorting,"Translucent cached quads require a view-dependent sort.");
            ByteBuffer data=MemoryUtil.memAlloc(Math.multiplyExact(indexCount,indexType.bytes));
            try{
                sortState.writeSortedIndexBuffer(data,sorting);
                data.position(0).limit(indexCount*indexType.bytes);
                var replacement=RenderSystem.getDevice().createBuffer(()->"CCL sorted indices",GpuBuffer.USAGE_INDEX,data);
                indices.close();indices=replacement;generation++;
            }finally{MemoryUtil.memFree(data);}
        }
        return new PreparedDraw(parent.prepare(),generation);
    }
    public final class PreparedDraw{
        private final PreparedRenderType prepared;
        private final long preparedGeneration;
        private PreparedDraw(PreparedRenderType prepared,long generation){this.prepared=prepared;this.preparedGeneration=generation;}
        public void draw(RenderPass pass){draw(pass,null,List.of());}
        public void drawOit(RenderPass pass,OitStage stage){draw(pass,Objects.requireNonNull(stage),List.of());}
        private void draw(RenderPass pass,@Nullable OitStage stage,List<Consumer<RenderPass>> callbacks){
            RenderSystem.assertOnRenderThread();
            if(closed||dirty||generation!=preparedGeneration)throw new IllegalStateException("Cached draw invalidated; prepare again.");
            if(indexCount==0)return;
            var pipeline=stage==null?prepared.pipeline():Objects.requireNonNull(prepared.oitPipelineSet(),"No OIT pipeline").getPipeline(stage);
            pass.pushDebugGroup(()->prepared.name());
            try{
                pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
                var scissor=prepared.scissorState();
                if(scissor.enabled())pass.enableScissor(scissor.x(),scissor.y(),scissor.width(),scissor.height());
                else pass.disableScissor();
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("DynamicTransforms",prepared.dynamicTransforms());
                pass.setVertexBuffer(0,vertices.slice());
                for(var texture:prepared.textures())pass.setUniform(texture.name(),texture.textureView(),texture.sampler());
                pass.setIndexBuffer(indices,indexType);
                callbacks.forEach(callback->callback.accept(pass));
                pass.drawIndexed(indexCount,1,0,0,0);
            }finally{pass.popDebugGroup();}
        }
    }
    public WithCallbacks withCallback(Runnable action){return new WithCallbacks().withAction(action);}
    public final class WithCallbacks{
        private final List<Consumer<RenderPass>> callbacks=new ArrayList<>();
        public WithCallbacks withAction(Runnable action){callbacks.add(pass->action.run());return this;}
        public WithCallbacks withState(Consumer<RenderPass> state){callbacks.add(Objects.requireNonNull(state));return this;}
        public void draw(PreparedDraw draw,RenderPass pass){draw.draw(pass,null,List.copyOf(callbacks));}
        public void drawOit(PreparedDraw draw,RenderPass pass,OitStage stage){draw.draw(pass,stage,List.copyOf(callbacks));}
    }
    private void releaseBuffers(){
        if(vertices!=null){vertices.close();vertices=null;}
        if(indices!=null){indices.close();indices=null;}
    }
    @Override public void close(){RenderSystem.assertOnRenderThread();if(!closed){releaseBuffers();closed=true;generation++;}}
}
