import test from 'node:test'
import assert from 'node:assert/strict'
import { existsSync, mkdtempSync, mkdirSync, readFileSync, readdirSync, rmSync, writeFileSync } from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { spawnSync } from 'node:child_process'
import { createHash } from 'node:crypto'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../..')
const doc = readFileSync(path.join(root, 'docs/server-upload.md'), 'utf8')
const legacyDoc = readFileSync(path.join(root, 'docs/deployment-flat-legacy.md'), 'utf8')
const blocks = [...(doc + '\n' + legacyDoc).matchAll(/```bash\r?\n([\s\S]*?)```/g)].map(match => match[1])
const prepare = blocks.find(block => block.includes('NEXT_RUNTIME_DIR='))
const replace = blocks.find(block => block.includes('mv "$APP_RUNTIME_DIR"'))
const bash = process.platform === 'win32' ? 'C:/Program Files/Git/bin/bash.exe' : 'bash'

test('归档入口绑定指定 ZIP 的校验值，同一命令可初始化和重复更新', () => {
  const directory = mkdtempSync(path.join(os.tmpdir(), 'tianshu archive '))
  const interpreter = process.platform === 'win32' ? 'python' : 'python3'
  try {
    const entry = path.join(directory, 'install-release.sh')
    writeFileSync(entry, readFileSync(path.join(root, 'scripts/install-release.sh')))
    const archive = path.join(directory, 'release.zip')
    const probe = "import os,sys; from pathlib import Path; assert os.getenv('RULE_ENGINE_INITIAL_DATA_ENABLED', 'from-env-file') == os.environ['EXPECTED_INITIAL_DATA']; assert os.environ['SPRING_SQL_INIT_MODE']=='always'; assert '--install-app' in sys.argv; Path('result.txt').write_text(str(Path(__file__).parent))"
    const zip = spawnSync(interpreter, ['-c', 'import sys,zipfile; z=zipfile.ZipFile(sys.argv[1],"w"); z.writestr("deploy.py",sys.argv[2]); z.close()', archive, probe])
    assert.equal(zip.status, 0)
    const digest = createHash('sha256').update(readFileSync(archive)).digest('hex')
    // Git Bash on Windows has python.exe rather than python3.
    if (process.platform === 'win32') writeFileSync(path.join(directory, 'python3'), '#!/bin/sh\nexec python "$@"\n')
    const setup = process.platform === 'win32' ? 'export PATH="$PWD:$PATH"; ' : ''
    const run = initialData => {
      const env = { ...process.env, EXPECTED_INITIAL_DATA: initialData ?? 'from-env-file' }
      if (initialData === undefined) delete env.RULE_ENGINE_INITIAL_DATA_ENABLED
      else env.RULE_ENGINE_INITIAL_DATA_ENABLED = initialData
      return spawnSync(bash, ['-c', setup + 'bash install-release.sh release.zip'], { cwd: directory, encoding: 'utf8', env })
    }
    for (const contents of [digest + '  other.zip', '0'.repeat(64) + '  release.zip']) {
      writeFileSync(archive + '.sha256', contents + '\n')
      assert.notEqual(run().status, 0)
      assert.ok(!existsSync(path.join(directory, 'releases')))
    }
    writeFileSync(archive + '.sha256', digest + '  release.zip\n')
    for (const initialData of [undefined, 'true', 'false']) {
      const result = run(initialData)
      assert.equal(result.status, 0, result.stderr)
      assert.ok(existsSync(path.join(directory, 'result.txt')))
    }
    assert.equal(readdirSync(path.join(directory, 'releases')).length, 3)
  } finally { rmSync(directory, { recursive: true, force: true }) }
})

