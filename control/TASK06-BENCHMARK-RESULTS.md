# Java Spec Generator · Task 06 Development Benchmark

Last updated: 2026-11-06 (KST). Source contract: `control/TASK-06-BRIEF.md`.
Same baseline: `108de803e15aaa5467f4a163f6197ad6c12fb445`. Distinct isolated Git worktrees; sequential candidates. No independent correctness verification or merge. The initial `control/PROTOCOL.md` is retained as a historical setup snapshot; its original pre-run status flags are superseded by the run records below.

## Five-run consolidated comparison

All five development attempts started from commit `108de803e15aaa5467f4a163f6197ad6c12fb445`, in independent worktrees. **The retest timer begins at development start after preparation, while the other four timers begin at dispatch**, so elapsed times are descriptive rather than a strictly controlled speed ranking.

| Candidate | Timing origin | Origin → commit | Origin → normal completion | Target tests | Full suite | Changed files / added lines | Commit / status |
| --- | --- | ---: | ---: | --- | --- | ---: | --- |
| **GPT + AgentDock (retest)** | Development start after preparation | **8m 47s** | **9m 11.116s** | **4 PASS** | **49 PASS, directly observed** | **4 / +402** | `dd28303` · completed |
| GPT + AgentDock (original) | Dispatch | 7m 51.041s | 8m 30.640s | 5 PASS | 50 PASS, directly observed | 5 / +370 | `a073bfd` · completed |
| Terra Medium + Paseo | Dispatch | 18m 00s | **Unavailable** | 4 PASS | Unconfirmed | 5 / +185 | `d259006` · committed; usage-limit error afterward |
| DeepSeek High + Paseo | Dispatch | 22m 30.321s | 25m 04.344s | 5 PASS | 50 PASS, developer-reported | 4 / +611 | `d672dd3` · completed |
| DeepSeek Default + Paseo | Dispatch | 28m 14.378s | 31m 48.652s | 5 PASS | 50 PASS, developer-reported | 3 / +896 | `2ba40f1` · completed |

**Comparison limits:** Completion time includes the measured observation endpoint for each run, not necessarily identical phases. Test counts are from separately authored test suites and do not prove equivalent coverage or functional quality. No independent Verifier was run. Detailed retest evidence is in `control/AGENTDOCK-RETEST-RESULTS.md`; chronological entries are in `control/METRICS.csv`.

## Four-candidate comparison

| Metric | GPT direct AgentDock | Paseo Codex Terra Medium | Paseo DeepSeek High | Paseo DeepSeek Default |
| --- | --- | --- | --- | --- |
| Date (KST) | 11/06 | 11/05 | 11/06 | 11/06 |
| Worktree | `dev-agentdock` | `dev-terra` | `dev-deepseek` | `dev-deepseek-default` |
| Dispatch | 05:51:44.959 | 22:00:52 | 04:25:40.679 | 05:03:22.622 |
| Commit | 05:59:36 | 22:18:52 | 04:48:11 | 05:31:37 |
| **Dispatch → commit** | **7m 51.041s** | **18m 00s** | **22m 30.321s** | **28m 14.378s** |
| Finish observation (KST) | 06:00:15.599 | 22:19:28.424 error only | 04:50:45.023 | 05:35:11.274 |
| **Dispatch → normal completion observation** | **8m 30.640s** | **Unavailable** | **25m 04.344s** | **31m 48.652s** |
| Commit | `a073bfd` | `d259006` | `d672dd3` | `2ba40f1` |
| Added lines / files | +370 / 5 | +185 / 5 | +611 / 4 | +896 / 3 |
| Target tests | 5 passed (local report) | 4 passed (local report) | 5 passed (developer report and Surefire) | 5 passed (developer report) |
| Full suite tests | **50 passed (directly executed)** | Unconfirmed | 50 passed (developer report) | 50 passed (developer report) |
| Outcome | Commit + normal completion | Commit, then Codex usage-limit error | Normal completion | Normal completion |

### Interpretation

- On **time to commit**, direct GPT + AgentDock recorded 7m51s vs Terra 18m, DeepSeek High 22m30s, and DeepSeek Default 28m14s. This is a speed observation, not a verified quality ranking.
- On **dispatch → normal completion**, direct GPT + AgentDock recorded 8m31s, DeepSeek High 25m04s, DeepSeek Default 31m49s. Terra has no normal-completion timing, because its session hit the token usage limit after committing.
- Each candidate wrote its own test suite and code, so test counts and line counts do not imply matching test coverage. The targeted build includes parser implementation, local tests and Git commit. The exact clock-to-completion endpoint includes controller final-observation overhead.
- Developer agents used Paseo+OpenCode/Codex, while the GPT candidate called AgentDock remote tools directly. Harness and permissions differ. GPT had previous comparison context but did not inspect earlier candidate source; **do not claim a controlled model-only benchmark**.
- This is one run per configuration. No claim about statistical significance or independent functional equivalence.

