package codechicken.lib.internal;

import codechicken.lib.datagen.LanguageProvider;
import codechicken.lib.util.CCLTags;
import net.covers1624.quack.util.CrashLock;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.ItemTagsProvider;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.data.BlockTagsProvider;

import net.neoforged.neoforge.data.event.GatherDataEvent;

import java.util.concurrent.CompletableFuture;

import static codechicken.lib.CodeChickenLib.MOD_ID;

/**
 * Created by covers1624 on 13/12/20.
 */
public class DataGenerators {

    private static final CrashLock LOCK = new CrashLock("Already Initialized");

    public static void init(IEventBus modBus) {
        LOCK.lock();
        modBus.addListener(DataGenerators::gatherServerData);
        modBus.addListener(DataGenerators::gatherClientData);
    }

    private static void gatherServerData(GatherDataEvent.Server event) {
        PackOutput output = event.getGenerator().getPackOutput();
        CompletableFuture<HolderLookup.Provider> lookupProvider = event.getWorldLookupProvider();
        event.addProvider(new BlockTags(output, lookupProvider));
        event.addProvider(new ItemTags(output, lookupProvider));
        event.addProvider(new LangUS(output, LanguageProvider.Side.SERVER));
    }

    private static void gatherClientData(GatherDataEvent.Client event) {
        event.addProvider(new LangUS(event.getGenerator().getPackOutput(), LanguageProvider.Side.CLIENT));
    }

    private static class BlockTags extends BlockTagsProvider {

        public BlockTags(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
            super(output, lookupProvider, MOD_ID);
        }

        @Override
        protected void addTags(HolderLookup.Provider pProvider) {
            tag(CCLTags.Blocks.WOOLS)
                    .addTag(CCLTags.Blocks.WOOLS_WHITE)
                    .addTag(CCLTags.Blocks.WOOLS_ORANGE)
                    .addTag(CCLTags.Blocks.WOOLS_MAGENTA)
                    .addTag(CCLTags.Blocks.WOOLS_LIGHT_BLUE)
                    .addTag(CCLTags.Blocks.WOOLS_YELLOW)
                    .addTag(CCLTags.Blocks.WOOLS_LIME)
                    .addTag(CCLTags.Blocks.WOOLS_PINK)
                    .addTag(CCLTags.Blocks.WOOLS_GRAY)
                    .addTag(CCLTags.Blocks.WOOLS_LIGHT_GRAY)
                    .addTag(CCLTags.Blocks.WOOLS_CYAN)
                    .addTag(CCLTags.Blocks.WOOLS_PURPLE)
                    .addTag(CCLTags.Blocks.WOOLS_BLUE)
                    .addTag(CCLTags.Blocks.WOOLS_BROWN)
                    .addTag(CCLTags.Blocks.WOOLS_GREEN)
                    .addTag(CCLTags.Blocks.WOOLS_RED)
                    .addTag(CCLTags.Blocks.WOOLS_BLACK);
            tag(CCLTags.Blocks.WOOLS_WHITE).add(ResourceKey.create(Registries.BLOCK, Identifier.withDefaultNamespace("white_wool")));
            tag(CCLTags.Blocks.WOOLS_ORANGE).add(ResourceKey.create(Registries.BLOCK, Identifier.withDefaultNamespace("orange_wool")));
            tag(CCLTags.Blocks.WOOLS_MAGENTA).add(ResourceKey.create(Registries.BLOCK, Identifier.withDefaultNamespace("magenta_wool")));
            tag(CCLTags.Blocks.WOOLS_LIGHT_BLUE).add(ResourceKey.create(Registries.BLOCK, Identifier.withDefaultNamespace("light_blue_wool")));
            tag(CCLTags.Blocks.WOOLS_YELLOW).add(ResourceKey.create(Registries.BLOCK, Identifier.withDefaultNamespace("yellow_wool")));
            tag(CCLTags.Blocks.WOOLS_LIME).add(ResourceKey.create(Registries.BLOCK, Identifier.withDefaultNamespace("lime_wool")));
            tag(CCLTags.Blocks.WOOLS_PINK).add(ResourceKey.create(Registries.BLOCK, Identifier.withDefaultNamespace("pink_wool")));
            tag(CCLTags.Blocks.WOOLS_GRAY).add(ResourceKey.create(Registries.BLOCK, Identifier.withDefaultNamespace("gray_wool")));
            tag(CCLTags.Blocks.WOOLS_LIGHT_GRAY).add(ResourceKey.create(Registries.BLOCK, Identifier.withDefaultNamespace("light_gray_wool")));
            tag(CCLTags.Blocks.WOOLS_CYAN).add(ResourceKey.create(Registries.BLOCK, Identifier.withDefaultNamespace("cyan_wool")));
            tag(CCLTags.Blocks.WOOLS_PURPLE).add(ResourceKey.create(Registries.BLOCK, Identifier.withDefaultNamespace("purple_wool")));
            tag(CCLTags.Blocks.WOOLS_BLUE).add(ResourceKey.create(Registries.BLOCK, Identifier.withDefaultNamespace("blue_wool")));
            tag(CCLTags.Blocks.WOOLS_BROWN).add(ResourceKey.create(Registries.BLOCK, Identifier.withDefaultNamespace("brown_wool")));
            tag(CCLTags.Blocks.WOOLS_GREEN).add(ResourceKey.create(Registries.BLOCK, Identifier.withDefaultNamespace("green_wool")));
            tag(CCLTags.Blocks.WOOLS_RED).add(ResourceKey.create(Registries.BLOCK, Identifier.withDefaultNamespace("red_wool")));
            tag(CCLTags.Blocks.WOOLS_BLACK).add(ResourceKey.create(Registries.BLOCK, Identifier.withDefaultNamespace("black_wool")));
        }

