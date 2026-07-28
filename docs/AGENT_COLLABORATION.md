# Cross-chat agent collaboration

RingWorld's Minecraft 26.1 port may be developed by two ChatGPT Desktop coding
agents at the same time. They must use separate Git checkouts and need a
reliable way to exchange task assignments, discoveries, blockers, commit
hashes, and handoffs.

The active arrangement uses two dedicated PCs and separate clones. GitHub
issue [#4](https://github.com/Delaser/RingWorld/issues/4) is therefore the
authoritative cross-PC coordination channel. The repository also provides
[`scripts/agent-comms.sh`](../scripts/agent-comms.sh) for the optional case
where both agents use worktrees from the same local clone.

## Dedicated-PC workflow

Both PCs need authenticated access to the private repository:

```sh
gh auth status
gh repo view Delaser/RingWorld
```

The secondary PC uses its own clone and a task branch created from the exact
commit named in the primary agent's assignment:

```sh
git clone https://github.com/Delaser/RingWorld.git RingWorld-agent2
cd RingWorld-agent2
git fetch origin
git switch --detach <assigned-base-commit>
git switch -c agent2/<assigned-task>
```

Read all existing coordination messages:

```sh
gh issue view 4 --repo Delaser/RingWorld --comments
```

Post an acknowledgement, finding, blocker, or handoff:

```sh
gh issue comment 4 --repo Delaser/RingWorld \
  --body "[SECONDARY][ACK S1] base=<sha> branch=agent2/26.1-audit"
```

Use these prefixes:

```text
[PRIMARY][ASSIGN S1]
[SECONDARY][ACK S1]
[SECONDARY][FINDING S1]
[SECONDARY][BLOCKED S1]
[SECONDARY][HANDOFF S1]
[PRIMARY][INTEGRATED S1]
```

Every substantive comment includes the task ID, base commit, branch, current
commit, files touched, validation run, and unresolved risks where applicable.
Long reports belong in a tracked document; the issue comment links the branch
and commit.

GitHub issue comments are durable project records, not a secret store. Never
post passwords, tokens, account files, private keys, non-public deployment
credentials, or player personal information.

## Required roles

Use these stable names:

```text
primary
secondary
```

The primary agent owns integration, topology, networking, and rendering. The
secondary agent owns the bounded tasks assigned in
[`MINECRAFT_26_1_PORT_PLAN.md`](MINECRAFT_26_1_PORT_PLAN.md).

## Same-clone local mailbox

This section applies only when both agents use worktrees from one clone. It
does not apply to the active dedicated-PC arrangement.

The script discovers the repository's shared Git directory with:

```sh
git rev-parse --git-common-dir
```

It hashes that common directory into a stable repository identity and stores
inboxes, archives, and presence files under:

```text
${TMPDIR}/ringworld-agent-comms-<repository-key>/
```

All worktrees made from the same clone derive the same key, so a message sent
from one worktree becomes visible to the other immediately. The mailbox is not
part of any branch, commit, archive, or distributable package. It is temporary
runtime coordination; durable decisions and handoffs still belong in commits
and documentation.

Set `RINGWORLD_AGENT_COMMS_DIR` to the same absolute writable path in both
agents if a fixed location is preferred.

This is local coordination, not a secret store. Never send passwords, tokens,
account files, private keys, or player personal information through it.

## Create a same-clone secondary worktree

The primary agent first publishes or identifies the exact integration commit.
From the main RingWorld checkout, create the secondary worktree:

```sh
git worktree add ../RingWorld-agent2 \
  -b agent2/26.1-audit \
  <integration-commit>
```

Open `/Users/chris/Documents/RingWorld-agent2` as the workspace for the second
ChatGPT Desktop task. Both folders remain separate checkouts but share one Git
common directory and therefore derive the same mailbox identity.

For later S-tasks, finish and hand off the current branch, then switch that
worktree to the next task branch based on the primary agent's named
integration commit. Never reuse uncommitted work as a new task base.

## Starting a session

Each agent begins its chat by reading:

```text
AGENTS.md
docs/MINECRAFT_26_1_PORT_PLAN.md
docs/AGENT_COLLABORATION.md
```

Then it initializes presence:

```sh
./scripts/agent-comms.sh init primary "online; integration branch"
./scripts/agent-comms.sh init secondary "online; awaiting assignment"
```

Run only the command matching the agent's role.

## Sending messages

Primary to secondary:

```sh
./scripts/agent-comms.sh send primary secondary \
  "Start S1 from commit <sha>. Own only the audit document."
```

Secondary to primary:

```sh
./scripts/agent-comms.sh send secondary primary \
  "S1 complete on commit <sha>; build not required; two unresolved mixins."
```

Messages are single-line records. Put long reports in a tracked document or
branch and send its path and commit hash through the mailbox.

## Receiving messages

Read and acknowledge all pending messages:

```sh
./scripts/agent-comms.sh receive primary
./scripts/agent-comms.sh receive secondary
```

Wait up to 30 seconds for a new message:

```sh
./scripts/agent-comms.sh wait primary 30
./scripts/agent-comms.sh wait secondary 30
```

`wait` returns as soon as a message arrives. Coding agents should use bounded
waits of 30 seconds and continue useful work if no message arrives.

Inspect pending messages without acknowledging them:

```sh
./scripts/agent-comms.sh peek secondary
```

Inspect already acknowledged messages:

```sh
./scripts/agent-comms.sh history secondary
```

## Presence and status

Update current activity:

```sh
./scripts/agent-comms.sh heartbeat primary "porting renderer P4"
./scripts/agent-comms.sh heartbeat secondary "working S2 storage tests"
```

View both agents:

```sh
./scripts/agent-comms.sh status
```

Presence is advisory. A stale heartbeat does not authorize another agent to
take over the same files.

## Coordination rhythm

Each agent should check its inbox:

- at the start of every chat turn;
- before modifying a shared or coordinated file;
- after a build or runtime test finishes;
- before committing;
- immediately after committing;
- before declaring a task blocked or complete.

Send a heartbeat when starting a task and a message when:

- a requested base commit is unavailable;
- file ownership must expand;
- an invariant appears to require redesign;
- serialization or protocol might change;
- a test exposes a failure owned by the other agent;
- a commit is ready for integration.

## File ownership

The port plan defines default ownership. Ownership can change only after a
message is acknowledged by both agents. The receiving agent replies with an
explicit confirmation such as:

```text
ACK ownership: secondary may edit RingWorldClient.java only for screenshot
API migration in S4; primary retains render-loop ownership.
```

Silence is not agreement.

## Handoff message

The secondary agent commits its scoped work, then sends:

```text
HANDOFF S2
base=<sha>
branch=agent2/26.1-storage
commit=<sha>
tests=./gradlew test build
known_failures=<none or concise list>
docs=ARCHITECTURE.md,OPERATIONS.md
```

Because mailbox messages are single-line, a detailed version should also be
placed in the branch description, commit message, or a tracked handoff file.

The primary agent acknowledges before cherry-picking:

```text
ACK HANDOFF S2 commit=<sha>; integration review starting
```

After integration:

```text
INTEGRATED S2 as <sha>; storage gate passed
```

## Conflict prevention

- Never run both agents in the same worktree.
- Never edit a file owned by the other lane without acknowledgement.
- Never force-push a handed-off branch.
- Never use a dirty integration branch as another task's base.
- Never stage generated runtime state.
- Never resolve a semantic conflict by keeping both implementations.
- The primary agent owns final conflict resolution and integration tests.

## Copy-paste bootstrap prompt for the second agent

The complete current prompt is maintained in
[`SECONDARY_AGENT_BOOTSTRAP_PROMPT.txt`](SECONDARY_AGENT_BOOTSTRAP_PROMPT.txt).

```text
You are the secondary RingWorld Minecraft 26.1 port agent. Read AGENTS.md,
docs/MINECRAFT_26_1_PORT_PLAN.md, and docs/AGENT_COLLABORATION.md completely.
Use a separate clone on the secondary PC. Read GitHub issue #4 and acknowledge
the current assignment there before editing. Work only on the explicitly
assigned S-task and file ownership. Do not alter topology invariants, weaken
mixin requirements, touch the live server, or stage generated/runtime files.
Before editing coordinated files, before committing, and after pushing, check
or message the primary agent through issue #4. Finish with a clean commit,
push the task branch, and post the exact handoff format from the port plan.
```
