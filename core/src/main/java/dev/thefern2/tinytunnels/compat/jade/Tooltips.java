package dev.thefern2.tinytunnels.compat.jade;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import dev.thefern2.tinytunnels.room.TunnelMode;
import dev.thefern2.tinytunnels.tunnel.KineticTunnelWallBlock;
import dev.thefern2.tinytunnels.tunnel.RedstoneTunnelWallBlock;
import dev.thefern2.tinytunnels.tunnel.TunnelWallBlock;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Client side of the tooltips. A wall's machine side comes from its block state; a machine's tunnels,
 * redstone signals and buffers come from {@link ServerData}, and are left out when the server doesn't
 * have the plugin.
 */
enum Tooltips implements IBlockComponentProvider {
    /**
     * One line per tunnel kind on the machine, in face order, with the face letters shown on the machine and each
     * tunnel's status: "Tunnel: D (Pass-through), U (Buffered in)", "Redstone Tunnel: N (In, signal 15)" and
     * "Kinetic Tunnel: E (Out), 64 RPM, 128 SU". Names and status come from the kinds, through {@link ServerData}.
     */
    MACHINE(TinyTunnelsJadePlugin.MACHINE) {
        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getServerData();
            RegistryOps<Tag> ops = accessor.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
            // Kind id -> its name, and the entries for its faces.
            Map<String, Component> names = new LinkedHashMap<>();
            Map<String, List<Component>> entries = new LinkedHashMap<>();
            Map<String, CompoundTag> byFace = new HashMap<>();
            for (Tag tag : data.getList("faces", Tag.TAG_COMPOUND)) {
                CompoundTag entry = (CompoundTag) tag;
                byFace.put(entry.getString("face"), entry);
            }
            for (Direction face : Direction.values()) {
                CompoundTag entry = byFace.get(face.getSerializedName());
                if (entry == null) continue;
                String kind = entry.getString("kind");
                Component name = component(entry.get("name"), ops);
                if (name == null) continue;
                names.putIfAbsent(kind, name);
                MutableComponent shown = Component.literal(face.getSerializedName().substring(0, 1).toUpperCase(Locale.ROOT));
                Component status = component(entry.get("status"), ops);
                if (status != null) shown.append(" (").append(status).append(")");
                entries.computeIfAbsent(kind, k -> new ArrayList<>()).add(shown);
            }
            names.forEach((kind, name) -> {
                Component list = ComponentUtils.formatList(entries.get(kind), Component.literal(", "));
                // Create's speed and stress for the kinetic tunnel; moves to the Create addon's own provider in A6.
                if (kind.equals(KINETIC_KIND) && data.contains("kinetic_rpm")) list = Component.empty().append(list).append(", ").append(kineticDetails(data));
                tooltip.add(Component.translatable("jade.tinytunnels.machine_tunnels", name, list));
            });
        }
    },
    /** One line: "Links to the east side: out, 64 RPM, 128 SU". */
    KINETIC_TUNNEL(TinyTunnelsJadePlugin.KINETIC_TUNNEL) {
        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            BlockState state = accessor.getBlockState();
            Component side = faceName(state.getValue(KineticTunnelWallBlock.FACE));
            String mode = state.getValue(KineticTunnelWallBlock.MODE).getSerializedName();
            tooltip.add(Component.translatable("jade.tinytunnels.kinetic_tunnel", side, kineticDetails(mode, accessor.getServerData())));
        }
    },
    /** One line: "Links to the top side". */
    TUNNEL(TinyTunnelsJadePlugin.TUNNEL) {
        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            BlockState state = accessor.getBlockState();
            tooltip.add(Component.translatable("jade.tinytunnels.links_to", faceName(state.getValue(TunnelWallBlock.FACE))));
            TunnelMode mode = state.getValue(TunnelWallBlock.MODE);
            if (!mode.isBuffered()) return;
            // "Buffered in: 12 Cobblestone, 500 mB Water"
            CompoundTag data = accessor.getServerData();
            HolderLookup.Provider registries = accessor.getLevel().registryAccess();
            List<Component> contents = new ArrayList<>();
            for (Tag tag : data.getList("items", Tag.TAG_COMPOUND)) {
                ItemStack stack = ItemStack.parseOptional(registries, (CompoundTag) tag);
                if (!stack.isEmpty()) contents.add(Component.literal(stack.getCount() + " ").append(stack.getHoverName()));
            }
            FluidStack fluid = data.contains("fluid") ? FluidStack.parseOptional(registries, data.getCompound("fluid")) : FluidStack.EMPTY;
            if (!fluid.isEmpty()) contents.add(Component.literal(fluid.getAmount() + " mB ").append(fluid.getHoverName()));
            Component shown = contents.isEmpty() ? Component.translatable("jade.tinytunnels.buffer.empty") : ComponentUtils.formatList(contents, Component.literal(", "));
            tooltip.add(Component.translatable("jade.tinytunnels.buffer", mode.displayName(), shown));
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

    private static final String KINETIC_KIND = "tinytunnels:kinetic";

    /** "out, 64 RPM, 128 SU", plus "overstressed"; just the start when the server sent no speed (no Create). */
    private static Component kineticDetails(String start, CompoundTag data) {
        if (!data.contains("kinetic_rpm")) return Component.literal(start);
        return Component.literal(start + ", ").append(kineticDetails(data));
    }

    /** "64 RPM, 128 SU", plus "overstressed". */
    private static Component kineticDetails(CompoundTag data) {
        Component details = Component.literal(String.format(Locale.ROOT, "%.0f RPM, %.0f SU", Math.abs(data.getFloat("kinetic_rpm")), data.getFloat("kinetic_su")));
        return data.getBoolean("kinetic_overstressed")
                ? Component.empty().append(details).append(", ").append(Component.translatable("jade.tinytunnels.overstressed"))
                : details;
    }

    private static @Nullable Component component(@Nullable Tag tag, RegistryOps<Tag> ops) {
        return tag == null ? null : ComponentSerialization.CODEC.parse(ops, tag).result().orElse(null);
    }

    private static Component faceName(Direction face) {
        return Component.translatable("tinytunnels.face." + face.getSerializedName());
    }
}
