package codechicken.lib.datagen;

import com.google.gson.JsonObject;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.client.resources.model.geometry.UnbakedGeometry;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.model.generators.template.CustomLoaderBuilder;
import org.jetbrains.annotations.Nullable;

import static codechicken.lib.CodeChickenLib.MOD_ID;
import static java.util.Objects.requireNonNull;

public class ClassModelLoaderBuilder extends CustomLoaderBuilder {
    private @Nullable Class<?> clazz;

    public ClassModelLoaderBuilder() {
        super(Identifier.fromNamespaceAndPath(MOD_ID, "class"), false);
    }

    public ClassModelLoaderBuilder clazz(Class<?> clazz) {
        if (!UnbakedModel.class.isAssignableFrom(clazz) && !UnbakedGeometry.class.isAssignableFrom(clazz)) {
            throw new IllegalArgumentException("Expected UnbakedModel or UnbakedGeometry.");
        }
        try {
            clazz.getConstructor();
        } catch (NoSuchMethodException ex) {
            throw new IllegalArgumentException("Expected public no-argument constructor.", ex);
        }
        this.clazz = clazz;
        return this;
    }

    @Override
    protected CustomLoaderBuilder copyInternal() {
        ClassModelLoaderBuilder copy = new ClassModelLoaderBuilder();
        copy.clazz = clazz;
        return copy;
    }

    @Override
    public JsonObject toJson(JsonObject json) {
        super.toJson(json);
        json.addProperty("class", requireNonNull(clazz).getName());
        return json;
    }
}