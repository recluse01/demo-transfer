## Context

- 主仓：`github.com/recluse01/demo-transfer`（public），已有 GitHub Actions（`.github/workflows/ci.yml`）跑 `mvn -B verify`。
- 目标平台：公司内网自建 GitLab，**Free 版**，当前用户有项目 Maintainer 级控制权但不确定能拿到专用机器。
- 网络方向：内网可访问公网 GitHub（mirror 走 github.com）；Docker Hub 可达性需单独核验。
- 保真轨事实（`AbstractMySqlIntegrationTest`）：镜像 `mysql:8.0.36` + `testcontainers/ryuk`（TC 1.19.8），CI 不开 `reuse.enable` → 每次全新容器 + Ryuk 清理；DDL 用相对路径 `../docker/mysql/init/01-demo-transfer.sql` 挂载，工作目录为模块目录，`mvn verify` 下与本地一致，无需改动。

## Goals / Non-Goals

**Goals**
- GitHub 当主仓，GitLab 只读镜像并等价跑 `mvn -B verify`，两平台并存。
- 在 GitLab Free 版上自实现 GitHub→GitLab 拉取同步。

**Non-Goals**
- 迁移 / 废弃 GitHub Actions；GitLab 覆盖率可视化；docker/DinD/k8s executor；runner 机器的 IaC 供给。

## Decisions

### D1：用「定时 CI job + git clone/push --mirror」自实现 pull mirror
Free 版无原生 pull mirror。Push mirror（GitLab→GitHub，Free 可用）方向相反且会反向覆盖公共主仓，已删除。选定：GitLab 定时流水线触发 `mirror-from-github`，`git clone --mirror` GitHub 后 `git push --mirror` 回本仓库，用 `GITLAB_PUSH_TOKEN`（Project Access Token，`write_repository`）写回。

**防环**：mirror 的 push 是 push 事件 → 只触发 `verify`（`if push/MR`），`verify` 不会再触发 `mirror`（`if schedule`）。无新提交时 `push --mirror` 为空操作、不触发流水线。

**前提**：`.gitlab-ci.yml` 必须先存在于 GitLab 调度的目标分支上，调度才有配置可跑——首次靠本地 `git push gitlab --all` 引导（不删重建项目）。当前文件在 `claude/v1-test`，故调度目标分支选该分支，或先合并到 `main`。

### D2：runner 选 shell executor + 本机 Docker（方案 A）
shell executor 直连本机 `/var/run/docker.sock`，是 Testcontainers / Ryuk **坑最少**的方式（DinD 下 Ryuk、socket、host override 配置成本高）。代价是机器为「宠物」需维护；对单项目内网场景性价比最高。

附带收益：同机同 `gitlab-runner` 用户下，`~/.m2` 与已拉 Docker 镜像天然跨构建复用，比 CI cache 更省。

### D3：shell executor 的三个落地前提
1. **docker 组**：作业以 `gitlab-runner` 用户运行，须 `usermod -aG docker gitlab-runner` 否则访问 socket 被拒。
2. **Docker Hub 可达性**：与 github.com 是独立链路，须单独 `docker pull mysql:8.0.36` 核验；不通则配 `registry-mirrors` 或预拉私有 registry。
3. **PATH/JAVA_HOME**：shell executor 非登录 shell 可能不 source profile，`verify` 作业内显式设 `JAVA_HOME`，不依赖机器环境变量。

### D4：两平台互不成为硬门禁
GitHub Actions 与 GitLab CI 各自独立出结论。runner / Docker / Docker Hub 任一前提不满足时，`verify` 在 GitLab 不可用，降级为「GitLab 只跑快速轨或暂不跑、保真轨由 GitHub Actions 兜底」，不阻塞 GitHub 主流程。

## Risks / Trade-offs

- **Docker Hub 不可达**（最可能卡点）→ 降级或配镜像加速；已在 spec 场景中显式覆盖。
- **runner 机器可得性不确定** → 短期可先只跑 mirror（仅需 git，已验证通过），verify 待机器就绪。
- **shell executor 共享主机** → 单 runner 默认 concurrency=1，避免并发争用；长期加 `docker system prune` 定时清理。
- **镜像延迟**：调度 ~5 分钟轮询，非实时；可接受。

## Migration / Rollout

