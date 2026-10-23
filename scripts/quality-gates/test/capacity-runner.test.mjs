import test from 'node:test'
import assert from 'node:assert/strict'
import { mkdtemp, readFile, rm } from 'node:fs/promises'
import { createServer } from 'node:http'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { performance } from 'node:perf_hooks'
import { writeFile } from 'node:fs/promises'
import { loadRequestDefinition } from '../lib/capacity-config.mjs'
import * as capacity from '../lib/capacity-runner.mjs'

import {
  buildCapacityReport,
  createSampleSummary,
  aggregateCapacityReports,
  evaluateThresholds,
  runCapacityScenario,
  summarizeSamples,
  writeCapacityReports,
} from '../lib/capacity-runner.mjs'

async function arrivalServer(t, handle) {
  const server = createServer(handle)
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
  t.after(() => new Promise(resolve => server.close(resolve)))
  return `http://127.0.0.1:${server.address().port}/execute`
}

function arrivalConfig(url, extra = {}) {
  return { url, concurrency: 4, arrivalRate: 20, maxScheduleLagMs: 100,
    warmupSeconds: 0, durationSeconds: 0.3, maxErrorRate: 0,
    maxP95Ms: 1000, minThroughput: 1, ...extra }
}

test('constant arrival scheduling sends the planned count independently of response duration', async t => {
  let received = 0
  const url = await arrivalServer(t, (_request, response) => {
    received++
    setTimeout(() => response.end('{"code":"000000","success":true}'), 35)
  })
  const result = await runCapacityScenario(arrivalConfig(url), { method: 'POST' })
  assert.equal(received, 6)
  assert.equal(result.summary.total, 6)
  assert.equal(result.summary.loadGeneration.scheduled, 6)
  assert.equal(result.summary.loadGeneration.dispatched, 6)
  assert.equal(result.summary.loadGeneration.droppedConcurrency, 0)
  assert.equal(result.summary.loadGeneration.droppedScheduleLag, 0)
  assert.equal(result.evaluation.passed, true)
})

test('overload drops scheduled arrivals at a bounded concurrency and cannot pass on successful survivors', async t => {
  let inFlight = 0
  let peak = 0
  const url = await arrivalServer(t, (_request, response) => {
    inFlight++
    peak = Math.max(peak, inFlight)
    setTimeout(() => { inFlight--; response.end('{"code":"000000","success":true}') }, 80)
  })
  const result = await runCapacityScenario(arrivalConfig(url, {
    concurrency: 1, arrivalRate: 100, durationSeconds: 0.24, maxErrorRate: 1,
  }), { method: 'POST' })
  assert.equal(peak, 1)
  assert.equal(result.summary.failed, 0)
  assert.equal(result.summary.loadGeneration.scheduled, 24)
  assert.ok(result.summary.loadGeneration.droppedConcurrency > 0)
  assert.equal(result.summary.total + result.summary.loadGeneration.droppedConcurrency
    + result.summary.loadGeneration.droppedScheduleLag, 24)
  assert.equal(result.evaluation.passed, false)
  assert.ok(result.evaluation.failures.some(failure => failure.includes('scheduled arrivals')))
})

test('a stalled generator reports missed arrivals instead of issuing an unbounded catch-up burst', async t => {
  let first = true
  const url = await arrivalServer(t, (_request, response) => {
    if (first) {
      first = false
      const until = performance.now() + 75
      while (performance.now() < until) { /* deliberate event-loop fault */ }
    }
    response.end('{"code":"000000","success":true}')
  })
  const result = await runCapacityScenario(arrivalConfig(url, {
    arrivalRate: 100, maxScheduleLagMs: 20, durationSeconds: 0.2,
  }), { method: 'POST' })
  assert.equal(result.summary.loadGeneration.scheduled, 20)
  assert.ok(result.summary.loadGeneration.droppedScheduleLag >= 3)
  assert.equal(result.evaluation.passed, false)
})

