#!/usr/bin/env sh
# Project-local JDK selection; does not change global jenv configuration.
if [ -z "${JAVA_HOME:-}" ] && [ "$(uname -s)" = "Darwin" ]; then
  JAVA_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null || true)
  export JAVA_HOME
fi
if [ -n "${JAVA_HOME:-}" ]; then
  PATH="$JAVA_HOME/bin:$PATH"
  export PATH
fi
if ! java -version >/dev/null 2>&1; then
  echo '无法运行 Java。请安装 JDK 21 并设置 JAVA_HOME。' >&2
  exit 1
fi
