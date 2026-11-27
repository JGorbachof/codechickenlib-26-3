package codechicken.lib.util;

import codechicken.lib.model.IVertexConsumer;
import com.mojang.renderpearl.api.GpuFormat;
import net.minecraft.client.model.geom.builders.UVPair;
import net.neoforged.neoforge.client.model.quad.BakedNormals;
import java.util.Arrays;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import com.mojang.renderpearl.api.vertex.VertexFormatElement;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.apache.commons.lang3.tuple.Pair;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Created by covers1624 on 9/7/22.
 */
public class VertexUtils {

    private static final ConcurrentMap<Pair<VertexFormat, VertexFormat>, int[]> formatMaps = new ConcurrentHashMap<>();

    private static final int[] DEFAULT_MAPPING = generateMapping(DefaultVertexFormat.BLOCK, DefaultVertexFormat.BLOCK);

    public static int[] mapFormats(VertexFormat from, VertexFormat to) {
        if (from.equals(DefaultVertexFormat.BLOCK) && to.equals(DefaultVertexFormat.BLOCK)) return DEFAULT_MAPPING;

        return formatMaps.computeIfAbsent(Pair.of(from, to), pair -> generateMapping(pair.getLeft(), pair.getRight()));
    }

    public static void putQuad(IVertexConsumer consumer, BakedQuad quad) {
        putQuad(consumer,quad,null);
    }

    /** Decode instance lighting/overlay and fold instance color into baked color. */
    public static void putQuad(IVertexConsumer consumer,BakedQuad quad,@org.jetbrains.annotations.Nullable com.mojang.blaze3d.vertex.QuadInstance instance) {
        consumer.setTexture(quad.materialInfo().sprite());
        consumer.setQuadOrientation(quad.direction());
        consumer.setQuadTint(quad.materialInfo().tintIndex());
        consumer.setApplyDiffuseLighting(quad.materialInfo().shadeDirectionOverride() != net.minecraft.core.Direction.UP);
        consumer.setMaterialInfo(quad.materialInfo());
        float[] data = new float[4];
        var elements = consumer.getVertexFormat().getElements();
        for (int v = 0; v < 4; v++) {
            for (int e = 0; e < elements.size(); e++) {
                Arrays.fill(data, 0);
                switch (elements.get(e).name()) {
                    case "Position" -> {
                        var pos = quad.position(v);
                        data[0] = pos.x(); data[1] = pos.y(); data[2] = pos.z(); data[3] = 1;
                    }
                    case "UV0" -> {
                        data[0] = UVPair.unpackU(quad.packedUV(v));
                        data[1] = UVPair.unpackV(quad.packedUV(v));
                    }
                    case "Color" -> {
                        int argb = quad.bakedColors().color(v);
                        if(instance!=null)argb=net.minecraft.util.ARGB.multiply(argb,instance.getColor(v));
                        data[0] = (argb >>> 16 & 255) / 255F;
                        data[1] = (argb >>> 8 & 255) / 255F;
                        data[2] = (argb & 255) / 255F;
                        data[3] = (argb >>> 24) / 255F;
                    }
                    case "UV2" -> {
                        if(instance!=null){
                            int light=instance.getLightCoords(v);
                            data[0]=net.minecraft.util.LightCoordsUtil.smoothBlock(light)/240F;
                            data[1]=net.minecraft.util.LightCoordsUtil.smoothSky(light)/240F;
                        }
                    }
                    case "UV1" -> {
                        if(instance!=null){
                            int overlay=instance.overlayCoords();
                            data[0]=(overlay&65535)/240F;
                            data[1]=(overlay>>>16)/240F;
                        }
                    }
                    case "Normal" -> {
                        int packed = quad.bakedNormals().normal(v);
                        if (BakedNormals.isUnspecified(packed)) {
                            var normal = quad.direction().getUnitVec3f();
                            data[0] = normal.x(); data[1] = normal.y(); data[2] = normal.z();
                        } else {
                            data[0] = BakedNormals.unpackX(packed);
                            data[1] = BakedNormals.unpackY(packed);
                            data[2] = BakedNormals.unpackZ(packed);
                        }
                    }
                }
                consumer.put(e, data);
            }
        }
    }

