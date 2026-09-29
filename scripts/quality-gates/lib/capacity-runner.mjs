import { mkdir, writeFile } from 'node:fs/promises'
import { join } from 'node:path'
import { performance } from 'node:perf_hooks'

import { sanitizeTarget } from './capacity-config.mjs'

export function summarizeSamples(samples, durationMs) {
  const latencies = samples.map(sample => sample.latencyMs).sort((a, b) => a - b)
  const success = samples.filter(sample => sample.ok).length
  const total = samples.length
  return {
    total,
    success,
    failed: total - success,
    errorRate: total === 0 ? 1 : (total - success) / total,
    throughput: durationMs <= 0 ? 0 : total / (durationMs / 1000),
    medianMs: median(latencies),
    p95Ms: percentile(latencies, 0.95),
    p99Ms: percentile(latencies, 0.99),
  }
}

export function evaluateThresholds(summary, config) {
  const failures = []
  if (summary.errorRate > config.maxErrorRate) {
    failures.push(`errorRate ${summary.errorRate} exceeds ${config.maxErrorRate}`)
  }
  if (summary.p95Ms > config.maxP95Ms) {
    failures.push(`p95Ms ${summary.p95Ms} exceeds ${config.maxP95Ms}`)
  }
  if (summary.throughput < config.minThroughput) {
    failures.push(`throughput ${summary.throughput} is below ${config.minThroughput}`)
  }
  return { passed: failures.length === 0, failures }
}

export async function runCapacityScenario(config, request) {
  await runWindow(config, request, config.warmupSeconds * 1000, false)
  const measured = await runWindow(
    config,
    request,
    config.durationSeconds * 1000,
    true
  )
  const summary = summarizeSamples(measured.samples, measured.durationMs)
  return {
    summary,
    evaluation: evaluateThresholds(summary, config),
  }
}

export function buildCapacityReport(config, request, summary, evaluation) {
  return {
    generatedAt: new Date().toISOString(),
    target: sanitizeTarget(config.url),
    request: {
      method: String(request.method || 'POST').toUpperCase(),
      headerNames: Object.keys(request.headers || {}).sort(),
      bodyIncluded: request.body !== undefined,
    },
    load: {
      concurrency: config.concurrency,
      warmupSeconds: config.warmupSeconds,
      durationSeconds: config.durationSeconds,
    },
    thresholds: {
      maxErrorRate: config.maxErrorRate,
      maxP95Ms: config.maxP95Ms,
      minThroughput: config.minThroughput,
    },
    summary,
    result: evaluation,
  }
}

export function aggregateCapacityReports(reports, config) {
  const nodes = reports || []
  const total = nodes.reduce((sum, report) => sum + report.summary.total, 0)
  const success = nodes.reduce((sum, report) => sum + report.summary.success, 0)
  const failed = total - success
  const summary = {
    total,
    success,
    failed,
    errorRate: total === 0 ? 1 : failed / total,
    throughput: nodes.reduce((sum, report) => sum + report.summary.throughput, 0),
    medianMs: nodes.length === 0 ? 0 : Math.max(...nodes.map(report => report.summary.medianMs)),
    p95Ms: nodes.length === 0 ? 0 : Math.max(...nodes.map(report => report.summary.p95Ms)),
    p99Ms: nodes.length === 0 ? 0 : Math.max(...nodes.map(report => report.summary.p99Ms)),
  }
  const evaluation = evaluateThresholds(summary, config)
  for (const report of nodes) {
    if (!report.result.passed) {
      for (const failure of report.result.failures) {
        evaluation.failures.push(`${report.target}: ${failure}`)
      }
    }
  }
  evaluation.passed = evaluation.failures.length === 0
  return { summary, evaluation }
}

