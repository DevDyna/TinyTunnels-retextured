# Void Safety

Added 2026-09-26.

**Today:** the room dimension is empty void from y = 0 to 256, with room floors at y = 64. A player who ends up outside a room falls below y = -64 and **dies from void damage**, and anything dropped is lost.

**How it can happen now that walls are unbreakable:** spectator mode, other mods' teleports, or mods that remove blocks directly without break events. Shell self-repair covers the last one within a tick.

**Options:**
- **Catch falls:** a player in the room dimension below `FLOOR_Y - 16` is teleported back to the spawn of the room whose column they're in (`RoomData.byChunk`), or the nearest room. With no room, use the normal exit (the return stack).
- **Catch items too:** item entities below the same line are moved into the nearest room, never silently lost.
- Spectators are exempt.

**Priority:** low; edge cases only.