    public static void unpack(int[] from, float[] to, VertexFormat formatFrom, int v, int e) {
        VertexFormatElement element = formatFrom.getElements().get(e);
        GpuFormat format = element.format();
        var type = format.componentType();
        int size = type.byteSize();
        if (size * format.componentCount() != format.blockSize() || size > 4 || type.name().startsWith("OPAQUE"))
            throw new IllegalArgumentException("Unsupported packed vertex format: " + format);
        int start = v * formatFrom.getVertexSize() + element.offset();
        for (int i = 0; i < Math.min(4, to.length); i++) {
            if (i >= format.componentCount()) {
                to[i] = i == 3 && element.name().equals("Position") ? 1 : 0;
                continue;
            }
            long bits = readBytes(from, start + i * size, size);
            to[i] = switch (type) {
                case FLOAT_32 -> Float.intBitsToFloat((int) bits);
                case FLOAT_16 -> Float.float16ToFloat((short) bits);
                case UNORM_8 -> bits / 255F;
                case UNORM_16 -> bits / 65535F;
                case SNORM_8 -> Math.max(-1, (byte) bits / 127F);
                case SNORM_16 -> Math.max(-1, (short) bits / 32767F);
                case UINT_8, UINT_16, UINT_32 -> (float) bits;
                case SINT_8 -> (byte) bits;
                case SINT_16 -> (short) bits;
                case SINT_32 -> (int) bits;
                default -> throw new IllegalArgumentException("Unsupported vertex format: " + format);
            };
        }
    }

    public static void pack(float[] from, int[] to, VertexFormat formatTo, int v, int e) {
        VertexFormatElement element = formatTo.getElements().get(e);
        GpuFormat format = element.format();
        var type = format.componentType();
        int size = type.byteSize();
        if (size * format.componentCount() != format.blockSize() || size > 4 || type.name().startsWith("OPAQUE"))
            throw new IllegalArgumentException("Unsupported packed vertex format: " + format);
        int start = v * formatTo.getVertexSize() + element.offset();
        for (int i = 0; i < format.componentCount(); i++) {
            float f = i < from.length ? from[i] : 0;
            long bits = switch (type) {
                case FLOAT_32 -> Float.floatToRawIntBits(f);
                case FLOAT_16 -> Float.floatToFloat16(f);
                case UNORM_8 -> Math.round(Math.clamp(f, 0, 1) * 255);
                case UNORM_16 -> Math.round(Math.clamp(f, 0, 1) * 65535);
                case SNORM_8 -> Math.round(Math.clamp(f, -1, 1) * 127);
                case SNORM_16 -> Math.round(Math.clamp(f, -1, 1) * 32767);
                case UINT_8 -> Math.round(Math.clamp(f, 0, 255));
                case UINT_16 -> Math.round(Math.clamp(f, 0, 65535));
                case UINT_32 -> Math.round(Math.clamp((double) f, 0, 4294967295D));
                case SINT_8 -> Math.round(Math.clamp(f, -128, 127));
                case SINT_16 -> Math.round(Math.clamp(f, -32768, 32767));
                case SINT_32 -> Math.round(Math.clamp((double) f, Integer.MIN_VALUE, Integer.MAX_VALUE));
                default -> throw new IllegalArgumentException("Unsupported vertex format: " + format);
            };
            writeBytes(to, start + i * size, size, bits);
        }
    }

    private static long readBytes(int[] data, int offset, int size) {
        long bits = 0;
        for (int i = 0; i < size; i++) {
            int p = offset + i;
            bits |= (long) (data[p >> 2] >>> ((p & 3) * 8) & 255) << (i * 8);
        }
        return bits;
    }

    private static void writeBytes(int[] data, int offset, int size, long bits) {
        for (int i = 0; i < size; i++) {
            int p = offset + i;
            int shift = (p & 3) * 8;
            data[p >> 2] = (data[p >> 2] & ~(255 << shift)) | ((int) (bits >>> (i * 8) & 255) << shift);
        }
    }

    private static int[] generateMapping(VertexFormat from, VertexFormat to) {
        int fromCount = from.getElements().size();
        int toCount = to.getElements().size();
        int[] eMap = new int[fromCount];

        for (int e = 0; e < fromCount; e++) {
            VertexFormatElement expected = from.getElements().get(e);
            int e2;
            for (e2 = 0; e2 < toCount; e2++) {
                VertexFormatElement current = to.getElements().get(e2);
                if (expected.name().equals(current.name())) {
                    break;
                }
            }
            eMap[e] = e2;
        }
        return eMap;
    }
}
