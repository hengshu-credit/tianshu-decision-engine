import { cpSync, existsSync, mkdirSync, readdirSync, rmSync, statSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import { spawnSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const outputRoot = path.join(root, 'output')
const runtimeRoot = path.join(root, 'docker', 'runtime')
const stageRoot = path.join(outputRoot, 'tianshu-deployment')
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
  rmSync(stageRoot, { recursive: true, force: true })
  mkdirSync(stageRoot, { recursive: true })
  for (const name of ['.env.example', 'README.md', 'LICENSE', 'NOTICE', 'THIRD_PARTY_LICENSES.md']) {
    cpSync(path.join(root, name), path.join(stageRoot, name))
  }
  copyTree(path.join(root, 'docker'), path.join(stageRoot, 'docker'))
  mkdirSync(path.join(stageRoot, 'docs'), { recursive: true })
  cpSync(path.join(root, 'docs', 'deployment.md'), path.join(stageRoot, 'docs', 'deployment.md'))
  writeFileSync(path.join(stageRoot, 'README-部署.md'), [
    '# 天枢决策引擎 Ubuntu 部署',
    '',
    '1. 复制并编辑配置：cp .env.example .env。替换所有密码、BCrypt 控制台密码、项目令牌和 RULE_AUTH_MASTER_KEY。',
    '2. 在独立的 Nginx Proxy Manager 中使用服务器 IP+端口配置 console.example.com -> :9090、engine-api.example.com -> :8080、api.example.com -> :7070，并申请 HTTPS 证书。',
    '3. server/web/HTTP/SDK 共用 APP_BIND_ADDRESS；远程调试 MySQL/Redis 时，将 MYSQL_BIND_ADDRESS/REDIS_BIND_ADDRESS 保持为 0.0.0.0。',
    '4. 启动服务：docker compose --project-name tianshu-full --env-file .env -f docker/docker-compose.full.yml up -d --scale server=2',
    '5. 控制台访问 https://console.example.com/；项目执行入口为 https://api.example.com/api/rule/execute/{ruleCode}；Kafka 外部调试端口为 KAFKA_ADVERTISED_HOST:KAFKA_HOST_PORT。',
    '',
    '完整安全说明和 Nginx Proxy Manager 配置见 docker/README.md；完整部署说明见 docs/deployment.md。'
  ].join('\n'), 'utf8')
}

try {
  const mavenBuild = [
    'rm -rf /tmp/tianshu-source && mkdir -p /tmp/tianshu-source',
    "tar -C /workspace --exclude=.git --exclude=output --exclude=.worktrees --exclude=.codex-validation --exclude=.codex-run-logs --exclude=node_modules --exclude='*/node_modules' --exclude='*/target' --exclude='docker/rule-engine-mysql/data' --exclude='docker/rule-engine-mysql/logs' --exclude='docker/rule-engine-redis/data' --exclude='docker/rule-engine-redis/logs' --exclude='docker/runtime/server.jar' --exclude='docker/runtime/runtime.jar' --exclude='docker/runtime/dist' --exclude='*.log' -cf - . | tar -C /tmp/tianshu-source -xf -",
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
  const webBuild = 'rm -rf /tmp/tianshu-web && mkdir -p /tmp/tianshu-web && tar -C /workspace/rule-engine-web --exclude=node_modules --exclude=dist -cf - . | tar -C /tmp/tianshu-web -xf - && cd /tmp/tianshu-web && npm ci --no-audit --no-fund && npm run build && rm -rf /workspace/rule-engine-web/dist && cp -a dist /workspace/rule-engine-web/dist'
  runDocker(nodeImage, ['sh', '-lc', webBuild], [{ source: root, target: '/workspace' }], '/workspace')
  if (withTests) {
    const webTest = 'cd /tmp/tianshu-web && npm test -- --run'
    runDocker(nodeImage, ['sh', '-lc', webTest], [{ source: root, target: '/workspace' }], '/workspace')
  }

  const serverJar = findJar(path.join(root, 'rule-engine-server', 'target'), 'rule-engine-server-')
  const runtimeJar = findJar(path.join(root, 'rule-engine-runtime', 'target'), 'rule-engine-runtime-')
  const frontendDist = path.join(root, 'rule-engine-web', 'dist')
  if (!existsSync(path.join(frontendDist, 'index.html'))) throw new Error('前端构建产物缺少 dist/index.html')
  replaceRuntimeArtifacts(serverJar, runtimeJar, frontendDist)
  prepareStage()

  const stamp = new Date().toISOString().replace(/[-:]/g, '').replace(/\.\d{3}Z$/, 'Z')
  const archiveName = `tianshu-deployment-${stamp}`
  const git = spawnSync('git', ['rev-parse', 'HEAD'], { cwd: root, encoding: 'utf8' })
  const commit = git.status === 0 && git.stdout.trim() ? git.stdout.trim() : 'working-tree'
  writeFileSync(path.join(stageRoot, 'BUILD-INFO.txt'), [
    `构建时间：${new Date().toISOString()}`,
    `基础提交：${commit}`,
    `构建模式：Docker${withTests ? '（包含测试）' : '（跳过测试；可用 --with-tests 开启）'}`,
    '完整 Compose 资源预算：8 GB 内存 / 2 CPU 核',
    '公网入口：docker/README.md（NPM IP+端口、HTTPS、证书和访问控制）'
  ].join('\n'), 'utf8')
  mkdirSync(outputRoot, { recursive: true })

  runDocker(mavenImage, ['sh', '-ec', `rm -f /output/tianshu-deployment-*.zip && jar --create --file /output/${archiveName}.zip -C /output/tianshu-deployment .`], [{ source: outputRoot, target: '/output' }])
  console.log(`\n部署包已生成：${path.join(outputRoot, `${archiveName}.zip`)}`)
} catch (error) {
  console.error(error.message)
  process.exitCode = 1
}
