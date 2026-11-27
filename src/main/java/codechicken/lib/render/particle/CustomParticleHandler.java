package codechicken.lib.render.particle;



import codechicken.lib.raytracer.VoxelShapeBlockHitResult;
import codechicken.lib.vec.Cuboid6;
import codechicken.lib.vec.Vector3;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleEngine;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;




import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;


import static java.util.Objects.requireNonNull;

/**
 * Created by covers1624 on 21/11/2016.
 */
public class CustomParticleHandler {

    /**
     * Call from {@link Block#addLandingEffects}
     * Provided the model bound is an instance of IModelParticleProvider, you will have landing particles just handled for you.
     *
     * @param world        The world.
     * @param pos          The position of the block.
     * @param entity       The entity.
     * @param numParticles The number of particles to spawn.
     * @return Always true for this, basically just return the result of this method inside {@link Block#addLandingEffects}
     */
    public static boolean handleLandingEffects(ServerLevel world, BlockPos pos, LivingEntity entity, int numParticles) {
        return ServerParticleHandler.handleLandingEffects(world,pos,entity,numParticles);
    }

    public static boolean handleRunningEffects(Level world, BlockPos pos, BlockState state, Entity entity) {
        if (!world.isClientSide()) return false;
        //Spoof a raytrace from the feet.
        BlockHitResult traceResult = new BlockHitResult(entity.position().add(0, 1, 0), Direction.UP, pos, false);
        BlockStateModel model = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state);
        if (model instanceof IModelParticleProvider) {
            ParticleEngine particleManager = Minecraft.getInstance().particleEngine;
            List<TextureAtlasSprite> sprites = new ArrayList<>(((IModelParticleProvider) model).getHitEffects(traceResult, state, (ClientLevel) world, pos));
            if (sprites.isEmpty()) return false;
            TextureAtlasSprite rolledSprite = sprites.get(world.getRandom().nextInt(sprites.size()));
            double x = entity.getX() + (world.getRandom().nextFloat() - 0.5D) * entity.getBbWidth();
            double y = entity.getBoundingBox().minY + 0.1D;
            double z = entity.getZ() + (world.getRandom().nextFloat() - 0.5D) * entity.getBbWidth();
            particleManager.add(new CustomBreakingParticle((ClientLevel) world, x, y, z, -entity.getDeltaMovement().x * 4.0D, 1.5D, -entity.getDeltaMovement().z * 4.0D, rolledSprite));
            return true;
        }