test('load reports identify per-target arrival rate and failed generation independent of HTTP error budget', () => {
  const config = arrivalConfig('http://localhost/execute')
  const summary = { total: 5, success: 5, failed: 0, errorRate: 0, throughput: 5, p95Ms: 10,
    loadGeneration: { scheduled: 6, dispatched: 5, droppedConcurrency: 1, droppedScheduleLag: 0 } }
  const evaluation = evaluateThresholds(summary, config)
  assert.equal(evaluation.passed, false)
  const report = buildCapacityReport(config, {}, summary, evaluation)
  assert.equal(report.load.mode, 'CONSTANT_ARRIVAL')
  assert.equal(report.load.arrivalRatePerTarget, 20)
  assert.equal(report.load.maxScheduleLagMs, 100)
})

test('aggregation rechecks dropped arrival evidence even if a supplied node result says pass', () => {
  const summary = { total: 5, success: 5, failed: 0, errorRate: 0, throughput: 5, requestRate: 5,
    medianMs: 10, p95Ms: 10, p99Ms: 10,
    loadGeneration: { scheduled: 6, dispatched: 5, droppedConcurrency: 1, droppedScheduleLag: 0 } }
  const result = aggregateCapacityReports([{ target: 'http://node/execute', summary, result: { passed: true, failures: [] } }],
    arrivalConfig('http://node/execute'), 1000)
  assert.equal(result.summary.loadGeneration.scheduled, 6)
  assert.equal(result.evaluation.passed, false)
})

test('aggregate throughput uses the common observation duration rather than adding incompatible rates', () => {
  const reports = [200, 1000].map(elapsedMs => ({ target: `http://node-${elapsedMs}`,
    summary: { total: 1, success: 1, failed: 0, errorRate: 0, elapsedMs,
      throughput: 1000 / elapsedMs, requestRate: 1000 / elapsedMs, medianMs: elapsedMs, p95Ms: elapsedMs, p99Ms: elapsedMs },
    result: { passed: true, failures: [] } }))
  const { summary } = aggregateCapacityReports(reports, arrivalConfig('http://unused'), 1000)
  assert.equal(summary.throughput, 2)
  assert.equal(summary.requestRate, 2)
  assert.equal(summary.nodeThroughputSum, 6)
})

test('multiple targets finish all warmups before sharing one measured observation window', async t => {
  let slowWarmupFinishedAt = 0
  const fast = await arrivalServer(t, (_request, response) => response.end('{"code":"000000","success":true}'))
  const slow = await arrivalServer(t, (_request, response) => {
    setTimeout(() => {
      if (!slowWarmupFinishedAt) slowWarmupFinishedAt = Date.now()
      response.end('{"code":"000000","success":true}')
    }, 70)
  })
  const result = await capacity.runCapacityTargets({ ...arrivalConfig(fast), urls: [fast, slow],
    arrivalRate: null, concurrency: 1, warmupSeconds: 0.01, durationSeconds: 0.1 }, { method: 'POST' })
  const starts = result.nodes.map(node => node.summary.measurementStartedAtMs)
  assert.equal(starts[0], starts[1])
  assert.ok(starts[0] >= slowWarmupFinishedAt - 5)
  assert.equal(result.summary.throughput, result.summary.success / (result.summary.elapsedMs / 1000))
  assert.ok(result.summary.elapsedMs >= Math.max(...result.nodes.map(node => node.summary.elapsedMs)))
})

test('arrival reports expose bounded catch-up admitted inside the explicit scheduling budget', async t => {
  let first = true
  t.mock.method(globalThis, 'fetch', async () => ({ status: 200, async text() {
    if (first) {
      first = false
      const until = performance.now() + 75
      while (performance.now() < until) { /* controlled scheduling lag below 100ms budget */ }
    }
    return '{"code":"000000","success":true}'
  } }))
  const result = await runCapacityScenario(arrivalConfig('http://unused', {
    arrivalRate: 50, concurrency: 100, durationSeconds: 0.2, maxScheduleLagMs: 100,
  }), { method: 'POST' })
  assert.ok(result.summary.loadGeneration.catchUpDispatched >= 2)
  assert.ok(result.summary.loadGeneration.maxDispatchesInMillisecond >= 2)
  assert.equal(result.summary.loadGeneration.droppedScheduleLag, 0)
})

