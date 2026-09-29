import test from 'node:test'
import assert from 'node:assert/strict'
import { mkdtemp, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

import {
  loadRequestDefinition,
  parseCapacityConfig,
  redactObject,
  sanitizeTarget,
} from '../lib/capacity-config.mjs'

const validArgs = [
  '--url', 'http://127.0.0.1:8080/api/rule/open/execute?token=secret',
  '--request-file', 'request.json',
  '--concurrency', '4',
  '--warmup-seconds', '1',
  '--duration-seconds', '5',
  '--max-error-rate', '0.01',
  '--max-p95-ms', '300',
  '--min-throughput', '10',
]

test('requires an explicit target, request file, and all thresholds', () => {
  assert.throws(() => parseCapacityConfig([]), /--url/)
  assert.throws(
    () => parseCapacityConfig(validArgs.filter((_, index) => index < 2)),
    /--request-file/
  )
})

test('accepts comma separated node targets for a multi-node gate', () => {
  const config = parseCapacityConfig(validArgs
    .filter((_, index) => index !== 0 && index !== 1)
    .concat(['--urls', 'http://node-a:8080/execute,http://node-b:8080/execute']))
  assert.deepEqual(config.urls, [
    'http://node-a:8080/execute',
    'http://node-b:8080/execute',
  ])
  assert.equal(config.url, 'http://node-a:8080/execute')
})

test('rejects unsafe protocols and non-positive load settings', () => {
  assert.throws(
    () => parseCapacityConfig(validArgs.map(value =>
      value.startsWith('http://') ? 'file:///tmp/request' : value
    )),
    /http/
  )
  const args = [...validArgs]
  args[args.indexOf('--concurrency') + 1] = '0'
  assert.throws(() => parseCapacityConfig(args), /concurrency/)
})

test('redacts secrets recursively and removes target query values', () => {
  const redacted = redactObject({
    Authorization: 'Bearer top-secret',
    nested: { password: 'plain', customerId: 'C001' },
    tokenValue: 'abc',
  })

  assert.deepEqual(redacted, {
    Authorization: '[REDACTED]',
    nested: { password: '[REDACTED]', customerId: 'C001' },
    tokenValue: '[REDACTED]',
  })
  assert.equal(
    sanitizeTarget('http://127.0.0.1:8080/execute?token=secret&name=test'),
    'http://127.0.0.1:8080/execute?token=%5BREDACTED%5D&name=%5BREDACTED%5D'
  )
  assert.equal(JSON.stringify(redacted).includes('top-secret'), false)
  assert.equal(JSON.stringify(redacted).includes('plain'), false)
})

test('loads a JSON request definition and rejects malformed shapes', async t => {
  const directory = await mkdtemp(join(tmpdir(), 'tianshu-request-'))
  t.after(() => rm(directory, { recursive: true, force: true }))
  const valid = join(directory, 'valid.json')
  const invalid = join(directory, 'invalid.json')
  await writeFile(valid, JSON.stringify({
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: { requestId: 'R1' },
    timeoutMs: 1000,
  }))
  await writeFile(invalid, JSON.stringify({ method: 'TRACE', headers: [] }))

  assert.deepEqual(await loadRequestDefinition(valid), {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: { requestId: 'R1' },
    timeoutMs: 1000,
  })
  await assert.rejects(() => loadRequestDefinition(invalid), /method|headers/)
})
