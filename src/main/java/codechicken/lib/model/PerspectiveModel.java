package codechicken.lib.model;

import codechicken.lib.texture.TextureUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.item.ItemDisplayContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

public interface PerspectiveModel extends ItemModel {
    @Nullable PerspectiveModelState getModelState();
    default boolean useAmbientOcclusion() { return true; }
    default boolean isGui3d() { return true; }
    default boolean usesBlockLight() { return true; }
    default boolean isCustomRenderer() { return false; }
    default TextureAtlasSprite getParticleIcon() { return TextureUtils.getMissingSprite(); }

    default Matrix4f perspectiveTransform(ItemDisplayContext context) {
        var state = getModelState();
        return state == null ? new Matrix4f() : new Matrix4f(state.getTransform(context).getMatrix());
    }

    /** Native layers apply a -0.5 translation before the local transform.
     * Conjugate the legacy centered transform so the resulting pose is T * center. */
    default Matrix4f nativeLayerTransform(ItemDisplayContext context) {
        return new Matrix4f().translation(0.5F,0.5F,0.5F)
                .mul(perspectiveTransform(context)).translate(-0.5F,-0.5F,-0.5F);
    }

    default PerspectiveModel applyTransform(ItemDisplayContext context, PoseStack stack, boolean leftFlip) {
        stack.last().mulPose(perspectiveTransform(context));
        return this;
    }
}