import { spawnSync } from 'node:child_process'
import { createHash } from 'node:crypto'
import { cpSync, existsSync, mkdirSync, mkdtempSync, readFileSync, readdirSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

// 使用白名单收集产物，不复制工作区、.env、Maven settings 或引擎源码。
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const version = readFileSync(path.join(root, 'pom.xml'), 'utf8').match(/<version>([^<]+)<\/version>/)[1]
if (!/^[a-zA-Z0-9.-]+$/.test(version)) throw new Error('Invalid release version')
const output = path.join(root, 'output', 'java-offline')
mkdirSync(output, { recursive: true })
const stage = mkdtempSync(path.join(output, 'stage-'))
const name = `tianshu-java-${version}-plain`
const bundle = path.join(stage, name)
mkdirSync(bundle)

function run(command, args, cwd = root, capture = false) {
  if (process.platform === 'win32' && command === 'mvn') {
    const quote = value => `'${String(value).replaceAll("'", "''")}'`
    const script = `& mvn.cmd ${args.map(quote).join(' ')}; exit $LASTEXITCODE`
    command = 'powershell.exe'
    args = ['-NoProfile', '-NonInteractive', '-EncodedCommand', Buffer.from(script, 'utf16le').toString('base64')]
  }
  const result = spawnSync(command, args, { cwd, windowsHide: true,
    stdio: capture ? 'pipe' : 'inherit', encoding: 'utf8', maxBuffer: 16 * 1024 * 1024 })
  if (result.error || result.status !== 0) throw result.error || new Error(`${command} failed: ${result.stderr || result.status}`)
  return result.stdout || ''
}
function copy(source, target) {
  mkdirSync(path.dirname(target), { recursive: true })
  cpSync(source, target, { recursive: true })
}
function dependencies(module, destination) {
  run('mvn', ['-B', '-pl', module, 'org.apache.maven.plugins:maven-dependency-plugin:3.7.0:copy-dependencies',
    '-DincludeScope=runtime', `-DoutputDirectory=${destination}`])
}
function files(directory) {
  return readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    const file = path.join(directory, entry.name)
    return entry.isDirectory() ? files(file) : [file]
  })
}

run('mvn', ['-B', '-pl', 'rule-engine-example', '-am', 'clean', 'install'])
const sdkLib = path.join(bundle, 'sdk', 'lib')
const exampleLib = path.join(bundle, 'example', 'lib')
dependencies('rule-engine-client-http', sdkLib)
copy(path.join(root, `rule-engine-client-http/target/rule-engine-client-http-${version}.jar`), path.join(sdkLib, `rule-engine-client-http-${version}.jar`))
dependencies('rule-engine-example', exampleLib)
copy(path.join(root, `rule-engine-example/target/rule-engine-example-${version}.jar`), path.join(bundle, 'example', 'rule-engine-example.jar'))
copy(path.join(root, 'rule-engine-example/src/main'), path.join(bundle, 'example-src/src/main'))
copy(path.join(root, 'rule-engine-example/distribution'), bundle)
copy(path.join(root, 'rule-engine-example/README.md'), path.join(bundle, 'README.md'))
copy(path.join(root, 'LICENSE'), path.join(bundle, 'LICENSE'))
copy(path.join(root, 'rule-engine-example/distribution-NOTICE.md'), path.join(bundle, 'NOTICE.md'))
for (const [source, filename] of [['pom.xml', 'qlexpress-rule.pom'], ['rule-engine-model/pom.xml', 'rule-engine-model.pom'], ['rule-engine-client-http/pom.xml', 'rule-engine-client-http.pom']]) {
  copy(path.join(root, source), path.join(bundle, 'sdk', 'poms', filename))
}
// 客户示例 POM 不依赖本仓库 parent；SDK 通过随包 JAR 使用，也可上传客户自己的 Maven 私服。
const pom = path.join(bundle, 'example-src/pom.xml')
writeFileSync(pom, readFileSync(pom, 'utf8').replaceAll('@SDK_VERSION@', version))

const jars = [...files(sdkLib), ...files(exampleLib)].filter(file => file.endsWith('.jar'))
const forbidden = /(?:rule-engine-core|rule-engine-server|rule-engine-client-(?!http)|pmml|qlexpress|spring-data-redis|lettuce|kafka)/i
for (const jar of jars) {
  if (forbidden.test(path.basename(jar))) throw new Error(`Unexpected engine dependency: ${jar}`)
  const entries = run('jar', ['tf', jar], root, true).split(/\r?\n/)
  if (entries.some(entry => /^(com\/hengshucredit\/rule\/(core|server)\/|org\/jpmml\/|com\/alibaba\/qlexpress)/.test(entry))) {
    throw new Error(`Engine implementation found inside ${jar}`)
  }
  const legal = entries.filter(entry => !entry.includes('..') && /^META-INF\/(?:[^/]*\/)*(?:LICENSE|NOTICE|COPYING)[^/]*$/i.test(entry))
  const legalDir = path.join(bundle, 'licenses', path.basename(jar, '.jar'))
  if (legal.length && !existsSync(legalDir)) {
    mkdirSync(legalDir, { recursive: true })
    run('jar', ['xf', jar, ...legal], legalDir)
  }
}
// 包含准确的依赖树供交付审核；完整原始 JAR 中的许可证与 NOTICE 不做裁剪。
run('mvn', ['-B', '-pl', 'rule-engine-example', 'org.apache.maven.plugins:maven-dependency-plugin:3.7.0:tree',
  '-Dscope=runtime', `-DoutputFile=${path.join(bundle, 'dependencies.txt')}`])
writeFileSync(path.join(bundle, 'release.json'), JSON.stringify({ product: 'tianshu-java', version, mode: 'plain',
  builtAt: new Date().toISOString(), minimumJava: 17, engineExecution: 'HTTP',
  offline: 'runtime and javac example rebuild; engine HTTP connectivity is required' }, null, 2) + '\n')
const sums = files(bundle).sort().map(file => `${createHash('sha256').update(readFileSync(file)).digest('hex')}  ${path.relative(bundle, file).replaceAll('\\', '/')}`)
writeFileSync(path.join(bundle, 'SHA256SUMS'), sums.join('\n') + '\n')
const archive = path.join(output, `${name}-${new Date().toISOString().replaceAll(/[:.]/g, '-')}.tar.gz`)
run('tar', ['-czf', archive, '-C', stage, name])
writeFileSync(`${archive}.sha256`, `${createHash('sha256').update(readFileSync(archive)).digest('hex')}  ${path.basename(archive)}\n`)
console.log(`\nOffline delivery: ${archive}\nStaging directory retained for inspection: ${bundle}`)
