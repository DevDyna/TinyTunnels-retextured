package dev.thefern2.tinytunnels.create.datagen;

import java.util.concurrent.CompletableFuture;

import dev.thefern2.tinytunnels.create.registry.ModItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.common.Tags;

public class ModRecipeProvider extends RecipeProvider {
    public ModRecipeProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    @Override
    protected void buildRecipes(RecipeOutput output) {
        // Core's tunnel and Create's parts, by id: neither is part of our compile-time API.
        Item tunnel = item("tinytunnels", "tunnel");
        Item shaft = item("create", "shaft");
        Item casing = item("create", "andesite_casing");
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.KINETIC_TUNNEL.get())
                .requires(tunnel)
                .requires(shaft)
                .requires(casing)
                .unlockedBy("has_tunnel", has(tunnel))
                .save(output);
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.KINETIC_PORT.get())
                .requires(shaft)
                .requires(casing)
                .requires(Tags.Items.INGOTS_COPPER)
                .unlockedBy("has_kinetic_tunnel", has(ModItems.KINETIC_TUNNEL.get()))
                .save(output);
    }

    private static Item item(String namespace, String path) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(namespace, path));
    }
}
