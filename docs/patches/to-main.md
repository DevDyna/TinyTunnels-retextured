# Patches: mc1.21.1/dev → main

Made on `mc1.21.1/dev`, still needed on `main`. How to apply each type: `README.md`.

## Pending

| Added | What | How | Files | Notes |
|---|---|---|---|---|
| 2026-09-27 | `MachineSize.CODEC` declared as `Codec<MachineSize>` instead of the deprecated `StringRepresentable.EnumCodec` | copy | `src/main/java/dev/thefern2/tinytunnels/machine/MachineSize.java` | It only warns on 1.21.1, but keeping the file identical keeps later picks clean. Run the GameTests after. |
| 2026-09-28 | Buffered tunnels (`plans/tiny-tunnels-buffered-tunnels.md`, BT3) | redo | `room/TunnelMode`, `Room`, `RoomData`, `tunnel/*`, `Config`, datagen, textures, GameTests | Built on 1.21.1 first. On `main`, rebuild the buffer on the transfer API, with transactions. Data, textures, lang and config carry over. |
| 2026-09-28 | GameTests `fluid_out_of_cauldron` (drain a full water cauldron through a tunnel: 1000 mB out, cauldron empty; simulate first, nothing changes) and `fluid_partial_refused` (500 mB into an empty cauldron gives 0, cauldron unchanged) | redo | `gametest/TunnelGameTests.java`, `gametest/TinyTunnelsGameTests.java` | On `main`, write them with `ResourceHandler<FluidResource>` and transactions: drain inside a committed transaction, and check that a rolled-back one leaves the cauldron full. |
| 2026-09-27 | Docs and `scripts/sync-docs.sh` (first time only) | copy | `docs/`, `scripts/` | Once: on `main`, run `git checkout mc1.21.1/dev -- scripts/ docs/`, or `bash <(git show mc1.21.1/dev:scripts/sync-docs.sh)`. After that, just run `scripts/sync-docs.sh`. |

## Not for main (1.21.1-only, listed so they aren't copied by mistake)

| What | Why |
|---|---|
| `room/RoomBeds` and the `roomBedsExplode` config | 26.x's `bed_rule` already has `explodes: false`, so beds just don't work there. |
| `DataGenerators` without `bus = MOD` | `main` never had the attribute. |

## Done

| Applied | What |
|---|---|
| 2026-09-27 | Room Wall item removed (`ModItems`, `ModCreativeTabs`, datagen): redone by hand on both branches. |
