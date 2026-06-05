---
change: merge-v1-test-v2-docs-tests
design-doc: docs/superpowers/specs/2026-06-05-merge-v1-test-v2-docs-tests-design.md
base-ref: 0c1836c79bbec65fefcad6296549742a3b3f2721
archived-with: 2026-06-05-merge-v1-test-v2-docs-tests
---

# Merge V1 Test V2 Docs Tests Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Merge `claude/v1-test` and `claude/v2` into a new branch while preserving v2 production changes and restoring v1 test, documentation, CI, and OpenSpec baselines.

**Architecture:** Use `claude/v1-test` as the baseline branch, merge v2 production code in, then restore tests/docs/CI/OpenSpec paths from v1 where v2 deleted or weakened them. Tests that target removed Saga classes are migrated to Temporal-equivalent behavior rather than deleted.

**Tech Stack:** Git, Maven multi-module, JDK 8, Spring Boot 2.7.18, Temporal SDK, Testcontainers MySQL, JaCoCo, OpenSpec/Comet.

archived-with: 2026-06-05-merge-v1-test-v2-docs-tests
---

### Task 1: Branch And Merge Setup

**Files:**
- Modify: repository git branch state
- Modify: `openspec/changes/merge-v1-test-v2-docs-tests/tasks.md`

- [ ] **Step 1: Confirm source branches exist**

Run:

```bash
git branch --list claude/v1-test claude/v2
```

Expected: both branches are listed.

- [ ] **Step 2: Create merge branch from v1 baseline**

Run:

```bash
git checkout -b claude/merge-v1-test-v2-docs-tests claude/v1-test
```

Expected: branch switches to `claude/merge-v1-test-v2-docs-tests`.

- [ ] **Step 3: Merge v2 without committing**

Run:

```bash
git merge --no-commit --no-ff claude/v2
```

Expected: merge either stops with conflicts or stages merge changes without committing.

- [ ] **Step 4: Record conflict categories**

Run:

```bash
git diff --name-only --diff-filter=U
```

Expected: conflicting paths are visible if any.

- [ ] **Step 5: Mark branch preparation tasks**

Edit `openspec/changes/merge-v1-test-v2-docs-tests/tasks.md` and mark 1.1, 1.2, and 1.3 complete after merge conflicts are classified and resolved.

### Task 2: Resolve Production Code And Build Configuration

**Files:**
- Modify: `common/src/main/java/com/demo/transfer/common/TransferStatus.java`
- Modify: `account-service/src/main/java/com/demo/transfer/account/service/AccountAssetService.java`
- Modify: `account-service/src/main/java/com/demo/transfer/account/web/AccountAssetController.java`
- Modify: `transfer-service/src/main/java/com/demo/transfer/transfer/**`
- Modify: `pom.xml`
- Modify: `account-a-service/pom.xml`
- Modify: `account-b-service/pom.xml`
- Modify: `account-service/pom.xml`
- Modify: `transfer-service/pom.xml`

- [ ] **Step 1: Keep v2 production implementation**

For production Java files and Temporal runtime configuration, resolve conflicts toward `claude/v2` behavior, retaining Temporal Workflow/Activity/Worker, `TransferOrderStateService`, initialization failure handling, freeze failure handling, and business logging.

- [ ] **Step 2: Preserve JDK 8 and existing Maven conventions**

Review Maven files to ensure `source/target` remains `1.8`, Temporal dependencies remain scoped appropriately, and test dependencies do not leak into runtime scope.

- [ ] **Step 3: Run compilation**

Run:

```bash
mvn -q -DskipTests compile
```

Expected: compile succeeds. If it fails, fix the smallest production/build issue and rerun.

- [ ] **Step 4: Mark build configuration tasks**

Edit `tasks.md` and mark 4.2 and 4.3 complete once compile succeeds.

### Task 3: Restore And Adapt Tests

**Files:**
- Modify: `account-service/src/test/**`
- Modify: `transfer-service/src/test/**`
- Modify: `account-service/src/test/resources/**`
- Modify: `transfer-service/src/test/resources/**`
- Modify: `openspec/changes/merge-v1-test-v2-docs-tests/tasks.md`

