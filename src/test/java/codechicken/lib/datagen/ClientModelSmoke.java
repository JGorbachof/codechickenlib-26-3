package codechicken.lib.datagen;

import codechicken.lib.model.CompositeItemModel;
import codechicken.lib.model.ClassModelLoader;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.renderer.item.ItemModels;
import net.minecraft.client.renderer.item.EmptyModel;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.data.DataProvider;
import net.minecraft.data.CachedOutput;
import net.minecraft.world.item.Items;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import java.util.*;
import java.nio.file.Path;
import java.util.concurrent.*;

@EventBusSubscriber(modid="codechickenlib")
public final class ClientModelSmoke {
    @SubscribeEvent public static void gather(GatherDataEvent.Client event){
        if(!Boolean.getBoolean("ccl.dev.modelSmoke"))return;
        validateRenderingHooks();
        validateWrappedModel();
        codechicken.lib.render.block.FluidDispatchSmoke.verify();
        codechicken.lib.render.block.BlockDispatchSmoke.verify();
        validateClassAndVisibility();
        codechicken.lib.gui.modular.lib.GuiNativeSmoke.verify();
        codechicken.lib.gui.modular.lib.GuiNativeSmoke.verifyItemTint();
        codechicken.lib.gui.modular.lib.GuiNativeSmoke.verifyEntityDepth();
        codechicken.lib.gui.modular.lib.AtlasNativeSmoke.verify();
        var provider=new ItemModelProvider(event.getGenerator().getPackOutput(),"codechickenlib"){
            @Override protected void registerModels(){
                generated(Items.STICK).customLoader(CompositeLoaderBuilder::ccl)
                    .nested("a", child->child.parent(GENERATED).texture(modLoc("item/a")))
                    .nested("b", child->child.parent(HANDHELD).texture(modLoc("item/b")))
                    .order("b","a").end();
                clazz(Items.IRON_INGOT,EmptyModel.class);
                generated(Items.GOLD_INGOT).override(o->o.model("gold_alt",b->b.parent(GENERATED))
                    .predicate(Identifier.withDefaultNamespace("custom_model_data"),2)
                    .rangeProperty(Identifier.withDefaultNamespace("custom_model_data"),JsonParser.parseString("{\"index\":1}").getAsJsonObject(),3)
                    .predicate(Identifier.withDefaultNamespace("time"),.25F)
                    .selectProperty(Identifier.withDefaultNamespace("charge_type"),new JsonObject(),new JsonPrimitive("arrow")));
            }
        };
        event.addProvider(new DataProvider(){
            @Override public String getName(){return "CCL model smoke";}
            @Override public CompletableFuture<?> run(CachedOutput cache){
                Map<Path,byte[]> first=new ConcurrentHashMap<>(),second=new ConcurrentHashMap<>();
                return provider.run((p,b,h)->first.put(p,b.clone())).thenCompose(v->provider.run((p,b,h)->second.put(p,b.clone()))).thenRun(()->{
                    if(first.size()!=7||!first.keySet().equals(second.keySet()))throw new IllegalStateException("Unexpected model files "+first.keySet());
                    first.forEach((p,b)->{if(!Arrays.equals(b,second.get(p)))throw new IllegalStateException("Nonrepeatable "+p);});
                    int definitions=0;
                    for(var entry:first.entrySet()){
                        if(!entry.getKey().toString().replace('\\','/').contains("/items/"))continue;
                        definitions++;
                        var json=JsonParser.parseString(new String(entry.getValue(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().get("model");
                        var model=ItemModels.CODEC.parse(JsonOps.INSTANCE,json).getOrThrow();
                        if(model instanceof CompositeItemModel.OrderedItem ordered && !ordered.itemRenderOrder().equals(List.of("b","a")))
                            throw new IllegalStateException("Composite order changed");
                    }
                    if(definitions!=3)throw new IllegalStateException("Wrong number of item definitions "+definitions);
                    var explicit=JsonParser.parseString("{\"type\":\"codechickenlib:item_composite\",\"children\":{\"a\":{\"type\":\"minecraft:empty\"}},\"item_render_order\":[]}");
                    var omitted=explicit.deepCopy();omitted.getAsJsonObject().remove("item_render_order");
                    var empty=(CompositeItemModel.OrderedItem)ItemModels.CODEC.parse(JsonOps.INSTANCE,explicit).getOrThrow();
                    var defaulted=(CompositeItemModel.OrderedItem)ItemModels.CODEC.parse(JsonOps.INSTANCE,omitted).getOrThrow();
                    if(!empty.itemRenderOrder().isEmpty()||!defaulted.itemRenderOrder().equals(List.of("a")))
                        throw new IllegalStateException("Explicit/implicit order changed");
                    if(!empty.children().containsKey("a"))throw new IllegalStateException("Missing child");
                    first.forEach((p,b)->{try{cache.writeIfNeeded(p,b,com.google.common.hash.Hashing.sha1().hashBytes(b));}catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}});
                    System.out.println("CCL_MODEL_SMOKE_PASS: seven repeatable files, three native codec definitions, explicit empty order");
                });
            }
        });
    }
    private static void validateRenderingHooks(){
        // Loading without class initialization runs FML/Mixin transformation
        // without constructing a Minecraft window or renderer.
        var targets=Map.of(
            "net.minecraft.client.renderer.extract.LevelExtractor",List.of("ccl$extractBreaking"),
            "net.minecraft.client.renderer.LevelRenderer",List.of("ccl$breaking"),
            "net.minecraft.client.renderer.chunk.SectionCompiler",List.of("ccl$renderBlock","ccl$customFluid"),
            "net.minecraft.client.renderer.block.FluidRenderer",List.of("ccl$renderFluid"),
            "net.minecraft.client.renderer.block.BlockModelResolver",List.of("ccl$renderEntity"),
            "net.minecraft.client.Minecraft",List.of("onRunTick","ccl$closeResources"),
            "net.minecraft.client.gui.screens.inventory.AbstractContainerScreen",List.of("ccl$hideNativeHighlight"),
            "com.mojang.blaze3d.platform.cursor.CursorType",List.of("ccl$createCursor"));
        for(var entry:targets.entrySet()){
            try{
                Class<?> target=Class.forName(entry.getKey(),false,ClientModelSmoke.class.getClassLoader());
                var methods=Arrays.stream(target.getDeclaredMethods()).map(java.lang.reflect.Method::getName).toList();
                for(var hook:entry.getValue())if(methods.stream().noneMatch(name->name.contains(hook)))
                    throw new IllegalStateException("Missing transformed hook "+entry.getKey()+" "+hook);
            }catch(ClassNotFoundException e){throw new IllegalStateException(e);}
        }
        var state=new net.minecraft.client.renderer.state.level.LevelRenderState();
        state.cameraRenderState=new net.minecraft.client.renderer.state.level.CameraRenderState();
        state.cameraRenderState.pos=new net.minecraft.world.phys.Vec3(1,2,3);
        var block=net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
        var custom=new net.minecraft.client.renderer.state.level.BlockBreakingRenderState(new net.minecraft.core.BlockPos(4,6,8),block,3);
        var vanilla=new net.minecraft.client.renderer.state.level.BlockBreakingRenderState(new net.minecraft.core.BlockPos(5,6,8),block,4);
        state.blockBreakingRenderStates.add(custom);
        state.blockBreakingRenderStates.add(vanilla);
        var entries=new LinkedHashMap<net.minecraft.client.renderer.state.level.BlockBreakingRenderState,codechicken.lib.render.block.BreakingFrame.Geometry>();
        entries.put(custom,new codechicken.lib.render.block.BreakingFrame.Geometry(List.of(),true,new net.minecraft.world.phys.Vec3(.25,0,0)));
        var frame=new codechicken.lib.render.block.BreakingFrame(entries);
        entries.clear();
        var count=new java.util.concurrent.atomic.AtomicInteger();
        var collector=(net.minecraft.client.renderer.SubmitNodeCollector)java.lang.reflect.Proxy.newProxyInstance(
            ClientModelSmoke.class.getClassLoader(),new Class[]{net.minecraft.client.renderer.SubmitNodeCollector.class},(proxy,method,args)->{
                if(!method.getName().equals("submitBreakingBlockModel"))throw new IllegalStateException("Unexpected submit "+method);
                var pose=(com.mojang.blaze3d.vertex.PoseStack)args[0];
                var position=pose.last().pose().transformPosition(new org.joml.Vector3f());
                if(!position.equals(new org.joml.Vector3f(3.25F,4,5),.00001F)||!args[2].equals(3)||!args[3].equals(true))
                    throw new IllegalStateException("Wrong extracted breaking submission");
                count.incrementAndGet();
                return null;
            });
        var pose=new com.mojang.blaze3d.vertex.PoseStack();
        frame.submit(pose,collector,state);
        if(count.get()!=1||!state.blockBreakingRenderStates.equals(List.of(vanilla))||!pose.last().pose().equals(new org.joml.Matrix4f(),.00001F))
            throw new IllegalStateException("Custom breaking did not preserve vanilla state or pose");
        System.out.println("CCL_RENDER_HOOK_SMOKE_PASS: ten transformed hooks and extracted breaking submission");
    }
    private static void validateWrappedModel(){
        var sprite=net.neoforged.neoforge.client.textures.UnitTextureAtlasSprite.INSTANCE;
        var builder=new codechicken.lib.render.buffer.BakedQuadVertexBuilder();
        builder.sprite(sprite);
        for(int i=0;i<4;i++)builder.addVertex(i&1,i>>1,0).setNormal(0,0,1);
        var quad=builder.bake().getFirst();
        var nativeQuads=net.minecraft.client.resources.model.geometry.ItemQuads.split(List.of(quad));
        net.minecraft.client.renderer.item.ItemModel model=(state,stack,resolver,display,level,owner,seed)->{
            if(seed!=73||display!=net.minecraft.world.item.ItemDisplayContext.GUI)throw new IllegalStateException("Wrapper lost extraction context");
            var layer=state.newLayer();layer.setQuads(nativeQuads);layer.setUsesBlockLight(true);
            layer.setFoilType(net.minecraft.client.renderer.item.ItemStackRenderState.FoilType.STANDARD);
            layer.tintLayers().add(0xFF123456);
            layer.setExtents(()->new org.joml.Vector3fc[]{new org.joml.Vector3f(),new org.joml.Vector3f(1,1,0)});
        };
        var wrapper=new codechicken.lib.model.bakedmodels.WrappedItemModel(model){
            @Override public void update(net.minecraft.client.renderer.item.ItemStackRenderState state,net.minecraft.world.item.ItemStack stack,
                    net.minecraft.client.renderer.item.ItemModelResolver resolver,net.minecraft.world.item.ItemDisplayContext display,
                    net.minecraft.client.multiplayer.ClientLevel level,net.minecraft.world.entity.ItemOwner owner,int seed){
                renderWrapped(state,stack,resolver,display,level,owner,seed,q->{
                    var editable=new codechicken.lib.model.Quad(codechicken.lib.model.CachedFormat.BLOCK);
                    editable.put(q);
                    for(var v:editable.vertices)v.vec()[0]+=2;
                    return editable.bake();
                });
            }
        };
        var state=new net.minecraft.client.renderer.item.TrackingItemStackRenderState();
        state.newLayer().setQuads(nativeQuads);
        state.getModelBoundingBox(); // force cache before adding the mapped layer
        wrapper.update(state,net.minecraft.world.item.ItemStack.EMPTY,null,net.minecraft.world.item.ItemDisplayContext.GUI,null,null,73);
        if(!state.isAnimated()||state.getModelBoundingBox().maxX<2.49)throw new IllegalStateException("Mapped bounds stale");
        var count=new java.util.concurrent.atomic.AtomicInteger();
        var collector=(net.minecraft.client.renderer.SubmitNodeCollector)java.lang.reflect.Proxy.newProxyInstance(
                ClientModelSmoke.class.getClassLoader(),new Class[]{net.minecraft.client.renderer.SubmitNodeCollector.class},(proxy,method,args)->{
                    if(!method.getName().equals("submitItem"))throw new IllegalStateException("Unexpected item submit "+method);
                    var submitted=(net.minecraft.client.resources.model.geometry.ItemQuads)args[6];
                    int index=count.getAndIncrement();
                    if(index==0&&!submitted.equals(nativeQuads))throw new IllegalStateException("Previous layer modified");
                    if(index==1){
                        if(submitted.all().getFirst().position0().x()<1.99F||!args[7].equals(net.minecraft.client.renderer.item.ItemStackRenderState.FoilType.STANDARD)
                                ||!Arrays.equals((int[])args[5],new int[]{0xFF123456})||!args[2].equals(123)||!args[3].equals(456))
                            throw new IllegalStateException("Mapped submission lost native metadata");
                    }
                    return null;
                });
        state.submit(new com.mojang.blaze3d.vertex.PoseStack(),collector,123,456,0);
        if(count.get()!=2)throw new IllegalStateException("Wrong layer count");
        System.out.println("CCL_WRAPPED_MODEL_SMOKE_PASS: transformed quad, bounds, tint, foil, previous layer and native submission");
    }
    public static final class FixtureUnbaked implements ItemModel.Unbaked {
        private boolean resolved;
        public FixtureUnbaked(){}
        @Override public com.mojang.serialization.MapCodec<? extends ItemModel.Unbaked> type(){return ClassModelLoader.ItemClass.CODEC;}
        @Override public void resolveDependencies(net.minecraft.client.resources.model.ResolvableModel.Resolver resolver){
            resolved=true;resolver.markDependency(Identifier.parse("test:fixture"));
        }
        @Override public ItemModel bake(ItemModel.BakingContext context,org.joml.Matrix4fc transform){
            if(!resolved||transform.m30()!=2F)throw new IllegalStateException("Lost class model resolve/bake state");
            return new EmptyModel();
        }
    }
    public static final class MatrixOnlyModel implements ItemModel {
        public MatrixOnlyModel(org.joml.Matrix4fc transform){
            if(transform.m30()!=2F)throw new IllegalArgumentException("Missing transform");
        }
        public void update(net.minecraft.client.renderer.item.ItemStackRenderState state,net.minecraft.world.item.ItemStack stack,
            net.minecraft.client.renderer.item.ItemModelResolver resolver,net.minecraft.world.item.ItemDisplayContext display,
            net.minecraft.client.multiplayer.ClientLevel level,net.minecraft.world.entity.ItemOwner owner,int seed){}
    }
    private static void validateClassAndVisibility(){
        var json=new JsonObject();
        json.addProperty("type","codechickenlib:class");json.addProperty("class",FixtureUnbaked.class.getName());
        var nativeModel=ItemModels.CODEC.parse(JsonOps.INSTANCE,json).getOrThrow();
        var composite=new CompositeItemModel.OrderedItem(Map.of("visible",nativeModel,"hidden",new ClassModelLoader.ItemClass("invalid.Hidden")),
            List.of("hidden","visible","visible"),Map.of("hidden",false));
        var encoded=ItemModels.CODEC.encodeStart(JsonOps.INSTANCE,composite).getOrThrow();
        var decoded=(CompositeItemModel.OrderedItem)ItemModels.CODEC.parse(JsonOps.INSTANCE,encoded).getOrThrow();
        if(!decoded.visibility().equals(Map.of("hidden",false)))throw new IllegalStateException("Lost visibility");
        var dependencies=new ArrayList<Identifier>();
        decoded.resolveDependencies(dependencies::add);
        if(!dependencies.equals(List.of(Identifier.parse("test:fixture"))))throw new IllegalStateException("Hidden or repeated dependency "+dependencies);
        decoded.bake(null,new org.joml.Matrix4f().translation(2,0,0));
        new ClassModelLoader.ItemClass(MatrixOnlyModel.class.getName()).bake(null,new org.joml.Matrix4f().translation(2,0,0));
        System.out.println("CCL_CLASS_VISIBILITY_SMOKE_PASS: native codec roundtrip, hidden dependency exclusion, class resolve/bake and matrix constructor");
    }
}