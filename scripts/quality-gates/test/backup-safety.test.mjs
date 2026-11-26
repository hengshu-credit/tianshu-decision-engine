import test from 'node:test'
import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { mkdtempSync, readFileSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
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
test('完整恢复未提供文件仓时记录失败并阻止数据库通过充当整体验收', { skip: process.platform !== 'win32' }, () => {
  const output = mkdtempSync(join(tmpdir(), 'tianshu-restore-test-'))
  try {
    const invocation = `$env:MYSQL_ROOT_PASSWORD='guard-test'
      function global:docker {
        if (($args -join ' ') -match 'information_schema.schemata') { '0'; $global:LASTEXITCODE = 0 }
        else { throw 'UNEXPECTED_DOCKER_CALL' }
      }
      & '${script}' -OutputDirectory '${output.replaceAll("'", "''")}'`
    const result = spawnSync('pwsh', ['-NoProfile', '-Command', invocation], { encoding: 'utf8' })
    assert.notEqual(result.status, 0)
    assert.match(result.stderr, /Full recovery needs the SFTP/)
    assert.doesNotMatch(result.stderr, /UNEXPECTED_DOCKER_CALL/)
    const report = JSON.parse(readFileSync(join(output, 'backup-restore-summary.json'), 'utf8'))
    assert.equal(report.result, 'FAIL')
    assert.equal(report.scope, 'DATABASE_AND_SFTP')
  } finally {
    rmSync(output, { recursive: true, force: true })
  }
})

test('恢复库已存在时拒绝覆盖，不能执行 dump 或 DROP', { skip: process.platform !== 'win32' }, () => {
  const result = run('source_db', 'existing_db', true)
  assert.notEqual(result.status, 0)
  assert.match(result.stderr, /Restore database already exists/)
  assert.doesNotMatch(result.stderr, /UNEXPECTED_DOCKER_CALL/)
})
