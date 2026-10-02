package dev.thefern2.tinytunnels.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import dev.thefern2.tinytunnels.api.FaceLook;
import dev.thefern2.tinytunnels.machine.MachineFaces;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockFaceUV;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * A machine's model: the plain cube, plus the face letter of each tunnel's {@link FaceLook} on its face. The
 * faces come from the block entity's {@link MachineFaces} through {@link MachineFaces#PROPERTY}. The overlay is
 * a full 16x16 quad 0.01 outside the face, culled with the face, the same as the hand-written overlay models
 * it replaces. Quads are baked on first use and kept per texture; a resource reload builds new models.
 */
final class MachineFaceModel extends BakedModelWrapper<BakedModel> {
    private static final FaceBakery BAKERY = new FaceBakery();
    private static final float OUT = 0.01f;

    private final Map<ResourceLocation, BakedQuad> quads = new ConcurrentHashMap<>();

    MachineFaceModel(BakedModel base) {
        super(base);
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData data, @Nullable RenderType renderType) {
        List<BakedQuad> base = super.getQuads(state, side, rand, data, renderType);
        if (side == null || (renderType != null && renderType != RenderType.solid())) return base;
        MachineFaces faces = data.get(MachineFaces.PROPERTY);
        FaceLook look = faces == null ? null : faces.lookAt(side);
        if (look == null) return base;
        List<BakedQuad> all = new ArrayList<>(base.size() + 1);
        all.addAll(base);
        all.add(quads.computeIfAbsent(look.texture(side), texture -> bake(texture, side)));
        return all;
    }

    private static BakedQuad bake(ResourceLocation texture, Direction face) {
        var sprite = Minecraft.getInstance().getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS).getSprite(texture);
        BlockElementFace element = new BlockElementFace(face, -1, "", new BlockFaceUV(new float[] {0, 0, 16, 16}, 0));
        return BAKERY.bakeQuad(from(face), to(face), element, sprite, face, BlockModelRotation.X0_Y0, null, true);
    }

    /** The overlay plane's corners: the whole face, pushed {@link #OUT} past it. */
    private static Vector3f from(Direction face) {
        return switch (face) {
            case DOWN -> new Vector3f(0, -OUT, 0);
            case UP -> new Vector3f(0, 16 + OUT, 0);
            case NORTH -> new Vector3f(0, 0, -OUT);
            case SOUTH -> new Vector3f(0, 0, 16 + OUT);
            case WEST -> new Vector3f(-OUT, 0, 0);
            case EAST -> new Vector3f(16 + OUT, 0, 0);
        };
    }

    private static Vector3f to(Direction face) {
        return switch (face) {
            case DOWN -> new Vector3f(16, -OUT, 16);
            case UP -> new Vector3f(16, 16 + OUT, 16);
            case NORTH -> new Vector3f(16, 16, -OUT);
            case SOUTH -> new Vector3f(16, 16, 16 + OUT);
            case WEST -> new Vector3f(-OUT, 16, 16);
            case EAST -> new Vector3f(16 + OUT, 16, 16);
        };
    }
}
