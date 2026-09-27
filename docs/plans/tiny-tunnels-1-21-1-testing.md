# Tiny Tunnels 1.21.1 In-Game Testing

Manual checks for the 1.21.1 backport (`tiny-tunnels-1-21-1-backport.md`, B1–B6), on branch `mc1.21.1/dev`.

- **Already automated:** the 30 GameTests pass on 1.21.1 (`./gradlew runGameTestServer`). They cover tunnel routing on every face, hoppers, fluids, nesting, the depth limit, shell repair, room loading, redstone and entry points.
- **What this pass covers:** what the GameTests can't. That's how things look, clicks, the room dimension, save and reload, and above all other mods' pipes and machines against the 1.21.1 `simulate`-based tunnels.
- Tick each box, and note anything odd in **Notes** at the bottom.

## Setup

- **Run config:** use the **Client** run config. On this branch it loads these test mods:
  - Create 6.0.8, Mekanism 10.7, AE2 (with GuideME)
  - Pipez, Energized Power, Storage Drawers
  - Jade and JEI
- **World:** a **new Creative world** with cheats on. Worlds from `main` (26.1.2) can't be opened on 1.21.1.
- **Finding items:** JEI is the quickest way to other mods' items. Search by name, or `@create`, `@mekanism`, `@ae2`.
- **Tiny Tunnels items:** get them from the creative tab, or run these commands:
  ```
  /give @s tinytunnels:normal_machine 4
  ```
  ```
  /give @s tinytunnels:tunnel 12
  ```
  ```
  /give @s tinytunnels:redstone_tunnel 4
  ```
  ```
  /give @s tinytunnels:tunnel_wrench
  ```
  ```
  /give @s tinytunnels:shrinker
  ```
- **How to count items:** for "nothing lost, nothing duplicated", start with a known number (for example 64 cobblestone). Check that the total across source and destination stays 64. Jade shows container contents when you look at them.

## 1. Client launch and basics (B1, B5)

1. Start the client.
   - [ ] The game reaches the title screen with no crash. The mods list shows Tiny Tunnels `0.1.0+mc1.21.1`.
2. Open the creative tab and look at every item.
   - [ ] All six machines, the Room Wall, Shrinker, Tunnel, Redstone Tunnel and Tunnel Wrench have textures. No purple-and-black squares.
   - [ ] The Tunnel Wrench is held like a tool, angled in hand. The others are flat items.
   - [ ] Every name is proper English, not a raw key like `item.tinytunnels.tunnel`.
3. In JEI, look up the recipes (**R** over an item) for the Tiny Machine, Shrinker, Tunnel, Redstone Tunnel and Tunnel Wrench.
   - [ ] They match the recipe table in `tiny-tunnels-phase-7-testing.md`, section 10 (redstone dust, not ender pearls).
4. Switch to survival (`/gamemode survival`) and pick up a redstone dust.
   - [ ] The recipe book unlocks the tiny, small, normal and large machines, the Shrinker and the Tunnel.

## 2. Machines, rooms and the room dimension (B3, B5)

1. Place a Normal Machine and hover it with the Tunnel Wrench or an empty hand.
   - [ ] The machine item's tooltip said "New room" before placing.
2. Right-click the machine with the Shrinker.
   - [ ] You shrink and arrive inside a 7x7 room with glowing grey walls.
   - [ ] The sky is dark (End-style sky) and stays the same. There's no day and night.
3. Right-click the air with the Shrinker.
   - [ ] You're back outside, where you entered from.
4. Break the machine with a pickaxe in survival and pick it up.
   - [ ] It drops itself, and the tooltip says "Room" plus an 8-character id.
   - [ ] Placed again somewhere else, entering it shows the same room with the same contents.
5. **Bed test.** Inside a room, place a bed and right-click it.
   - [ ] The bed **explodes**, like in the Nether. This is expected on 1.21.1: the old dimension format has only `bed_works`, which makes beds explode when off. On 26.x beds simply don't work. If that's not acceptable, note it; blocking bed use in rooms needs code.
