package codechicken.lib.render;

import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;

import codechicken.lib.vec.*;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.shapes.VoxelShape;


import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;

import java.util.ArrayList;
import java.util.List;

//TODO Document and reorder things in here.
public class RenderUtils {

    private static final class FluidPipeline {
        private static final net.minecraft.resources.Identifier ID=net.minecraft.resources.Identifier.fromNamespaceAndPath(codechicken.lib.CodeChickenLib.MOD_ID,"fluid");
        private static final com.mojang.renderpearl.api.pipeline.RenderPipeline PIPELINE=
                com.mojang.renderpearl.api.pipeline.RenderPipeline.builder(net.minecraft.client.renderer.RenderPipelines.BLOCK_SNIPPET)
                        .withLocation(ID.withPrefix("pipeline/"))
                        .withColorTargetState(new com.mojang.renderpearl.api.pipeline.ColorTargetState(com.mojang.renderpearl.api.pipeline.BlendFunction.TRANSLUCENT))
                        .withDepthStencilState(com.mojang.renderpearl.api.pipeline.DepthStencilState.DEFAULT)
                        .build();
        private static final net.minecraft.client.renderer.oit.OitPipelineSet OIT=
                net.minecraft.client.renderer.oit.OitPipelineSet.builder(ID,
                        com.mojang.renderpearl.api.pipeline.RenderPipeline.builder(net.minecraft.client.renderer.RenderPipelines.GENERIC_BLOCKS_SNIPPET)
                                .withBindGroupLayout(net.minecraft.client.renderer.BindGroupLayouts.DYNAMIC_TRANSFORMS)
                                .withVertexShader("core/block").withFragmentShader("core/block"))
                        .withAccumulateModifier(builder->builder.withBindGroupLayout(net.minecraft.client.renderer.BindGroupLayouts.SAMPLER2))
                        .build();
        private static final RenderType TYPE=RenderType.create("ccl_fluid",
                net.minecraft.client.renderer.rendertype.RenderSetup.builder(PIPELINE)
                        .withTexture("Sampler0",net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS)
                        .useLightmap().sortOnUpload().setOitPipelines(OIT).createRenderSetup());
    }

    public static void registerPipelines(net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent event){
        event.registerPipeline(FluidPipeline.PIPELINE);
        event.registerOitPipelineSet(FluidPipeline.OIT);
    }

    public static RenderType getFluidRenderType() {
        return FluidPipeline.TYPE;
    }

    public static FluidModel getFluidModel(Fluid fluid) {
        return Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(fluid.defaultFluidState());
    }

    public static int getFluidTint(FluidStack stack) {
        if (stack.isEmpty()) return -1;
        var tint = getFluidModel(stack.getFluid()).fluidTintSource();
        return tint == null ? -1 : tint.colorAsStack(stack);
    }

    public static int getFluidTint(Fluid fluid) {
        if (fluid == net.minecraft.world.level.material.Fluids.EMPTY) return -1;
        var tint = getFluidModel(fluid).fluidTintSource();
        return tint == null ? -1 : tint.color(fluid.defaultFluidState());
    }

    /**
     * Render into the consumer supplied by the target render submission callback.
     */
    public static void renderFluidCuboid(CCRenderState ccrs, Matrix4 mat, RenderType renderType, VertexConsumer source, FluidStack stack, Cuboid6 bound, double capacity, double res) {
        if (stack.isEmpty() || capacity <= 0) return;
        if (!Double.isFinite(capacity)) throw new IllegalArgumentException("Invalid fluid fill: " + capacity);
        if (!Double.isFinite(res) || res <= 0) throw new IllegalArgumentException("Invalid fluid texture resolution: " + res);
        capacity = Math.min(1, capacity);
        int alpha = 255;
        FluidType type = stack.getFluid().getFluidType();
        Cuboid6 filled = bound.copy();
        if (type.isLighterThanAir()) {
            alpha = (int) (Math.pow(capacity, 0.4) * 255);
        } else {
            filled.max.y = filled.min.y + (filled.max.y - filled.min.y) * capacity;
        }
        FluidModel model = getFluidModel(stack.getFluid());
        ccrs.bind(renderType, source);
        ccrs.baseColour = getFluidTint(stack) << 8 | alpha;
        makeFluidModel(filled, model.stillMaterial().sprite(), res).render(ccrs, mat);
    }

