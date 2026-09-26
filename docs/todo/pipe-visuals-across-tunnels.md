# Pipe Visuals Across Tunnels

Added 2026-09-26.

**Problem:** with pipes on a machine face or a tunnel, it's hard to tell whether a pipe is extracting. Depending on where the tunnel is, Pipez's extract flange doesn't always show up.

**Likely cause (to verify):** our capability providers return `null` on the **client** (`level.isClientSide()`), because all tunnel logic is server-only. If Pipez decides whether to draw a connection or flange by querying capabilities on the client, it sees "nothing here" on machine faces and tunnels. It would then rely only on whatever connection state the server syncs, which may not include the flange.

**Next step:** check how Pipez 26.1.2 decides connection rendering: a client-side capability lookup, or state synced from the server.

**Possible fix if it's client-side:** on the client, return an empty handler for machine faces that have a tunnel, and for a tunnel's inward side, so pipes see "something connectable". This needs the tunnel map on the client. The machine's per-face booleans from `machine-face-labels.md` provide it, and a tunnel already knows its inward side from its position.

**If it's server-synced:** it's probably Pipez's own rendering. Note it and move on.

## Findings (2026-09-26)

**Confirmed cause:** Pipez (26.1 branch) renders the extract flange from its own synced `extractingSides` flags, not from capabilities. But `PipeTileEntity.markPipesDirty` runs on **both** client and server, for example after a wrench click. For every extracting side it checks `canConnectTo`, which is a capability lookup, and **clears the extract flag if nothing is there**. Our providers answered `null` on the client, so the client dropped the flag and the flange vanished, while the server kept extracting. Connections themselves are fine because Pipez stores them in its block state, which syncs from the server.

**Fix (implemented 2026-09-26, pending in-game test):**
- On the client, machine faces with a tunnel, and a tunnel's inward side, answer an **empty handler** (present, moves nothing).
- The client learns which sides those are from block state: the machine's per-face tunnel flags (see `machine-face-labels.md`), and a new `inward` property on the tunnel wall.

