# Patches Between Branches

A queue of changes made on one branch that still need to reach the other. It lets you keep working on one branch without switching back and forth.

- `to-main.md`: made on `mc1.21.1/dev`, still needed on `main`.
- `to-1.21.1.md`: made on `main`, still needed on `mc1.21.1/dev`. Create it when the first entry comes up.

## How an entry is applied

Each entry says **how** it moves. There are three ways.

**Docs are kept identical on both branches** for now: after committing doc changes, switch to the other branch and run `scripts/sync-docs.sh`. It makes `docs/` and `scripts/` there match the branch you came from and stages the result. The sync is one-way, so edit docs on one branch at a time. The script warns when the sync would remove lines that exist only on the current branch. Docs therefore don't need entries in this queue.

**1. Copy the file (`copy`).** Use this when the file is meant to be identical on both branches: docs, plain-logic classes, anything with no Minecraft-version API in it. On the target branch:

```sh
git checkout mc1.21.1/dev -- docs/
git checkout mc1.21.1/dev -- src/main/java/dev/thefern2/tinytunnels/machine/MachineSize.java
```

This overwrites the target's copy with the source's, and stages it. Check `git diff --staged` before committing.

- **Never copy a seam file.** Seam files are listed in `docs/plans/tiny-tunnels-1-21-1-backport.md`: the transfer, persistence, teleport, lifecycle, registration, datagen and GameTest code, plus build files. Copying one would bring the other version's API across and break the build.
- **Only copy a directory when the target hasn't changed anything in it.** For `docs/`, check first with `git diff <target> <source> -- docs`. The diff should show only the source's own changes.

**2. Cherry-pick the commit (`pick`).** Use this for a commit that touches only files that are identical on both branches, or that apply cleanly.

```sh
git cherry-pick -x <sha>
```

**3. Redo it by hand (`redo`).** Use this when the change touches seam files, so the code differs per branch. The entry describes the change; make it again on the target branch.

After applying, move the entry to **Done** in the same file, with the date.