test('a blocked event loop cannot turn an expired request into a success by delaying its abort timer', async t => {
  t.mock.method(globalThis, 'fetch', async () => ({
    status: 200,
    async text() {
      const until = performance.now() + 30
      while (performance.now() < until) { /* simulate expensive synchronous JSON decoding */ }
      return '{"code":"000000","success":true}'
    },
  }))
  const result = await runCapacityScenario({ ...arrivalConfig('http://unused'), arrivalRate: null,
    concurrency: 1, durationSeconds: 0.01 }, { method: 'POST', timeoutMs: 5 })
  assert.equal(result.summary.success, 0)
  assert.equal(result.summary.outcomes.TIMEOUT, result.summary.total)
  assert.equal(result.evaluation.passed, false)
})

test('aggregates multi-node summaries and keeps node failures attributable', () => {
  const result = aggregateCapacityReports([
    { target: 'http://node-a/execute', summary: { total: 10, success: 10, failed: 0, errorRate: 0, throughput: 10, requestRate: 10, medianMs: 10, p95Ms: 20, p99Ms: 25 }, result: { passed: true, failures: [] } },
    { target: 'http://node-b/execute', summary: { total: 8, success: 7, failed: 1, errorRate: 0.125, throughput: 7, requestRate: 8, medianMs: 12, p95Ms: 40, p99Ms: 50 }, result: { passed: false, failures: ['errorRate 0.125 exceeds 0.1'] } },
  ], { maxErrorRate: 0.1, maxP95Ms: 100, minThroughput: 1 }, 1000)
  assert.equal(result.summary.total, 18)
  assert.equal(result.summary.failed, 1)
  assert.equal(result.summary.throughput, 17)
  assert.equal(result.summary.requestRate, 18)
  assert.equal(result.summary.p95Ms, 40)
  assert.equal(result.evaluation.passed, false)
  assert.ok(result.evaluation.failures.some(failure => failure.includes('node-b')))
})

test('summarizes throughput, errors, and latency percentiles from measured samples', () => {
  const summary = summarizeSamples([
    { ok: true, latencyMs: 10 },
    { ok: true, latencyMs: 20 },
    { ok: false, latencyMs: 30 },
    { ok: true, latencyMs: 40 },
  ], 1000)

  const { medianMs, p95Ms, p99Ms, ...rates } = summary
  assert.deepEqual(rates, {
    total: 4,
    success: 3,
    failed: 1,
    errorRate: 0.25,
    throughput: 3,
    requestRate: 4,
  })
  // 3位有效数字直方图返回桶边界；不能为了精确排序保留长期运行的每条样本。
  for (const [actual, expected] of [[medianMs, 25], [p95Ms, 40], [p99Ms, 40]]) {
    assert.ok(actual >= expected && actual <= expected * 1.001, `${actual} outside histogram precision for ${expected}`)
  }
})

test('one million latency samples retain counts and tails without a growing sample array', () => {
  const metrics = createSampleSummary()
  const before = process.memoryUsage().heapUsed
  for (let i = 0; i < 1_000_000; i++) metrics.record({ ok: i % 10 !== 0, latencyMs: i % 100 + 1 })
  const summary = metrics.summary(100_000)
  assert.equal(summary.total, 1_000_000)
  assert.equal(summary.success, 900_000)
  assert.equal(summary.errorRate, 0.1)
  assert.equal(summary.throughput, 9000)
  assert.ok(summary.p95Ms >= 95 && summary.p95Ms <= 95.1)
  assert.ok(summary.p99Ms >= 99 && summary.p99Ms <= 99.1)
  assert.ok(process.memoryUsage().heapUsed - before < 16 * 1024 * 1024,
    '原始百万样本不能常驻JS堆；应只保留直方图与计数')
})

