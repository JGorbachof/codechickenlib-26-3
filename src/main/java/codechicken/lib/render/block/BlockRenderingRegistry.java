package codechicken.lib.render.block;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;




import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Registry for {@link ICCBlockRenderer} instances.
 * <p>
 * Created by covers1624 on 8/09/2016.
 *
 * @see ICCBlockRenderer
 */


public class BlockRenderingRegistry {

    private static volatile boolean initialized = false;

    private static final Map<Block, ICCBlockRenderer> blockRenderers = new ConcurrentHashMap<>();
    private static final Map<Fluid, ICCBlockRenderer> fluidRenderers = new ConcurrentHashMap<>();
    private static final List<ICCBlockRenderer> globalRenderers = new java.util.concurrent.CopyOnWriteArrayList<>();

    public static void init() {
        // Hooks are installed by the mixin configuration, not by replacing Minecraft fields.
        if (!net.neoforged.fml.loading.FMLEnvironment.getDist().isClient()) throw new IllegalStateException("Block renderer initialization is client-only.");
        initialized = true;
    }

    public static boolean hasRenderers() {
        return initialized && (!blockRenderers.isEmpty() || !fluidRenderers.isEmpty() || !globalRenderers.isEmpty());
    }

    static boolean hasBlockRenderer(Block block){
        return initialized&&(!globalRenderers.isEmpty()||blockRenderers.containsKey(block));
    }
    static boolean hasFluidRenderer(Fluid fluid){
        return initialized&&(!globalRenderers.isEmpty()||fluidRenderers.containsKey(fluid));
    }

    /**
     * Register an {@link ICCBlockRenderer} for the given block.
     * <p>
     * Unlike {@link #registerGlobalRenderer}, registering your {@link ICCBlockRenderer} via this method
     * is guaranteed to only be tested against the block provided.
     *
     * @param block    The block to register to.
     * @param renderer The {@link ICCBlockRenderer}.
     * @throws IllegalArgumentException If the same Block is registered twice.
     */

    public static synchronized void registerRenderer(Block block, ICCBlockRenderer renderer) {
        ICCBlockRenderer prev = blockRenderers.get(block);
        if (prev != null) {
            throw new IllegalArgumentException("Renderer already registered for block. " + BuiltInRegistries.BLOCK.getKey(block));
        }
        blockRenderers.put(block, renderer);
    }

    /**
     * Register an {@link ICCBlockRenderer} for the given fluid.
     * <p>
     * Unlike {@link #registerGlobalRenderer}, registering your {@link ICCBlockRenderer} via this method
     * is guaranteed to only be tested against the fluid provided.
     *
     * @param fluid    The fluid to register to.
     * @param renderer The {@link ICCBlockRenderer}.
     * @throws IllegalArgumentException If the same Fluid is registered twice.
     */

    public static synchronized void registerRenderer(Fluid fluid, ICCBlockRenderer renderer) {
        ICCBlockRenderer prev = fluidRenderers.get(fluid);
        if (prev != null) {
            throw new IllegalArgumentException("Renderer already registered for fluid. " + BuiltInRegistries.FLUID.getKey(fluid));
        }
        fluidRenderers.put(fluid, renderer);
    }

    /**
     * Register a global {@link ICCBlockRenderer}, capable of overriding the
     * rendering of any block in world.
     *
     * @param renderer The {@link ICCBlockRenderer}.
     */

    public static synchronized void registerGlobalRenderer(ICCBlockRenderer renderer) {
        globalRenderers.add(java.util.Objects.requireNonNull(renderer));
    }

    @Nullable
    static ICCBlockRenderer findFor(Block block, Predicate<ICCBlockRenderer> pred) {
        for (ICCBlockRenderer renderer : globalRenderers) {
            if (pred.test(renderer)) {
                return renderer;
            }
        }

        ICCBlockRenderer found = blockRenderers.get(block);
        if (found != null && pred.test(found)) return found;

        return null;
    }

    @Nullable
    static ICCBlockRenderer findFor(Fluid fluid, Predicate<ICCBlockRenderer> pred) {
        for (ICCBlockRenderer renderer : globalRenderers) {
            if (pred.test(renderer)) {
                return renderer;
            }
        }

        ICCBlockRenderer found = fluidRenderers.get(fluid);
        if (found != null && pred.test(found)) return found;

        return null;
    }

}
