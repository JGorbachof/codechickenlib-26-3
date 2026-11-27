package codechicken.lib.datagen.recipe;

import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.advancements.*;
import net.minecraft.advancements.triggers.Criterion;
import net.minecraft.advancements.triggers.InventoryChangeTrigger;
import net.minecraft.advancements.predicates.ItemPredicate;
import net.minecraft.advancements.triggers.RecipeUnlockedTrigger;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderGetter;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.common.conditions.ICondition;

import java.util.*;
import java.util.function.Supplier;

import static net.covers1624.quack.util.SneakyUtils.unsafeCast;

/**
 * Created by covers1624 on 28/12/20.
 */
public abstract class AbstractRecipeBuilder<R, T extends AbstractRecipeBuilder<R, T>> implements RecipeBuilder {

    protected final Throwable created = new Throwable("Created at");
    protected final Advancement.Builder advancementBuilder = Advancement.Builder.advancement();
    protected final List<ICondition> conditions = new LinkedList<>();
    protected final Identifier id;
    protected final R result;
    private final Set<ItemLike> criteriaItems = new HashSet<>();
    private final Set<TagKey<Item>> criteriaTags = new HashSet<>();
    private int criteriaCounter = 0;
    private final Map<String, Supplier<Criterion<?>>> deferredCriteria = new LinkedHashMap<>();
    protected boolean generateCriteria = false;
    protected boolean enableUnlocking = false;
    protected String group = "";

    protected AbstractRecipeBuilder(Identifier id, R result) {
        this.id = id;
        this.result = result;
    }

    private HolderLookup.Provider registries;
    private BootstrapContext<Recipe<?>> bootstrapContext;
    private net.minecraft.data.recipes.RecipeOutput outputContext;

    private HolderGetter<Item> itemLookup() {
        return bootstrapContext != null ? bootstrapContext.lookup(Registries.ITEM) : outputContext != null ? outputContext.lookup(Registries.ITEM) : Objects.requireNonNull(registries).lookupOrThrow(Registries.ITEM);
    }

    @Override
    public final void register(BootstrapContext<Recipe<?>> recipeContext, BootstrapContext<Advancement> advancementContext) {
        bootstrapContext = Objects.requireNonNull(recipeContext);
        try {
            validate();
            ResourceKey<Recipe<?>> recipeKey = ResourceKey.create(Registries.RECIPE, id);
            ICondition[] conditionsArray = conditions.toArray(ICondition[]::new);
            var holder = recipeContext.register(recipeKey, _build(), conditionsArray);
            AdvancementHolder advancement = buildAdvancement(holder);
            if (advancement != null) {
                advancement.register(advancementContext, conditionsArray);
            }
        } finally {
            bootstrapContext = null;
        }
    }

    public T withRegistries(HolderLookup.Provider registries) {
        this.registries = Objects.requireNonNull(registries);
        return getThis();
    }

    protected Supplier<Ingredient> deferTagIngredient(TagKey<Item> tag) {
        return () -> resolveTagIngredient(tag);
    }

    protected Ingredient resolveTagIngredient(TagKey<Item> tag) {
        if (registries == null && bootstrapContext == null && outputContext == null) {
            throw new IllegalStateException("Registry lookup required for recipe tag " + tag);
        }
        return Ingredient.of(itemLookup().getOrThrow(tag));
    }

    protected T getThis() {
        return unsafeCast(this);
    }

    public T withCondition(ICondition cond) {
        conditions.add(cond);
        return getThis();
    }

    public T enableUnlocking() {
        enableUnlocking = true;
        return getThis();
    }

    public T autoCriteria() {
        enableUnlocking();
        generateCriteria = true;
        return getThis();
    }

    public T setGroup(String group) {
        this.group = group;
        return getThis();
    }

    public T addCriterion(String name, Criterion<?> criterion) {
        if (!enableUnlocking) {
            throw new IllegalStateException("Recipe unlocking must be enabled with 'enableUnlocking'");
        }
        if (deferredCriteria.containsKey(name)) {
            throw new IllegalArgumentException("Duplicate criterion " + name);
        }
        advancementBuilder.addCriterion(name, criterion);
        return getThis();
    }

