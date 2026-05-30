# 按行业最佳实践完善项目文档 — 设计

- 日期：2026-05-30
- 定位：教学/参考示例工程
- 风格约束：简要、尽量减少重复描述（DRY，单一来源 + 链接为主）。注意是"尽量减少"而非"完全禁止"：当一份内容曝光位置的价值高于同步成本时（如落地页的架构图），允许保留差异化的精简副本。

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

**demo §12「关键讲解点」的处理**：不直接删除。ADR 与 §12 受众不同——ADR 是给"改系统的人"读的决策档案（重背景/权衡/后果），§12 是给"讲系统的人"用的现场演示话术（重一句话点透）。§12 保留为**指向 ADR 的一句话摘要清单**（如"为什么不用分布式事务 → 见 ADR-0001"），既去重又不破坏演示连贯性。

## 二、Mermaid 可视化图

每张图只在一处作为**单一来源**，其他文档链接过去。

| 图 | 类型 | 落在哪 | 替换掉 |
| --- | --- | --- | --- |
| 组件架构图（完整版） | `graph` | docs/README | demo / overview 的 ASCII 拓扑 |
| 组件架构图（精简版） | `graph` | 根 README | 根 README 现有 ASCII 拓扑 |
| 转账主流程时序图 | `sequenceDiagram` | overview §5 | §5 的 ASCII 流程 |
| 状态机图 | `stateDiagram-v2` | overview §6 | 补充现有状态表（表保留做说明） |

根 `README.md` 作为仓库落地页，保留一张**精简架构图**（3 服务 + 3 库，一句话），帮助第一眼建立心智模型；完整带说明的架构图放在 docs/README，两者定位不同而非重复。demo / overview 的 ASCII 拓扑删除，改为"架构图见文档中心"。

## 三、术语表 `concepts/glossary.md`

锚点式条目，每条 2–3 句 + 指向项目中的体现。涵盖：Saga、编排式 vs 协同式、幂等 / 幂等键、悲观锁、乐观锁（version）、冻结 / 确认扣减 / 解冻 / 入账、最终一致 vs 强一致、补偿事务、财务流水、Feign。

**链接范围**：只在 overview 和 docs/README 这两个"主干"文档里链接术语；demo / api 这类操作文档不强求逐处插链接，避免分散编辑带来的维护负担与漏改。

## 四、文档导航重构

`docs/README.md` 重建为文档中心：

- 一句话项目简介 + 组件架构图（Mermaid 单一来源）。
- 导航分区：概念（glossary）/ 决策（ADR）/ 设计（overview）/ 接口调试 / 演示 / SQL。
- 新人阅读路径：入门 → 架构 → 深入（线性顺序）。

`docs/design/README.md` 精简为指向 overview 的薄索引。

## 五、DRY 去重清单

| 内容 | 处理 |
| --- | --- |
| 拓扑图（三处） | 完整版仅 docs/README；根 README 保留差异化精简版；demo / overview 删除改链接 |
| 排查 SQL | 仅留 debug-api；demo §2.7 改为链接 |
| 启动 / 端口 / 环境变量 | README 为单一来源；其他文档链接 |
| "为什么"段落 | 收敛到 ADR，原处精简为结论 + 链接 |

## 验收标准

- 新增 `docs/concepts/glossary.md`、`docs/decisions/`（README + 5 篇 ADR）。
- overview 的主流程与状态机改为 Mermaid；完整架构图在 docs/README，根 README 保留精简 Mermaid 架构图。
- 根 README、demo 中重复的拓扑 / SQL 删除并改为链接，无内容回退。
- 全文交叉链接可达，无死链；无代码与 SQL 脚本改动。
