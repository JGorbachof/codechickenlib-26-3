package codechicken.lib.block.component.data;

import codechicken.lib.block.ModularBlock;
import codechicken.lib.datagen.NoValidationBLockLootSubProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataProvider;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.providers.number.ints.ConstantValue;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.function.Consumer;

/**
 * An {@link ModularBlock.Component} for loot table data generation.
 * <p>
 * Created by covers1624 on 4/9/22.
 */
@ApiStatus.Experimental
public class LootTableComponent extends DataGenComponent {

    private LootTable.Builder table = new LootTable.Builder();
    private final Consumer<LootTableComponent> configure;
    private boolean configured;
    private final java.util.Set<LootTableProvider> registered=java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

    public LootTableComponent(Consumer<LootTableComponent> configure) {
        this.configure = configure;
    }

    @Override
    protected void addToProvider(DataProvider p) {
        if (p instanceof LootTableProvider provider) registerLoot(provider);
    }

    public synchronized void registerLoot(LootTableProvider provider) {
            if(registered.contains(provider))return;
            if(!configured){configure.accept(this);configured=true;}
            if (!(provider.subProviders instanceof ArrayList)) {
                provider.subProviders = new ArrayList<>(provider.subProviders);
            }
            provider.subProviders.add(
                    new LootTableProvider.SubProviderEntry(
                            registries -> new NoValidationBLockLootSubProvider(registries) {
                                @Override
                                protected void generate() {
                                    add(getBlock(), table);
                                }
                            },
                            LootContextParamSets.BLOCK
                    )
            );

            registered.add(provider);
    }

    public void setTable(LootTable.Builder table) {
        this.table = table;
    }

    public void addPool(LootPool.Builder builder) {
        table.withPool(builder);
    }

    public void singleItemPool(ItemLike item) {
        addPool(LootPool.lootPool()
                .setRolls(net.minecraft.core.Holder.direct(new ConstantValue(1)))
                .add(LootItem.lootTableItem(item))
        );
    }
}