    public static CCModel makeFluidModel(Cuboid6 bound, TextureAtlasSprite tex, double res) {
        CCModel model = CCModel.newModel(PrimitiveTopology.QUADS);
        List<Vertex5> verts = new ArrayList<>();
        makeFluidCuboid(verts, bound, tex, res);
        model.verts = verts.toArray(new Vertex5[0]);
        return model;
    }

    public static void makeFluidCuboid(List<Vertex5> vertices, Cuboid6 bound, TextureAtlasSprite tex, double res) {
        makeFluidQuadVertices(vertices, new Vector3(bound.min.x, bound.min.y, bound.min.z), new Vector3(bound.max.x, bound.min.y, bound.min.z), new Vector3(bound.max.x, bound.min.y, bound.max.z), new Vector3(bound.min.x, bound.min.y, bound.max.z), tex, res);
        makeFluidQuadVertices(vertices, new Vector3(bound.min.x, bound.max.y, bound.min.z), new Vector3(bound.min.x, bound.max.y, bound.max.z), new Vector3(bound.max.x, bound.max.y, bound.max.z), new Vector3(bound.max.x, bound.max.y, bound.min.z), tex, res);
        makeFluidQuadVertices(vertices, new Vector3(bound.min.x, bound.max.y, bound.min.z), new Vector3(bound.min.x, bound.min.y, bound.min.z), new Vector3(bound.min.x, bound.min.y, bound.max.z), new Vector3(bound.min.x, bound.max.y, bound.max.z), tex, res);
        makeFluidQuadVertices(vertices, new Vector3(bound.max.x, bound.max.y, bound.max.z), new Vector3(bound.max.x, bound.min.y, bound.max.z), new Vector3(bound.max.x, bound.min.y, bound.min.z), new Vector3(bound.max.x, bound.max.y, bound.min.z), tex, res);
        makeFluidQuadVertices(vertices, new Vector3(bound.max.x, bound.max.y, bound.min.z), new Vector3(bound.max.x, bound.min.y, bound.min.z), new Vector3(bound.min.x, bound.min.y, bound.min.z), new Vector3(bound.min.x, bound.max.y, bound.min.z), tex, res);
        makeFluidQuadVertices(vertices, new Vector3(bound.min.x, bound.max.y, bound.max.z), new Vector3(bound.min.x, bound.min.y, bound.max.z), new Vector3(bound.max.x, bound.min.y, bound.max.z), new Vector3(bound.max.x, bound.max.y, bound.max.z), tex, res);
    }

    public static void makeFluidQuadVertices(List<Vertex5> vertices, Vector3 point1, Vector3 point2, Vector3 point3, Vector3 point4, TextureAtlasSprite icon, double res) {
        makeFluidQuadVertices(vertices, point2, point4.copy().subtract(point1), point1.copy().subtract(point2), icon, res);
    }

    public static void makeFluidQuadVertices(List<Vertex5> vertices, Vector3 base, Vector3 wide, Vector3 high, TextureAtlasSprite icon, double res) {
        if (!Double.isFinite(res) || res <= 0) throw new IllegalArgumentException("Invalid fluid texture resolution: " + res);
        Vector3 a = new Vector3();
        Vector3 b = new Vector3();
        Vector3 c = new Vector3();
        Vector3 d = new Vector3();

        double u1 = icon.getU0();
        double du = icon.getU1() - icon.getU0();
        double v2 = icon.getV1();
        double dv = icon.getV1() - icon.getV0();

        double wlen = wide.mag();
        double hlen = high.mag();

        double x = 0;
        while (x < wlen) {
            double rx = wlen - x;
            if (rx > res) {
                rx = res;
            }

            double y = 0;
            while (y < hlen) {
                double ry = hlen - y;
                if (ry > res) {
                    ry = res;
                }

                Vector3 dx1 = a.set(wide).multiply(x / wlen);
                Vector3 dx2 = b.set(wide).multiply((x + rx) / wlen);
                Vector3 dy1 = c.set(high).multiply(y / hlen);
                Vector3 dy2 = d.set(high).multiply((y + ry) / hlen);

                vertices.add(new Vertex5(base.x + dx1.x + dy2.x, base.y + dx1.y + dy2.y, base.z + dx1.z + dy2.z, u1, v2 - ry / res * dv));
                vertices.add(new Vertex5(base.x + dx1.x + dy1.x, base.y + dx1.y + dy1.y, base.z + dx1.z + dy1.z, u1, v2));
                vertices.add(new Vertex5(base.x + dx2.x + dy1.x, base.y + dx2.y + dy1.y, base.z + dx2.z + dy1.z, (u1 + rx / res * du), v2));
                vertices.add(new Vertex5(base.x + dx2.x + dy2.x, base.y + dx2.y + dy2.y, base.z + dx2.z + dy2.z, (u1 + rx / res * du), v2 - ry / res * dv));

                y += ry;
            }

            x += rx;
        }
    }

