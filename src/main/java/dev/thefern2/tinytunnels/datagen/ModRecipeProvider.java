package dev.thefern2.tinytunnels.datagen;

import java.util.concurrent.CompletableFuture;

import dev.thefern2.tinytunnels.machine.MachineSize;
import dev.thefern2.tinytunnels.registry.ModItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.Tags;

/**
 * Crafting recipes. Every machine size is made from raw materials, never from a smaller machine:
 * crafting ignores data components, so an upgrade recipe would eat a bound machine and strand its
 * room. Room walls have no recipe.
 */
public class ModRecipeProvider extends RecipeProvider {
    protected ModRecipeProvider(HolderLookup.Provider registries, RecipeOutput output) {
        super(registries, output);
    }

    @Override
    protected void buildRecipes() {
        machine(MachineSize.TINY, Tags.Items.INGOTS_COPPER);
        machine(MachineSize.SMALL, Tags.Items.INGOTS_IRON);
        machine(MachineSize.NORMAL, Tags.Items.INGOTS_GOLD);
        machine(MachineSize.LARGE, Tags.Items.GEMS_DIAMOND);
        shaped(RecipeCategory.MISC, ModItems.MACHINES.get(MachineSize.GIANT).get())
                .pattern("MMM").pattern("MEM").pattern("MMM")
                .define('M', Tags.Items.GEMS_EMERALD)
                .define('E', Items.ENDER_EYE)
                .unlockedBy("has_ender_eye", has(Items.ENDER_EYE))
                .save(output);
        shaped(RecipeCategory.MISC, ModItems.MACHINES.get(MachineSize.MAXIMUM).get())
                .pattern("NON").pattern("OSO").pattern("NON")
                .define('N', Tags.Items.INGOTS_NETHERITE)
                .define('O', Tags.Items.OBSIDIANS)
                .define('S', Tags.Items.NETHER_STARS)
                .unlockedBy("has_nether_star", has(Tags.Items.NETHER_STARS))
                .save(output);

        shaped(RecipeCategory.TOOLS, ModItems.SHRINKER.get())
                .pattern("IGI").pattern("IRI").pattern(" I ")
                .define('I', Tags.Items.INGOTS_IRON)
                .define('G', Tags.Items.GLASS_BLOCKS_COLORLESS)
                .define('R', Tags.Items.DUSTS_REDSTONE)
                .unlockedBy("has_redstone", has(Tags.Items.DUSTS_REDSTONE))
                .save(output);
        shaped(RecipeCategory.MISC, ModItems.TUNNEL.get(), 2)
                .pattern("CRC").pattern("HRB").pattern("C C")
                .define('C', Tags.Items.INGOTS_COPPER)
                .define('R', Tags.Items.DUSTS_REDSTONE)
                .define('H', Items.HOPPER)
                .define('B', Items.BUCKET)
                .unlockedBy("has_redstone", has(Tags.Items.DUSTS_REDSTONE))
                .save(output);
        shapeless(RecipeCategory.REDSTONE, ModItems.REDSTONE_TUNNEL.get())
                .requires(ModItems.TUNNEL.get())
                .requires(Items.COMPARATOR)
                .unlockedBy("has_tunnel", has(ModItems.TUNNEL.get()))
                .save(output);
        shaped(RecipeCategory.TOOLS, ModItems.TUNNEL_WRENCH.get())
                .pattern("I I").pattern(" C ").pattern(" I ")
                .define('I', Tags.Items.INGOTS_IRON)
                .define('C', Tags.Items.INGOTS_COPPER)
                .unlockedBy("has_tunnel", has(ModItems.TUNNEL.get()))
                .save(output);
    }

    /** Eight of the size's material around a redstone dust. */
    private void machine(MachineSize size, TagKey<Item> material) {
        shaped(RecipeCategory.MISC, ModItems.MACHINES.get(size).get())
                .pattern("MMM").pattern("MRM").pattern("MMM")
                .define('M', material)
                .define('R', Tags.Items.DUSTS_REDSTONE)
                .unlockedBy("has_redstone", has(Tags.Items.DUSTS_REDSTONE))
                .save(output);
    }

    public static class Runner extends RecipeProvider.Runner {
        public Runner(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
            super(output, registries);
        }

        @Override
        protected RecipeProvider createRecipeProvider(HolderLookup.Provider registries, RecipeOutput output) {
            return new ModRecipeProvider(registries, output);
        }

        @Override
        public String getName() {
            return "Tiny Tunnels Recipes";
        }
    }
}
