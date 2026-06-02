## ADDED Requirements

### Requirement: 持续集成自动执行双轨测试与门禁
项目 SHALL 在每次 push 到分支与每个 pull_request 上，通过 GitHub Actions 自动执行 `mvn -B verify`，一条命令覆盖快速轨（surefire `*Test`）、保真轨（failsafe `*IT` / Testcontainers）与 JaCoCo 合并覆盖率门禁；任一测试失败或门禁不达标 MUST 使 CI 构建失败。CI 运行环境 MUST 使用 JDK 8（Temurin 8），与项目编译目标 `1.8` 一致。

#### Scenario: push 与 PR 触发 CI
- **WHEN** 开发者向分支 push 提交，或开启/更新一个 pull_request
- **THEN** GitHub Actions 自动启动单个 job，在 `ubuntu-latest` 上检出代码、配置 Temurin 8 与 Maven 依赖缓存，并执行 `mvn -B verify`

#### Scenario: 保真轨在 CI 真实执行
- **WHEN** CI 执行 `mvn -B verify`
- **THEN** 借助 runner 自带的 Docker，Testcontainers 启动全新 MySQL 容器并由 Ryuk 自动清理，全部 `*IT` 与 `*Test` 互不重叠地运行，行为与本地一致

#### Scenario: 门禁不达标使 CI 失败
- **WHEN** 整体或核心业务包覆盖率低于阈值，或任一用例失败
- **THEN** JaCoCo `check` 或 failsafe `verify` 使 `mvn verify` 非零退出，CI 标记为失败

### Requirement: 覆盖率报告产出为可查看 artifact
CI SHALL 在 `mvn verify` 后将 JaCoCo 覆盖率报告（`**/target/site/jacoco/`）作为可下载 artifact 上传，便于查看覆盖率明细。

#### Scenario: 覆盖率报告上传
- **WHEN** CI 完成 `mvn -B verify`
- **THEN** 通过 `actions/upload-artifact` 上传各模块的 JaCoCo HTML 报告，可在 workflow 运行页下载查看
