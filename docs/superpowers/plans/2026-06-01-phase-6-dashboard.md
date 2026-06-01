# Phase 6.4 Dashboard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a static dashboard page at `/dashboard.html` that visualizes the existing metrics REST API.

**Architecture:** Serve a Spring Boot static HTML resource from `src/main/resources/static`. The page uses ECharts and browser-native JavaScript to fetch `/api/ratelimit/stats`, render top summary values, render charts, and populate a limiter detail table.

**Tech Stack:** Spring Boot static resources, HTML, CSS, browser JavaScript, ECharts 5, JUnit 5, MockMvc.

---

## File Structure

Create or modify:

```text
.gitignore
README.md
src/main/resources/static/dashboard.html
src/test/java/com/example/ratelimiter/metrics/DashboardStaticResourceTest.java
docs/superpowers/specs/2026-06-01-phase-6-dashboard-design.md
docs/superpowers/plans/2026-06-01-phase-6-dashboard.md
```

Responsibilities:

- `.gitignore`: ignores `.superpowers/` visual-companion scratch files.
- `dashboard.html`: static dashboard UI and fetch/render logic.
- `DashboardStaticResourceTest`: verifies Spring serves `/dashboard.html`.
- `README.md`: documents Dashboard access.

## Task 1: Static Resource Test

**Files:**

- Create: `src/test/java/com/example/ratelimiter/metrics/DashboardStaticResourceTest.java`

- [ ] **Step 1: Write failing static resource test**

Create `src/test/java/com/example/ratelimiter/metrics/DashboardStaticResourceTest.java`:

```java
package com.example.ratelimiter.metrics;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DashboardStaticResourceTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void dashboardHtmlIsServed() throws Exception {
        mockMvc.perform(get("/dashboard.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("RateLimiter Dashboard")))
                .andExpect(content().string(containsString("/api/ratelimit/stats")))
                .andExpect(content().string(containsString("echarts")));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn -f .worktrees\dashboard\pom.xml test -Dtest=DashboardStaticResourceTest
```

Expected: test fails with 404 because `dashboard.html` does not exist.

## Task 2: Dashboard Page

**Files:**

- Create: `src/main/resources/static/dashboard.html`

- [ ] **Step 1: Add static dashboard page**

Create `src/main/resources/static/dashboard.html` with:

- top summary metrics
- allowed/rejected chart
- available permits chart
- limiter detail table
- refresh button
- empty and error states
- `fetch('/api/ratelimit/stats')`

- [ ] **Step 2: Run static resource test**

Run:

```powershell
mvn -f .worktrees\dashboard\pom.xml test -Dtest=DashboardStaticResourceTest
```

Expected: test passes.

## Task 3: README and Ignore Rules

**Files:**

- Modify: `.gitignore`
- Modify: `README.md`

- [ ] **Step 1: Ignore brainstorming scratch files**

Add to `.gitignore`:

```text
.superpowers/
```

- [ ] **Step 2: Update README**

Update current phase to Phase 6.4, add Dashboard to completed list, and document:

```powershell
mvn spring-boot:run
```

Then open:

```text
http://localhost:8080/dashboard.html
```

- [ ] **Step 3: Run full tests**

Run:

```powershell
mvn -f .worktrees\dashboard\pom.xml test
```

Expected: all tests pass.

- [ ] **Step 4: Commit**

Run:

```powershell
git -C .worktrees\dashboard add .gitignore README.md src/main/resources/static/dashboard.html src/test/java/com/example/ratelimiter/metrics/DashboardStaticResourceTest.java docs/superpowers/specs/2026-06-01-phase-6-dashboard-design.md docs/superpowers/plans/2026-06-01-phase-6-dashboard.md
git -C .worktrees\dashboard commit -m "feat: add rate limiter dashboard"
```

## Self-Review Checklist

- Dashboard uses `/api/ratelimit/stats`.
- No frontend build tool is introduced.
- Empty and error states are present.
- Static resource is tested.
- README documents local access.
- `.superpowers/` is ignored.
