package dev.thefern2.tinytunnels.datagen;

import dev.thefern2.tinytunnels.TinyTunnels;
import dev.thefern2.tinytunnels.machine.MachineSize;
import dev.thefern2.tinytunnels.registry.ModBlocks;
import dev.thefern2.tinytunnels.registry.ModItems;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

public class ModLanguageProvider extends LanguageProvider {
    public ModLanguageProvider(PackOutput output) {
        super(output, TinyTunnels.MODID, "en_us");
    }

    @Override
    protected void addTranslations() {
        add("itemGroup.tinytunnels", "Tiny Tunnels");

        ModBlocks.MACHINES.forEach((size, block) -> addBlock(block, machineName(size)));
        addBlock(ModBlocks.ROOM_WALL, "Room Wall");
        addBlock(ModBlocks.TUNNEL_WALL, "Tunnel Wall");
        addBlock(ModBlocks.REDSTONE_TUNNEL_WALL, "Redstone Tunnel Wall");
        addBlock(ModBlocks.UNKNOWN_TUNNEL_WALL, "Unknown Tunnel");

        addItem(ModItems.SHRINKER, "Shrinker");
        addItem(ModItems.TUNNEL, "Tunnel");
        addItem(ModItems.REDSTONE_TUNNEL, "Redstone Tunnel");
        addItem(ModItems.TUNNEL_WRENCH, "Tunnel Wrench");
        // Create only; runData runs with Create.

        add("tooltip.tinytunnels.machine.unbound", "New room");
        add("tooltip.tinytunnels.machine.copy_hint", "Sneak + right-click a machine of this size to copy its room");
        add("tooltip.tinytunnels.machine.bound", "Room %s");
        add("message.tinytunnels.enter.no_room", "This machine has no room yet");
        add("message.tinytunnels.exit.no_return", "Couldn't find the way back; sent to world spawn");
        add("message.tinytunnels.bed.no_sleep", "You can't sleep inside a room");
        add("message.tinytunnels.tunnel.mapped", "Tunnel linked to the machine's %s side");
        add("message.tinytunnels.tunnel.removed", "Tunnel removed");
        add("message.tinytunnels.tunnel.no_free_face", "Every other side already has a tunnel");
        add("message.tinytunnels.tunnel.all_faces_used", "All six sides already have a tunnel");
        add("message.tinytunnels.tunnel.not_room_wall", "Tunnels go on a room's wall, from inside");
        add("message.tinytunnels.tunnel.one_per_machine", "This machine already has a %s");
        add("message.tinytunnels.tunnel.max_per_room", "This machine already has %2$s of: %1$s");
        add("message.tinytunnels.tunnel.unknown_kind", "Unknown tunnel (%s): its mod isn't loaded, so it can't be moved or removed");
        add("message.tinytunnels.tunnel.edge", "Tunnels can't go on an edge or corner");
        add("tinytunnels.tunnel_mode.passthrough", "Pass-through");
        add("tinytunnels.tunnel_mode.buffered_in", "Buffered in");
        add("tinytunnels.tunnel_mode.buffered_out", "Buffered out");
        add("message.tinytunnels.tunnel.mode", "Tunnel set to %s");
        add("message.tinytunnels.tunnel.not_empty", "Empty the tunnel first");
        add("message.tinytunnels.tunnel.holds_fluid", "This tunnel holds %s mB of %s. Sneak + wrench again to discard it");
        add("jade.tinytunnels.buffer", "%s: %s");
        add("jade.tinytunnels.buffer.empty", "empty");
        add("message.tinytunnels.redstone_tunnel.in", "Redstone in from the machine's %s side");
        add("message.tinytunnels.redstone_tunnel.out", "Redstone out to the machine's %s side");
        add("tinytunnels.face.down", "bottom");
        add("tinytunnels.face.up", "top");
        add("tinytunnels.face.north", "north");
        add("tinytunnels.face.south", "south");
        add("tinytunnels.face.west", "west");
        add("tinytunnels.face.east", "east");
        add("message.tinytunnels.machine.inside_itself", "A machine can't go inside its own room");
        add("message.tinytunnels.machine.already_placed", "This machine's room is already in use by a machine at %s in %s");
        add("message.tinytunnels.machine.confirm_delete_keep", "Sneak + wrench again to empty this room into your inventory and remove it");
        add("message.tinytunnels.machine.confirm_delete_destroy", "Sneak + wrench again to remove this room; its contents will be lost");
        add("message.tinytunnels.machine.deleted_keep", "Room removed; its contents are in your inventory");
        add("message.tinytunnels.machine.deleted_destroy", "Room removed with its contents");
        add("message.tinytunnels.machine.turned", "Machine turned: its side tunnels moved one side clockwise");
        add("message.tinytunnels.copy.different_size", "This empty machine isn't the same size as the one you're copying");
        add("message.tinytunnels.copy.refused", "This room can't be copied in survival: %s");
        add("message.tinytunnels.copy.missing", "Missing to copy this room: %s");
        add("message.tinytunnels.copy.and_more", "%s, and %s more");
        add("message.tinytunnels.copy.done", "Room copied into the empty machine");
        add("message.tinytunnels.copy.done_skipped", "Room copied into the empty machine; the %s machines inside weren't copied");
        add("message.tinytunnels.machine.room_loading", "The room is still loading; try again in a moment");

        // Tunnel kinds: names and one-line status (TunnelKind.displayName and describe).
        add("tunnel_kind.tinytunnels.transfer", "Tunnel");
        add("tunnel_kind.tinytunnels.redstone", "Redstone Tunnel");
        add("tunnel_kind.tinytunnels.redstone.in", "In, signal %s");
        add("tunnel_kind.tinytunnels.redstone.out", "Out, signal %s");

        // Jade tooltips (compat/jade) and their entries in Jade's settings.
        add("jade.tinytunnels.machine_tunnels", "%s: %s");
        add("jade.tinytunnels.links_to", "Links to the %s side");
        add("jade.tinytunnels.redstone_tunnel", "Links to the %s side: %s %s");
        add("jade.tinytunnels.redstone_tunnel.no_power", "Links to the %s side: %s");
        add("config.jade.plugin_tinytunnels.machine", "Machine tunnels");
        add("config.jade.plugin_tinytunnels.tunnel", "Tunnel machine side");
        add("config.jade.plugin_tinytunnels.redstone_tunnel", "Redstone tunnel");
        add("jade.tinytunnels.overstressed", "overstressed");
    }

    private static String machineName(MachineSize size) {
        String name = size.getName();
        return Character.toUpperCase(name.charAt(0)) + name.substring(1) + " Machine (" + size.getInterior() + "x" + size.getInterior() + ")";
    }
}
