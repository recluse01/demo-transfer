# continuous-integration Delta

## MODIFIED Requirements

### Requirement: 持续集成自动执行双轨测试与门禁
合并后的项目 SHALL 保留 `claude/v1-test` 的 GitHub Actions 与 GitLab CI 双平台配置，并继续以 `mvn -B verify` 执行快速轨、保真轨和 JaCoCo 合并覆盖率门禁。`claude/v2` 的合并不得删除 CI 配置或降低门禁。

#### Scenario: 合并后 CI 文件仍存在
- **WHEN** 合并 `claude/v2` 到新工作分支
- **THEN** `.github/workflows/ci.yml` 与 `.gitlab-ci.yml` 仍存在，并保持 v1 的双轨测试执行语义

#### Scenario: Temporal 依赖不破坏 JDK 8 CI
- **WHEN** CI 在 JDK 8 环境执行 `mvn -B verify`
- **THEN** v2 引入的 Temporal 依赖和测试依赖不破坏项目 `source/target 1.8` 编译与 JaCoCo 门禁
