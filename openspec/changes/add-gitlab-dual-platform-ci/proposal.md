## Why

现有 CI 只在 GitHub Actions 上兑现（`continuous-integration` 能力当前按 GitHub Actions 描述）。团队需要在**公司内网自建 GitLab** 上也跑同一套自动化测试与门禁，以便内网开发者无需访问公网即可看到流水线结果。约束有两条硬现实：

1. 该内网 GitLab 是 **Free 版**，没有原生 pull mirror（从外部仓库拉取是 Premium 功能），无法在 GitLab 侧直接「订阅」GitHub 的变更。
2. GitLab 侧没有现成 runner，且保真轨（Testcontainers）需要 Docker。

目标是在**不改生产代码、不改测试代码**的前提下（仅为适配 runner 的 Docker v28 覆盖一处 docker-java 依赖版本，见下），让 GitHub 继续当主仓，GitLab 作为只读镜像并等价执行 `mvn -B verify`，两个平台并存、互不成为对方的硬门禁。

## What Changes

- 新增 `.gitlab-ci.yml`：两个作业靠 `rules` 隔离——
  - `mirror-from-github`：仅 `schedule` 触发，`git clone --mirror` 公网 GitHub + `git push --mirror` 回本 GitLab 仓库，**Free 版自实现 pull mirror**。
  - `verify`：`push` / `merge_request_event` / `web` 触发，跑 `mvn -B verify`（快速轨 + 保真轨 + JaCoCo 合并门禁），上传 JaCoCo 报告 artifact；其中 `web` 允许在 GitLab UI 直接 `Run pipeline` 手动重跑。
  - 防环：mirror 的 push 产生 push 事件，只触发 verify、不会再触发 mirror。
- 修改 `pom.xml`：在 `testcontainers-bom` 之前导入 `docker-java-bom` 3.4.0，覆盖 TC 1.19.8 内置的 3.3.6。旧 docker-java 默认发 Docker API 1.32，被 runner 上 Docker v28（最低 1.44）拒绝导致保真轨容器起不来；3.4.0 修复 API 协商，且其编译目标仍为 1.8，不破坏 JDK 8。这是本 change **唯一**触及的非 CI 文件，属依赖管理变更、不动任何生产/测试源码。
- 运维落地（非代码产物，记录在 design.md）：在内网一台 Linux 机器上注册 **shell executor** runner（方案 A），装 Docker + Temurin 8 + Maven，并把 `gitlab-runner` 用户加入 docker 组；配置 `GITLAB_PUSH_TOKEN`（Project Access Token，write_repository）与每 5 分钟的 Pipeline Schedule。
- 删除此前误配的 **Push 镜像**（GitLab→GitHub 方向，会反向覆盖公共主仓）。

非目标（YAGNI）：迁移 / 废弃 GitHub Actions；GitLab 侧覆盖率可视化（日志正则或 cobertura 转换，留作 verify 跑通后的后续）；docker executor / DinD / Kubernetes executor；自动化 runner 机器供给（IaC）。

## Capabilities

### Modified Capabilities
- `continuous-integration`: 在原 GitHub Actions 单平台之外，新增「双平台并存」与「Free 版自实现 GitHub→GitLab 拉取镜像」两条要求——GitHub 为主仓，GitLab 作为只读镜像在 self-managed runner 上等价执行 `mvn -B verify`；两平台结果各自独立，互不构成硬门禁。

## Impact

- 新增：`.gitlab-ci.yml`（仓库根）。
- 修改：`pom.xml`（导入 `docker-java-bom` 3.4.0 覆盖 TC 内置版本，适配 runner Docker v28 的 API 协商）。
- 运维（仓库外）：一台 Linux runner 机器（shell executor + Docker + Temurin 8 + Maven）、`GITLAB_PUSH_TOKEN` CI/CD 变量、Pipeline Schedule。
- 不触及任何生产代码与测试代码；不新增运行时依赖；Maven 构建配置仅有上述 docker-java 版本覆盖一处变更（依赖管理层面，编译目标仍 `1.8`）。
- 前提风险：runner 机器需能访问 **Docker Hub**（拉 `mysql:8.0.36`、`testcontainers/ryuk`）——与 github.com 可达性是两条独立链路，需单独核验；不满足时 `verify` 不可用，可降级为 GitLab 侧只跑快速轨、保真轨由 GitHub Actions 兜底。
