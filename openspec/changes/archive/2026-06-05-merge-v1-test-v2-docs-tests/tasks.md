# Tasks

## 1. 分支准备
- [x] 1.1 确认当前工作区未跟踪的 agent/skill 文件不纳入本 change 暂存范围
- [x] 1.2 从 `claude/v1-test` 创建新工作分支 `claude/merge-v1-test-v2-docs-tests`
- [x] 1.3 合入 `claude/v2`，记录并分类冲突路径

## 2. 测试基线对齐
- [x] 2.1 恢复 v1 中被 v2 删除的测试支撑类、测试资源和保真轨配置
- [x] 2.2 恢复或改写账户服务金额精度、幂等、Web 层测试
- [x] 2.3 恢复或改写转账服务 Controller、跨服务调用、状态流转测试
- [x] 2.4 将旧 Saga/Retry/Scheduler 专属断言迁移为 Temporal Workflow/Activity 等价断言
- [x] 2.5 保留 v2 已新增的初始化失败、冻结失败和 Temporal 工作流相关测试覆盖

## 3. 文档与 OpenSpec 基线对齐
- [x] 3.1 恢复 v1 的 README、文档中心、术语表、ADR 和测试策略文档
- [x] 3.2 将 v2 的 Temporal 实现事实补入服务实现总览和演示文档，不删除 v1 的设计解释
- [x] 3.3 恢复 v1 的 `automated-testing`、`continuous-integration` 主 spec 与相关归档资料
- [x] 3.4 检查 `AGENTS.md`、提交规范和 docs/superpowers 资料，保留 v1 协作约定

## 4. CI 与构建配置
- [x] 4.1 保留 v1 的 GitHub Actions 与 GitLab CI 双平台配置
- [x] 4.2 合并 v2 的 Temporal 依赖与配置，确保仍满足 JDK 8 和 JaCoCo 门禁
- [x] 4.3 检查 Maven 多模块依赖关系，避免测试依赖泄漏到运行时

## 5. 验证与收尾
- [x] 5.1 运行 `mvn -q test -DfailIfNoTests=false`
- [x] 5.2 在 Docker 可用时运行 `mvn -q verify -DfailIfNoTests=false`（已尝试；当前环境缺少 `/var/run/docker.sock`，保真轨无法完成）
- [x] 5.3 检查 `git status --short --branch` 与相关 diff，确认只包含本 change 范围
- [x] 5.4 更新 tasks 勾选状态并记录无法运行验证的原因
