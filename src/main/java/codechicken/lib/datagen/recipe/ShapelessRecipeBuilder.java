package codechicken.lib.datagen.recipe;

import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.ItemLike;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Supplier;

/**
 * Created by covers1624 on 28/12/20.
 */
public class ShapelessRecipeBuilder extends AbstractItemStackRecipeBuilder<ShapelessRecipeBuilder> {

    private static final Logger logger = LogManager.getLogger();

    private final Factory factory;
    private final NonNullList<Supplier<Ingredient>> ingredients = NonNullList.create();

    private CraftingBookCategory category = CraftingBookCategory.MISC;

    protected ShapelessRecipeBuilder(Identifier id, ItemStackTemplate result, Factory factory) {
        super(id, result);
        this.factory = factory;
    }

    public static ShapelessRecipeBuilder builder(ItemLike result) {
        return builder(result, 1);
    }

    public static ShapelessRecipeBuilder builder(ItemLike result, int count) {
        return builder(new ItemStackTemplate(result.asItem(), count));
    }

    public static ShapelessRecipeBuilder builder(ItemLike result, int count, Identifier id) {
        return builder(new ItemStackTemplate(result.asItem(), count), id);
    }

    public static ShapelessRecipeBuilder builder(Supplier<? extends ItemLike> result) {
        return builder(result.get(), 1);
    }

    public static ShapelessRecipeBuilder builder(Supplier<? extends ItemLike> result, int count) {
        return builder(new ItemStackTemplate(result.get().asItem(), count));
    }

    public static ShapelessRecipeBuilder builder(Supplier<? extends ItemLike> result, int count, Identifier id) {
        return builder(new ItemStackTemplate(result.get().asItem(), count), id);
    }

    public static ShapelessRecipeBuilder builder(ItemStack result) {
        return builder(ItemStackTemplate.fromNonEmptyStack(result));
    }

    public static ShapelessRecipeBuilder builder(ItemStackTemplate result) {
        return builder(result, result.typeHolder().unwrapKey().orElseThrow().identifier());
    }

    public static ShapelessRecipeBuilder builder(ItemStack result, Identifier id) {
        return builder(ItemStackTemplate.fromNonEmptyStack(result), id);
    }

    public static ShapelessRecipeBuilder builder(ItemStackTemplate result, Identifier id) {
        return new ShapelessRecipeBuilder(id, result, (g, c, out, inputs) -> new ShapelessRecipe(new Recipe.CommonInfo(true), new CraftingRecipe.CraftingBookInfo(c, g), out, inputs));
    }

    // region Custom
    public static ShapelessRecipeBuilder custom(ItemLike result, Factory factory) {
        return custom(result, 1, factory);
    }

    public static ShapelessRecipeBuilder custom(ItemLike result, int count, Factory factory) {
        return custom(new ItemStackTemplate(result.asItem(), count), factory);
    }

    public static ShapelessRecipeBuilder custom(ItemLike result, int count, Identifier id, Factory factory) {
        return custom(new ItemStackTemplate(result.asItem(), count), id, factory);
    }

    public static ShapelessRecipeBuilder custom(ItemStack result, Factory factory) {
        return custom(ItemStackTemplate.fromNonEmptyStack(result), factory);
    }

    public static ShapelessRecipeBuilder custom(ItemStackTemplate result, Factory factory) {
        return custom(result, result.typeHolder().unwrapKey().orElseThrow().identifier(), factory);
    }

    public static ShapelessRecipeBuilder custom(ItemStack result, Identifier id, Factory factory) {
        return custom(ItemStackTemplate.fromNonEmptyStack(result), id, factory);
    }

    public static ShapelessRecipeBuilder custom(ItemStackTemplate result, Identifier id, Factory factory) {
        return new ShapelessRecipeBuilder(id, result, factory);
    }
    // endregion

    public ShapelessRecipeBuilder addIngredient(TagKey<Item> tag) {
        return addIngredient(tag, 1);
    }

    public ShapelessRecipeBuilder addIngredient(TagKey<Item> tag, int quantity) {
        addAutoCriteria(tag);
        Supplier<Ingredient> ingredient = deferTagIngredient(tag);
        for (int i = 0; i < quantity; ++i) {
            ingredients.add(ingredient);
        }
        return this;
    }

    public ShapelessRecipeBuilder addIngredient(Supplier<? extends ItemLike> item) {
        return addIngredient(item.get(), 1);
    }

    public ShapelessRecipeBuilder addIngredient(Supplier<? extends ItemLike> item, int quantity) {
        addAutoCriteria(item.get());
        Ingredient direct = Ingredient.of(item.get());
        Supplier<Ingredient> ingredient = () -> direct;
        for (int i = 0; i < quantity; ++i) {
            ingredients.add(ingredient);
        }
        return this;
    }

    public ShapelessRecipeBuilder addIngredient(ItemLike item) {
        return addIngredient(item, 1);
    }

    public ShapelessRecipeBuilder addIngredient(ItemLike item, int quantity) {
        addAutoCriteria(item);
        Ingredient direct = Ingredient.of(item);
        Supplier<Ingredient> ingredient = () -> direct;
        for (int i = 0; i < quantity; ++i) {
            ingredients.add(ingredient);
        }
        return this;
    }

    public ShapelessRecipeBuilder addIngredient(Ingredient ingredient) {
        return this.addIngredient(ingredient, 1);
    }

    public ShapelessRecipeBuilder addIngredient(Ingredient ingredient, int quantity) {
        if (generateCriteria) {
            logger.warn("Criteria not automatically generated for raw ingredient.", new Throwable("Here, have a stack trace"));
        }
        for (int i = 0; i < quantity; ++i) {
            ingredients.add(directIngredient(ingredient));
        }

        return this;
    }

    private static Supplier<Ingredient> directIngredient(Ingredient ingredient) {
        java.util.Objects.requireNonNull(ingredient);
        return () -> ingredient;
    }

    public ShapelessRecipeBuilder category(CraftingBookCategory category) {
        this.category = category;
        return this;
    }

    @Override
    public Recipe<?> _build() {
        NonNullList<Ingredient> resolved = NonNullList.create();
        for (Supplier<Ingredient> ingredient : ingredients) {
            resolved.add(ingredient.get());
        }
        return factory.build(
                group,
                category,
                result,
                resolved
        );
    }

    public interface Factory {

        Recipe<?> build(String group, CraftingBookCategory category, ItemStackTemplate result, NonNullList<Ingredient> ingredients);
    }
}
