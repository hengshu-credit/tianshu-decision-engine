import { spawn } from 'node:child_process'
import { existsSync, readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const isWindows = process.platform === 'win32'
const command = name => isWindows ? `${name}.cmd` : name

function loadEnv() {
  const file = path.join(root, '.env')
  if (!existsSync(file)) throw new Error('缺少根目录 .env，请先复制 .env.example 并填写真实配置。')
  const values = {}
  for (const line of readFileSync(file, 'utf8').split(/\r?\n/)) {
    const match = line.match(/^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)\s*$/)
    if (!match || match[1].startsWith('#')) continue
    values[match[1]] = match[2].replace(/^(['"])(.*)\1$/, '$2')
  }
  return { ...values, ...process.env }
}

const env = loadEnv()
const children = []
let stopping = false

function start(name, executable, args, cwd, childEnv = env) {
  const child = spawn(command(executable), args, {
    cwd,
    env: { ...childEnv, FORCE_COLOR: '1' },
    stdio: 'inherit'
  })
  children.push({ name, child })
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
    if (child.exitCode === null) child.kill('SIGTERM')
  }
  setTimeout(() => process.exit(code), 1000).unref()
}

process.once('SIGINT', () => stop(0))
process.once('SIGTERM', () => stop(0))

try {
  console.log('正在编译后端和运行时模块…')
  const build = spawn(command('mvn'), ['-B', 'install', '-DskipTests'], {
    cwd: root,
    env: { ...env, FORCE_COLOR: '1' },
    stdio: 'inherit'
  })
  const buildCode = await new Promise(resolve => build.once('exit', resolve))
  if (buildCode !== 0) throw new Error(`Maven 编译失败：${buildCode}`)

  const runtimeServer = env.RUNTIME_RULE_SERVER_URL || 'http://127.0.0.1:8080'
  start('server', 'mvn', ['spring-boot:run', '-DskipTests'], path.join(root, 'rule-engine-server'))
  start('http', 'mvn', ['spring-boot:run', '-DskipTests'], path.join(root, 'rule-engine-runtime'), {
    ...env, SPRING_PROFILES_ACTIVE: 'http', SERVER_PORT: '7070', RULE_SERVER_URL: runtimeServer
  })
  start('sdk', 'mvn', ['spring-boot:run', '-DskipTests'], path.join(root, 'rule-engine-runtime'), {
    ...env, SPRING_PROFILES_ACTIVE: 'sdk', SERVER_PORT: '7071', RULE_SERVER_URL: runtimeServer
  })

  const web = path.join(root, 'rule-engine-web')
  if (!existsSync(path.join(web, 'node_modules'))) {
    console.log('正在安装前端依赖…')
    const install = spawn(command('npm'), ['ci'], { cwd: web, env, stdio: 'inherit' })
    const installCode = await new Promise(resolve => install.once('exit', resolve))
    if (installCode !== 0) throw new Error(`前端依赖安装失败：${installCode}`)
  }
  start('web', 'npm', ['run', 'dev'], web, env)
  console.log('源码服务已启动：server=8080, web=9090, http=7070, sdk=7071。按 Ctrl+C 停止全部服务。')
} catch (error) {
  console.error(error.message)
  stop(1)
}
