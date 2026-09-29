#!/usr/bin/env node

import {
  loadRequestDefinition,
  parseCapacityConfig,
  sanitizeTarget,
} from './lib/capacity-config.mjs'
import {
  buildCapacityReport,
  aggregateCapacityReports,
  runCapacityScenario,
  writeMultiCapacityReports,
  writeCapacityReports,
} from './lib/capacity-runner.mjs'

try {
  const config = parseCapacityConfig(process.argv.slice(2))
  const request = await loadRequestDefinition(config.requestFile)
  const nodeReports = await Promise.all(config.urls.map(async url => {
    const nodeConfig = { ...config, url }
    const { summary, evaluation } = await runCapacityScenario(nodeConfig, request)
    return buildCapacityReport(nodeConfig, request, summary, evaluation)
  }))
  const { summary, evaluation } = aggregateCapacityReports(nodeReports, config)
  if (nodeReports.length === 1) {
    await writeCapacityReports(nodeReports[0], config.reportDir)
  } else {
    const report = { generatedAt: new Date().toISOString(), request: nodeReports[0].request,
      load: nodeReports[0].load, thresholds: nodeReports[0].thresholds,
      nodes: nodeReports, summary, result: evaluation }
    await writeMultiCapacityReports(report, config.reportDir)
  }
  console.log(JSON.stringify({
    result: evaluation.passed ? 'PASS' : 'FAIL',
    target: config.urls.length === 1
      ? sanitizeTarget(config.url)
      : config.urls.map(sanitizeTarget),
    summary,
    failures: evaluation.failures,
    reportDir: config.reportDir,
  }, null, 2))
  if (!evaluation.passed) process.exitCode = 1
} catch (error) {
  console.error(`[capacity-gate] ${error.message}`)
  process.exitCode = 2
}
