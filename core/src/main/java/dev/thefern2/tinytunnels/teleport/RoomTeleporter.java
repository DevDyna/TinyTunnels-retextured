package dev.thefern2.tinytunnels.teleport;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import dev.thefern2.tinytunnels.Config;
import dev.thefern2.tinytunnels.TinyTunnels;
import dev.thefern2.tinytunnels.registry.ModAttachments;
import dev.thefern2.tinytunnels.room.EntryPoint;
import dev.thefern2.tinytunnels.room.Room;
import dev.thefern2.tinytunnels.room.RoomBuilder;
import dev.thefern2.tinytunnels.room.RoomData;
import dev.thefern2.tinytunnels.room.RoomDimension;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Moves players into and out of rooms. Entering optionally plays a short shrink animation first,
 * using a transient scale modifier so nothing is left behind if the player disconnects mid-way.
 */
@EventBusSubscriber(modid = TinyTunnels.MODID)
public final class RoomTeleporter {
    private static final ResourceLocation SHRINK_MODIFIER = TinyTunnels.id("shrink");
    private static final double MIN_SCALE = 0.0625;

    private static final Map<UUID, PendingEnter> PENDING = new HashMap<>();

    private static final class PendingEnter {
        final UUID roomId;
        final int totalTicks;
        int elapsed;

        PendingEnter(UUID roomId, int totalTicks) {
            this.roomId = roomId;
            this.totalTicks = totalTicks;
        }
    }

    public static boolean isEntering(ServerPlayer player) {
        return PENDING.containsKey(player.getUUID());
    }

    public static void enter(ServerPlayer player, Room room) {
        if (isEntering(player)) return;
        if (Config.SHRINK_ANIMATION.getAsBoolean()) {
            PENDING.put(player.getUUID(), new PendingEnter(room.id(), Config.SHRINK_TICKS.getAsInt()));
        } else {
            teleportIn(player, room.id());
        }
    }

    /** Returns to where the player last entered from. With nowhere to go back to, sends them to world spawn. */
    public static void exit(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        // Where they stand now is a spot someone fits; the next entry to this room starts there.
        RoomEntry.rememberExit(player.serverLevel(), player.position(), player.getYRot(), player.getXRot());
        ReturnStack stack = player.getData(ModAttachments.RETURN_STACK);
        ReturnPoint point = stack.peek().orElse(null);
        stack = stack.pop();
        // A point inside a room that's since been deleted leads into empty space: go further back.
        while (point != null && inDeletedRoom(server, point)) {
            point = stack.peek().orElse(null);
            stack = stack.pop();
        }
        player.setData(ModAttachments.RETURN_STACK, stack);

        ServerLevel target = point == null ? null : server.getLevel(point.dimension());
        if (point != null && target != null) {
            teleport(player, target, point.pos(), point.yRot(), point.xRot());
            return;
        }

        // 1.21.1 has one world spawn, always in the overworld.
        ServerLevel overworld = server.overworld();
        teleport(player, overworld, overworld.getSharedSpawnPos().getBottomCenter(), overworld.getSharedSpawnAngle(), 0);
        player.displayClientMessage(Component.translatable("message.tinytunnels.exit.no_return"), true);
    }

    private static boolean inDeletedRoom(MinecraftServer server, ReturnPoint point) {
        return point.dimension().equals(RoomDimension.key(server))
                && RoomData.get(server).byChunk(new ChunkPos(BlockPos.containing(point.pos()))).isEmpty();
    }

    private static void teleportIn(ServerPlayer player, UUID roomId) {
        MinecraftServer server = player.getServer();
        Room room = RoomData.get(server).room(roomId).orElse(null);
        ServerLevel rooms = RoomDimension.getRoomLevel(server);
        if (room == null || rooms == null) {
            player.displayClientMessage(Component.translatable("message.tinytunnels.enter.no_room"), true);
            return;
        }

        // Cheap repair: puts back any wall or tunnel that went missing.
        RoomBuilder.build(rooms, room);

        EntryPoint entry = RoomEntry.find(rooms, room, player.getDimensions(Pose.STANDING), player.getYRot(), player.getXRot());
        ReturnStack stack = player.getData(ModAttachments.RETURN_STACK);
        player.setData(ModAttachments.RETURN_STACK, stack.push(ReturnPoint.of(player)));
        if (!teleport(player, rooms, entry.pos(), entry.yRot(), entry.xRot())) {
            player.setData(ModAttachments.RETURN_STACK, stack);
        }
    }

    /** The only place players are moved between levels. Returns false if the teleport was refused. */
    public static boolean teleport(ServerPlayer player, ServerLevel level, Vec3 pos, float yRot, float xRot) {
        return player.teleportTo(level, pos.x, pos.y, pos.z, Set.of(), yRot, xRot);
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) return;
        Iterator<Map.Entry<UUID, PendingEnter>> it = PENDING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, PendingEnter> entry = it.next();
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null || !player.isAlive()) {
                it.remove();
                continue;
            }
            PendingEnter pending = entry.getValue();
            pending.elapsed++;
            double progress = Math.min(1.0, (double) pending.elapsed / pending.totalTicks);
            setScale(player, 1.0 + (MIN_SCALE - 1.0) * progress);
            if (pending.elapsed >= pending.totalTicks) {
                it.remove();
                clearScale(player);
                teleportIn(player, pending.roomId);
            }
        }
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (PENDING.remove(event.getEntity().getUUID()) != null && event.getEntity() instanceof ServerPlayer player) {
            clearScale(player);
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        PENDING.clear();
    }

    private static void setScale(ServerPlayer player, double scale) {
        AttributeInstance attribute = player.getAttribute(Attributes.SCALE);
        if (attribute == null) return;
        attribute.removeModifier(SHRINK_MODIFIER);
        attribute.addTransientModifier(new AttributeModifier(SHRINK_MODIFIER, scale - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    private static void clearScale(ServerPlayer player) {
        AttributeInstance attribute = player.getAttribute(Attributes.SCALE);
        if (attribute != null) attribute.removeModifier(SHRINK_MODIFIER);
    }

    private RoomTeleporter() {}
}