export async function writeMultiCapacityReports(report, directory) {
  await mkdir(directory, { recursive: true })
  await writeFile(
    join(directory, 'capacity-report.multi.json'),
    `${JSON.stringify(report, null, 2)}\n`,
    'utf8'
  )
  const failures = report.result.failures.length === 0
    ? '无'
    : report.result.failures.map(failure => `- ${failure}`).join('\n')
  const nodeLines = report.nodes.map(node =>
    `- ${node.target}: ${node.result.passed ? 'PASS' : 'FAIL'}，吞吐 ${format(node.summary.throughput)} req/s，p95 ${format(node.summary.p95Ms)}ms`)
  const markdown = `# 多节点容量门禁报告

- 结果：${report.result.passed ? 'PASS' : 'FAIL'}
- 节点数：${report.nodes.length}
- 聚合吞吐：${format(report.summary.throughput)} req/s
- 聚合错误率：${format(report.summary.errorRate * 100)}%
- 最差 p95：${format(report.summary.p95Ms)}ms

## 节点结果

${nodeLines.join('\n')}

## 未通过项

${failures}
`
  await writeFile(join(directory, 'capacity-report.multi.md'), markdown, 'utf8')
}

export async function writeCapacityReports(report, directory) {
  await mkdir(directory, { recursive: true })
  await writeFile(
    join(directory, 'capacity-report.json'),
    `${JSON.stringify(report, null, 2)}\n`,
    'utf8'
  )
  const failures = report.result.failures.length === 0
    ? '无'
    : report.result.failures.map(failure => `- ${failure}`).join('\n')
  const markdown = `# 容量门禁报告

- 结果：${report.result.passed ? 'PASS' : 'FAIL'}
- 目标：${report.target}
- 请求方法：${report.request.method}
- 并发：${report.load.concurrency}
- 预热：${report.load.warmupSeconds}s
- 测量：${report.load.durationSeconds}s
- 样本：${report.summary.total}
- 吞吐：${format(report.summary.throughput)} req/s
- 错误率：${format(report.summary.errorRate * 100)}%
- 中位延迟：${format(report.summary.medianMs)}ms
- p95：${format(report.summary.p95Ms)}ms
- p99：${format(report.summary.p99Ms)}ms

## 未通过项

${failures}
`
  await writeFile(join(directory, 'capacity-report.md'), markdown, 'utf8')
}

async function runWindow(config, request, durationMs, collectSamples) {
  const samples = []
  const started = performance.now()
  const deadline = started + durationMs
  const workers = Array.from({ length: config.concurrency }, () =>
    runWorker(config.url, request, deadline, collectSamples ? samples : null)
  )
  await Promise.all(workers)
  return { samples, durationMs: performance.now() - started }
}

async function runWorker(url, request, deadline, samples) {
  while (performance.now() < deadline) {
    const started = performance.now()
    const controller = new AbortController()
    const timeout = setTimeout(
      () => controller.abort(),
      Number(request.timeoutMs) > 0 ? Number(request.timeoutMs) : 10000
    )
    let ok = false
    try {
      const response = await fetch(url, {
        method: String(request.method || 'POST').toUpperCase(),
        headers: request.headers || {},
        body: request.body === undefined
          ? undefined
          : typeof request.body === 'string'
            ? request.body
            : JSON.stringify(request.body),
        signal: controller.signal,
      })
      ok = response.ok
      await response.arrayBuffer()
    } catch {
      ok = false
    } finally {
      clearTimeout(timeout)
      if (samples) {
        samples.push({ ok, latencyMs: performance.now() - started })
      }
    }
  }
}

function format(value) {
  return Number(value).toFixed(2)
}

function median(values) {
  if (values.length === 0) return 0
  const middle = Math.floor(values.length / 2)
  return values.length % 2 === 0
    ? (values[middle - 1] + values[middle]) / 2
    : values[middle]
}

function percentile(values, ratio) {
  if (values.length === 0) return 0
  const index = Math.max(0, Math.ceil(values.length * ratio) - 1)
  return values[index]
}
