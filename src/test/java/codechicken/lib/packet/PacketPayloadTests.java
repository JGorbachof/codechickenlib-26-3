package codechicken.lib.packet;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PacketPayloadTests {
    @Test void outgoingPayloadIsIndependentOfLaterWritesAndReusable() {
        var packet=new PacketCustom(Identifier.parse("ccl:test"),200,null).writeInt(42);
        var payload=(PacketCustom.Pkt)packet.toCustomPayload();
        packet.writeInt(77);
        var codec=PacketCustomChannel.payloadCodec(payload.type());
        var buf=new FriendlyByteBuf(Unpooled.buffer());
        try {
            codec.encode(buf,payload);
            codec.encode(buf,payload);
            assertEquals(10,buf.readableBytes());
            assertEquals(200,buf.readUnsignedByte());
            assertEquals(42,buf.readInt());
            var decoded=codec.decode(buf);
            var input=new PacketCustom(decoded);
            try {
                assertEquals(200,input.getType());
                assertEquals(42,input.readInt());
                assertEquals(0,buf.readableBytes());
            } finally {input.releaseInbound();}
        } finally {buf.release();packet.toFriendlyByteBuf().release();}
    }
    @Test void emptyPayloadRejectedWithoutAllocatingNettyCopy() {
        var packet=new PacketCustom(Identifier.parse("ccl:test"),0,null);
        var codec=PacketCustomChannel.payloadCodec(((PacketCustom.Pkt)packet.toCustomPayload()).type());
        var empty=new FriendlyByteBuf(Unpooled.buffer());
        try {assertThrows(io.netty.handler.codec.DecoderException.class,()->codec.decode(empty));}
        finally {empty.release();packet.toFriendlyByteBuf().release();}
    }
    @Test void byteTagCopiesOnlyWrittenBytesFromDirectAndSlicedBuffers() {
        var direct=Unpooled.directBuffer(32);
        try {
            direct.writeBytes(new byte[]{9,1,2,3,8});
            var slice=direct.slice(1,3);
            slice.readByte();
            var data=new codechicken.lib.data.MCDataByteBuf(slice);
            assertArrayEquals(new byte[]{1,2,3},data.snapshotBytes());
            assertArrayEquals(new byte[]{1,2,3},((net.minecraft.nbt.ByteArrayTag)data.toTag()).getAsByteArray());
        } finally {direct.release();}
    }
}