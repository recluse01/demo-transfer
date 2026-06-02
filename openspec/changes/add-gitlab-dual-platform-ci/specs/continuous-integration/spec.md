## ADDED Requirements

### Requirement: 双平台持续集成并存（GitHub 主仓 + GitLab 镜像）
项目 SHALL 在保留 GitHub Actions 作为主仓 CI 的同时，在公司内网自建 GitLab 上运行一套**等价**的持续集成：GitHub 为唯一事实来源仓库，GitLab 作为只读镜像，在带 Docker 的 self-managed runner 上同样执行 `mvn -B verify`（快速轨 + 保真轨 + JaCoCo 合并门禁）。两个平台的结果 MUST 各自独立产出，任一平台 MUST NOT 成为另一平台的硬门禁。GitLab runner MUST 提供 Docker（供 Testcontainers）与 JDK 8（对齐编译目标 `1.8`）。

#### Scenario: GitLab 侧等价执行 verify
- **WHEN** 一个提交经镜像进入 GitLab 并触发 push 流水线，且 runner 已具备 Docker + JDK 8 + Maven
- **THEN** GitLab 执行 `mvn -B verify`，与 GitHub Actions 一致地跑快速轨、保真轨与 JaCoCo 门禁，任一失败使该 GitLab 流水线失败

#### Scenario: GitLab UI 手动触发 verify
- **WHEN** 维护者在 GitLab UI 对目标分支执行 `Run pipeline`（source=`web`），且 runner 已具备 Docker + JDK 8 + Maven
- **THEN** GitLab 同样执行 `mvn -B verify`，用于手工重跑或在无新提交时主动验证当前分支状态

#### Scenario: 两平台互不成为硬门禁
- **WHEN** 某一平台的流水线失败或暂不可用
- **THEN** 另一平台的 CI 结论不受其影响，GitHub 主仓流程不被 GitLab 侧阻塞

#### Scenario: runner 前提不满足时降级
- **WHEN** runner 缺少 Docker，或机器无法访问 Docker Hub 拉取 `mysql:8.0.36` / `testcontainers/ryuk`
- **THEN** `verify` 的保真轨在 GitLab 不可用，降级为 GitLab 侧只跑快速轨或暂不执行，保真轨由 GitHub Actions 兜底，不阻塞主仓

### Requirement: GitLab Free 版自实现 GitHub→GitLab 拉取镜像
由于内网 GitLab 为 Free 版、无原生 pull mirror，项目 SHALL 通过一个**仅定时流水线触发**的 CI 作业自实现 GitHub→GitLab 同步：作业 `git clone --mirror` 公网 GitHub 仓库后 `git push --mirror` 回本 GitLab 仓库，使用具 `write_repository` 权限的 Project Access Token 写回。该镜像 MUST 为单向只读（GitHub→GitLab），GitLab 端 MUST NOT 作为修改源。MUST NOT 使用 Push 镜像（GitLab→GitHub）方向，以免反向覆盖公共主仓。

#### Scenario: 定时触发同步
- **WHEN** Pipeline Schedule 按设定间隔（如每 5 分钟）触发流水线
- **THEN** 镜像作业从 GitHub 全量拉取并推送回 GitLab，使 GitHub 上的新提交进入 GitLab；无新提交时 `push --mirror` 为空操作、不产生额外流水线

#### Scenario: 镜像与测试互不成环
- **WHEN** 镜像作业 `push --mirror` 向 GitLab 推入新提交
- **THEN** 该 push 事件仅触发 `verify`（push/MR/web 触发中的 `push` 路径），而 `verify` 不会再触发镜像作业（仅 schedule 触发），不形成循环

#### Scenario: GitLab 端只读
- **WHEN** 有人直接在 GitLab 端修改被镜像的分支
- **THEN** 下一次镜像同步将其覆盖为 GitHub 的内容；GitLab 不作为事实来源
