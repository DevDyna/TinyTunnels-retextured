package dev.thefern2.tinytunnels.machine;

import java.util.EnumMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import dev.thefern2.tinytunnels.TinyTunnels;
import dev.thefern2.tinytunnels.api.FaceLook;
import dev.thefern2.tinytunnels.api.TunnelKinds;
import dev.thefern2.tinytunnels.room.Room;
import dev.thefern2.tinytunnels.room.RoomTunnel;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.model.data.ModelProperty;

/**
 * What each machine face is linked to, as the client sees it: the tunnel kind's id and its {@link FaceLook}.
 * The server derives it from the hosted room ({@link #fromRoom}) and sends it in the machine block entity's
 * update tag; the client uses it for the face overlay (through {@link #PROPERTY}), the capability check on
 * tunnel faces, and Jade. It isn't saved to disk. Immutable and compared by value, so an unchanged look
 * (for example a redstone power change that stays lit) isn't resent.
 *
 * @param faces machine face -> what's on it; faces without a tunnel are absent
 */
public record MachineFaces(Map<Direction, Face> faces) {
    /**
     * One face.
     *
     * @param kind the tunnel kind's registry id
     * @param look the overlay, or null to draw nothing (a kind that isn't registered)
     */
    public record Face(ResourceLocation kind, @Nullable FaceLook look) {}

    public static final MachineFaces NONE = new MachineFaces(Map.of());

    /** The model data key the machine block entity puts its faces under, for the overlay model. */
    public static final ModelProperty<MachineFaces> PROPERTY = new ModelProperty<>();

    // Core's kind ids and redstone looks, for client checks and GameTests (the registered kinds are the source).
    public static final ResourceLocation TRANSFER = id("transfer");
    public static final ResourceLocation REDSTONE = id("redstone");
    public static final ResourceLocation KINETIC = id("kinetic");

    public static final FaceLook REDSTONE_LOOK = look("machine_port_redstone");
    public static final FaceLook REDSTONE_LIT_LOOK = look("machine_port_redstone_on");

    private static final String TAG = "faces";

    public MachineFaces {
        faces = faces.isEmpty() ? Map.of() : Map.copyOf(new EnumMap<>(faces));
    }

    /** The faces of a room's tunnels, as the machine hosting it shows them. */
    public static MachineFaces fromRoom(Room room) {
        Map<Direction, Face> faces = new EnumMap<>(Direction.class);
        room.faces().forEach((face, tunnel) -> faces.put(face, face(tunnel)));
        return faces.isEmpty() ? NONE : new MachineFaces(faces);
    }

    private static <D> Face face(RoomTunnel<D> tunnel) {
        return new Face(TunnelKinds.REGISTRY.getKey(tunnel.kind()), tunnel.kind().faceLook(tunnel.data()));
    }

    public @Nullable Face at(Direction face) {
        return faces.get(face);
    }

    public @Nullable ResourceLocation kindAt(Direction face) {
        Face entry = faces.get(face);
        return entry == null ? null : entry.kind();
    }

    public @Nullable FaceLook lookAt(Direction face) {
        Face entry = faces.get(face);
        return entry == null ? null : entry.look();
    }

    /** True if {@code face} has a tunnel of the kind {@code kind}. */
    public boolean has(Direction face, ResourceLocation kind) {
        return kind.equals(kindAt(face));
    }

    public boolean isEmpty() {
        return faces.isEmpty();
    }

    /** Writes the faces under {@code faces}: {@code {"north": {"kind": "...", "look": "..."}}}. */
    public void save(CompoundTag tag) {
        CompoundTag all = new CompoundTag();
        faces.forEach((face, entry) -> {
            CompoundTag one = new CompoundTag();
            one.putString("kind", entry.kind().toString());
            if (entry.look() != null) one.putString("look", entry.look().texturePrefix().toString());
            all.put(face.getSerializedName(), one);
        });
        tag.put(TAG, all);
    }

    /** Reads what {@link #save} wrote. Missing or malformed entries are skipped. */
    public static MachineFaces load(CompoundTag tag) {
        if (!tag.contains(TAG, CompoundTag.TAG_COMPOUND)) return NONE;
        CompoundTag all = tag.getCompound(TAG);
        Map<Direction, Face> faces = new EnumMap<>(Direction.class);
        for (Direction face : Direction.values()) {
            if (!all.contains(face.getSerializedName(), CompoundTag.TAG_COMPOUND)) continue;
            CompoundTag one = all.getCompound(face.getSerializedName());
            ResourceLocation kind = ResourceLocation.tryParse(one.getString("kind"));
            if (kind == null) continue;
            ResourceLocation look = one.contains("look") ? ResourceLocation.tryParse(one.getString("look")) : null;
            faces.put(face, new Face(kind, look == null ? null : new FaceLook(look)));
        }
        return faces.isEmpty() ? NONE : new MachineFaces(faces);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(TinyTunnels.MODID, path);
    }

    private static FaceLook look(String name) {
        return new FaceLook(ResourceLocation.fromNamespaceAndPath(TinyTunnels.MODID, "block/" + name));
    }
}
