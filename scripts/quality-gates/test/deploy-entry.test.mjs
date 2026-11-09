import test from 'node:test'
import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'

test('自动部署入口：双 Compose、制品校验、原配置和副本保留、迁移与失败恢复', () => {
  const result = spawnSync(process.platform === 'win32' ? 'python' : 'python3',
    ['-B', fileURLToPath(new URL('./deploy_entry_test.py', import.meta.url))], { encoding: 'utf8' })
  assert.ifError(result.error)
  assert.equal(result.status, 0, result.stdout + result.stderr)
})
