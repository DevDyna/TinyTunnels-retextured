package dev.thefern2.tinytunnels.api;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

/**
 * The overlay a tunnel draws on its machine face: one texture per face, showing the face letter. The texture for
 * a face is {@code texturePrefix + "_" + face}, for example {@code tinytunnels:block/machine_port_transfer_north}.
 * A kind with a second look (redstone while lit) returns a second {@code FaceLook} with its own prefix.
 *
 * <p>The textures must be under {@code <namespace>:textures/block/}, which the block atlas stitches for every
 * namespace: shipping the PNGs is enough.
 *
 * @param texturePrefix a block texture location without the face suffix
 */
public record FaceLook(ResourceLocation texturePrefix) {
    /** The texture for one machine face. */
    public ResourceLocation texture(Direction face) {
        return texturePrefix.withSuffix("_" + face.getSerializedName());
    }
}
