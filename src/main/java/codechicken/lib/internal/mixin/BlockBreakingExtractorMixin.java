package codechicken.lib.internal.mixin;

import codechicken.lib.render.block.*;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.*;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.world.level.block.RenderShape;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.*;

@Mixin(LevelExtractor.class)
abstract class BlockBreakingExtractorMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow private ClientLevel level;
    @Inject(method="extractBlockDestroyAnimation",at=@At("RETURN"))
    private void ccl$extractBreaking(Camera camera,LevelRenderState state,CallbackInfo ci){
        state.setRenderData(BreakingFrame.KEY,null);
        if(level==null||!BlockRenderingRegistry.hasRenderers())return;
        var entries=new LinkedHashMap<BlockBreakingRenderState,BreakingFrame.Geometry>();
        for(var breaking:state.blockBreakingRenderStates){
            var block=breaking.blockState();
            if(block.getRenderShape()!=RenderShape.MODEL)continue;
            var pos=breaking.blockPos();
            var model=minecraft.getModelManager().getBlockStateModelSet().get(block);
            var parts=new ArrayList<BlockStateModelPart>();
            if(CCBlockRendererDispatcher.collectBreaking(model,level,pos,block,parts)){
                boolean translucent=parts.stream().anyMatch(p->(p.materialFlags()&1)!=0);
                entries.put(breaking,new BreakingFrame.Geometry(parts,translucent,block.getOffset(pos)));
            }
        }
        if(!entries.isEmpty())state.setRenderData(BreakingFrame.KEY,new BreakingFrame(entries));
    }
}