import test from 'node:test'
import assert from 'node:assert/strict'
import { mkdtempSync, mkdirSync, writeFileSync, readFileSync, readdirSync, rmSync } from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { spawnSync } from 'node:child_process'
import { composeInvocation, releaseSourceFiles, testDescription, copyDeploymentMigrations } from '../../package-deployment.mjs'

for (const platform of ['win32', 'darwin', 'linux']) for (const plugin of [true, false]) test(`${platform} 自动选择 ${plugin ? 'docker compose' : 'docker-compose'} 并保留含空格路径`, () => {
  const probes = []
  const result = composeInvocation('work space/docker/build.yml', 'work space/docker/build.env', ['run', '--rm', 'builder'], platform, (command, args) => {
    probes.push([command, args])
    return args[0] === 'compose' && !plugin ? { status: 1, stdout: '' } : { status: 0, stdout: 'v5.1.4\n' }
  })
  const executable = plugin ? 'docker' : 'docker-compose'
  assert.equal(result.command, executable + (platform === 'win32' ? '.exe' : ''))
  assert.equal(probes.length, plugin ? 1 : 2)
  assert.deepEqual(result.args, [...(plugin ? ['compose'] : []), '--project-name', 'tianshu-build', '--env-file', 'work space/docker/build.env', '-f', 'work space/docker/build.yml', 'run', '--rm', 'builder'])
})

test('构建拒绝只有旧 Python Compose v1 的环境', () => {
  assert.throws(() => composeInvocation('build.yml', 'build.env', [], 'linux', () => ({ status: 0, stdout: '1.29.2' })), /不支持旧 Python/)
})

test('Git 工作区包含未提交源码，排除本地配置、忽略目录及已删除文件', () => {
  const directory = mkdtempSync(path.join(os.tmpdir(), 'tianshu source '))
  try {
    assert.equal(spawnSync('git', ['init', '--quiet', directory]).status, 0)
    writeFileSync(path.join(directory, '.gitignore'), 'output/\n')
    writeFileSync(path.join(directory, 'new.js'), 'source')
    writeFileSync(path.join(directory, '.env.server'), 'private')
    writeFileSync(path.join(directory, '.env.server.example'), 'template')
    mkdirSync(path.join(directory, 'output'))
    writeFileSync(path.join(directory, 'output', 'old.zip'), 'ignored')
    assert.deepEqual(releaseSourceFiles(directory), ['.env.server.example', '.gitignore', 'new.js'])
  } finally { rmSync(directory, { recursive: true, force: true }) }
})

test('解压源码无需 .git 可重新打包，拒绝清单路径越界且排除凭据', () => {
  const directory = mkdtempSync(path.join(os.tmpdir(), 'tianshu-source-'))
  try {
    writeFileSync(path.join(directory, 'pom.xml'), '<project/>')
    writeFileSync(path.join(directory, '.env.client'), 'private')
    writeFileSync(path.join(directory, '.release-source-files.json'), JSON.stringify(['pom.xml', '.env.client']))
    assert.deepEqual(releaseSourceFiles(directory), ['pom.xml'])
    for (const entry of ['../private', '/etc/passwd', 'C:/private', '..\\private']) {
      writeFileSync(path.join(directory, '.release-source-files.json'), JSON.stringify([entry]))
      assert.throws(() => releaseSourceFiles(directory), /非法相对路径/)
    }
  } finally { rmSync(directory, { recursive: true, force: true }) }
})

test('复用 Java 制品时构建记录不能声称执行了后端测试', () => {
  assert.match(testDescription(true, true), /未运行后端测试/)
  assert.match(testDescription(true, false), /^后端测试/)
  assert.match(testDescription(false, false), /跳过测试/)
})

test('部署包独立交付迁移 SQL 和执行说明，内容与构建源码一致且不混入初始化数据', () => {
  const directory = mkdtempSync(path.join(os.tmpdir(), 'tianshu migrations '))
  try {
    const source = path.join(directory, 'source')
    const sql = path.join(source, 'rule-engine-server/src/main/resources/sql')
    mkdirSync(path.join(sql, 'migrations'), { recursive: true })
    writeFileSync(path.join(sql, 'schema.sql'), 'CREATE TABLE initial_only (id INT);')
    writeFileSync(path.join(sql, 'initial-data.sql'), 'INSERT INTO initial_only VALUES (1);')
    const migration = '-- 中文说明\r\nALTER TABLE existing ADD value INT;\r\n'
    writeFileSync(path.join(sql, 'migrations/2026-11-08-example.sql'), migration)
    writeFileSync(path.join(sql, 'migrations/README.md'), '备份并停止应用后执行适用迁移，再启动新版。')
    copyDeploymentMigrations(source, directory)
    assert.deepEqual(readdirSync(path.join(directory, 'migrations')).sort(), ['2026-11-08-example.sql', 'README.md'])
    assert.equal(readFileSync(path.join(directory, 'migrations/2026-11-08-example.sql'), 'utf8'), migration)
    assert.equal(readFileSync(path.join(directory, 'migrations/README.md'), 'utf8'), '备份并停止应用后执行适用迁移，再启动新版。')
  } finally { rmSync(directory, { recursive: true, force: true }) }
})
