package codechicken.lib.datagen.recipe;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Recipe;

/**
 * Created by covers1624 on 28/12/20.
 */
public abstract class AbstractItemStackRecipeBuilder<T extends AbstractRecipeBuilder<ItemStackTemplate, T>> extends AbstractRecipeBuilder<ItemStackTemplate, T> {

    protected AbstractItemStackRecipeBuilder(Identifier id, ItemStackTemplate result) {
        super(id, result);
    }

    @Override
    public abstract Recipe<?> _build();
}
