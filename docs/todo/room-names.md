# Room Names

Added 2026-09-26.

**Problem:** rooms are only known by UUID, which is hard to read and remember.

**Plan:**
- Add `Optional<String> name` to `Room` (codec field) and `RoomData.rename`.
- **Ways to set it:**
  - **Anvil (recommended first):** renaming a machine item in an anvil names its room when placed. It needs no extra UI and uses vanilla `CUSTOM_NAME`.
  - **Command:** `/tinytunnels rename <name>` from inside the room, or while looking at its machine.
  - **UI (later):** a small screen on sneak-use of the Shrinker on a machine.
- **Where to show it:**
  - the machine item tooltip (instead of "Room xxxxxxxx")
  - the action bar on entering
  - `rooms` / `tickets` output
  - Jade
- **Permissions:** probably anyone who can enter the room, until ownership exists.
