import { readFile } from 'node:fs/promises'
import { capacityJsonNode, capacityJsonText, parseCapacityJson } from './capacity-json.mjs'

const SECRET_KEY = /(authorization|cookie|token|password|secret|api[-_]?key|(^|[-_])key$)/i
const ARGUMENTS = new Set(['--url', '--urls', '--request-file', '--concurrency', '--warmup-seconds',
  '--duration-seconds', '--max-error-rate', '--max-p95-ms', '--min-throughput', '--report-dir',
  '--arrival-rate', '--max-schedule-lag-ms'])

export function parseCapacityConfig(args) {
  const values = parseArgs(args)
  const urlValues = values.get('--urls')
    ? values.get('--urls').split(',').map(value => value.trim()).filter(Boolean)
    : [required(values, '--url')]
  if (urlValues.length === 0) throw new Error('--url or --urls is required')
  for (const url of urlValues) {
    const parsedUrl = new URL(url)
    if (!['http:', 'https:'].includes(parsedUrl.protocol)) {
      throw new Error('--url must use http or https')
    }
  }
  const config = {
    url: urlValues[0],
    urls: urlValues,
    requestFile: required(values, '--request-file'),
    concurrency: positiveInteger(values, '--concurrency'),
    warmupSeconds: positiveNumber(values, '--warmup-seconds'),
    durationSeconds: positiveNumber(values, '--duration-seconds'),
    maxErrorRate: nonNegativeNumber(values, '--max-error-rate'),
    maxP95Ms: positiveNumber(values, '--max-p95-ms'),
    minThroughput: positiveNumber(values, '--min-throughput'),
    reportDir: values.get('--report-dir') || 'target/quality-gates',
    arrivalRate: values.has('--arrival-rate') ? positiveNumber(values, '--arrival-rate') : null,
    maxScheduleLagMs: values.has('--max-schedule-lag-ms') ? positiveNumber(values, '--max-schedule-lag-ms') : 100,
  }
  if (config.maxErrorRate > 1) {
    throw new Error('--max-error-rate must be between 0 and 1')
  }
  if (values.has('--max-schedule-lag-ms') && config.arrivalRate === null) {
    throw new Error('--max-schedule-lag-ms requires --arrival-rate')
  }
  if (config.arrivalRate !== null && !Number.isSafeInteger(Math.ceil(config.arrivalRate
      * Math.max(config.warmupSeconds, config.durationSeconds)))) {
    throw new Error('arrival-rate and duration produce an unsafe scheduled request count')
  }
  return config
}

export function redactObject(value, key = '') {
  if (SECRET_KEY.test(key)) return '[REDACTED]'
  if (Array.isArray(value)) {
    return value.map(item => redactObject(item))
  }
  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.entries(value).map(([entryKey, entryValue]) => [
      entryKey,
      redactObject(entryValue, entryKey),
    ]))
  }
  return value
}

export function sanitizeTarget(value) {
  const url = new URL(value)
  for (const key of url.searchParams.keys()) {
    url.searchParams.set(key, '[REDACTED]')
  }
  return url.toString()
}

export async function loadRequestDefinition(path) {
  let definition
  let document
  try {
    const text = await readFile(path, 'utf8')
    document = parseCapacityJson(text)
    definition = JSON.parse(text)
  } catch (error) {
    throw new Error(`failed to read request definition: ${error.message}`)
  }
  if (!definition || typeof definition !== 'object' || Array.isArray(definition)) {
    throw new Error('request definition must be a JSON object')
  }
  const method = String(definition.method || 'POST').toUpperCase()
  if (!['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD'].includes(method)) {
    throw new Error(`request method is not supported: ${method}`)
  }
  const headers = definition.headers || {}
  if (typeof headers !== 'object' || Array.isArray(headers)) {
    throw new Error('request headers must be an object')
  }
  const timeoutMs = definition.timeoutMs === undefined
    ? 10000
    : Number(definition.timeoutMs)
  if (!Number.isFinite(timeoutMs) || timeoutMs <= 0) {
    throw new Error('request timeoutMs must be greater than zero')
  }
  const expectedResponse = definition.expectedResponse === undefined
    ? { success: true }
    : definition.expectedResponse
  if (!expectedResponse || typeof expectedResponse !== 'object'
      || Array.isArray(expectedResponse) || Object.keys(expectedResponse).length === 0
      || Object.entries(expectedResponse).some(([path, value]) =>
        path.split('.').some(part => !part.trim())
        || (value !== null && !['string', 'boolean', 'number'].includes(typeof value)))) {
    throw new Error('request expectedResponse must contain dotted field paths and scalar expected values')
  }
  const expectedResponseNumberText = Object.create(null)
  for (const [key, value] of Object.entries(expectedResponse)) {
    if (typeof value !== 'number') continue
    const node = capacityJsonNode(document, ['expectedResponse', key])
    if (node?.type === 'number') expectedResponseNumberText[key] = capacityJsonText(document, node)
  }
  const bodyNode = capacityJsonNode(document, ['body'])
  return {
    method,
    headers,
    ...(definition.body === undefined ? {} : { body: definition.body }),
    ...(bodyNode && bodyNode.type !== 'string' ? { rawBody: capacityJsonText(document, bodyNode) } : {}),
    timeoutMs,
    expectedResponse,
    ...(Object.keys(expectedResponseNumberText).length ? { expectedResponseNumberText } : {}),
  }
}

function parseArgs(args) {
  const values = new Map()
  for (let index = 0; index < args.length; index += 2) {
    const key = args[index]
    const value = args[index + 1]
    if (!key?.startsWith('--') || value === undefined || value.startsWith('--')) {
      throw new Error(`invalid argument near ${key || '<empty>'}`)
    }
    if (!ARGUMENTS.has(key)) throw new Error(`unknown argument: ${key}`)
    values.set(key, value)
  }
  return values
}

function required(values, key) {
  const value = values.get(key)
  if (!value) throw new Error(`${key} is required`)
  return value
}

function positiveInteger(values, key) {
  const value = Number(required(values, key))
  if (!Number.isInteger(value) || value <= 0) {
    throw new Error(`${key.replace(/^--/, '')} must be a positive integer`)
  }
  return value
}

function positiveNumber(values, key) {
  const value = Number(required(values, key))
  if (!Number.isFinite(value) || value <= 0) {
    throw new Error(`${key.replace(/^--/, '')} must be greater than zero`)
  }
  return value
}

function nonNegativeNumber(values, key) {
  const value = Number(required(values, key))
  if (!Number.isFinite(value) || value < 0) {
    throw new Error(`${key.replace(/^--/, '')} must not be negative`)
  }
  return value
}
