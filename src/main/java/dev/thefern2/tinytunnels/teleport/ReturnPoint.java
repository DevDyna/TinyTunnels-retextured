package dev.thefern2.tinytunnels.teleport;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Where a player stood before entering a room. Exiting returns here, not to the machine. */
public record ReturnPoint(ResourceKey<Level> dimension, Vec3 pos, float yRot, float xRot) {
    public static final Codec<ReturnPoint> CODEC = RecordCodecBuilder.create(i -> i.group(
            Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(ReturnPoint::dimension),
            Vec3.CODEC.fieldOf("pos").forGetter(ReturnPoint::pos),
            Codec.FLOAT.fieldOf("y_rot").forGetter(ReturnPoint::yRot),
            Codec.FLOAT.fieldOf("x_rot").forGetter(ReturnPoint::xRot)
    ).apply(i, ReturnPoint::new));

    public static ReturnPoint of(ServerPlayer player) {
        return new ReturnPoint(player.level().dimension(), player.position(), player.getYRot(), player.getXRot());
    }
}
