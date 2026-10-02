# Tiny Tunnels Create Addon Testing

The in-game pass for A6 (`tiny-tunnels-api-and-addons.md`). The kinetic tunnel now lives in its own mod, **Tiny Tunnels: Create** (`tinytunnels_create`). Core has no Create code any more. The machine is a plain machine again. Rotation leaves and enters it through a **Kinetic Port**: a small flange you place against a machine face, which links to the kinetic tunnel on that face.

GameTests cover the mechanics: speed and sign on every face pair, IN and OUT, stress, shared capacity, the wrench, re-placing the machine, a port that isn't linked, the port popping off, unknown kinds, and since A7 several tunnels per machine and the loop guard. This pass covers what they can't: crafting, placing by hand, how it looks, overload with real water wheels, Jade and the goggles, old worlds, and core without the addon.

Run on `mc1.21.1/dev` after A6.4, with Create 6.0.8 and Jade.

## Setup

- Start the client with the addon (core + addon + Create) from the repository root:

```
./gradlew :addons:create:runClient
```

- Use a **new creative world** for sections 1–6. Section 7 uses the old world from the A3 pass. Section 8 starts core alone.
- Items:

```
/give @s tinytunnels:normal_machine 2
```

```
/give @s tinytunnels:shrinker
```

```
/give @s tinytunnels:tunnel_wrench
```

```
/give @s tinytunnels_create:kinetic_tunnel 2
```

```
/give @s tinytunnels_create:kinetic_port 4
```

- From Create's creative tab or JEI: Creative Motor, Shaft, Speedometer, Stressometer, Millstone, Water Wheel, Cogwheel, Large Cogwheel, Wrench, Engineer's Goggles, Andesite Casing. Also Copper Ingots.
- **Which face?** A new tunnel takes the first machine face with no tunnel, in the order bottom, top, north, south, west, east. The action bar names it. Right-click the tunnel wall with the Tunnel Wrench to move it to the next free face. Sneak + wrench removes it.
- **Several kinetic tunnels per machine** since A7: one per face, up to six. (Sections 1–8 were written when one per machine was the rule; section 9 covers several.)

## 1. The client starts clean

1. Start the client (command above) and wait for the title screen. Then check the log:

```
grep -nE "ERROR|Exception|Missing textures|Unable to load model" run/logs/latest.log | grep -iE "tinytunnels"
```

   - [ ] The title screen comes up, and there's no crash.
   - [ ] The grep prints nothing. (Jade's dev-only Pipez translation error is known, and doesn't mention `tinytunnels`.)
   - [ ] On the Mods screen, both **Tiny Tunnels** and **Tiny Tunnels: Create** are listed.

## 2. Crafting

Switch to survival for this section (`/gamemode survival`), with the ingredients from creative first.

2. **Kinetic Tunnel.** In a crafting table, put a Tunnel, a Shaft and an Andesite Casing, in any slots.
   - [ ] The result is a **Kinetic Tunnel**.
3. **Kinetic Port.** Put a Shaft, an Andesite Casing and a Copper Ingot, in any slots.
   - [ ] The result is a **Kinetic Port**.
   - [ ] In JEI, both recipes show up on the item (press R on it).

Switch back to creative (`/gamemode creative`).

## 3. Placing the port

4. Place a Normal Machine, enter it with the Shrinker, and right-click the middle of a wall with a Kinetic Tunnel.
   - [ ] The wall turns brass, and the action bar says "Rotation out to the machine's bottom side".
   - [ ] Right-click the wall with the Tunnel Wrench until it says "east side".
5. Leave the room. Right-click the machine's **east** face (brass **E**) with a Kinetic Port.
   - [ ] A small flange sits flat on the east face, with a shaft stub pointing east.
