# Tiny Tunnels Phase 7 In-Game Testing

Manual checks for `tiny-tunnels-phase-7.md`: the redstone tunnel, the Jade tooltip, and recipes. The GameTests already cover the signal logic (26 tests, `./gradlew runGameTestServer`). This pass checks what they can't: looks, clicks, dust shapes, Jade, the recipe book, and save and reload. Tick each box, and note anything odd in **Notes** at the bottom.

## Setup

- Use the **Client** run config. Jade and Pipez are loaded as test mods.
- A **new Creative world** with cheats on. Old worlds load too: machine faces lose their letters for a moment and get them back when the machine loads.
- Get the items from the Tiny Tunnels creative tab, or run these 3 commands:
  ```
  /give @s tinytunnels:normal_machine
  ```
  ```
  /give @s tinytunnels:redstone_tunnel 6
  ```
  ```
  /give @s tinytunnels:tunnel_wrench
  ```
- Place the machine on open ground and enter it with the Shrinker.

**Directions in one line:** **in** carries a signal from outside the machine into the room. **Out** carries it from the room to outside. The wall's letter is the machine side it's linked to, the same as for tunnels (D, U, N, S, W, E). The table of letters and facings is in `tiny-tunnels-phase-6a-testing.md`.

**How redstone ports look.** Item tunnels use one colour per side (D orange, U yellow, N blue, S red, W green, E purple). Redstone ports look the same on every side, and the machine's outside port matches the wall inside:

| | Off (no signal) | On (carrying a signal) |
|---|---|---|
| **In** | dark grey frame, near-black inside, dark letter | bright red port, pale glowing letter |
| **Out** (wall only) | the same, plus yellow corner marks | the same, plus yellow corner marks |

## 1. Placing a redstone tunnel

1. Inside the room, hold a **Redstone Tunnel** and right-click the middle block of a side wall, at floor level plus one, so the block in front of it sits on the floor.
   - [ ] The wall becomes a dark (off) port with a **D** and no corner marks.
   - [ ] The action bar says "Redstone in from the machine's bottom side".
   - [ ] In survival, one item was used.
2. Exit the room and look at the machine's bottom face. To see it, dig a hole under the machine and look up.
   - [ ] The machine's bottom face has a dark (off) redstone port, not the orange item-tunnel port.
3. Place one normal **Tunnel** in the same room.
   - [ ] It takes the next free letter (U). Redstone and item tunnels share the machine's six sides.

## 2. Signal in (outside → room)

Use the tunnel from section 1. If it's on D, wrench it to a side letter first (section 5) so it's easy to reach. The steps below say **N**; use whatever letter you have.

1. Outside, place a **lever** on the ground touching the machine's N face, or a lever directly on that face.
2. Inside, place a **redstone lamp** directly in front of the N wall.
3. Flip the lever on.
   - [ ] Inside, the lamp turns on and the wall's port turns bright red.
   - [ ] Outside, the machine's N port turns bright red too.
