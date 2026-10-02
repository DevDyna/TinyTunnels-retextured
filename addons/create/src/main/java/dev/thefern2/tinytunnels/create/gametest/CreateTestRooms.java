package dev.thefern2.tinytunnels.create.gametest;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;

import dev.thefern2.tinytunnels.api.PlaceResult;
import dev.thefern2.tinytunnels.api.RoomView;
import dev.thefern2.tinytunnels.api.TunnelService;
import dev.thefern2.tinytunnels.create.kinetic.KineticMode;
import dev.thefern2.tinytunnels.create.port.KineticPortBlock;
import dev.thefern2.tinytunnels.create.port.KineticPortBlockEntity;
import dev.thefern2.tinytunnels.create.registry.ModBlocks;
import dev.thefern2.tinytunnels.create.registry.ModTunnelKinds;
import dev.thefern2.tinytunnels.machine.MachineHost;
import dev.thefern2.tinytunnels.machine.MachineSize;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Builds machines, rooms, kinetic tunnels and ports for the addon's GameTests. Tunnels go through
 * {@link TunnelService}, as the addon's own item does. Placing a machine uses core's machine block directly: the
 * API has no call for that, and the addon itself never places machines.
 */
final class CreateTestRooms {
    static final TunnelService SERVICE = TunnelService.get();

    /** A machine placed and bound like a player placing it; returns its room. */
    static RoomView placeMachine(GameTestHelper helper, BlockPos relative) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(relative);
        level.setBlock(pos, dev.thefern2.tinytunnels.registry.ModBlocks.MACHINES.get(MachineSize.NORMAL).get().defaultBlockState(), Block.UPDATE_ALL);
        ((MachineHost) level.getBlockEntity(pos)).bindOnPlace(level, MachineSize.NORMAL);
        return room(helper, relative);
    }

    /** The room of the machine at {@code relative}, as it is now. */
    static RoomView room(GameTestHelper helper, BlockPos relative) {
        return SERVICE.machineAt(helper.getLevel(), helper.absolutePos(relative)).flatMap(machine -> machine.hostedRoom()).orElseThrow();
    }

    static ServerLevel rooms(GameTestHelper helper) {
        return Objects.requireNonNull(SERVICE.roomLevel(helper.getLevel().getServer()), "room dimension");
    }

    /**
     * A kinetic tunnel on the middle of the room's wall on {@code side}, linked to machine face {@code face}: placed
     * through the service (it takes the first free face), then moved to {@code face}. Returns the wall.
     */
    static BlockPos addKineticTunnel(GameTestHelper helper, BlockPos machine, Direction face, Direction side, KineticMode mode) {
        RoomView room = room(helper, machine);
        BlockPos wall = room.shape().wallCenter(side);
        PlaceResult result = SERVICE.place(rooms(helper), wall, ModTunnelKinds.KINETIC.get(), mode, null);
        if (!(result instanceof PlaceResult.Placed placed)) throw new IllegalStateException("kinetic tunnel not placed: " + result);
        if (placed.face() != face && !SERVICE.move(room(helper, machine), placed.face(), face)) {
            throw new IllegalStateException("couldn't move the kinetic tunnel from " + placed.face() + " to " + face);
        }
        return wall;
    }

    static void removeKineticTunnel(GameTestHelper helper, BlockPos machine, Direction face) {
        if (!SERVICE.remove(room(helper, machine), face, null)) throw new IllegalStateException("no tunnel on " + face);
    }

    /** A kinetic port against the machine at {@code machine} (relative), on {@code face}. Returns its relative position. */
    static BlockPos port(GameTestHelper helper, BlockPos machine, Direction face) {
        BlockPos port = machine.relative(face);
        helper.setBlock(port, ModBlocks.KINETIC_PORT.get().defaultBlockState().setValue(KineticPortBlock.FACING, face));
        return port;
    }

    static KineticPortBlockEntity portEntity(GameTestHelper helper, BlockPos relative) {
        return (KineticPortBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(relative));
    }

    /** A creative motor inside the room, next to the wall and pushing into it. */
    static BlockPos pushInto(GameTestHelper helper, RoomView room, BlockPos wall, int rpm) {
        Direction inward = Objects.requireNonNull(room.shape().inwardNormal(wall), "inward");
        BlockPos pos = wall.relative(inward);
        motor(rooms(helper), pos, inward.getOpposite(), rpm);
        return pos;
    }

    static void motor(Level level, BlockPos pos, Direction facing, int rpm) {
        level.setBlock(pos, block("creative_motor").defaultBlockState().setValue(DirectionalKineticBlock.FACING, facing), Block.UPDATE_ALL);
        ((CreativeMotorBlockEntity) level.getBlockEntity(pos)).generatedSpeed.setValue(rpm);
    }

    static BlockState shaft(Direction.Axis axis) {
        return block("shaft").defaultBlockState().setValue(BlockStateProperties.AXIS, axis);
    }

    static Block block(String createId) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create", createId));
    }

    static float speed(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof KineticBlockEntity kinetic ? kinetic.getSpeed() : 0;
    }

    /** Null-safe equality assert; {@code assertValueEqual} throws a NullPointerException on a null actual. */
    static void assertEquals(GameTestHelper helper, @Nullable Object actual, @Nullable Object expected, String what) {
        helper.assertTrue(Objects.equals(actual, expected), what + ": expected " + expected + ", got " + actual);
    }

    /** Right-clicks a block the way a player does: the block with the item, then empty-handed, then the item's own use. */
    static void click(GameTestHelper helper, Level level, BlockPos pos, Player player) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        BlockState state = level.getBlockState(pos);
        ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);
        ItemInteractionResult result = state.useItemOn(stack, level, player, InteractionHand.MAIN_HAND, hit);
        if (result.consumesAction()) return;
        if (result == ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION && state.useWithoutItem(level, player, hit).consumesAction()) return;
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
    }

    /** Right-clicks a tunnel wall with core's Tunnel Wrench through the real click event, sneaking or not. */
    static void wrenchClick(GameTestHelper helper, BlockPos wall, boolean sneak) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(dev.thefern2.tinytunnels.registry.ModItems.TUNNEL_WRENCH.get()));
        player.setShiftKeyDown(sneak);
        NeoForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, wall,
                new BlockHitResult(Vec3.atCenterOf(wall), Direction.UP, wall, false)));
    }

    private CreateTestRooms() {}
}
