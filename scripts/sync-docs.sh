#!/usr/bin/env bash
# Makes docs/ (and scripts/) on the current branch identical to another branch's committed copy,
# so both version branches carry the same docs.
#
#   scripts/sync-docs.sh [source-branch] [--yes]
#
# The source defaults to the other version branch: main <-> mc1.21.1/dev.
# Files that exist here but not on the source are removed. Nothing is committed; review with
# `git diff --staged`, then commit.
set -euo pipefail

PATHS=(docs scripts)
OTHER_BRANCHES=(main mc1.21.1/dev)

cd "$(git rev-parse --show-toplevel)"
current=$(git branch --show-current)

source_branch=""
assume_yes=false
for arg in "$@"; do
    case "$arg" in
        --yes|-y) assume_yes=true ;;
        -h|--help) sed -n '2,9p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *) source_branch="$arg" ;;
    esac
done

if [[ -z "$source_branch" ]]; then
    for branch in "${OTHER_BRANCHES[@]}"; do
        [[ "$branch" != "$current" ]] && source_branch="$branch"
    done
fi

if [[ "$source_branch" == "$current" ]]; then
    echo "Source and current branch are both '$current'." >&2
    exit 1
fi
if ! git rev-parse --verify --quiet "$source_branch^{commit}" > /dev/null; then
    echo "Branch '$source_branch' doesn't exist." >&2
    exit 1
fi

# Uncommitted doc edits would be overwritten; make them commit or stash first.
if [[ -n "$(git status --porcelain -- "${PATHS[@]}")" ]]; then
    echo "Uncommitted changes in ${PATHS[*]}; commit or stash them first:" >&2
    git status --short -- "${PATHS[@]}" >&2
    exit 1
fi

changes=$(git diff --stat HEAD "$source_branch" -- "${PATHS[@]}")
if [[ -z "$changes" ]]; then
    echo "${PATHS[*]} already match '$source_branch'."
    exit 0
fi

echo "Syncing ${PATHS[*]} on '$current' from '$source_branch':"
echo "$changes"

# The sync is one-way: anything written on this branch but not on the source is lost.
losing=$(git diff --numstat HEAD "$source_branch" -- "${PATHS[@]}" | awk '$2 != "0" { print "  " $3 " (-" $2 " lines)" }')
if [[ -n "$losing" ]]; then
    echo
    echo "These files lose lines that exist on '$current' but not on '$source_branch'."
    echo "If those lines are new work here, sync the other way instead:"
    echo "$losing"
    echo "  (see them with: git diff $source_branch HEAD -- ${PATHS[*]})"
fi

if ! $assume_yes; then
    read -r -p "Apply? [y/N] " answer || answer=""
    [[ "$answer" == [yY]* ]] || { echo "Nothing changed."; exit 1; }
fi

git restore --source="$source_branch" --staged --worktree -- "${PATHS[@]}"
echo "Done. Review with 'git diff --staged', then commit."
