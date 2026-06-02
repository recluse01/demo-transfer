# PR 增量覆盖率可视化 · 设计

- 日期：2026-06-01
- 状态：已通过设计评审，待实现计划
- 范围：仅 CI 与测试文档，不触及生产代码、不改 Maven 构建

## 背景

测试体系已落地双轨测试（surefire `*Test` / failsafe `*IT`）+ JaCoCo 合并覆盖率门禁，
并新增了 GitHub Actions CI（`mvn -B verify`）。但覆盖率结果只以两种形态存在：

- 每模块的 `target/site/jacoco/jacoco.xml` / HTML，需下载 CI 的 `jacoco-report` artifact、本地解压打开才能看；
- 全量 JaCoCo `check-merged` 门禁（整体 LINE ≥ 70%）只在构建失败时以非零退出体现。

缺口：**PR 上看不到本次改动代码的覆盖情况**。评审者无法一眼判断新增/改动的代码有没有被测到，
覆盖率可能在「整体仍达标」的掩护下，于新代码处悄悄下滑。

## 目标

在每个 PR 上自动展示**增量（diff）覆盖率**——本次改动文件/行的覆盖率，连同整体覆盖率，
贴成一条 PR 评论。约束：GitHub 原生、数据不出库、无状态、低维护。

## 非目标（YAGNI）

- 覆盖率历史**趋势**图表（需持久化历史数据，维护成本高，本期不做）。
- 测试结果摘要进 job summary（解决「翻日志找失败用例」的痛，价值高，但属独立关注点，留作**下一个 change**）。
- CI 缓存/并行提速、外部 SaaS（Codecov/Coveralls）。

## 设计

### 数据流

不改 Maven，复用现有产物：

1. CI 的 `mvn -B verify` 已为每个模块产出 `target/site/jacoco/jacoco.xml`（单元+IT 合并后的覆盖数据）。
2. 在 `ci.yml` 的 `verify` job 末尾新增一步 `Madrapps/jacoco-report@v1.7.2`：
   - `paths: ${{ github.workspace }}/**/target/site/jacoco/jacoco.xml` —— glob 自动收齐全部模块；
   - `token: ${{ secrets.GITHUB_TOKEN }}`；
   - `min-coverage-overall: 70`（与现有 `check-merged` 门禁 70% 对齐）；
   - `min-coverage-changed-files: 80`（改动代码的展示标准，更严，但本期**只展示不卡构建**）；
   - `title: 📊 覆盖率报告`、`update-comment: true`（同一 PR 内更新同一条评论，不刷屏）。
3. 该 action 无状态：用 PR 的 diff 识别改动文件，结合当前 `jacoco.xml` 算出改动文件/行的覆盖率，
   无需任何历史基线。

### 关键约束与边界

- **仅 PR 触发**：该步加 `if: always() && github.event_name == 'pull_request'`。
  增量本质上需要 PR diff，`push` 到分支无可对比对象，跳过即可；`always()` 保证测试失败时也尽力贴评论。
  若 verify 早退导致 XML 缺失，该步静默跳过（best-effort），不影响构建结论。
- **权限**：job 显式声明 `permissions: { contents: read, pull-requests: write }`，
  否则 `GITHUB_TOKEN` 无权在 PR 上贴评论。
- **先报告、不新增硬门禁**：增量覆盖率只展示。现有的全量 JaCoCo `check-merged`（70%）
  仍是唯一的构建硬门禁。增量门禁（用 `github-script` 读 action 的 `coverage-changed-files`
  输出来 fail）作为以后想收紧时的一行开关，本期不开——避免一上来就堵 PR。

### 改动面

- **`.github/workflows/ci.yml`**：为 `verify` job 加 `permissions` + 一个 coverage-report step。
- **`docs/design/testing-strategy.md`**：CI 小节补一行约定——「保真轨用例不得依赖 wall-clock 紧阈值
  来通过；需要超时语义时，让注入延迟远超超时阈值，且超时配置不得把时序脆弱性摊给同类即时返回的用例」
  （本次 WireMock 读超时修复的自然沉淀）。
- 不碰 pom、不碰任何测试/生产代码、不引入运行时依赖。`Madrapps/jacoco-report` 是 marketplace action，
  跑在 runner 内、仅用 `GITHUB_TOKEN` 贴评论，数据不出 GitHub，与已在用的
  `checkout`/`setup-java`/`upload-artifact` 同类。

## 实现计划

1. 更新 `.github/workflows/ci.yml` 的 `verify` job：
   - 在 job 级别声明 `permissions: { contents: read, pull-requests: write }`；
   - 在 `mvn -B verify` 和 artifact 上传之后新增 `Madrapps/jacoco-report@v1.7.2` 步骤；
   - 步骤条件使用 `if: always() && github.event_name == 'pull_request'`，仅在 PR 上尝试生成评论；
   - 步骤级别加 `continue-on-error: true`，`with` 中也显式配置 `continue-on-error: true`，确保第三方 action 异常、缺失 XML、权限受限时不影响主 CI 结论；
   - 配置 `skip-if-no-changes: true`，文档/配置类 PR 没有可计算覆盖率变更时不刷无意义评论；
   - 保持 `min-coverage-overall: 70`、`min-coverage-changed-files: 80` 仅用于展示，不新增增量覆盖率硬门禁。
2. 更新 `docs/design/testing-strategy.md` 的 CI 小节：
   - 记录 PR 覆盖率评论的来源、触发条件和 best-effort 语义；
   - 说明外部 fork、Dependabot 或组织策略限制 `GITHUB_TOKEN` 写权限时，覆盖率评论可能跳过，但 `mvn verify` 和 JaCoCo 硬门禁仍是 CI 的判定依据；
   - 补充保真轨用例不得依赖 wall-clock 紧阈值的约定，避免慢 CI 环境误伤。
3. 提交前检查：
   - 本地执行 YAML 结构检查或至少人工核对缩进；
   - 确认 workflow 未改变 `push` 事件的构建结论；
   - 测试 PR 上验证评论会创建、重复推送会更新、无覆盖率改动时会跳过。

## 验证

开一个测试 PR（故意改动一两个有覆盖的文件），确认：

1. PR 上出现「📊 覆盖率报告」评论；
2. 评论里整体 + 改动文件覆盖率数字正确（与本地 `jacoco.xml` 核对）；
3. 再推一次 commit，评论被**更新**而非新增；
4. `push`（非 PR）构建不受影响、coverage-report 步骤被跳过、无报错。

## 风险

- 风险面低：纯增量 CI 配置 + 一行文档。无生产代码、无 Maven 改动。
- 唯一外部依赖是固定版本的 marketplace action（`@v1.7.2`），失效时回退方案是自写脚本解析 XML（路线 B）。
- 若仓库默认 `GITHUB_TOKEN` 权限被组织策略限制为只读，需在 workflow/job 显式授予 `pull-requests: write`
  （本设计已包含）。
