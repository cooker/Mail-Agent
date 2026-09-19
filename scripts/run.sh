#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
umask 077
if [ -f .env ]; then
  set -a
  . ./.env
  set +a
fi
. ./scripts/java-env.sh
if [ ! -f target/mail-agent-1.0.0.jar ]; then
  echo '未找到 JAR，请先运行 ./scripts/build.sh' >&2
  exit 1
fi
exec java -jar target/mail-agent-1.0.0.jar "$@"
