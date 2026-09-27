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
  (cd "$seed" && find . -name '*.md' | sed 's|^\./||') | while read -r rel; do
    ant beta:memory-stores:memories create --memory-store-id "$BOB_MEMORY_STORE_ID" \
      --path "/$rel" --content "@$seed/$rel" --transform path -r
  done
fi
