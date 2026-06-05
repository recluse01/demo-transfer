## MODIFIED Requirements

### Requirement: Activity 执行与自动重试

Temporal Workflow 规格 SHALL 使用当前 `TransferStatus` 中存在的状态描述 Activity 结果，不引用不存在的 `FROZEN` 状态。人工审核模式下 freeze 成功后进入 `WAIT_REVIEW`；自动模式下 freeze 成功后 Workflow 继续执行 confirmDebit 和 credit，不暴露单独冻结成功状态。

#### Scenario: freeze Activity 成功状态描述有效

- **WHEN** `freeze` Activity 成功
- **THEN** 人工审核模式的 `transfer_order.status` 更新为 `WAIT_REVIEW`，自动模式保持当前状态并继续后续 Activity，不使用 `FROZEN`

### Requirement: 删除手动重试接口

接口文档 SHALL 与该要求一致，不再把 `POST /{transferId}/retry` 描述为当前可调试接口。

#### Scenario: API 文档不列出已删除重试接口

- **WHEN** 读者查看接口清单或排查说明
- **THEN** 文档不提示调用手动重试接口，而是引导查看 Temporal Workflow 历史和 Activity 重试

### Requirement: 删除旧兼容提币接口

接口文档和演示文档 SHALL 与该要求一致，不再把 `POST /{transferId}/withdraw-result` 描述为当前流程的一部分。

#### Scenario: API 文档不列出已删除提币回调接口

- **WHEN** 读者查看站内自动转账说明
- **THEN** 文档说明 Workflow 自动推进，不需要旧版 `/withdraw-result` 回调
