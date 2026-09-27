package dev.thefern2.tinytunnels.compat.jade;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


import dev.thefern2.tinytunnels.machine.MachineBlock;
import dev.thefern2.tinytunnels.tunnel.RedstoneTunnelWallBlock;
import dev.thefern2.tinytunnels.tunnel.TunnelWallBlock;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Client side of the tooltips. Tunnel faces and a wall's machine side come from block states;
 * redstone direction and signal come from {@link ServerData}, and are left out when the server
 * doesn't have the plugin.
 */
enum Tooltips implements IBlockComponentProvider {
    /**
     * At most two short lines, using the letters shown on the machine's faces:
     * "Tunnels: D U" and "Redstone: N in 15, E out 0".
     */
    MACHINE(TinyTunnelsJadePlugin.MACHINE) {
        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            BlockState state = accessor.getBlockState();
            CompoundTag data = accessor.getServerData();
            List<String> tunnels = new ArrayList<>();
            List<String> redstone = new ArrayList<>();
            for (Direction face : Direction.values()) {
                String letter = face.getSerializedName().substring(0, 1).toUpperCase(Locale.ROOT);
                if (MachineBlock.hasTunnel(state, face)) {
                    tunnels.add(letter);
                } else if (MachineBlock.hasRedstone(state, face)) {
                    String side = face.getSerializedName();
                    redstone.add(data.contains("redstone_" + side)
                            ? letter + " " + data.getString("redstone_" + side) + " " + data.getInt("power_" + side)
                            : letter);
                }
            }
            if (!tunnels.isEmpty()) tooltip.add(Component.translatable("jade.tinytunnels.tunnels", String.join(" ", tunnels)));
            if (!redstone.isEmpty()) tooltip.add(Component.translatable("jade.tinytunnels.redstone", String.join(", ", redstone)));
        }
    },
    /** One line: "Links to the top side". */
    TUNNEL(TinyTunnelsJadePlugin.TUNNEL) {
        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            tooltip.add(Component.translatable("jade.tinytunnels.links_to", faceName(accessor.getBlockState().getValue(TunnelWallBlock.FACE))));
        }
    },
    /** One line: "Links to the north side: in 15". */
    REDSTONE_TUNNEL(TinyTunnelsJadePlugin.REDSTONE_TUNNEL) {
        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            BlockState state = accessor.getBlockState();
            Component side = faceName(state.getValue(RedstoneTunnelWallBlock.FACE));
            String mode = state.getValue(RedstoneTunnelWallBlock.MODE).getSerializedName();
            CompoundTag data = accessor.getServerData();
            tooltip.add(data.contains("power")
                    ? Component.translatable("jade.tinytunnels.redstone_tunnel", side, mode, data.getInt("power"))
                    : Component.translatable("jade.tinytunnels.redstone_tunnel.no_power", side, mode));
        }
    };

    private final ResourceLocation uid;

    Tooltips(ResourceLocation uid) {
        this.uid = uid;
    }

    @Override
    public ResourceLocation getUid() {
        return uid;
    }

    private static Component faceName(Direction face) {
        return Component.translatable("tinytunnels.face." + face.getSerializedName());
    }
}
