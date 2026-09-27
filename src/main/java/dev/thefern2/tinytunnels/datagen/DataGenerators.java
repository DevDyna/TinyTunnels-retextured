package dev.thefern2.tinytunnels.datagen;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import dev.thefern2.tinytunnels.TinyTunnels;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.data.event.GatherDataEvent;

@EventBusSubscriber(modid = TinyTunnels.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class DataGenerators {
    @SubscribeEvent
    static void gatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput output = generator.getPackOutput();
        ExistingFileHelper files = event.getExistingFileHelper();
        CompletableFuture<HolderLookup.Provider> registries = event.getLookupProvider();

        generator.addProvider(event.includeClient(), new ModModelProvider(output, files));
        generator.addProvider(event.includeClient(), new ModLanguageProvider(output));
        ModBlockTagsProvider blockTags = generator.addProvider(event.includeServer(), new ModBlockTagsProvider(output, registries, files));
        generator.addProvider(event.includeServer(), new ModItemTagsProvider(output, registries, blockTags.contentsGetter(), files));
        generator.addProvider(event.includeServer(), new ModRecipeProvider(output, registries));
        generator.addProvider(event.includeServer(), new LootTableProvider(output, Set.of(),
                List.of(new LootTableProvider.SubProviderEntry(ModBlockLootProvider::new, LootContextParamSets.BLOCK)),
                registries));
    }

    private DataGenerators() {}
}
