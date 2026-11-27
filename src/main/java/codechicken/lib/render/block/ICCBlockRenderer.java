package codechicken.lib.render.block;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

/** Custom block and fluid rendering at the 26.3 quad-output and submission boundaries. */
public interface ICCBlockRenderer {
    /** Null layer denotes breaking. Other layers are logical material layers; the native section compiler may route leaves to an opaque buffer. */
    boolean canHandleBlock(BlockAndTintGetter world, BlockPos pos, BlockState state, @Nullable ChunkSectionLayer layer);

    /**
     * Emit block-local quads and their per-vertex color/light using the supplied output.
     * x/y/z are the section-relative output offset. Called on the chunk compiler thread.
     * The requested layer must match the emitted quad's material layer.
     */
    void renderBlock(BlockState state, BlockPos pos, BlockAndTintGetter world, BlockQuadOutput output,
                     float x, float y, float z, RandomSource random, @Nullable ChunkSectionLayer layer);

    default void renderBreaking(BlockState state, BlockPos pos, BlockAndTintGetter world, BlockQuadOutput output) {
        renderBlock(state, pos, world, output, 0, 0, 0, RandomSource.create(state.getSeed(pos)), null);
    }

    default boolean canHandleEntity(BlockState state) { return false; }
    default boolean canHandleEntity(BlockState state, @Nullable net.minecraft.client.renderer.block.model.BlockDisplayContext display) { return canHandleEntity(state); }

    default void renderEntity(BlockState state, PoseStack pose, SubmitNodeCollector collector,
                              int packedLight, int packedOverlay, int outlineColor) {
        throw new UnsupportedOperationException("Entity rendering requires an implementation when canHandleEntity returns true.");
    }

    /** Context-aware extracted block callback; existing implementations remain valid. */
    default void renderEntity(BlockState state,@Nullable net.minecraft.client.renderer.block.model.BlockDisplayContext display,
                              PoseStack pose,SubmitNodeCollector collector,int packedLight,int packedOverlay,int outlineColor){
        renderEntity(state,pose,collector,packedLight,packedOverlay,outlineColor);
    }

    default boolean canHandleFluid(BlockAndTintGetter world, BlockPos pos, BlockState state, FluidState fluid) { return false; }

    default void renderFluid(BlockPos pos, BlockAndTintGetter world, FluidRenderer.Output output,
                             BlockState state, FluidState fluid) {
        throw new UnsupportedOperationException("Fluid rendering requires an implementation when canHandleFluid returns true.");
    }
}