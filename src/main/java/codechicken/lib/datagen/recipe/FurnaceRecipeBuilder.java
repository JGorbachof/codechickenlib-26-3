package codechicken.lib.datagen.recipe;

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
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * Created by covers1624 on 28/12/20.
 */
public class FurnaceRecipeBuilder extends AbstractItemStackRecipeBuilder<FurnaceRecipeBuilder> {

    private static final Logger logger = LogManager.getLogger();

    private final Factory factory;

    private CookingBookCategory category = CookingBookCategory.MISC;
    private @Nullable Supplier<Ingredient> ingredient;
    private float experience = 0.0F;
    private int cookingTime = 200;

    protected FurnaceRecipeBuilder(Identifier id, ItemStackTemplate result, Factory factory) {
        super(id, result);
        this.factory = factory;
    }

    //region Smelting
    public static FurnaceRecipeBuilder smelting(ItemLike result) {
        return smelting(result, 1);
    }

    public static FurnaceRecipeBuilder smelting(ItemLike result, int count) {
        return smelting(new ItemStackTemplate(result.asItem(), count));
    }

    public static FurnaceRecipeBuilder smelting(ItemLike result, int count, Identifier id) {
        return smelting(new ItemStackTemplate(result.asItem(), count), id);
    }

    public static FurnaceRecipeBuilder smelting(Supplier<? extends ItemLike> result) {
        return smelting(result.get(), 1);
    }

    public static FurnaceRecipeBuilder smelting(Supplier<? extends ItemLike> result, int count) {
        return smelting(new ItemStackTemplate(result.get().asItem(), count));
    }

    public static FurnaceRecipeBuilder smelting(Supplier<? extends ItemLike> result, int count, Identifier id) {
        return smelting(new ItemStackTemplate(result.get().asItem(), count), id);
    }

    public static FurnaceRecipeBuilder smelting(ItemStack result) {
        return smelting(ItemStackTemplate.fromNonEmptyStack(result));
    }

    public static FurnaceRecipeBuilder smelting(ItemStackTemplate result) {
        return smelting(result, result.typeHolder().unwrapKey().orElseThrow().identifier());
    }

    public static FurnaceRecipeBuilder smelting(ItemStack result, Identifier id) {
        return smelting(ItemStackTemplate.fromNonEmptyStack(result), id);
    }

    public static FurnaceRecipeBuilder smelting(ItemStackTemplate result, Identifier id) {
        return new FurnaceRecipeBuilder(id, result, (g, c, i, out, xp, time) -> new SmeltingRecipe(new Recipe.CommonInfo(true), new AbstractCookingRecipe.CookingBookInfo(c, g), i, out, xp, time))
                .cookingTime(200);
    }
    //endregion

    //region Blasting
    public static FurnaceRecipeBuilder blasting(ItemLike result) {
        return blasting(result, 1);
    }

    public static FurnaceRecipeBuilder blasting(ItemLike result, int count) {
        return blasting(new ItemStackTemplate(result.asItem(), count));
    }

    public static FurnaceRecipeBuilder blasting(ItemLike result, int count, Identifier id) {
        return blasting(new ItemStackTemplate(result.asItem(), count), id);
    }

    public static FurnaceRecipeBuilder blasting(Supplier<? extends ItemLike> result) {
        return blasting(result.get(), 1);
    }

    public static FurnaceRecipeBuilder blasting(Supplier<? extends ItemLike> result, int count) {
        return blasting(new ItemStackTemplate(result.get().asItem(), count));
    }

    public static FurnaceRecipeBuilder blasting(Supplier<? extends ItemLike> result, int count, Identifier id) {
        return blasting(new ItemStackTemplate(result.get().asItem(), count), id);
    }

    public static FurnaceRecipeBuilder blasting(ItemStack result) {
        return blasting(ItemStackTemplate.fromNonEmptyStack(result));
    }

    public static FurnaceRecipeBuilder blasting(ItemStackTemplate result) {
        return blasting(result, result.typeHolder().unwrapKey().orElseThrow().identifier());
    }

