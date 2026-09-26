# Better Tunnel Removal

Added 2026-09-26.

**Today:** sneak + right-click a tunnel with **both hands empty**. It works, but clearing both hands is awkward.

**Options:**
- **Sneak + right-click with a Tunnel item** in hand (recommended now). You're likely holding one while arranging tunnels anyway.
- **Sneak + right-click with anything**, handled in `useItemOn` so it runs before the held item. Simplest, but it can clash with items that have their own sneak action.
- **A Tunnel Wrench:** sneak-use removes, plain use cycles. It also fits later per-tunnel settings (typed tunnels, redstone in/out mode).

**Leaning:** the Tunnel-in-hand option now, and the wrench once tunnels get settings.

## Decision and implementation (2026-09-26, pending in-game test)

**A wrench, like Create's.** The **Tunnel Wrench** item (stacks to 1, in the creative tab, tagged `c:tools/wrench`):
- right-click a tunnel: cycle to the next free machine face
- sneak + right-click a tunnel: remove it; the Tunnel item goes to your inventory (dropped if it's full, none in creative)

**Details:**
- **Any item tagged `c:tools/wrench`** (other mods' wrenches) does the same.
- **Handled in `TunnelWrenching`** via `PlayerInteractEvent.RightClickBlock`, because vanilla skips a block's own use handler when sneaking with an item, and so other wrenches don't act first.
- **Empty-hand cycling and removal are gone.** Any other click on a tunnel behaves like a plain block. This also fixes accidentally cycling a tunnel while placing a block against it.
- **Phase 7 redstone:** the wrench is the natural place for per-tunnel settings, if a setting is chosen over a separate redstone tunnel.

