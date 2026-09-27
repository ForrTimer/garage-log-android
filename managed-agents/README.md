# Bob as a Managed Agent

Bob's configuration, kept as files so every change is reviewed and versioned. The app still
talks to the Messages API directly today (`data/ai/DirectClaudeClient`); this is step one of
moving Bob onto a server-hosted agent with memory.

| File | What it is |
| --- | --- |
| `bob.agent.yaml` | Model, instructions and tools. Created once; later edits are `ant beta:agents update`, which makes a new version. |
| `bob.environment.yaml` | The container Bob's file tools run in. No outbound network; web search runs server-side. |
| `setup.sh` | Creates the agent, environment and memory store, and seeds the store. Writes IDs to `ids.env`. |

The memory seed notes are **not** in this repo, because it's public and they describe a real
owner's vehicles. Keep them in a local folder (`owner.md`, `vehicles/<nickname>.md`).

## Setup

```sh
ant auth login
bash managed-agents/setup.sh ../garage-log-bob-memory
```

## Try it

```sh
source managed-agents/ids.env
ant beta:sessions create --agent "$BOB_AGENT_ID" --environment-id "$BOB_ENVIRONMENT_ID" \
  --title "Bob test" \
  --resource "{type: memory_store, memory_store_id: $BOB_MEMORY_STORE_ID}" \
  --transform id -r
ant beta:sessions connect <session id> --web
```

`connect --web` opens the Console session viewer, where you can chat and watch memory reads and
writes. Start the first message with the kind of request (Chat, Diagnosis or Schedule) and the
vehicle, as the app will.

Rough cost: tokens at Sonnet 5 rates, web searches at $10 per 1,000, plus $0.08 per hour a
session is running. Sessions can be given a hard dollar budget at creation.
