## Working Agreements

### Stage Completion Commits

After each completed work stage, automatically commit the changes from that
stage only.

Git commit messages must follow the AngularJS style:

```text
<type>(<scope>): <subject>
```

Allowed `type` values include:

- `feat`
- `fix`
- `refactor`
- `test`
- `docs`
- `chore`

Use the following extended format when a stage includes enough detail to
justify a multi-line commit body:

```text
<type>(<scope>): <subject>

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
