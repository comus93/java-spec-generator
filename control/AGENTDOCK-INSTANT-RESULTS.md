# Task 06 · GPT-6 Instant / Direct AgentDock-MCSVR

- Model: GPT-6 Instant (current chat); tools: AgentDock-MCSVR direct, no delegated coding agent.
- Host: MCSVR Windows; baseline: 108de803e15aaa5467f4a163f6197ad6c12fb445.
- Isolated worktree: C:\Workspace\github\java-spec-generator-bench\dev-agentdock-instant
- Branch: bench/task06/dev-agentdock-instant
- Development start / first actual development action: 2026-11-06 06:57:26.2835565 +09:00 (first exploration command).
- First test file write and RED: 2026-11-06 06:59:11.1454244 +09:00 (RED compilation failure, missing JspFacts and PmdJspAnalyzer).
- First targeted PASS: 2026-11-06 07:00:19.9075282 +09:00 (2 tests; 0 failures).
- Full test PASS: 2026-11-06 07:00:40.0099720 +09:00 invocation, completion observed by 07:00:51.9369149 +09:00 (47 tests, 0 failures/errors).
- Git commit: 2026-11-06 07:01:00 +09:00 (Git commit timestamp); observed after command at 07:01:03.8902897 +09:00.
- Normal completion observation: 2026-11-06 07:01:38.3976099 +09:00 (clean Git status confirmed).
- Start to commit (Git timestamp): 213.716 seconds; start to observed commit: 217.607 seconds.
- Start to normal completion observation: 252.114 seconds.
- Commit: 2a158d582149b0d6346d157d2199bb9812dbc33b.
- Files changed: 3; lines added: 174; lines removed: 0.
  - JspFacts.java +29
  - PmdJspAnalyzer.java +110
  - PmdJspAnalyzerTest.java +35
- Mandatory brief read as UTF-8: yes. Optional development plan read: **no**.
- Prior implementations and benchmark records before development: not read.
- Errors: initial shell lacked java/mvn in PATH; used installed JDK21 and Maven3.9.16 via explicit paths. First RED was expected. Full suite passed. First attempt to commit failed because custom PATH omitted git; retried successfully with default PATH. No blockers.
- Implementation: PMD JSP parser with registry, TextDocument, ParserTask and JSP visitor. JSP fields, ordered tag/EL/directive flows and anchor-only LINK_CLICK events.
- Limitations: Only 2 developer-authored Task06 tests, fewer than other candidates. No independent verification. Attribute/EL extraction and malformed legacy JSP behavior require external acceptance testing. No merge or deployment.

## Comparison (descriptive, not controlled)

| Candidate | Origin → commit | Normal completion | Full tests |
|---|---:|---:|---|
| GPT-6 Instant + AgentDock | 3m 33.716s | 4m 12.114s | 47 observed |
| GPT AgentDock original | 7m 51.041s | 8m 30.640s | 50 observed |
| GPT AgentDock retest | ~8m 47s | 9m 11.116s | 49 observed |
| Terra Medium + Paseo | 18m 00s | unavailable (usage-limit error) | unconfirmed |
| DeepSeek High + Paseo | 22m 30.321s | 25m 04.344s | 50 reported |
| DeepSeek Default + Paseo | 28m 14.378s | 31m 48.652s | 50 reported |

Instant started its timer after preparation, unlike dispatch-based runs. Test suites differ. Neither speed nor local test counts establish comparable code quality.
