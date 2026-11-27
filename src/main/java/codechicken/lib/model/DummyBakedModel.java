package codechicken.lib.model;

import codechicken.lib.texture.TextureUtils;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import java.util.List;

/** Intentionally empty model for the split block/item rendering APIs. */
public class DummyBakedModel implements BlockStateModel, BlockStateModelPart, ItemModel {
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand) {
        return getQuads(side);
    }

    @Override public List<BakedQuad> getQuads(@Nullable Direction side) { return List.of(); }
    @Override public void collectParts(RandomSource random, List<BlockStateModelPart> parts) { parts.add(this); }
    @Override public int materialFlags() { return 0; }
    @Override public boolean useAmbientOcclusion() { return false; }
    public boolean isGui3d() { return false; }
    public boolean usesBlockLight() { return false; }
    public boolean isCustomRenderer() { return false; }
    public TextureAtlasSprite getParticleIcon() { return TextureUtils.getMissingSprite(); }
    @Override public Material.Baked particleMaterial() { return new Material.Baked(getParticleIcon(), false); }
    @Override public void update(ItemStackRenderState state, ItemStack stack, ItemModelResolver resolver,
            ItemDisplayContext display, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
        // No layers: this model intentionally renders nothing.
    }
}