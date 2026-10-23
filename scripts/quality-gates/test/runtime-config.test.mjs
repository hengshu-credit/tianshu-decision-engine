import test from 'node:test'
import assert from 'node:assert/strict'
import { checkRuntimeConfig } from '../check-runtime-config.mjs'
import { readFileSync } from 'node:fs'
import { parseEnv } from 'node:util'
import { createPrivateKey, createPublicKey, sign, verify } from 'node:crypto'

const settings = { RUNTIME_RULE_SERVER_URL: 'http://127.0.0.1:8080', RULE_AUTH_TYPE: 'API_KEY', RULE_API_KEY: 'private-api-key',
  HTTP_ACCESS_TOKEN: 'h'.repeat(32), SDK_ACCESS_TOKEN: 's'.repeat(32), HTTP_RULE_ALLOWED_CODES: 'CaseSensitive', SDK_RULE_ALLOWED_CODES: 'CaseSensitive',
  RUNTIME_RULE_PROJECT_ID: '9007199254740993', RUNTIME_RULE_PROJECT_CODE: 'Project_A',
  REDIS_HOST: '127.0.0.1', REDIS_PORT: '6379', REDIS_DATABASE: '0', REDIS_PASSWORD: 'private-redis-password' }

test('HTTP 自检不要求 Redis；SDK 保留大整数项目 ID 并要求项目订阅配置', () => {
  const http = { ...settings }; delete http.REDIS_PASSWORD; delete http.RUNTIME_RULE_PROJECT_CODE
  assert.equal(checkRuntimeConfig(http, 'http').passed, true)
  assert.equal(checkRuntimeConfig(http, 'sdk').passed, false)
  assert.equal(checkRuntimeConfig(settings, 'sdk').passed, true)
  assert.equal(settings.RUNTIME_RULE_PROJECT_ID, '9007199254740993')
})
test('占位凭据、无效协议、错误鉴权方式和超出 Long 的项目 ID 不能通过', () => {
  for (const changes of [{ RULE_API_KEY: 'replace-with-api-key' }, { RUNTIME_RULE_SERVER_URL: 'file:///tmp' },
    { RULE_AUTH_TYPE: 'TYPO' }, { RUNTIME_RULE_PROJECT_ID: '9223372036854775808' }, { SDK_ACCESS_TOKEN: 'short' }, { REDIS_PORT: '65536' }]) {
    assert.equal(checkRuntimeConfig({ ...settings, ...changes }, 'sdk').passed, false)
  }
})
test('自检输出不包含任何原始凭据；全局模式仍必须有私钥', () => {
  const report = JSON.stringify(checkRuntimeConfig(settings, 'sdk'))
  for (const secret of [settings.RULE_API_KEY, settings.REDIS_PASSWORD, settings.SDK_ACCESS_TOKEN]) assert.equal(report.includes(secret), false)
  assert.equal(checkRuntimeConfig({ ...settings, RULE_AUTH_TYPE: 'GLOBAL_KEY' }, 'sdk').passed, false)
  assert.equal(checkRuntimeConfig({ ...settings, RULE_AUTH_TYPE: 'GLOBAL_KEY', RULE_GLOBAL_PRIVATE_KEY: 'private-value', SDK_RULE_ALLOWED_CODES: '' }, 'sdk').passed, true)
})

test('快速试运行示例提供完整凭据，Redis 一致，两个运行时配置均可通过自检', () => {
  const examples = Object.fromEntries(['server', 'client'].map(service => [service,
    parseEnv(readFileSync(new URL(`../../../.env.${service}.example`, import.meta.url), 'utf8'))]))
  const fields = { server: ['MYSQL_PASSWORD', 'MYSQL_ROOT_PASSWORD', 'REDIS_PASSWORD', 'RULE_AUTH_MASTER_KEY', 'KAFKA_PASSWORD'],
    client: ['HTTP_ACCESS_TOKEN', 'SDK_ACCESS_TOKEN', 'REDIS_PASSWORD'] }
  for (const [service, keys] of Object.entries(fields)) {
    for (const key of keys) {
      const value = examples[service][key]
      assert.match(value, /^[a-zA-Z0-9_-]{32,}$/, `${service}: ${key} 应为可直接解析的示例凭据`)
      assert.equal(value.startsWith('replace-with-'), false)
    }
  }
  for (const key of ['REDIS_HOST', 'REDIS_PORT', 'REDIS_DATABASE', 'REDIS_PASSWORD']) {
    assert.equal(examples.client[key], examples.server[key], key)
  }
  assert.equal(new Set(examples.server.RULE_AUTH_MASTER_KEY).size >= 8, true)
  assert.equal(examples.server.RULE_GLOBAL_ACCESS_ENABLED, 'true')
  assert.equal(examples.client.RULE_AUTH_TYPE, 'GLOBAL_KEY')
  assert.equal(checkRuntimeConfig(examples.client, 'http').passed, true)
  assert.equal(checkRuntimeConfig(examples.client, 'sdk').passed, true)
})

test('示例 Ed25519 公私钥及标识配对，签名可验证且篡改消息验证失败', () => {
  const server = parseEnv(readFileSync(new URL('../../../.env.server.example', import.meta.url), 'utf8'))
  const client = parseEnv(readFileSync(new URL('../../../.env.client.example', import.meta.url), 'utf8'))
  assert.equal(client.RULE_GLOBAL_KEY_ID, server.RULE_GLOBAL_ACCESS_KEY_ID)
  const privateKey = createPrivateKey({ key: Buffer.from(client.RULE_GLOBAL_PRIVATE_KEY, 'base64'), format: 'der', type: 'pkcs8' })
  const publicKey = createPublicKey({ key: Buffer.from(server.RULE_GLOBAL_ACCESS_PUBLIC_KEY, 'base64'), format: 'der', type: 'spki' })
  assert.equal(privateKey.asymmetricKeyType, 'ed25519')
  assert.equal(publicKey.asymmetricKeyType, 'ed25519')
  const message = Buffer.from('tianshu-example-configuration')
  const signature = sign(null, message, privateKey)
  assert.equal(verify(null, message, publicKey, signature), true)
  assert.equal(verify(null, Buffer.from('tampered-message'), publicKey, signature), false)
})