1. 删除误配的 Push 镜像（已完成）。
2. `.gitlab-ci.yml` 提交进 GitHub 主仓（已完成，commit d8b4727 于 `claude/v1-test`）。
3. 本地 `git push gitlab --all --tags` 引导 GitLab 初始代码。
4. 注册 shell executor runner（先 git → mirror 可跑；后补 Docker/JDK8/Maven → verify 可跑）。
5. 配 `GITLAB_PUSH_TOKEN` + Pipeline Schedule（`*/5 * * * *`，目标分支含 `.gitlab-ci.yml`）。
6. 自检 `docker pull mysql:8.0.36` / `docker ps` / `java -version` / `mvn -v` 三项全过后解锁 `verify`。

> 当前进度：第 1~3 步完成；定时 mirror 流水线已跑通（#1472 Passed）。
> `claude/v1-test` 已额外推送验收提交 `1bb8f97 fix(ci): 显式推导 GitLab verify 的 JAVA_HOME`，
> 用于触发 GitHub→GitLab mirror 与后续 `verify`。第 4~6 步仍需在 GitLab / runner 外部环境完成。
> 另：`runner-selfcheck.sh` 已在当前开发机以普通用户执行通过（`docker pull mysql:8.0.36`、`docker ps`、
> `java -version`、`mvn -v` 均成功），证明脚本本身可运行；但这**不构成** `gitlab-runner` 用户、
> Linux 主机上的任务 3.4 完成证据。

## Acceptance / Ops Runbook

### A1：GitHub→GitLab 镜像验收（对应任务 2.1 / 2.2）

1. **GitHub 侧基准提交**：`claude/v1-test` 分支历史中必须包含验收提交 `1bb8f97`。
2. **触发方式**：等待下一次 `Pipeline Schedule`，或在 GitLab UI 手动运行一次 `schedule` 流水线（目标分支仍为 `claude/v1-test`）。
3. **提交验收**：在 GitLab 仓库的 `claude/v1-test` 分支 `Commits` 中确认出现 `1bb8f97`。
4. **日志验收**：打开 `mirror-from-github` 作业日志，确认：
   - 出现 `git clone --mirror` 与 `git push --mirror`；
   - 作业最终成功；
   - 日志中**没有** `rejected`、`remote rejected`、`non-fast-forward`、`deny updating a hidden ref` 等 ref 被拒绝迹象。

> 若 GitLab 已出现该提交但未触发 `verify`，优先检查 `.gitlab-ci.yml` 是否已位于 GitLab 目标分支、以及 push 事件是否被项目级流水线规则拦截。

### A2：runner 机器落地与自检（对应任务 3.1 ~ 3.4）

1. **注册 runner（shell executor）**：确认项目级 runner 为 `shell` 执行器，并勾选 `Run untagged jobs`。
2. **Docker 权限**：执行 `sudo usermod -aG docker gitlab-runner` 后重启 runner 进程或整机，避免 `docker.sock` 权限被拒。
3. **JDK / Maven 安装**：安装 Temurin 8 与 Maven，并确保 `gitlab-runner` 用户可直接调用 `java`、`mvn`。
4. **以 `gitlab-runner` 用户自检**：

   ```bash
   sudo -u gitlab-runner -H bash -lc '
     cd /path/to/demo-transfer &&
     ./openspec/changes/add-gitlab-dual-platform-ci/runner-selfcheck.sh
   '
   ```

5. **通过标准**：
   - 脚本中的 `docker pull mysql:8.0.36` 成功，说明 Docker Hub 基本可达；
   - `docker ps` 无权限错误；
   - `java -version` 显示 JDK 8；
   - `mvn -v` 显示 Maven 可用，且 Java version 为 `1.8.x`。

### A3：`verify` 作业验收（对应任务 3.5 / 3.6）

当前 `.gitlab-ci.yml` 中，`verify` 作业已显式：

- 通过 `java -XshowSettings:properties -version` 提取 `java.home`；
- 如有尾部 `/jre` 则裁掉，导出为 `JAVA_HOME`；
- 打印 `java -version` 与 `mvn -v` 后再执行 `mvn -B verify`。

验收时应在 `verify` 日志中确认：

1. `java -version` 输出 JDK 8；
2. `mvn -v` 输出 `Java version: 1.8`；
3. `mvn -B verify` 全绿；
4. 产物中已上传 `**/target/site/jacoco/`。

长期清理（任务 3.6）可在 runner 主机上加定时任务，例如每天凌晨执行一次：

```bash
docker system prune -af --volumes
```

用于回收 Testcontainers 残留镜像、匿名卷与停止容器，避免单机 shell executor 长期膨胀。