## Follow-up and provenance

- Raw chronological events: `control/METRICS.csv` (older abandoned attempts retained separately).
- GPT direct run: `control/AGENTDOCK-DEV-RESULTS.md`; host stamps `control/AGENTDOCK-20261106-{DISPATCH,FINISHED}.txt`.
- DeepSeek High: `control/PASEO-DEV-RESULTS.md`.
- DeepSeek Default: `control/DEEPSEEK-DEFAULT-RESULTS.md`; adapter-default evidence recorded in related settings files.
- Canonical task and protocol: `control/TASK-06-BRIEF.md`, `control/PROTOCOL.md`.
- All four source candidates remain on isolated Git branches. No main/release changes.
- Independent quality verification should compare acceptance cases on a single predefined test set before interpreting relative implementation correctness. The reserved verifier worktrees have not been started.

## 2026-11-06 follow-up: GPT direct AgentDock **independent retest**

The original four-candidate results above remain unchanged. This is a fifth, independently tracked development run using the same baseline and brief.

| Metric | **GPT direct AgentDock retest** | Previous GPT direct | Terra Medium | DeepSeek High | DeepSeek Default |
| --- | --- | --- | --- | --- | --- |
| Timer origin | **Post-preparation development start** | Dispatch | Dispatch | Dispatch | Dispatch |
| Origin (KST) | 06:28:48.535 | 05:51:44.959 | 22:00:52 (11/05) | 04:25:40.679 | 05:03:22.622 |
| Commit (KST) | **06:37:36** | 05:59:36 | 22:18:52 (11/05) | 04:48:11 | 05:31:37 |
| Origin → commit | **8m 47s** | 7m 51.041s | 18m 00s | 22m 30.321s | 28m 14.378s |
| Normal completion endpoint (KST) | **06:37:59.651** | 06:00:15.599 | Unavailable (usage limit after commit) | 04:50:45.023 | 05:35:11.274 |
| Origin → normal completion | **9m 11.116s** | 8m 30.640s | Unavailable | 25m 04.344s | 31m 48.652s |
| Commit | `dd28303` | `a073bfd` | `d259006` | `d672dd3` | `2ba40f1` |
| Files / added lines | **4 / 402** | 5 / 370 | 5 / 185 | 4 / 611 | 3 / 896 |
| Targeted PASS | **4 tests** | 5 tests | 4 tests | 5 tests | 5 tests |
| Full suite PASS | **49 tests, observed** | 50 tests, observed | Unconfirmed | 50 reported | 50 reported |
| Outcome | **Normal completion, clean commit** | Normal completion | Usage limit after commit | Normal completion | Normal completion |

**Benchmark qualification:** Original candidates were timed from dispatch, often through coordinator or Paseo completion observation; the retest was timed from the start of actual code work **after environment preparation** to the post-commit host timestamp. Accordingly, even the 40.476-second difference between the two GPT direct completion figures is **not a like-for-like speed delta**. Likewise, test coverage cannot be ranked from different target test counts. All runs used the same starting commit but different implementations and execution conditions. These are descriptive results, not a controlled model-only performance comparison.

**Retest trace:** `control/AGENTDOCK-RETEST-RESULTS.md` and new `METRICS.csv` row `20261106-gpt-agentdock-retest`. The retest read the optional Task 06 plan; the requested line-range read inadvertently surfaced adjoining Task 07 plan text (unused), documented as a scope deviation in the detailed report. No prior source candidate was read/reused before development completion. No independent verifier or merge performed.

## 2026-11-06 GPT-6 Instant direct AgentDock run

Independent worktree dev-agentdock-instant at baseline 108de803; commit 2a158d5. Timer after preparation: start 06:57:26.284 KST, commit 07:01:00 KST (213.716s), normal completion observation 07:01:38.398 KST (252.114s). RED observed; targeted 2 PASS; full 47 PASS; 3 files +174. Optional plan not read. Previous GPT original 471.041s to commit / 510.640s completion; GPT retest ~527s / 551.116s; Terra 1080s to commit (no normal finish); DeepSeek High 1350.321s / 1504.344s; DeepSeek Default 1694.378s / 1908.652s. Different timer origins, self-authored test suites, and harnesses prohibit controlled speed or quality ranking. No independent verification. See AGENTDOCK-INSTANT-RESULTS.md.
