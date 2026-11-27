package codechicken.lib.render;

import codechicken.lib.model.CachedFormat;
import codechicken.lib.model.Quad;
import codechicken.lib.util.VertexUtils;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.client.textures.UnitTextureAtlasSprite;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class VertexCodecTests {
    @Test
    public void namedElementsAndPackedRoundTrip() {
        var format = VertexFormat.builder(0)
                .addAttribute("Position", GpuFormat.RGB32_FLOAT)
                .addAttribute("Color", GpuFormat.RGBA8_UNORM)
                .addAttribute("UV2", GpuFormat.RG16_SINT)
                .addAttribute("Normal", GpuFormat.RGBA8_SNORM).build();
        var cached = CachedFormat.lookup(format);
        assertTrue(cached.hasNormal);
        assertTrue(cached.hasLightMap);
        int[] storage = new int[(format.getVertexSize() * 4 + 3) / 4];
        float[][] expected = {{0.25F, -3, 5, 1}, {1, 0, 128 / 255F, 1}, {240, 16, 0, 0}, {-1, 0, 1, 0}};
        for (int v = 0; v < 4; v++) {
            for (int e = 0; e < expected.length; e++) VertexUtils.pack(expected[e], storage, format, v, e);
        }
        float[] actual = new float[4];
        for (int v = 0; v < 4; v++) {
            for (int e = 0; e < expected.length; e++) {
                VertexUtils.unpack(storage, actual, format, v, e);
                assertArrayEquals(expected[e], actual, 0.0001F);
            }
        }
    }

    @Test
    public void editableQuadRoundTrip() {
        Quad original = new Quad();
        original.reset(CachedFormat.BLOCK);
        original.setQuadOrientation(Direction.NORTH);
        original.setTexture(UnitTextureAtlasSprite.INSTANCE);
        original.setQuadTint(3);
        for (int v = 0; v < 4; v++) {
            var vertex = original.vertices[v];
            vertex.vec()[0] = v & 1;
            vertex.vec()[1] = v >> 1;
            vertex.uv()[0] = v / 4F;
            vertex.uv()[1] = 1 - v / 4F;
            vertex.normal()[2] = -1;
            vertex.color()[0] = v / 3F;
        }
        var baked = original.bake();
        Quad decoded = new Quad();
        decoded.reset(CachedFormat.BLOCK);
        decoded.put(baked);
        var rebaked = decoded.bake();
        assertEquals(baked.materialInfo(), rebaked.materialInfo());
        assertEquals(baked.direction(), rebaked.direction());
        for (int v = 0; v < 4; v++) {
            assertEquals(baked.position(v), rebaked.position(v));
            assertEquals(baked.packedUV(v), rebaked.packedUV(v));
            assertEquals(baked.bakedColors().color(v), rebaked.bakedColors().color(v));
            assertEquals(baked.bakedNormals().normal(v), rebaked.bakedNormals().normal(v));
        }
    }
    @Test void quadBuilderRetainsSpriteAndRejectsIncompletePrimitive(){
        var builder=new codechicken.lib.render.buffer.BakedQuadVertexBuilder();
        builder.addVertex(0,0,0).setUv(0,0);
        builder.sprite(UnitTextureAtlasSprite.INSTANCE);
        builder.addVertex(0,1,0).setUv(0,1);
        builder.addVertex(1,1,0).setUv(1,1);
        builder.addVertex(1,0,0).setUv(1,0);
        var quads=builder.bake();
        assertEquals(1,quads.size());
        assertSame(UnitTextureAtlasSprite.INSTANCE,quads.getFirst().materialInfo().sprite());
        assertEquals(1,builder.bake().size());
        builder.addVertex(0,0,0);
        assertThrows(IllegalStateException.class,builder::bake);
        assertThrows(IllegalStateException.class,()->builder.setFormat(CachedFormat.BLOCK));
    }
    @Test void copyFromInitializesAndRejectsMismatchedFormat(){
        var builder=new codechicken.lib.render.buffer.BakedQuadVertexBuilder();
        builder.sprite(UnitTextureAtlasSprite.INSTANCE);
        for(int i=0;i<4;i++)builder.addVertex(i&1,i>>1,0).setNormal(0,0,1);
        var original=new Quad(CachedFormat.BLOCK);
        original.put(builder.bake().getFirst());
        var copy=new Quad().copyFrom(original);
        assertTrue(copy.full);
        assertEquals(original.bake().materialInfo(),copy.bake().materialInfo());
        var wrong=new Quad(CachedFormat.lookup(com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION));
        assertThrows(IllegalArgumentException.class,()->wrong.copyFrom(original));
    }
    @Test void instanceLightingRoundTripPreservesSmoothCoordinatesAndOverlay(){
        var builder=new codechicken.lib.render.buffer.BakedQuadVertexBuilder();
        builder.sprite(UnitTextureAtlasSprite.INSTANCE);
        int overlay=net.minecraft.client.renderer.texture.OverlayTexture.pack(3,10);
        for(int v=0;v<4;v++)builder.addVertex(v&1,v>>1,0).setNormal(0,0,1).setUv2(17+v,239-v).setOverlay(overlay);
        var lit=builder.bakeWithInstances().getFirst();
        assertEquals(overlay,lit.overlay());
        var instance=lit.instance();
        for(int v=0;v<4;v++)assertEquals(net.minecraft.util.LightCoordsUtil.smoothPack(17+v,239-v),instance.getLightCoords(v));
        var editable=new Quad(CachedFormat.lookup(com.mojang.blaze3d.vertex.DefaultVertexFormat.ENTITY));
        editable.put(lit);
        var roundtrip=editable.bakeWithInstance(0,0);
        assertEquals(lit.overlay(),roundtrip.overlay());
        for(int v=0;v<4;v++)assertEquals(lit.light(v),roundtrip.light(v));
        instance.setLightCoords(0,0);
        assertEquals(lit.light0(),lit.instance().getLightCoords(0));
    }

    @Test void triangleInstanceDuplicatesLastVertexAndRejectsDifferentOverlays(){
        var builder=new codechicken.lib.render.buffer.BakedQuadVertexBuilder(com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES);
        builder.sprite(UnitTextureAtlasSprite.INSTANCE);
        for(int v=0;v<3;v++)builder.addVertex(v&1,v>>1,0).setNormal(0,0,1).setUv2(11*v,240);
        var lit=builder.bakeWithInstances().getFirst();
        assertEquals(lit.light2(),lit.light3());
        assertEquals(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,lit.overlay());
        builder.reset();builder.sprite(UnitTextureAtlasSprite.INSTANCE);
        for(int v=0;v<3;v++)builder.addVertex(v&1,v>>1,0).setNormal(0,0,1).setUv1(v,10);
        assertThrows(IllegalArgumentException.class,builder::bakeWithInstances);
    }

    @Test void perspectiveUvLockUsesNativeFaceMapping(){
        var transform=new com.mojang.math.Transformation(new org.joml.Matrix4f().rotateY((float)Math.PI/2));
        var locked=new codechicken.lib.model.PerspectiveModelState(java.util.Map.of(),transform,true);
        var unlocked=new codechicken.lib.model.PerspectiveModelState(java.util.Map.of(),transform,false);
        for(var face:Direction.values()){
            var expected=net.minecraft.core.BlockMath.getFaceTransformation(transform,face).getMatrix();
            assertTrue(expected.equals(locked.faceTransformation(face),0.00001F));
            assertTrue(new org.joml.Matrix4f(locked.faceTransformation(face)).mul(locked.inverseFaceTransformation(face)).equals(new org.joml.Matrix4f(),0.00001F));
            assertTrue(unlocked.faceTransformation(face).equals(new org.joml.Matrix4f(),0.00001F));
        }
    }
}