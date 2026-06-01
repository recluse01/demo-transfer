## Context

测试体系已落地双轨测试（surefire `*Test` / failsafe `*IT`）+ JaCoCo 合并覆盖率门禁，并有 GitHub Actions CI（单 job 跑 `mvn -B verify`）。但覆盖率结果只以 artifact（`jacoco.xml`/HTML，需本地下载解压）和 `check-merged` 门禁（仅失败时非零退出）两种形态存在，**PR 上看不到本次改动代码的覆盖情况**。

已核验的现状事实（实现据此，不再假设）：

- `mvn -B verify` 经 `report-merged`（绑定 `verify` 阶段，从 `jacoco-merged.exec` 生成）为每个模块产出 `target/site/jacoco/jacoco.xml`，含单元+IT 合并覆盖数据。
- 现有 `ci.yml` 单 `verify` job，触发器 `push` + `pull_request`，无 `permissions` 块。
- pom 的 JaCoCo 排除项（`common/**`、`config/**`、`*Application*`、`*Request*`/`*Response*`）使这些类不进入 `jacoco.xml`，故改动文件覆盖率天然只覆盖纳入度量的文件。

完整背景见原设计稿 `docs/superpowers/specs/2026-06-01-pr-diff-coverage-visualization-design.md`。

## Goals / Non-Goals

**Goals:**

- 每个 PR 上自动展示增量（diff）覆盖率 + 整体覆盖率，GitHub 原生、数据不出库、无状态、低维护。
- 改动面收敛为**单一文件 `.github/workflows/ci.yml`**。

**Non-Goals:**

- 覆盖率历史趋势图（需持久化历史）。
- 测试结果摘要进 job summary（独立关注点，下一个 change）。
- CI 缓存/并行提速、外部 SaaS。
- 上一笔 WireMock 读超时修复的文档沉淀（单独的 docs commit，不混入本 change）。
- 引入任何新的增量硬门禁（本期只展示）。

## Decisions

### D1：复用现有 `jacoco.xml`，不改 Maven

在 `verify` job 末尾加一步 `Madrapps/jacoco-report@v1.7.2`：

- `paths: ${{ github.workspace }}/**/target/site/jacoco/jacoco.xml`（glob 自动收齐全部模块）；
- `token: ${{ secrets.GITHUB_TOKEN }}`；
- `min-coverage-overall: 70`（与 `check-merged` 70% 对齐）；
- `min-coverage-changed-files: 80`（改动代码展示标准，更严，**只展示不卡构建**）；
- `title: 📊 覆盖率报告`、`update-comment: true`（同 PR 更新同一条评论，不刷屏）。

该 action 无状态：用 PR diff 识别改动文件，结合当前 `jacoco.xml` 算改动文件/行覆盖率，无需历史基线。
*备选*：自写脚本解析 XML（路线 B）——维护成本高，仅作回退。

### D2：best-effort 展示，`always()` 语义收窄

step 条件 `if: always() && github.event_name == 'pull_request'`。

定位明确为「**有 XML 就贴/更新，没有就跳过**」，**不承诺测试失败场景一定有评论**——因为单测在 `test` 阶段失败时构建即停，`jacoco.xml` 根本不生成。`always()` 仅用于在 job 内前序步骤非成功时仍尝试执行（best-effort），而非保证产出评论。

*为什么不去掉 `always()`*：IT 在 `verify` 阶段失败、但报告已生成的窄窗口下，仍希望尽力贴出评论。

### D3：只展示不卡构建，硬门禁维持单一

`min-coverage-changed-files: 80` 仅驱动评论里的 ✅/❌ 标记，不引入硬门禁。现有 `check-merged`（整体 70%）仍是唯一会使构建失败的覆盖率门禁。增量门禁（用 `github-script` 读 action 的 `coverage-changed-files` 输出来 fail）作为以后想收紧时的一行开关，本期不开——避免一上来就堵 PR。

### D4：显式声明 job 权限

`verify` job 加 `permissions: { contents: read, pull-requests: write }`，否则 `GITHUB_TOKEN` 无权在 PR 贴评论（尤其组织策略把默认 token 限为只读时）。

### D5：action 版本用 major/固定 tag `@v1.7.2`

与项目其它 action（`checkout@v4`/`setup-java@v4`）的 tag 钉版约定一致，不上 SHA 钉版（YAGNI；失效回退见 D1 路线 B）。

## Risks / Trade-offs

- [Madrapps 在匹配不到任何 path 时可能报错而非跳过，导致 best-effort 失效、把已失败的 job 二次染红] → 实现 PR 中专门造一次「单测故意失败」的提交实测，确认 step 优雅跳过；若该版本会报错，则给 step 套 `continue-on-error: true` 兜底。
- [Madrapps 的 `min-coverage-*` 是否会让 step 退非零，直接关系到「只展示不卡构建」是否成立] → 实现时实测 step 退出码确认；若会 fail 则改用 `continue-on-error: true` 或移除阈值，保证不卡构建。
- [Fork PR 的 `GITHUB_TOKEN` 强制只读、secrets 不可用，贴评论会失败] → 本项目为内部仓库，接受 fork PR 不贴评论，天然落在 D2 的 best-effort 内，不做额外处理。
- 风险面整体低：纯 CI 配置改动，无生产代码、无 Maven 改动、无运行时依赖。
