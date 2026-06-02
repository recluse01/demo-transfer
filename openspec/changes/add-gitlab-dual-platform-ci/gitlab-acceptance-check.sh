#!/usr/bin/env bash
set -euo pipefail

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "[error] 缺少命令: $1" >&2
    exit 1
  fi
}

for cmd in curl jq grep; do
  require_cmd "$cmd"
done

usage() {
  cat <<'EOF'
用法：
  GITLAB_BASE_URL=https://gitlab.example.com \
  GITLAB_PROJECT_ID=group%2Fproject 或 123 \
  GITLAB_TOKEN=xxxx \
  ./openspec/changes/add-gitlab-dual-platform-ci/gitlab-acceptance-check.sh

可选环境变量：
  GITLAB_REF=claude/v1-test
  EXPECTED_COMMIT=1bb8f97
EOF
}

if [ -z "${GITLAB_BASE_URL:-}" ] || [ -z "${GITLAB_PROJECT_ID:-}" ] || [ -z "${GITLAB_TOKEN:-}" ]; then
  usage >&2
  exit 1
fi

GITLAB_REF="${GITLAB_REF:-claude/v1-test}"
EXPECTED_COMMIT="${EXPECTED_COMMIT:-1bb8f97}"
GITLAB_BASE_URL="${GITLAB_BASE_URL%/}"

urlencode() {
  jq -rn --arg v "$1" '$v|@uri'
}

if [[ "$GITLAB_PROJECT_ID" =~ ^[0-9]+$ ]]; then
  PROJECT_ID_ENCODED="$GITLAB_PROJECT_ID"
else
  PROJECT_ID_ENCODED="$(urlencode "$GITLAB_PROJECT_ID")"
fi

api_get() {
  local path="$1"
  curl --silent --show-error --fail --location \
    --header "PRIVATE-TOKEN: $GITLAB_TOKEN" \
    "$GITLAB_BASE_URL/api/v4/projects/$PROJECT_ID_ENCODED$path"
}

find_job_id() {
  local jobs_json="$1"
  local job_name="$2"
  printf '%s' "$jobs_json" | jq -r --arg name "$job_name" '
    map(select(.name == $name)) | sort_by(.id) | reverse | .[0].id // empty
  '
}

assert_trace_not_contains() {
  local trace="$1"
  local pattern="$2"
  local message="$3"
  if printf '%s' "$trace" | grep -Eiq "$pattern"; then
    echo "[error] $message" >&2
    exit 1
  fi
}

assert_trace_contains() {
  local trace="$1"
  local pattern="$2"
  local message="$3"
  if ! printf '%s' "$trace" | grep -Eiq "$pattern"; then
    echo "[error] $message" >&2
    exit 1
  fi
}

echo "[step] 校验 GitLab 分支是否已包含提交 $EXPECTED_COMMIT"
commits_json="$(api_get "/repository/commits?ref_name=$(urlencode "$GITLAB_REF")&per_page=100")"
commit_line="$(printf '%s' "$commits_json" | jq -r --arg sha "$EXPECTED_COMMIT" '
  map(select((.id | startswith($sha)) or (.id[0:8] == $sha))) | .[0] // empty |
  [.id, .title] | @tsv
')"
if [ -z "$commit_line" ]; then
  echo "[error] GitLab 分支 $GITLAB_REF 中未找到提交 $EXPECTED_COMMIT" >&2
  exit 1
fi
COMMIT_SHA="$(printf '%s' "$commit_line" | cut -f1)"
COMMIT_TITLE="$(printf '%s' "$commit_line" | cut -f2-)"
echo "[info] 已找到提交: $COMMIT_SHA  $COMMIT_TITLE"

