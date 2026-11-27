package codechicken.lib.model.bakedmodels;

import codechicken.lib.model.CachedFormat;
import codechicken.lib.model.PerspectiveModel;
import codechicken.lib.model.PerspectiveModelState;
import codechicken.lib.render.particle.IModelParticleProvider;
import codechicken.lib.util.VertexUtils;
import codechicken.lib.vec.Vector3;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3fc;
import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;


import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by covers1624 on 1/02/2017.
 */
@Deprecated // These may go away unless there is sufficient need for generic implementations like these.
public abstract class AbstractBakedPropertiesModel implements PerspectiveModel, IModelParticleProvider, BlockStateModelPart {

    protected final ModelProperties properties;

    public AbstractBakedPropertiesModel(ModelProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean useAmbientOcclusion() {
        return properties.isAmbientOcclusion();
    }

    @Override
    public boolean isGui3d() {
        return properties.isGui3d();
    }

    @Override
    public boolean isCustomRenderer() {
        return properties.isBuiltInRenderer();
    }

    @Override
    public boolean usesBlockLight() {
        return properties.usesBlockLight();
    }

    @Override
    public TextureAtlasSprite getParticleIcon() {
        return properties.getParticleTexture();
    }

    public abstract List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random);

    @Override public List<BakedQuad> getQuads(@Nullable Direction side) {
        return getQuads(null, side, RandomSource.create(0));
    }

    @Override public void collectParts(RandomSource random, List<BlockStateModelPart> parts) {
        parts.add(snapshot(null,random));
    }

    @Override public void collectParts(BlockAndTintGetter world,BlockPos pos,BlockState state,RandomSource random,List<BlockStateModelPart> parts) {
        parts.add(snapshot(state,random));
    }

    @Override public int materialFlags(BlockAndTintGetter world,BlockPos pos,BlockState state) {
        return snapshot(state,RandomSource.create(state.getSeed(pos))).materialFlags();
    }

    // All face queries use the same random seed, matching the old block tessellator.
    protected BlockStateModelPart snapshot(@Nullable BlockState state,RandomSource random) {
        long seed=random.nextLong();
        var unculled=List.copyOf(getQuads(state,null,RandomSource.create(seed)));
        var faces=new java.util.EnumMap<Direction,List<BakedQuad>>(Direction.class);
        int flags=0;
        for(var quad:unculled)flags|=quad.materialInfo().flags();
        for(var face:Direction.values()){
            var quads=List.copyOf(getQuads(state,face,RandomSource.create(seed)));
            faces.put(face,quads);
            for(var quad:quads)flags|=quad.materialInfo().flags();
        }
        int materialFlags=flags;
        var particle=particleMaterial();
        boolean ao=useAmbientOcclusion();
        return new BlockStateModelPart(){
            @Override public List<BakedQuad> getQuads(@Nullable Direction face){return face==null?unculled:faces.get(face);}
            @Override public boolean useAmbientOcclusion(){return ao;}
            @Override public Material.Baked particleMaterial(){return particle;}
            @Override public int materialFlags(){return materialFlags;}
        };
    }

    private static List<BakedQuad> allQuads(BlockStateModelPart part){
        var quads=new ArrayList<>(part.getQuads(null));
        for(var face:Direction.values())quads.addAll(part.getQuads(face));
        return quads;
    }

    @Override public Material.Baked particleMaterial() {
        return new Material.Baked(getParticleIcon(), false);
    }

    @Override public int materialFlags() {
        int flags = 0;
        for (BakedQuad quad : getAllQuads(null)) flags |= quad.materialInfo().flags();
        return flags;
    }

    protected List<BakedQuad> getAllQuads(@Nullable BlockState state) {
        List<BakedQuad> quads = new ArrayList<>(getQuads(state, null, RandomSource.create(0)));
        for (Direction face : Direction.values()) quads.addAll(getQuads(state, face, RandomSource.create(0)));
        return quads;
    }

