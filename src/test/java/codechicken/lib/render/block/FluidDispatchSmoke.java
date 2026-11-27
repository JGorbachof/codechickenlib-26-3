package codechicken.lib.render.block;

import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.renderer.block.*;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.*;

public final class FluidDispatchSmoke{
    public static void verify(){
        BlockRenderingRegistry.init();
        var count=new AtomicInteger();var accept=new java.util.concurrent.atomic.AtomicBoolean(false);
        var fluid=Fluids.LAVA.defaultFluidState();var state=Blocks.LAVA.defaultBlockState();
        var pos=BlockPos.ZERO;FluidRenderer.Output output=layer->{throw new IllegalStateException("Unexpected output");};
        BlockRenderingRegistry.registerRenderer(Fluids.LAVA,new ICCBlockRenderer(){
            public boolean canHandleBlock(BlockAndTintGetter w,BlockPos p,BlockState s,ChunkSectionLayer l){return false;}
            public void renderBlock(BlockState s,BlockPos p,BlockAndTintGetter w,BlockQuadOutput o,float x,float y,float z,RandomSource r,ChunkSectionLayer l){}
            public boolean canHandleFluid(BlockAndTintGetter w,BlockPos p,BlockState s,FluidState f){count.incrementAndGet();return accept.get();}
            public void renderFluid(BlockPos p,BlockAndTintGetter w,FluidRenderer.Output o,BlockState s,FluidState f){}
        });
        var nativeCalls=new AtomicInteger();var vanillaCalls=new AtomicInteger();
        var result=CCBlockRendererDispatcher.renderSectionFluid(null,pos,output,state,fluid,()->{
            nativeCalls.incrementAndGet();
            if(CCBlockRendererDispatcher.renderFluid(null,pos,output,state,fluid))throw new IllegalStateException("Reentered custom fluid");
            if(CCBlockRendererDispatcher.renderFluid(null,pos.above(),output,state,fluid))throw new IllegalStateException("Unexpected adjacent override");
            return false;
        },()->{
            vanillaCalls.incrementAndGet();
            if(CCBlockRendererDispatcher.renderFluid(null,pos,output,state,fluid))throw new IllegalStateException("Reentered vanilla");
        });
        if(!result||count.get()!=2||nativeCalls.get()!=1||vanillaCalls.get()!=1)throw new IllegalStateException("Fluid fallback counts "+count);
        accept.set(true);
        if(!CCBlockRendererDispatcher.renderSectionFluid(null,pos,output,state,fluid,()->{throw new IllegalStateException("Native should be skipped");},()->{throw new IllegalStateException("Vanilla should be skipped");}))
            throw new IllegalStateException("CCL override not accepted");
        accept.set(false);
        try{
            CCBlockRendererDispatcher.renderSectionFluid(null,pos,output,state,fluid,()->{throw new IllegalArgumentException("fixture");},()->{});
            throw new IllegalStateException("Expected failure");
        }catch(IllegalArgumentException expected){}
        int before=count.get();
        if(CCBlockRendererDispatcher.renderFluid(null,pos,output,state,fluid)||count.get()!=before+1)
            throw new IllegalStateException("Fallback scope leaked after exception");
        System.out.println("CCL_FLUID_DISPATCH_SMOKE_PASS: one predicate per cell, adjacent call, native fallback and exception scope");
    }
}