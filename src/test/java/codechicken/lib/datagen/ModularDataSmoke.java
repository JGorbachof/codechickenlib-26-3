package codechicken.lib.datagen;

import codechicken.lib.block.*;
import codechicken.lib.block.component.PropertyComponent;
import codechicken.lib.block.component.data.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.nbt.*;
import net.minecraft.tags.TagKey;
import net.minecraft.data.*;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.item.Items;
import net.minecraft.util.ProblemReporter;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.*;
import java.util.concurrent.*;
import java.nio.file.Path;

@EventBusSubscriber(modid="codechickenlib")
public final class ModularDataSmoke {
    private static final Identifier ID=Identifier.parse("codechickenlib:smoke_modular");
    private static final TagKey<Block> TAG=TagKey.create(Registries.BLOCK,Identifier.parse("codechickenlib:smoke/modular"));
    private static final BooleanProperty ACTIVE=BooleanProperty.create("active");
    private static TestBlock block;
    private static BlockEntityType<TestTile> type;

    @SubscribeEvent public static void register(RegisterEvent event){
        if(!Boolean.getBoolean("ccl.dev.recipeSmoke"))return;
        event.register(Registries.BLOCK,ID,()->block=new TestBlock());
        event.register(Registries.BLOCK_ENTITY_TYPE,ID,()->type=new BlockEntityType<>(TestTile::new,block));
    }
    static final class TestBlock extends ModularTileBlock<TestTile>{
        final PayloadComponent component;
        TestBlock(){
            super(Properties.of().setId(ResourceKey.create(Registries.BLOCK,ID)),()->type);
            addComponent(new PropertyComponent<Boolean>(ACTIVE,true){});
            addComponent(new TagComponent(TAG));
            addComponent(new LootTableComponent(c->c.singleItemPool(Items.STICK)));
            component=addComponent("payload",new PayloadComponent());
        }
    }
    static final class PayloadComponent extends ModularTileBlock.TileComponent<Data>{
        protected Data createData(ModularBlockEntity tile){return new Data(tile,this);}
    }
    static final class Data extends ModularBlockEntity.DataComponent{
        int value;
        HolderLookup.Provider saved,loaded;
        Data(ModularBlockEntity tile,PayloadComponent component){super(tile,component);}
        protected void save(CompoundTag tag,HolderLookup.Provider registries){
            saved=registries;tag.putInt("value",value);
            if(registries.lookupOrThrow(Registries.ITEM).getOrThrow(Items.STICK.builtInRegistryHolder().key()).value()!=Items.STICK)
                throw new IllegalStateException("Wrong item registry");
        }
        protected void load(CompoundTag tag,HolderLookup.Provider registries){loaded=registries;value=tag.getIntOr("value",-1);}
    }
    static final class TestTile extends ModularBlockEntity{
        TestTile(BlockPos pos,BlockState state){super(type,pos,state);}
        void save(ValueOutput output){saveAdditional(output);}
    }

