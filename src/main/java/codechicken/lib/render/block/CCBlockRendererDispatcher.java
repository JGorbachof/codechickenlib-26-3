package codechicken.lib.render.block;

import codechicken.lib.internal.ClientInit;
import codechicken.lib.internal.ExceptionMessageEventHandler;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.*;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Matrix4f;
import org.joml.Vector3fc;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Replacement dispatcher for the split world, fluid and extracted-entity rendering paths. */
public final class CCBlockRendererDispatcher {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final AtomicLong LAST_MESSAGE = new AtomicLong();

    private CCBlockRendererDispatcher() {}

    public static void renderBatched(ModelBlockRenderer vanilla, BlockQuadOutput output, float x, float y, float z,
            BlockAndTintGetter world, BlockPos pos, BlockState state, BlockStateModel model, long seed) {
        renderBatched(output,x,y,z,world,pos,state,seed,remaining->vanilla.tesselateBlock(remaining,x,y,z,world,pos,state,model,seed));
    }

    static void renderBatched(BlockQuadOutput output,float x,float y,float z,BlockAndTintGetter world,BlockPos pos,
            BlockState state,long seed,Consumer<BlockQuadOutput> vanilla){
        // Override logical material layers before the native output performs any
        // physical layer remapping (e.g. opaque leaves).
        EnumSet<ChunkSectionLayer> overridden=EnumSet.noneOf(ChunkSectionLayer.class);
        if(BlockRenderingRegistry.hasBlockRenderer(state.getBlock())){
            for(var layer:ChunkSectionLayer.values()){
                LayerQuadBatch batch;
                try{
                    var renderer=BlockRenderingRegistry.findFor(state.getBlock(),r->r.canHandleBlock(world,pos,state,layer));
                    if(renderer==null)continue;
                    batch=new LayerQuadBatch(layer);
                    renderer.renderBlock(state,pos,world,batch,x,y,z,RandomSource.create(seed),layer);
                }catch(Throwable t){
                    if(!ClientInit.catchBlockRenderExceptions)throw t;
                    handleCaughtException(t,state,pos,world);
                    continue; // No partial custom output; allow vanilla for this layer.
                }
                batch.commit(output);
                overridden.add(layer);
            }
        }
        if(overridden.size()==ChunkSectionLayer.values().length)return;
        try{
            BlockQuadOutput remaining=overridden.isEmpty()?output:(qx,qy,qz,quad,instance)->{
                if(!overridden.contains(quad.materialInfo().layer()))output.put(qx,qy,qz,quad,instance);
            };
            vanilla.accept(remaining);
        }catch(Throwable t){
            if(!ClientInit.catchBlockRenderExceptions)throw t;
            handleCaughtException(t,state,pos,world);
        }
    }

    private record FluidFallback(BlockAndTintGetter world,BlockPos pos,FluidRenderer.Output output,BlockState state,FluidState fluid){
        boolean matches(BlockAndTintGetter w,BlockPos p,FluidRenderer.Output o,BlockState s,FluidState f){
            return world==w&&pos.equals(p)&&output==o&&state==s&&fluid==f;
        }
    }
    private static final ThreadLocal<FluidFallback> FLUID_FALLBACK=new ThreadLocal<>();

    /**
     * The section compiler has already tried CCL before calling NeoForge's custom
     * renderer. Complete its optional vanilla fallback in the same scoped call,
     * so the FluidRenderer HEAD hook cannot evaluate the same predicates twice.
     */
    public static boolean renderSectionFluid(BlockAndTintGetter world,BlockPos pos,FluidRenderer.Output output,
            BlockState state,FluidState fluid,java.util.function.BooleanSupplier nativeCustom,Runnable vanilla){
        if(!BlockRenderingRegistry.hasFluidRenderer(fluid.getType()))return nativeCustom.getAsBoolean();
        if(renderFluid(world,pos,output,state,fluid))return true;
        var previous=FLUID_FALLBACK.get();
        FLUID_FALLBACK.set(new FluidFallback(world,pos.immutable(),output,state,fluid));
        try{
            if(!nativeCustom.getAsBoolean())vanilla.run();
            return true;
        }finally{
            if(previous==null)FLUID_FALLBACK.remove();else FLUID_FALLBACK.set(previous);
        }
    }

