import { mkdir, writeFile } from 'node:fs/promises'
import { join } from 'node:path'
import { createHistogram, performance } from 'node:perf_hooks'
import { setTimeout as sleep } from 'node:timers/promises'

import { sanitizeTarget } from './capacity-config.mjs'
import { matchesCapacityResponse, parseCapacityJson } from './capacity-json.mjs'

export function summarizeSamples(samples, durationMs) {
  const metrics = createSampleSummary()
  for (const sample of samples) metrics.record(sample)
  return metrics.summary(durationMs)
}

/** 固定精度直方图，不随24小时运行的请求数量保留/排序原始样本。 */
export function createSampleSummary() {
  const latencies = createHistogram({ figures: 3 })
  let total = 0
  let success = 0
  const outcomes = Object.create(null)
  return {
    record(sample) {
      latencies.record(Math.max(1, Math.ceil(sample.latencyMs * 1e6)))
      total++
      if (sample.ok) success++
      if (sample.outcome) outcomes[sample.outcome] = (outcomes[sample.outcome] || 0) + 1
    },
    summary(durationMs) {
      // HDR会对percentile的样本秩四舍五入；门禁必须取ceil秩，避免小样本漏掉慢请求。
      const rankValue = rank => total === 0 ? 0 : latencies.percentile(rank * 100 / total) / 1e6
      return {
        total, success, failed: total - success,
        errorRate: total === 0 ? 1 : (total - success) / total,
        throughput: durationMs <= 0 ? 0 : success / (durationMs / 1000),
        requestRate: durationMs <= 0 ? 0 : total / (durationMs / 1000),
        medianMs: total === 0 ? 0 : (rankValue(Math.floor((total + 1) / 2)) + rankValue(Math.floor(total / 2) + 1)) / 2,
        p95Ms: rankValue(Math.ceil(total * 0.95)), p99Ms: rankValue(Math.ceil(total * 0.99)),
        ...(Object.keys(outcomes).length ? { outcomes: { ...outcomes } } : {}),
      }
    },
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
  const load = summary.loadGeneration
  if (load && load.droppedConcurrency + load.droppedScheduleLag > 0) {
    failures.push(`scheduled arrivals not dispatched: concurrency=${load.droppedConcurrency}, scheduleLag=${load.droppedScheduleLag}`)
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
  const summary = measured.summary
  return {
    summary,
    evaluation: evaluateThresholds(summary, config),
  }
}

export async function runCapacityTargets(config, request) {
  const targets = (config.urls || [config.url]).map(url => ({ ...config, url }))
  await Promise.all(targets.map(target => runWindow(target, request, config.warmupSeconds * 1000, false)))
  // 所有目标先完成预热和排空，再共享同一个测量起点，不能相加互不重叠的窗口。
  const started = performance.now()
  const measured = await Promise.all(targets.map(target => runWindow(target, request,
    config.durationSeconds * 1000, true, started)))
  const nodes = targets.map((target, index) => {
    const summary = measured[index].summary
    return buildCapacityReport(target, request, summary, evaluateThresholds(summary, target))
  })
  const durationMs = nodes.length ? Math.max(...nodes.map(node => node.summary.elapsedMs)) : 0
  return { nodes, ...aggregateCapacityReports(nodes, config, durationMs) }
}

export function buildCapacityReport(config, request, summary, evaluation) {
  return {
    generatedAt: new Date().toISOString(),
    target: sanitizeTarget(config.url),
    request: {
      method: String(request.method || 'POST').toUpperCase(),
      headerNames: Object.keys(request.headers || {}).sort(),
      bodyIncluded: request.body !== undefined,
      responseAssertionPaths: Object.keys(request.expectedResponse || { success: true }),
    },
    load: {
      mode: config.arrivalRate ? 'CONSTANT_ARRIVAL' : 'CLOSED',
      arrivalRatePerTarget: config.arrivalRate || null,
      maxScheduleLagMs: config.arrivalRate ? config.maxScheduleLagMs ?? 100 : null,
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
    latencyStatistics: { method: 'HDR_HISTOGRAM', significantFigures: 3, unit: 'milliseconds' },
    result: evaluation,
  }
}

export function aggregateCapacityReports(reports, config, observationDurationMs) {
  const nodes = reports || []
  if (nodes.length && (!Number.isFinite(observationDurationMs) || observationDurationMs <= 0)) {
    throw new Error('aggregation requires a measured common observation duration')
  }
  const total = nodes.reduce((sum, report) => sum + report.summary.total, 0)
  const success = nodes.reduce((sum, report) => sum + report.summary.success, 0)
  const failed = total - success
  const summary = {
    total,
    success,
    failed,
    errorRate: total === 0 ? 1 : failed / total,
    throughput: observationDurationMs > 0 ? success / (observationDurationMs / 1000) : 0,
    requestRate: observationDurationMs > 0 ? total / (observationDurationMs / 1000) : 0,
    elapsedMs: observationDurationMs || 0,
    nodeThroughputSum: nodes.reduce((sum, report) => sum + report.summary.throughput, 0),
    medianMs: nodes.length === 0 ? 0 : Math.max(...nodes.map(report => report.summary.medianMs)),
    p95Ms: nodes.length === 0 ? 0 : Math.max(...nodes.map(report => report.summary.p95Ms)),
    p99Ms: nodes.length === 0 ? 0 : Math.max(...nodes.map(report => report.summary.p99Ms)),
  }
  const loads = nodes.map(report => report.summary.loadGeneration).filter(Boolean)
  if (loads.length) {
    summary.loadGeneration = Object.fromEntries(['scheduled', 'dispatched', 'droppedConcurrency', 'droppedScheduleLag', 'catchUpDispatched']
      .map(key => [key, loads.reduce((sum, load) => sum + (load[key] || 0), 0)]))
  }
  const outcomes = {}
  for (const report of nodes) {
    for (const [key, count] of Object.entries(report.summary.outcomes || {})) outcomes[key] = (outcomes[key] || 0) + count
  }
  if (Object.keys(outcomes).length) summary.outcomes = outcomes
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
- 聚合成功决策吞吐：${format(report.summary.throughput)} req/s
- 聚合请求完成速率（含失败）：${format(report.summary.requestRate)} req/s
- 共同观测时长：${format(report.summary.elapsedMs)}ms（包括最后目标的在途请求排空）
- 各目标速率之和：${format(report.summary.nodeThroughputSum)} req/s，仅供对照，不作为集群实际吞吐
- 聚合错误率：${format(report.summary.errorRate * 100)}%
- 最差 p95：${format(report.summary.p95Ms)}ms
- 压力模式：${report.load.mode === 'CONSTANT_ARRIVAL' ? '固定到达率（每个目标分别施压）' : '固定并发'}
- 每目标到达率：${report.load.arrivalRatePerTarget ?? '未设置'} req/s
${loadGenerationText(report.summary)}

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
- 成功口径：HTTP 200 且响应字段校验通过（${report.request.responseAssertionPaths.join(', ')}）
- 压力模式：${report.load.mode === 'CONSTANT_ARRIVAL' ? '固定到达率' : '固定并发'}
- 每目标到达率：${report.load.arrivalRatePerTarget ?? '未设置'} req/s
- 并发上限：${report.load.concurrency}
- 预热：${report.load.warmupSeconds}s
- 测量：${report.load.durationSeconds}s
- 样本：${report.summary.total}
- 成功决策吞吐：${format(report.summary.throughput)} req/s
- 请求完成速率（含失败）：${format(report.summary.requestRate)} req/s
- 错误率：${format(report.summary.errorRate * 100)}%
- 中位延迟：${format(report.summary.medianMs)}ms
- p95：${format(report.summary.p95Ms)}ms
- p99：${format(report.summary.p99Ms)}ms
- 延迟统计：3位有效数字HDR直方图；观测速率包含窗口末尾等待在途请求结束的时间
- 观测时长：${format(report.summary.elapsedMs ?? report.load.durationSeconds * 1000)}ms；排空：${format(report.summary.drainMs ?? 0)}ms
${loadGenerationText(report.summary)}
${report.summary.loadGenerator ? `- 负载进程CPU（单核百分比）：${format(report.summary.loadGenerator.cpuPercentOfOneCore)}%；事件循环利用率：${format(report.summary.loadGenerator.eventLoopUtilization * 100)}%
- 负载进程峰值RSS：${format(report.summary.loadGenerator.processPeakRssMiB)}MiB（进程启动以来）；多目标窗口重叠时不可累加CPU指标` : ''}

## 未通过项

${failures}
`
  await writeFile(join(directory, 'capacity-report.md'), markdown, 'utf8')
}

async function runWindow(config, request, durationMs, collectSamples, commonStarted) {
  const metrics = collectSamples ? createSampleSummary() : null
  const started = commonStarted ?? performance.now()
  const cpuStarted = process.cpuUsage()
  const utilizationStarted = performance.eventLoopUtilization()
  const deadline = started + durationMs
  let loadGeneration
  if (config.arrivalRate) {
    loadGeneration = await runArrivals(config, request, started, durationMs, metrics)
  } else {
    await Promise.all(Array.from({ length: config.concurrency }, () =>
      runWorker(config.url, request, deadline, metrics)))
  }
  const elapsed = performance.now() - started
  const cpu = process.cpuUsage(cpuStarted)
  const summary = metrics?.summary(elapsed)
  if (summary) {
    summary.measurementMs = durationMs
    summary.measurementStartedAtMs = performance.timeOrigin + started
    summary.elapsedMs = elapsed
    summary.drainMs = Math.max(0, elapsed - durationMs)
    summary.loadGenerator = {
      // 同一进程的多个目标窗口会重叠；这不是被测服务CPU，也不能跨目标相加。
      cpuMs: (cpu.user + cpu.system) / 1000,
      cpuPercentOfOneCore: elapsed > 0 ? (cpu.user + cpu.system) / (elapsed * 10) : 0,
      eventLoopUtilization: performance.eventLoopUtilization(utilizationStarted).utilization,
      processPeakRssMiB: process.resourceUsage().maxRSS / 1024,
    }
    if (loadGeneration) summary.loadGeneration = loadGeneration
  }
  return { summary }
}

async function runArrivals(config, request, started, durationMs, metrics) {
  const deadline = started + durationMs
  const interval = 1000 / config.arrivalRate
  const expected = durationMs / 1000 * config.arrivalRate
  const nearest = Math.round(expected)
  // 避免浮点时间相加/相减把24次错误扩大为25次；只合并机器精度内的整数边界。
  const scheduled = Math.abs(expected - nearest) <= Number.EPSILON * expected * 4 ? nearest : Math.ceil(expected)
  if (!Number.isSafeInteger(scheduled)) throw new Error('unsafe scheduled request count')
  const pending = new Set()
  const maxLag = config.maxScheduleLagMs ?? 100
  const stats = { scheduled, dispatched: 0, droppedConcurrency: 0, droppedScheduleLag: 0,
    maxInFlight: 0, maxDispatchLagMs: 0, catchUpDispatched: 0, maxDispatchesInMillisecond: 0 }
  let millisecond = -1
  let dispatchedInMillisecond = 0
  for (let index = 0; index < scheduled;) {
    const due = started + index * interval
    const now = performance.now()
    if (now < due) { await sleep(due - now); continue }
    if (now >= deadline) {
      stats.droppedScheduleLag += scheduled - index
      break
    }
    if (now - due > maxLag) {
      // 超出预算的到达直接丢弃；预算内允许有界补发，其数量和突发另行展示。
      const missed = Math.min(scheduled - index, Math.max(1, Math.ceil((now - due - maxLag) / interval)))
      stats.droppedScheduleLag += missed
      index += missed
      continue
    }
    index++
    if (pending.size >= config.concurrency) { stats.droppedConcurrency++; continue }
    stats.dispatched++
    stats.maxDispatchLagMs = Math.max(stats.maxDispatchLagMs, now - due)
    if (now - due >= interval) stats.catchUpDispatched++
    const bucket = Math.floor(now - started)
    if (bucket !== millisecond) { millisecond = bucket; dispatchedInMillisecond = 0 }
    stats.maxDispatchesInMillisecond = Math.max(stats.maxDispatchesInMillisecond, ++dispatchedInMillisecond)
    const execution = runRequest(config.url, request, metrics)
    pending.add(execution)
    stats.maxInFlight = Math.max(stats.maxInFlight, pending.size)
    execution.finally(() => pending.delete(execution))
  }
  // 即使最后一次到达早于窗口末尾，也按完整施压窗口计算速率；之后单列排空耗时。
  if (performance.now() < deadline) await sleep(deadline - performance.now())
  await Promise.all(pending)
  return stats
}

async function runWorker(url, request, deadline, metrics) {
  while (performance.now() < deadline) {
    await runRequest(url, request, metrics)
  }
}

async function runRequest(url, request, metrics) {
  const started = performance.now()
  const controller = new AbortController()
  const timeoutMs = Number(request.timeoutMs) > 0 ? Number(request.timeoutMs) : 10000
  const timeout = setTimeout(
    () => controller.abort(),
    timeoutMs
  )
  let ok = false
  let outcome = 'TRANSPORT_ERROR'
  try {
    const response = await fetch(url, {
      method: String(request.method || 'POST').toUpperCase(),
      headers: request.headers || {},
      body: request.rawBody !== undefined ? request.rawBody : request.body === undefined
        ? undefined
        : typeof request.body === 'string'
          ? request.body
          : JSON.stringify(request.body),
      signal: controller.signal,
    })
    outcome = response.status === 200 ? 'INVALID_JSON' : `HTTP_${response.status}`
    const body = parseCapacityJson(await response.text())
    // 202 WAITING_EXTERNAL 仅代表请求已接收，不能计入决策完成吞吐。
    ok = response.status === 200 && matchesCapacityResponse(body, request)
    if (response.status === 200) outcome = ok ? 'SUCCESS' : 'ASSERTION_FAILED'
  } catch {
    ok = false
    if (controller.signal.aborted) outcome = 'TIMEOUT'
  } finally {
    clearTimeout(timeout)
    const latencyMs = performance.now() - started
    // JSON解码等同步工作会推迟timer；仍必须遵守完整请求的实际截止时间。
    if (controller.signal.aborted || latencyMs >= timeoutMs) { ok = false; outcome = 'TIMEOUT' }
    if (metrics) metrics.record({ ok, outcome, latencyMs })
  }
}

function format(value) {
  return Number(value).toFixed(2)
}

function loadGenerationText(summary) {
  const load = summary.loadGeneration
  return load ? `- 计划到达/实际发起：${load.scheduled}/${load.dispatched}
- 并发已满未发起：${load.droppedConcurrency}；调度迟到未发起：${load.droppedScheduleLag}
- 预算内补发：${load.catchUpDispatched ?? 0}（迟于至少一个到达间隔）；固定1ms桶最大发起数：${load.maxDispatchesInMillisecond ?? '见各目标报告'}
- 未发起请求单独使门禁失败，不混为HTTP错误或成功样本` : ''
}
