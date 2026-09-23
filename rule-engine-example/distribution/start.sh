#!/bin/sh
set -eu
cd "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
if [ "${1:-}" = "--rebuilt" ]; then
  shift
  test -f build/example.jar || { echo 'Run java tools/BuildExample.java first.' >&2; exit 1; }
  exec java -cp 'build/example.jar:example/lib/*' com.hengshucredit.rule.example.RuleExampleApplication "$@"
fi
exec java -jar example/rule-engine-example.jar "$@"
