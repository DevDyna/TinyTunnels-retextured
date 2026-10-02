/**
 * The public API of Tiny Tunnels: add a tunnel kind, read rooms and machines, and change tunnels.
 *
 * <p>Only this package and its subpackages are public. Everything else in the Tiny Tunnels jar is internal
 * and may change in any release. Before 1.0 a breaking change here bumps the minor version (0.2 to 0.3);
 * declare the range you support on {@code tinytunnels} in your {@code neoforge.mods.toml}, with
 * {@code ordering="AFTER"}.
 *
 * <p>Start with {@link dev.thefern2.tinytunnels.api.TunnelKind} (register one on
 * {@link dev.thefern2.tinytunnels.api.TunnelKinds#REGISTRY_KEY}) and
 * {@link dev.thefern2.tinytunnels.api.TunnelService#get()}.
 */
package dev.thefern2.tinytunnels.api;
