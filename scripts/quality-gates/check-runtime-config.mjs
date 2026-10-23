import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { loadSourceEnvironment } from '../lib/source-environment.mjs'

/** 与 runtime 的 http/sdk profile 对齐；报告只显示配置项名称，不输出凭据值。 */
export function checkRuntimeConfig(env, mode) {
  if (!['http', 'sdk'].includes(mode)) throw new Error('--mode 必须是 http 或 sdk')
  const checks = []
  const configured = value => typeof value === 'string' && value.trim() !== '' && !value.startsWith('replace-with-')
  const add = (key, passed, message) => checks.push({ key, passed: Boolean(passed), message })
  const requireValue = key => { add(key, configured(env[key]), '必须填写真实值'); return env[key] }
  const upstream = env.RULE_SERVER_URL ?? env.RUNTIME_RULE_SERVER_URL
  let upstreamValid = false
  try {
    const parsed = new URL(upstream)
    upstreamValid = ['http:', 'https:'].includes(parsed.protocol) && !parsed.username && !parsed.password && !parsed.search && !parsed.hash
  } catch { /* 无效地址通过检查项呈现，不回显原值。 */ }
  add('RUNTIME_RULE_SERVER_URL', upstreamValid, '填写服务端 HTTP(S) 地址；不包含凭据、查询参数或片段')
  const auth = String(env.RULE_AUTH_TYPE || 'LEGACY_TOKEN').toUpperCase()
  const authFields = { LEGACY_TOKEN: ['PROJECT_ACCESS_TOKEN'], BASIC: ['RULE_USERNAME', 'RULE_PASSWORD'],
    API_KEY: ['RULE_API_KEY'], HMAC_SHA256: ['RULE_ACCESS_KEY', 'RULE_HMAC_SECRET'], GLOBAL_KEY: ['RULE_GLOBAL_PRIVATE_KEY'] }
  add('RULE_AUTH_TYPE', Boolean(authFields[auth]), '支持 LEGACY_TOKEN、BASIC、API_KEY、HMAC_SHA256、GLOBAL_KEY')
  for (const key of authFields[auth] || []) {
    if (key === 'PROJECT_ACCESS_TOKEN') add('RUNTIME_PROJECT_ACCESS_TOKEN', configured(env.PROJECT_ACCESS_TOKEN ?? env.RUNTIME_PROJECT_ACCESS_TOKEN), '填写控制台项目调用鉴权中的兼容令牌')
    else requireValue(key)
  }
  if (auth === 'API_KEY') {
    add('RULE_API_KEY_PLACEMENT', ['HEADER', 'QUERY'].includes(String(env.RULE_API_KEY_PLACEMENT || 'HEADER').toUpperCase()), '传递位置必须与控制台一致')
    add('RULE_API_KEY_NAME', configured(env.RULE_API_KEY_NAME ?? 'X-Rule-Api-Key'), '参数名必须与控制台一致')
  }
  const prefix = mode.toUpperCase()
  const token = env.RUNTIME_ACCESS_TOKEN ?? env[`${prefix}_ACCESS_TOKEN`]
  add(`${prefix}_ACCESS_TOKEN`, configured(token) && token.length >= 32, '运行时入口令牌至少 32 位，与上游项目凭据分别配置')
  if (auth !== 'GLOBAL_KEY') {
    const codes = env.RULE_ALLOWED_CODES ?? env[`${prefix}_RULE_ALLOWED_CODES`]
    add(`${prefix}_RULE_ALLOWED_CODES`, configured(codes) && codes.split(',').some(code => code.trim()), '填写已发布规则编码，大小写保持一致')
  }
  if (mode === 'sdk') {
    if (auth !== 'GLOBAL_KEY') {
      const id = env.RULE_PROJECT_ID ?? env.RUNTIME_RULE_PROJECT_ID
      add('RUNTIME_RULE_PROJECT_ID', /^\d+$/.test(String(id)) && BigInt(id) > 0n && BigInt(id) <= 9223372036854775807n, '使用真实项目 ID')
      add('RUNTIME_RULE_PROJECT_CODE', configured(env.RULE_PROJECT_CODE ?? env.RUNTIME_RULE_PROJECT_CODE), '使用真实项目编码，不能用应用名称代替 Redis 频道')
    }
    requireValue('REDIS_HOST'); requireValue('REDIS_PASSWORD')
    add('REDIS_PORT', /^\d+$/.test(String(env.REDIS_PORT)) && Number(env.REDIS_PORT) > 0 && Number(env.REDIS_PORT) <= 65535, '使用与 server 相同 Redis 实例的可达端口')
    add('REDIS_DATABASE', /^\d+$/.test(String(env.REDIS_DATABASE)), '使用与 server 相同的 Redis database')
  }
  return { mode, passed: checks.every(check => check.passed), checks,
    next: mode === 'http' ? '启动后检查 readiness，再使用已知测试用例验证上游鉴权和业务结果。'
      : '启动后检查 readiness、项目订阅及发布更新；仅在本地执行纯计算规则，服务端数据源能力请使用 HTTP 模式。' }
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    const args = process.argv.slice(2), options = {}
    for (let i = 0; i < args.length; i += 2) {
      if (!['--mode', '--root'].includes(args[i]) || !args[i + 1] || options[args[i]]) throw new Error('用法：--mode http|sdk [--root 仓库或配置目录]')
      options[args[i]] = args[i + 1]
    }
    const report = checkRuntimeConfig(loadSourceEnvironment(path.resolve(options['--root'] || '.'), 'client'), options['--mode'])
    console.log(JSON.stringify(report, null, 2))
    if (!report.passed) process.exitCode = 1
  } catch (error) { console.error(error.message); process.exitCode = 2 }
}
