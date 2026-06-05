---
change: improve-project-docs-consistency
design-doc: docs/superpowers/specs/2026-06-05-improve-project-docs-consistency-design.md
base-ref: 86c178df1cd158fceccf174b5994913e1041e97e
archived-with: 2026-06-05-improve-project-docs-consistency
---

# Improve Project Docs Consistency Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Align current project documentation and OpenSpec specs with the Temporal Workflow implementation, current API surface, and Maven test tracks.

**Architecture:** This is a documentation/specification-only change. Treat Java source, database schema, runtime configuration, and CI behavior as authoritative inputs, then update reader-facing docs and OpenSpec specs to match those inputs.

**Tech Stack:** Markdown, OpenSpec, Maven, Spring Boot 2.7, Temporal Workflow.

---

### Task 1: Current Documentation Facts

**Files:**
- Modify: `README.md`
- Modify: `docs/README.md`
- Modify: `docs/api/transfer-debug-api.md`
- Modify: `docs/demo/cross-account-transfer-demo.md`
- Modify: `docs/design/testing-strategy.md`
- Modify: `docs/decisions/0001-orchestrated-saga.md`
- Modify: `docs/decisions/0002-credit-failed-retry-no-compensation.md`

- [ ] **Step 1: Inspect current code and tests used as facts**

Run:

```bash
rg -n "class TransferController|@PostMapping|@GetMapping|enum TransferStatus|class TransferWorkflowImpl|class TransferActivitiesImpl|class .*Test|class .*IT" transfer-service account-service common
```

Expected: output identifies the current transfer API, status enum, Temporal workflow/activity classes, and enabled tests.

- [ ] **Step 2: Update validation command wording**

Edit README and testing strategy so `mvn test` is described as the fast track for `*Test`, and `mvn verify` is described as the full track for `*Test`, `*IT`, Testcontainers MySQL, and JaCoCo gates. Mention Docker only for the full track.

- [ ] **Step 3: Update API and demo docs**

Edit API/demo docs so the current interface list contains only:

```text
POST /transfers
POST /transfers/{transferId}/review
GET /transfers/{transferId}
```

Remove any current-interface wording for `/withdraw-result`, `/{transferId}/retry`, old retry scheduler/service calls, or manual retry examples.

- [ ] **Step 4: Update testing strategy and ADR wording**

Replace old `TransferRetryService`, `TransferRetryScheduler`, and `retryOne` descriptions with Temporal Workflow, Activity, and RetryPolicy wording. Keep “orchestrated Saga” as the architectural pattern, but name Temporal as the current implementation carrier.

### Task 2: OpenSpec Main Specs and Delta Specs

**Files:**
- Create: `openspec/specs/project-documentation/spec.md`
- Modify: `openspec/specs/automated-testing/spec.md`
- Modify: `openspec/specs/continuous-integration/spec.md`
- Modify: `openspec/specs/temporal-workflow-orchestration/spec.md`
- Modify: `openspec/changes/improve-project-docs-consistency/specs/project-documentation/spec.md`
- Modify: `openspec/changes/improve-project-docs-consistency/specs/automated-testing/spec.md`
- Modify: `openspec/changes/improve-project-docs-consistency/specs/continuous-integration/spec.md`
- Modify: `openspec/changes/improve-project-docs-consistency/specs/temporal-workflow-orchestration/spec.md`

- [ ] **Step 1: Add project-documentation main spec**

Create the main spec with a concrete Purpose and requirements for current implementation facts, test command boundaries, and enabled-test coverage wording.

- [ ] **Step 2: Fix existing main specs**

Remove `TBD` Purpose text from CI spec. Replace nonexistent `FROZEN` status references with current observable states: `WAIT_REVIEW` for manual review after freeze, and continued workflow execution for automatic mode.

- [ ] **Step 3: Keep delta specs aligned**

Ensure the change-local delta specs describe the same requirements and scenarios that the main specs will contain after archive, without adding business behavior outside this documentation consistency change.

### Task 3: Verification and Comet State

**Files:**
- Modify: `openspec/changes/improve-project-docs-consistency/tasks.md`
- Modify: `openspec/changes/improve-project-docs-consistency/.comet.yaml`

- [ ] **Step 1: Run strict OpenSpec validation**

Run:

```bash
openspec validate --all --strict
```

Expected: validation passes for all active and main specs.

- [ ] **Step 2: Run Maven verification commands**

Run:

```bash
mvn -q -DskipTests compile
mvn -q test -DfailIfNoTests=false
```

Expected: compile and fast tests pass. Do not report `mvn verify` as passed unless Docker-backed full verification is actually run.

- [ ] **Step 3: Scan old implementation remnants**

Run:

```bash
rg -n "TransferRetryService|TransferRetryScheduler|retryOne|withdraw-result|/\\{transferId\\}/retry|FROZEN"
```

Expected: remaining hits are either historical archive/process records or explicit “old implementation removed” explanations, not current interface lists, current coverage claims, or current status model descriptions.

- [ ] **Step 4: Update tasks and guard**

Mark all `openspec/changes/improve-project-docs-consistency/tasks.md` items complete only after the corresponding evidence exists, then run:

```bash
COMET_ENV="${COMET_ENV:-$(find . "$HOME"/.*/skills "$HOME/.config" "$HOME/.gemini" -path '*/comet/scripts/comet-env.sh' -type f -print -quit 2>/dev/null)}"
. "$COMET_ENV"
"$COMET_BASH" "$COMET_GUARD" improve-project-docs-consistency build --apply
```

Expected: build guard passes and advances the change to verify.