- [ ] **Step 1: Restore v1 test support and resources**

Restore v1 versions for deleted test support classes and resources unless v2 has an equivalent current implementation:

```bash
git checkout claude/v1-test -- account-service/src/test transfer-service/src/test
```

Then reapply v2 Temporal-specific tests where needed from `claude/v2`.

- [ ] **Step 2: Keep v2 Temporal tests**

Ensure these v2 tests are present or equivalent coverage exists:

```text
transfer-service/src/test/java/com/demo/transfer/transfer/workflow/TransferActivitiesImplTest.java
transfer-service/src/test/java/com/demo/transfer/transfer/workflow/TransferWorkflowImplTest.java
```

- [ ] **Step 3: Migrate obsolete Saga tests**

If restored tests reference removed classes such as `TransferSagaService`, `TransferRetryService`, or `TransferRetryScheduler`, migrate their assertions to `TransferWorkflowImpl`, `TransferActivitiesImpl`, `TransferController`, or `TransferOrderStateService`.

- [ ] **Step 4: Run fast tests**

Run:

```bash
mvn -q test -DfailIfNoTests=false
```

Expected: fast tests pass without Docker.

- [ ] **Step 5: Mark test tasks**

Edit `tasks.md` and mark 2.1 through 2.5 complete once fast tests pass and v1/v2 test intent is preserved.

### Task 4: Restore Documentation, CI, And OpenSpec Baselines

**Files:**
- Modify: `.github/workflows/ci.yml`
- Modify: `.gitlab-ci.yml`
- Modify: `AGENTS.md`
- Modify: `README.md`
- Modify: `docs/**`
- Modify: `openspec/specs/**`
- Modify: `openspec/changes/archive/**`
- Modify: `openspec/changes/merge-v1-test-v2-docs-tests/tasks.md`

- [ ] **Step 1: Restore v1 CI and docs baseline**

Restore v1 versions for CI, docs, and OpenSpec baseline paths that v2 deleted or weakened:

```bash
git checkout claude/v1-test -- .github/workflows/ci.yml .gitlab-ci.yml AGENTS.md README.md docs openspec/specs openspec/changes/archive
```

- [ ] **Step 2: Reapply v2 factual updates**

Review v2 docs changes and reapply only factual updates required for Temporal, initialization failure, freeze failure, or demo correctness. Do not delete v1 navigation, ADRs, or testing strategy.

- [ ] **Step 3: Preserve v2 active OpenSpec changes when relevant**

Keep v2 active changes for init/freeze failure if they correspond to retained production behavior:

```text
openspec/changes/add-init-failed-terminal-state/**
openspec/changes/fix-freeze-failure-terminal-state/**
```

- [ ] **Step 4: Mark docs and CI tasks**

Edit `tasks.md` and mark 3.1 through 3.4 and 4.1 complete after docs/CI/OpenSpec paths are restored and reviewed.

### Task 5: Final Verification And Task Closure

**Files:**
- Modify: `openspec/changes/merge-v1-test-v2-docs-tests/tasks.md`
- Modify: `.comet.yaml` state via Comet scripts

- [ ] **Step 1: Check whitespace and conflict markers**

Run:

```bash
git diff --check
rg -n '(<{7}|>{7})' .
```

Expected: no whitespace errors and no conflict markers.

- [ ] **Step 2: Run full verification when possible**

Run:

```bash
mvn -q verify -DfailIfNoTests=false
```

Expected: verify succeeds when Docker/Testcontainers are available. If environment blocks Docker, record exact reason in the final report and keep `mvn test` as the minimum passed verification.

- [ ] **Step 3: Review final diff scope**

Run:

```bash
git status --short --branch
git diff --stat
```

Expected: only this change's intended files are modified; untracked agent/skill files remain unstaged.

- [ ] **Step 4: Mark remaining tasks complete**

Edit `tasks.md` and mark 5.1 through 5.4 complete after verification evidence is recorded.

- [ ] **Step 5: Commit change-scoped files**

Stage only files related to this change and commit with a Conventional Commit message:

```bash
git add <change-scoped-files>
git commit -m "chore(merge): 合并 v1-test 与 v2 并对齐测试文档" -m "Made-with: Codex"
```
