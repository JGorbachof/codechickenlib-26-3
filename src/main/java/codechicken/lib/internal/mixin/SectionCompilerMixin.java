package codechicken.lib.internal.mixin;

import codechicken.lib.render.block.CCBlockRendererDispatcher;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SectionCompiler.class)
abstract class SectionCompilerMixin {
    @Redirect(method = "compile(Lnet/minecraft/core/SectionPos;Lnet/minecraft/client/renderer/chunk/RenderSectionRegion;Lcom/mojang/blaze3d/vertex/VertexSorting;Lnet/minecraft/client/renderer/SectionBufferBuilderPack;Ljava/util/List;)Lnet/minecraft/client/renderer/chunk/SectionCompiler$Results;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/ModelBlockRenderer;tesselateBlock(Lnet/minecraft/client/renderer/block/BlockQuadOutput;FFFLnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;J)V"))
    private void ccl$renderBlock(ModelBlockRenderer renderer, BlockQuadOutput output, float x, float y, float z,
            BlockAndTintGetter world, BlockPos pos, BlockState state, BlockStateModel model, long seed) {
        CCBlockRendererDispatcher.renderBatched(renderer,output,x,y,z,world,pos,state,model,seed);
    }
    @Redirect(method="compile(Lnet/minecraft/core/SectionPos;Lnet/minecraft/client/renderer/chunk/RenderSectionRegion;Lcom/mojang/blaze3d/vertex/VertexSorting;Lnet/minecraft/client/renderer/SectionBufferBuilderPack;Ljava/util/List;)Lnet/minecraft/client/renderer/chunk/SectionCompiler$Results;",
            at=@At(value="INVOKE",target="Lnet/neoforged/neoforge/client/fluid/CustomFluidRenderer;renderFluid(Lnet/minecraft/client/renderer/block/FluidRenderer;Lnet/minecraft/world/level/material/FluidState;Lnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/client/renderer/block/FluidRenderer$Output;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean ccl$customFluid(net.neoforged.neoforge.client.fluid.CustomFluidRenderer nativeRenderer,
            net.minecraft.client.renderer.block.FluidRenderer renderer,net.minecraft.world.level.material.FluidState fluid,
            BlockAndTintGetter world,BlockPos pos,net.minecraft.client.renderer.block.FluidRenderer.Output output,BlockState state){
        return CCBlockRendererDispatcher.renderSectionFluid(world,pos,output,state,fluid,
                ()->nativeRenderer.renderFluid(renderer,fluid,world,pos,output,state),
                ()->renderer.tesselate(world,pos,output,state,fluid));
    }
}