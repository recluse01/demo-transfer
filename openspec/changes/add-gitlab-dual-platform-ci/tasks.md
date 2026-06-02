# Tasks

## 1. 同步链路（已完成）
- [x] 1.1 删除误配的 Push 镜像（GitLab→GitHub）
- [x] 1.2 编写 `.gitlab-ci.yml`（`mirror-from-github` + `verify`，rules 防环）
- [x] 1.3 提交 `.gitlab-ci.yml` 到 GitHub 主仓
- [x] 1.4 本地 `git push gitlab --all --tags` 引导 GitLab 初始代码
- [x] 1.5 配置 `GITLAB_PUSH_TOKEN`（Project Access Token，write_repository，masked）
- [x] 1.6 创建 Pipeline Schedule（`*/5 * * * *`，目标分支含 `.gitlab-ci.yml`）
- [x] 1.7 验证定时 mirror 流水线跑通（#1472 Passed）

## 2. 端到端镜像验证
- [x] 2.1 在 GitHub 推一个测试提交（已推 `1bb8f97`，并已确认 GitLab `claude/v1-test` 分支包含该提交；当前观察到 GitLab 分支头为 `2190f58`）
- [x] 2.2 检查 mirror 作业日志，确认 `git push --mirror` 无 ref 被拒 warning（schedule pipeline `1521` / mirror job `5883` 成功，trace 无 `rejected|remote rejected|non-fast-forward|deny updating a hidden ref`）

## 3. runner 与 verify（方案 A）
- [x] 3.1 准备一台内网 Linux 机器，注册 shell executor runner（GitLab push pipeline `1525` / verify job `5887` 日志显示 `Using Shell (bash) executor`，runner 在线执行）
- [x] 3.2 安装 Docker，`usermod -aG docker gitlab-runner` 并重启 runner（由 job `5887` 中 Testcontainers 成功拉起 MySQL / Ryuk、`mvn -B verify` 全绿间接验证 runner 用户具备 Docker 可用性）
- [x] 3.3 安装 Temurin 8 与 Maven（job `5887` 日志已确认 `openjdk version "1.8.0_492"` 与 `Apache Maven 3.6.3`）
- [x] 3.4 自检：已在 runner 主机构建目录 `/home/gitlab-runner/builds/E-2rrftSv/0/neil-demo/demo/demo-transfer` 以 `gitlab-runner` 用户执行 `openspec/changes/add-gitlab-dual-platform-ci/runner-selfcheck.sh`，`docker pull mysql:8.0.36`、`docker ps`、`java -version`、`mvn -v` 全过
- [x] 3.5 `verify` 作业内显式设 `JAVA_HOME`（已提交于 `1bb8f97`），并在 `0ff6484` 中补齐 docker-java API 版本对齐；push pipeline `1525` / verify job `5887` 已确认 `mvn -B verify` 全绿并上传 JaCoCo artifacts
- [ ] 3.6 长期清理：加 `docker system prune` 定时任务

## 4. 后续（本 change 非目标，单独提）
- [ ] 4.1 GitLab 侧覆盖率可视化（日志正则 / cobertura 转换，替代 GitHub 的 Madrapps PR 评论）
