package dev.thefern2.tinytunnels.room;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.world.phys.Vec3;

/**
 * Where a player stood, and which way they faced, when they last left a room with the Shrinker.
 * The next entry starts here, since it was a spot someone could stand in.
 */
public record EntryPoint(Vec3 pos, float yRot, float xRot) {
    public static final Codec<EntryPoint> CODEC = RecordCodecBuilder.create(i -> i.group(
            Vec3.CODEC.fieldOf("pos").forGetter(EntryPoint::pos),
            Codec.FLOAT.fieldOf("y_rot").forGetter(EntryPoint::yRot),
            Codec.FLOAT.fieldOf("x_rot").forGetter(EntryPoint::xRot)
    ).apply(i, EntryPoint::new));
}
