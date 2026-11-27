package codechicken.lib.model.bakedmodels;

import codechicken.lib.model.bakedmodels.ModelProperties.PerspectiveProperties;
import com.google.common.collect.ImmutableMap;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.resources.model.geometry.BakedQuad;

import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;


import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Created by covers1624 on 19/11/2016.
 */
@Deprecated // These may go away unless there is sufficient need for generic implementations like these.
public class PerspectiveAwareLayeredModel extends AbstractBakedPropertiesModel {

    private final Map<ChunkSectionLayer, List<BakedQuad>> unculledQuads;
    private final Map<ChunkSectionLayer, Map<Direction, List<BakedQuad>>> faceQuads;
    private final ChunkSectionLayer generallayer;
    private final java.util.Set<ChunkSectionLayer> chunkLayers;

    public PerspectiveAwareLayeredModel(Map<ChunkSectionLayer, Map<Direction, List<BakedQuad>>> faceQuads, PerspectiveProperties properties) {
        this(faceQuads, ImmutableMap.of(), properties, ChunkSectionLayer.SOLID);
    }

    public PerspectiveAwareLayeredModel(Map<ChunkSectionLayer, Map<Direction, List<BakedQuad>>> faceQuads, Map<ChunkSectionLayer, List<BakedQuad>> unculledQuads, PerspectiveProperties properties, ChunkSectionLayer generallayer) {
        super(properties);
        var faces=new java.util.LinkedHashMap<ChunkSectionLayer,Map<Direction,List<BakedQuad>>>();
        faceQuads.forEach((layer,map)->{
            var byFace=new java.util.EnumMap<Direction,List<BakedQuad>>(Direction.class);
            map.forEach((face,quads)->byFace.put(face,quads.stream().map(q->withLayer(q,layer)).toList()));
            faces.put(layer,ImmutableMap.copyOf(byFace));
        });
        this.faceQuads=ImmutableMap.copyOf(faces);
        var unculled=new java.util.LinkedHashMap<ChunkSectionLayer,List<BakedQuad>>();
        unculledQuads.forEach((layer,quads)->unculled.put(layer,quads.stream().map(q->withLayer(q,layer)).toList()));
        this.unculledQuads=ImmutableMap.copyOf(unculled);
        this.generallayer = generallayer;
        var layers = new java.util.LinkedHashSet<>(faceQuads.keySet()); layers.addAll(unculledQuads.keySet()); chunkLayers = java.util.Collections.unmodifiableSet(layers);
    }

    static BakedQuad withLayer(BakedQuad quad,ChunkSectionLayer layer){
        var old=quad.materialInfo();
        if(old.layer()==layer)return quad;
        var transparency=switch(layer){
            case SOLID -> com.mojang.blaze3d.platform.Transparency.NONE;
            case CUTOUT -> com.mojang.blaze3d.platform.Transparency.TRANSPARENT;
            case TRANSLUCENT -> com.mojang.blaze3d.platform.Transparency.TRANSLUCENT;
        };
        var material=BakedQuad.MaterialInfo.of(new net.minecraft.client.resources.model.sprite.Material.Baked(old.sprite(),false),
                transparency,old.tintIndex(),old.shadeDirectionOverride(),old.lightEmission(),old.ambientOcclusion());
        return new BakedQuad(quad.position0(),quad.position1(),quad.position2(),quad.position3(),
                quad.packedUV0(),quad.packedUV1(),quad.packedUV2(),quad.packedUV3(),quad.direction(),
                material,quad.bakedNormals(),quad.bakedColors());
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand) {
        var quads = new java.util.ArrayList<BakedQuad>();
        for (var layer : chunkLayers) quads.addAll(getQuads(state, side, rand, layer));
        return quads;
    }

    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand, @Nullable ChunkSectionLayer layer) {
        if (layer == null) {
            layer = generallayer;
        }
        if (side == null) {
            return unculledQuads.getOrDefault(layer, List.of());
        }
        Map<Direction, List<BakedQuad>> faceQuadMap = faceQuads.get(layer);
        if (faceQuadMap != null) {
            return faceQuadMap.getOrDefault(side, List.of());
        }
        return List.of();
    }
}
