# Tiny Tunnels Face Looks Testing

The in-game pass for A3 (`tiny-tunnels-api-and-addons.md`; the design is in `tiny-tunnels-api-design.md`, "Face looks"). The machine's face letters no longer come from block states. The machine block entity sends each face's tunnel kind and look to the client, and an overlay model draws the letter. The machine block has 2 states per size: `signal` true or false. Jade reads the tunnel names and status from the kinds.

GameTests cover the server side: which kind and look each face has, the lit look following power, and 2 states per size. This pass checks what they can't: what the client draws, reloads, redstone dust, Pipez, Create's shaft, and Jade's wording.

Run on `mc1.21.1/dev` after A3.8 and A3.9, with Create 6.0.8, Pipez and Jade on the dev runtime.

## Setup

- Start the client from the repository root:

```
./gradlew runClient
```

- Use a **new creative world** for steps 1–7, so old machines don't get in the way. Step 8 reuses it.
- Items:

```
/give @s tinytunnels:normal_machine 2
```

```
/give @s tinytunnels:shrinker
```

```
/give @s tinytunnels:tunnel 2
```

```
/give @s tinytunnels:redstone_tunnel 2
```

```
/give @s tinytunnels:kinetic_tunnel
```

```
/give @s tinytunnels:tunnel_wrench
```

```
/give @s pipez:item_pipe 4
```

- Also from the creative tab or JEI: redstone blocks, redstone dust, a lever, and from Create: Creative Motor, Shaft, Speedometer, Stressometer, Encased Fan.
- **Which face?** A new tunnel takes the first machine face with no tunnel, in the order bottom (D), top (U), north (N), south (S), west (W), east (E). The action bar names it. Right-click the tunnel wall with the Tunnel Wrench to move it to the next free face. Sneak + wrench removes it.
- **Letters:** orange-gold for a tunnel (items, fluids, energy), red for a redstone tunnel (bright red while powered), brass for a kinetic tunnel. Each letter is the face it sits on.
- **Mode:** right-click a tunnel wall with an empty hand to cycle Pass-through → Buffered in → Buffered out.

## 1. The client starts clean

1. Start the client (command above) and wait for the title screen. Then check the log for errors:

```
grep -nE "ERROR|Exception|Missing textures|Unable to load model" run/logs/latest.log | grep -iE "tinytunnels|machine_port|machine_face"
```

   - [x] The title screen comes up, and there's no crash.
   - [x] The grep prints nothing. (Jade's dev-only Pipez translation error is known, and doesn't mention `tinytunnels`.)
2. Create the world, and place a Normal Machine.
   - [x] The machine looks as before: plain faces, no letters, no purple-and-black missing texture.

## 2. Letters on the right faces

Build the machine used for the rest of the pass. Enter it with the Shrinker. All tunnels go on the middle of a wall, from inside.

3. **Two tunnels.** Right-click one wall with a Tunnel, then another wall with the second Tunnel.
   - [x] The action bar says "Tunnel linked to the machine's bottom side", then "Tunnel linked to the machine's top side".
4. Right-click the second tunnel wall (top) once with an empty hand.
   - [x] Action bar: "Tunnel set to Buffered in".
5. **Redstone.** Right-click a third wall with a Redstone Tunnel.
   - [x] Action bar: "Redstone in from the machine's north side".
6. **Kinetic.** Right-click a fourth wall with the Kinetic Tunnel. (One kinetic tunnel per machine is still the rule, by design: more than one comes in A7, with the port block.)
   - [x] Action bar: "Rotation out to the machine's south side".
   - [x] Right-click it with the Tunnel Wrench twice. The action bar says "west side", then "east side".
7. Leave the room and look at each face of the machine.
   - [x] Bottom: orange-gold **D**. Top: orange-gold **U**.
   - [x] North: dark red **N**.
   - [x] East: brass **E**.
   - [x] South and west: no letter. (South had the kinetic tunnel before the wrench moved it; its letter is gone.)
8. **Lit and unlit.** Place a redstone block against the machine's north face.
   - [x] The **N** turns bright red within a second.
   - [x] Break the redstone block. The **N** goes back to dark red.
   - [x] Put a lever on the block in front of the north face instead, and flip it a few times: on is bright, off is dark, each time.

## 3. Letters survive reloads

9. **Chunk reload (client).** Press F3+A.
   - [x] After the redraw, the letters are the same as in step 7: D, U, N (dark), E, nothing on S and W.
