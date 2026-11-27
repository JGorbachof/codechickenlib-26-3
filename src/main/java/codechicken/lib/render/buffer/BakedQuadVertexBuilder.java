package codechicken.lib.render.buffer;

import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;

import codechicken.lib.model.CachedFormat;
import codechicken.lib.model.Quad;
import codechicken.lib.util.VectorUtils;
import codechicken.lib.vec.Vector3;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link VertexConsumer} implementation to build {@link BakedQuad}s.
 * <p>
 * Created by covers1624 on 4/23/20.
 */
public class BakedQuadVertexBuilder implements VertexConsumer, ISpriteAwareVertexConsumer {

    private final List<codechicken.lib.model.LitQuad> quadList = new ArrayList<>();
    private final PrimitiveTopology mode;

    private final Quad current = new Quad();
    private CachedFormat format;
    private int vertex = -1;
    private final int[] lights=new int[4],overlays=new int[4];
    private TextureAtlasSprite pendingSprite;

    public BakedQuadVertexBuilder() {
        this(PrimitiveTopology.QUADS);
    }

    public BakedQuadVertexBuilder(VertexFormat format) {
        this(format, PrimitiveTopology.QUADS);
    }

    public BakedQuadVertexBuilder(CachedFormat format) {
        this(format, PrimitiveTopology.QUADS);
    }

    public BakedQuadVertexBuilder(PrimitiveTopology mode) {
        this(CachedFormat.BLOCK, mode);
    }

    public BakedQuadVertexBuilder(VertexFormat format, PrimitiveTopology mode) {
        this(CachedFormat.lookup(format), mode);
    }

    public BakedQuadVertexBuilder(CachedFormat format, PrimitiveTopology mode) {
        if (mode != PrimitiveTopology.QUADS && mode != PrimitiveTopology.TRIANGLES) {
            throw new IllegalArgumentException("Only QUADS or TRIANGLES supported. Got: " + mode);
        }
        this.mode = mode;
        setFormat(format);
    }

    // Provided for interop with other mods that may provide different quad formats.
    public void setFormat(VertexFormat format) {
        setFormat(CachedFormat.lookup(format));
    }

    // Provided for interop with other mods that may provide different quad formats.
    public void setFormat(CachedFormat format) {
        if(vertex>=0)throw new IllegalStateException("Cannot change format with pending vertices.");
        this.format = java.util.Objects.requireNonNull(format);
        current.reset(format);
    }

    public void reset() {
        quadList.clear();
        vertex = -1;
        pendingSprite = null;
        current.reset(format);
    }

    @Override
    public void sprite(TextureAtlasSprite sprite) {
        pendingSprite = java.util.Objects.requireNonNull(sprite);
        if(vertex>=0 && vertex<mode.primitiveLength-1 && current.sprite==null)current.setTexture(sprite);
        else if(vertex==mode.primitiveLength-1 && current.sprite==null)current.setTexture(sprite);
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        if (!format.hasPosition) throw new IllegalStateException("Baked quad format requires position.");

        endPrevVertex();
        if (vertex == 0 && pendingSprite != null) current.setTexture(pendingSprite);
        lights[vertex]=0;
        overlays[vertex]=net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY;
        current.vertices[vertex].vec()[0] = x;
        current.vertices[vertex].vec()[1] = y;
        current.vertices[vertex].vec()[2] = z;
        return this;
    }

    @Override
    public VertexConsumer setColor(int red, int green, int blue, int alpha) {
        if (!format.hasColor) return this;

        current.vertices[vertex].color()[0] = red / 255F;
        current.vertices[vertex].color()[1] = green / 255F;
        current.vertices[vertex].color()[2] = blue / 255F;
        current.vertices[vertex].color()[3] = alpha / 255F;
        return this;
    }

    @Override
    public VertexConsumer setColor(int color) {
        return setColor(color >>> 16 & 255, color >>> 8 & 255, color & 255, color >>> 24);
    }

    @Override
    public VertexConsumer setUv3(float u, float v) {
        throw new UnsupportedOperationException("Baked quads have no secondary glint UV.");
    }

    @Override
    public VertexConsumer setLineWidth(float width) {
        throw new UnsupportedOperationException("Baked quads have no line width.");
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        if (!format.hasUV) return this;

        current.vertices[vertex].uv()[0] = u;
        current.vertices[vertex].uv()[1] = v;
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        if(vertex<0)throw new IllegalStateException("No active vertex.");
        if(u<0||u>65535||v<0||v>65535)throw new IllegalArgumentException("Invalid overlay coordinate.");
        overlays[vertex]=net.minecraft.client.renderer.texture.OverlayTexture.pack(u,v);
        if (!format.hasOverlay) return this;

        current.vertices[vertex].overlay()[0] = u / (float) 0xF0;
        current.vertices[vertex].overlay()[1] = v / (float) 0xF0;
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        if(vertex<0)throw new IllegalStateException("No active vertex.");
        if(u<0||u>240||v<0||v>240)throw new IllegalArgumentException("Invalid light coordinate.");
        lights[vertex]=net.minecraft.util.LightCoordsUtil.smoothPack(u,v);
        if (!format.hasLightMap) return this;

        current.vertices[vertex].lightmap()[0] = u / (float) 0xF0;
        current.vertices[vertex].lightmap()[1] = v / (float) 0xF0;
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        if (!format.hasNormal) return this;

        current.vertices[vertex].normal()[0] = x;
        current.vertices[vertex].normal()[1] = y;
        current.vertices[vertex].normal()[2] = z;
        return this;
    }

    private void endPrevVertex() {
        vertex++;
        if (vertex == mode.primitiveLength) {
            if (mode == PrimitiveTopology.TRIANGLES) {
                lights[3]=lights[2];overlays[3]=overlays[2];
                //Quadulate.
                for (int e = 0; e < current.format().elementCount; e++) {
                    System.arraycopy(current.vertices[2].raw[e], 0, current.vertices[3].raw[e], 0, 4);
                }
            }
            if (current.sprite == null) {
                throw new IllegalStateException("Sprite not set.");
            }
            if (format.hasNormal && Vector3.fromArray(current.vertices[0].normal()).magSquared() > 0) {
                current.setQuadOrientation(VectorUtils.findSideE(Vector3.fromArray(current.vertices[0].normal())));
            } else {
                current.calculateOrientation(false);
            }
            for(int v=1;v<4;v++)if(overlays[v]!=overlays[0])throw new IllegalArgumentException("Native quad overlays must be uniform across vertices.");
            quadList.add(new codechicken.lib.model.LitQuad(current.bake(),lights[0],lights[1],lights[2],lights[3],overlays[0]));
            current.reset(format);
            vertex = 0;
        }
    }

    private void finish() {
        if(vertex>=0){
            if(vertex!=mode.primitiveLength-1)throw new IllegalStateException("Incomplete baked primitive: "+(vertex+1)+" vertices.");
            endPrevVertex();
        }
        vertex=-1;
    }
    /** Static model geometry; per-draw channels are available via bakeWithInstances(). */
    public List<BakedQuad> bake(){finish();return new ArrayList<>(quadList.stream().map(codechicken.lib.model.LitQuad::quad).toList());}
    public List<codechicken.lib.model.LitQuad> bakeWithInstances(){finish();return new ArrayList<>(quadList);
    }
}