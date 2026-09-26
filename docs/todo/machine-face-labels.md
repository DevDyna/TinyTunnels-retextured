# Machine Face Labels

Added 2026-09-26.

**Problem:** outside, there's no way to tell which machine face has a tunnel, or which letter it maps to. Inside, tunnels show a letter; the machine shows nothing.

**Options:**
- **Blockstate overlay (recommended):** give the machine six boolean properties, one per face, set to true when that face has a tunnel. A multipart model adds a port-with-letter overlay on those faces, matching the tunnel inside. It's plain models, so it has no render cost and works with shaders.
  - Keep the properties in sync whenever tunnels change (placed, cycled, removed) and when the machine is placed.
- **Jade tooltip:** looking at a machine lists "Tunnels: U, N, E"; looking at a tunnel shows "→ machine top". It's cheap and complements the overlay. Jade is already a test mod.
- **Block entity renderer:** draws labels dynamically. More flexible, but it adds per-frame cost; not needed for static labels.

**Related:** `pipe-visuals-across-tunnels.md`. Six per-face booleans also give the client the tunnel map, which that item may need.

## Implemented (2026-09-26, pending in-game test)

- The machine has six boolean block state properties (`down`, `up`, `north`, `south`, `west`, `east`), true when that face has a tunnel.
- A multipart model adds `models/block/machine_port_<face>.json`: a flat overlay 0.01 outside the face, with a transparent texture, so it renders as cutout automatically in 26.x and never z-fights. It shows the same colour and letter as the tunnel inside.
- The flags sync from room data at the end of the tick whenever tunnels change (placed, cycled, removed), when the machine is placed, and when it loads. That goes through `CapabilityUpdates.roomChanged`.
- The Jade tooltip is still open (Phase 7).