    public static FurnaceRecipeBuilder blasting(ItemStack result, Identifier id) {
        return blasting(ItemStackTemplate.fromNonEmptyStack(result), id);
    }

    public static FurnaceRecipeBuilder blasting(ItemStackTemplate result, Identifier id) {
        return new FurnaceRecipeBuilder(id, result, (g, c, i, out, xp, time) -> new BlastingRecipe(new Recipe.CommonInfo(true), new AbstractCookingRecipe.CookingBookInfo(c, g), i, out, xp, time))
                .cookingTime(100);
    }
    //endregion

    //region Smoking
    public static FurnaceRecipeBuilder smoking(ItemLike result) {
        return smoking(result, 1);
    }

    public static FurnaceRecipeBuilder smoking(ItemLike result, int count) {
        return smoking(new ItemStackTemplate(result.asItem(), count));
    }

    public static FurnaceRecipeBuilder smoking(ItemLike result, int count, Identifier id) {
        return smoking(new ItemStackTemplate(result.asItem(), count), id);
    }

    public static FurnaceRecipeBuilder smoking(Supplier<? extends ItemLike> result) {
        return smoking(result.get(), 1);
    }

    public static FurnaceRecipeBuilder smoking(Supplier<? extends ItemLike> result, int count) {
        return smoking(new ItemStackTemplate(result.get().asItem(), count));
    }

    public static FurnaceRecipeBuilder smoking(Supplier<? extends ItemLike> result, int count, Identifier id) {
        return smoking(new ItemStackTemplate(result.get().asItem(), count), id);
    }

    public static FurnaceRecipeBuilder smoking(ItemStack result) {
        return smoking(ItemStackTemplate.fromNonEmptyStack(result));
    }

    public static FurnaceRecipeBuilder smoking(ItemStackTemplate result) {
        return smoking(result, result.typeHolder().unwrapKey().orElseThrow().identifier());
    }

    public static FurnaceRecipeBuilder smoking(ItemStack result, Identifier id) {
        return smoking(ItemStackTemplate.fromNonEmptyStack(result), id);
    }

    public static FurnaceRecipeBuilder smoking(ItemStackTemplate result, Identifier id) {
        return new FurnaceRecipeBuilder(id, result, (g, c, i, out, xp, time) -> new SmokingRecipe(new Recipe.CommonInfo(true), new AbstractCookingRecipe.CookingBookInfo(c, g), i, out, xp, time))
                .cookingTime(100);
    }
    //endregion

    //region Campfire
    public static FurnaceRecipeBuilder campfire(ItemLike result) {
        return campfire(result, 1);
    }

    public static FurnaceRecipeBuilder campfire(ItemLike result, int count) {
        return campfire(new ItemStackTemplate(result.asItem(), count));
    }

    public static FurnaceRecipeBuilder campfire(ItemLike result, int count, Identifier id) {
        return campfire(new ItemStackTemplate(result.asItem(), count), id);
    }

    public static FurnaceRecipeBuilder campfire(Supplier<? extends ItemLike> result) {
        return campfire(result.get(), 1);
    }

    public static FurnaceRecipeBuilder campfire(Supplier<? extends ItemLike> result, int count) {
        return campfire(new ItemStackTemplate(result.get().asItem(), count));
    }

    public static FurnaceRecipeBuilder campfire(Supplier<? extends ItemLike> result, int count, Identifier id) {
        return campfire(new ItemStackTemplate(result.get().asItem(), count), id);
    }

    public static FurnaceRecipeBuilder campfire(ItemStack result) {
        return campfire(ItemStackTemplate.fromNonEmptyStack(result));
    }

    public static FurnaceRecipeBuilder campfire(ItemStackTemplate result) {
        return campfire(result, result.typeHolder().unwrapKey().orElseThrow().identifier());
    }

    public static FurnaceRecipeBuilder campfire(ItemStack result, Identifier id) {
        return campfire(ItemStackTemplate.fromNonEmptyStack(result), id);
    }

