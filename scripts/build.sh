#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
. ./scripts/java-env.sh
exec ./mvnw -B -ntp verify "$@"