    /**
     * Builds a solid cuboid.
     * Expects VertexFormat of POSITION_COLOR.
     * If you need anything more specialized, Use a {@link CCModel}.
     *
     * @param builder The {@link VertexConsumer}
     * @param c       The {@link Cuboid6}
     * @param r       Red color.
     * @param g       Green color.
     * @param b       Blue Color.
     * @param a       Alpha channel.
     */
    public static void bufferCuboidSolid(VertexConsumer builder, Cuboid6 c, float r, float g, float b, float a) {
        builder.addVertex((float) c.min.x, (float) c.max.y, (float) c.min.z).setColor(r, g, b, a);
        builder.addVertex((float) c.max.x, (float) c.max.y, (float) c.min.z).setColor(r, g, b, a);
        builder.addVertex((float) c.max.x, (float) c.min.y, (float) c.min.z).setColor(r, g, b, a);
        builder.addVertex((float) c.min.x, (float) c.min.y, (float) c.min.z).setColor(r, g, b, a);

        builder.addVertex((float) c.min.x, (float) c.min.y, (float) c.max.z).setColor(r, g, b, a);
        builder.addVertex((float) c.max.x, (float) c.min.y, (float) c.max.z).setColor(r, g, b, a);
        builder.addVertex((float) c.max.x, (float) c.max.y, (float) c.max.z).setColor(r, g, b, a);
        builder.addVertex((float) c.min.x, (float) c.max.y, (float) c.max.z).setColor(r, g, b, a);

        builder.addVertex((float) c.min.x, (float) c.min.y, (float) c.min.z).setColor(r, g, b, a);
        builder.addVertex((float) c.max.x, (float) c.min.y, (float) c.min.z).setColor(r, g, b, a);
        builder.addVertex((float) c.max.x, (float) c.min.y, (float) c.max.z).setColor(r, g, b, a);
        builder.addVertex((float) c.min.x, (float) c.min.y, (float) c.max.z).setColor(r, g, b, a);

        builder.addVertex((float) c.min.x, (float) c.max.y, (float) c.max.z).setColor(r, g, b, a);
        builder.addVertex((float) c.max.x, (float) c.max.y, (float) c.max.z).setColor(r, g, b, a);
        builder.addVertex((float) c.max.x, (float) c.max.y, (float) c.min.z).setColor(r, g, b, a);
        builder.addVertex((float) c.min.x, (float) c.max.y, (float) c.min.z).setColor(r, g, b, a);

        builder.addVertex((float) c.min.x, (float) c.min.y, (float) c.max.z).setColor(r, g, b, a);
        builder.addVertex((float) c.min.x, (float) c.max.y, (float) c.max.z).setColor(r, g, b, a);
        builder.addVertex((float) c.min.x, (float) c.max.y, (float) c.min.z).setColor(r, g, b, a);
        builder.addVertex((float) c.min.x, (float) c.min.y, (float) c.min.z).setColor(r, g, b, a);

        builder.addVertex((float) c.max.x, (float) c.min.y, (float) c.min.z).setColor(r, g, b, a);
        builder.addVertex((float) c.max.x, (float) c.max.y, (float) c.min.z).setColor(r, g, b, a);
        builder.addVertex((float) c.max.x, (float) c.max.y, (float) c.max.z).setColor(r, g, b, a);
        builder.addVertex((float) c.max.x, (float) c.min.y, (float) c.max.z).setColor(r, g, b, a);
    }

