import { spawn } from 'node:child_process'
import { mkdirSync, writeFileSync } from 'node:fs'
import net from 'node:net'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { loadSourceEnvironment } from './lib/source-environment.mjs'
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const logDir = path.join(root, '.codex-run-logs')
const serverEnv = loadSourceEnvironment(root, 'server')
const psQuote = value => `'${String(value).replaceAll("'", "''")}'`

function setting(name, fallback) {
  return serverEnv[name] || fallback
}

function portSetting(name, fallback) {
  const port = Number(setting(name, fallback))
  if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error(`${name} 端口无效`)
  return port
}

async function request(url) {
  try {
    const response = await fetch(url, { signal: AbortSignal.timeout(3000) })
    if (!response.ok) return null
    return response.headers.get('content-type')?.includes('json')
      ? await response.json()
      : await response.text()
  } catch {
    return null
  }
}

function portInUse(port) {
  return new Promise(resolve => {
    const socket = net.createConnection({ host: 'localhost', port })
    const finish = result => { socket.destroy(); resolve(result) }
    socket.setTimeout(2000)
    socket.once('connect', () => finish(true))
    socket.once('error', () => finish(false))
    socket.once('timeout', () => finish(false))
  })
}

async function start(service, script) {
  if (await service.ready()) {
    console.log(`${service.name} 已在运行：${service.url}`)
    return null
  }
  if (await portInUse(service.port)) {
    throw new Error(`${service.name}端口 ${service.port} 已被占用或服务尚未就绪，请先检查现有进程。`)
  }
  const stamp = new Date().toISOString().replaceAll(/[:.]/g, '-')
  const stdout = path.join(logDir, `${service.id}-${stamp}.stdout.log`)
  const stderr = path.join(logDir, `${service.id}-${stamp}.stderr.log`)
  const encoded = Buffer.from(`$ErrorActionPreference = 'Stop'\n$ProgressPreference = 'SilentlyContinue'\n${script}`, 'utf16le').toString('base64')
  // Windows PowerShell 在无控制台的 detached 进程中可能直接退出；由 Start-Process 创建隐藏的独立进程。
  const launcher = `$ErrorActionPreference = 'Stop'
$serviceProcess = Start-Process -FilePath (Get-Process -Id $PID).Path -ArgumentList @('-NoProfile','-NonInteractive','-EncodedCommand',${psQuote(encoded)}) -WorkingDirectory ${psQuote(root)} -WindowStyle Hidden -RedirectStandardOutput ${psQuote(stdout)} -RedirectStandardError ${psQuote(stderr)} -PassThru
Write-Output $serviceProcess.Id`
  const launcherProcess = spawn('powershell.exe', [
    '-NoProfile', '-NonInteractive', '-EncodedCommand', Buffer.from(launcher, 'utf16le').toString('base64')
  ], { cwd: root, windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] })
  let output = ''
  let errors = ''
  launcherProcess.stdout.on('data', chunk => { output += chunk.toString() })
  launcherProcess.stderr.on('data', chunk => { errors += chunk.toString() })
  try {
    await new Promise((resolve, reject) => {
      launcherProcess.once('error', reject)
      launcherProcess.once('exit', code => code === 0 ? resolve() : reject(new Error(errors || `${service.name}启动器退出：${code}`)))
    })
  } finally {
    // 子服务可能继承管道句柄；只等待启动器退出，不等待子服务关闭管道。
    launcherProcess.stdout.destroy()
    launcherProcess.stderr.destroy()
  }
  const pid = Number(output.trim())
  if (!Number.isInteger(pid) || pid <= 0) throw new Error(`${service.name}未返回有效进程 ID。`)
  console.log(`${service.name}启动中，日志：${stdout}`)
  return { ...service, pid, stdout, stderr }
}

async function main() {
  if (process.platform !== 'win32') throw new Error('此命令用于 Windows。')
  mkdirSync(logDir, { recursive: true })
  const frontendPort = portSetting('VITE_PORT', 9090)
  const backendPort = portSetting('SERVER_PORT', 8080)
  const frontendUrl = `http://localhost:${frontendPort}`
  const backendUrl = `http://localhost:${backendPort}`
  const frontend = {
    id: 'frontend', name: '前端', port: frontendPort, url: frontendUrl,
    ready: async () => {
      const html = await request(frontendUrl)
      return typeof html === 'string' && html.includes('<title>天枢决策引擎</title>') && html.includes('<div id="app">')
    }
  }
  const backend = {
    id: 'backend', name: '后端', port: backendPort, url: backendUrl,
    ready: async () => {
      const health = await request(`${backendUrl}/actuator/health/readiness`)
      if (health?.status !== 'UP') return false
      const config = await request(`${backendUrl}/api/auth/console/config`)
      return config?.code === '000000' && typeof config.data?.loginEnabled === 'boolean'
    }
  }
  const launched = []
  const front = await start(frontend, `
Set-Location -LiteralPath ${psQuote(path.join(root, 'rule-engine-web'))}
$env:SERVER_PORT = ${psQuote(String(backendPort))}
$env:VITE_PORT = ${psQuote(String(frontendPort))}
if (-not (Test-Path -LiteralPath 'node_modules')) {
  & npm.cmd ci
  if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}
& npm.cmd run dev
exit $LASTEXITCODE`)
  if (front) launched.push(front)

  // server 通过统一 dotenv 加载器读取配置，不根据本机网卡猜测数据库地址。
  const back = await start(backend, `
Set-Location -LiteralPath ${psQuote(root)}
& mvn.cmd -B -pl rule-engine-core -am install -DskipTests
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Set-Location -LiteralPath ${psQuote(path.join(root, 'rule-engine-server'))}
& mvn.cmd -B spring-boot:run -DskipTests ${psQuote(`-Dspring-boot.run.jvmArguments=-Djdk.net.unixdomain.tmpdir="${logDir.replaceAll('\\', '/')}"`)}
exit $LASTEXITCODE`)
  if (back) launched.push(back)
  if (launched.length) {
    writeFileSync(path.join(logDir, 'start-dev-processes.json'), JSON.stringify(launched, null, 2))
  }

  const deadline = Date.now() + 1800000
  let nextProgress = Date.now() + 30000
  const pending = [...launched]
  while (pending.length && Date.now() < deadline) {
    for (const service of [...pending]) {
      if (await service.ready()) {
        console.log(`${service.name}就绪：${service.url}`)
        pending.splice(pending.indexOf(service), 1)
      } else {
        try { process.kill(service.pid, 0) } catch {
          throw new Error(`${service.name}启动失败，请查看 ${service.stdout} 和 ${service.stderr}`)
        }
      }
    }
    if (Date.now() >= nextProgress && pending.length) {
      console.log(`仍在等待${pending.map(service => service.name).join('、')}就绪（包含编译和模型预热）…`)
      nextProgress = Date.now() + 30000
    }
    if (pending.length) await new Promise(resolve => setTimeout(resolve, 2000))
  }
  if (pending.length) throw new Error(`等待服务就绪超过 30 分钟，请检查 ${logDir} 中的日志。`)
  const proxyConfig = await request(`${frontendUrl}/api/auth/console/config`)
  if (proxyConfig?.code !== '000000' || typeof proxyConfig.data?.loginEnabled !== 'boolean') {
    throw new Error('前后端已启动，但前端 API 代理检查失败，请检查 VITE_DEV_PROXY。')
  }
  console.log(`\n前后端已就绪并在后台运行。\n前端：${frontendUrl}\n后端：${backendUrl}`)
}

main().catch(error => {
  console.error(error.message)
  process.exitCode = 1
})

