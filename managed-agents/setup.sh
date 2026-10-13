#!/usr/bin/env bash
# One-time setup for Bob as a Managed Agent. Run after `ant auth login`, from the repo root:
#
#   bash managed-agents/setup.sh <folder with owner.md and vehicles/*.md>
#
# Creates the agent, its environment and a memory store seeded from the folder, and writes the
# IDs to managed-agents/ids.env (gitignored). Re-running skips whatever ids.env already has, so
# it never makes a second agent. To change Bob afterwards, update the agent instead:
#
#   ant beta:agents update --agent-id "$BOB_AGENT_ID" --version <current> < managed-agents/bob.agent.yaml
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
seed="${1:?usage: setup.sh <memory seed folder>}"
ids="$here/ids.env"
touch "$ids"
# shellcheck disable=SC1090
source "$ids"

save() { echo "$1=$2" >> "$ids"; export "$1=$2"; echo "$1=$2"; }

if [ -z "${BOB_AGENT_ID:-}" ]; then
  save BOB_AGENT_ID "$(ant beta:agents create < "$here/bob.agent.yaml" --transform id -r)"
fi
if [ -z "${BOB_ENVIRONMENT_ID:-}" ]; then
  save BOB_ENVIRONMENT_ID "$(ant beta:environments create < "$here/bob.environment.yaml" --transform id -r)"
fi
if [ -z "${BOB_MEMORY_STORE_ID:-}" ]; then
  save BOB_MEMORY_STORE_ID "$(ant beta:memory-stores create \
    --name "Garage Log" \
    --description "What earlier conversations learned about the owner and their vehicles that the app's data doesn't show: preferences, modifications, confirmed fixes, past diagnoses. owner.md, plus one note per vehicle under vehicles/." \
    --transform id -r)"
fi
# The app's own records, mirrored in by the app before each request and mounted read-only, so Bob
# looks up what a question needs instead of the app pasting a whole vehicle into every message.
if [ -z "${BOB_DATA_STORE_ID:-}" ]; then
  save BOB_DATA_STORE_ID "$(ant beta:memory-stores create \
    --name "Garage Log data" \
    --description "The Garage Log app's own records, kept in sync by the app: index.md lists every vehicle; each vehicle folder has specs, schedule, issues, fuel and service history by year. Read-only." \
    --transform id -r)"
fi

# Seed notes that aren't in the store yet. A note already there is left alone: once Bob has been
# writing to it, the store is the source of truth, not the seed folder.
existing="$(ant beta:memory-stores:memories list --memory-store-id "$BOB_MEMORY_STORE_ID" \
  --transform path --format jsonl < /dev/null)"
(cd "$seed" && find . -name '*.md' | sed 's|^\./||') | while read -r rel; do
  if grep -qxF "\"/$rel\"" <<< "$existing"; then
    echo "already in store: /$rel"
  else
    # < /dev/null: ant otherwise reads stdin as the request body, which here is this loop's list.
    # MSYS2_ARG_CONV_EXCL: Git Bash on Windows rewrites "/owner.md" into a Windows path.
    MSYS2_ARG_CONV_EXCL="--path=" ant beta:memory-stores:memories create \
      --memory-store-id "$BOB_MEMORY_STORE_ID" \
      "--path=/$rel" --content "$(cat "$seed/$rel")" --transform path -r < /dev/null
  fi
done