    @SubscribeEvent public static void gather(GatherDataEvent.Server event){
        if(!Boolean.getBoolean("ccl.dev.recipeSmoke"))return;
        var pack=event.getGenerator().getPackOutput();
        var lookup=event.getReloadableLookupProvider();
        var tagProvider=new BlockTagsProvider(pack,event.getWorldLookupProvider(),"codechickenlib"){
            @Override protected void addTags(HolderLookup.Provider p){DataGenComponent.addToProvider(block,this);}
        };
        var lootProvider=new LootTableProvider(Set.of(),List.of(
            new LootTableProvider.SubProviderEntry(context->new NoValidationBLockLootSubProvider(context){
                @Override protected void generate(){add(Blocks.STONE,b->LootTable.lootTable().withPool(net.minecraft.world.level.storage.loot.LootPool.lootPool().setRolls(Holder.direct(new net.minecraft.world.level.storage.loot.providers.number.ints.ConstantValue(1))).add(net.minecraft.world.level.storage.loot.entries.LootItem.lootTableItem(Items.STONE))));}
            },LootContextParamSets.BLOCK)));
        DataGenComponent.addLootTables(block,lootProvider);
        DataGenComponent.addLootTables(block,lootProvider);
        var second=new LootTableProvider(Set.of(),List.of());
        DataGenComponent.addLootTables(block,second);
        event.addProvider(new DataProvider(){
            @Override public String getName(){return "CCL modular block data and persistence smoke";}
            @Override public CompletableFuture<?> run(CachedOutput cache){
                return lookup.thenCompose(registries->{
                    verifySave(registries);
                    verifyTransfer();
                    var first=generateLoot(registries,lootProvider);
                    var repeat=generateLoot(registries,lootProvider);
                    var independent=generateLoot(registries,second);
                    if(!first.equals(repeat)||first.size()!=2||independent.size()!=1)throw new IllegalStateException("Unexpected loot generation "+first);
                    var key=block.getLootTable().orElseThrow().identifier();
                    var json=first.get(key).getAsJsonObject();
                    if(!json.equals(independent.get(key))||json.getAsJsonArray("pools").size()!=1||!json.toString().contains("minecraft:stick"))
                        throw new IllegalStateException("Duplicate or wrong loot pool "+json);
                    var firstTags=new ConcurrentHashMap<Path,byte[]>();
                    var secondTags=new ConcurrentHashMap<Path,byte[]>();
                    return tagProvider.run((p,b,h)->firstTags.put(p,b.clone())).thenCompose(v->tagProvider.run((p,b,h)->secondTags.put(p,b.clone()))).thenRun(()->{
                        if(firstTags.size()!=1||!firstTags.keySet().equals(secondTags.keySet()))throw new IllegalStateException("Unexpected tags");
                        firstTags.forEach((p,b)->{
                            if(!Arrays.equals(b,secondTags.get(p)))throw new IllegalStateException("Nonrepeatable tag");
                            var tag=com.google.gson.JsonParser.parseString(new String(b,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                            if(tag.getAsJsonArray("values").size()!=1||!tag.toString().contains(ID.toString()))throw new IllegalStateException("Invalid tag "+tag);
                            try{cache.writeIfNeeded(p,b,com.google.common.hash.Hashing.sha1().hashBytes(b));}catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
                        });
                        System.out.println("CCL_MODULAR_SMOKE_PASS: repeatable loot and tags, property default, detached component NBT save/load");
                    });
                });
            }
        });
    }
    @SubscribeEvent public static void textureSmoke(GatherDataEvent.Server event){
        if(!Boolean.getBoolean("ccl.dev.recipeSmoke"))return;
        var generator=event.getGenerator();
        var input=new java.awt.image.BufferedImage(5,4,java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<4;y++)for(int x=0;x<5;x++)input.setRGB(x,y,0x80000000|y<<8|x);
        var bytes=new java.io.ByteArrayOutputStream();
        try{javax.imageio.ImageIO.write(input,"png",bytes);}catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
        var pack=(net.minecraft.server.packs.PackResources)java.lang.reflect.Proxy.newProxyInstance(ModularDataSmoke.class.getClassLoader(),new Class[]{net.minecraft.server.packs.PackResources.class},(p,m,a)->{if(m.getName().equals("packId"))return "smoke";throw new UnsupportedOperationException(m.getName());});
        net.minecraft.server.packs.resources.ResourceProvider resources=id->Optional.of(new net.minecraft.server.packs.resources.Resource(pack,()->new java.io.ByteArrayInputStream(bytes.toByteArray())));
        var provider=new DynamicTextureProvider(generator,resources,"codechickenlib"){
            @Override public void addTextures(){
                addDynamicTexture("textures/gui/test","textures/gui/smoke_dynamic",9,7,1,2,1,1);
                addDynamicTexture("textures/gui/test","textures/gui/smoke_dynamic",9,7,1,2,1,1);
            }
        };
        provider.addDynamicTexture("textures/gui/test","textures/gui/smoke_static",3,3,1);
        event.addProvider(new DataProvider(){
            @Override public String getName(){return "CCL dynamic texture smoke";}
            @Override public CompletableFuture<?> run(CachedOutput cache){
                var first=new ConcurrentHashMap<Path,byte[]>();
                var second=new ConcurrentHashMap<Path,byte[]>();
                var writes=new java.util.concurrent.atomic.AtomicInteger();
                return provider.run((p,b,h)->{writes.incrementAndGet();first.put(p,b.clone());}).thenCompose(v->provider.run((p,b,h)->{writes.incrementAndGet();second.put(p,b.clone());})).thenRun(()->{
                    if(writes.get()!=4||first.size()!=2||!first.keySet().equals(second.keySet()))throw new IllegalStateException("Duplicate or missing dynamic texture output");
                    first.forEach((p,b)->{
                        if(!Arrays.equals(b,second.get(p)))throw new IllegalStateException("Nonrepeatable dynamic texture "+p);
                        try{
                            var image=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(b));
                            if(image.getRGB(image.getWidth()-1,image.getHeight()-1)!=input.getRGB(4,3))throw new IllegalStateException("Lost corner pixel");
                            cache.writeIfNeeded(p,b,com.google.common.hash.Hashing.sha1().hashBytes(b));
                        }catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
                    });
                    System.out.println("CCL_TEXTURE_SMOKE_PASS: pre-added and generated output, duplicate path, byte-identical repeat and border pixels");
                });
            }
        });
    }

    private static Map<Identifier,com.google.gson.JsonElement> generateLoot(HolderLookup.Provider registries,LootTableProvider provider){
        var context=HolderLookup.Provider.create(registries.listRegistries().filter(r->!r.key().equals(Registries.LOOT_TABLE)));
        var fallback=HolderLookup.Provider.create(registries.listRegistries().filter(r->r.key().equals(Registries.LOOT_TABLE)));
        var cloner=new Cloner.Factory();cloner.addCodec(Registries.LOOT_TABLE,LootTable.DIRECT_CODEC);
        var output=new RegistrySetBuilder().add(Registries.LOOT_TABLE,provider).buildPatch(context,fallback,cloner).patchesWithConditions();
        var result=new TreeMap<Identifier,com.google.gson.JsonElement>();
        output.lookupOrThrow(Registries.LOOT_TABLE).listElements().forEach(h->result.put(h.key().identifier(),LootTable.DIRECT_CODEC.encodeStart(output.createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE),h.value()).getOrThrow()));
        return result;
    }
    private static void verifySave(HolderLookup.Provider registries){
        if(!block.defaultBlockState().getValue(ACTIVE))throw new IllegalStateException("Lazy property default lost");
        var tile=(TestTile)block.newBlockEntity(BlockPos.ZERO,block.defaultBlockState());
        var data=tile.getData(block.component);data.value=42;
        var output=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,registries);
        tile.save(output);
        var tag=output.buildResult();
        if(data.saved!=registries||tag.getCompoundOrEmpty("payload").getIntOr("value",-1)!=42)throw new IllegalStateException("Detached save context or layout changed "+tag);
        var restored=(TestTile)block.newBlockEntity(BlockPos.ZERO,block.defaultBlockState());
        restored.loadAdditional(TagValueInput.create(ProblemReporter.DISCARDING,registries,tag));
        var read=restored.getData(block.component);
        if(read.loaded!=registries||read.value!=42)throw new IllegalStateException("Detached load context or data changed");
    }
    private static void verifyTransfer(){
        // Only bind a test-only direct holder, never mutate vanilla registries.
        var holder=Holder.direct(Items.STICK,net.minecraft.core.component.DataComponentMap.builder().set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE,64).build());
        var container=new net.minecraft.world.SimpleContainer(4);
        var sample=new net.minecraft.world.item.ItemStack(holder,25);
        sample.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("CCL fixture"));
        container.setItem(0,sample);
        container.setItem(1,sample.copyWithCount(10));
        var changes=new ArrayList<String>();
        var source=new codechicken.lib.inventory.container.modular.ModularSlot(container,0).onSet((old,next)->changes.add("source:"+old.getCount()+":"+next.getCount()));
        var alias=new codechicken.lib.inventory.container.modular.ModularSlot(container,0);
        var target=new codechicken.lib.inventory.container.modular.ModularSlot(container,1).setStackLimit(s->16).onSet((old,next)->changes.add("target:"+old.getCount()+":"+next.getCount()));
        var disabled=new codechicken.lib.inventory.container.modular.ModularSlot(container,2).setEnabled(false);
        var empty=new codechicken.lib.inventory.container.modular.ModularSlot(container,3).setStackLimit(s->8);
        var remaining=source.getItem().copy();
        if(!codechicken.lib.inventory.container.SlotTransfer.move(remaining,List.of(alias,target,disabled,empty),false,false,source))
            throw new IllegalStateException("No transfer");
        source.setByPlayer(remaining);
        if(container.getItem(0).getCount()!=11||container.getItem(1).getCount()!=16||!container.getItem(2).isEmpty()||container.getItem(3).getCount()!=8)
            throw new IllegalStateException("Item counts changed incorrectly: "+container);
        if(!changes.equals(List.of("target:10:16","source:25:11")))throw new IllegalStateException("Wrong slot callback changes "+changes);
        System.out.println("CCL_TRANSFER_SMOKE_PASS: copy slots, alias exclusion, cap, disabled target, conserved item count");
    }
}