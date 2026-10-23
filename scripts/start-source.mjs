import { spawn } from 'node:child_process'
import { existsSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { loadSourceEnvironment } from './lib/source-environment.mjs'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const isWindows = process.platform === 'win32'
const command = name => isWindows ? `${name}.cmd` : name

function launch(executable, args, options) {
  return isWindows
    ? spawn('cmd.exe', ['/d', '/c', command(executable), ...args], { ...options, windowsHide: true })
    : spawn(executable, args, options)
}

const serverEnv = loadSourceEnvironment(root, 'server')
const clientEnv = loadSourceEnvironment(root, 'client')
const children = []
let stopping = false

function start(name, executable, args, cwd, childEnv = serverEnv) {
  const child = launch(executable, args, {
    cwd,
    env: { ...childEnv, FORCE_COLOR: '1' },
    stdio: 'inherit'
  })
  children.push({ name, child })
  child.once('error', error => { console.error(`${name} 启动失败：${error.message}`); stop(1) })
  child.once('exit', (code, signal) => {
    if (stopping) return
    if (code !== 0) {
      console.error(`${name} 已退出：code=${code ?? 'null'} signal=${signal ?? 'null'}`)
      stop(code || 1)
    }
  })
  return child
}

function stop(code = 0) {
  if (stopping) return
  stopping = true
  for (const { child } of children.reverse()) {
    if (child.exitCode === null && child.pid) {
      if (isWindows) spawn('taskkill.exe', ['/PID', String(child.pid), '/T', '/F'], { windowsHide: true, stdio: 'ignore' })
      else child.kill('SIGTERM')
    }
  }
  setTimeout(() => process.exit(code), 1000).unref()
}

process.once('SIGINT', () => stop(0))
process.once('SIGTERM', () => stop(0))

try {
  console.log('正在编译后端和运行时模块…')
  const build = launch('mvn', ['-B', 'install', '-DskipTests'], {
    cwd: root,
    env: { ...process.env, FORCE_COLOR: '1' },
    stdio: 'inherit'
  })
  const buildCode = await new Promise((resolve, reject) => { build.once('exit', resolve); build.once('error', reject) })
  if (buildCode !== 0) throw new Error(`Maven 编译失败：${buildCode}`)

  start('server', 'mvn', ['spring-boot:run', '-DskipTests'], path.join(root, 'rule-engine-server'))
  start('http', 'mvn', ['spring-boot:run', '-DskipTests'], path.join(root, 'rule-engine-runtime'), {
    ...clientEnv, SPRING_PROFILES_ACTIVE: 'http', SERVER_PORT: clientEnv.RUNTIME_HTTP_PORT || '7070'
  })
  start('sdk', 'mvn', ['spring-boot:run', '-DskipTests'], path.join(root, 'rule-engine-runtime'), {
    ...clientEnv, SPRING_PROFILES_ACTIVE: 'sdk', SERVER_PORT: clientEnv.RUNTIME_SDK_PORT || '7071'
  })

  const web = path.join(root, 'rule-engine-web')
  if (!existsSync(path.join(web, 'node_modules'))) {
    console.log('正在安装前端依赖…')
    const install = launch('npm', ['ci'], { cwd: web, env: serverEnv, stdio: 'inherit' })
    const installCode = await new Promise((resolve, reject) => { install.once('exit', resolve); install.once('error', reject) })
    if (installCode !== 0) throw new Error(`前端依赖安装失败：${installCode}`)
  }
  start('web', 'npm', ['run', 'dev'], web, serverEnv)
  console.log(`源码服务启动中：server=${serverEnv.SERVER_PORT || 8080}, web=${serverEnv.VITE_PORT || 9090}, http=${clientEnv.RUNTIME_HTTP_PORT || 7070}, sdk=${clientEnv.RUNTIME_SDK_PORT || 7071}。按 Ctrl+C 停止全部服务。`)
} catch (error) {
  console.error(error.message)
  stop(1)
}