echo "[step] 检查最新 schedule pipeline 与 mirror-from-github 作业"
schedule_pipelines_json="$(api_get "/pipelines?ref=$(urlencode "$GITLAB_REF")&source=schedule&per_page=20")"
schedule_pipeline_id="$(printf '%s' "$schedule_pipelines_json" | jq -r '.[0].id // empty')"
schedule_pipeline_sha="$(printf '%s' "$schedule_pipelines_json" | jq -r '.[0].sha // empty')"
schedule_pipeline_status="$(printf '%s' "$schedule_pipelines_json" | jq -r '.[0].status // empty')"
if [ -z "$schedule_pipeline_id" ]; then
  echo "[error] 未找到 ref=$GITLAB_REF source=schedule 的 pipeline" >&2
  exit 1
fi
echo "[info] 最新 schedule pipeline: id=$schedule_pipeline_id sha=$schedule_pipeline_sha status=$schedule_pipeline_status"

schedule_jobs_json="$(api_get "/pipelines/$schedule_pipeline_id/jobs?per_page=100")"
mirror_job_id="$(find_job_id "$schedule_jobs_json" "mirror-from-github")"
if [ -z "$mirror_job_id" ]; then
  echo "[error] schedule pipeline $schedule_pipeline_id 中未找到 mirror-from-github 作业" >&2
  exit 1
fi
mirror_job_status="$(printf '%s' "$schedule_jobs_json" | jq -r --arg id "$mirror_job_id" '
  map(select((.id | tostring) == $id)) | .[0].status // empty
')"
echo "[info] mirror-from-github: job_id=$mirror_job_id status=$mirror_job_status"

mirror_trace="$(api_get "/jobs/$mirror_job_id/trace")"
assert_trace_not_contains "$mirror_trace" 'rejected|remote rejected|non-fast-forward|deny updating a hidden ref' \
  "mirror-from-github 日志中出现 ref 被拒绝迹象"
echo "[info] mirror-from-github trace 未发现 ref rejected 相关报错"

echo "[step] 检查提交 $COMMIT_SHA 对应的 push pipeline 与 verify 作业"
push_pipelines_json="$(api_get "/pipelines?ref=$(urlencode "$GITLAB_REF")&source=push&sha=$COMMIT_SHA&per_page=20")"
push_pipeline_id="$(printf '%s' "$push_pipelines_json" | jq -r '.[0].id // empty')"
push_pipeline_status="$(printf '%s' "$push_pipelines_json" | jq -r '.[0].status // empty')"
if [ -z "$push_pipeline_id" ]; then
  echo "[error] 未找到 ref=$GITLAB_REF sha=$COMMIT_SHA source=push 的 pipeline" >&2
  exit 1
fi
echo "[info] 对应 push pipeline: id=$push_pipeline_id status=$push_pipeline_status"

push_jobs_json="$(api_get "/pipelines/$push_pipeline_id/jobs?per_page=100")"
verify_job_id="$(find_job_id "$push_jobs_json" "verify")"
if [ -z "$verify_job_id" ]; then
  echo "[error] push pipeline $push_pipeline_id 中未找到 verify 作业" >&2
  exit 1
fi
verify_job_status="$(printf '%s' "$push_jobs_json" | jq -r --arg id "$verify_job_id" '
  map(select((.id | tostring) == $id)) | .[0].status // empty
')"
echo "[info] verify: job_id=$verify_job_id status=$verify_job_status"

verify_trace="$(api_get "/jobs/$verify_job_id/trace")"
assert_trace_contains "$verify_trace" 'openjdk version "1\.8|java version "1\.8|Java version: 1\.8' \
  "verify 日志中未发现 JDK 8 证据"
assert_trace_contains "$verify_trace" 'Apache Maven|Maven home:' \
  "verify 日志中未发现 Maven 版本输出"
assert_trace_contains "$verify_trace" 'mvn -B verify|BUILD SUCCESS' \
  "verify 日志中未发现 mvn verify 执行证据"

echo "[ok] GitLab 验收通过：提交已镜像，mirror trace 无 ref rejected，verify trace 含 JDK 8 / Maven / mvn verify 证据"
