import { spawnSync } from 'node:child_process'
import { cpSync, existsSync, mkdirSync, readdirSync, rmSync, statSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const webRoot = path.join(root, 'rule-engine-web')
const outputRoot = path.join(root, 'docker', 'tianshu-decision-engine-runtime')

const command = name => process.platform === 'win32' ? `${name}.cmd` : name

function run(name, args, cwd = root) {
  console.log(`\n> ${name} ${args.join(' ')}`)
  const result = spawnSync(command(name), args, {
    cwd,
    stdio: 'inherit',
    shell: process.platform === 'win32'
  })
  if (result.error) throw result.error
  if (result.status !== 0) {
    throw new Error(`${name} 执行失败，退出码：${result.status ?? 'unknown'}`)
  }
}

function findJar(targetRoot, prefix) {
  const jars = readdirSync(targetRoot)
    .filter(name => name.startsWith(prefix) && name.endsWith('.jar') && !name.endsWith('.jar.original'))
    .map(name => path.join(targetRoot, name))
    .filter(file => statSync(file).isFile())
    .sort((left, right) => statSync(right).mtimeMs - statSync(left).mtimeMs)
  if (jars.length === 0) throw new Error(`未找到 ${prefix}*.jar`)
  return jars[0]
}

try {
  run('mvn', ['-B', 'clean', 'package', '-DskipTests'])
  run('npm', ['ci'], webRoot)
  run('npm', ['run', 'build'], webRoot)

  const serverJar = findJar(path.join(root, 'rule-engine-server', 'target'), 'rule-engine-server-')
  const runtimeJar = findJar(path.join(root, 'rule-engine-runtime', 'target'), 'rule-engine-runtime-')
  const frontendDist = path.join(webRoot, 'dist')
  if (!existsSync(path.join(frontendDist, 'index.html'))) {
    throw new Error('前端构建产物缺少 dist/index.html')
  }

  mkdirSync(outputRoot, { recursive: true })
  rmSync(path.join(outputRoot, 'dist'), { recursive: true, force: true })
  for (const file of ['server.jar', 'runtime.jar', 'schema.sql', 'data.sql']) {
    rmSync(path.join(outputRoot, file), { force: true })
  }

  cpSync(serverJar, path.join(outputRoot, 'server.jar'))
  cpSync(runtimeJar, path.join(outputRoot, 'runtime.jar'))
  cpSync(frontendDist, path.join(outputRoot, 'dist'), { recursive: true })

  console.log('\n运行时制品已生成：')
  for (const file of ['server.jar', 'runtime.jar']) {
    const fullPath = path.join(outputRoot, file)
    console.log(`- ${file}: ${(statSync(fullPath).size / 1024 / 1024).toFixed(1)} MiB`)
  }
  console.log(`- dist/: ${readdirSync(path.join(outputRoot, 'dist')).length} 个顶层条目`)
  console.log(`输出目录：${outputRoot}`)
} catch (error) {
  console.error(error.message)
  process.exitCode = 1
}
