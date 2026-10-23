import test from 'node:test'
import assert from 'node:assert/strict'
import { createServer } from 'node:http'
import { mkdtemp, writeFile, readFile, rm } from 'node:fs/promises'
import { spawn } from 'node:child_process'
import { tmpdir } from 'node:os'
import { join, resolve, sep } from 'node:path'
import { fileURLToPath } from 'node:url'

test('CLI exports arrival evidence and returns failure on dropped load despite successful HTTP responses', async t => {
  const temporaryRoot = resolve(tmpdir())
  const directory = await mkdtemp(join(temporaryRoot, 'tianshu-arrival-cli-'))
  t.after(async () => {
    assert.ok(resolve(directory).startsWith(temporaryRoot + sep))
    await rm(directory, { recursive: true, force: true })
  })
  const server = createServer((_request, response) => {
    setTimeout(() => response.end('{"code":"000000","success":true}'), 80)
  })
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
  t.after(() => new Promise(resolve => server.close(resolve)))
  const request = join(directory, 'request.json')
  await writeFile(request, JSON.stringify({ method: 'POST', timeoutMs: 1000 }))
  const args = [fileURLToPath(new URL('../run-capacity-gate.mjs', import.meta.url)),
    '--url', `http://127.0.0.1:${server.address().port}/execute`, '--request-file', request,
    '--concurrency', '1', '--arrival-rate', '100', '--max-schedule-lag-ms', '100',
    '--warmup-seconds', '0.01', '--duration-seconds', '0.3',
    '--max-error-rate', '1', '--max-p95-ms', '1000', '--min-throughput', '1', '--report-dir', directory]
  const child = spawn(process.execPath, args, { windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] })
  let stdout = ''
  let stderr = ''
  child.stdout.on('data', value => { stdout += value })
  child.stderr.on('data', value => { stderr += value })
  const timeout = setTimeout(() => child.kill(), 10000)
  const code = await new Promise((resolve, reject) => {
    child.once('error', reject)
    child.once('exit', resolve)
  }).finally(() => clearTimeout(timeout))
  assert.equal(code, 1, stderr || stdout)
  const report = JSON.parse(await readFile(join(directory, 'capacity-report.json'), 'utf8'))
  assert.equal(report.load.mode, 'CONSTANT_ARRIVAL')
  assert.equal(report.load.arrivalRatePerTarget, 100)
  assert.equal(report.summary.loadGeneration.scheduled, 30)
  assert.ok(report.summary.loadGeneration.droppedConcurrency > 0)
  assert.equal(report.summary.failed, 0)
  assert.equal(report.summary.outcomes.SUCCESS, report.summary.success)
  assert.equal(report.result.passed, false)
  const markdown = await readFile(join(directory, 'capacity-report.md'), 'utf8')
  assert.match(markdown, /固定到达率/)
  assert.match(markdown, /计划到达\/实际发起/)
  assert.match(markdown, /并发已满未发起/)
})
