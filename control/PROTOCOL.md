# Task 06 benchmark protocol (2026-11-05 KST)

Baseline commit: 108de803e15aaa5467f4a163f6197ad6c12fb445
Source repo: C:\Workspace\github\java-spec-generator
Canonical Task 06 brief: control\TASK-06-BRIEF.md
Original development plan is tracked in each worktree at docs\superpowers\plans\2026-11-05-program-spec-generator-implementation.md.

Worktrees:
dev-agentdock     bench/task06/dev-agentdock     | GPT direct AgentDock | reserved, NOT launched
dev-terra         bench/task06/dev-terra         | Paseo Codex gpt-6.1-terra medium
dev-deepseek      bench/task06/dev-deepseek      | Paseo OpenCode opencode-go/deepseek-v4.1-flash high
integration       bench/task06/integration       | holds candidate integrated implementation | not yet merged
verify-agentdock  bench/task06/verify-agentdock  | verification reserved, NOT launched
verify-terra      bench/task06/verify-terra      | verification reserved, NOT launched
verify-deepseek   bench/task06/verify-deepseek   | verification reserved, NOT launched

DEVELOPMENT PHASE ONLY. Two Paseo runs sequential, Terra first then DeepSeek. Do not start the next while previous is running. Do not run AgentDock candidate or independent verifier, do not merge into integration/main, do not modify original feature branch. Do not use old abandoned Task06 changes.
For each run, record independent wall clock ISO timestamps: dispatch before creating worker, first observed action if available, finish after actual completion, elapsed wall seconds, final status, output branch/commit, reported test count if performed, and blockers. Mark estimates/missing timestamp as unavailable. The benchmark owner will execute GPT direct AgentDock afterward.

The 3 verification worktrees currently have baseline commit, NOT an integrated Task06 commit. Before verification, reset/recreate them to the exact SAME integration commit under a fresh session for each participant. Do not start verification until separately authorized.
