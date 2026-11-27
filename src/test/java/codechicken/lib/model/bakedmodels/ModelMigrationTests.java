package codechicken.lib.model.bakedmodels;

import codechicken.lib.model.*;
import com.mojang.math.Transformation;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.EmptyModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.neoforged.neoforge.client.textures.UnitTextureAtlasSprite;
import org.joml.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ModelMigrationTests {
    @Test void nativeLayerTransformCentersBeforePerspective(){
        Matrix4f transform=new Matrix4f().translation(0.3F,0.2F,-0.1F).rotateY(0.8F).scale(0.6F);
        var state=new PerspectiveModelState(Map.of(ItemDisplayContext.GUI,new Transformation(transform)));
        PerspectiveModel model=new PerspectiveModel(){
            public PerspectiveModelState getModelState(){return state;}
            public void update(net.minecraft.client.renderer.item.ItemStackRenderState out,net.minecraft.world.item.ItemStack stack,net.minecraft.client.renderer.item.ItemModelResolver resolver,ItemDisplayContext display,net.minecraft.client.multiplayer.ClientLevel level,net.minecraft.world.entity.ItemOwner owner,int seed){}
        };
        var actual=new Matrix4f().translation(-0.5F,-0.5F,-0.5F).mul(model.nativeLayerTransform(ItemDisplayContext.GUI));
        assertTrue(actual.equals(new Matrix4f(transform).translate(-0.5F,-0.5F,-0.5F),0.00001F));
    }
    @Test void classModelAcceptsTransformAwareConstructor(){
        var loader=new ClassModelLoader.ItemClass(TransformAware.class.getName());
        var transform=new Matrix4f().translation(1,2,3);
        var model=(TransformAware)loader.bake(null,transform);
        transform.identity();
        assertEquals(new Matrix4f().translation(1,2,3),model.matrix);
        var legacy=new ClassModelLoader.ItemClass(EmptyModel.class.getName());
        assertInstanceOf(EmptyModel.class,legacy.bake(null,new Matrix4f()));
        assertThrows(IllegalArgumentException.class,()->legacy.bake(null,new Matrix4f().scale(2)));
    }
    public static class TransformAware extends EmptyModel {
        final Matrix4fc matrix;
        public TransformAware(Matrix4fc transform){matrix=transform;}
    }
    @Test void propertyCopiesRetainFlagsWithoutAtlasInitialization(){
        var props=ModelProperties.builder().withAO(true).withGui3D(false).withUsesBlockLight(true).build();
        var copy=ModelProperties.builder().copyFrom(props).withTransforms(PerspectiveModelState.IDENTITY).build();
        assertTrue(copy.isAmbientOcclusion());
        assertFalse(copy.isGui3d());
        assertTrue(copy.usesBlockLight());
    }
    @Test void layeredModelCopiesGeometryAndUsesDeclaredLayer(){
        var builder=new codechicken.lib.render.buffer.BakedQuadVertexBuilder();
        builder.sprite(UnitTextureAtlasSprite.INSTANCE);
        for(int i=0;i<4;i++)builder.addVertex(i&1,i>>1,0).setNormal(0,0,1);
        var quad=builder.bake().getFirst();
        var mutable=new ArrayList<>(List.of(quad));
        var props=ModelProperties.builder().withParticle(UnitTextureAtlasSprite.INSTANCE).withTransforms(PerspectiveModelState.IDENTITY).build();
        var model=new PerspectiveAwareLayeredModel(Map.of(),Map.of(ChunkSectionLayer.TRANSLUCENT,mutable),props,ChunkSectionLayer.SOLID);
        mutable.clear();
        var result=model.getQuads(null,null,RandomSource.create(0)).getFirst();
        assertEquals(ChunkSectionLayer.TRANSLUCENT,result.materialInfo().layer());
        assertEquals(quad.bakedColors(),result.bakedColors());
        assertEquals(quad.position0(),result.position0());
    }
    @Test void blockSnapshotUsesSameSeedForEveryFace(){
        var seeds=new ArrayList<Long>();
        var props=ModelProperties.builder().withParticle(UnitTextureAtlasSprite.INSTANCE).build();
        var model=new AbstractBakedPropertiesModel(props){
            public List<BakedQuad> getQuads(net.minecraft.world.level.block.state.BlockState state,Direction face,RandomSource random){
                seeds.add(random.nextLong());
                return List.of();
            }
        };
        var parts=new ArrayList<BlockStateModelPart>();
        model.collectParts(RandomSource.create(42),parts);
        assertEquals(7,seeds.size());
        assertEquals(1,new HashSet<>(seeds).size());
        assertEquals(1,parts.size());
        assertEquals(0,parts.getFirst().materialFlags());
    }
}