        return false;
    }

    /**
     * Call from {@link Block#addLandingEffects}
     * Provided the model bound is an instance of IModelParticleProvider, you will have landing particles just handled for you.
     * Use the default PerspectiveModel implementations in CCL, Hit effects will be polled from your model based on your bounding box hit.
     *
     * @param state       The state.
     * @param world       The world.
     * @param traceResult The trace result.
     * @param manager     The ParticleManager.
     * @return True if particles were added, basically just return the result of this method inside {@link Block#addLandingEffects}
     */
    public static boolean handleHitEffects(BlockState state, Level world, HitResult traceResult, ParticleEngine manager) {
        if (!world.isClientSide()) return false;
        if (traceResult instanceof BlockHitResult hit) {
            BlockPos pos = hit.getBlockPos();
            BlockStateModel model = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state);
            if (model instanceof IModelParticleProvider) {
                Cuboid6 bounds = new Cuboid6();
                if (hit instanceof VoxelShapeBlockHitResult) {
                    bounds.set(((VoxelShapeBlockHitResult) hit).shape.bounds());
                } else {
                    bounds.set(state.getShape(world, pos).bounds());
                }
                bounds = bounds.copy().add(pos);
                Set<TextureAtlasSprite> hitSprites = ((IModelParticleProvider) model).getHitEffects(hit, state, (ClientLevel) world, pos);
                List<TextureAtlasSprite> sprites = new ArrayList<>(hitSprites);
                if (sprites.isEmpty()) return false;
                addBlockHitEffects(world, bounds, hit.getDirection(), sprites.get(world.getRandom().nextInt(sprites.size())), manager);
                return true;
            }
        }
        return false;
    }

    /**
     * {@link IClientBlockExtensions#addHitEffects}
     * Provided the model bound is an instance of IModelParticleProvider, you will have landing particles just handled for you.
     * Use the default PerspectiveModel implementations inside CCL, Destroy effects will just be handled for you.
     *
     * @param world   The world.
     * @param pos     The position of the block.
     * @param manager The ParticleManager.
     * @return True if particles were added, basically just return the result of this method inside {@link IClientBlockExtensions#addHitEffects}
     */
    public static boolean handleDestroyEffects(Level world, BlockPos pos, BlockState state, ParticleEngine manager) {
        if (!world.isClientSide()) return false;
        BlockStateModel model = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state);
        if (model instanceof IModelParticleProvider) {
            Cuboid6 bounds = new Cuboid6(state.getShape(world, pos).bounds());
            var sprites = new ArrayList<>(((IModelParticleProvider) model).getDestroyEffects(state, (ClientLevel) world, pos));
            if (sprites.isEmpty()) return false;
            addBlockDestroyEffects(world, bounds.add(pos), sprites, manager);
            return true;
        }
        return false;
    }

    public static void addLandingEffects(Level world, BlockPos pos, BlockState state, Vector3 entityPos, int numParticles) {
        if (!world.isClientSide()) return;
        //Spoof a raytrace from the feet.
        BlockHitResult traceResult = new BlockHitResult(new Vec3(entityPos.x, pos.getY() + 1, entityPos.z), Direction.UP, pos, false);
        ParticleEngine manager = Minecraft.getInstance().particleEngine;
        Random randy = new Random();
        BlockStateModel model = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state);
        if (model instanceof IModelParticleProvider) {
            List<TextureAtlasSprite> sprites = new ArrayList<>(((IModelParticleProvider) model).getHitEffects(traceResult, state, (ClientLevel) world, pos));

            if (sprites.isEmpty()) return;
            double speed = 0.15000000596046448D;
            if (numParticles != 0) {
                for (int i = 0; i < numParticles; i++) {
                    double mX = randy.nextGaussian() * speed;
                    double mY = randy.nextGaussian() * speed;
                    double mZ = randy.nextGaussian() * speed;
                    manager.add(CustomBreakingParticle.newLandingParticle((ClientLevel) world, entityPos.x, entityPos.y, entityPos.z, mX, mY, mZ, sprites.get(randy.nextInt(sprites.size()))));
                }
            }
        }
    }

    public static void addBlockHitEffects(Level world, Cuboid6 bounds, Direction side, TextureAtlasSprite icon, ParticleEngine particleManager) {
        if (!world.isClientSide()) return;
        float border = 0.1F;
        Vector3 diff = bounds.max.copy().subtract(bounds.min).add(-2 * border);
        diff.x *= world.getRandom().nextDouble();
        diff.y *= world.getRandom().nextDouble();
        diff.z *= world.getRandom().nextDouble();
        Vector3 pos = diff.add(bounds.min).add(border);

        if (side == Direction.DOWN) {
            diff.y = bounds.min.y - border;
        }
        if (side == Direction.UP) {
            diff.y = bounds.max.y + border;
        }
        if (side == Direction.NORTH) {
            diff.z = bounds.min.z - border;
        }
        if (side == Direction.SOUTH) {
            diff.z = bounds.max.z + border;
        }
        if (side == Direction.WEST) {
            diff.x = bounds.min.x - border;
        }
        if (side == Direction.EAST) {
            diff.x = bounds.max.x + border;
        }

        particleManager.add(new CustomBreakingParticle((ClientLevel) world, pos.x, pos.y, pos.z, 0, 0, 0, icon).setPower(0.2F).scale(0.6F));
    }

    public static void addBlockDestroyEffects(Level world, Cuboid6 bounds, List<TextureAtlasSprite> icons, ParticleEngine particleManager) {
        if (!world.isClientSide()) return;
        Vector3 diff = bounds.max.copy().subtract(bounds.min);
        Vector3 center = bounds.min.copy().add(bounds.max).multiply(0.5);
        Vector3 density = diff.copy().multiply(4).ceil();

        for (int i = 0; i < density.x; ++i) {
            for (int j = 0; j < density.y; ++j) {
                for (int k = 0; k < density.z; ++k) {
                    double x = bounds.min.x + (i + 0.5) * diff.x / density.x;
                    double y = bounds.min.y + (j + 0.5) * diff.y / density.y;
                    double z = bounds.min.z + (k + 0.5) * diff.z / density.z;
                    particleManager.add(new CustomBreakingParticle((ClientLevel) world, x, y, z, x - center.x, y - center.y, z - center.z, icons.get(world.getRandom().nextInt(icons.size()))));
                }
            }
        }
    }
}
