# Conventional Commits 规范

本仓库提交信息遵循 Angular / Conventional Commits 规范，并使用中文描述变更。

## 提交信息格式

```text
<type>(<scope>): <subject>

<body>

<footer>
Made-with: <tool>
```

## Header

Header 是必填项，格式固定为：

```text
<type>(<scope>): <subject>
```

### type

只允许使用以下类型：

- `feat`：新增功能
- `fix`：修复缺陷
- `docs`：文档变更
- `style`：代码格式、空白、缩进等不影响逻辑的变更
- `refactor`：重构，既不新增功能也不修复缺陷
- `perf`：性能优化
- `test`：新增或调整测试
- `chore`：构建流程、依赖、脚手架、辅助工具等维护变更
- `build`：构建系统或外部依赖变更
- `ci`：CI 配置或脚本变更
- `revert`：回滚提交

### scope

`scope` 使用本次变更影响的模块、目录或能力名称，保持简短、稳定、可检索。

示例：

- `transfer`
- `workflow`
- `openspec`
- `docs`
- `git`

### subject

`subject` 使用中文，简洁说明本次变更，建议不超过 50 个字。

要求：

- 使用祈使或陈述语气，不以句号结尾
- 说明“做了什么”，避免只写“更新代码”“修改问题”
- 与 `type` 保持一致，例如文档变更使用 `docs`

## Body

`body` 是可选项，用于说明修改原因、影响范围、关键实现或迁移注意事项。

要求：

- 与 Header 之间保留一个空行
- 使用条目化描述，优先说明对使用者、接口、数据或流程的影响
- 不重复 Header 已经表达清楚的信息

文档类变更可使用以下结构：

```text
新增内容：
- <新增文档、章节或说明>

内容更新：
- <文件或模块>：<更新内容>

结构调整：
- <目录、导航、分类或引用关系调整>

其他：
- <格式、链接、术语等维护项>
```

不涉及的分组应删除，避免生成空段落。

## Footer

`footer` 用于记录关联事项、破坏性变更、关闭的问题单，以及本仓库要求的工具标记。

常见格式：

```text
Refs: #123
Closes: #456
BREAKING CHANGE: <破坏性变更说明>
Made-with: Codex
```

要求：

- `Made-with` 必填，值填写实际使用的 AI 工具名
- 存在破坏性变更时，必须使用 `BREAKING CHANGE:` 说明影响和迁移方式
- 多个 footer 每行一个

## 示例

### 功能变更

```text
feat(transfer): 支持跨账户转账冻结补偿

新增内容：
- 新增冻结失败后的补偿流程
- 新增跨账户转账状态推进逻辑

内容更新：
- transfer：补充冻结、解冻和确认的幂等处理

Made-with: Codex
```

### 缺陷修复

```text
fix(workflow): 修复冻结失败后状态未终止的问题

内容更新：
- workflow：冻结失败时将业务状态推进为终态
- transfer：避免后续步骤继续处理失败订单

Made-with: Codex
```

### 文档变更

```text
docs(git): 规范提交信息模板

内容更新：
- docs/conventional-commits.md：补充 Header、Body、Footer 编写规则
- AGENTS.md：更新提交规范文档引用

Made-with: Codex
```

## AI 生成提交信息提示词

```text
请根据当前 Git 变更生成中文 commit message。

要求：
1. 遵循 Angular / Conventional Commits 规范
2. Header 格式为：<type>(<scope>): <subject>
3. type 只能使用：feat、fix、docs、style、refactor、perf、test、chore、build、ci、revert
4. scope 使用本次变更影响的模块、目录或能力名称，保持简短
5. subject 使用中文，简洁明确，不超过 50 个字，不以句号结尾
6. 如有必要，使用 body 说明修改原因、影响范围或关键实现
7. 删除不涉及的 body 分组，避免空段落
8. footer 必须包含：Made-with: <实际使用的 AI 工具名>
9. 不要输出解释，只输出最终 commit message
```
