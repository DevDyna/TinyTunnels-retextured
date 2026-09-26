# Unbreakable Room Walls

Added 2026-09-26. **Done 2026-09-26** (Phase 6a, task "Unbreakable shell").

**Decision:** room walls and tunnel walls can't be broken by anyone, in creative or survival.
- No gameplay needs a hand-broken wall: tunnels come out with sneak-use, admins have `/setblock`, and future resizing would rebuild walls in code.
- If something replaces a wall anyway (commands, mods that set blocks directly), it's put back on the next tick.
- Covers **only the room's own shell**. Blocks built inside a room, vanilla or modded, behave normally.
