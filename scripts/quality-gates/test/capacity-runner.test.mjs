import test from 'node:test'
import assert from 'node:assert/strict'
import { mkdtemp, readFile, rm } from 'node:fs/promises'
import { createServer } from 'node:http'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

import {
  buildCapacityReport,
  aggregateCapacityReports,
  evaluateThresholds,
  runCapacityScenario,
  summarizeSamples,
  writeCapacityReports,
} from '../lib/capacity-runner.mjs'

test('aggregates multi-node summaries and keeps node failures attributable', () => {
  const result = aggregateCapacityReports([
    { target: 'http://node-a/execute', summary: { total: 10, success: 10, failed: 0, errorRate: 0, throughput: 10, medianMs: 10, p95Ms: 20, p99Ms: 25 }, result: { passed: true, failures: [] } },
    { target: 'http://node-b/execute', summary: { total: 8, success: 7, failed: 1, errorRate: 0.125, throughput: 8, medianMs: 12, p95Ms: 40, p99Ms: 50 }, result: { passed: false, failures: ['errorRate 0.125 exceeds 0.1'] } },
  ], { maxErrorRate: 0.1, maxP95Ms: 100, minThroughput: 1 })
  assert.equal(result.summary.total, 18)
  assert.equal(result.summary.failed, 1)
  assert.equal(result.summary.throughput, 18)
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

  assert.deepEqual(summary, {
    total: 4,
    success: 3,
    failed: 1,
    errorRate: 0.25,
    throughput: 4,
    medianMs: 25,
    p95Ms: 40,
    p99Ms: 40,
  })
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
    response.end('{"ok":true}')
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
