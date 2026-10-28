#!/bin/sh
set -eu
script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
command -v node >/dev/null 2>&1 || { printf '%s\n' '需要安装 Node.js 20.19+' >&2; exit 1; }
exec node "$script_dir/package-deployment.mjs" "$@"
