import assert from 'node:assert/strict'
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import test from 'node:test'
import { loadSourceEnvironment } from '../lib/source-environment.mjs'

test('source services keep their own settings and OS overrides', () => {
  const root = mkdtempSync(path.join(tmpdir(), 'tianshu-source-env-'))
  try {
    writeFileSync(path.join(root, '.env'), 'REDIS_HOST=legacy\nLEGACY_SETTING=present\n')
    writeFileSync(path.join(root, '.env.server'), "REDIS_HOST=server-redis\nCONSOLE_PASSWORD='$2b$10$hash#literal'\n")
    writeFileSync(path.join(root, '.env.client'), 'REDIS_HOST=client-redis\nSDK_ACCESS_TOKEN=client-token\n')
    const server = loadSourceEnvironment(root, 'server', {})
    const client = loadSourceEnvironment(root, 'client', { REDIS_HOST: 'os-redis' })
    assert.equal(server.REDIS_HOST, 'server-redis')
    assert.equal(server.CONSOLE_PASSWORD, '$2b$10$hash#literal')
    assert.equal(server.SDK_ACCESS_TOKEN, undefined)
    assert.equal(client.REDIS_HOST, 'os-redis')
    assert.equal(client.SDK_ACCESS_TOKEN, 'client-token')
    assert.equal(client.CONSOLE_PASSWORD, undefined)
    assert.equal(client.LEGACY_SETTING, undefined)
    writeFileSync(path.join(root, '.env.server.local'), 'REDIS_HOST=127.0.0.1\n')
    assert.equal(loadSourceEnvironment(root, 'server', {}).REDIS_HOST, 'server-redis')
    assert.equal(loadSourceEnvironment(root, 'server', { REDIS_HOST: 'os-redis' }).REDIS_HOST, 'os-redis')
  } finally {
    rmSync(root, { recursive: true, force: true })
  }
})
