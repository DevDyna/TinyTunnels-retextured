package dev.thefern2.tinytunnels.registry;

import java.util.function.Supplier;

import dev.thefern2.tinytunnels.TinyTunnels;
import dev.thefern2.tinytunnels.teleport.ReturnStack;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, TinyTunnels.MODID);

    /** Not copied on death: dying inside a room respawns normally and forgets the way back. */
    public static final Supplier<AttachmentType<ReturnStack>> RETURN_STACK = ATTACHMENTS.register("return_stack",
            () -> AttachmentType.builder(() -> ReturnStack.EMPTY).serialize(ReturnStack.MAP_CODEC.codec()).build());

    private ModAttachments() {}
}
