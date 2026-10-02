# Tiny Tunnels API Split Team

The agent team for the API split (`docs/plans/tiny-tunnels-api-and-addons.md`, phases A0–A8). Written 2026-10-01, for a new session.

**Agreed with the user (2026-10-01):** A0 goes first. The team starts with the lead, core, client and QA; the Create addon engineer joins when A6 opens. The task list is `api-split-tasks.md`. The Create addon engineer joined for A6 (2026-10-01); the user chose the addon id `tinytunnels_create`, its own version, and a simple port look for now.

**Paths:** since A0, the mod lives in `core/` (`core/src/main/java/dev/thefern2/tinytunnels/...`). Package names didn't change. Run Gradle from the repository root; the commands are the same as before.

**Every teammate reads `api-split-handoff.md` first.** Teammates don't share the lead's conversation; the docs are the shared memory.

## Team members

Five members. Start with the lead and the first two roles; add the others when their phases open (see "Order of work").

| Member | Owns | Phases |
|---|---|---|
| **Lead (architect)** | The API: the `api` package and `tiny-tunnels-api-design.md`. The task list, reviews, phase gates, every message to the user. Shared files: `build.gradle` (root and `core/`), `settings.gradle`, `neoforge.mods.toml`, `registry/*`, `TinyTunnels.java`. | A0, A1, gates |
| **Core engineer** | Core's internals: `room/`, `machine/` (except rendering), `tunnel/`, `loading/`, `wall/`, `command/`. Turns transfer and redstone into tunnel kinds, the save format, the `TunnelService` implementation, events. | A2, A4, A5 |
| **Client engineer** | Rendering and assets: the machine face looks (baked model overlay through `ModelData`, the machine block entity's update tag, the `signal` state), `datagen/`, `core/src/main/resources/assets`, `core/src/generated` (the only one who runs `runData`), and Jade (`compat/jade`). | A3 |
| **Create addon engineer** | `addons/create/` once it exists. Before that: `compat/create/`. The port block, moving the kinetic tunnel onto the API, more than one kinetic tunnel per machine. | A6, A7 |
| **QA and docs** | GameTests (`gametest/`, and the addon's tests), the **only** one who runs `runGameTestServer` and `runClient`, the test kind for A5, in-game checklists, `docs/todo.md`, plan status lines, the release workflow and API guide. | All gates, A8 |

**Smaller team:** lead plus two (core, and Create plus client) works for A0–A2. Keep QA as a separate member: someone has to own test runs, because of the `run/gametest` rule below.

## Rules

### Ownership

- **Only edit files you own.** For a change in someone else's files, send them a task or a message saying exactly what and why. The owner makes it, or hands you that one file for that task.
- **Shared files** (lead-owned, listed above) change only through the lead.
- **The `api` package changes only through the lead.** If you need an API change, ask the lead with the use case. The lead updates `tiny-tunnels-api-design.md` in the same task, so the design doc and the code never disagree.
- **New files** go to whoever owns the folder.

### Builds and tests

- **`./gradlew compileJava` is allowed for anyone, anytime.** Gradle queues concurrent builds on its own lock.
- **GameTest runs belong to QA only** (`:core:runGameTestServer` and `:addons:create:runGameTestServer`). Each run deletes its world (`run/gametest/world`, `run/gametest-create/world`), so two at once break each other. To get a run, message QA with what changed. QA replies with the result line, and the failures in full.
- **`runData` belongs to the client engineer only.** It rewrites `src/generated`.
- **`runClient` is for the user's in-game testing.** QA prepares it, and the user plays.
- **A failing test is reported, not chased.** QA reports it to the owner of the code and to the lead. The owner fixes it in a task. A flaky test gets a note in the testing doc and a todo line. It doesn't start an investigation without the lead's go (see the working agreements in the handoff).
- **Intermittent failures go to the user, fast (user, 2026-10-02).** If a test fails only sometimes (passes alone, fails a few times under the full suite's load), stop after 2–3 failures: log the failure line (testing doc Notes and a todo line), write short in-game steps, and hand them to the user through the lead. Don't re-run it dozens of times or investigate further unless the user's in-game check shows a real problem. GameTests act far faster and more crowded than any player can.

### Tasks

- **Every piece of work is a task in the shared task list**, named with its phase, for example `A2.3 transfer kind: capabilities through TunnelService`. A task says what's done when it's finished, and who owns it.
- **Claim before starting, and keep tasks small:** one owner and a few files each.
- **A task is done when** it compiles, its tests pass in a QA run, its docs are updated (plan status line, design doc for API changes, todo), and the owner has written a two- to four-line summary in the task: what changed, which files, anything left.
- **Blocked:** say so in the task and message the lead. Don't work around another owner's files.

### Messages

- **Short and specific:** what you need, which file, why. Paste exact errors.
- **Questions for the user go through the lead.** The lead batches them and asks in one message, and records the answers in the plan.
- **Teammates never message the user directly** about decisions, and never ask the user to run git commands.

### Phase gates (lead)

The next phase opens only when:

1. **Build:** `./gradlew build` passes.
2. **Tests:** QA's runs pass: core alone (`:core:`) and with the addon (`:addons:create:`; before A6.4: with Create and with `-PnoCreate`). The number of tests only goes up, unless the plan says a test was removed on purpose.
3. **Docs:** the plan's phase row says done with the date, and the design doc matches the code.
4. **In game, for phases that change what players see** (A3 looks, A6 port block): QA has written the checklist, and the lead asks the user to run it. Results are recorded in the checklist.

### User's agreements (apply to every member)

These are repeated from the handoff, because they matter most:

- **Never commit, and never mention git.** The user handles git.
- **A user prompt ending in `?` is a question:** no file changes.
- **When the user says stop, everyone stops.**
- **Stay on the task.** No side quests without asking.

## Order of work

```
A0 (lead, alone)        restructure: core/ subproject, empty addons/create/
  │
A1 (lead)               write the api package from the design
  │
  ├── A2 (core)         tunnel-kind registry, transfer and redstone as kinds, new save format, TunnelService
  │     │
  │     ├── A4 (core)   events and queries in use by core
  │     └── A5 (core + QA) unknown kinds: raw entries, inert wall, test kind
  │
  ├── A3 (client)       face looks through ModelData. Can start against today's PortKind,
  │                     then switches to FaceLook when A2 lands
  │
  └── A6 (create)       starts when A2 and A3 are done: port block, kinetic on the API,
        │               Create removed from core
        A7 (create)     several kinetic tunnels per machine
A8 (QA + lead)          publishing workflow, Javadoc, "write a tunnel kind" guide
```

- **A0 comes first and alone** (agreed 2026-10-01). It moves every source file, so it must land before anyone works in parallel. It's part of A6 moved earlier; the plan should be updated if the user agrees.
- **Parallel work starts after A1:** core on A2, and client on A3.
- **A9 (port to `main`)** isn't this team's job. It's a separate session after A8.

## First session checklist (lead)

1. Read the handoff and the three plans it lists.
2. Ask the user: OK to do A0 (subprojects) first? OK to start with three members and add the rest later?
3. Create the tasks for A0 and A1, and do them.
4. Create the A2 and A3 tasks, then spawn the core and client engineers with: "read `docs/handoffs/api-split-handoff.md` and `docs/handoffs/api-split-team.md`, then take your tasks".
5. Spawn QA before the first test run is needed.
