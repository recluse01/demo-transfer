## 1. 修改 CI workflow

- [x] 1.1 为 `.github/workflows/ci.yml` 的 `verify` job 增加 `permissions: { contents: read, pull-requests: write }`
- [x] 1.2 在「上传 JaCoCo 覆盖率报告」步骤之后新增 coverage-report step：`Madrapps/jacoco-report@v1.7.2`，配置 `paths`（`${{ github.workspace }}/**/target/site/jacoco/jacoco.xml`）、`token`、`min-coverage-overall: 70`、`min-coverage-changed-files: 80`、`title: 📊 覆盖率报告`、`update-comment: true`、`if: always() && github.event_name == 'pull_request'`；并按拍板预置 step 级 `continue-on-error: true`
- [x] 1.3 本地用 `actionlint`/`yamllint`（或目视）确认 workflow 语法正确、缩进与现有步骤一致（actionlint 未装，已用 python yaml 校验 + 目视）

## 2. 在测试 PR 上验证行为

- [ ] 2.1 开一个测试 PR（故意改动一两个有覆盖的文件），确认 PR 出现「📊 覆盖率报告」评论，且整体 + 改动文件覆盖率数字与本地 `jacoco.xml` 核对一致
- [ ] 2.2 在同一 PR 再推一次 commit，确认评论被**更新**而非新增
- [ ] 2.3 确认 `push`（非 PR）构建不受影响、coverage-report 步骤被跳过、无报错
- [ ] 2.4 实测：故意让一个单测失败推到 PR，确认 coverage-report step 在 XML 缺失时**优雅跳过**、不把 job 二次染红（已预置 `continue-on-error: true`，本任务降级为「确认行为符合预期」）
- [ ] 2.5 实测：构造改动文件覆盖率 < 80% 的情形，确认 step 退出码不使 job 失败（验证「只展示不卡构建」成立；`continue-on-error: true` 已兜底）

## 3. 收尾

- [ ] 3.1 在 `docs/design/testing-strategy.md` 的 CI 小节补一句「PR 上有增量覆盖率评论（best-effort、不卡构建）」的指引（一行，限本 change 范围，不含 WireMock 文档沉淀）
- [ ] 3.2 按 Conventional Commits 提交本阶段改动（中文）
