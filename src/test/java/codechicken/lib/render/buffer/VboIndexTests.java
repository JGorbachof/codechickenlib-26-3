package codechicken.lib.render.buffer;
import com.mojang.renderpearl.api.pipeline.*;
import org.junit.jupiter.api.Test;
import java.nio.*;
import static org.junit.jupiter.api.Assertions.*;
class VboIndexTests {
    @Test void expandsNativeQuadAndLineIndexOrder(){
        var bytes=ByteBuffer.allocate(24).order(ByteOrder.nativeOrder());
        VBORenderType.writeSequential(bytes,PrimitiveTopology.QUADS,IndexType.SHORT,12);
        bytes.flip();var shorts=new short[12];bytes.asShortBuffer().get(shorts);
        assertArrayEquals(new short[]{0,1,2,2,3,0,4,5,6,6,7,4},shorts);
        bytes.clear();VBORenderType.writeSequential(bytes,PrimitiveTopology.LINES,IndexType.INT,6);
        bytes.flip();var ints=new int[6];bytes.asIntBuffer().get(ints);
        assertArrayEquals(new int[]{0,1,2,3,2,1},ints);
    }
    @Test void rejectsIncompletePrimitivesAndShortOverflow(){
        assertThrows(IllegalArgumentException.class,()->VBORenderType.writeSequential(ByteBuffer.allocate(20),PrimitiveTopology.QUADS,IndexType.SHORT,5));
        assertThrows(IllegalArgumentException.class,()->VBORenderType.writeSequential(ByteBuffer.allocate(200000),PrimitiveTopology.POINTS,IndexType.SHORT,65537));
        assertThrows(IllegalArgumentException.class,()->VBORenderType.writeSequential(ByteBuffer.allocate(3),PrimitiveTopology.TRIANGLES,IndexType.INT,3));
    }
}