6. **Spawn fallback (no way back).** Run the command below, which builds a room and moves you in *without* a return point:
   ```
   /tinytunnels debug build 900 5
   ```
   Then right-click the air with the Shrinker.
   - [ ] You land at world spawn, and the action bar says "Couldn't find the way back; sent to world spawn".
7. **Loading follows the machine (away from spawn).** Place a machine **at least 300 blocks from world spawn**, outside the spawn chunks, and put something visibly working inside, such as a hopper chain or a Create funnel line. Teleport 3000 blocks away, wait 10 seconds, run the command below, then teleport back.
   ```
   /tinytunnels debug tickets
   ```
   - [ ] While you're away, the room is **not** listed as loaded. The log shows `[TT-DEBUG] machine onChunkUnloaded` and `ticket REMOVE room=…`.
   - [ ] Nothing moved inside while you were away.
   - [ ] Back near the machine, the log shows `machine onLoad` and `ticket ADD`, and the room starts working again by itself.
   - [ ] For comparison, a machine **near spawn** stays loaded while you're away. That's expected on 1.21.1 (see "Spawn chunks" in 6a-inside).

## 3. Tunnels with vanilla blocks (B3, B4)

1. Enter a room. Place a Tunnel on a side wall, one block above the floor, and put a **chest** in front of it inside.
   - [ ] The wall shows a coloured port with a letter. The machine outside shows the same port on that side.
2. Outside, point a **hopper** into that machine face, with 64 cobblestone in it.
   - [ ] All 64 end up in the chest inside. The hopper is empty.
3. Inside, point a **hopper** into the tunnel wall (hopper facing the wall). Outside, put a chest against the matching machine face.
   - [ ] Items arrive in the chest outside, with the count unchanged.
4. Put a **cauldron** behind a tunnel inside. Outside, right-click the matching machine face with a **water bucket**. Vanilla buckets don't use capabilities, so if nothing happens, do this in the Create or Mekanism step with a pump or pipe instead.
   - [ ] (with a pipe or pump) The cauldron fills with water.
5. Right-click the tunnel wall with the Tunnel Wrench.
   - [ ] It moves to the next free machine side. The letter and colour change.
6. Sneak and right-click the tunnel wall with the Tunnel Wrench.
   - [ ] The tunnel turns back into a plain room wall, and you get the Tunnel item back.
   - [ ] **Wait 2 seconds.** It stays a plain wall, and the old tunnel doesn't come back. This checks that 1.21.1's `onRemove` path doesn't trigger shell repair on our own edit.
7. Try to break a room wall in survival, and try `/setblock ~ ~ ~ minecraft:stone` on a wall block.
   - [ ] Survival mining does nothing.
   - [ ] The `/setblock`'d block turns back into a room wall within a second.

## 4. Redstone tunnels (B3)

1. Place a Redstone Tunnel inside a room. Outside, put a lever on the matching machine face. Inside, put a lamp in front of the wall.
   - [ ] The lever turns the lamp on and off. The ports on both sides turn bright red while powered.
2. Right-click the redstone tunnel wall with an **empty hand**.
   - [ ] It flips to **out**: yellow corner marks, and the action bar says "Redstone out to the machine's … side".
3. While holding a block (for example stone), right-click the redstone tunnel wall.
   - [ ] The stone is **placed** against the wall, and the tunnel does **not** flip.
4. With the tunnel on **out**: inside, put a lever next to the wall. Outside, put a lamp against the machine face.
   - [ ] The lever inside switches the lamp outside.
5. With the lamp outside lit, break the machine.
   - [ ] The lamp outside turns off at once, with no stuck-on lamp or dust.
   - [ ] Placing the machine back restores the signal.

## 5. Jade

1. Look at a machine with an item tunnel and a redstone tunnel, at an item tunnel wall, and at a redstone tunnel wall.
   - [ ] The machine shows "Tunnels: …" and "Redstone: N in 15" (or similar). The item wall shows "Links to the … side". The redstone wall shows "Links to the … side: in 15".

