package codechicken.lib.render.item;

import codechicken.lib.model.PerspectiveModel;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import java.util.ArrayList;
import java.util.function.Consumer;

public interface IItemRenderer extends PerspectiveModel {
    /** Submit custom geometry via the collector; do not retain a live vertex buffer. */
    void renderItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, SubmitNodeCollector collector,
                    int packedLight, int packedOverlay, boolean foil, int outlineColor);

    /** Override for models that extend beyond the standard item cube. */
    default void getExtents(Consumer<Vector3fc> output) {
        for (int x = 0; x <= 1; x++) for (int y = 0; y <= 1; y++) for (int z = 0; z <= 1; z++)
            output.accept(new Vector3f(x, y, z));
    }

    @Override default boolean isCustomRenderer() { return true; }

    @Override
    default void update(ItemStackRenderState state, ItemStack stack, ItemModelResolver resolver,
            ItemDisplayContext display, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
        var renderer = this;
        var layer = state.newLayer();
        state.appendModelIdentityElement(this);
        // Custom renderer may depend on arbitrary stack components; avoid stale GUI caching.
        state.setAnimated();
        layer.setUsesBlockLight(usesBlockLight());
        layer.setParticleMaterial(new Material.Baked(getParticleIcon(), false));
        layer.setLocalTransform(nativeLayerTransform(display));
        if (stack.hasFoil()) layer.setFoilType(ItemStackRenderState.FoilType.STANDARD);
        var extents = new ArrayList<Vector3fc>();
        getExtents(v -> extents.add(new Vector3f(v)));
        layer.setExtents(() -> extents.toArray(Vector3fc[]::new));
        layer.setupSpecialModel(new SpecialModelRenderer<ItemStack>() {
            @Override public ItemStack extractArgument(ItemStack item) { return item.copy(); }
            @Override public void getExtents(Consumer<Vector3fc> out) { extents.forEach(out); }
            @Override public void submit(ItemStack item, PoseStack pose, SubmitNodeCollector collector,
                    int light, int overlay, boolean foil, int outline) {
                pose.pushPose();
                try {

                    renderer.renderItem(item, display, pose, collector, light, overlay, foil, outline);
                } finally { pose.popPose(); }
            }
        }, stack.copy());
    }
}