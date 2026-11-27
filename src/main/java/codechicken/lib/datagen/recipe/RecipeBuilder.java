package codechicken.lib.datagen.recipe;

import net.minecraft.core.registries.Registries;
import net.minecraft.advancements.Advancement;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.Recipe;
import net.neoforged.neoforge.common.conditions.ICondition;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Created by covers1624 on 27/12/20.
 */
public interface RecipeBuilder {

    Identifier getId();

    BuiltRecipe build();

    default void register(BootstrapContext<Recipe<?>> recipeContext, BootstrapContext<Advancement> advancementContext) {
        BuiltRecipe built = build();
        ICondition[] conditionsArray = built.conditions.toArray(ICondition[]::new);
        recipeContext.register(ResourceKey.create(Registries.RECIPE, getId()), built.recipe, conditionsArray);
        if (built.advancement != null) {
            built.advancement.register(advancementContext, conditionsArray);
        }
    }

    // Compat with vanilla's Recipe Provider.
    default void build(RecipeOutput output) {
        BuiltRecipe built = build();
        output.accept(ResourceKey.create(Registries.RECIPE, getId()), built.recipe, built.advancement, built.conditions.toArray(ICondition[]::new));
    }

    record BuiltRecipe(
            Recipe<?> recipe,
            @Nullable AdvancementHolder advancement,
            List<ICondition> conditions
    ) { }
}
