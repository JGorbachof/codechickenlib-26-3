package codechicken.lib.datagen.recipe;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RecipeBootstrapTests {
    @Test void advancementIdentifierPreservesNamespaceAndNestedPath() {
        var id=Identifier.fromNamespaceAndPath("example","machines/gear");
        assertEquals(Identifier.fromNamespaceAndPath("example","recipes/machines/gear"),
                AbstractRecipeBuilder.advancementId(id));
    }
    @Test void onlyRecipeAndAdvancementRegistriesArePatched() {
        assertTrue(RecipeProvider.isOutputRegistry(Registries.RECIPE));
        assertTrue(RecipeProvider.isOutputRegistry(Registries.ADVANCEMENT));
        assertFalse(RecipeProvider.isOutputRegistry(Registries.ITEM));
        assertFalse(RecipeProvider.isOutputRegistry(Registries.LOOT_TABLE));
    }
}