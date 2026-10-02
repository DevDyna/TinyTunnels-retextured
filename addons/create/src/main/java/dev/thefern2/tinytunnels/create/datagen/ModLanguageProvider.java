package dev.thefern2.tinytunnels.create.datagen;

import dev.thefern2.tinytunnels.create.TinyTunnelsCreate;
import dev.thefern2.tinytunnels.create.registry.ModBlocks;
import dev.thefern2.tinytunnels.create.registry.ModItems;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

public class ModLanguageProvider extends LanguageProvider {
    public ModLanguageProvider(PackOutput output) {
        super(output, TinyTunnelsCreate.MODID, "en_us");
    }

    @Override
    protected void addTranslations() {
        addBlock(ModBlocks.KINETIC_TUNNEL_WALL, "Kinetic Tunnel Wall");
        addBlock(ModBlocks.KINETIC_PORT, "Kinetic Port");
        addItem(ModItems.KINETIC_TUNNEL, "Kinetic Tunnel");

        add("tunnel_kind.tinytunnels_create.kinetic", "Kinetic Tunnel");
        add("tunnel_kind.tinytunnels_create.kinetic.in", "In");
        add("tunnel_kind.tinytunnels_create.kinetic.out", "Out");
        add("message.tinytunnels_create.kinetic_tunnel.in", "Rotation in from the machine's %s side");
        add("message.tinytunnels_create.kinetic_tunnel.out", "Rotation out to the machine's %s side");

        add("tinytunnels_create.face.down", "bottom");
        add("tinytunnels_create.face.up", "top");
        add("tinytunnels_create.face.north", "north");
        add("tinytunnels_create.face.south", "south");
        add("tinytunnels_create.face.west", "west");
        add("tinytunnels_create.face.east", "east");

        add("jade.tinytunnels_create.links_to", "Links to the %s side: %s");
        add("jade.tinytunnels_create.not_linked", "Not linked");
        add("jade.tinytunnels_create.blocked_same_source", "Blocked: same source on both sides");
        add("jade.tinytunnels_create.overstressed", "overstressed");
        add("config.jade.plugin_tinytunnels_create.kinetic_tunnel", "Kinetic tunnel");
        add("config.jade.plugin_tinytunnels_create.kinetic_port", "Kinetic port");
    }
}
