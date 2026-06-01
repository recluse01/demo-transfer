## Why

现有覆盖率结果只以两种形态存在：每模块的 `target/site/jacoco/jacoco.xml`（需下载 CI artifact 本地解压才能看）、以及全量 JaCoCo `check-merged` 门禁（整体行覆盖 ≥70%，仅在构建失败时以非零退出体现）。**PR 上看不到本次改动代码的覆盖情况**——评审者无法一眼判断新增/改动的代码有没有被测到，覆盖率可能在「整体仍达标」的掩护下于新代码处悄悄下滑。

## What Changes

- 在 CI 的 `verify` job 末尾新增一步 `Madrapps/jacoco-report@v1.7.2`，复用现有 `jacoco.xml`（双轨合并后的覆盖数据），在每个 **PR** 上自动贴一条「📊 覆盖率报告」评论，展示整体 + 改动文件的覆盖率。
- 为 `verify` job 显式声明 `permissions: { contents: read, pull-requests: write }`，使 `GITHUB_TOKEN` 能在 PR 上贴评论。
- 该步定位为 **best-effort 展示**：`if: always() && github.event_name == 'pull_request'`；有 `jacoco.xml` 就贴/更新评论（`update-comment: true` 不刷屏），没有就跳过；**不引入任何新的硬门禁**，现有 `check-merged`（整体 70%）仍是唯一的构建硬门禁。

非目标（YAGNI）：覆盖率历史趋势图（需持久化历史，本期不做）；测试结果摘要进 job summary（独立关注点，留作下一个 change）；CI 缓存/并行提速、外部 SaaS（Codecov/Coveralls）；以及上一笔 WireMock 读超时修复的文档沉淀（独立处理，不混入本 change）。

## Capabilities

### New Capabilities
<!-- 无新增 capability -->

### Modified Capabilities
- `automated-testing`: 在既有「覆盖率门禁」之外，新增「PR 增量覆盖率可视化」需求——CI MUST 在 PR 上以 best-effort 评论展示改动文件的覆盖率，且该展示 MUST NOT 成为新的构建硬门禁。

## Impact

- **`.github/workflows/ci.yml`**：为 `verify` job 加 `permissions` 块 + 一个 coverage-report step。**这是唯一的代码改动面**。
- 不碰 `pom.xml`、不碰任何测试/生产代码、不引入运行时依赖。`Madrapps/jacoco-report` 是 marketplace action，跑在 runner 内、仅用 `GITHUB_TOKEN` 贴评论，数据不出 GitHub，与已在用的 `checkout`/`setup-java`/`upload-artifact` 同类。
- 外部依赖：固定 tag `@v1.7.2`（与项目其它 action 用 major tag 的约定一致）；失效时回退方案是自写脚本解析 XML（路线 B）。