for (const valid of [true, false]) test(`部署目录内的五行更新命令：${valid ? '重复解压使用独立子目录且保留原配置' : '校验失败不解压或启动'}`, () => {
  const fixture = mkdtempSync(path.join(os.tmpdir(), 'tianshu upload '))
  try {
    const installation = path.join(fixture, 'installation')
    mkdirSync(path.join(installation, 'docker'), { recursive: true })
    mkdirSync(path.join(installation, 'deploy-prod/releases'), { recursive: true })
    writeFileSync(path.join(installation, '.env.server'), 'keep-env')
    writeFileSync(path.join(installation, 'docker/keep'), 'keep-data')
    writeFileSync(path.join(fixture, 'neighbor'), 'keep-neighbor')
    const archive = path.join(installation, 'package.zip')
    const zip = spawnSync(process.platform === 'win32' ? 'python' : 'python3', ['-B', '-c',
      'import sys,zipfile; z=zipfile.ZipFile(sys.argv[1],"w"); z.writestr("deploy.py","# deployment entry"); z.writestr("README-部署.md","中文部署说明".encode("utf-8")); z.close()', archive])
    assert.ifError(zip.error)
    assert.equal(zip.status, 0)
    const checksum = createHash('sha256').update(readFileSync(archive)).digest('hex')
    writeFileSync(archive + '.sha256', `${valid ? checksum : '0'.repeat(64)}  package.zip\n`)
    const quick = blocks.find(block => block.includes('PKG=包文件名.zip') && block.includes('mktemp'))
    assert.ok(quick)
    assert.equal(quick.trim().split(/\r?\n/).length, 5)
    const interpreter = process.platform === 'win32' ? 'python' : 'python3'
    const input = `set -euo pipefail\nFIXTURE_ROOT="$PWD"\npython3() { if [ "$3" = "-m" ]; then command ${interpreter} "$@"; else printf "%s\\n" "$@" > "$FIXTURE_ROOT/call.txt"; fi; }\n`
      + quick.replace('cd /root/tianshu-decision-engine', 'cd "$FIXTURE_ROOT/installation"').replace('PKG=包文件名.zip', 'PKG=package.zip')
    for (let attempt = 0; attempt < (valid ? 2 : 1); attempt++) {
      const result = spawnSync(bash, ['--noprofile', '--norc', '-s'], { cwd: fixture, input, encoding: 'utf8' })
      assert.ifError(result.error)
      assert.equal(result.status === 0, valid, result.stderr)
    }
    const releases = readdirSync(path.join(installation, 'deploy-prod/releases'))
    assert.equal(releases.length, valid ? 2 : 0)
    if (valid) {
      for (const name of releases) {
        assert.equal(readFileSync(path.join(installation, 'deploy-prod/releases', name, 'deploy.py'), 'utf8'), '# deployment entry')
        assert.equal(readFileSync(path.join(installation, 'deploy-prod/releases', name, 'README-部署.md'), 'utf8'), '中文部署说明')
      }
      const args = readFileSync(path.join(fixture, 'call.txt'), 'utf8').trim().split('\n')
      assert.deepEqual(args.splice(0, 2), ['-X', 'utf8'])
      assert.equal(args[1], '--target')
      assert.ok(args[0].startsWith(args[2] + '/deploy-prod/releases/update-'))
      assert.ok(args[2].endsWith('/installation'))
    } else assert.equal(existsSync(path.join(fixture, 'call.txt')), false)
    assert.equal(readFileSync(path.join(installation, '.env.server'), 'utf8'), 'keep-env')
    assert.equal(readFileSync(path.join(installation, 'docker/keep'), 'utf8'), 'keep-data')
    assert.equal(readFileSync(path.join(fixture, 'neighbor'), 'utf8'), 'keep-neighbor')
    assert.equal(createHash('sha256').update(readFileSync(archive)).digest('hex'), checksum)
  } finally { rmSync(fixture, { recursive: true, force: true }) }
})

for (const existing of ['docker', '.env.server', '.env.client']) test(`首次安装步骤拒绝覆盖已有 ${existing}`, () => {
  const fixture = mkdtempSync(path.join(os.tmpdir(), 'tianshu install '))
  try {
    const target = path.join(fixture, 'installation')
    const source = path.join(fixture, 'release')
    mkdirSync(target)
    mkdirSync(path.join(source, 'docker'), { recursive: true })
    writeFileSync(path.join(source, 'docker/keep'), 'new')
    for (const env of ['.env.server', '.env.client']) {
      writeFileSync(path.join(source, env), 'new-config')
    }
    const sentinel = existing === 'docker' ? 'docker/keep' : existing
    mkdirSync(path.dirname(path.join(target, sentinel)), { recursive: true })
    writeFileSync(path.join(target, sentinel), 'original')
    const install = blocks.find(block => block.includes('cp -a "$RELEASE_DIR/."'))
    assert.ok(install)
    const result = spawnSync(bash, ['--noprofile', '--norc', '-s'], {
      cwd: fixture, encoding: 'utf8',
      input: 'set -euo pipefail\nFIXTURE_ROOT="$PWD"\nRELEASE_DIR="$PWD/release"\nvi() { :; }\n'
        + install.replaceAll('/root/tianshu-decision-engine', '"$FIXTURE_ROOT/installation"')
    })
    assert.ifError(result.error)
    assert.notEqual(result.status, 0, '发现已有安装时必须终止')
    assert.equal(readFileSync(path.join(target, sentinel), 'utf8'), 'original')
  } finally { rmSync(fixture, { recursive: true, force: true }) }
})

