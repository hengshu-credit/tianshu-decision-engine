import { cpSync, createReadStream, existsSync, mkdirSync, readdirSync, rmSync, statSync, writeFileSync } from 'node:fs'
import { createHash } from 'node:crypto'
import path from 'node:path'
import { spawnSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const outputRoot = path.join(root, 'output')
const runtimeRoot = path.join(root, 'docker', 'runtime')
const stamp = new Date().toISOString().replace(/[-:]/g, '').replace(/\.\d{3}Z$/, 'Z')
const archiveName = `tianshu-deployment-${stamp}`
const stageRoot = path.join(outputRoot, archiveName)
const configRoot = process.env.PACKAGE_CONFIG_DIR ? path.resolve(process.env.PACKAGE_CONFIG_DIR) : null
const withTests = process.argv.includes('--with-tests') || process.env.PACKAGE_RUN_TESTS === 'true'
const skipJavaBuild = process.env.PACKAGE_SKIP_JAVA === 'true'
const mavenImage = process.env.PACKAGE_MAVEN_IMAGE || 'maven:3.9.9-eclipse-temurin-17'
const nodeImage = process.env.PACKAGE_NODE_IMAGE || 'node:26-bookworm'

const dockerPath = value => process.platform === 'win32'
  ? path.resolve(value).replaceAll('\\', '/')
  : path.resolve(value)

const hostUser = process.platform === 'win32' || typeof process.getuid !== 'function'
  ? []
  : ['--user', `${process.getuid()}:${process.getgid()}`]

function runDocker(image, args, mounts, workdir) {
  const command = ['run', '--rm', ...hostUser]
  for (const mount of mounts) {
    command.push('--mount', `type=bind,source=${dockerPath(mount.source)},target=${mount.target}`)
  }
  if (image === mavenImage) command.push('--mount', 'type=volume,source=tianshu-maven-cache,target=/root/.m2')
  if (image === nodeImage) command.push('--mount', 'type=volume,source=tianshu-npm-cache,target=/root/.npm')
  if (workdir) command.push('--workdir', workdir)
  command.push(image, ...args)
  console.log(`\n> docker ${command.join(' ')}`)
  const result = spawnSync('docker', command, { cwd: root, stdio: 'inherit' })
  if (result.error) throw result.error
  if (result.status !== 0) throw new Error(`Docker 构建步骤失败，退出码：${result.status ?? 'unknown'}`)
}

function runDockerRetry(image, args, mounts, workdir, attempts = 2) {
  let lastError
  for (let index = 1; index <= attempts; index++) {
    try {
      runDocker(image, args, mounts, workdir)
      return
    } catch (error) {
      lastError = error
      if (index < attempts) console.warn(`Docker 步骤第 ${index} 次失败，准备重试。`)
    }
  }
  throw lastError
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

function copyTree(source, target) {
  cpSync(source, target, {
    recursive: true,
    filter(entry) {
      const relative = path.relative(source, entry)
      const parts = relative.split(path.sep)
      return !parts.includes('data')
        && !parts.includes('logs')
        && !parts.includes('node_modules')
        && !parts.includes('target')
    }
  })
}

function replaceRuntimeArtifacts(serverJar, runtimeJar, frontendDist) {
  mkdirSync(runtimeRoot, { recursive: true })
  rmSync(path.join(runtimeRoot, 'dist'), { recursive: true, force: true })
  for (const name of ['server.jar', 'runtime.jar']) rmSync(path.join(runtimeRoot, name), { force: true })
  cpSync(serverJar, path.join(runtimeRoot, 'server.jar'))
  cpSync(runtimeJar, path.join(runtimeRoot, 'runtime.jar'))
  cpSync(frontendDist, path.join(runtimeRoot, 'dist'), { recursive: true })
}

function prepareStage() {
  mkdirSync(stageRoot)
  for (const name of ['.env.server.example', '.env.client.example', 'README.md', 'LICENSE', 'NOTICE', 'THIRD_PARTY_LICENSES.md']) {
    cpSync(path.join(root, name), path.join(stageRoot, name))
  }
  copyTree(path.join(root, 'docker'), path.join(stageRoot, 'docker'))
  copyTree(path.join(root, 'licenses'), path.join(stageRoot, 'licenses'))
  copyTree(path.join(root, 'docs'), path.join(stageRoot, 'docs'))
  cpSync(path.join(root, 'docs', 'server-upload.md'), path.join(stageRoot, 'README-部署.md'))
  if (configRoot) {
    for (const name of ['.env.server', '.env.client']) {
      cpSync(path.join(configRoot, name), path.join(stageRoot, name))
    }
  }
}

async function sha256(file) {
  const hash = createHash('sha256')
  for await (const chunk of createReadStream(file)) hash.update(chunk)
  return hash.digest('hex')
}

function listFiles(directory) {
  return readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    const file = path.join(directory, entry.name)
    return entry.isDirectory() ? listFiles(file) : [file]
  }).sort()
}

function prepareSource() {
  const git = spawnSync('git', ['ls-files', '-z', '--cached', '--others', '--exclude-standard'], { cwd: root, encoding: 'utf8', maxBuffer: 16 * 1024 * 1024 })
  if (git.status !== 0) throw new Error('无法读取交付源码清单')
  const sourceRoot = path.join(stageRoot, 'source')
  for (const file of new Set(git.stdout.split('\0').filter(Boolean))) {
    if (file.split('/').some(part => part.startsWith('.env') && !part.endsWith('.example'))) continue
    const source = path.join(root, file)
    if (!existsSync(source)) continue
    const target = path.join(sourceRoot, file)
    mkdirSync(path.dirname(target), { recursive: true })
    cpSync(source, target)
  }
}