    protected List<BakedQuad> getAllQuads(BlockState state, BlockAndTintGetter world, BlockPos pos) {
        List<BlockStateModelPart> parts = new ArrayList<>();
        collectParts(world, pos, state, RandomSource.create(state.getSeed(pos)), parts);
        List<BakedQuad> quads = new ArrayList<>();
        for (var part : parts) {
            quads.addAll(part.getQuads(null));
            for (Direction face : Direction.values()) quads.addAll(part.getQuads(face));
        }
        return quads;
    }

    /** Override for stack-dependent tint layers; white preserves the baked vertex colors by default. */
    protected int itemTint(ItemStack stack, int index, @Nullable ClientLevel level, @Nullable ItemOwner owner) {
        return -1;
    }

    @Override
    public void update(ItemStackRenderState output, ItemStack stack, ItemModelResolver resolver, ItemDisplayContext display,
            @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
        List<BakedQuad> quads = allQuads(snapshot(null,RandomSource.create(seed)));
        if (quads.isEmpty()) return;
        var layer = output.newLayer();
        output.appendModelIdentityElement(this);
        output.appendModelIdentityElement(List.copyOf(quads));
        layer.setQuads(ItemQuads.split(quads));
        layer.setUsesBlockLight(usesBlockLight());
        layer.setParticleMaterial(particleMaterial());
        layer.setLocalTransform(nativeLayerTransform(display));
        if (stack.hasFoil()) {
            layer.setFoilType(ItemStackRenderState.FoilType.STANDARD);
            output.setAnimated();
        }
        int maxTint = -1;
        List<Vector3fc> extents = new ArrayList<>();
        for (var quad : quads) {
            maxTint = Math.max(maxTint, quad.materialInfo().tintIndex());
            for (int v = 0; v < 4; v++) extents.add(quad.position(v));
        }
        for (int i = 0; i <= maxTint; i++) {
            int tint = itemTint(stack, i, level, owner);
            layer.tintLayers().add(tint);
            output.appendModelIdentityElement(tint);
        }
        layer.setExtents(() -> extents.toArray(Vector3fc[]::new));
    }

    @Override
    public Set<TextureAtlasSprite> getHitEffects(BlockHitResult traceResult, BlockState state, BlockAndTintGetter world, BlockPos pos) {
        Vector3 vec = new Vector3(traceResult.getLocation()).subtract(traceResult.getBlockPos());
        return getAllQuads(state, world, pos).stream()
                .filter(quad -> quad.direction() == traceResult.getDirection())
                .filter(quad -> checkDepth(quad, vec, traceResult.getDirection()))
                .map(quad -> quad.materialInfo().sprite())
                .collect(Collectors.toSet());
    }

    protected boolean checkDepth(BakedQuad quad, Vector3 hit, Direction hitFace) {
        Vector3 posVec = new Vector3();
        for (int v = 0; v < 4; v++) {
            var pos = quad.position(v);
            posVec.add(pos.x(), pos.y(), pos.z());
        }
        posVec.divide(4);

        double diff = switch (hitFace.getAxis()) {
            case X -> Math.abs(hit.x - posVec.x);
            case Y -> Math.abs(hit.y - posVec.y);
            case Z -> Math.abs(hit.z - posVec.z);
        };
        return !(diff > 0.01);
    }

    @Override
    public Set<TextureAtlasSprite> getDestroyEffects(BlockState state, BlockAndTintGetter world, BlockPos pos) {
        return getAllQuads(state, world, pos).stream().map(quad -> quad.materialInfo().sprite()).collect(Collectors.toSet());
    }

    @Nullable
    @Override
    public PerspectiveModelState getModelState() {
        return properties instanceof ModelProperties.PerspectiveProperties props ? props.getTransforms() : null;
    }

    @Override
    public PerspectiveModel applyTransform(ItemDisplayContext context, PoseStack pStack, boolean leftFlip) {
        return PerspectiveModel.super.applyTransform(context, pStack, leftFlip);
    }
}
