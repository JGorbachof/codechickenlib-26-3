package codechicken.lib.datagen.recipe;

import com.google.gson.JsonParser;
import com.google.common.hash.HashCode;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import net.minecraft.tags.ItemTags;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.conditions.ModLoadedCondition;
import net.neoforged.neoforge.data.event.GatherDataEvent;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

@EventBusSubscriber(modid="codechickenlib")
public final class RecipeDataSmoke {
    @SubscribeEvent
    public static void gather(GatherDataEvent.Server event) {
        if(!Boolean.getBoolean("ccl.dev.recipeSmoke"))return;
        var output=event.getGenerator().getPackOutput();
        var recipeId=Identifier.parse("codechickenlib:smoke/nested_gear");
        var plainId=Identifier.parse("codechickenlib:smoke/plain");
        var provider=new RecipeProvider(event.getReloadableLookupProvider(),output,"codechickenlib") {
            @Override protected void registerRecipes() {
                shapedRecipe(Items.IRON_INGOT,1,recipeId).autoCriteria()
                    .withCondition(new ModLoadedCondition("codechickenlib"))
                    .patternLine("PP").key('P',ItemTags.PLANKS);
                shapelessRecipe(Items.STICK,1,plainId).addIngredient(Items.OAK_PLANKS);
                smelting(Items.IRON_INGOT,2,Identifier.parse("codechickenlib:smoke/smelting"))
                    .ingredient(Items.RAW_IRON).experience(0.7F).cookingTime(120);
                blasting(Items.IRON_INGOT,1,Identifier.parse("codechickenlib:smoke/blasting")).ingredient(Items.RAW_IRON);
                smoking(Items.COOKED_BEEF,1,Identifier.parse("codechickenlib:smoke/smoking")).ingredient(Items.BEEF);
                campfire(Items.COOKED_BEEF,1,Identifier.parse("codechickenlib:smoke/campfire")).ingredient(Items.BEEF);
            }
        };
        event.addProvider(new DataProvider() {
            @Override public String getName(){return "CCL recipe bootstrap smoke";}
            @Override public CompletableFuture<?> run(CachedOutput cache){
                var first=new ConcurrentHashMap<Path,byte[]>();
                var second=new ConcurrentHashMap<Path,byte[]>();
                return provider.run((p,b,h)->first.put(p,b.clone()))
                    .thenCompose(v->provider.run((p,b,h)->second.put(p,b.clone())))
                    .thenRun(()->{
                        if(first.size()!=7||!first.keySet().equals(second.keySet()))throw new IllegalStateException("Unexpected generated paths "+first.keySet());
                        first.forEach((p,b)->{if(!Arrays.equals(b,second.get(p)))throw new IllegalStateException("Nonrepeatable recipe data "+p);});
                        Path recipe=find(first,"recipe/smoke/nested_gear.json");
                        Path advancement=find(first,"advancement/recipes/smoke/nested_gear.json");
                        find(first,"recipe/smoke/plain.json");
                        for(String name:List.of("smelting","blasting","smoking","campfire")){
                            var file=find(first,"recipe/smoke/"+name+".json");
                            var json=JsonParser.parseString(new String(first.get(file),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                            String expected="minecraft:"+(name.equals("campfire")?"campfire_cooking":name);
                            if(!json.get("type").getAsString().equals(expected))throw new IllegalStateException("Wrong furnace type "+json);
                            if(name.equals("smelting") && (json.getAsJsonObject("result").get("count").getAsInt()!=2 ||
                                json.get("cookingtime").getAsInt()!=120 || Math.abs(json.get("experience").getAsFloat()-0.7F)>0.0001F))
                                throw new IllegalStateException("Furnace output/timing changed "+json);
                        }
                        String r=new String(first.get(recipe),java.nio.charset.StandardCharsets.UTF_8);
                        String a=new String(first.get(advancement),java.nio.charset.StandardCharsets.UTF_8);
                        if(!r.contains("minecraft:planks")||!r.contains("neoforge:conditions")||
                           !a.contains("neoforge:conditions")||!a.contains("has_the_recipe")||
                           !a.contains("has_ingredient_0")||!a.contains(recipeId.toString()))
                            throw new IllegalStateException("Missing recipe/advancement data "+r+" "+a);
                        first.forEach((p,b)->{
                            try{cache.writeIfNeeded(p,b,com.google.common.hash.Hashing.sha1().hashBytes(b));}
                            catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
                        });
                        System.out.println("CCL_RECIPE_SMOKE_PASS: seven repeatable files, tagged conditional recipe, advancement, shapeless and four furnace types");
                    });
            }
        });
    }
    private static Path find(Map<Path,byte[]> files,String suffix){
        return files.keySet().stream().filter(p->p.toString().replace('\\','/').endsWith(suffix)).findFirst()
                .orElseThrow(()->new IllegalStateException("Missing "+suffix+": "+files.keySet()));
    }
}