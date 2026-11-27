package codechicken.lib.render;

import codechicken.lib.colour.Colour;
import codechicken.lib.model.CachedFormat;
import codechicken.lib.model.IVertexConsumer;
import codechicken.lib.model.Quad;
import codechicken.lib.render.lighting.LC;
import codechicken.lib.render.pipeline.IVertexSource;
import codechicken.lib.render.pipeline.attribute.AttributeKey;
import codechicken.lib.render.pipeline.attribute.AttributeKey.AttributeKeyRegistry;
import codechicken.lib.render.pipeline.attribute.ColourAttribute;
import codechicken.lib.render.pipeline.attribute.LightCoordAttribute;
import codechicken.lib.render.pipeline.attribute.NormalAttribute;
import codechicken.lib.vec.Vector3;
import codechicken.lib.vec.Vertex5;
import com.google.common.annotations.VisibleForTesting;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;

import java.util.Arrays;
import java.util.function.Supplier;

import static net.covers1624.quack.util.SneakyUtils.unsafeCast;
import static java.util.Objects.requireNonNull;

/**
 * Created by covers1624 on 12/5/20.
 */
public class BakedVertexSource implements IVertexSource, IVertexConsumer {

    private static final ThreadLocal<BakedVertexSource> instances = ThreadLocal.withInitial(BakedVertexSource::new);

    private final Quad unpacker = new Quad(CachedFormat.BLOCK);

    private int vertexIndex = 0;
    private Vertex5[] vertices = new Vertex5[0];
    private Object[] attributes = new Object[0];
    private TextureAtlasSprite[] sprites = new TextureAtlasSprite[0];
    private boolean[] instanceCoordinates=new boolean[0];
    private int[] instanceLights=new int[0],instanceOverlays=new int[0];

    @VisibleForTesting
    BakedVertexSource() {
        ensureAttr(AttributeKeyRegistry.numAttributes() - 1);
        ensureSpace(24); //Ensure enough space for a full standard block model.
    }

    public static BakedVertexSource instance() {
        return instances.get();
    }

    @Override
    public Vertex5[] getVertices() {
        return vertices;
    }

    @Override
    public int getVertexCount() {
        return vertexIndex;
    }

    @Override
    public <T> T getAttribute(AttributeKey<T> attr) {
        ensureAttr(attr.attributeKeyIndex);
        return unsafeCast(attributes[attr.attributeKeyIndex]);
    }

    @Override
    public boolean hasAttribute(AttributeKey<?> attr) {
        return attr == NormalAttribute.attributeKey || attr == ColourAttribute.attributeKey || attr == LightCoordAttribute.attributeKey;
    }

    @Override
    public void prepareVertex(CCRenderState ccrs) {
        ccrs.sprite = sprites[ccrs.vertexIndex];
    }

    public void reset() {
        reset(CachedFormat.BLOCK);
    }

    public void reset(CachedFormat format) {
        vertexIndex = 0;
        unpacker.reset(format);
    }

    // Use getVertexCount()
    @Deprecated(forRemoval = true, since = "1.18.2")
    public int availableVertices() {
        return vertexIndex;
    }

    private void onFull() {
        ensureSpace(getVertexCount() + 4);
        for (int i = 0; i < 4; i++) {
            int v = vertexIndex++;
            Quad.Vertex vertex = unpacker.vertices[i];
            Vertex5 vertex5 = vertices[v];
            vertex5.vec.set(vertex.vec());
            vertex5.uv.set(vertex.uv());

            Vector3 normal = getAttr(NormalAttribute.attributeKey)[v];
            if (unpacker.format().hasNormal) normal.set(vertex.normal());
            else normal.set(requireNonNull(unpacker.orientation).getStepX(), unpacker.orientation.getStepY(), unpacker.orientation.getStepZ());
            getAttr(LightCoordAttribute.attributeKey)[v].compute(vertex5.vec, normal);

            getAttr(ColourAttribute.attributeKey)[v] = Colour.packRGBA(vertex.color());

            sprites[v] = unpacker.sprite;
            instanceCoordinates[v]=false;
        }
        unpacker.rewind();
    }

