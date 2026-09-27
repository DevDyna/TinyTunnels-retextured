# Room Entry Point

Added and done 2026-09-26.

**Bug:** every entry put the player at the room's floor centre. Once that spot is built on, entering puts you inside blocks and you get stuck.

**Fix** (`teleport/RoomEntry`, `room/EntryPoint`, `Room.entry`):
- **Leaving saves a spot.** Leaving with the Shrinker saves where you stood and which way you faced, as that room's entry point. It's one per room, shared by all players, and stored in `RoomData`.
  - It's only saved if you're inside the room's interior.
  - Leaving by other means (death, `/tp`, logging out) doesn't change it.
- **Entering picks the first spot that works:**
  1. The saved spot, facing the saved way, if a player still fits there.
  2. The floor centre, keeping your own facing.
  3. The clear spot nearest the centre with something to stand on; failing that, any clear spot.
- **GameTests:** `entry_saved_exit_used`, `entry_blocked_saved_exit`, `entry_blocked_centre`, `entry_exit_outside_room`.

**Manual check:**
- [ ] Walk to a corner of a room, face a wall, and leave with the Shrinker. Enter again: you're in that corner, facing that wall.
- [ ] Leave from a spot, then (from another room or in creative) fill that spot with blocks. Enter: you're at the floor centre instead, not stuck.
- [ ] Fill the floor centre with a 2-high pillar and make sure the saved spot is blocked too. Enter: you're standing on or next to the pillar, not inside it.
- [ ] A room you've never left from still puts you at the floor centre.
