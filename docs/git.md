请根据以下代码变更内容，按照 Angular / Conventional Commits 提交规范生成中文的 Git commit message。


格式：
```
<type>(<scope>): <本次文档更新摘要>

新增内容：
- <新增文章/页面/章节 1>
- <新增文章/页面/章节 2>
- <新增文章/页面/章节 3>

内容更新：
- <页面/模块 1>：<更新内容>
- <页面/模块 2>：<更新内容>
- <页面/模块 3>：<更新内容>

结构调整：
- <目录/导航/导读/分类/引用关系调整>

其他：
- <零散维护项，如链接、术语、作者页、格式等>

Made-with: <工具名>
```

要求：
1. 格式为：<type>(<scope>): <subject>
2. type 只能使用：feat、fix、docs、style、refactor、perf、test、chore、build、ci、revert
3. scope 使用本次变更影响的模块名称，尽量简短
4. subject 使用中文，简洁明确，不超过 50 个字
5. 如有必要，可补充 body，说明修改原因、影响范围或关键实现
6. 不要输出解释，只输出最终 commit message
7. Made-with 后面跟使用的ai工具
