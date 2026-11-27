package codechicken.lib.datagen;

import com.google.gson.*;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.client.resources.model.geometry.UnbakedGeometry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static codechicken.lib.CodeChickenLib.MOD_ID;

/** Emits separate geometry and stack-dependent item definitions for 26.3. */
public abstract class ItemModelProvider implements DataProvider {
    protected static final Identifier GENERATED = Identifier.withDefaultNamespace("item/generated");
    protected static final Identifier HANDHELD = Identifier.withDefaultNamespace("item/handheld");
    protected final String modid;
    private final PackOutput output;
    private final Map<Identifier, SimpleItemModelBuilder> models = new LinkedHashMap<>();
    private final Map<Identifier, SimpleItemModelBuilder> items = new LinkedHashMap<>();

    public ItemModelProvider(PackOutput output, String modid) {
        this.output = output;
        this.modid = modid;
    }

    protected abstract void registerModels();
    protected Identifier modLoc(String path) { return Identifier.fromNamespaceAndPath(modid, path); }
    protected String name(ItemLike item) { return BuiltInRegistries.ITEM.getKey(item.asItem()).getPath(); }
    protected String name(Block block) {return BuiltInRegistries.BLOCK.getKey(block).getPath();}
    protected Identifier itemTexture(ItemLike item) { return itemTexture(item, ""); }
    protected Identifier itemTexture(Supplier<? extends Item> item) { return itemTexture(item.get()); }
    protected Identifier itemTexture(ItemLike item, String folder) { return modLoc("item/" + prefix(folder) + name(item)); }
    protected Identifier itemTexture(Supplier<? extends Item> item, String folder) { return itemTexture(item.get(), folder); }
    protected Identifier blockTexture(Block block) { return blockTexture(block, ""); }
    protected Identifier blockTexture(Supplier<? extends Block> block) { return blockTexture(block.get()); }
    protected Identifier blockTexture(Block block, String folder) { return modLoc("block/" + prefix(folder) + name(block)); }
    protected Identifier blockTexture(Supplier<? extends Block> block, String folder) { return blockTexture(block.get(), folder); }
    private static String prefix(String folder) { return folder.isEmpty() || folder.endsWith("/") ? folder : folder + "/"; }

    protected SimpleItemModelBuilder getBuilder(ItemLike item) { return getSimple(item); }
    protected SimpleItemModelBuilder getBuilder(Supplier<? extends Item> item) { return getSimple(item.get()); }
    protected SimpleItemModelBuilder getSimple(ItemLike item) {
        var key = BuiltInRegistries.ITEM.getKey(item.asItem());
        return items.computeIfAbsent(key, id -> getSimple(item, name(item)));
    }
    protected SimpleItemModelBuilder getSimple(ItemLike item, String name) {
        Identifier id = modLoc("item/" + name);
        return models.computeIfAbsent(id, key -> new SimpleItemModelBuilder(this, item.asItem(), key));
    }
    protected SimpleItemModelBuilder getSimple(Supplier<? extends Item> item) { return getSimple(item.get()); }
    protected SimpleItemModelBuilder generated(ItemLike item) { return getSimple(item).parent(GENERATED); }
    protected SimpleItemModelBuilder generated(Supplier<? extends Item> item) { return generated(item.get()); }
    protected SimpleItemModelBuilder handheld(ItemLike item) { return getSimple(item).parent(HANDHELD); }
    protected SimpleItemModelBuilder handheld(Supplier<? extends Item> item) { return handheld(item.get()); }
    protected void simpleItemBlock(Block block) { getSimple(block).parent(modLoc("block/" + name(block))).noTexture(); }
    protected SimpleItemModelBuilder clazz(Supplier<? extends Item> item, Class<?> clazz) { return clazz(item.get(), clazz); }
    protected SimpleItemModelBuilder clazz(ItemLike item, Class<?> clazz) {
        return generated(item).noTexture().customLoader(ClassCustomLoaderBuilder::new).clazz(clazz).end();
    }

