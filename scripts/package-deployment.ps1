# Windows PowerShell 5.1 / PowerShell 7；允许从任意工作目录调用。
$ErrorActionPreference = 'Stop'
if (-not (Get-Command node -ErrorAction SilentlyContinue)) { throw '需要安装 Node.js 20.19+' }
& node (Join-Path $PSScriptRoot 'package-deployment.mjs') @args
exit $LASTEXITCODE
