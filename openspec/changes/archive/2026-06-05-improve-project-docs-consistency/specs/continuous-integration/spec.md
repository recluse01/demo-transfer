## MODIFIED Requirements

### Requirement: 持续集成自动执行双轨测试与门禁

CI 规格 SHALL 有明确 Purpose，并且项目文档中提到的全量验证命令 SHALL 与 CI 的 `mvn -B verify` 保持一致。

#### Scenario: CI Purpose 不再占位

- **WHEN** 读者查看 `continuous-integration` 主规格
- **THEN** `## Purpose` 提供明确说明，不保留 `TBD`

#### Scenario: 文档全量验证命令与 CI 一致

- **WHEN** README 描述提交前或全量验证
- **THEN** 它使用 `mvn verify` 语义，与 CI 的快速轨、保真轨、JaCoCo 门禁一致
