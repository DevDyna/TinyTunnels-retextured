package dev.thefern2.tinytunnels.compat;

import net.neoforged.fml.ModList;

/**
 * Which optional mods are loaded. Code outside a mod's compat package checks these flags and only then
 * calls into that package, so no class of an absent mod is ever loaded.
 */
public final class Compat {
    /** Create: the kinetic tunnel and the kinetic machine block entity ({@code compat/create}). */
    public static final boolean CREATE = ModList.get().isLoaded("create");

    private Compat() {}
}
