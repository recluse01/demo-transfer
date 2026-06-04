#!/usr/bin/env bash
set -euo pipefail

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "[error] 缺少命令: $1" >&2
    exit 1
  fi
}

for cmd in docker java mvn; do
  require_cmd "$cmd"
done

JAVA_HOME="$(java -XshowSettings:properties -version 2>&1 | sed -n 's/^[[:space:]]*java.home = //p' | head -n 1)"
case "$JAVA_HOME" in
  */jre) JAVA_HOME="${JAVA_HOME%/jre}" ;;
esac

if [ -z "$JAVA_HOME" ] || [ ! -d "$JAVA_HOME" ]; then
  echo "[error] 无法推导有效的 JAVA_HOME: ${JAVA_HOME:-<empty>}" >&2
  exit 1
fi

export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

echo "[info] 当前用户: $(id -un)"
echo "[info] docker 路径: $(command -v docker)"
echo "[info] java 路径: $(command -v java)"
echo "[info] mvn 路径: $(command -v mvn)"
echo "[info] JAVA_HOME=$JAVA_HOME"

if [ "$(id -un)" != "gitlab-runner" ]; then
  echo "[warn] 建议以 gitlab-runner 用户执行此脚本，当前用户为 $(id -un)" >&2
fi

echo "[step] docker pull mysql:8.0.36"
docker pull mysql:8.0.36

echo "[step] docker ps"
docker ps

echo "[step] java -version"
java -version

echo "[step] mvn -v"
mvn -v
