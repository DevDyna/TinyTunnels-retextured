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

        addItem(ModItems.SHRINKER, "Shrinker");
        addItem(ModItems.TUNNEL, "Tunnel");
        addItem(ModItems.TUNNEL_WRENCH, "Tunnel Wrench");

        add("tooltip.tinytunnels.machine.unbound", "New room");
        add("tooltip.tinytunnels.machine.bound", "Room %s");
        add("message.tinytunnels.enter.no_room", "This machine has no room yet");
        add("message.tinytunnels.exit.no_return", "Couldn't find the way back; sent to world spawn");
        add("message.tinytunnels.tunnel.mapped", "Tunnel linked to the machine's %s side");
        add("message.tinytunnels.tunnel.removed", "Tunnel removed");
        add("message.tinytunnels.tunnel.no_free_face", "Every other side already has a tunnel");
        add("message.tinytunnels.tunnel.all_faces_used", "All six sides already have a tunnel");
        add("message.tinytunnels.tunnel.edge", "Tunnels can't go on an edge or corner");
        add("tinytunnels.face.down", "bottom");
        add("tinytunnels.face.up", "top");
        add("tinytunnels.face.north", "north");
        add("tinytunnels.face.south", "south");
        add("tinytunnels.face.west", "west");
        add("tinytunnels.face.east", "east");
        add("message.tinytunnels.machine.inside_itself", "A machine can't go inside its own room");
        add("message.tinytunnels.machine.already_placed", "This machine's room is already in use by a machine at %s in %s");
    }

    private static String machineName(MachineSize size) {
        String name = size.getName();
        return Character.toUpperCase(name.charAt(0)) + name.substring(1) + " Machine (" + size.getInterior() + "x" + size.getInterior() + ")";
    }
}
