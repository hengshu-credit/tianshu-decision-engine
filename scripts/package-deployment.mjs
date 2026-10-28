import { cpSync, createReadStream, existsSync, mkdirSync, readFileSync, readdirSync, statSync, writeFileSync } from 'node:fs'
import { createHash } from 'node:crypto'
import path from 'node:path'
import { spawnSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const sourceManifest = '.release-source-files.json'
const normalizedPath = value => process.platform === 'win32' ? path.resolve(value).toLowerCase() : path.resolve(value)

export function composeInvocation(composeFile, envFile, args, platform = process.platform) {
  return { command: platform === 'win32' ? 'docker-compose.exe' : 'docker-compose',
    args: ['--project-name', 'tianshu-build', '--env-file', envFile, '-f', composeFile, ...args] }
}

export function releaseSourceFiles(directory) {
  // --show-prefix 避免 Windows TEMP 短路径与 Git 返回的长路径不一致。
  const gitRoot = spawnSync('git', ['rev-parse', '--show-prefix'], { cwd: directory, encoding: 'utf8' })
  let files
  if (gitRoot.status === 0 && gitRoot.stdout.trim() === '') {
    const git = spawnSync('git', ['ls-files', '-z', '--cached', '--others', '--exclude-standard'], { cwd: directory, encoding: 'utf8', maxBuffer: 16 * 1024 * 1024 })
    if (git.status !== 0) throw new Error('无法读取交付源码清单')
    files = git.stdout.split('\0').filter(Boolean)
  } else {
    if (!existsSync(path.join(directory, sourceManifest))) throw new Error('请在 Git 源码检出目录或发布包的 source/ 目录运行')
    files = JSON.parse(readFileSync(path.join(directory, sourceManifest), 'utf8'))
  }
  return [...new Set(files)].filter(file => {
    if (typeof file !== 'string' || path.posix.isAbsolute(file) || path.win32.isAbsolute(file)
      || file.includes('\\') || file.split('/').includes('..')) throw new Error('源码清单包含非法相对路径')
    if (file.split('/').some(part => part.startsWith('.env') && !part.endsWith('.example'))) return false
    return existsSync(path.join(directory, file)) && statSync(path.join(directory, file)).isFile()
  }).sort()
}

export function testDescription(withTests, skipJavaBuild) {
  if (!withTests) return '跳过测试；可用 --with-tests 开启'
  return skipJavaBuild ? '前端 lint 和单元测试；复用 Java 产物，未运行后端测试' : '后端测试、前端 lint 和单元测试'
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

async function main() {
  if (process.argv.includes('--help')) {
    console.log('用法：node scripts/package-deployment.mjs [--with-tests]\nWindows: ./scripts/package-deployment.ps1\nmacOS/Linux: sh scripts/package-deployment.sh\n需要 Node.js 20.19+、Docker Linux 容器和 docker-compose v2+（含 v5）。\n输出 output/tianshu-deployment-*.zip 及 .zip.sha256；不覆盖本地 target、dist 或 docker/runtime。')
    return
  }
  for (const arg of process.argv.slice(2)) if (arg !== '--with-tests') throw new Error(`未知参数：${arg}`)
  const withTests = process.argv.includes('--with-tests') || process.env.PACKAGE_RUN_TESTS === 'true'
  const skipJavaBuild = process.env.PACKAGE_SKIP_JAVA === 'true'
  const outputRoot = path.join(root, 'output')
  const stamp = new Date().toISOString().replace(/[-:.]/g, '')
  const archiveName = `tianshu-deployment-${stamp}`
  const stageRoot = path.join(outputRoot, archiveName)
  const sourceRoot = path.join(stageRoot, 'source')
  const runtimeRoot = path.join(stageRoot, 'docker', 'runtime')
  const configRoot = process.env.PACKAGE_CONFIG_DIR ? path.resolve(process.env.PACKAGE_CONFIG_DIR) : null
  const mavenImage = process.env.PACKAGE_MAVEN_IMAGE || 'maven:3.9.9-eclipse-temurin-17'
  const nodeImage = process.env.PACKAGE_NODE_IMAGE || 'node:26-bookworm'
  const buildPlatform = process.env.PACKAGE_PLATFORM || 'linux/amd64'
  if (!['linux/amd64', 'linux/arm64'].includes(buildPlatform)) throw new Error('PACKAGE_PLATFORM 仅支持 linux/amd64 或 linux/arm64')
  const invocation = composeInvocation(path.join(root, 'docker', 'docker-compose.build.yml'), path.join(root, 'docker', 'build.env'), [])
  const version = spawnSync(invocation.command, ['version', '--short'], { encoding: 'utf8' })
  if (version.error || version.status !== 0 || !(Number(version.stdout.trim().replace(/^v/, '').split('.')[0]) >= 2)) {
    throw new Error('需要可直接执行的 docker-compose v2+（含 v5），不支持旧 Python Compose v1')
  }
  if (configRoot) for (const name of ['.env.server', '.env.client']) {
    if (!statSync(path.join(configRoot, name)).isFile()) throw new Error(`配置目录缺少 ${name}`)
  }
  // 构建与交付使用同一份源码快照，不复制本地缓存、开发产物或环境凭据。
  const files = releaseSourceFiles(root)
  mkdirSync(sourceRoot, { recursive: true })
  for (const file of files) {
    const target = path.join(sourceRoot, file)
    mkdirSync(path.dirname(target), { recursive: true })
    cpSync(path.join(root, file), target)
  }
  writeFileSync(path.join(sourceRoot, sourceManifest), JSON.stringify(files, null, 2) + '\n')
  for (const name of ['.env.server.example', '.env.client.example', 'README.md', 'LICENSE', 'NOTICE', 'THIRD_PARTY_LICENSES.md', 'docker', 'licenses', 'docs']) {
    cpSync(path.join(sourceRoot, name), path.join(stageRoot, name), { recursive: true })
  }
  writeFileSync(path.join(stageRoot, 'README-部署.md'), readFileSync(path.join(sourceRoot, 'docs', 'server-upload.md'), 'utf8')
    .replace('(release-packaging.md)', '(docs/release-packaging.md)'))
  if (configRoot) for (const name of ['.env.server', '.env.client']) cpSync(path.join(configRoot, name), path.join(stageRoot, name))
  mkdirSync(runtimeRoot, { recursive: true })
  const env = { ...process.env, PACKAGE_SOURCE_DIR: sourceRoot.replaceAll('\\', '/'),
    PACKAGE_ARTIFACT_DIR: runtimeRoot.replaceAll('\\', '/'), PACKAGE_OUTPUT_DIR: outputRoot.replaceAll('\\', '/'),
    PACKAGE_PLATFORM: buildPlatform, PACKAGE_BUILD_IMAGE: mavenImage }
  const owner = `${process.getuid?.() ?? 0}:${process.getgid?.() ?? 0}`
  function runBuild(image, command) {
    const args = [...invocation.args, 'run', '--rm', '--no-deps', '-T', 'builder', ...command]
    console.log(`\n> ${invocation.command} --project-name tianshu-build ... run --rm --no-deps -T builder (${image})`)
    const result = spawnSync(invocation.command, args, { cwd: root, env: { ...env, PACKAGE_BUILD_IMAGE: image }, stdio: 'inherit' })
    if (result.error) throw result.error
    if (result.status !== 0) throw new Error(`Docker Compose 构建失败，退出码：${result.status ?? 'unknown'}`)
  }
  const copySource = 'mkdir -p /tmp/tianshu-source && cp -a /source/. /tmp/tianshu-source/ && cd /tmp/tianshu-source'
  if (skipJavaBuild) {
    for (const [module, name] of [['rule-engine-server', 'server.jar'], ['rule-engine-runtime', 'runtime.jar']]) {
      const jars = readdirSync(path.join(root, module, 'target')).filter(name => name.endsWith('.jar') && !name.endsWith('-sources.jar') && !name.endsWith('-javadoc.jar'))
      if (jars.length !== 1) throw new Error(`复用 Java 产物要求 ${module}/target 中恰好一个可执行 JAR`)
      cpSync(path.join(root, module, 'target', jars[0]), path.join(runtimeRoot, name))
    }
  } else {
    runBuild(mavenImage, ['sh', '-ec', `${copySource} && mvn -B -P!onnx-gpu clean package -DskipTests -Donnxruntime.artifactId=onnxruntime${withTests ? ' && mvn -B -P!onnx-gpu test -Donnxruntime.artifactId=onnxruntime' : ''} && cp rule-engine-server/target/rule-engine-server-*.jar /artifacts/server.jar && cp rule-engine-runtime/target/rule-engine-runtime-*.jar /artifacts/runtime.jar && chown -R ${owner} /artifacts`])
  }
  runBuild(nodeImage, ['sh', '-ec', `${copySource} && cd rule-engine-web && npm ci --no-audit --no-fund && npm run build${withTests ? ' && npm run lint && npm test' : ''} && cp -a dist /artifacts/dist && chown -R ${owner} /artifacts`])
  for (const name of ['server.jar', 'runtime.jar', 'dist/index.html']) if (!existsSync(path.join(runtimeRoot, name))) throw new Error(`缺少构建产物 ${name}`)
  const git = spawnSync('git', ['rev-parse', 'HEAD'], { cwd: root, encoding: 'utf8' })
  const status = spawnSync('git', ['status', '--porcelain'], { cwd: root, encoding: 'utf8' })
  writeFileSync(path.join(stageRoot, 'BUILD-INFO.txt'), [
    `构建时间：${new Date().toISOString()}`, `基础提交：${git.status === 0 ? git.stdout.trim() : 'source archive'}`,
    `工作区修改：${status.status === 0 ? (status.stdout.trim() ? '有' : '无') : '无 Git 元数据'}；精确构建源码见 source/`,
    `构建模式：docker-compose（${testDescription(withTests, skipJavaBuild)}）`,
    `Java 产物：${skipJavaBuild ? '复用已有 target，未重新构建' : '本次 Docker Maven CPU 构建'}`,
    `构建平台：${buildPlatform}`, `构建镜像：${mavenImage} / ${nodeImage}`,
    `配置：${configRoot ? '附带指定配置；升级保留服务器现有配置' : '仅含示例模板，不含本机凭据'}`,
    '部署入口：README-部署.md；HTTPS 继续由现有 Nginx Proxy Manager 管理。'
  ].join('\n') + '\n')
  const sums = []
  for (const file of listFiles(stageRoot)) sums.push(`${await sha256(file)}  ${path.relative(stageRoot, file).split(path.sep).join('/')}`)
  writeFileSync(path.join(stageRoot, 'SHA256SUMS.txt'), sums.join('\n') + '\n')
  runBuild(mavenImage, ['sh', '-ec', `jar --create --no-manifest --file /output/${archiveName}.zip -C /output/${archiveName} . && chown ${owner} /output/${archiveName}.zip`])
  const archive = path.join(outputRoot, `${archiveName}.zip`)
  const digest = await sha256(archive)
  writeFileSync(`${archive}.sha256`, `${digest}  ${path.basename(archive)}\n`)
  console.log(`\n部署包：${archive}\nSHA256：${digest}\n校验文件：${archive}.sha256`)
}

if (process.argv[1] && normalizedPath(process.argv[1]) === normalizedPath(fileURLToPath(import.meta.url))) {
  main().catch(error => { console.error(error.message); process.exitCode = 1 })
}
