package codechicken.lib.internal;

import codechicken.lib.colour.EnumColour;
import codechicken.lib.render.RenderUtils;

import codechicken.lib.vec.Cuboid6;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import net.minecraft.client.renderer.RenderPipelines;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.covers1624.quack.util.CrashLock;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Created by covers1624 on 9/06/18.
 */
public class HighlightHandler {

    private static final CrashLock LOCK = new CrashLock("Already Initialized");

    private static final Cuboid6 BOX = Cuboid6.full.copy().expand(0.02);
    private static final float[] RED = EnumColour.RED.getColour(128).packArray();

    @Nullable
    public static BlockPos highlight;
    public static boolean useDepth = true;

    private static final RenderType box = createHighlightType("box_depth", true);
    private static final RenderType boxNoDepth = createHighlightType("box_no_depth", false);

    private static RenderType createHighlightType(String name, boolean depth) {
        RenderPipeline pipeline = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath(codechicken.lib.CodeChickenLib.MOD_ID,"pipeline/"+name))
                .withCull(true)
                .withDepthStencilState(depth ? Optional.of(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false)) : Optional.empty())
                .build();
        return RenderType.create("ccl:" + name, RenderSetup.builder(pipeline).sortOnUpload().createRenderSetup());
    }

    private record HighlightFrame(double x, double y, double z, boolean depth) {}
    private static final ContextKey<HighlightFrame> HIGHLIGHT_FRAME =
            new ContextKey<>(Identifier.fromNamespaceAndPath("ccl", "highlight_frame"));

    public static void registerPipelines(net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent event) {
        event.registerPipeline(box.pipeline());
        event.registerPipeline(boxNoDepth.pipeline());
    }

    public static void init() {
        LOCK.lock();
        NeoForge.EVENT_BUS.addListener(HighlightHandler::extractHighlight);
        NeoForge.EVENT_BUS.addListener(HighlightHandler::submitHighlight);
    }

    private static void extractHighlight(ExtractLevelRenderStateEvent event) {
        BlockPos pos = highlight;
        Vec3 camera = event.getRenderState().cameraRenderState.pos;
        event.getRenderState().setRenderData(HIGHLIGHT_FRAME, pos == null ? null :
                new HighlightFrame(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z, useDepth));
    }

    private static void submitHighlight(SubmitCustomGeometryEvent event) {
        HighlightFrame frame = event.getLevelRenderState().getRenderData(HIGHLIGHT_FRAME);
        if (frame == null) return;
        PoseStack stack = event.getPoseStack();
        stack.pushPose();
        try {
            stack.translate(frame.x(), frame.y(), frame.z());
            event.getSubmitNodeCollector().submitCustomGeometry(stack, frame.depth() ? box : boxNoDepth,
                    (pose, consumer) -> RenderUtils.bufferCuboidSolid(consumer, pose, BOX, RED[0], RED[1], RED[2], RED[3]));
        } finally {
            stack.popPose();
        }
    }
}