        @Override
        public String getName() {
            return "CodeChickenLib Block tags.";
        }
    }

    private static class ItemTags extends ItemTagsProvider {

        public ItemTags(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
            super(output, lookupProvider, MOD_ID);
        }

        @Override
        protected void addTags(HolderLookup.Provider pProvider) {
            tag(CCLTags.Items.WOOLS)
                    .addTag(CCLTags.Items.WOOLS_WHITE)
                    .addTag(CCLTags.Items.WOOLS_ORANGE)
                    .addTag(CCLTags.Items.WOOLS_MAGENTA)
                    .addTag(CCLTags.Items.WOOLS_LIGHT_BLUE)
                    .addTag(CCLTags.Items.WOOLS_YELLOW)
                    .addTag(CCLTags.Items.WOOLS_LIME)
                    .addTag(CCLTags.Items.WOOLS_PINK)
                    .addTag(CCLTags.Items.WOOLS_GRAY)
                    .addTag(CCLTags.Items.WOOLS_LIGHT_GRAY)
                    .addTag(CCLTags.Items.WOOLS_CYAN)
                    .addTag(CCLTags.Items.WOOLS_PURPLE)
                    .addTag(CCLTags.Items.WOOLS_BLUE)
                    .addTag(CCLTags.Items.WOOLS_BROWN)
                    .addTag(CCLTags.Items.WOOLS_GREEN)
                    .addTag(CCLTags.Items.WOOLS_RED)
                    .addTag(CCLTags.Items.WOOLS_BLACK);
            tag(CCLTags.Items.WOOLS_WHITE).add(ResourceKey.create(Registries.ITEM, Identifier.withDefaultNamespace("white_wool")));
            tag(CCLTags.Items.WOOLS_ORANGE).add(ResourceKey.create(Registries.ITEM, Identifier.withDefaultNamespace("orange_wool")));
            tag(CCLTags.Items.WOOLS_MAGENTA).add(ResourceKey.create(Registries.ITEM, Identifier.withDefaultNamespace("magenta_wool")));
            tag(CCLTags.Items.WOOLS_LIGHT_BLUE).add(ResourceKey.create(Registries.ITEM, Identifier.withDefaultNamespace("light_blue_wool")));
            tag(CCLTags.Items.WOOLS_YELLOW).add(ResourceKey.create(Registries.ITEM, Identifier.withDefaultNamespace("yellow_wool")));
            tag(CCLTags.Items.WOOLS_LIME).add(ResourceKey.create(Registries.ITEM, Identifier.withDefaultNamespace("lime_wool")));
            tag(CCLTags.Items.WOOLS_PINK).add(ResourceKey.create(Registries.ITEM, Identifier.withDefaultNamespace("pink_wool")));
            tag(CCLTags.Items.WOOLS_GRAY).add(ResourceKey.create(Registries.ITEM, Identifier.withDefaultNamespace("gray_wool")));
            tag(CCLTags.Items.WOOLS_LIGHT_GRAY).add(ResourceKey.create(Registries.ITEM, Identifier.withDefaultNamespace("light_gray_wool")));
            tag(CCLTags.Items.WOOLS_CYAN).add(ResourceKey.create(Registries.ITEM, Identifier.withDefaultNamespace("cyan_wool")));
            tag(CCLTags.Items.WOOLS_PURPLE).add(ResourceKey.create(Registries.ITEM, Identifier.withDefaultNamespace("purple_wool")));
            tag(CCLTags.Items.WOOLS_BLUE).add(ResourceKey.create(Registries.ITEM, Identifier.withDefaultNamespace("blue_wool")));
            tag(CCLTags.Items.WOOLS_BROWN).add(ResourceKey.create(Registries.ITEM, Identifier.withDefaultNamespace("brown_wool")));
            tag(CCLTags.Items.WOOLS_GREEN).add(ResourceKey.create(Registries.ITEM, Identifier.withDefaultNamespace("green_wool")));
            tag(CCLTags.Items.WOOLS_RED).add(ResourceKey.create(Registries.ITEM, Identifier.withDefaultNamespace("red_wool")));
            tag(CCLTags.Items.WOOLS_BLACK).add(ResourceKey.create(Registries.ITEM, Identifier.withDefaultNamespace("black_wool")));
        }

        @Override
        public String getName() {
            return "CodeChickenLib Item tags.";
        }
    }

    public static class LangUS extends LanguageProvider {

        public LangUS(PackOutput output, Side side) {
            super(output, MOD_ID, "en_us", side);
        }

        @Override
        protected void addTranslations() {
            addServer("ccl.commands.gc.before", "Before:");
            addServer("ccl.commands.gc.performing", "Performing GC..");
            addServer("ccl.commands.gc.after", "After:");
            addServer("ccl.commands.killall.fail", "Found no entities.");
            addServer("ccl.commands.killall.fail.player", "You cannot kill players with this command.");
            addServer("ccl.commands.killall.success", "Killed %s entities.");
            addServer("ccl.commands.killall.success.line", "Killed %s");
            addServer("ccl.commands.count.fail", "Found no entities.");
            addServer("ccl.commands.count.total", "Found %s entities.");

            addServer("ccl.energy_bar.energy_storage", "Energy Storage");
            addServer("ccl.energy_bar.capacity", "Capacity:");
            addServer("ccl.energy_bar.stored", "Stored:");
            addServer("ccl.energy_bar.rf", "RF");
            addServer("ccl.fluid_tank.fluid_storage", "Fluid Storage");
            addServer("ccl.fluid_tank.capacity", "Capacity:");
            addServer("ccl.fluid_tank.stored", "Stored:");
            addServer("ccl.fluid_tank.mb", "mB");
            addServer("ccl.fluid_tank.contains", "Contains:");
        }
    }
}
