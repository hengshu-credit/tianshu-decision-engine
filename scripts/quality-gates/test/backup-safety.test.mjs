import test from 'node:test'
import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'

const script = fileURLToPath(new URL('../run-docker-backup-restore-gate.ps1', import.meta.url)).replaceAll("'", "''")
function run(source, target, existing = false) {
  const docker = existing
    ? "function global:docker { if (($args -join ' ') -match 'information_schema.schemata') { '1'; $global:LASTEXITCODE = 0 } else { throw 'UNEXPECTED_DOCKER_CALL' } }"
    : "function global:docker { throw 'UNEXPECTED_DOCKER_CALL' }"
  return spawnSync('pwsh', ['-NoProfile', '-Command', `$env:MYSQL_ROOT_PASSWORD='guard-test'; ${docker}; & '${script}' -Database ${source} -RestoreDatabase ${target}`], { encoding: 'utf8' })
}
test('备份门禁在调用 Docker 前拒绝源库作为恢复库', { skip: process.platform !== 'win32' }, () => {
  const result = run('same_db', 'same_db')
  assert.notEqual(result.status, 0)
  assert.match(result.stderr, /Source and restore databases must differ/)
  assert.doesNotMatch(result.stderr, /UNEXPECTED_DOCKER_CALL/)
})
test('恢复库已存在时拒绝覆盖，不能执行 dump 或 DROP', { skip: process.platform !== 'win32' }, () => {
  const result = run('source_db', 'existing_db', true)
  assert.notEqual(result.status, 0)
  assert.match(result.stderr, /Restore database already exists/)
  assert.doesNotMatch(result.stderr, /UNEXPECTED_DOCKER_CALL/)
})