4. Flip the lever off.
   - [ ] The lamp turns off (after vanilla's short lamp delay). Both ports go dark again.
5. Replace the lamp with **redstone dust** on the floor in front of the wall, and run a dust line to a lamp.
   - [ ] The dust visibly connects to the wall (the line points into it).
   - [ ] With the lever on, the dust is lit and the lamp is on.
6. Outside, replace the lever with redstone dust running from a lever to the machine's N face.
   - [ ] The dust points into the machine's N face and nowhere else on the machine.

## 3. Signal out (room → outside)

1. Inside, right-click the N wall with an **empty hand**.
   - [ ] A comparator click plays. The action bar says "Redstone out to the machine's north side". Yellow corner marks appear.
   - [ ] Inside, the lamp or dust goes dark.
2. Inside, place a **lever** directly in front of the wall (on the floor, touching it).
3. Outside, place a **redstone lamp** touching the machine's N face.
4. Flip the inside lever on.
   - [ ] Outside, the lamp turns on and the machine's port turns bright red. Inside, the wall's port turns bright red, with yellow corners.
5. Hold any block (stone) and right-click the redstone wall.
   - [ ] The stone is placed against the wall. The direction does **not** change, because only an empty hand flips it.

## 4. Signal strength (analog)

1. With the N tunnel set to **out**: inside, place a redstone block 4 blocks from the wall and a dust line from it to the wall (3 dust).
2. Look at the wall with Jade.
   - [ ] Jade shows "Redstone out, power 13".
3. Outside, replace the lamp with a dust line leading away from the N face.
   - [ ] The dust next to the machine is at power 13 (F3's targeted block shows `power: 13`), then 12, 11 and so on.

## 5. Wrench: cycle and remove

1. Hold the **Tunnel Wrench** and right-click the redstone wall.
   - [ ] The letter moves to the next free side. The action bar names the side and the same direction.
   - [ ] Outside, the old face's redstone port is gone and the new face has one. The old face stops emitting: its lamp or dust goes dark.
2. Sneak and right-click the redstone wall with the wrench.
   - [ ] It becomes a plain wall. In survival, you get a Redstone Tunnel back.
   - [ ] Outside, the redstone port is gone and nothing is powered by that face any more.

## 6. Loops

1. Build this loop with two redstone tunnels:
   - **N** set to in and **S** set to out, both on the same inside wall, next to each other at floor level.
   - Inside: dust in front of both walls, touching each other.
   - Outside: a dust ring on the ground from the machine's S face, round one side, to the N face.
2. Place a redstone block next to the dust at the N face for a second, then remove it.
   - [ ] The signal goes round once or twice, each lap dimmer, then everything goes dark. It doesn't stay on for good.
   - [ ] The game doesn't freeze, and F3 shows normal tick times.
3. Optional, a clock: put a redstone torch inverter in the inside path.
   - [ ] It flickers. The torch may burn out after a while; that's vanilla's torch safety, not a bug. The game stays responsive.

## 7. Save, reload, and moving the machine

1. Set up section 2 again (in, lever outside on, lamp inside on). Stand outside.
2. Save and quit to the title screen, then reopen the world.
   - [ ] Enter the room: the lamp is still on and the wall's port is bright red.
3. Outside, flip the lever off, then quit and reopen again.
   - [ ] Inside, the lamp is off.
4. With the lever on, break the machine (survival pickaxe, or creative) and place it one block over, so the lever still touches the same face. Move the lever if needed.
   - [ ] Enter: the lamp is on again, and both ports are bright red.

### Leaving and entering

Found in testing on 2026-09-26: entering a room turned redstone tunnels back into plain walls. It's fixed, and the `redstone_shell_repair` GameTest now covers it.

1. With a redstone tunnel in place, exit the room with the Shrinker and enter it again. Do it twice.
   - [ ] The redstone tunnel is still there each time, with the same letter, direction and look.
   - [ ] A Redstone Tunnel item can't be used on that block again, because it's no longer a plain wall.

## 8. Pipes don't use redstone faces

1. Run a Pipez item pipe into the machine's redstone face.
   - [ ] The pipe doesn't connect to that face.
2. Run the same pipe into the item tunnel's face (the one from section 1).
   - [ ] It connects as before.

## 9. Jade

Tiny Tunnels adds at most two short lines under Jade's own name and mod lines.

1. Look at the machine from outside.
   - [ ] "Tunnels: D U" lists the item tunnel letters. It's missing when there are none.
   - [ ] "Redstone: N in 15, E out 0" lists the redstone letters with direction and power. It's missing when there are none.
   - [ ] No room-size line: the size is already in the block name.
2. Look at an item tunnel wall inside.
   - [ ] One line: "Links to the top side", or whichever side it's linked to.
3. Look at a redstone tunnel wall.
   - [ ] One line: "Links to the north side: in 15", or out and the current power.
4. Open Jade's settings (numpad 0 by default), then Plugins.
   - [ ] Tiny Tunnels entries are listed with readable names (Machine tunnels, Tunnel machine side, Redstone tunnel), and turning one off hides its line.

## 10. Recipes

1. Switch to survival in a new world, or clear the recipe book, and pick up some redstone dust:
   ```
   /gamemode survival
   ```
   ```
   /give @s minecraft:redstone
   ```
   - [ ] Toasts appear, and the recipe book shows the tiny, small, normal and large machines, the Shrinker, and the Tunnel.
2. Give yourself a Tunnel:
   ```
   /give @s tinytunnels:tunnel
   ```
   - [ ] The Redstone Tunnel (Tunnel + Comparator) and the Tunnel Wrench unlock.
3. Craft a Tiny Machine from 8 copper ingots and a redstone dust in a crafting table.
   - [ ] It crafts. Placing it builds a new 3x3 room.

| Item | Recipe |
|---|---|
| Tiny / Small / Normal / Large machine | 8 copper / iron / gold / diamond around a redstone dust |
| Giant machine | 8 emeralds around an eye of ender |
| Maximum machine | 4 netherite ingots in the corners, 4 obsidian on the sides, a nether star in the middle |
| Shrinker | `IGI` / `IRI` / ` I `: iron, glass, redstone |
| Tunnel ×2 | `CRC` / `HRB` / `C C`: copper, redstone, hopper, bucket |
| Redstone Tunnel | Tunnel + Comparator (shapeless) |
| Tunnel Wrench | `I I` / ` C ` / ` I `: iron, copper |

## Notes

-