    @Override
    public final Identifier getId() {
        return id;
    }

    @Override
    public final BuiltRecipe build() {
        validate();
        var recipe = _build();
        net.minecraft.core.Holder<Recipe<?>> holder = null;
        if (enableUnlocking) {
            if (registries == null) throw new IllegalStateException("Use register() or build(RecipeOutput) to generate an unlockable recipe " + id);
            holder = registries.lookupOrThrow(Registries.RECIPE).getOrThrow(ResourceKey.create(Registries.RECIPE,id));
        }
        return new BuiltRecipe(recipe,buildAdvancement(holder),List.copyOf(conditions));
    }

    @Override
    public final void build(net.minecraft.data.recipes.RecipeOutput output) {
        // Native RecipeOutput exposes a bootstrap lookup capable of forward references.
        var previous=outputContext;
        outputContext=Objects.requireNonNull(output);
        try {
            validate();
            var key=ResourceKey.create(Registries.RECIPE,id);
            var recipe=_build();
            var holder=enableUnlocking?output.lookup(Registries.RECIPE).getOrThrow(key):null;
            output.accept(key,recipe,buildAdvancement(holder),conditions.toArray(ICondition[]::new));
        } finally { outputContext=previous; }
    }

    static Identifier advancementId(Identifier recipeId) { return recipeId.withPrefix("recipes/"); }

    private @org.jetbrains.annotations.Nullable AdvancementHolder buildAdvancement(
            @org.jetbrains.annotations.Nullable net.minecraft.core.Holder<Recipe<?>> holder) {
        var advancementId=advancementId(id);
        // Never mutate the caller's builder while generating automatic criteria: repeat
        // builds must not append the same criterion twice.
        var base=advancementBuilder.build(advancementId).value();
        if (!enableUnlocking) return base.criteria().isEmpty()?null:new AdvancementHolder(advancementId,base);
        var criteria=new LinkedHashMap<>(base.criteria());
        deferredCriteria.forEach((name,criterion)->{
            if(criteria.putIfAbsent(name,criterion.get())!=null)throw new IllegalArgumentException("Duplicate criterion "+name);
        });
        if(criteria.putIfAbsent("has_the_recipe",RecipeUnlockedTrigger.unlocked(Objects.requireNonNull(holder)))!=null)
            throw new IllegalArgumentException("Reserved criterion has_the_recipe");
        var key=ResourceKey.create(Registries.RECIPE,id);
        return new AdvancementHolder(advancementId,new Advancement(
                Optional.of(Identifier.withDefaultNamespace("recipes/root")),base.display(),
                AdvancementRewards.Builder.recipe(key).build(),Map.copyOf(criteria),
                AdvancementRequirements.anyOf(criteria.keySet()),base.sendsTelemetryEvent()));
    }

    protected void validate() {
    }

    protected abstract Recipe<?> _build();

    protected void addAutoCriteria(ItemLike item) {
        if (generateCriteria && criteriaItems.add(item)) {
            addCriterion("has_ingredient_" + criteriaCounter++, hasItem(item));
        }
    }

    protected void addAutoCriteria(TagKey<Item> tag) {
        if (generateCriteria && criteriaTags.add(tag)) {
            deferredCriteria.put("has_ingredient_" + criteriaCounter++, () -> hasItem(tag));
        }
    }

    protected Criterion<?> hasItem(ItemLike itemIn) {
        return InventoryChangeTrigger.TriggerInstance.hasItems(itemIn);
    }

    protected Criterion<?> hasItem(TagKey<Item> tagIn) {
        return this.hasItem(ItemPredicate.Builder.item().of(itemLookup(), tagIn).build());
    }

    protected Criterion<?> hasItem(ItemPredicate... predicates) {
        return CriteriaTriggers.INVENTORY_CHANGED.createCriterion(
                new InventoryChangeTrigger.TriggerInstance(
                        Optional.empty(),
                        InventoryChangeTrigger.TriggerInstance.Slots.ANY,
                        List.of(predicates)
                )
        );
    }
}