test('small sample tails cannot pass a latency gate through HDR nearest-percentile rounding', () => {
  for (const [fast, percentile] of [[10, 'p95Ms'], [50, 'p99Ms']]) {
    const summary = summarizeSamples([
      ...Array.from({ length: fast }, () => ({ ok: true, latencyMs: 10 })),
      { ok: true, latencyMs: 1000 },
    ], 1000)
    assert.ok(summary[percentile] >= 1000, `${percentile} must include the ceil-rank slow observation`)
    if (percentile === 'p95Ms') {
      assert.equal(evaluateThresholds(summary, { maxErrorRate: 0, maxP95Ms: 100, minThroughput: 1 }).passed, false)
    }
  }
})

test('raw JSON request numbers and numeric assertions remain exact without coupling to frontend packages', async t => {
  const directory = await mkdtemp(join(tmpdir(), 'tianshu-capacity-exact-'))
  t.after(() => rm(directory, { recursive: true, force: true }))
  const file = join(directory, 'request.json')
  let responseText = '{"code":"000000","success":true,"score":9007199254740992}'
  let received = ''
  const url = await arrivalServer(t, (request, response) => {
    request.on('data', chunk => { received += chunk })
    request.on('end', () => response.end(responseText))
  })
  await writeFile(file, '{"body":{"value":9007199254740993},"expectedResponse":{"success":true,"score":9007199254740993}}')
  const request = await loadRequestDefinition(file)
  const config = arrivalConfig(url, { arrivalRate: 1, durationSeconds: 0.02 })
  const wrong = await runCapacityScenario(config, request)
  assert.equal(wrong.summary.success, 0, '相邻大整数不能被Number舍入后假通过')
  assert.match(received, /9007199254740993/)
  responseText = '{"code":"000000","success":true,"score":9007199254740993}'
  assert.equal((await runCapacityScenario(config, request)).summary.success, 1)
  await writeFile(file, '{"expectedResponse":{"score":0.10000000000000001}}')
  responseText = '{"code":"000000","success":true,"score":0.1}'
  assert.equal((await runCapacityScenario(config, await loadRequestDefinition(file))).summary.success, 0)
  await writeFile(file, '{"expectedResponse":{"score":1e400}}')
  responseText = '{"code":"000000","success":true,"score":10e399,"irrelevant":{"__proto__":2,"id":9007199254740993}}'
  assert.equal((await runCapacityScenario(config, await loadRequestDefinition(file))).summary.success, 1)
})

test('passes values exactly on thresholds and reports each exceeded threshold', () => {
  const boundary = evaluateThresholds({
    errorRate: 0.01,
    p95Ms: 300,
    throughput: 10,
  }, {
    maxErrorRate: 0.01,
    maxP95Ms: 300,
    minThroughput: 10,
  })
  assert.equal(boundary.passed, true)
  assert.deepEqual(boundary.failures, [])

  const failed = evaluateThresholds({
    errorRate: 0.02,
    p95Ms: 301,
    throughput: 9,
  }, {
    maxErrorRate: 0.01,
    maxP95Ms: 300,
    minThroughput: 10,
  })
  assert.equal(failed.passed, false)
  assert.equal(failed.failures.length, 3)
})

test('runs warmup and measured requests against a real local server', async t => {
  let requests = 0
  const server = createServer((request, response) => {
    requests++
    response.writeHead(200, { 'content-type': 'application/json' })
    response.end('{"success":true,"code":"000000"}')
  })
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
  t.after(() => new Promise(resolve => server.close(resolve)))
  const address = server.address()

  const result = await runCapacityScenario({
    url: `http://127.0.0.1:${address.port}/execute`,
    concurrency: 2,
    warmupSeconds: 0.03,
    durationSeconds: 0.08,
    maxErrorRate: 0,
    maxP95Ms: 1000,
    minThroughput: 1,
  }, {
    method: 'POST',
    headers: { authorization: 'Bearer secret', 'content-type': 'application/json' },
    body: { token: 'do-not-report', value: 1 },
    timeoutMs: 1000,
  })

  assert.ok(requests > result.summary.total)
  assert.ok(result.summary.total > 0)
  assert.equal(result.summary.failed, 0)
  assert.equal(result.evaluation.passed, true)
})

