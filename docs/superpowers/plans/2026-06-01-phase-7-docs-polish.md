# Phase 7 Documentation Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the repository readable and runnable for a new reviewer from README and focused docs alone.

**Architecture:** This phase does not change runtime code. It reorganizes public-facing documentation around quick start, architecture, feature coverage, benchmarks, operational notes, and known limitations while keeping detailed implementation history in existing specs and plans.

**Tech Stack:** Markdown, Mermaid diagrams, Maven verification.

---

### Task 1: README Public Handoff

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Rewrite the opening section**

Replace the current short opening with a concise project summary, status, and table of contents. Keep the project name unchanged.

- [ ] **Step 2: Add quick start commands**

Add Java/Maven prerequisites, `mvn test`, `mvn spring-boot:run`, dashboard URL, metrics URL, and benchmark smoke commands.

- [ ] **Step 3: Add feature matrix and module map**

Document local algorithms, Redis limiter, adaptive limiter, Spring AOP, SPI, REST metrics, dashboard, and benchmark comparison entries.

- [ ] **Step 4: Add limitations and roadmap**

State unfinished or intentionally scoped items honestly: no dynamic refresh, dashboard is local static, Redis integration requires explicit wiring, benchmark numbers must be locally reproduced.

- [ ] **Step 5: Verify README content**

Run: `Select-String -Path .worktrees\docs-polish\README.md -Pattern "Quick Start|Architecture|Known Limitations|Roadmap"`

Expected: all four sections are present.

### Task 2: Focused Supporting Docs

**Files:**
- Create: `docs/ARCHITECTURE.md`
- Create: `docs/QUICK_START.md`

- [ ] **Step 1: Add architecture notes**

Create a Mermaid diagram and explain how the core API, algorithms, Redis wrapper, adaptive components, AOP, SPI, metrics endpoint, and dashboard fit together.

- [ ] **Step 2: Add quick start guide**

Create a command-first guide for running tests, starting the app, visiting dashboard, reading metrics, and running JMH smoke benchmarks.

- [ ] **Step 3: Link docs from README**

Add links to `docs/ARCHITECTURE.md` and `docs/QUICK_START.md` in the README documentation section.

- [ ] **Step 4: Verify docs exist**

Run: `Test-Path .worktrees\docs-polish\docs\ARCHITECTURE.md; Test-Path .worktrees\docs-polish\docs\QUICK_START.md`

Expected: both commands print `True`.

### Task 3: Verification and Commit

**Files:**
- Verify all modified documentation files.

- [ ] **Step 1: Run full tests**

Run: `mvn -f .worktrees\docs-polish\pom.xml test`

Expected: build success with zero failures.

- [ ] **Step 2: Review diff**

Run: `git -C .worktrees\docs-polish diff --stat`

Expected: only documentation files are changed.

- [ ] **Step 3: Commit**

Run:

```powershell
git -C .worktrees\docs-polish add README.md docs/ARCHITECTURE.md docs/QUICK_START.md docs/superpowers/plans/2026-06-01-phase-7-docs-polish.md
git -C .worktrees\docs-polish commit -m "docs: polish public project handoff"
```

Expected: commit succeeds on `codex/docs-polish`.
