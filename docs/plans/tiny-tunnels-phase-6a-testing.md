# Tiny Tunnels Phase 6a In-Game Testing

Manual checks for Phase 6a of `tiny-tunnels-implementation.md`: unbreakable shell, tunnels, and occupancy loading. Automated GameTests come in 6b. Tick each box, and note anything odd in **Notes** at the bottom.

## Setup

- Use the **Client** run config. The dev run now also loads the test mods: **Pipez**, **Energized Power**, **Storage Drawers**, **Jade**, **AE2** (with GuideME). Their items are in their own creative tabs.
- A **new Creative world** with cheats on is simplest. Old worlds work too.
- `[TT-DEBUG]` lines still go to `run/logs/latest.log`; the log helps if something fails.

**How tunnels map, in one line:** a tunnel's letter is the **machine side** it links to. Something touching the machine's **top** face outside is reachable through the tunnel marked **U** inside, from the block touching the tunnel's inner face, and the other way round.

| Letter | Machine side |
|---|---|
| D | bottom |
| U | top |
| N | north |
| S | south |
| W | west |
| E | east |

**Which machine face am I looking at?** The face you see is the **opposite** of the direction you're facing (F3 shows your facing direction).

| You're facing | You're looking at the machine's | Tunnel letter |
|---|---|---|
| south | north face | N |
| north | south face | S |
| east | west face | W |
| west | east face | E |
| down (from above) | top face | U |
| up (from below) | bottom face | D |

