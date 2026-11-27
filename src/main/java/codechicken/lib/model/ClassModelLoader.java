package codechicken.lib.model;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.client.resources.model.geometry.UnbakedGeometry;
import net.neoforged.neoforge.client.model.AbstractUnbakedModel;
import net.neoforged.neoforge.client.model.StandardModelParameters;
import net.neoforged.neoforge.client.model.UnbakedModelLoader;

/** Class-backed geometry loader for the split block/item model system. */
public class ClassModelLoader implements UnbakedModelLoader<UnbakedModel> {
    @Override
    public UnbakedModel read(JsonObject json, JsonDeserializationContext context) throws JsonParseException {
        if (!json.has("class")) throw new JsonParseException("Class model requires class.");
        String name = json.get("class").getAsString();
        try {
            Class<?> type = Class.forName(name, false, ClassModelLoader.class.getClassLoader());
            if (UnbakedModel.class.isAssignableFrom(type)) {
                return type.asSubclass(UnbakedModel.class).getConstructor().newInstance();
            }
            var geometry = type.asSubclass(UnbakedGeometry.class).getConstructor().newInstance();
            var parameters = StandardModelParameters.parse(json, context);
            return new AbstractUnbakedModel(parameters) {
                @Override public UnbakedGeometry geometry() { return geometry; }
            };
        } catch (ReflectiveOperationException | ClassCastException ex) {
            throw new JsonParseException("Expected a public no-argument UnbakedModel or UnbakedGeometry: " + name, ex);
        }
    }

    /** Class-backed item definition: {"type":"codechickenlib:class","class":"..."} */
    public static final class ItemClass implements net.minecraft.client.renderer.item.ItemModel.Unbaked {
        public static final com.mojang.serialization.MapCodec<ItemClass> CODEC =
                com.mojang.serialization.Codec.STRING.fieldOf("class").xmap(ItemClass::new, ItemClass::className);
        @Override public com.mojang.serialization.MapCodec<? extends net.minecraft.client.renderer.item.ItemModel.Unbaked> type() { return CODEC; }
        private final String className;
        private net.minecraft.client.renderer.item.ItemModel.Unbaked delegate;
        private boolean inspected;
        public ItemClass(String className){this.className=java.util.Objects.requireNonNull(className);}
        public String className(){return className;}
        private synchronized net.minecraft.client.renderer.item.ItemModel.Unbaked unbaked(){
            if(inspected)return delegate;
            try{
                var type=Class.forName(className,false,ClassModelLoader.class.getClassLoader());
                delegate=net.minecraft.client.renderer.item.ItemModel.Unbaked.class.isAssignableFrom(type)?type.asSubclass(net.minecraft.client.renderer.item.ItemModel.Unbaked.class).getConstructor().newInstance():null;
                inspected=true;return delegate;
            }catch(ReflectiveOperationException ex){throw new IllegalArgumentException("Cannot construct unbaked class model "+className,ex);}
        }
        @Override public void resolveDependencies(net.minecraft.client.resources.model.ResolvableModel.Resolver resolver) {var model=unbaked();if(model!=null)model.resolveDependencies(resolver);}
        @Override public net.minecraft.client.renderer.item.ItemModel bake(net.minecraft.client.renderer.item.ItemModel.BakingContext context, org.joml.Matrix4fc transform) {
            try {
                var type = Class.forName(className, false, ClassModelLoader.class.getClassLoader());
                var unbaked=unbaked();if(unbaked!=null)return unbaked.bake(context,transform);
                var modelType=type.asSubclass(net.minecraft.client.renderer.item.ItemModel.class);
                try {
                    return modelType.getConstructor(org.joml.Matrix4fc.class).newInstance(new org.joml.Matrix4f(transform));
                } catch(NoSuchMethodException absent){
                    if(!transform.equals(new org.joml.Matrix4f(),0.000001F))
                        throw new IllegalArgumentException("Class model "+className+" requires a Matrix4fc constructor for nonidentity bake transforms.");
                    return modelType.getConstructor().newInstance();
                }
            } catch (ReflectiveOperationException | ClassCastException ex) {
                throw new IllegalArgumentException("Expected public no-argument ItemModel: " + className, ex);
            }
        }
    }
}