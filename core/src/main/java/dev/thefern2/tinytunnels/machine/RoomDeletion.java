package dev.thefern2.tinytunnels.machine;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.thefern2.tinytunnels.Config;
import dev.thefern2.tinytunnels.loading.RoomTickets;
import dev.thefern2.tinytunnels.room.Room;
import dev.thefern2.tinytunnels.room.RoomData;
import dev.thefern2.tinytunnels.room.RoomDimension;
import dev.thefern2.tinytunnels.room.RoomGeometry;
import dev.thefern2.tinytunnels.room.RoomTunnel;
import dev.thefern2.tinytunnels.teleport.RoomTeleporter;
import dev.thefern2.tinytunnels.tunnel.TunnelChanges;
import dev.thefern2.tinytunnels.wall.ShellProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Deletes a room for good (machine management M3): the machine block, the room's blocks and walls, its entities
 * and its {@link RoomData} entry, freeing its grid slot. Players inside are sent out. By default the contents come
 * back to the player: blocks as silk touch would drop them, container contents, tunnel items and buffers, nested
 * machines with their rooms intact. With {@link Config#deletingRoomDestroysContents} (or in creative) they're
 * destroyed instead, rooms nested inside too. Outside creative an unbound machine comes back either way.
 */
public final class RoomDeletion {
    private static final int MAX_NESTING = 32;
    private static final int CLEAR = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    /**
     * Deletes the room of the machine at {@code pos}. False if that machine isn't the live host of a room, or if
     * the room is still loading (the player is told).
     */
    public static boolean delete(ServerLevel level, BlockPos pos, ServerPlayer player) {
        if (!(level.getBlockEntity(pos) instanceof MachineHost machine)) return false;
        Room room = machine.hostedRoom().orElse(null);
        ServerLevel rooms = RoomDimension.getRoomLevel(level.getServer());
        if (room == null || rooms == null) return false;
        boolean keep = keepsContents(player);
        if (!entitiesLoaded(rooms, room, !keep, 0)) {
            player.displayClientMessage(Component.translatable("message.tinytunnels.machine.room_loading"), true);
            return false;
        }
        BlockState machineState = level.getBlockState(pos);
        // The machine first, without its loot: its removal event still sees the room, and it lets go of the room.
        level.destroyBlock(pos, false, player);
        List<ItemStack> items = new ArrayList<>();
        clear(rooms, room, keep ? player : null, items, 0);
        if (!player.getAbilities().instabuild) items.add(new ItemStack(machineState.getBlock()));
        items.forEach(stack -> give(player, stack));
        return true;
    }

    /** Whether deleting a room now gives its contents back to {@code player}. */
    public static boolean keepsContents(Player player) {
        return !player.getAbilities().instabuild && !Config.deletingRoomDestroysContents();
    }

    /**
     * Empties and forgets {@code room}. With a player, everything inside goes into {@code items} (tunnel buffers
     * straight to the player); without one it's destroyed, and so are the rooms of machines nested inside.
     */
    private static void clear(ServerLevel rooms, Room room, @Nullable ServerPlayer player, List<ItemStack> items, int depth) {
        RoomGeometry geometry = room.geometry();
        rooms.getChunk(geometry.chunk().x, geometry.chunk().z);

        // Tunnels through TunnelChanges, so each kind cleans up (redstone links, an addon's network) and empties its buffer.
        for (Direction face : List.copyOf(room.faces().keySet())) {
            RoomTunnel<?> tunnel = room.at(face);
            if (player != null && tunnel != null) items.add(tunnelItem(tunnel));
            TunnelChanges.remove(rooms, room.id(), face, player);
        }

        ItemStack silkTouch = silkTouchPickaxe(rooms);
        List<Room> nested = new ArrayList<>();
        ShellProtection.edit(() -> {
            for (BlockPos pos : BlockPos.betweenClosed(geometry.min(), geometry.max())) {
                BlockState state = rooms.getBlockState(pos);
                if (state.isAir()) continue;
                if (geometry.isInterior(pos)) {
                    BlockEntity entity = rooms.getBlockEntity(pos);
                    if (player != null) {
                        items.addAll(Block.getDrops(state, rooms, pos, entity, player, silkTouch));
                    } else if (entity instanceof MachineHost machine) {
                        machine.hostedRoom().ifPresent(nested::add);
                    }
                }
                // No neighbour updates: nothing pops off or falls while the room is taken apart. Containers still
                // spill their contents here, and the entity sweep below picks them up.
                rooms.setBlock(pos, Blocks.AIR.defaultBlockState(), CLEAR);
            }
        });

        AABB box = AABB.encapsulatingFullBlocks(geometry.min(), geometry.max()).inflate(1);
        for (Entity entity : rooms.getEntitiesOfClass(Entity.class, box, entity -> !(entity instanceof Player))) {
            if (player != null) items.addAll(entityItems(entity));
            entity.discard();
        }

        if (depth + 1 < MAX_NESTING) nested.forEach(inner -> clear(rooms, inner, null, items, depth + 1));

        RoomTickets.release(rooms.getServer(), room);
        RoomData.get(rooms.getServer()).delete(room.id());
        // After the room is forgotten, so their way out skips it.
        for (ServerPlayer inside : List.copyOf(rooms.players())) {
            if (inside.chunkPosition().equals(geometry.chunk())) RoomTeleporter.exit(inside);
        }
    }

    /**
     * Whether the room's entities are loaded, so the sweep finds them all (with {@code nested}, the rooms of
     * machines inside too). A room is loaded while its machine's chunk ticks, but entities load a few ticks after
     * the chunk; deleting before that would leave them behind in a grid slot that gets reused.
     */
    private static boolean entitiesLoaded(ServerLevel rooms, Room room, boolean nested, int depth) {
        RoomGeometry geometry = room.geometry();
        if (!rooms.areEntitiesLoaded(geometry.chunk().toLong())) return false;
        if (!nested || depth + 1 >= MAX_NESTING) return true;
        for (BlockEntity entity : rooms.getChunk(geometry.chunk().x, geometry.chunk().z).getBlockEntities().values()) {
            if (!geometry.isInterior(entity.getBlockPos()) || !(entity instanceof MachineHost machine)) continue;
            Room inner = machine.hostedRoom().orElse(null);
            if (inner != null && !entitiesLoaded(rooms, inner, true, depth + 1)) return false;
        }
        return true;
    }

    private static <D> ItemStack tunnelItem(RoomTunnel<D> tunnel) {
        return tunnel.kind().item(tunnel.data());
    }

    static ItemStack silkTouchPickaxe(ServerLevel level) {
        ItemStack tool = new ItemStack(Items.NETHERITE_PICKAXE);
        tool.enchant(level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.SILK_TOUCH), 1);
        return tool;
    }

    /** What an entity left in the room gives back. Mobs and vehicles give nothing. */
    private static List<ItemStack> entityItems(Entity entity) {
        List<ItemStack> items = new ArrayList<>();
        switch (entity) {
            case ItemEntity item -> items.add(item.getItem().copy());
            case ItemFrame frame -> {
                items.add(new ItemStack(frame instanceof GlowItemFrame ? Items.GLOW_ITEM_FRAME : Items.ITEM_FRAME));
                items.add(frame.getItem().copy());
            }
            case Painting painting -> items.add(new ItemStack(Items.PAINTING));
            case ArmorStand stand -> {
                items.add(new ItemStack(Items.ARMOR_STAND));
                for (EquipmentSlot slot : EquipmentSlot.values()) items.add(stand.getItemBySlot(slot).copy());
            }
            default -> {}
        }
        return items;
    }

    private static void give(Player player, ItemStack stack) {
        if (stack.isEmpty()) return;
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    private RoomDeletion() {}
}