**Pipez extraction:** Pipez pipes only push into blocks. To pull from a block, set that pipe connection to extract (in Pipez, sneak + right-click the connection with the **Pipe Wrench**, or open the pipe's GUI). Each step says which side extracts.

**Pipe → tunnel → pipe (corrected 2026-09-28): energy only in pass-through mode.** A pass-through tunnel is invisible to pipes: pushing into a tunnel is pushing straight into whatever touches the matching machine face on the other side. A Pipez pipe only accepts input on connections set to **extract**, since those are its network's inputs. Normal connections only push out.
- **Energy:** with a pipe on **both** sides of a tunnel, set the far pipe's connection to the machine face (or to the tunnel, inside) to extract too. This works because Pipez exposes a real energy storage on extracting sides, and it was what was verified on 2026-09-26.
- **Items and fluids:** on extracting sides Pipez exposes only dummy handlers that hold nothing, so pipe → pass-through tunnel → pipe **never moves anything**. This was verified on both 26.x and 1.21.1 on 2026-09-28. The fix is **buffered tunnels** (`tiny-tunnels-buffered-tunnels.md`).
- Directly touching Pipez pipes would just merge into one network; the tunnel keeps them as two separate networks.
- With a **block** on the far side (Battery Box, chest, tank) touching the face, no extra setting is needed.
- Compact Machines tunnels behaved the same way with Pipez.

## 1. Unbreakable shell

1. Enter any room with the Shrinker. In creative, try to break a room wall.
   - [ ] It doesn't break (it may flicker, then come back).
2. Switch to survival and try again with a pickaxe, then switch back:
   ```
   /gamemode survival
   ```
   ```
   /gamemode creative
   ```
   - [ ] It doesn't break.
3. Replace a wall with a command. Look at a wall block, note its X Y Z (F3 *Targeted Block*), and run:
   ```
   /setblock X Y Z minecraft:stone
   ```
   - [ ] Within a tick the stone turns back into a room wall.
4. Place a furnace and a chest inside the room, then break them in survival.
   - [ ] They break and drop normally. Only the walls are protected.

## 2. Placing, cycling and removing tunnels

1. Inside a room, hold a **Tunnel** and right-click any wall, floor or ceiling block. Blocks right next to an inside corner are fine; see step 2.
   - [ ] The wall becomes a tunnel with a **D** port, and the action bar says "Tunnel linked to the machine's bottom side".
   - [ ] In survival, one Tunnel item was used.
2. Place tunnels on blocks touching an inside corner, e.g. two walls and the floor meeting at one corner.
   - [ ] All three are accepted, each gets its own letter, and items reach each one's inner side. The shell's true edge and corner blocks sit behind the corner and can't be clicked from inside, so the "Tunnels can't go on an edge or corner" refusal never shows in normal play. It's only a safety check.
3. Hold the **Tunnel Wrench** (Tiny Tunnels tab) and right-click the tunnel several times.
   - [ ] The letter cycles D → U → N → S → W → E, skipping letters other tunnels in this room already use. The action bar names the side each time.
4. Place tunnels until all six letters are used, then try a seventh.
   - [ ] Refused: "All six sides already have a tunnel".
   - [ ] Right-clicking a tunnel with the wrench now says "Every other side already has a tunnel".
5. Sneak + right-click a tunnel with the Tunnel Wrench.
   - [ ] It turns back into a plain wall, "Tunnel removed" shows, and in survival the Tunnel item goes to your inventory.
   - [ ] **Other wrenches work too:** repeat 3 and 5 with the Pipez **Pipe Wrench**, which Pipez tags `c:tools/wrench`.
   - [ ] **Nothing else edits tunnels:** right-click a tunnel with an empty hand, and with a block in hand. The tunnel doesn't change, and the block places against the tunnel normally.
6. List the room's tunnels. Click the room's ID in `debug rooms`, or copy it:
   ```
   /tinytunnels debug rooms
   ```
   - [ ] The line shows `tunnels: N` matching what you placed.

## 3. Items

Set up one room with a **U** tunnel (machine top) and a **D** tunnel (machine bottom).

1. **Outside → inside.** Inside, place a chest touching the **U** tunnel's inner face. Outside, place a hopper on top of the machine, pointing down into it, and put a stack of cobblestone in the hopper.
   - [ ] The cobblestone moves into the chest inside. Enter to check.
2. **Inside → outside.** Inside, place a hopper pointing into the **D** tunnel and put items in it. Outside, place a chest under the machine, touching its bottom face.
   - [ ] The items end up in the chest outside.
3. **Pipez, push only.** Outside, run a Pipez **Item Pipe** from a chest (set to **extract** from that chest) to any machine face that has a tunnel. Inside, put a Storage Drawer touching that tunnel.
   - [ ] Items fill the drawer. Nothing is set to pull on the inside.
4. **Pipe first, tunnel after.** Connect an Item Pipe to a machine face with **no** tunnel yet. Inside, add a tunnel mapped to that face (cycle the letter to match) and put a chest behind it.
   - [ ] Items start flowing without re-placing the pipe.
5. **Hot swap.** While the hopper from 3.1 is still feeding, go inside and replace the chest behind **U** with a barrel.
   - [ ] Items now go into the barrel, with no changes outside.
6. **Nothing held in the tunnel.** Remove the chest behind **U**, leaving air, while the hopper keeps trying.
   - [ ] The hopper just stops; its items stay in the hopper. Put a chest back and it resumes, with no items lost or duplicated.

## 4. Fluids

1. Outside, place a **Creative Fluid Tank** filled with water (right-click it with a water bucket). Connect it with a Pipez **Fluid Pipe**, set to **extract** from the tank, to a machine face with a tunnel. Inside, place a **Fluid Tank (Small)** touching that tunnel.
   - [ ] The small tank fills with water.
2. Remove the small tank, then put it back.
   - [ ] Filling stops, then resumes. No error in the log.

## 5. Energy

1. Outside, place a **Creative Battery Box**. Connect it with a Pipez **Energy Pipe**, set to **extract** from the box, to a machine face with a tunnel. Inside, place a **Battery Box** touching that tunnel.
   - [ ] The Battery Box charges. Check with Jade or its GUI.
2. **Reverse.** Inside, a Creative Battery Box plus an Energy Pipe (extract) into a tunnel. Outside, a Battery Box touching the matching machine face.
   - [ ] The outside Battery Box charges.

## 6. Nested chain

1. In room A, place a machine for room B, right next to one of A's tunnels, with its side against that tunnel. In room B, add a tunnel mapped to that side of B's machine, with a chest behind it. Outside, feed A's machine with a hopper on the face matching A's tunnel.
   - [ ] Items travel overworld → A's machine face → A's tunnel → B's machine face → B's tunnel → the chest in B.
   - [ ] No crash, no log errors.

## 7. Occupancy loading

1. Keep the hopper from 3.1 feeding the room. Stand **inside** the room and run:
   ```
   /tinytunnels debug tickets
   ```
   - [ ] The output ends with "1 machine chunk(s) held because a player is inside", listing this room's machine position.
   - [ ] While you stay inside, the chest behind **U** keeps filling from the hopper outside.
2. Exit, teleport far away, wait 10 s and check:
   ```
   /tp @s ~3000 ~ ~
   ```
   ```
   /tinytunnels debug tickets
   ```
   - [ ] Nothing is held and the room isn't listed.
3. Teleport back:
   ```
   /tp @s ~-3000 ~ ~
   ```
   - [ ] The hopper resumes feeding. Nothing was lost or duplicated while you were away.
4. **Nested.** Stand inside room B from test 6 and run `debug tickets`.
   - [ ] Two holds are listed: B's machine (inside room A) and A's machine (overworld). The chain from test 6 keeps flowing while you watch from B.

## 8. Save and reload

1. With pipes connected on both sides of some tunnels, *Save and Quit to Title* and reopen the world. Don't touch anything.
   - [ ] Every pipe setup from sections 3–5 works again: items, fluid and energy flow.
   - [ ] Tunnels show the same letters.

## 9. AE2 (optional, longer setup)

1. Build a small ME network outside: an **ME Energy Acceptor** fed FE from a Creative Battery Box, **ME Cables**, and an **ME Terminal** (no controller needed for small networks). Put an **ME Storage Bus** on a machine face that has a tunnel. Inside, put a chest with some items behind that tunnel.
   - [ ] The ME Terminal shows the chest's items, and you can take them out and put items in.

## 10. Redstone and chests

Room walls, tunnel walls and machines are **not redstone conductors** (changed 2026-09-26). Chests open under them, and power doesn't pass through them. Redstone tunnels aren't built yet (Phase 7), so no signal should cross between a room and its machine.

1. **Chest under the ceiling.** Inside a room, place a chest directly under the ceiling (a wall or tunnel above it) and open it.
   - [ ] It opens.
2. **Chest under a machine.** Outside, place a chest directly under a machine and open it.
   - [ ] It opens.
3. **No power through a machine.** Outside, put a lever on one side of a machine and a Redstone Lamp touching the **opposite** side. Flip the lever.
   - [ ] The lamp stays off.

   For comparison, swap the machine for a stone block (lever on one side, lamp on the other).
   - [ ] With stone, the lamp turns on. That's normal conductor behaviour, which our blocks no longer have.
4. **A lever on a machine still powers what touches the lever.** Keep the lever on the machine's side, and replace the ground block directly below the lever with a Redstone Lamp, so the lamp touches the lever but not the machine. Flip the lever.
   - [ ] The lamp turns on. Only power passing *through* our blocks is gone; levers and dust next to them work normally.
5. **Dust on top.** Place redstone dust on top of a machine and on a room floor, then power it with a lever next to it.
   - [ ] The dust can be placed and carries power across the top of the block.
6. **No signal crosses a tunnel.** Outside, power the machine's face that has a tunnel (a lever on the machine, or a redstone block touching that face). Inside, place a Redstone Lamp touching that tunnel.
   - [ ] The lamp stays off. Signals only cross once redstone tunnels exist.
7. **Hopper lock still local.** Outside, the hopper from 3.1 feeding the machine: power the hopper itself with a lever.
   - [ ] The hopper stops, as vanilla hoppers do when powered. Unpower it and feeding resumes.

## Notes

Record failures here, with the section and step, plus anything from `run/logs/latest.log` (`grep TT-DEBUG` or `ERROR`).
