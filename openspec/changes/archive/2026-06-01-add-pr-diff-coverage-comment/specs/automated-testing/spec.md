## ADDED Requirements

### Requirement: PR 增量覆盖率可视化

CI SHALL 在每个 pull request 上以 best-effort 方式展示本次改动代码的覆盖率：复用 `verify` 阶段已产出的双轨合并 `jacoco.xml`，在 PR 上贴一条评论，呈现整体覆盖率与改动文件覆盖率。该展示 MUST NOT 构成新的构建硬门禁——现有的全量 `check-merged`（整体行覆盖 ≥70%）仍是唯一会使构建失败的覆盖率门禁。

#### Scenario: PR 上出现可更新的覆盖率评论

- **WHEN** 一个 pull request 触发 CI 且 `verify` 成功产出各模块 `jacoco.xml`
- **THEN** PR 上出现一条「📊 覆盖率报告」评论，含整体与改动文件覆盖率数字；同一 PR 后续推送 commit 时该评论被更新而非新增

#### Scenario: 改动文件覆盖率低于展示标准不阻断构建

- **WHEN** 改动文件覆盖率低于展示标准（80%）
- **THEN** 评论以标记（如 ❌）提示，但 coverage-report 步骤与整个 job 不因此失败，PR 不被阻断

#### Scenario: 非 PR 构建跳过展示

- **WHEN** CI 由 `push`（非 pull_request）触发
- **THEN** coverage-report 步骤被跳过、不报错，构建结论不受影响

#### Scenario: 覆盖数据缺失时优雅跳过

- **WHEN** 测试在 `verify` 之前失败导致 `jacoco.xml` 未生成
- **THEN** coverage-report 步骤优雅跳过（best-effort），不把已失败的 job 二次染红、也不掩盖真实的测试失败结论
