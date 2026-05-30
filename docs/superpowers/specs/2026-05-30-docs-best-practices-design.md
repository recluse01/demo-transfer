# 按行业最佳实践完善项目文档 — 设计

- 日期：2026-05-30
- 定位：教学/参考示例工程
- 风格约束：简要、避免重复描述（DRY，单一来源 + 链接）

## 目标

现有文档在"是什么 / 怎么做"上已较完整（README、实现总览、演示、调试 API、SQL、提交规范）。在教学定位下，补强四块并同步去重：

1. ADR 架构决策记录——沉淀分散的"为什么"。
2. Mermaid 可视化图——替换 ASCII，GitHub 可直接渲染。
3. 术语表/概念索引——降低阅读门槛。
4. 文档导航重构——线性阅读路径，并消除跨文档重复。

非目标（YAGNI）：不加 LICENSE / CHANGELOG / CONTRIBUTING / CODE_OF_CONDUCT；不改代码；不动 SQL 脚本；不引入文档构建工具。

## 最终 docs/ 结构

```
docs/
├── README.md                      # 重建为「文档中心」：导航 + 阅读路径（唯一入口）
├── concepts/
│   └── glossary.md                # 新增 术语表/概念索引
├── decisions/
│   ├── README.md                  # 新增 ADR 索引
│   ├── 0001-orchestrated-saga.md
│   ├── 0002-credit-failed-retry-no-compensation.md
│   ├── 0003-shared-account-service.md
│   ├── 0004-idempotency-by-transferid-operationtype.md
│   └── 0005-eventual-consistency-no-xa.md
├── design/
│   ├── README.md                  # 精简为指向 overview 的薄索引
│   └── service-implementation-overview.md   # 图换 Mermaid，删重复
├── api/transfer-debug-api.md      # 保留；排查 SQL 作为单一来源留这里
├── demo/cross-account-transfer-demo.md      # 删本地重复的 SQL/拓扑，改链接
└── sql/                           # 不动
```

## 一、ADR 架构决策记录

新增 `docs/decisions/`，每篇采用标准 ADR 格式：背景 / 决策 / 理由 / 权衡 / 后果，篇幅约 1 屏。ADR 是这些"为什么"的**单一来源**；overview / demo 中相应段落精简为一句话结论 + 链接到对应 ADR。

| 编号 | 主题 | 素材来源 |
| --- | --- | --- |
| 0001 | 用编排式 Saga，不用 XA/Seata/TCC | overview §1/§9、demo §12 |
| 0002 | CREDIT_FAILED 只重试目标入账、不做反向补偿 | overview §6/§8、demo §12 |
| 0003 | A/B 服务共用一份 account-service | overview §2 |
| 0004 | 以 transferId+operationType 做幂等键 | overview §7、demo §12 |
| 0005 | 只保证最终一致，不引入强一致 | overview §9/§11 |

`docs/decisions/README.md` 提供 ADR 列表索引与一句话摘要。

## 二、Mermaid 可视化图

每张图只在一处作为**单一来源**，其他文档链接过去。

| 图 | 类型 | 落在哪 | 替换掉 |
| --- | --- | --- | --- |
| 组件架构图 | `graph` | docs/README | README / overview / demo 三处 ASCII 拓扑 |
| 转账主流程时序图 | `sequenceDiagram` | overview §5 | §5 的 ASCII 流程 |
| 状态机图 | `stateDiagram-v2` | overview §6 | 补充现有状态表（表保留做说明） |

根 `README.md` 与 demo 的拓扑 ASCII 删除，改为"架构图见文档中心"。

## 三、术语表 `concepts/glossary.md`

锚点式条目，每条 2–3 句 + 指向项目中的体现。涵盖：Saga、编排式 vs 协同式、幂等 / 幂等键、悲观锁、乐观锁（version）、冻结 / 确认扣减 / 解冻 / 入账、最终一致 vs 强一致、补偿事务、财务流水、Feign。其他文档首次出现术语时链接到此处。

## 四、文档导航重构

`docs/README.md` 重建为文档中心：

- 一句话项目简介 + 组件架构图（Mermaid 单一来源）。
- 导航分区：概念（glossary）/ 决策（ADR）/ 设计（overview）/ 接口调试 / 演示 / SQL。
- 新人阅读路径：入门 → 架构 → 深入（线性顺序）。

`docs/design/README.md` 精简为指向 overview 的薄索引。

## 五、DRY 去重清单

| 内容 | 处理 |
| --- | --- |
| 拓扑图（三处） | 合一 → 仅 docs/README |
| 排查 SQL | 仅留 debug-api；demo §2.7 改为链接 |
| 启动 / 端口 / 环境变量 | README 为单一来源；其他文档链接 |
| "为什么"段落 | 收敛到 ADR，原处精简为结论 + 链接 |

## 验收标准

- 新增 `docs/concepts/glossary.md`、`docs/decisions/`（README + 5 篇 ADR）。
- overview 的主流程与状态机改为 Mermaid；架构图统一到 docs/README。
- 根 README、demo 中重复的拓扑 / SQL 删除并改为链接，无内容回退。
- 全文交叉链接可达，无死链；无代码与 SQL 脚本改动。
