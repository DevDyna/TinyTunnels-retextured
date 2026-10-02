package dev.thefern2.tinytunnels.compat.create;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;

/** The kinetic machine's shaft face, or none: the value of {@link KineticMachineBlock#KINETIC_FACE}. Until A6. */
public enum KineticFace implements StringRepresentable {
    NONE(null), DOWN(Direction.DOWN), UP(Direction.UP), NORTH(Direction.NORTH), SOUTH(Direction.SOUTH), WEST(Direction.WEST), EAST(Direction.EAST);

    private final @Nullable Direction face;

    KineticFace(@Nullable Direction face) {
        this.face = face;
    }

    public @Nullable Direction face() {
        return face;
    }

    public static KineticFace of(@Nullable Direction face) {
        return face == null ? NONE : values()[face.ordinal() + 1];
    }

    @Override
    public String getSerializedName() {
        return face == null ? "none" : face.getSerializedName();
    }
}
