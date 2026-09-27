package dev.thefern2.tinytunnels.command;

import java.util.Arrays;
import java.util.UUID;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import dev.thefern2.tinytunnels.TinyTunnels;
import dev.thefern2.tinytunnels.loading.RoomTickets;
import dev.thefern2.tinytunnels.machine.MachineHost;
import dev.thefern2.tinytunnels.machine.MachineSize;
import dev.thefern2.tinytunnels.registry.ModDataComponents;
import dev.thefern2.tinytunnels.registry.ModItems;
import dev.thefern2.tinytunnels.room.Room;
import dev.thefern2.tinytunnels.room.RoomData;
import dev.thefern2.tinytunnels.room.RoomBuilder;
import dev.thefern2.tinytunnels.room.RoomDimension;
import dev.thefern2.tinytunnels.room.RoomGeometry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = TinyTunnels.MODID)
public final class TinyTunnelsCommand {
    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(TinyTunnels.MODID)
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("debug")
                        .then(Commands.literal("build")
                                .then(Commands.argument("gridIndex", IntegerArgumentType.integer(0))
                                        .then(Commands.argument("size", IntegerArgumentType.integer(1, RoomGeometry.MAX_INTERIOR))
                                                .executes(TinyTunnelsCommand::debugBuild))))
                        .then(Commands.literal("rooms").executes(TinyTunnelsCommand::debugRooms))
                        .then(Commands.literal("tickets").executes(TinyTunnelsCommand::debugTickets))
                        .then(Commands.literal("give")
                                .then(Commands.argument("id", UuidArgument.uuid()).executes(TinyTunnelsCommand::debugGive)))
                        .then(Commands.literal("room")
                                .then(Commands.argument("id", UuidArgument.uuid()).executes(TinyTunnelsCommand::debugRoom)))));
    }

    private static int debugBuild(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        int gridIndex = IntegerArgumentType.getInteger(context, "gridIndex");
        int size = IntegerArgumentType.getInteger(context, "size");
        if (size % 2 == 0) {
            source.sendFailure(Component.literal("Room size must be odd (3, 5, ... 13)."));
            return 0;
        }

        ServerLevel rooms = RoomDimension.getRoomLevel(source.getServer());
        if (rooms == null) {
            source.sendFailure(Component.literal("The room dimension isn't loaded."));
            return 0;
        }

        RoomGeometry room = new RoomGeometry(gridIndex, size);
        RoomBuilder.buildNew(rooms, room);
        player.teleport(new TeleportTransition(rooms, room.spawn(), Vec3.ZERO, player.getYRot(), player.getXRot(), TeleportTransition.DO_NOTHING));
        source.sendSuccess(() -> Component.literal("Built room " + gridIndex + " (" + size + "x" + size + ") at " + room.min().toShortString()), true);
        return 1;
    }

    private static int debugRooms(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        var rooms = RoomData.get(source.getServer()).rooms();
        source.sendSuccess(() -> Component.literal(rooms.size() + " room(s)"), false);
        rooms.forEach(room -> source.sendSuccess(() -> describe(room), false));
        return rooms.size();
    }

    private static int debugRoom(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        UUID id = UuidArgument.getUuid(context, "id");
        Room room = RoomData.get(source.getServer()).room(id).orElse(null);
        if (room == null) {
            source.sendFailure(Component.literal("No room " + id));
            return 0;
        }
        source.sendSuccess(() -> describe(room), false);
        room.tunnels().forEach((face, pos) -> source.sendSuccess(() -> Component.literal("  tunnel " + face.getSerializedName() + " -> " + pos.toShortString()), false));
        room.redstone().forEach((face, tunnel) -> source.sendSuccess(() -> Component.literal("  redstone " + face.getSerializedName() + " -> "
                + tunnel.pos().toShortString() + " " + tunnel.mode().getSerializedName() + " power " + tunnel.power()), false));
        return 1;
    }

    private static int debugTickets(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        RoomData data = RoomData.get(source.getServer());
        var active = RoomTickets.active();
        source.sendSuccess(() -> Component.literal(active.size() + " room(s) loaded by their machine"), false);
        active.forEach(id -> data.room(id).ifPresent(room -> {
            source.sendSuccess(() -> describe(room), false);
            source.sendSuccess(() -> Component.literal("    " + loadState(source, room)).withStyle(ChatFormatting.GRAY), false);
        }));
        var occupied = RoomTickets.occupied();
        if (!occupied.isEmpty()) {
            source.sendSuccess(() -> Component.literal(occupied.size() + " machine chunk(s) held because a player is inside"), false);
            occupied.forEach((id, host) -> source.sendSuccess(() -> Component.literal("    room " + id.toString().substring(0, 8)
                    + " -> machine at " + host.pos().toShortString() + " in " + host.dimension().identifier()).withStyle(ChatFormatting.GRAY), false));
        }
        return active.size();
    }

    /** Gives a machine bound to an existing room: for testing duplicates, or recovering a room whose machine was lost. */
    private static int debugGive(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        UUID id = UuidArgument.getUuid(context, "id");
        Room room = RoomData.get(source.getServer()).room(id).orElse(null);
        MachineSize size = room == null ? null : Arrays.stream(MachineSize.values()).filter(s -> s.getInterior() == room.size()).findFirst().orElse(null);
        if (room == null || size == null) {
            source.sendFailure(Component.literal("No room " + id));
            return 0;
        }
        ItemStack stack = new ItemStack(ModItems.MACHINES.get(size).get());
        stack.set(ModDataComponents.ROOM_ID.get(), id);
        if (!player.getInventory().add(stack)) player.drop(stack, false);
        source.sendSuccess(() -> Component.literal("Gave a " + size.getName() + " machine bound to room " + id), false);
        return 1;
    }

    // TODO(debug): reports whether the host chunk is really loaded, to tell a missed unload from a chunk that stayed loaded.
    private static String loadState(CommandSourceStack source, Room room) {
        String hostState = room.host().map(host -> {
            ServerLevel hostLevel = source.getServer().getLevel(host.dimension());
            if (hostLevel == null) return "host level missing";
            int cx = host.pos().getX() >> 4, cz = host.pos().getZ() >> 4;
            boolean chunkLoaded = hostLevel.getChunkSource().hasChunk(cx, cz);
            String machine = chunkLoaded
                    ? (hostLevel.getBlockEntity(host.pos()) instanceof MachineHost m ? "machine present, room=" + m.getRoomId() : "NO machine there")
                    : "-";
            return "host chunk " + cx + "," + cz + " loaded=" + chunkLoaded + " ticking=" + (chunkLoaded && hostLevel.shouldTickBlocksAt(host.pos())) + " (" + machine + ")";
        }).orElse("no host");
        ServerLevel rooms = RoomDimension.getRoomLevel(source.getServer());
        var chunk = room.geometry().chunk();
        boolean roomLoaded = rooms != null && rooms.getChunkSource().hasChunk(chunk.getMinBlockX() >> 4, chunk.getMinBlockZ() >> 4);
        return hostState + " | room chunk loaded=" + roomLoaded;
    }

    private static Component describe(Room room) {
        String host = room.host().map(h -> h.pos().toShortString() + " in " + h.dimension().identifier()).orElse("not placed");
        RoomGeometry geometry = room.geometry();
        // Clicking the ID fills in the give command for that room.
        Component id = Component.literal(room.id().toString()).withStyle(style -> style
                .withColor(ChatFormatting.AQUA)
                .withClickEvent(new ClickEvent.SuggestCommand("/" + TinyTunnels.MODID + " debug give " + room.id()))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to get a machine bound to this room"))));
        return Component.empty().append(id).append("  #" + room.gridIndex() + "  " + room.size() + "x" + room.size()
                + "  at " + geometry.min().toShortString() + "  host: " + host + "  tunnels: " + room.tunnels().size() + "  redstone: " + room.redstone().size());
    }

    private TinyTunnelsCommand() {}
}