try {
  if (configRoot) {
    for (const name of ['.env.server', '.env.client']) {
      if (!statSync(path.join(configRoot, name)).isFile()) throw new Error(`配置目录缺少 ${name}`)
    }
  }
  mkdirSync(outputRoot, { recursive: true })
  const copySource = [
    'rm -rf /tmp/tianshu-source && mkdir -p /tmp/tianshu-source',
    "tar -C /workspace --exclude=.git --exclude=output --exclude=.worktrees --exclude='.codex*' --exclude=.env.server --exclude=.env.client --exclude='.env.*.local' --exclude=node_modules --exclude='*/node_modules' --exclude='*/target' --exclude='docker/rule-engine-mysql/data' --exclude='docker/rule-engine-mysql/logs' --exclude='docker/rule-engine-redis/data' --exclude='docker/rule-engine-redis/logs' --exclude='docker/runtime/server.jar' --exclude='docker/runtime/runtime.jar' --exclude='docker/runtime/dist' --exclude='*.log' -cf - . | tar -C /tmp/tianshu-source -xf -",
  ].join(' && ')
  const mavenBuild = [
    copySource,
    'cd /tmp/tianshu-source',
    `mvn -B -P!onnx-gpu clean package -DskipTests -Donnxruntime.artifactId=onnxruntime${withTests ? ' && mvn -B -P!onnx-gpu test -Donnxruntime.artifactId=onnxruntime' : ''}`,
    'rm -rf /workspace/rule-engine-server/target /workspace/rule-engine-runtime/target',
    'cp -a /tmp/tianshu-source/rule-engine-server/target /workspace/rule-engine-server/target',
    'cp -a /tmp/tianshu-source/rule-engine-runtime/target /workspace/rule-engine-runtime/target'
  ].join(' && ')
  if (skipJavaBuild) {
    console.log('跳过 Docker Maven 构建（PACKAGE_SKIP_JAVA=true），复用现有 target 产物。')
  } else {
    runDockerRetry(mavenImage, ['sh', '-lc', mavenBuild], [{ source: root, target: '/workspace' }], '/workspace')
  }
  // 前端契约测试也读取相邻的 model/server 源码；测试与构建须在同一容器保留完整目录结构。
  const webBuild = `${copySource} && cd /tmp/tianshu-source/rule-engine-web && npm ci --no-audit --no-fund && npm run build${withTests ? ' && npm run lint && npm test' : ''} && rm -rf /workspace/rule-engine-web/dist && cp -a dist /workspace/rule-engine-web/dist`
  runDocker(nodeImage, ['sh', '-lc', webBuild], [{ source: root, target: '/workspace' }], '/workspace')

  const serverJar = findJar(path.join(root, 'rule-engine-server', 'target'), 'rule-engine-server-')
  const runtimeJar = findJar(path.join(root, 'rule-engine-runtime', 'target'), 'rule-engine-runtime-')
  const frontendDist = path.join(root, 'rule-engine-web', 'dist')
  if (!existsSync(path.join(frontendDist, 'index.html'))) throw new Error('前端构建产物缺少 dist/index.html')
  replaceRuntimeArtifacts(serverJar, runtimeJar, frontendDist)
  prepareStage()
  prepareSource()

  const git = spawnSync('git', ['rev-parse', 'HEAD'], { cwd: root, encoding: 'utf8' })
  const commit = git.status === 0 && git.stdout.trim() ? git.stdout.trim() : 'working-tree'
  const status = spawnSync('git', ['status', '--porcelain'], { cwd: root, encoding: 'utf8' })
  writeFileSync(path.join(stageRoot, 'BUILD-INFO.txt'), [
    `构建时间：${new Date().toISOString()}`,
    `基础提交：${commit}`,
    `工作区修改：${status.stdout.trim() ? '有；对应源码包含本次修改，见 source/' : '无'}`,
    `构建模式：Docker${withTests ? (skipJavaBuild ? '（前端 lint 和单元测试；复用 Java 产物，未运行后端测试）' : '（后端测试、前端 lint 和单元测试）') : '（跳过测试；可用 --with-tests 开启）'}`,
    `Java 产物：${skipJavaBuild ? '复用已有 target，未重新构建' : '本次 Docker Maven CPU 构建'}`,
    `配置：${configRoot ? '附带指定目录的 .env.server/.env.client；升级时以服务器现有配置为准' : '仅含示例模板，不含本机凭据'}`,
    `构建镜像：${mavenImage} / ${nodeImage}`,
    '完整 Compose 资源预算：8 GB 内存 / 2 CPU 核',
    '公网入口：docker/README.md（NPM IP+端口、HTTPS、证书和访问控制）'
  ].join('\n'), 'utf8')
  const sums = []
  for (const file of listFiles(stageRoot)) {
    sums.push(`${await sha256(file)}  ${path.relative(stageRoot, file).split(path.sep).join('/')}`)
  }
  writeFileSync(path.join(stageRoot, 'SHA256SUMS.txt'), `${sums.join('\n')}\n`, 'utf8')
  runDocker(mavenImage, ['jar', '--create', '--no-manifest', '--file', `/output/${archiveName}.zip`, '-C', `/output/${archiveName}`, '.'], [{ source: outputRoot, target: '/output' }])
  const archive = path.join(outputRoot, `${archiveName}.zip`)
  writeFileSync(`${archive}.sha256`, `${await sha256(archive)}  ${path.basename(archive)}\n`, 'utf8')
  console.log(`\n部署包已生成：${archive}\nSHA256 校验文件：${archive}.sha256`)
} catch (error) {
  console.error(error.message)
  process.exitCode = 1
}
