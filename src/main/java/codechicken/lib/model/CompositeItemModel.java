package codechicken.lib.model;

import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.renderer.item.CompositeModel;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModels;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.Identifier;
import net.minecraft.client.resources.model.ResolvableModel;
import net.neoforged.neoforge.client.model.CompositeUnbakedGeometry;
import net.neoforged.neoforge.client.model.CompositeUnbakedModel;
import net.neoforged.neoforge.client.model.StandardModelParameters;
import net.neoforged.neoforge.client.model.UnbakedModelLoader;
import org.joml.Matrix4fc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Ordered composite geometry, and independent item models for stack-dependent children. */
public class CompositeItemModel implements UnbakedModelLoader<CompositeUnbakedModel> {
    @Override
    public CompositeUnbakedModel read(JsonObject json, JsonDeserializationContext context) {
        JsonObject childrenJson = json.getAsJsonObject("children");
        if (childrenJson == null || childrenJson.isEmpty()) throw new JsonParseException("Composite model requires children.");
        Map<String, Either<Identifier, UnbakedModel>> children = new LinkedHashMap<>();
        for (var entry : childrenJson.entrySet()) {
            JsonElement value = entry.getValue();
            Either<Identifier, UnbakedModel> child = switch (value) {
                case JsonPrimitive ref -> Either.left(Identifier.parse(ref.getAsString()));
                case JsonObject inline -> Either.right(context.deserialize(inline, UnbakedModel.class));
                default -> throw new JsonParseException("Invalid composite child: " + entry.getKey());
            };
            children.put(entry.getKey(), child);
        }
        // Preserve every geometry child. Item-only ordering is handled by the item model codec below.
        return new CompositeUnbakedModel(StandardModelParameters.parse(json, context),
                new CompositeUnbakedGeometry(ImmutableMap.copyOf(children)));
    }

    public record OrderedItem(Map<String, ItemModel.Unbaked> children, List<String> itemRenderOrder, Map<String,Boolean> visibility) implements ItemModel.Unbaked {
        public static final MapCodec<OrderedItem> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.unboundedMap(Codec.STRING, ItemModels.CODEC).fieldOf("children").forGetter(OrderedItem::children),
                Codec.STRING.listOf().optionalFieldOf("item_render_order").forGetter(model -> java.util.Optional.of(model.itemRenderOrder())),
                Codec.unboundedMap(Codec.STRING,Codec.BOOL).optionalFieldOf("visibility",Map.of()).forGetter(OrderedItem::visibility)
        ).apply(instance, (children,order,visibility) -> new OrderedItem(children,order.orElseGet(() -> List.copyOf(children.keySet())),visibility)));

        public OrderedItem(Map<String,ItemModel.Unbaked> children,List<String> itemRenderOrder){this(children,itemRenderOrder,Map.of());}
        public OrderedItem {
            visibility=Map.copyOf(visibility);
            children = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(children));
            itemRenderOrder = List.copyOf(itemRenderOrder);
            if (children.isEmpty()) throw new IllegalArgumentException("Composite item requires children.");
            for (String name : itemRenderOrder) {
                if (!children.containsKey(name)) throw new IllegalArgumentException("Unknown composite item child: " + name);
            }
        }

        @Override public MapCodec<? extends ItemModel.Unbaked> type() { return CODEC; }

        @Override public void resolveDependencies(ResolvableModel.Resolver resolver) {
            itemRenderOrder.stream().filter(name->visibility.getOrDefault(name,true)).distinct().forEach(name -> children.get(name).resolveDependencies(resolver));
        }

        @Override public ItemModel bake(ItemModel.BakingContext context, Matrix4fc transform) {
            Map<String, ItemModel> baked = new LinkedHashMap<>();
            itemRenderOrder.stream().filter(name->visibility.getOrDefault(name,true)).distinct().forEach(name -> baked.put(name, children.get(name).bake(context, transform)));
            List<ItemModel> ordered = new ArrayList<>();
            for (String name : itemRenderOrder) if(visibility.getOrDefault(name,true))ordered.add(baked.get(name));
            return new CompositeModel(List.copyOf(ordered));
        }
    }
}