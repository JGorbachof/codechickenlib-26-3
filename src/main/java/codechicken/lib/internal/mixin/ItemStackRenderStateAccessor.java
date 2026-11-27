package codechicken.lib.internal.mixin;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemStackRenderState.class)
public interface ItemStackRenderStateAccessor {
    @Accessor("activeLayerCount") int ccl$getActiveLayerCount();
    @Accessor("layers") ItemStackRenderState.LayerRenderState[] ccl$getLayers();
    @Accessor("cachedModelBoundingBox") void ccl$setCachedModelBoundingBox(AABB bounds);
}