test('writes reports without request credentials or body values', async t => {
  const directory = await mkdtemp(join(tmpdir(), 'tianshu-capacity-'))
  t.after(() => rm(directory, { recursive: true, force: true }))
  const report = buildCapacityReport({
    url: 'http://127.0.0.1:8080/execute?token=secret',
    concurrency: 2,
    warmupSeconds: 1,
    durationSeconds: 5,
    maxErrorRate: 0.01,
    maxP95Ms: 300,
    minThroughput: 10,
  }, {
    method: 'POST',
    headers: { Authorization: 'Bearer secret' },
    body: { password: 'plain', customerId: 'C001' },
  }, {
    total: 10,
    success: 10,
    failed: 0,
    errorRate: 0,
    throughput: 20,
    requestRate: 20,
    medianMs: 20,
    p95Ms: 30,
    p99Ms: 35,
  }, { passed: true, failures: [] })

  await writeCapacityReports(report, directory)
  const json = await readFile(join(directory, 'capacity-report.json'), 'utf8')
  const markdown = await readFile(join(directory, 'capacity-report.md'), 'utf8')

  for (const content of [json, markdown]) {
    assert.equal(content.includes('Bearer secret'), false)
    assert.equal(content.includes('plain'), false)
    assert.equal(content.includes('C001'), false)
    assert.equal(content.includes('token=secret'), false)
  }
})

for (const scenario of [
  { name: 'waiting external is not a completed decision', status: 202, body: '{"success":false,"code":"400006"}' },
  { name: 'HTTP success with a failed decision is rejected', status: 200, body: '{"success":false}' },
  { name: 'an unexpected HTML page cannot pass the execution gate', status: 200, body: '<html>Login</html>' },
  { name: 'missing execution status cannot pass the gate', status: 200, body: '{}' },
]) {
  test(scenario.name, async t => {
    const server = createServer((request, response) => {
      response.writeHead(scenario.status)
      response.end(scenario.body)
    })
    await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
    t.after(() => new Promise(resolve => server.close(resolve)))
    const result = await runCapacityScenario({
      url: `http://127.0.0.1:${server.address().port}/execute`,
      concurrency: 1, warmupSeconds: 0.01, durationSeconds: 0.04,
      maxErrorRate: 0, maxP95Ms: 1000, minThroughput: 1,
    }, { method: 'POST', timeoutMs: 1000 })
    assert.ok(result.summary.total > 0)
    assert.equal(result.summary.success, 0)
    assert.equal(result.evaluation.passed, false)
  })
}

test('checks a custom runtime envelope and exact expected output types', async t => {
  let body = { success: true, code: '000000', data: { success: true, result: { score: 88 } } }
  const server = createServer((request, response) => {
    response.writeHead(200, { 'content-type': 'application/json' })
    response.end(JSON.stringify(body))
  })
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
  t.after(() => new Promise(resolve => server.close(resolve)))
  const config = {
    url: `http://127.0.0.1:${server.address().port}/execute`,
    concurrency: 1, warmupSeconds: 0.01, durationSeconds: 0.04,
    maxErrorRate: 0, maxP95Ms: 1000, minThroughput: 1,
  }
  const request = { method: 'POST', expectedResponse: { code: '000000', 'data.success': true, 'data.result.score': 88 } }
  assert.equal((await runCapacityScenario(config, request)).evaluation.passed, true)
  body.data.result.score = '88'
  const failed = await runCapacityScenario(config, request)
  assert.equal(failed.summary.success, 0)
  assert.equal(failed.evaluation.passed, false)
})

test('minimum throughput counts completed decisions rather than failed responses', () => {
  const summary = summarizeSamples([
    { ok: true, latencyMs: 10 },
    { ok: false, latencyMs: 10 },
    { ok: false, latencyMs: 10 },
  ], 1000)
  const result = evaluateThresholds(summary, { maxErrorRate: 1, maxP95Ms: 1000, minThroughput: 2 })
  assert.equal(result.passed, false)
  assert.ok(result.failures.some(failure => failure.includes('throughput')))
})
