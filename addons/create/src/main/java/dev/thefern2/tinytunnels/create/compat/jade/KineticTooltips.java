package dev.thefern2.tinytunnels.create.compat.jade;

import java.util.Locale;

import dev.thefern2.tinytunnels.create.TinyTunnelsCreate;
import dev.thefern2.tinytunnels.create.kinetic.KineticMode;
import dev.thefern2.tinytunnels.create.kinetic.KineticTunnelKind;
import dev.thefern2.tinytunnels.create.kinetic.KineticTunnelWallBlock;
import dev.thefern2.tinytunnels.create.kinetic.LinkedKineticBlockEntity;
import dev.thefern2.tinytunnels.create.port.KineticPortBlock;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * One line for each end: "Links to the east side: Out, 64 RPM, 128 SU" (plus "overstressed"), "Not linked", or
 * "Blocked: same source on both sides" when the loop guard stops it. SU is
 * the stress going through the tunnel, the same at both ends. The server sends {@code linked}, {@code mode},
 * {@code rpm}, {@code su} and {@code overstressed}; the face comes from the block state.
 */
enum KineticTooltips implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    KINETIC_TUNNEL("kinetic_tunnel") {
        @Override
        Direction face(BlockState state) {
            return state.getValue(KineticTunnelWallBlock.FACE);
        }
    },
    KINETIC_PORT("kinetic_port") {
        @Override
        Direction face(BlockState state) {
            return state.getValue(KineticPortBlock.FACING);
        }
    };

    private final ResourceLocation uid;

    KineticTooltips(String path) {
        this.uid = TinyTunnelsCreate.id(path);
    }

    /** The machine face this end belongs to. */
    abstract Direction face(BlockState state);

    @Override
    public ResourceLocation getUid() {
        return uid;
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof LinkedKineticBlockEntity end)) return;
        KineticMode mode = end.mode();
        data.putBoolean("linked", mode != null);
        if (mode == null) return;
        data.putBoolean("blocked", end.isBlocked());
        data.putString("mode", mode.getSerializedName());
        data.putFloat("rpm", end.getSpeed());
        data.putFloat("su", end.passedStress());
        data.putBoolean("overstressed", end.isOverStressed());
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (!data.contains("linked")) return;
        if (!data.getBoolean("linked")) {
            tooltip.add(Component.translatable("jade.tinytunnels_create.not_linked"));
            return;
        }
        if (data.getBoolean("blocked")) {
            tooltip.add(Component.translatable("jade.tinytunnels_create.blocked_same_source"));
            return;
        }
        Component side = KineticTunnelKind.faceName(face(accessor.getBlockState()));
        Component details = Component.translatable("tunnel_kind.tinytunnels_create.kinetic." + data.getString("mode"))
                .append(String.format(Locale.ROOT, ", %.0f RPM, %.0f SU", Math.abs(data.getFloat("rpm")), data.getFloat("su")));
        if (data.getBoolean("overstressed")) {
            details = Component.empty().append(details).append(", ").append(Component.translatable("jade.tinytunnels_create.overstressed"));
        }
        tooltip.add(Component.translatable("jade.tinytunnels_create.links_to", side, details));
    }
}
