package dev.thefern2.tinytunnels.machine;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.thefern2.tinytunnels.TinyTunnels;
import dev.thefern2.tinytunnels.registry.ModDataComponents;
import dev.thefern2.tinytunnels.room.Room;
import dev.thefern2.tinytunnels.room.RoomBuilder;
import dev.thefern2.tinytunnels.room.RoomData;
import dev.thefern2.tinytunnels.room.RoomDimension;
import dev.thefern2.tinytunnels.room.RoomGeometry;
import dev.thefern2.tinytunnels.room.RoomTunnel;
import dev.thefern2.tinytunnels.tunnel.TunnelChanges;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Copies a room into an empty machine (room duplicator D0): hold an empty machine of the same size and sneak +
 * right-click the machine to copy. The new room gets every block inside with its exact state, but no block entity
 * data (chests empty, signs blank), and the same tunnels with empty buffers. Entities and machines nested inside
 * aren't copied. In survival the copy costs what deleting the original would give back, without container contents
 * ({@link RoomDeletion}'s silk touch drops, plus a tunnel item per tunnel), taken only when the inventory has all
 * of it. Blocks survival can't get refuse the copy. Creative copies everything for free.
 */
@EventBusSubscriber(modid = TinyTunnels.MODID)
public final class RoomCopy {
    private static final int CLEAR = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final int MISSING_SHOWN = 5;

    /** What a copy needs: the blocks to place, the items it costs, what refuses it, and the nested machines left out. */
    private record Plan(List<Placed> blocks, List<ItemStack> cost, Set<Block> refused, int nestedMachines) {}

    private record Placed(BlockPos offset, BlockState state) {}

    // Sneaking with an item skips the clicked block's own use, so without this the empty machine would be placed.
    @SubscribeEvent
    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack held = event.getItemStack();
        if (!event.getEntity().isSecondaryUseActive() || !isEmptyMachine(held)) return;
        if (!(event.getLevel().getBlockState(event.getPos()).getBlock() instanceof MachineBlock)) return;

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof ServerPlayer player) {
            copy(level, event.getPos(), player, event.getHand());
        }
    }

    static boolean isEmptyMachine(ItemStack stack) {
        return stack.getItem() instanceof MachineItem && !stack.has(ModDataComponents.ROOM_ID.get());
    }

    /**
     * Copies the room of the machine at {@code pos} into one of the empty machines in the player's {@code hand}, and
     * tells the player what happened. False when nothing was copied.
     */
    public static boolean copy(ServerLevel level, BlockPos pos, ServerPlayer player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        Room source = level.getBlockEntity(pos) instanceof MachineHost machine ? machine.hostedRoom().orElse(null) : null;
        ServerLevel rooms = RoomDimension.getRoomLevel(level.getServer());
        if (source == null || rooms == null) {
            player.displayClientMessage(Component.translatable("message.tinytunnels.enter.no_room"), true);
            return false;
        }
        if (!(held.getItem() instanceof MachineItem item) || ((MachineBlock) item.getBlock()).getSize().getInterior() != source.size()) {
            player.displayClientMessage(Component.translatable("message.tinytunnels.copy.different_size"), true);
            return false;
        }

        boolean free = player.getAbilities().instabuild;
        Plan plan = plan(rooms, source, player, free);
        if (!plan.refused().isEmpty()) {
            player.displayClientMessage(Component.translatable("message.tinytunnels.copy.refused", names(plan.refused())), false);
            return false;
        }
        if (!free) {
            List<ItemStack> missing = missing(player.getInventory(), plan.cost());
            if (!missing.isEmpty()) {
                player.displayClientMessage(Component.translatable("message.tinytunnels.copy.missing", list(missing)), false);
                return false;
            }
            plan.cost().forEach(cost -> take(player.getInventory(), cost));
        }

        Room copy = build(rooms, source, plan);
        ItemStack bound = held.split(1);
        bound.set(ModDataComponents.ROOM_ID.get(), copy.id());
        if (held.isEmpty()) {
            player.setItemInHand(hand, bound);
        } else {
            give(player, bound);
        }
        player.displayClientMessage(plan.nestedMachines() == 0
                ? Component.translatable("message.tinytunnels.copy.done")
                : Component.translatable("message.tinytunnels.copy.done_skipped", plan.nestedMachines()), true);
        return true;
    }

    private static Plan plan(ServerLevel rooms, Room source, Player player, boolean free) {
        RoomGeometry geometry = source.geometry();
        rooms.getChunk(geometry.chunk().x, geometry.chunk().z);
        ItemStack silkTouch = RoomDeletion.silkTouchPickaxe(rooms);
        List<Placed> blocks = new ArrayList<>();
        List<ItemStack> cost = new ArrayList<>();
        Set<Block> refused = new LinkedHashSet<>();
        int nested = 0;
        for (BlockPos pos : BlockPos.betweenClosed(geometry.min(), geometry.max())) {
            if (!geometry.isInterior(pos)) continue;
            BlockState state = rooms.getBlockState(pos);
            if (state.isAir()) continue;
            if (state.getBlock() instanceof MachineBlock) {
                nested++;
                continue;
            }
            blocks.add(new Placed(pos.subtract(geometry.min()), state));
            if (free) continue;
            if (state.getDestroySpeed(rooms, pos) < 0) {
                refused.add(state.getBlock());
                continue;
            }
            // No block entity: the copy has none of its data, so it costs the plain item (an empty shulker box).
            List<ItemStack> drops = Block.getDrops(state, rooms, pos, null, player, silkTouch);
            if (drops.isEmpty() && !isFreeHalf(state) && state.getBlock().asItem() != Items.AIR) refused.add(state.getBlock());
            drops.forEach(drop -> add(cost, drop));
            FluidState fluid = state.getFluidState();
            if (fluid.isSource() && !state.hasProperty(BlockStateProperties.WATERLOGGED)) add(cost, new ItemStack(fluid.getType().getBucket()));
        }
        if (!free) source.faces().values().forEach(tunnel -> add(cost, tunnelItem(tunnel)));
        return new Plan(blocks, cost, refused, nested);
    }

    /** The part of a two-block thing whose other part pays for it: an upper door or plant half, a bed's head. */
    private static boolean isFreeHalf(BlockState state) {
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) return state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER;
        if (state.hasProperty(BlockStateProperties.BED_PART)) return state.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD;
        return false;
    }

    /** A new room of the same size with the plan's blocks at the same places and the source's tunnels on the same faces. */
    private static Room build(ServerLevel rooms, Room source, Plan plan) {
        RoomData data = RoomData.get(rooms.getServer());
        Room copy = data.allocate(source.size());
        RoomBuilder.buildNew(rooms, copy.geometry());
        // No neighbour updates: the states are copied exactly, the way they stand in the original.
        for (Placed placed : plan.blocks()) rooms.setBlock(copy.geometry().min().offset(placed.offset()), placed.state(), CLEAR);
        for (Map.Entry<Direction, RoomTunnel<?>> tunnel : source.faces().entrySet()) {
            TunnelChanges.copyInto(rooms, source, copy.id(), tunnel.getKey(), tunnel.getValue());
        }
        return data.room(copy.id()).orElseThrow();
    }

    private static <D> ItemStack tunnelItem(RoomTunnel<D> tunnel) {
        return tunnel.kind().item(tunnel.data());
    }

    /** Adds {@code stack} to {@code stacks}, merged with an equal item. */
    private static void add(List<ItemStack> stacks, ItemStack stack) {
        if (stack.isEmpty()) return;
        for (ItemStack existing : stacks) {
            if (ItemStack.isSameItemSameComponents(existing, stack)) {
                existing.grow(stack.getCount());
                return;
            }
        }
        stacks.add(stack.copy());
    }

    /**
     * A stack that can pay for {@code cost}: the same item with the same components, or the plain item with none.
     * A shulker box full of items or a named item is never taken for a plain one.
     */
    private static boolean pays(ItemStack stack, ItemStack cost) {
        return ItemStack.isSameItemSameComponents(stack, cost) || (stack.is(cost.getItem()) && stack.getComponentsPatch().isEmpty());
    }

    private static List<ItemStack> payingSlots(Inventory inventory) {
        List<ItemStack> slots = new ArrayList<>(inventory.items);
        slots.addAll(inventory.offhand);
        return slots;
    }

    private static List<ItemStack> missing(Inventory inventory, List<ItemStack> cost) {
        List<ItemStack> missing = new ArrayList<>();
        for (ItemStack needed : cost) {
            int have = 0;
            for (ItemStack stack : payingSlots(inventory)) {
                if (pays(stack, needed)) have += stack.getCount();
            }
            if (have < needed.getCount()) missing.add(needed.copyWithCount(needed.getCount() - have));
        }
        return missing;
    }

    private static void take(Inventory inventory, ItemStack cost) {
        int left = cost.getCount();
        for (ItemStack stack : payingSlots(inventory)) {
            if (left == 0) break;
            if (!pays(stack, cost)) continue;
            int taken = Math.min(left, stack.getCount());
            stack.shrink(taken);
            left -= taken;
        }
        inventory.setChanged();
    }

    /** "12 Oak Planks, 3 Glass", the first few, then "and 4 more". */
    private static Component list(List<ItemStack> stacks) {
        MutableComponent list = Component.empty();
        for (int i = 0; i < Math.min(stacks.size(), MISSING_SHOWN); i++) {
            if (i > 0) list.append(", ");
            list.append(stacks.get(i).getCount() + " ").append(stacks.get(i).getHoverName());
        }
        int more = stacks.size() - MISSING_SHOWN;
        return more > 0 ? Component.translatable("message.tinytunnels.copy.and_more", list, more) : list;
    }

    private static Component names(Set<Block> blocks) {
        MutableComponent names = Component.empty();
        for (Block block : blocks) {
            if (!names.getSiblings().isEmpty()) names.append(", ");
            names.append(block.getName());
        }
        return names;
    }

    private static void give(Player player, ItemStack stack) {
        if (stack.isEmpty()) return;
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    private RoomCopy() {}
}