for (const complete of [true, false]) test(`执行文档中的应用更新命令：${complete ? '仅替换制品并保留配置与共享数据' : '缺失制品时先失败且保留旧版'}`, () => {
  const fixture = mkdtempSync(path.join(os.tmpdir(), 'tianshu update '))
  const files = {
    'installation/.env.server': 'server-secret-placeholder',
    'installation/.env.client': 'client-secret-placeholder',
    'installation/docker/docker-compose.server.yml': 'original deployment',
    'installation/docker/nginx/default.conf': 'original proxy',
    'installation/docker/rule-engine-mysql/data/keep': 'mysql-data',
    'installation/docker/rule-engine-redis/data/keep': 'redis-data',
    'installation/docker/rule-engine-kafka/start-kafka.sh': 'kafka-config',
    'installation/docker/runtime/server.jar': 'old-server',
    'installation/docker/runtime/runtime.jar': 'old-runtime',
    'installation/docker/runtime/dist/index.html': 'old-index',
    'installation/docker/runtime/dist/obsolete.js': 'old-asset',
    'release/docker/runtime/server.jar': 'new-server',
    'release/docker/runtime/runtime.jar': 'new-runtime',
    'release/docker/runtime/dist/new.js': 'new-asset',
    'release/BUILD-INFO.txt': 'new-build',
    ...(complete ? { 'release/docker/runtime/dist/index.html': 'new-index' } : {})
  }
  try {
    for (const [file, content] of Object.entries(files)) {
      mkdirSync(path.dirname(path.join(fixture, file)), { recursive: true })
      writeFileSync(path.join(fixture, file), content)
    }
    assert.ok(prepare && replace, '必须执行实际发布文档中的准备与替换步骤')
    const script = 'set -euo pipefail\nFIXTURE_ROOT="$PWD"\nRELEASE_DIR="$PWD/release"\n'
      + prepare.replace('APP_DIR=/root/tianshu-decision-engine', 'APP_DIR="$FIXTURE_ROOT/installation"')
      + '\n' + replace
    const result = spawnSync(bash, ['--noprofile', '--norc', '-s'], { cwd: fixture, input: script, encoding: 'utf8' })
    assert.ifError(result.error)
    if (complete) {
      assert.equal(result.status, 0, result.stderr)
      const backups = readdirSync(path.join(fixture, 'installation/backups'))
      assert.equal(backups.length, 1)
      assert.equal(readFileSync(path.join(fixture, 'installation/backups', backups[0], 'runtime/dist/obsolete.js'), 'utf8'), 'old-asset')
      for (const file of ['server.jar', 'runtime.jar', 'dist/index.html', 'dist/new.js']) {
        assert.deepEqual(readFileSync(path.join(fixture, 'installation/docker/runtime', file)), readFileSync(path.join(fixture, 'release/docker/runtime', file)))
      }
      assert.equal(existsSync(path.join(fixture, 'installation/docker/runtime/dist/obsolete.js')), false)
    } else {
      assert.notEqual(result.status, 0)
      for (const [file, content] of Object.entries(files).filter(([file]) => file.startsWith('installation/docker/runtime/'))) {
        assert.equal(readFileSync(path.join(fixture, file), 'utf8'), content)
      }
      assert.equal(existsSync(path.join(fixture, 'installation/backups')), false)
    }
    for (const [file, content] of Object.entries(files).filter(([file]) => file.startsWith('installation/') && !file.startsWith('installation/docker/runtime/'))) {
      assert.equal(readFileSync(path.join(fixture, file), 'utf8'), content, file)
    }
  } finally { rmSync(fixture, { recursive: true, force: true }) }
})