    @Override public String getName() { return modid + " Item models."; }
    @Override public CompletableFuture<?> run(CachedOutput cache) {
        models.clear();
        items.clear();
        registerModels();
        List<CompletableFuture<?>> futures = new ArrayList<>();
        var geometryPaths = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "models");
        var itemPaths = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "items");
        Map<Identifier, JsonObject> definitions = new LinkedHashMap<>();
        items.forEach((id, builder) -> {
            JsonObject definition = new JsonObject();
            definition.add("model", builder.itemJson(new HashSet<>()));
            definitions.put(id, definition);
        });
        models.forEach((id, builder) -> {
            if (builder.needsGeometry()) futures.add(DataProvider.saveStable(cache, builder.geometryJson(), geometryPaths.json(id)));
        });
        definitions.forEach((id, definition) -> futures.add(DataProvider.saveStable(cache, definition, itemPaths.json(id))));
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    public static class SimpleItemModelBuilder {
        private final ItemModelProvider provider;
        private final Item item;
        private final Identifier id;
        private final Map<String, Identifier> layers = new LinkedHashMap<>();
        private final List<OverrideBuilder> overrides = new ArrayList<>();
        private Identifier parent;
        private boolean noTexture;
        private String folder = "";
        private CustomLoaderBuilder loader;
        private SimpleItemModelBuilder(ItemModelProvider provider, Item item, Identifier id) {
            this.provider = provider; this.item = item; this.id = id;
        }
        public SimpleItemModelBuilder parent(Identifier parent) { this.parent = Objects.requireNonNull(parent); return this; }
        public SimpleItemModelBuilder texture(Identifier texture) { return texture("layer0", texture); }
        public SimpleItemModelBuilder texture(String layer, Identifier texture) {
            if (noTexture || !folder.isEmpty()) throw new IllegalStateException("Texture conflicts with prior settings.");
            layers.put(layer, Objects.requireNonNull(texture)); return this;
        }
        public SimpleItemModelBuilder folder(String folder) {
            if (noTexture || !layers.isEmpty()) throw new IllegalStateException("Folder conflicts with prior settings.");
            this.folder = Objects.requireNonNull(folder); return this;
        }
        public SimpleItemModelBuilder noTexture() {
            if (!layers.isEmpty() || !folder.isEmpty()) throw new IllegalStateException("NoTexture conflicts with prior settings.");
            noTexture = true; return this;
        }
        public SimpleItemModelBuilder override(Consumer<OverrideBuilder> consumer) {
            var override = new OverrideBuilder(this);
            consumer.accept(override);
            if (override.target == null || override.predicates.isEmpty()) throw new IllegalArgumentException("Override requires a model and predicates.");
            overrides.add(override); return this;
        }
        public <L extends CustomLoaderBuilder> L customLoader(Function<SimpleItemModelBuilder, L> factory) {
            if (loader != null) throw new IllegalStateException("Loader already set.");
            L result = factory.apply(this); loader = result; return result;
        }
        public Identifier getModel() { return id; }
        private boolean needsGeometry() { return loader == null || !loader.nativeItem(); }
        private JsonObject geometryJson() {
            JsonObject json = new JsonObject();
            json.addProperty("parent", Objects.requireNonNull(parent, "Missing parent for " + id).toString());
            if (!noTexture) {
                JsonObject textures = new JsonObject();
                if (layers.isEmpty()) textures.addProperty("layer0", provider.itemTexture(item, folder).toString());
                else layers.forEach((layer, texture) -> textures.addProperty(layer, texture.toString()));
                json.add("textures", textures);
            }
            return loader == null ? json : loader.toJson(json);
        }
        private JsonObject itemJson(Set<Identifier> active) {
            if (!active.add(id)) throw new IllegalStateException("Recursive item model: " + id);
            try {
                JsonObject result;
                if (loader != null && loader.nativeItem()) result = loader.itemJson(active);
                else { result = new JsonObject(); result.addProperty("type", "minecraft:model"); result.addProperty("model", id.toString()); }
                for (OverrideBuilder override : overrides) {
                    JsonObject next = override.target.itemJson(active);
                    for (var predicate : override.predicates.entrySet()) {
                        next = predicate.getValue().wrap(next, result);
                    }
                    result = next;
                }
                return result;
            } finally { active.remove(id); }
        }
    }

    private record PredicateSpec(JsonObject property, float threshold, boolean conditional, JsonElement selection) {
        PredicateSpec(JsonObject property,float threshold,boolean conditional){this(property,threshold,conditional,null);}
        JsonObject wrap(JsonObject yes, JsonObject no) {
            JsonObject json = property.deepCopy();
            if(selection!=null){
                json.addProperty("type","minecraft:select");
                var entry=new JsonObject();entry.add("when",selection.deepCopy());entry.add("model",yes);
                var cases=new JsonArray();cases.add(entry);json.add("cases",cases);json.add("fallback",no);return json;
            }
            json.addProperty("type", conditional ? "minecraft:condition" : "minecraft:range_dispatch");
            if (conditional && threshold == -1) return yes;
            if (conditional && threshold == 2) return no;
            if (conditional) {
                json.add("on_true", threshold > 0 ? yes : no);
                json.add("on_false", threshold > 0 ? no : yes);
            } else {
                JsonObject entry = new JsonObject(); entry.addProperty("threshold", threshold); entry.add("model", yes);
                JsonArray entries = new JsonArray(); entries.add(entry);
                json.add("entries", entries); json.add("fallback", no);
            }
            return json;
        }
    }

    public static class OverrideBuilder {
        private final SimpleItemModelBuilder parent;
        private SimpleItemModelBuilder target;
        private final Map<String, PredicateSpec> predicates = new LinkedHashMap<>();
        private OverrideBuilder(SimpleItemModelBuilder parent) { this.parent = parent; }
        public OverrideBuilder model(String name, Consumer<SimpleItemModelBuilder> consumer) {
            target = parent.provider.getSimple(parent.item, name); consumer.accept(target); return this;
        }
        public OverrideBuilder predicate(Identifier key, float value) {
            String path = key.getPath();
            if (!Float.isFinite(value)) throw new IllegalArgumentException("Non-finite threshold.");
            if (!key.getNamespace().equals("minecraft")) throw new IllegalArgumentException("Use explicit rangeProperty or conditionProperty for custom predicate " + key);
            return switch (path) {
                case "damaged", "broken" -> legacyBoolean(key, value);
                case "pulling", "blocking", "throwing", "tooting" -> legacyBoolean(Identifier.withDefaultNamespace("using_item"), value);
                case "cast" -> legacyBoolean(Identifier.withDefaultNamespace("fishing_rod/cast"), value);
                case "damage", "cooldown", "custom_model_data" -> rangeProperty(key, new JsonObject(), value);
                case "time" -> {
                    var parameters=new JsonObject();parameters.addProperty("source","daytime");
                    yield rangeProperty(key,parameters,value);
                }
                case "pull" -> {
                    if(parent.item instanceof net.minecraft.world.item.CrossbowItem)yield rangeProperty(Identifier.withDefaultNamespace("crossbow/pull"),new JsonObject(),value);
                    if(parent.item instanceof net.minecraft.world.item.BowItem){
                        // Legacy bow predicate is elapsed ticks divided by 20.
                        yield rangeProperty(Identifier.withDefaultNamespace("use_duration"),new JsonObject(),value*20);
                    }
                    throw new IllegalArgumentException("Use explicit rangeProperty for non-bow pull predicates.");
                }
                case "charged","firework" -> {
                    if(value<=0){predicates.put(key.toString(),new PredicateSpec(new JsonObject(),-1,true));yield this;}
                    if(value>1){predicates.put(key.toString(),new PredicateSpec(new JsonObject(),2,true));yield this;}
                    var matches=new JsonArray();
                    if(path.equals("charged"))matches.add("arrow");
                    matches.add("rocket");
                    yield selectProperty(Identifier.withDefaultNamespace("charge_type"),new JsonObject(),matches);
                }
                default -> throw new IllegalArgumentException("Legacy predicate requires explicit native property mapping: " + key);
            };
        }
        private OverrideBuilder legacyBoolean(Identifier key, float minimum) {
            JsonObject property = new JsonObject(); property.addProperty("property", key.toString());
            predicates.put(key.toString(), new PredicateSpec(property, minimum <= 0 ? -1 : minimum > 1 ? 2 : 1, true));
            return this;
        }
        public OverrideBuilder rangeProperty(Identifier key, JsonObject parameters, float threshold) {
            if (!Float.isFinite(threshold)) throw new IllegalArgumentException("Non-finite threshold.");
            JsonObject property = parameters.deepCopy(); property.addProperty("property", key.toString());
            predicates.put("range:"+property, new PredicateSpec(property, threshold, false)); return this;
        }
        public OverrideBuilder conditionProperty(Identifier key, JsonObject parameters, boolean expected) {
            JsonObject property = parameters.deepCopy(); property.addProperty("property", key.toString());
            predicates.put("condition:"+property, new PredicateSpec(property, expected ? 1 : 0, true)); return this;
        }
        public OverrideBuilder selectProperty(Identifier key,JsonObject parameters,JsonElement matches){
            if(matches==null||matches.isJsonNull()||matches.isJsonArray()&&matches.getAsJsonArray().isEmpty())
                throw new IllegalArgumentException("Selection requires matching values.");
            var property=parameters.deepCopy();property.addProperty("property",key.toString());
            predicates.put("select:"+property+":"+matches,new PredicateSpec(property,0,false,matches.deepCopy()));
            return this;
        }
    }

    public static class CustomLoaderBuilder {
        protected final Identifier loaderId;
        protected final SimpleItemModelBuilder parent;
        protected final Map<String, Boolean> visibility = new LinkedHashMap<>();
        protected CustomLoaderBuilder(Identifier loaderId, SimpleItemModelBuilder parent) { this.loaderId = loaderId; this.parent = parent; }
        public CustomLoaderBuilder visibility(String part, boolean visible) { visibility.put(part, visible); return this; }
        public SimpleItemModelBuilder end() { return parent; }
        protected boolean nativeItem() { return false; }
        protected JsonObject itemJson(Set<Identifier> active) { throw new UnsupportedOperationException("Not a native item loader."); }
        protected JsonObject toJson(JsonObject json) {
            json.addProperty("loader", loaderId.toString());
            if (!visibility.isEmpty()) { JsonObject parts = new JsonObject(); visibility.forEach(parts::addProperty); json.add("visibility", parts); }
            return json;
        }
    }

    public static class ClassCustomLoaderBuilder extends CustomLoaderBuilder {
        private Class<?> clazz;
        protected ClassCustomLoaderBuilder(SimpleItemModelBuilder parent) { super(Identifier.fromNamespaceAndPath(MOD_ID, "class"), parent); }
        public ClassCustomLoaderBuilder clazz(Class<?> clazz) {
            if (!ItemModel.class.isAssignableFrom(clazz) && !ItemModel.Unbaked.class.isAssignableFrom(clazz) && !UnbakedModel.class.isAssignableFrom(clazz) && !UnbakedGeometry.class.isAssignableFrom(clazz))
                throw new IllegalArgumentException("Expected ItemModel, UnbakedModel or UnbakedGeometry.");
            try { clazz.getConstructor(); } catch (NoSuchMethodException e) {
                if(!ItemModel.class.isAssignableFrom(clazz))throw new IllegalArgumentException("Expected public no-arg constructor.",e);
                try{clazz.getConstructor(org.joml.Matrix4fc.class);}catch(NoSuchMethodException absent){throw new IllegalArgumentException("Expected public no-arg or Matrix4fc constructor.",absent);}
            }
            this.clazz = clazz; return this;
        }
        @Override protected boolean nativeItem() { return ItemModel.class.isAssignableFrom(Objects.requireNonNull(clazz))||ItemModel.Unbaked.class.isAssignableFrom(clazz); }
        @Override protected JsonObject itemJson(Set<Identifier> active) {
            JsonObject json = new JsonObject(); json.addProperty("type", loaderId.toString()); json.addProperty("class", clazz.getName()); return json;
        }
        @Override protected JsonObject toJson(JsonObject json) { super.toJson(json); json.addProperty("class", Objects.requireNonNull(clazz).getName()); return json; }
    }

    public static class CompositeLoaderBuilder extends CustomLoaderBuilder {
        private final Map<String, SimpleItemModelBuilder> children = new LinkedHashMap<>();
        private final List<String> order = new ArrayList<>();
        protected CompositeLoaderBuilder(Identifier loader, SimpleItemModelBuilder parent) { super(loader, parent); }
        public static CompositeLoaderBuilder forge(SimpleItemModelBuilder parent) { return ccl(parent); }
        public static CompositeLoaderBuilder ccl(SimpleItemModelBuilder parent) { return new CompositeLoaderBuilder(Identifier.fromNamespaceAndPath(MOD_ID, "item_composite"), parent); }
        public CompositeLoaderBuilder nested(String name, Consumer<SimpleItemModelBuilder> consumer) {
            if (children.containsKey(name)) throw new IllegalArgumentException("Duplicate child " + name);
            var child = parent.provider.getSimple(parent.item, parent.id.getPath().substring(5) + "/" + name);
            consumer.accept(child); children.put(name, child); order.add(name); return this;
        }
        public CompositeLoaderBuilder order(String... names) {
            for (String name : names) if (!children.containsKey(name)) throw new IllegalArgumentException("Unknown child " + name);
            order.clear(); Collections.addAll(order, names); return this;
        }
        @Override protected boolean nativeItem() { return true; }
        @Override protected JsonObject itemJson(Set<Identifier> active) {
            JsonObject json = new JsonObject(); json.addProperty("type", loaderId.toString());
            JsonObject childJson = new JsonObject(); JsonArray renderOrder = new JsonArray();
            children.forEach((name, child) -> { if (visibility.getOrDefault(name, true)) childJson.add(name, child.itemJson(active)); });
            for (String name : order) if (visibility.getOrDefault(name, true)) renderOrder.add(name);
            if (renderOrder.isEmpty()) { JsonObject empty = new JsonObject(); empty.addProperty("type", "minecraft:empty"); return empty; }
            json.add("children", childJson); json.add("item_render_order", renderOrder); return json;
        }
    }
}