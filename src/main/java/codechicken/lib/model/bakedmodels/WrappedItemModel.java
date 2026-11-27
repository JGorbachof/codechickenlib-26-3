package codechicken.lib.model.bakedmodels;

import codechicken.lib.model.PerspectiveModel;
import codechicken.lib.model.PerspectiveModelState;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Delegates to a wrapped item model at extraction time, with the actual stack and owner. */
public abstract class WrappedItemModel implements PerspectiveModel {
    protected final ItemModel wrapped;
    protected final PerspectiveModelState parentState;

    public WrappedItemModel(ItemModel wrapped) {
        this.wrapped = java.util.Objects.requireNonNull(wrapped);
        this.parentState = wrapped instanceof PerspectiveModel perspective && perspective.getModelState() != null
                ? perspective.getModelState() : PerspectiveModelState.IDENTITY;
    }

    @Override public PerspectiveModelState getModelState() { return parentState; }

    @Override
    public void update(ItemStackRenderState state, ItemStack stack, ItemModelResolver resolver,
            ItemDisplayContext display, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
        renderWrapped(state, stack, resolver, display, level, owner, seed);
    }

    /** Add the wrapped model's complete render state, including its own transforms and foil. */
    protected void renderWrapped(ItemStackRenderState state, ItemStack stack, ItemModelResolver resolver,
            ItemDisplayContext display, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
        wrapped.update(state, stack, resolver, display, level, owner, seed);
    }
    @Override public boolean useAmbientOcclusion(){return wrapped instanceof PerspectiveModel p?p.useAmbientOcclusion():PerspectiveModel.super.useAmbientOcclusion();}
    @Override public boolean isGui3d(){return wrapped instanceof PerspectiveModel p?p.isGui3d():PerspectiveModel.super.isGui3d();}
    @Override public boolean usesBlockLight(){return wrapped instanceof PerspectiveModel p?p.usesBlockLight():PerspectiveModel.super.usesBlockLight();}
    @Override public boolean isCustomRenderer(){return wrapped instanceof PerspectiveModel p&&p.isCustomRenderer();}
    @Override public net.minecraft.client.renderer.texture.TextureAtlasSprite getParticleIcon(){
        return wrapped instanceof PerspectiveModel p?p.getParticleIcon():PerspectiveModel.super.getParticleIcon();
    }

    /**
     * Map only newly extracted ordinary geometry, leaving existing layers and native
     * special-renderer callbacks intact. Repartition materials and recalculate bounds;
     * the mapper may return null to suppress a quad. Tint indices and transforms are
     * inherited from the wrapped model. Use the ordinary overload for special models.
     */
    protected void renderWrapped(ItemStackRenderState state,ItemStack stack,ItemModelResolver resolver,
            ItemDisplayContext display,@Nullable ClientLevel level,@Nullable ItemOwner owner,int seed,
            java.util.function.UnaryOperator<net.minecraft.client.resources.model.geometry.BakedQuad> mapper){
        java.util.Objects.requireNonNull(mapper);
        var access=(codechicken.lib.internal.mixin.ItemStackRenderStateAccessor)state;
        int start=access.ccl$getActiveLayerCount();
        renderWrapped(state,stack,resolver,display,level,owner,seed);
        var layers=access.ccl$getLayers();
        for(int i=start;i<access.ccl$getActiveLayerCount();i++){
            var layer=layers[i];
            var source=(codechicken.lib.internal.mixin.ItemLayerAccessor)layer;
            if(source.ccl$getSpecialRenderer()!=null)continue;
            var quads=source.ccl$getQuads().all().stream().map(mapper).filter(java.util.Objects::nonNull).toList();
            layer.setQuads(net.minecraft.client.resources.model.geometry.ItemQuads.split(quads));
            var extents=new java.util.ArrayList<org.joml.Vector3fc>();
            for(var quad:quads)for(int v=0;v<4;v++)extents.add(new org.joml.Vector3f(quad.position(v)));
            var bounds=extents.toArray(org.joml.Vector3fc[]::new);
            layer.setExtents(()->bounds);
            state.appendModelIdentityElement(quads);
        }
        access.ccl$setCachedModelBoundingBox(null);
        // Mapper may depend on changing data not represented in the wrapped identity.
        state.setAnimated();
    }
}