6. Right-click the machine's **north** face with another Kinetic Port.
   - [ ] It places, flat on the north face.
   - [ ] Jade on it: "Not linked". (There's no kinetic tunnel on north.)
7. Try to place a Kinetic Port on a plain block (stone, dirt), not a machine.
   - [ ] It doesn't place, and the item isn't used up.
8. Break the north port with a pickaxe.
   - [ ] It drops as a Kinetic Port item.

## 4. Speed, sign and stress through the port

9. **OUT.** Inside, put a Creative Motor against the kinetic tunnel wall, facing it, at 64 RPM. Outside, on the east port's stub, put a Shaft, then a Speedometer, then a Stressometer.
   - [ ] The shaft turns, and the Speedometer reads 64 RPM.
   - [ ] Jade on the port: "Links to the east side: Out, 64 RPM, 0 SU".
   - [ ] The port's own shaft stub turns with the line, at the same speed and the same way (A6.7).
   - [ ] Put a Kinetic Port on the machine's north face (no kinetic tunnel there): its stub stays still, and Jade says "Not linked". Break it again.
10. **Load.** Add a Millstone at the end of the outside line.
    - [ ] The Stressometer shows the load. Jade on the port and on the wall inside show the same SU (a Millstone at 64 RPM is 256 SU).
11. **Sign.** Turn the inside motor around (to −64 RPM, or flip it with Create's Wrench).
    - [ ] The outside shaft turns the other way. The Speedometer reads −64 RPM (or 64 RPM turning the other way).
12. **IN.** Inside, right-click the kinetic tunnel wall with an empty hand.
    - [ ] Action bar: "Rotation in from the machine's east side".
    - [ ] Remove the inside motor and the outside line. Put a Creative Motor on the port's stub, facing the machine, at 32 RPM, and a Shaft on the wall inside.
    - [ ] The inside shaft turns at 32 RPM. Jade on the port: "Links to the east side: In, 32 RPM, ...".
    - [ ] Click the wall again to go back to OUT, and put the step 9 setup back.
13. **Overload with water wheels (the old K5).** Inside, replace the motor with 4 Water Wheels on one shaft line, geared up with a Cogwheel and Large Cogwheel to 32 or 64 RPM, into the tunnel wall. Outside, add loads (Mechanical Press, Encased Fan, Millstone) until they need more than the wheels give.
    - [ ] The outside speed matches the inside speed before the overload.
    - [ ] With too much load, both sides stop (overstressed): the wheels' network inside and the line outside. Jade on the port and on the wall say "overstressed".
    - [ ] It stays stopped; it doesn't flicker on and off.
    - [ ] Remove a load outside: both sides start again.

## 5. Wrench move and the port popping off

14. Put the Creative Motor back inside at 64 RPM. Inside, right-click the kinetic tunnel wall with the Tunnel Wrench.
    - [ ] Action bar: "Rotation out to the machine's bottom side". The brass letter moves from E to the bottom face.
    - [ ] The east line stops. Jade on the east port: "Not linked".
    - [ ] The east port's stub stops turning.
    - [ ] Place a Kinetic Port on the machine's bottom face (from below, or dig under it) with a Shaft beyond it: it turns at 64 RPM.
15. Wrench it on until it's back on **east**.
    - [ ] The east line turns again; the bottom port says "Not linked".
16. **Pop off.** Break the machine with a pickaxe while the line runs.
    - [ ] Every port on it pops off and drops as an item. The outside line stops.
    - [ ] The machine drops as an item that keeps its room (its tooltip names the room).
17. Place the machine again, wait a couple of seconds, and put a port back on the east face.
    - [ ] The east line turns again at 64 RPM, from the same room.

## 6. Jade and goggles

18. With the line running (step 17), look at things with Jade:
    - [ ] The port: "Links to the east side: Out, 64 RPM, 256 SU" (with the Millstone; the SU matches the Stressometer).
    - [ ] The tunnel wall inside: the same line.
    - [ ] The machine: "Kinetic Tunnel: E (Out)".
    - [ ] A port on a face without the tunnel: "Not linked".
19. Put on Engineer's Goggles and look at the linked port, then at an unlinked port.
    - [ ] The linked port shows Create's usual lines (speed, stress).
    - [ ] The unlinked port shows "Not linked" under Create's lines.

## 7. The old world from the A3 pass

The world you used for `tiny-tunnels-face-looks-testing.md` has a kinetic tunnel from before the addon (`tinytunnels:kinetic`). That kind is gone from core now, so its entry is kept as an unknown kind. This is expected.

20. Open that world (still with `:addons:create:runClient`).
    - [ ] The world loads; no crash.
    - [ ] The machine keeps its room: you can enter it, and its other tunnels (D, U, N) work and show their letters. (It was saved as a kinetic machine; it now loads as a plain machine.)
    - [ ] Inside, where the kinetic tunnel was, the wall is an **Unknown Tunnel** wall with a "?" (not a hole, not a plain wall).
21. Right-click that wall with the Tunnel Wrench, then sneak + wrench.
    - [ ] Nothing changes. The action bar says "Unknown tunnel (tinytunnels:kinetic): its mod isn't loaded, so it can't be moved or removed".
    - [ ] Record what the machine's east face shows (expected: no letter).

## 8. Core alone

22. In the new world from sections 1–6, make sure the machine has its kinetic tunnel on east with a port, and a normal Tunnel on another face. Save and Quit, close the client, and start core alone (no addon, no Create):

```
./gradlew :core:runClient
```

   - [ ] The client starts. The log check prints nothing about `tinytunnels` (except lines about `tinytunnels_create` items or blocks that are missing, which are expected):

```
grep -nE "ERROR|Exception|Missing textures|Unable to load model" run/logs/latest.log | grep -iE "tinytunnels" | grep -v tinytunnels_create
```

23. Open that world.
    - [ ] The machine loads with its room: you can enter it, and the normal Tunnel still works.
    - [ ] The ports are gone (their blocks don't exist without the addon).
    - [ ] Inside, the kinetic tunnel's wall is an **Unknown Tunnel** wall with a "?".
    - [ ] The wrench on it changes nothing, and says "Unknown tunnel (tinytunnels_create:kinetic): its mod isn't loaded, so it can't be moved or removed".
24. Save and Quit, and open the world again with the addon:

```
./gradlew :addons:create:runClient
```

   - [ ] The kinetic tunnel is back inside (brass wall), with its mode as before. The brass **E** is back on the machine.
    - [ ] Place a port on east again: with the inside motor, the line turns.

## 9. Several kinetic tunnels (A7)

Since A7 a machine can have a kinetic tunnel on every face. Rotation still can't loop: a link that would feed rotation back to where it came from is **blocked**. It doesn't turn, and it says so. GameTests cover the mechanics (`multi_*`, `loop_*`); this section is how it looks and feels.

Use a new Normal Machine with no tunnels. Give yourself more kinetic tunnels and ports:

```
/give @s tinytunnels_create:kinetic_tunnel 4
```

```
/give @s tinytunnels_create:kinetic_port 4
```

25. **Two tunnels, two lines.** Inside, put a Kinetic Tunnel on the east wall and one on the west wall, and wrench each to its matching face (the action bar says "east side", then "west side"). Put a Creative Motor against each wall, the east one at 64 RPM and the west one at 32. Outside, put a port on the east face and one on the west face, each with a Shaft and a Speedometer.
    - [ ] The second Kinetic Tunnel places (no "already has a kinetic tunnel" message).
    - [ ] The machine shows two brass letters, **E** and **W**.
    - [ ] The east line reads 64 RPM and the west line 32 RPM.
    - [ ] Jade on the machine: "Kinetic Tunnel: W (Out), E (Out)".
26. **Mixed directions.** Click the west wall with an empty hand (it turns IN). Remove the west motor inside and put a Shaft there instead. Outside, replace the west line with a Creative Motor on the west port, facing the machine, at 32 RPM.
    - [ ] The east line still reads 64; the shaft inside the west wall turns at 32.
    - [ ] Jade on the west port: "Links to the west side: In, 32 RPM, ...".
27–29. ~~**The loop (motor inside, motor removed, motor outside).**~~ **Replaced (user, 2026-10-02):** a loop through tunnels can't be closed in game, the same as in plain Create. Whatever is placed last to close it (a shaft, a gearbox or a port) breaks as soon as it's placed, exactly as a plain overworld shaft loop with a gearbox and motor does. So there's no free spin and nothing counted twice to see in game. The guard's own cases (a loop that Create would allow, the motor removed, the motor outside) are covered by the GameTests `loop_room_motor`, `loop_outside_motor` and `loop_nested`.
    - [x] Closing a loop through two tunnels breaks the last piece placed, like Create (user, 2026-10-02).

30. ~~**Two OUT links into one line (the diamond).**~~ **Removed (user, 2026-10-02):** not a valid in-game case. Plain Create doesn't let a loop close at all: in a plain overworld shaft loop with a gearbox and motor, the shaft that closes the loop breaks as soon as it's placed. The capacity rule is still covered by the GameTest `loop_diamond`, and the fix it led to (the link already driving keeps the line, A7.6) stays.
31. **Stress share recheck (the share rule changed in A7).** Undo the joins: one OUT tunnel on east with a motor inside at 64, a port with a Stressometer and a Millstone outside. Then add a second Creative Motor on the outside line (on a gearbox), also turning the line at 64.
    - [ ] Before the outside motor: Jade on the port and on the wall show the Millstone's whole load (256 SU at 64 RPM), as in step 10.
    - [ ] With the outside motor: the wall inside is charged only its share, about half (128 SU), and the port shows the same number.
    - [ ] Recheck steps 10 and 13 (load, and overload with water wheels): they read as before when there's no other source on the outside line.

## Results

Recorded from the user's runs, with the date.

### 2026-10-02

- **Step 9 (failed, fixed):** with the motor inside at 120 RPM, lowering it to 64 left the outside at 120 (Speedometer, Jade and the visual). Cause: a port loaded while spinning fell into a source loop that made Create ignore every decrease (A6.8). Fixed in `LinkedKineticBlockEntity`. **Rechecked: the outside follows the speed down.**
- **Port stub (A6.7, fixed):** the port's shaft stub didn't turn; it now does.
- **Section 8 (failed, A6.9):** with core alone, everything passed except one thing: on the first entry into the room, the addon kinetic tunnel's place was a hole (air: the unregistered addon block loads as air). After leaving and entering again, the "?" unknown wall was there. Fix (core): a room chunk that loads runs the shell repair on the next tick. GameTest `unknown_kind_chunk_load_repair` (the unknown wall and a plain room wall loaded as air both come back). **To recheck:** section 8, steps 22–23, with a world that has the addon's tunnel.
- **Section 8 rechecked after A6.9 (passed):** saved and quit inside the room, loaded with core alone: the "?" wall is there on load.
- **The whole list is complete** (user, 2026-10-02). The port recipe stays as it is, and so does the id `tinytunnels_create`.
- **Section 9 (A7):** steps 25–26 passed (26 after A7.5: an unpowered link now reads as idle, not "Blocked"). Steps 27–29: a tunnel loop can't be closed, the last piece breaks, as in plain Create, so they were replaced. Step 30 was removed: not a valid in-game case. Step 30 also found A7.6 (a newly placed port broke at once); that fix is kept. Step 31 (stress share recheck) passed. **Section 9 is complete.**

## Notes

- **`loop_nested` under load (2026-10-02):** after the A7.2 cleanup it failed once in a full addon run (79/80): `line around the inner machine, driven through A: expected 64.0, got 0.0` (the loop never started within 300 ticks). It passes alone (3/3). Same pattern as `kinetic_machine_replaced` under load. Logged, not chased (team rule); the user's section 9 loop steps are the check.

- **`loop_nested` tick limit (2026-10-02):** raised from 300 to 600 ticks. It's the biggest build in the suite (a machine in a room, two tunnels, two lines), and under full-suite load it once timed out with no check failing ("Test timed out before sequence completed"); the 2-of-3 start window adds a tick or two per link. The waits and checks inside the test are unchanged.
- **`kinetic_toggle` under load (2026-10-02):** failed once in a full addon run after A7.5 ("inside shaft speed after the flip: expected -32.0, got 0.0"); alone it passed 3 of 3. A load-only failure, the same "never starts under full-suite load" family as `loop_nested` and `kinetic_machine_replaced`. Logged, not chased.
- **`loop_nested` again (2026-10-02, after machine management M3):** timed out once more at 600 ticks in a full addon run (82/83), "Test timed out before sequence completed". The change in that run was only the delete messages' wording; the two runs before it passed 83/83. Logged, not chased; section 9 loop steps are the check.
- **`kinetic_machine_replaced` under load (2026-10-02):** fails in some full addon runs (2–4 of 10): after the machine is picked up and placed back, its room doesn't resume ticking in the GameTest. It passes alone. **Checked in game by the user (2026-10-02): picking up and re-placing the machine and port works every time.** Re-placing by hand always takes well over 5 s, because the port has to be placed too. Treated as a GameTest-only effect (many rooms and tickets changing at once); the test runs only when named with `-PonlyTest`.

- **Section 7 and old machines:** A6.4 removed the `kinetic_machine` block entity id; a registry alias maps it to `tinytunnels:machine`, so a machine saved under it loads as a plain machine and keeps its room (GameTest `kinetic_machine_id_keeps_room`).
- **Section 8:** the ports are blocks of the addon, so without it they're dropped from the world, as with any removed mod's blocks. They can be crafted or given again.
- **Create limit (2026-10-02):** a creative motor whose speed changes every tick (by a contraption or a computer, never by hand: the value panel commits once) breaks the shaft against it after about 14 changes. That's Create's anti-flicker rule: each slow-down detaches and reattaches the network, adding to a block's flicker score until it passes 128. It happens with a plain motor and shaft and no Tiny Tunnels (the unregistered control GameTest `kinetic_stepped_control`), so it isn't the addon's. At a player's pace (`kinetic_speed_change_stepped`: 120 -> 100 -> 80 -> 64, 20 ticks apart) the port follows every change.
- Known, not part of this pass: the GameTest server sometimes hangs on shutdown; the "room frozen after a very fast re-place" issue (`tiny-tunnels-kinetic-tunnel-testing.md`, Notes).
