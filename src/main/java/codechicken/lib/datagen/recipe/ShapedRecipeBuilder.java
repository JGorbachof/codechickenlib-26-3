package codechicken.lib.datagen.recipe;

import it.unimi.dsi.fastutil.chars.Char2ObjectMap;
import it.unimi.dsi.fastutil.chars.Char2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.chars.CharOpenHashSet;
import it.unimi.dsi.fastutil.chars.CharSet;
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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Created by covers1624 on 28/12/20.
 */
public class ShapedRecipeBuilder extends AbstractItemStackRecipeBuilder<ShapedRecipeBuilder> {

    private static final Logger logger = LogManager.getLogger();

    private final Factory factory;
    private final List<String> patternLines = new ArrayList<>();
    private final Char2ObjectMap<Supplier<Ingredient>> keys = new Char2ObjectOpenHashMap<>();

    private CraftingBookCategory category = CraftingBookCategory.MISC;
    private boolean showNotification = true;

    protected ShapedRecipeBuilder(Identifier id, ItemStackTemplate result, Factory factory) {
        super(id, result);
        this.factory = factory;
    }

    public static ShapedRecipeBuilder builder(ItemLike result) {
        return builder(result, 1);
    }

    public static ShapedRecipeBuilder builder(ItemLike result, int count) {
        return builder(new ItemStackTemplate(result.asItem(), count));
    }

    public static ShapedRecipeBuilder builder(ItemLike result, int count, Identifier id) {
        return builder(new ItemStackTemplate(result.asItem(), count), id);
    }

    public static ShapedRecipeBuilder builder(Supplier<? extends ItemLike> result) {
        return builder(result.get(), 1);
    }

    public static ShapedRecipeBuilder builder(Supplier<? extends ItemLike> result, int count) {
        return builder(new ItemStackTemplate(result.get().asItem(), count));
    }

    public static ShapedRecipeBuilder builder(Supplier<? extends ItemLike> result, int count, Identifier id) {
        return builder(new ItemStackTemplate(result.get().asItem(), count), id);
    }

    public static ShapedRecipeBuilder builder(ItemStack result) {
        return builder(ItemStackTemplate.fromNonEmptyStack(result));
    }

    public static ShapedRecipeBuilder builder(ItemStackTemplate result) {
        return builder(result, result.typeHolder().unwrapKey().orElseThrow().identifier());
    }

    public static ShapedRecipeBuilder builder(ItemStack result, Identifier id) {
        return builder(ItemStackTemplate.fromNonEmptyStack(result), id);
    }

    public static ShapedRecipeBuilder builder(ItemStackTemplate result, Identifier id) {
        return new ShapedRecipeBuilder(id, result, (g, c, p, out, notify) -> new ShapedRecipe(new Recipe.CommonInfo(notify), new CraftingRecipe.CraftingBookInfo(c, g), p, out));
    }

    // region Custom
    public static ShapedRecipeBuilder custom(ItemLike result, Factory factory) {
        return custom(result, 1, factory);
    }

    public static ShapedRecipeBuilder custom(ItemLike result, int count, Factory factory) {
        return custom(new ItemStackTemplate(result.asItem(), count), factory);
    }

    public static ShapedRecipeBuilder custom(ItemLike result, int count, Identifier id, Factory factory) {
        return custom(new ItemStackTemplate(result.asItem(), count), id, factory);
    }

    public static ShapedRecipeBuilder custom(ItemStack result, Factory factory) {
        return custom(ItemStackTemplate.fromNonEmptyStack(result), factory);
    }

    public static ShapedRecipeBuilder custom(ItemStackTemplate result, Factory factory) {
        return custom(result, result.typeHolder().unwrapKey().orElseThrow().identifier(), factory);
    }

    public static ShapedRecipeBuilder custom(ItemStack result, Identifier id, Factory factory) {
        return custom(ItemStackTemplate.fromNonEmptyStack(result), id, factory);
    }

    public static ShapedRecipeBuilder custom(ItemStackTemplate result, Identifier id, Factory factory) {
        return new ShapedRecipeBuilder(id, result, factory);
    }
    // endregion

    public ShapedRecipeBuilder key(char key, TagKey<Item> item) {
        addAutoCriteria(item);
        return keyInternal(key, deferTagIngredient(item));
    }

    public ShapedRecipeBuilder key(char key, ItemLike item) {
        addAutoCriteria(item);
        return keyInternal(key, directIngredient(Ingredient.of(item)));
    }

    public ShapedRecipeBuilder key(char key, Supplier<? extends ItemLike> item) {
        addAutoCriteria(item.get());
        return keyInternal(key, directIngredient(Ingredient.of(item.get())));
    }

    public ShapedRecipeBuilder key(char key, Ingredient ingredient) {
        if (generateCriteria) {
            logger.warn("Criteria not automatically generated for raw ingredient.", new Throwable("Here, have a stack trace"));
        }
        return keyInternal(key, directIngredient(ingredient));
    }

    private static Supplier<Ingredient> directIngredient(Ingredient ingredient) {
        return () -> ingredient;
    }

    private ShapedRecipeBuilder keyInternal(char key, Supplier<Ingredient> ingredient) {
        if (keys.containsKey(key)) {
            throw new IllegalArgumentException("Symbol '" + key + "' is already defined!");
        }
        if (key == ' ') {
            throw new IllegalArgumentException("Symbol ' ' (whitespace) is reserved and cannot be defined");
        }
        keys.put(key, ingredient);
        return this;
    }

    public ShapedRecipeBuilder patternLine(String patternIn) {
        if (!patternLines.isEmpty() && patternIn.length() != patternLines.get(0).length()) {
            throw new IllegalArgumentException("Pattern must be the same width on every line!");
        }
        this.patternLines.add(patternIn);
        return this;
    }

    public ShapedRecipeBuilder category(CraftingBookCategory category) {
        this.category = category;
        return this;
    }

    public ShapedRecipeBuilder showNotifications(boolean showNotification) {
        this.showNotification = showNotification;
        return this;
    }

    @Override
    public Recipe<?> _build() {
        Char2ObjectMap<Ingredient> resolved = new Char2ObjectOpenHashMap<>();
        keys.char2ObjectEntrySet().forEach(entry -> resolved.put(entry.getCharKey(), entry.getValue().get()));
        return factory.build(
                group,
                category,
                ShapedRecipePattern.of(resolved, patternLines),
                result,
                showNotification
        );
    }

    @Override
    protected void validate() {
        super.validate();
        if (patternLines.isEmpty()) {
            throw new IllegalStateException("No pattern is defined for shaped recipe " + id + "!", created);
        }
        if (patternLines.size() == 1 && patternLines.get(0).length() == 1) {
            throw new IllegalStateException("Shaped recipe " + id + " only takes in a single item - should it be a shapeless recipe instead?", created);
        }

        CharSet chars = new CharOpenHashSet(keys.keySet());
        chars.remove(' ');

        for (String line : this.patternLines) {
            for (char c : line.toCharArray()) {
                if (c == ' ') continue;
                if (!keys.containsKey(c)) {
                    throw new IllegalStateException("Pattern in recipe " + id + " uses undefined symbol '" + c + "'", created);
                }
                chars.remove(c);
            }
        }

        if (!chars.isEmpty()) {
            throw new IllegalStateException("Ingredients are defined but not used in pattern for recipe " + id, created);
        }
    }

    public interface Factory {

        Recipe<?> build(String group, CraftingBookCategory category, ShapedRecipePattern pattern, ItemStackTemplate result, boolean showNotification);
    }
}