10. **Chunk reload (server).** Fly at least 400 blocks away in a straight line, wait 10 seconds, and fly back.
    - [x] The letters are the same as in step 7.
11. **World reload.** Power the north face again (redstone block), then Save and Quit to Title, and open the world again.
    - [x] The letters are the same, and **N** is bright red, because it's still powered.
    - [x] Break the redstone block: **N** goes dark.

## 4. Redstone dust connects only to redstone faces

12. Put redstone dust on the ground next to the machine's **north** face (the redstone tunnel's face) and next to its **west** face (no tunnel).
    - [x] North: the dust points into the machine (a line toward it, not a dot or a cross).
    - [x] West: the dust doesn't point into the machine.
13. **Add a second redstone face.** Enter the room and place the second Redstone Tunnel on a free wall. It takes the south face. Wrench it once.
    - [x] Action bar: "Redstone in from the machine's west side".
    - [x] Leave the room. The west dust now points into the machine, without being touched. West shows a dark red **W**.
14. **Remove it.** Enter the room and sneak + right-click that wall with the Tunnel Wrench.
    - [x] Action bar: "Tunnel removed".
    - [x] Leave the room. The west dust no longer points into the machine. The **W** is gone. The north dust still points in.

## 5. Pipez on transfer faces

15. Put an Item Pipe against the machine's **bottom** face (orange-gold D), and another against its **west** face (no tunnel).
    - [x] Bottom: the pipe connects to the machine (the pipe's end piece, its flange, is drawn against the machine).
    - [x] West: the pipe doesn't connect to the machine (no flange on that side).
16. Set the bottom pipe's connection to **extract** (sneak + right-click the connection with the Pipe Wrench). Then Save and Quit, and open the world again.
    - [x] The bottom connection is still there, and still set to extract.
17. Put a pipe against the **north** face (redstone) and the **east** face (kinetic).
    - [x] Neither connects. Only transfer faces get a flange.

## 6. Create's shaft follows the kinetic face

18. Put a Shaft against the machine's **east** face (brass E), then a Speedometer. Inside, put a Creative Motor against the kinetic tunnel wall, facing it, and set it to 64 RPM.
    - [x] The outside shaft turns, and the Speedometer reads 64 RPM.
    - [x] The machine draws no shaft of its own (only the brass letter); the shaft you placed connects to it on east only.
    - [x] F3 on the machine: `kinetic_face: east`, `signal: true` (north has a redstone tunnel).
19. **Move it with the wrench.** Inside, right-click the kinetic tunnel wall with the Tunnel Wrench.
    - [x] Action bar: "Rotation out to the machine's south side" (south is free again after step 14).
    - [x] The brass letter moves from E to **S**.
    - [x] The east shaft stops. A Shaft put against the south face turns at 64 RPM.
    - [x] F3 on the machine: `kinetic_face: south`.
20. Wrench it again until it's back on **east** (the action bar says "east side"), and put the east shaft line back.
    - [x] The east shaft turns again, and the brass **E** is back.

## 7. Jade wording

Look at the machine from outside with Jade on. The state is: Pass-through tunnel on D, Buffered in on U, an IN redstone tunnel on N, the kinetic tunnel OUT on E.

21. Put a redstone block against the north face. Put a Stressometer and then an Encased Fan on the east line (at 64 RPM, a fan should need 128 SU; use what the Stressometer shows if it differs).
    - [x] Jade shows exactly these three lines, in this order:
      - "Tunnel: D (Pass-through), U (Buffered in)"
      - "Redstone Tunnel: N (In, signal 15)"
      - "Kinetic Tunnel: E (Out), 64 RPM, 128 SU"
    - [x] The SU number matches the Stressometer.
22. Break the redstone block.
    - [x] The redstone line reads "Redstone Tunnel: N (In, signal 0)".
23. Inside, look at each tunnel wall with Jade.
    - [x] The wall lines read as before A3 (for example "Links to the bottom side" on a tunnel wall, "Links to the east side: out, 64 RPM, 128 SU" on the kinetic wall).

## 8. Without Create

24. Save and Quit, close the client, and start it without Create:

```
./gradlew runClient -PnoCreate
```

   - [x] The client starts. This log check prints nothing. It's step 1's, without the lines about `kinetic_tunnel`: without Create that item doesn't exist, so its unknown item and recipe errors are expected.

```
grep -nE "ERROR|Exception|Missing textures|Unable to load model" run/logs/latest.log | grep -iE "tinytunnels|machine_port|machine_face" | grep -v kinetic_tunnel
```

25. Open the same world.
    - [x] The machine loads with its room (you can enter it).
    - [x] Letters: orange-gold **D** and **U**, red **N**, and the brass **E** is still drawn (the kinetic tunnel is kept in the room).
    - [x] Inside, the kinetic tunnel wall is still there (not a plain wall, no hole), and inert.
26. Power the north face with a redstone block, then break it.
    - [x] **N** turns bright red, then dark again.
27. Jade on the machine.
    - [x] "Tunnel: D (Pass-through), U (Buffered in)"
    - [x] "Redstone Tunnel: N (In, signal 15)" while powered
    - [x] "Kinetic Tunnel: E (Out)", with no RPM or SU
28. Press F3 and look at the machine. The targeted block's properties are on the right.
    - [x] The only property is `signal`: `true`, because the machine has a redstone tunnel. No `kinetic_face` (that's Create only) and no per-face properties.
29. In a new spot, place the second Normal Machine (no tunnels) and look at it with F3.
    - [x] `signal: false`, and no letters.

## 9. Recheck after A3.13

30. After A3.13 (the up face's frame recoloured), put a tunnel on the machine's up face: set it to Buffered in, look at the wall, then set it to Buffered out and look again.
    - [ ] Buffered in and Buffered out each show their marks clearly on the up face's wall.
    - [ ] The up face's new frame colour looks right next to the other faces.

## Results

Recorded from the user's runs, with the date.

### 2026-10-01

Steps 1–24 and 27–29 passed, except as noted. The world for step 24 on was saved with Create, as the checklist says to do. The user's notes are quoted as written.

- **Step 4 (passed, up face open):** "Buffer in/out frames are barely noticeable, for example on a yellow frame I can barely distinguish the in and out markers, they are there though". The user likes the current in/out pattern and the yellow marks; the only problem is the `up` face, whose frame is also yellow. Fix: recolour the up frame (A3.13, client), then recheck in step 30.
- **Step 7 (passed):** "Top and bottom do not look blue". The checklist was wrong: the transfer letter is orange-gold (`machine_port_transfer_*.png`), not blue. Fixed in Setup, step 7, step 15 and step 25.
- **Step 6 (question, not a failure):** "I thought we had solved the issue of one k tunnel at a time?" One kinetic tunnel per machine is by design until A7 (several kinetic tunnels per machine, with the port block). Noted at step 6.
- **Step 24 (checklist fix):** the `-PnoCreate` log grep printed the `kinetic_tunnel` unknown item and recipe errors. They're expected (the item exists only with Create); the grep now leaves them out.
- **Step 25 (failed):** "letters are all missing outside though tunnels still work I powered on a lamp with redstone, k tunnels are gone".
  - All face letters missing without Create: open, the core engineer's A3.12.
  - "k tunnels are gone": open; the lead is asking what exactly (the brass letter, the wall inside, or the tunnel in Jade).
  - The machine still loads with its room, and the redstone tunnel carries a signal.
- **Step 26 (failed):** no lit **N**, because no letters are drawn (same cause as step 25). The redstone signal itself works.
- **A3.12 (cause and fix):** machines saved under the other block entity id (`machine` vs `kinetic_machine`) were sent to the client under a type it doesn't build for that block, so the client dropped their data and drew no letters. Now `MachineBlockEntity.getType()` always returns `MACHINE`, and `KineticMachineBlockEntity.getType()` returns `KINETIC_MACHINE`. GameTests pass (62 with Create, 51 with `-PnoCreate`).
- **Rerun on the same world:** steps 25–27 need a rerun with `-PnoCreate` on the same world as before, to confirm the letters are back.
- **Rerun after A3.12 (2026-10-01, `-PnoCreate`, same world):** the letters are back, and the kinetic tunnel wall is there. Steps 25–27 passed.
- **To rerun after A3.12:** steps 25–27 with `-PnoCreate`. Step 27's "Kinetic Tunnel: E (Out)" line should be checked again too, given "k tunnels are gone".

## Notes

- Step 15's "flange" is the end piece Pipez draws where a pipe connects to a block. It connects only where the machine answers a capability on that side, and on the client that's exactly the faces with a transfer tunnel (the "Pipez fix", `tiny-tunnels-api-design.md`).
- The Jade machine lines need the server side to have Jade too, which the dev runtime has.
- Known, not part of this pass: the GameTest server sometimes hangs on shutdown; `redstone_item_and_ports` is flaky; Jade's dev-only Pipez translation error.
