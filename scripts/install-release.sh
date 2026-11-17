#!/usr/bin/env bash
# 在现有安装目录运行，首次和后续发布使用同一入口。
set -euo pipefail

if (( $# < 1 )); then
  echo '在安装目录执行 bash install-release.sh 发布包.zip [--sdk] [--migration-script 迁移脚本]，首次部署和更新使用同一命令。' >&2
  exit 2
fi

target=$(pwd -P)
archive=$(realpath -e -- "$1")
shift
checksum="$archive.sha256"
IFS= read -r manifest_line < "$checksum"
expected=${manifest_line:0:64}
if [[ ! $expected =~ ^[[:xdigit:]]{64}$ || ${manifest_line:64} != "  ${archive##*/}" ]]; then
  echo "校验文件与指定发布包不匹配：$checksum" >&2
  exit 1
fi
actual=$(sha256sum < "$archive")
if [[ ${actual:0:64} != "${expected,,}" ]]; then
  echo 'ZIP 的 SHA256 校验失败。' >&2
  exit 1
fi

releases="$target/releases"
if [[ -f "$target/.deployment-layout.json" ]]; then
  releases="$target/deploy-prod/releases"
elif [[ -d "$target/deploy-prod/docker" || -d "$target/config" || -e "$target/deploy-prod/current" ]]; then
  echo '检测到未整理或缺少布局记录的目录，请按 README-DEPLOY.md 核对原运行目录。' >&2
  exit 1
fi
if [[ "$target" == / || "$target" == "$HOME" || -L "$releases" || -L "$target/deploy-prod" ]]; then
  echo '请进入真实的天枢安装目录运行。' >&2
  exit 1
fi
mkdir -p -- "$releases"
release=$(mktemp -d "$releases/update-XXXXXXXX")
python3 -X utf8 -m zipfile -e "$archive" "$release"
printf '已校验并解压至 %s\n' "$release"
echo '正在识别安装环境。server 启动时会按本包 schema.sql 核对数据库并输出结构升级记录。'

# 两条路径都由已校验的新包执行，启动升级开关只作用于本次部署。
export SPRING_SQL_INIT_MODE=always
exec python3 -X utf8 "$release/deploy.py" --target "$target" --install-app "$@"
