# Debug Output and Command Split

Added 2026-09-26.

**Today (2026-10-02):** the `[TT-DEBUG]` log lines, `ShutdownWatchdog` and `/tinytunnels debug cap` are removed. What's left is the grey load-state line in `/tinytunnels debug tickets` and the other `debug` subcommands.

Regular players and server admins don't need it.

**Plan:**
- Add a `debug` flag to the server config (`tinytunnels-server.toml`, default `false`).
- Gate the `TODO(debug)` logs and detail lines behind it, or delete the logs, since follow-the-host and occupancy are verified.
- Split commands by audience:
  - `/tinytunnels rooms`, `/tinytunnels room <id>`: normal admin info (permission 2).
  - `/tinytunnels give <id>`: recovery for a lost machine (permission 2). Useful beyond debugging.
  - `/tinytunnels debug build …` and verbose `debug tickets`: registered only when `debug = true`.