## 6. Mod matrix

For each test, check three things. Note the mod and the step if any of them fails:

- **Moves:** things go through the tunnel in the right direction.
- **No void:** the total doesn't drop.
- **No dupe:** the total doesn't grow.

Leave each setup running for at least 30 seconds, because the way `simulate` behaves under repeated push and pull is what this pass is about.

### 6a. Create 6.0.8 (items and fluids; rotation is B8)

Get a Creative Motor from JEI to power anything that needs rotation.

1. **Funnel out of a machine.** Put an Andesite Funnel on a machine face outside, pulling from the machine onto a belt or into a chest. Inside, put a chest with 64 cobblestone behind the matching tunnel.
   - [ ] Items flow out through the funnel. 64 in total, no dupes.
2. **Funnel into a machine.** Put a Funnel on the machine face, pushing from a belt into the machine. Inside, put an empty chest behind the tunnel.
   - [ ] All items arrive inside.
   - [ ] **Fill the inside chest completely.** The funnel stops. No items are lost, and none spill on the ground.
3. **Chute.** Stack a Chute on top of the machine, pointing into its top face, with items fed in from above. Inside, put a chest behind the U tunnel.
   - [ ] Items arrive inside. When the chest is full, the chute holds its items; nothing is voided.
4. **Belt into a machine.** Run a belt straight into a machine face. Put items on the belt.
   - [ ] They enter the machine and land inside.
5. **Mechanical Pump (fluids).** Outside: a water source, then fluid pipe, then a Mechanical Pump driven by a Creative Motor, then pipe, then the machine face. Inside: a Fluid Tank (or a cauldron) behind the tunnel.
   - [ ] Water fills the tank inside. When it's full, the pump stops filling, and nothing leaks or duplicates.
6. **Pull fluid out.** Reverse the pump so it pulls from the machine face. Inside, put a full Fluid Tank behind the tunnel.
   - [ ] Water comes out, and the tank inside drains by the same amount.

### 6a-inside. Create inside the room

This is the reason Tiny Tunnels exists: Create machinery **inside** the room, feeding the tunnel walls.

- **Setup:** every Create block below goes inside the room, attached to or pointing at a **tunnel wall**. Plain chests and tanks go outside, against the matching machine face.
- **Power:** use a Creative Motor inside the room. Rotation can't cross a tunnel yet; that's B8.
- **Loading:** the room only runs while the machine's chunk is ticking. If you walk far enough away that the machine's chunk unloads, the factory inside pauses too. That's by design.
- **Spawn chunks:** 1.21.1 keeps the chunks around world spawn loaded and ticking (`spawnChunkRadius`, default 2). So a machine near spawn **never unloads, and its room runs forever**, however far away you go. That's vanilla behaviour, not a bug. 26.x removed spawn chunks, so it doesn't happen on `main`. Test unloading with a machine away from spawn (step 2.7).

1. **Funnel pulling from a tunnel wall.** Put an Andesite Funnel on the tunnel wall, feeding a belt. Outside, put a chest with 64 cobblestone against the matching machine face.
   - [ ] Items come in from the chest outside onto the belt. 64 in total.
   - [ ] With a filter set on the funnel, only the filtered item comes through.
2. **Funnel pushing into a tunnel wall.** Run a belt into a funnel on the tunnel wall. Outside, put an empty chest against the machine face.
   - [ ] Items arrive in the chest outside.
   - [ ] **Fill the chest outside completely.** The belt backs up and holds its items. Nothing is lost, and nothing drops on the ground.
3. **Chute into the floor tunnel.** Put a Chute pointing down into a **D** (floor) tunnel, with items fed in from above. Outside, put a chest under the machine.
   - [ ] Items arrive in the chest under the machine. When that chest is full, the chute holds its items.
