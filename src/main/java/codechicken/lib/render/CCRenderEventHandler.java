package codechicken.lib.render;

import codechicken.lib.raytracer.VoxelShapeBlockHitResult;
import net.covers1624.quack.util.CrashLock;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ExtractBlockOutlineRenderStateEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;

public class CCRenderEventHandler {

    private static final CrashLock LOCK = new CrashLock("Already Initialized");


    public static int renderTime;
    public static float renderFrame;

    public static void init() {
        LOCK.lock();
        NeoForge.EVENT_BUS.addListener(CCRenderEventHandler::clientTick);
        NeoForge.EVENT_BUS.addListener(CCRenderEventHandler::renderTick);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, CCRenderEventHandler::onBlockOutline);
    }

    private static void clientTick(ClientTickEvent.Post event) {
        renderTime++;
    }

    private static void renderTick(RenderFrameEvent.Pre event) {
        renderFrame = event.getPartialTick().getGameTimeDeltaPartialTick(true);
    }

    private static void onBlockOutline(ExtractBlockOutlineRenderStateEvent event) {
        BlockHitResult hit = event.getHitResult();
        if (!(hit instanceof VoxelShapeBlockHitResult voxelHit)) return;
        VoxelShape shape = voxelHit.shape;

        // Reuse the native outline submission path, retaining the selected subshape
        // and the same contrast, depth-write, line width and terrain phase as vanilla.
        event.addCustomRenderer((outline, collector, poseStack, levelRenderState) -> {
            var renderer=net.minecraft.client.Minecraft.getInstance().gameRenderer;
            Vec3 camera=levelRenderState.cameraRenderState.pos;
            var pos=voxelHit.getBlockPos();
            poseStack.pushPose();
            try {
                poseStack.translate(pos.getX()-camera.x,pos.getY()-camera.y,pos.getZ()-camera.z);
                if(outline.highContrast()){
                    collector.submitShapeOutline(poseStack,shape,RenderTypes.secondaryBlockOutline(),0xFF000000,7F,outline.isTranslucent());
                }
                var type=outline.highContrast()?RenderTypes.linesDepthBias():
                        renderer.useImprovedTransparency()?RenderTypes.linesTranslucentNoDepthWrite():RenderTypes.linesTranslucent();
                int color=outline.highContrast()?-11010079:net.minecraft.util.ARGB.black(102);
                float width=renderer.gameRenderState().windowRenderState.appropriateLineWidth;
                collector.submitShapeOutline(poseStack,shape,type,color,width,outline.isTranslucent());
            } finally {
                poseStack.popPose();
            }
            return true;
        });
    }
}
