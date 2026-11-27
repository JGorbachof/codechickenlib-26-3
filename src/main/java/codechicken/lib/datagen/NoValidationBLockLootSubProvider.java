package codechicken.lib.datagen;

import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.data.loot.LootTableSubProvider;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootTable;
import java.util.*;

/** Emits only explicitly registered loot tables, rather than validating every block in the registry. */
public abstract class NoValidationBLockLootSubProvider extends BlockLootSubProvider {
    private final Map<ResourceKey<LootTable>,LootTable.Builder> tables;
    protected NoValidationBLockLootSubProvider(LootTableSubProvider.Context output){
        this(Set.of(),output);
    }
    protected NoValidationBLockLootSubProvider(Set<Item> resistant,LootTableSubProvider.Context output){
        this(resistant,FeatureFlagSet.of(),output);
    }
    protected NoValidationBLockLootSubProvider(Set<Item> resistant,FeatureFlagSet flags,LootTableSubProvider.Context output){
        this(resistant,flags,new LinkedHashMap<>(),output);
    }
    protected NoValidationBLockLootSubProvider(Set<Item> resistant,FeatureFlagSet flags,Map<ResourceKey<LootTable>,LootTable.Builder> tables,LootTableSubProvider.Context output){
        super(resistant,flags,output);
        this.tables=tables;
    }
    @Override protected void add(Block block,LootTable.Builder builder){
        var key=block.getLootTable().orElseThrow(()->new IllegalStateException("Block "+block+" has no loot table"));
        tables.put(key,builder);
    }
    @Override public void run(){
        generate();
        tables.forEach(output::accept);
    }
}