package codechicken.lib.raytracer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HitResultTests {
    @Test void copiesPreserveMissBorderAndMetadata() {
        var hit=new SubHitBlockHitResult(true,Vec3.ZERO,Direction.NORTH,BlockPos.ZERO,false,7,2.5);
        var moved=hit.withDirection(Direction.SOUTH).withPosition(new BlockPos(1,2,3)).hitBorder();
        assertEquals(HitResult.Type.MISS,moved.getType());
        assertEquals(Direction.SOUTH,moved.getDirection());
        assertEquals(new BlockPos(1,2,3),moved.getBlockPos());
        assertTrue(moved.isWorldBorderHit());
        assertEquals(7,moved.subHit);
        assertEquals(2.5,moved.dist);
    }
}