    public static boolean renderFluid(BlockAndTintGetter world, BlockPos pos, FluidRenderer.Output output, BlockState state, FluidState fluid) {
        if (!BlockRenderingRegistry.hasFluidRenderer(fluid.getType())) return false;
        var fallback=FLUID_FALLBACK.get();
        if(fallback!=null&&fallback.matches(world,pos,output,state,fluid))return false;
        FluidVertexBatch batch=new FluidVertexBatch();
        try {
            ICCBlockRenderer renderer=BlockRenderingRegistry.findFor(fluid.getType(),r->r.canHandleFluid(world,pos,state,fluid));
            if(renderer==null)return false;
            renderer.renderFluid(pos,world,batch,state,fluid);
            batch.validate();
        }catch(Throwable t){
            if(!ClientInit.catchBlockRenderExceptions)throw t;
            handleCaughtException(t,state,pos,world);
            return false;
        }
        batch.commit(output);
        return true;
    }

    public static boolean renderEntity(BlockModelRenderState output, BlockState state) {
        return renderEntity(output,state,null);
    }
    public static boolean renderEntity(BlockModelRenderState output,BlockState state,@Nullable net.minecraft.client.renderer.block.model.BlockDisplayContext display){
        if (!BlockRenderingRegistry.hasBlockRenderer(state.getBlock())) return false;
        ICCBlockRenderer renderer;
        try { renderer=BlockRenderingRegistry.findFor(state.getBlock(),r->r.canHandleEntity(state,display)); }
        catch(RuntimeException e){
            if(!ClientInit.catchBlockRenderExceptions)throw e;
            handleCaughtException(e,state,null,null);return false;
        }
        if (renderer == null) return false;
        output.clear();
        output.blockLightCoords = state.emissiveRendering() ? LightCoordsUtil.FULL_BRIGHT : LightCoordsUtil.pack(state.getLightEmission(),0);
        output.setupSpecialModel(new SpecialModelRenderer<Void>() {
            @Override public Void extractArgument(ItemStack item) { return null; }
            @Override public void getExtents(Consumer<Vector3fc> out) {}
            @Override public void submit(Void unused, PoseStack pose, SubmitNodeCollector collector, int light, int overlay, boolean foil, int outline) {
                // Submission is not transactional. Never replay vanilla after a partial custom submission.
                renderer.renderEntity(state,display,pose,collector,light,overlay,outline);
            }
        }, new Matrix4f());
        return true;
    }

    public static boolean collectBreaking(BlockStateModel model, BlockAndTintGetter world, BlockPos pos, BlockState state, List<BlockStateModelPart> parts) {
        if (!BlockRenderingRegistry.hasBlockRenderer(state.getBlock())) return false;
        try {
        ICCBlockRenderer renderer = BlockRenderingRegistry.findFor(state.getBlock(), r -> r.canHandleBlock(world,pos,state,null));
        if (renderer == null) return false;
        List<BakedQuad> quads = new ArrayList<>();
        renderer.renderBreaking(state,pos,world,(x,y,z,quad,instance) -> {
            if (x != 0 || y != 0 || z != 0) throw new IllegalArgumentException("Breaking quads must be block-local.");
            quads.add(quad);
        });
        List<BakedQuad> frozen = List.copyOf(quads);
        Material.Baked particle = model.particleMaterial(world,pos,state);
        parts.add(new BlockStateModelPart() {
            @Override public List<BakedQuad> getQuads(@Nullable Direction side) { return side == null ? frozen : List.of(); }
            @Override public boolean useAmbientOcclusion() { return false; }
            @Override public Material.Baked particleMaterial() { return particle; }
            @Override public int materialFlags() { int flags=0; for (var q:frozen) flags |= q.materialInfo().flags(); return flags; }
        });
        return true;
        } catch(Throwable t){
            if(!ClientInit.catchBlockRenderExceptions)throw t;
            handleCaughtException(t,state,pos,world);return false;
        }
    }

    private static void handleCaughtException(Throwable t, BlockState state, BlockPos pos, BlockAndTintGetter world) {
        String message = "CCL caught a block-render exception at " + pos + " for " + BuiltInRegistries.BLOCK.getKey(state.getBlock()) + " (" + state + ")";
        if (ExceptionMessageEventHandler.exceptionMessageCache.add(message + org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace(t))) LOGGER.error(message,t);
        if (ClientInit.messagePlayerOnRenderExceptionCaught) {
            long now = System.nanoTime(), previous = LAST_MESSAGE.get();
            if (now - previous > TimeUnit.SECONDS.toNanos(5) && LAST_MESSAGE.compareAndSet(previous,now)) {
                Minecraft mc = Minecraft.getInstance();
                if(mc==null)return;
                mc.execute(() -> { if (mc.player != null) mc.player.sendSystemMessage(Component.literal("CCL caught an exception rendering a block. See the log for info.")); });
            }
        }
    }
}