4. **Pump through a wall.**
   - Inside: a Fluid Tank of water, then pipe, then a Mechanical Pump, then pipe, then a tunnel wall.
   - Outside: an empty Fluid Tank against the matching machine face.
   - [ ] Water fills the tank outside, and the tank inside drains by the same amount.
   - [ ] Reverse the pump (wrench it) so it pulls from the tunnel wall, with a full tank outside. Water comes back in.
5. **A small real factory.** Use two tunnels:
   - **Input tunnel:** a funnel pulls from its wall onto a belt that runs under a Mechanical Press (or into a Basin under a Mechanical Mixer).
   - **Output tunnel:** the product goes by belt and funnel into the second wall.
   - **Outside:** a chest of iron ingots on the input face, and an empty chest on the output face.
   - [ ] Iron ingots go in, and iron sheets come out in the outside chest. The counts match (one sheet per ingot).
   - [ ] Leave it running for a minute. It doesn't jam or dupe, and nothing is lost when the output chest fills up.
6. **Save and reload with the factory running.**
   - [ ] After reloading, it carries on by itself. Funnels and pumps reconnect to the tunnel walls without being re-placed.

### 6b. Mekanism 10.7

1. **Energy.** Outside: a Creative Energy Cube, then Basic Universal Cable, then a machine face. Inside: an Energized Power machine or battery (or a Mekanism Basic Energy Cube) behind the tunnel.
   - [ ] Energy fills the block inside.
2. **Logistical Transporter, pushing.** Outside: a chest, then a Basic Logistical Transporter set to pull from that chest (use the Configurator on the chest-side connection), then the machine face. Inside: a chest behind the tunnel.
   - [ ] Items arrive inside, with counts matching.
3. **Logistical Transporter, pulling.** Set the transporter connection at the machine face to **pull**. Inside: a chest with 64 cobblestone behind the tunnel.
   - [ ] Items come out. 64 in total.
4. **Mechanical Pipe.** Pump water into the machine face with a Basic Mechanical Pipe from a Fluid Tank.
   - [ ] Water arrives in the tank or cauldron inside.

### 6c. AE2

Power the network with a Creative Energy Cell and connect it with cables.

1. **Storage Bus on a machine face.** Inside: a chest with items behind the tunnel.
   - [ ] The ME terminal lists the items in the chest inside. Taking items out through the terminal removes them from the chest inside.
2. **Import Bus** on a machine face, with items in a chest inside.
   - [ ] The items move into the ME system. The count matches.
3. **Export Bus** on a machine face, set to export cobblestone. Inside: an empty chest.
   - [ ] Cobblestone arrives inside, and the ME count drops by the same amount.

### 6d. Pipez and Storage Drawers

1. **Pipez item pipe** from a chest into the machine face (pipe set to extract at the chest). Inside: a **Storage Drawer** behind the tunnel.
   - [ ] Items fill the drawer, including past one stack. Drawers have odd slot sizes.
2. **Pipez fluid and energy pipes** into their own machine faces.
   - [ ] Both arrive inside.
3. **Pipe loop.** Run a Pipez item pipe from the machine's N face around to its S face, with tunnels on both. Inside, connect the two tunnels with another pipe.
   - [ ] No crash and no freeze. At most one "Tunnel proxy depth limit" warning in the log.

### 6e. Compact Machines 7 (optional)

Compact Machines isn't in `build.gradle` yet. Ask for it to be added, then:

1. Load both mods.
   - [ ] The game starts. Both mods' rooms work, and their dimensions don't clash.
2. Put a Compact Machine inside a Tiny Tunnels room, and route items to it through a tunnel.
   - [ ] Items reach the Compact Machine's side.

## 7. Save and reload

1. Leave a setup from section 6 running, save and quit to the title screen, then load the world again.
   - [ ] Rooms, tunnels (letters and colours), redstone tunnels (mode and stored signal) and their contents are all still there.
   - [ ] Pipes reconnect to the machines on their own, without breaking and re-placing anything.
   - [ ] The Shrinker still returns you to the right place from inside a room.

## Notes

-
