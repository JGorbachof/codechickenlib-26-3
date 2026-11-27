package codechicken.lib.render.block;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.state.level.BlockBreakingRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Extract custom destruction geometry on the world-owning thread; submit without a world reference. */
public record BreakingFrame(Map<BlockBreakingRenderState,Geometry> entries) {
    public static final ContextKey<BreakingFrame> KEY=new ContextKey<>(Identifier.fromNamespaceAndPath("codechickenlib","breaking_frame"));
    public BreakingFrame { entries=Collections.unmodifiableMap(new LinkedHashMap<>(entries)); }
    public record Geometry(List<BlockStateModelPart> parts,boolean translucent,Vec3 offset) {
        public Geometry { parts=List.copyOf(parts);Objects.requireNonNull(offset); }
    }
    public void submit(PoseStack pose,SubmitNodeCollector collector,LevelRenderState state){
        var camera=state.cameraRenderState.pos;
        for(var breaking:state.blockBreakingRenderStates){
            var geometry=entries.get(breaking);
            if(geometry==null)continue;
            var pos=breaking.blockPos();
            pose.pushPose();
            try{
                pose.translate(pos.getX()-camera.x(),pos.getY()-camera.y(),pos.getZ()-camera.z());
                pose.translate(geometry.offset());
                collector.submitBreakingBlockModel(pose,geometry.parts(),breaking.progress(),geometry.translucent());
            }finally{pose.popPose();}
        }
        state.blockBreakingRenderStates.removeIf(entries::containsKey);
    }
}