    private void ensureAttr(int aIdx) {
        if (attributes.length <= aIdx) {
            attributes = Arrays.copyOf(attributes, aIdx + 1);
        }
    }

    //Expands array storage.
    @VisibleForTesting
    void ensureSpace(int numVertices) {
        //If we don't have enough, or are full, expand.
        if (vertices.length < numVertices) {
            int prevLen = vertices.length;
            int fillStart = prevLen;
            vertices = Arrays.copyOf(vertices, numVertices);
            fill(vertices, fillStart, numVertices, Vertex5::new);
            for (int aIdx = 0; aIdx < attributes.length; aIdx++) {
                Object attr = attributes[aIdx];
                AttributeKey<?> key = AttributeKeyRegistry.getAttributeKey(aIdx);
                if (attr == null) {
                    attr = key.createDefault(numVertices);
                } else {
                    attr = key.copy(unsafeCast(attr), numVertices);
                }
                attributes[aIdx] = attr;
                //Fill non primitive vertex attributes with new things.
                if (key == NormalAttribute.attributeKey) {
                    fill((Object[]) attr, fillStart, numVertices, Vector3::new);
                } else if (key == LightCoordAttribute.attributeKey) {
                    fill((Object[]) attr, fillStart, numVertices, LC::new);
                }
            }
            sprites = Arrays.copyOf(sprites, numVertices);
            instanceCoordinates=Arrays.copyOf(instanceCoordinates,numVertices);
            instanceLights=Arrays.copyOf(instanceLights,numVertices);
            instanceOverlays=Arrays.copyOf(instanceOverlays,numVertices);
        }
    }

    private <T> T getAttr(AttributeKey<T> key) {
        return unsafeCast(attributes[key.attributeKeyIndex]);
    }

    @Override
    public void put(Quad quad) {
        unpacker.put(quad);
        onFull();
    }

    @Override
    public void put(int element, float... data) {
        unpacker.put(element, data);
        if (unpacker.full) {
            onFull();
        }
    }

    @Override
    public void put(codechicken.lib.model.LitQuad quad){
        put(quad.quad(),quad.instance());
    }

    /** Preserve native per-instance color/light/overlay independently of logical vertex format. */
    public void put(net.minecraft.client.resources.model.geometry.BakedQuad quad,com.mojang.blaze3d.vertex.QuadInstance instance){
        java.util.Objects.requireNonNull(instance);
        int start=vertexIndex;
        codechicken.lib.util.VertexUtils.putQuad(this,quad,instance);
        if(vertexIndex!=start+4)throw new IllegalStateException("Incomplete instance quad");
        for(int i=0;i<4;i++){
            instanceCoordinates[start+i]=true;
            instanceLights[start+i]=instance.getLightCoords(i);
            instanceOverlays[start+i]=instance.overlayCoords();
        }
    }
    @Override public boolean hasInstanceCoordinates(int vertex){
        return vertex>=0&&vertex<vertexIndex&&instanceCoordinates[vertex];
    }
    @Override public int instanceLight(int vertex){
        if(!hasInstanceCoordinates(vertex))throw new IllegalStateException("No instance light");
        return instanceLights[vertex];
    }
    @Override public int instanceOverlay(int vertex){
        if(!hasInstanceCoordinates(vertex))throw new IllegalStateException("No instance overlay");
        return instanceOverlays[vertex];
    }

    //@formatter:off
    @Override public VertexFormat getVertexFormat() { return unpacker.getVertexFormat(); }
    @Override public void setQuadTint(int tint) { unpacker.setQuadTint(tint); }
    @Override public void setQuadOrientation(Direction orientation) { unpacker.setQuadOrientation(orientation); }
    @Override public void setApplyDiffuseLighting(boolean diffuse) { unpacker.setApplyDiffuseLighting(diffuse); }
    @Override public void setTexture(TextureAtlasSprite texture) { unpacker.setTexture(texture); }
    @Override public void setMaterialInfo(net.minecraft.client.resources.model.geometry.BakedQuad.MaterialInfo info) { unpacker.setMaterialInfo(info); }
    //@formatter:on

    private static void fill(Object[] arr, int start, int end, Supplier<Object> supplier) {
        for (int i = start; i < end; i++) {
            arr[i] = supplier.get();
        }
    }
}
