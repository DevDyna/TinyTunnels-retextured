# Debug Output and Command Split

Added 2026-09-26.

**Today:** debug-only code is marked `TODO(debug)`:
- `[TT-DEBUG]` log lines in `MachineBlockEntity` and `RoomTickets` (ticket and occupancy changes)
- the grey load-state line in `/tinytunnels debug tickets`

Regular players and server admins don't need it.

**Plan:**
- Add a `debug` flag to the server config (`tinytunnels-server.toml`, default `false`).
- Gate the `TODO(debug)` logs and detail lines behind it, or delete the logs, since follow-the-host and occupancy are verified.
- Split commands by audience:
  - `/tinytunnels rooms`, `/tinytunnels room <id>`: normal admin info (permission 2).
  - `/tinytunnels give <id>`: recovery for a lost machine (permission 2). Useful beyond debugging.
  - `/tinytunnels debug build …` and verbose `debug tickets`: registered only when `debug = true`.
