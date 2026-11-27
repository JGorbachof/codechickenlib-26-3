package codechicken.lib.model.bakedmodels;

import codechicken.lib.model.DummyBakedModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** Selects an item model for each render extraction instead of using the removed ItemOverrides. */
public class OverrideListModel extends DummyBakedModel {
    @FunctionalInterface
    public interface Selector {
        @Nullable ItemModel resolve(ItemStack stack, ItemDisplayContext display, @Nullable ClientLevel level,
                @Nullable ItemOwner owner, int seed);
    }

    private final Selector overrides;
    private final ItemModel fallback;

    public OverrideListModel(Selector overrides) {
        this(overrides, new DummyBakedModel());
    }

    public OverrideListModel(Selector overrides, ItemModel fallback) {
        this.overrides = Objects.requireNonNull(overrides);
        this.fallback = Objects.requireNonNull(fallback);
    }

    public Selector getOverrides() { return overrides; }

    @Override
    public void update(ItemStackRenderState state, ItemStack stack, ItemModelResolver resolver,
            ItemDisplayContext display, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
        ItemModel selected = overrides.resolve(stack, display, level, owner, seed);
        if (selected == null || selected == this) selected = fallback;
        if (selected == this) throw new IllegalStateException("Recursive item model fallback");
        selected.update(state, stack, resolver, display, level, owner, seed);
    }
}