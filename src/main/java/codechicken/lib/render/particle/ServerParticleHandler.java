package codechicken.lib.render.particle;

import codechicken.lib.internal.network.CCLNetwork;
import codechicken.lib.packet.PacketCustom;
import codechicken.lib.vec.Vector3;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/** Common-side packet dispatch. No client renderer types are linked here. */
public final class ServerParticleHandler {
    private ServerParticleHandler() {}

    public static boolean handleLandingEffects(ServerLevel world,BlockPos pos,LivingEntity entity,int numParticles) {
        // The original custom packet is addressed only to the landing player.
        // Other entities retain vanilla landing particles instead of an invalid cast.
        if (!(entity instanceof ServerPlayer player) || entity.level()!=world || numParticles<=0) return false;
        PacketCustom packet=new PacketCustom(CCLNetwork.NET_CHANNEL,CCLNetwork.C_ADD_LANDING_EFFECTS,world.registryAccess());
        packet.writePos(pos);
        packet.writeVector(Vector3.fromEntity(entity));
        packet.writeVarInt(numParticles);
        packet.sendToPlayer(player);
        return true;
    }
}