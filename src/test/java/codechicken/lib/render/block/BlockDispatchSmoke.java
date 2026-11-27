package codechicken.lib.render.block;
import codechicken.lib.internal.ClientInit;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.*;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;
public final class BlockDispatchSmoke{
    static BakedQuad quad(ChunkSectionLayer layer){
        var b=new codechicken.lib.render.buffer.BakedQuadVertexBuilder();
        b.sprite(net.neoforged.neoforge.client.textures.UnitTextureAtlasSprite.INSTANCE);
        for(int i=0;i<4;i++)b.addVertex(i&1,i>>1,0).setNormal(0,0,1);
        var q=b.bake().getFirst();var old=q.materialInfo();
        var transparency=switch(layer){
            case SOLID->com.mojang.blaze3d.platform.Transparency.NONE;
            case CUTOUT->com.mojang.blaze3d.platform.Transparency.TRANSPARENT;
            case TRANSLUCENT->com.mojang.blaze3d.platform.Transparency.TRANSLUCENT;
        };
        var mat=BakedQuad.MaterialInfo.of(new net.minecraft.client.resources.model.sprite.Material.Baked(old.sprite(),false),transparency,old.tintIndex(),old.shadeDirectionOverride(),old.lightEmission(),old.ambientOcclusion());
        return new BakedQuad(q.position0(),q.position1(),q.position2(),q.position3(),q.packedUV0(),q.packedUV1(),q.packedUV2(),q.packedUV3(),q.direction(),mat,q.bakedNormals(),q.bakedColors());
    }
    public static void verify(){
        var state=Blocks.OAK_LEAVES.defaultBlockState();
        var display=BlockDisplayContext.create();
        var submitted=new java.util.concurrent.atomic.AtomicInteger();
        var fail=new java.util.concurrent.atomic.AtomicBoolean();
        var quads=new EnumMap<ChunkSectionLayer,BakedQuad>(ChunkSectionLayer.class);
        for(var layer:ChunkSectionLayer.values())quads.put(layer,quad(layer));
        BlockRenderingRegistry.init();
        BlockRenderingRegistry.registerRenderer(Blocks.OAK_LEAVES,new ICCBlockRenderer(){
            public boolean canHandleBlock(BlockAndTintGetter w,BlockPos p,BlockState s,ChunkSectionLayer l){return l==ChunkSectionLayer.CUTOUT;}
            public void renderBlock(BlockState s,BlockPos p,BlockAndTintGetter w,BlockQuadOutput o,float x,float y,float z,RandomSource r,ChunkSectionLayer l){o.put(x,y,z,quads.get(l),new QuadInstance());}
            public boolean canHandleEntity(BlockState s,BlockDisplayContext d){if(fail.get())throw new IllegalStateException("fixture predicate");return d==display;}
            public void renderEntity(BlockState s,BlockDisplayContext d,PoseStack pose,SubmitNodeCollector c,int light,int overlay,int outline){
                if(d!=display||overlay!=456||outline!=789)throw new IllegalStateException("Lost display context");
                submitted.incrementAndGet();
            }
        });
        var logical=new ArrayList<ChunkSectionLayer>();
        // Native opaque output collapses all physical layers; filter by logical layer first.
        CCBlockRendererDispatcher.renderBatched((x,y,z,q,i)->logical.add(q.materialInfo().layer()),0,0,0,null,BlockPos.ZERO,state,42,
            out->quads.values().forEach(q->out.put(0,0,0,q,new QuadInstance())));
        if(logical.size()!=3||!EnumSet.copyOf(logical).equals(EnumSet.allOf(ChunkSectionLayer.class)))throw new IllegalStateException("Lost/duplicated logical geometry "+logical);
        var output=new BlockModelRenderState();
        if(CCBlockRendererDispatcher.renderEntity(output,state,BlockDisplayContext.create())||!output.isEmpty())throw new IllegalStateException("Declined display was replaced");
        if(!CCBlockRendererDispatcher.renderEntity(output,state,display))throw new IllegalStateException("Accepted display not replaced");
        output.submit(new PoseStack(),null,123,456,789);
        if(submitted.get()!=1)throw new IllegalStateException("Missing entity callback");
        boolean oldCatch=ClientInit.catchBlockRenderExceptions,oldMessage=ClientInit.messagePlayerOnRenderExceptionCaught;
        try{
            ClientInit.catchBlockRenderExceptions=true;ClientInit.messagePlayerOnRenderExceptionCaught=false;fail.set(true);
            var fallback=new BlockModelRenderState();fallback.blockLightCoords=123;
            if(CCBlockRendererDispatcher.renderEntity(fallback,state,display)||fallback.blockLightCoords!=123)throw new IllegalStateException("Failed predicate mutated fallback state");
            ClientInit.catchBlockRenderExceptions=false;
            try{CCBlockRendererDispatcher.renderEntity(fallback,state,display);throw new AssertionError("Expected strict exception");}catch(IllegalStateException expected){}
        }finally{ClientInit.catchBlockRenderExceptions=oldCatch;ClientInit.messagePlayerOnRenderExceptionCaught=oldMessage;}
        System.out.println("CCL_BLOCK_DISPATCH_SMOKE_PASS: logical layer routing, context-aware entity submit and predicate fallback");
    }
}