    public static void bufferCuboidSolid(VertexConsumer builder, PoseStack.Pose pose, Cuboid6 c, float r, float g, float b, float a) {
        builder.addVertex(pose, (float) c.min.x, (float) c.max.y, (float) c.min.z).setColor(r, g, b, a);
        builder.addVertex(pose, (float) c.max.x, (float) c.max.y, (float) c.min.z).setColor(r, g, b, a);
        builder.addVertex(pose, (float) c.max.x, (float) c.min.y, (float) c.min.z).setColor(r, g, b, a);
        builder.addVertex(pose, (float) c.min.x, (float) c.min.y, (float) c.min.z).setColor(r, g, b, a);

        builder.addVertex(pose, (float) c.min.x, (float) c.min.y, (float) c.max.z).setColor(r, g, b, a);
        builder.addVertex(pose, (float) c.max.x, (float) c.min.y, (float) c.max.z).setColor(r, g, b, a);
        builder.addVertex(pose, (float) c.max.x, (float) c.max.y, (float) c.max.z).setColor(r, g, b, a);
        builder.addVertex(pose, (float) c.min.x, (float) c.max.y, (float) c.max.z).setColor(r, g, b, a);

        builder.addVertex(pose, (float) c.min.x, (float) c.min.y, (float) c.min.z).setColor(r, g, b, a);
        builder.addVertex(pose, (float) c.max.x, (float) c.min.y, (float) c.min.z).setColor(r, g, b, a);
        builder.addVertex(pose, (float) c.max.x, (float) c.min.y, (float) c.max.z).setColor(r, g, b, a);
        builder.addVertex(pose, (float) c.min.x, (float) c.min.y, (float) c.max.z).setColor(r, g, b, a);

        builder.addVertex(pose, (float) c.min.x, (float) c.max.y, (float) c.max.z).setColor(r, g, b, a);
        builder.addVertex(pose, (float) c.max.x, (float) c.max.y, (float) c.max.z).setColor(r, g, b, a);
        builder.addVertex(pose, (float) c.max.x, (float) c.max.y, (float) c.min.z).setColor(r, g, b, a);
        builder.addVertex(pose, (float) c.min.x, (float) c.max.y, (float) c.min.z).setColor(r, g, b, a);

        builder.addVertex(pose, (float) c.min.x, (float) c.min.y, (float) c.max.z).setColor(r, g, b, a);
        builder.addVertex(pose, (float) c.min.x, (float) c.max.y, (float) c.max.z).setColor(r, g, b, a);
        builder.addVertex(pose, (float) c.min.x, (float) c.max.y, (float) c.min.z).setColor(r, g, b, a);
        builder.addVertex(pose, (float) c.min.x, (float) c.min.y, (float) c.min.z).setColor(r, g, b, a);

        builder.addVertex(pose, (float) c.max.x, (float) c.min.y, (float) c.min.z).setColor(r, g, b, a);
        builder.addVertex(pose, (float) c.max.x, (float) c.max.y, (float) c.min.z).setColor(r, g, b, a);
        builder.addVertex(pose, (float) c.max.x, (float) c.max.y, (float) c.max.z).setColor(r, g, b, a);
        builder.addVertex(pose, (float) c.max.x, (float) c.min.y, (float) c.max.z).setColor(r, g, b, a);
    }

    /** Consumer must be supplied by the line render submission callback. */
    public static void bufferHitbox(Matrix4 mat,VertexConsumer consumer,net.minecraft.client.Camera camera,Cuboid6 cuboid){
        var view=camera.position();
        bufferHitBox(mat.copy().translate(-view.x,-view.y,-view.z),consumer,cuboid);
    }
    public static void bufferHitBox(Matrix4 mat,VertexConsumer consumer,Cuboid6 cuboid){
        bufferCuboidOutline(new codechicken.lib.render.buffer.TransformingVertexConsumer(consumer,mat),cuboid.copy().expand(0.002),0,0,0,0.4F);
    }
    public static void bufferShapeHitBox(Matrix4 mat,VertexConsumer consumer,net.minecraft.client.Camera camera,VoxelShape shape){
        var view=camera.position();
        bufferShapeHitBox(mat.copy().translate(-view.x,-view.y,-view.z),consumer,shape);
    }
    public static void bufferShapeHitBox(Matrix4 mat,VertexConsumer consumer,VoxelShape shape){
        bufferShapeOutline(new codechicken.lib.render.buffer.TransformingVertexConsumer(consumer,mat),shape,0,0,0,0.4F);
    }

