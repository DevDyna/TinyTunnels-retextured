package dev.thefern2.tinytunnels.client;

import java.util.IdentityHashMap;
import java.util.Map;

import dev.thefern2.tinytunnels.TinyTunnels;
import dev.thefern2.tinytunnels.registry.ModBlocks;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;

/** Wraps every machine block state's model in {@link MachineFaceModel}, which draws the face letters. */
@EventBusSubscriber(modid = TinyTunnels.MODID, value = Dist.CLIENT)
public final class ClientModels {
    private ClientModels() {}

    @SubscribeEvent
    static void wrapMachines(ModelEvent.ModifyBakingResult event) {
        Map<ModelResourceLocation, BakedModel> models = event.getModels();
        // States share baked models; wrap each one once.
        Map<BakedModel, BakedModel> wrapped = new IdentityHashMap<>();
        for (var holder : ModBlocks.MACHINES.values()) {
            Block block = holder.get();
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                ModelResourceLocation location = BlockModelShaper.stateToModelLocation(state);
                BakedModel base = models.get(location);
                if (base != null) models.put(location, wrapped.computeIfAbsent(base, MachineFaceModel::new));
            }
        }
    }
}
