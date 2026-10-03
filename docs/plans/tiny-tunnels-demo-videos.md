# Tiny Tunnels Demo Videos

Videos to show the mod off around the first release: one showcase of the whole mod, then short videos per tunnel type, each built around an interesting machine inside a room. Filmed during the playtest week before the first release (2026-10-02 to about 2026-10-09), so the builds double as play testing.

**Status (2026-10-02):** planned. Next: V0 (the showcase world and its builds).

## Decisions so far

| Question | Decision |
|---|---|
| Which videos | **One showcase, then one per tunnel type** with a use case inside (user, 2026-10-02). |
| When | This week, alongside the playtest, before the first release (user, 2026-10-02). |

## The videos

| # | Video | Length | The hook |
|---|---|---|---|
| **V1** | **Showcase** | 60–90 s | "A whole factory in one block": walk into a machine, see the build, walk out, pipes and shafts running into it. |
| **V2** | **Item and fluid tunnels** | 1–2 min | An auto-smelter in a block: items in one side, smelted out another. |
| **V3** | **Redstone tunnels** | 1–2 min | A redstone circuit in a box: a lever outside drives a machine-sized contraption, a signal comes back out. |
| **V4** | **Kinetic tunnels (Create)** | 2–3 min | A power plant in a block: water wheels inside, rotation and stress out of the machine face. |
| **V5** | **The wrench: carry, turn, copy, delete** | 1–2 min | Pick a factory up, turn it, copy it, delete it and get everything back. |

V5 isn't a tunnel type, but copying a whole factory in one click is the most shareable trick the mod has, so it gets its own short.

### V1: Showcase (60–90 s)

1. **Cold open (0–5 s):** a single machine block on the ground with a shaft and a pipe going into it. Something is clearly running.
2. **Enter (5–15 s):** Shrinker on the machine, the shrink animation, inside the room.
3. **The room (15–35 s):** a slow pan over the factory inside (V4's power plant, or V2's smelter). The tunnels on the walls glow with their letters.
4. **Out (35–45 s):** Shrinker out, cut to the outside: the letters on the machine faces match the tunnels inside.
5. **Montage (45–75 s), 3–4 s each:** items through, fluids through, a redstone lamp lit from inside, a Create shaft spinning out of the face, machine sizes (tiny to maximum) in a row, a machine inside a machine's room, the wrench turning a machine, the copy.
6. **End card (75–90 s):** name, "NeoForge 1.21.1", "Create addon", where to download.

### V2: Item and fluid tunnels

- **Build: the auto-smelter.** A normal machine (7×7). Inside: hoppers feeding a row of furnaces, a chest collecting the output. Outside: a hopper or chest on the input side, a chest on the output side.
- **Shots:**
  1. Place the tunnels inside; the letter shows up on the machine face each time.
  2. Pour items in outside; cut inside to the furnaces running; cut outside to the output chest filling.
  3. **Fluids:** a tank or cauldron through a tunnel (bucket in, bucket out).
  4. **Buffered mode:** pipe → tunnel → pipe (Pipez) only works in a buffered mode. Show the empty-hand click cycling pass-through / buffered in / buffered out, and the pipes flowing after.
  5. Optional: **energy** through a tunnel with an FE mod (Pipez energy pipes and a battery).
- **Say:** tunnels pass straight through to the block on the other side; buffered mode is for pipe-to-pipe.

### V3: Redstone tunnels

- **Build, pick one:**
  - **A combination lock:** levers outside on the machine faces; a circuit inside checks the combination and sends the signal back out to open a door.
  - **A clock in a box:** a pulse circuit inside; outside, the machine pulses a redstone lamp or a piston.
- **Shots:**
  1. Craft a Redstone Tunnel (a Tunnel + a comparator); place it; right-click flips IN and OUT.
  2. A lever outside lights a lamp inside (IN).
  3. A circuit inside powers dust outside (OUT).
  4. **Analog:** a comparator reading a chest inside; the signal strength outside matches.
- **Say:** in and out per face, full 0–15 strength, the machine face shows a lit letter.

### V4: Kinetic tunnels (Create)

The reason the mod exists, so it gets the most care.

- **Build: the power plant.** A large or giant machine. Inside: water wheels (or a steam engine), gearboxes up to a target RPM, a kinetic tunnel on a wall. Outside: the Kinetic Port on that face, a shaft into a Create machine line (a crusher, a press, a mixer).
- **Shots:**
  1. Inside, build and show the stress gauge / goggles reading the capacity.
  2. Outside, the shaft from the port spinning; goggles show the same speed and stress capacity coming from the machine.
  3. **Factory in a block, the other way round:** rotation in from outside, a compact processing line inside.
  4. Several kinetic tunnels on one machine (different faces).
- **Avoid on camera:** closing a rotation loop through tunnels. It breaks the last block placed, exactly like plain Create, and looks like a bug.
- **Say:** real Create rotation and stress across the machine boundary, through the addon.

### V5: The wrench

- **Build:** reuse V2's smelter (it has a chest with items, which shows the refund).
- **Shots:**
  1. **Turn:** right-click, the face letters move one side clockwise; move the pipes to match.
  2. **Carry:** break with the wrench, the item keeps the room; place it somewhere else and walk in: it's all there.
  3. **Copy:** hold an empty machine, sneak + right-click: "Missing to copy this room: …" first, then with the items, a second smelter. Place it, walk in.
  4. **Delete:** sneak + wrench twice: the room's blocks and the chest's items come back to the inventory.
  5. **Nesting:** a machine inside a machine's room, walk in twice.
- **Say:** chests come out of a copy empty; the copy costs the blocks.

## Phases

| Phase | Work | Done when |
|---|---|---|
| **V0** | **Showcase world.** A superflat creative world, day, clear weather, `doDaylightCycle` and `doWeatherCycle` off, mob spawning off. Build V2's smelter, V3's circuit and V4's power plant (they're reused in V1 and V5). Mods for filming: core, the Create addon, Create, Jade, JEI or EMI, Pipez. | The three builds work in the world, and the playtest notes from building them are written down. |
| **V1** | Showcase: shot list above, then edit. | A 60–90 s cut. |
| **V2–V5** | One per video, in any order. V4 first if only one gets done. | Each cut exported. |

**Filming setup:** 1080p60 with OBS, F1 to hide the HUD for the slow shots, HUD on for the shots that need chat or tooltips (copy, delete, buffered mode). A camera or replay mod would make the pans smoother; check what exists for NeoForge 1.21.1 before counting on one.

## Open questions

- **Voice-over, captions or music only?** Captions work for every video and need no recording setup.
- **Where they go:** YouTube (V1 as a Short too?), Modrinth and CurseForge galleries, r/feedthebeast, the Create Discord's showcase channel.
- **Shaders** for the showcase, or vanilla look so it matches what players see?
- **Release timing:** post V1 on release day, and the others over the following days?