    public static void bufferCuboidOutline(VertexConsumer builder, Cuboid6 c, float r, float g, float b, float a) {
        bufferLinePair(builder, c.min.x, c.min.y, c.min.z, c.max.x, c.min.y, c.min.z, r, g, b, a);
        bufferLinePair(builder, c.max.x, c.min.y, c.min.z, c.max.x, c.min.y, c.max.z, r, g, b, a);
        bufferLinePair(builder, c.max.x, c.min.y, c.max.z, c.min.x, c.min.y, c.max.z, r, g, b, a);
        bufferLinePair(builder, c.min.x, c.min.y, c.max.z, c.min.x, c.min.y, c.min.z, r, g, b, a);
        bufferLinePair(builder, c.min.x, c.max.y, c.min.z, c.max.x, c.max.y, c.min.z, r, g, b, a);
        bufferLinePair(builder, c.max.x, c.max.y, c.min.z, c.max.x, c.max.y, c.max.z, r, g, b, a);
        bufferLinePair(builder, c.max.x, c.max.y, c.max.z, c.min.x, c.max.y, c.max.z, r, g, b, a);
        bufferLinePair(builder, c.min.x, c.max.y, c.max.z, c.min.x, c.max.y, c.min.z, r, g, b, a);
        bufferLinePair(builder, c.min.x, c.min.y, c.min.z, c.min.x, c.max.y, c.min.z, r, g, b, a);
        bufferLinePair(builder, c.max.x, c.min.y, c.min.z, c.max.x, c.max.y, c.min.z, r, g, b, a);
        bufferLinePair(builder, c.max.x, c.min.y, c.max.z, c.max.x, c.max.y, c.max.z, r, g, b, a);
        bufferLinePair(builder, c.min.x, c.min.y, c.max.z, c.min.x, c.max.y, c.max.z, r, g, b, a);
    }

    public static void bufferShapeOutline(VertexConsumer builder, VoxelShape shape, float r, float g, float b, float a) {
        shape.forAllEdges((x1, y1, z1, x2, y2, z2) ->
                bufferLinePair(builder, x1, y1, z1, x2, y2, z2, r, g, b, a));
    }

    public static void bufferShapeOutline(VertexConsumer builder, PoseStack.Pose pose, VoxelShape shape, float r, float g, float b, float a) {
        shape.forAllEdges((x1, y1, z1, x2, y2, z2) -> {
            bufferLinePair(builder, pose, x1, y1, z1, x2, y2, z2, r, g, b, a);
        });
    }

    private static void bufferLinePair(VertexConsumer builder, PoseStack.Pose pose, double x1, double y1, double z1, double x2, double y2, double z2, float r, float g, float b, float a) {
        Vector3 normal = new Vector3(x1, y1, z1).subtract(x2, y2, z2);
        double length = normal.mag();
        if (length == 0) return;
        normal.divide(length);
        builder.addVertex(pose, (float) x1, (float) y1, (float) z1).setColor(r, g, b, a)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z).setLineWidth(1.0F);
        builder.addVertex(pose, (float) x2, (float) y2, (float) z2).setColor(r, g, b, a)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z).setLineWidth(1.0F);
    }

    private static void bufferLinePair(VertexConsumer builder, double x1, double y1, double z1, double x2, double y2, double z2, float r, float g, float b, float a) {
        Vector3 v1 = new Vector3(x1, y1, z1).subtract(x2, y2, z2);
        double d = v1.mag();
        if (d == 0) return;
        v1.divide(d);
        builder.addVertex((float) x1, (float) y1, (float) z1).setColor(r, g, b, a).setNormal((float) v1.x, (float) v1.y, (float) v1.z).setLineWidth(1.0F);
        builder.addVertex((float) x2, (float) y2, (float) z2).setColor(r, g, b, a).setNormal((float) v1.x, (float) v1.y, (float) v1.z).setLineWidth(1.0F);
    }

    public static Matrix4 getMatrix(Matrix4 in, Vector3 translation, Rotation rotation, double scale) {
        return in.translate(translation).scale(scale).rotate(rotation);
    }

    @Deprecated
    public static Matrix4 getMatrix(Vector3 translation, Rotation rotation, double scale) {
        return getMatrix(new Matrix4(), translation, rotation, scale);
    }

    public static float getPearlBob(double time) {
        return (float) Math.sin(time / 25 * 3.141593) * 0.1F;
    }

    public static int getTimeOffset(BlockPos pos) {
        return getTimeOffset(pos.getX(), pos.getY(), pos.getZ());
    }

    public static int getTimeOffset(int x, int y, int z) {
        return x * 3 + y * 5 + z * 9;
    }
}
