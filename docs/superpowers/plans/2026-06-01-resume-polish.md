# Phase 8 Resume Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add standard Actuator/Micrometer monitoring and resume-oriented project documentation.

**Architecture:** A Spring `MeterBinder` reads existing `RateLimiterMetricsService` snapshots and exposes aggregate Micrometer gauges. Documentation layers summarize project value without changing core behavior.

**Tech Stack:** Spring Boot Actuator, Micrometer, JUnit 5, MockMvc, Markdown.

---

### Task 1: Actuator/Micrometer Metrics

**Files:**
- Modify: `pom.xml`
- Create: `src/main/resources/application.properties`
- Create: `src/main/java/com/example/ratelimiter/metrics/RateLimiterMetricsBinder.java`
- Create: `src/test/java/com/example/ratelimiter/metrics/RateLimiterMetricsBinderTest.java`
- Create: `src/test/java/com/example/ratelimiter/metrics/ActuatorRateLimiterMetricsTest.java`

- [ ] **Step 1: Write failing tests**

Write tests for aggregate gauge values and Actuator metric endpoint exposure.

- [ ] **Step 2: Run tests to verify failure**

Run: `mvn -f .worktrees\resume-polish\pom.xml test -Dtest=RateLimiterMetricsBinderTest,ActuatorRateLimiterMetricsTest`

Expected: compilation or test failure because binder and actuator dependency are not implemented yet.

- [ ] **Step 3: Implement minimal metrics integration**

Add Actuator dependency, endpoint exposure properties, and `RateLimiterMetricsBinder`.

- [ ] **Step 4: Run targeted tests**

Run: `mvn -f .worktrees\resume-polish\pom.xml test -Dtest=RateLimiterMetricsBinderTest,ActuatorRateLimiterMetricsTest`

Expected: both tests pass.

### Task 2: Resume Documentation

**Files:**
- Create: `docs/INTERVIEW_NOTES.md`
- Create: `docs/BENCHMARK_REPORT.md`
- Modify: `README.md`
- Modify: `docs/ARCHITECTURE.md`
- Modify: `docs/QUICK_START.md`

- [ ] **Step 1: Add interview notes**

Document project pitch, architecture talking points, tradeoffs, and likely questions.

- [ ] **Step 2: Add benchmark report**

Document benchmark commands and smoke results observed during development, clearly labeling them as smoke results rather than final performance claims.

- [ ] **Step 3: Update public docs**

Link the new docs and add Actuator/Micrometer commands to README and quick start.

- [ ] **Step 4: Verify docs references**

Run: `Select-String -Path .worktrees\resume-polish\README.md -Pattern "Actuator|Micrometer|Interview|Benchmark Report"`

Expected: all terms are present.

### Task 3: Final Verification and Commit

**Files:**
- All modified files.

- [ ] **Step 1: Run full tests**

Run: `mvn -f .worktrees\resume-polish\pom.xml test`

Expected: build success with zero failures.

- [ ] **Step 2: Review diff**

Run: `git -C .worktrees\resume-polish diff --stat`

Expected: metric integration and documentation files only.

- [ ] **Step 3: Commit**

Run:

```powershell
git -C .worktrees\resume-polish add .
git -C .worktrees\resume-polish commit -m "feat: add actuator metrics and resume docs"
```