    public static FurnaceRecipeBuilder campfire(ItemStackTemplate result, Identifier id) {
        return new FurnaceRecipeBuilder(id, result, (g, c, i, out, xp, time) -> new CampfireCookingRecipe(new Recipe.CommonInfo(true), new AbstractCookingRecipe.CookingBookInfo(c, g), i, out, xp, time))
                .cookingTime(600);
    }
    //endregion

    //region Custom
    public static FurnaceRecipeBuilder custom(ItemLike result, Factory factory) {
        return custom(result, 1, factory);
    }

    public static FurnaceRecipeBuilder custom(ItemLike result, int count, Factory factory) {
        return custom(new ItemStackTemplate(result.asItem(), count), factory);
    }

    public static FurnaceRecipeBuilder custom(ItemLike result, int count, Identifier id, Factory factory) {
        return custom(new ItemStackTemplate(result.asItem(), count), id, factory);
    }

    public static FurnaceRecipeBuilder custom(Supplier<? extends ItemLike> result, Factory factory) {
        return custom(result.get(), 1, factory);
    }

    public static FurnaceRecipeBuilder custom(Supplier<? extends ItemLike> result, int count, Factory factory) {
        return custom(new ItemStackTemplate(result.get().asItem(), count), factory);
    }

    public static FurnaceRecipeBuilder custom(Supplier<? extends ItemLike> result, int count, Identifier id, Factory factory) {
        return custom(new ItemStackTemplate(result.get().asItem(), count), id, factory);
    }

    public static FurnaceRecipeBuilder custom(ItemStack result, Factory factory) {
        return custom(ItemStackTemplate.fromNonEmptyStack(result), factory);
    }

    public static FurnaceRecipeBuilder custom(ItemStackTemplate result, Factory factory) {
        return custom(result, result.typeHolder().unwrapKey().orElseThrow().identifier(), factory);
    }

    public static FurnaceRecipeBuilder custom(ItemStack result, Identifier id, Factory factory) {
        return custom(ItemStackTemplate.fromNonEmptyStack(result), id, factory);
    }

    public static FurnaceRecipeBuilder custom(ItemStackTemplate result, Identifier id, Factory factory) {
        return new FurnaceRecipeBuilder(id, result, factory);
    }
    //endregion

    public FurnaceRecipeBuilder category(CookingBookCategory category) {
        this.category = category;
        return this;
    }

    public FurnaceRecipeBuilder ingredient(TagKey<Item> tag) {
        addAutoCriteria(tag);
        this.ingredient = deferTagIngredient(tag);
        return this;
    }

    public FurnaceRecipeBuilder ingredient(ItemLike item) {
        addAutoCriteria(item);
        Ingredient direct = Ingredient.of(item);
        this.ingredient = () -> direct;
        return this;
    }

    public FurnaceRecipeBuilder ingredient(Supplier<? extends ItemLike> item) {
        addAutoCriteria(item.get());
        Ingredient direct = Ingredient.of(item.get());
        this.ingredient = () -> direct;
        return this;
    }

    public FurnaceRecipeBuilder ingredient(Ingredient ingredient) {
        if (generateCriteria) {
            logger.warn("Criteria not automatically generated for raw ingredient.", new Throwable("Here, have a stack trace"));
        }
        this.ingredient = ingredient == null ? null : () -> ingredient;
        return this;
    }

    public FurnaceRecipeBuilder experience(float experience) {
        this.experience = experience;
        return this;
    }

    public FurnaceRecipeBuilder cookingTime(int cookingTime) {
        this.cookingTime = cookingTime;
        return this;
    }

    @Override
    public Recipe<?> _build() {
        return factory.build(group, category, requireNonNull(ingredient).get(), result, experience, cookingTime);
    }

    @Override
    protected void validate() {
        super.validate();
        if (ingredient == null) {
            throw new IllegalStateException("No ingredient set.");
        }
    }

    public interface Factory {

        Recipe<?> build(String group, CookingBookCategory category, Ingredient ingredient, ItemStackTemplate result, float experience, int cookingTime);
    }
}
