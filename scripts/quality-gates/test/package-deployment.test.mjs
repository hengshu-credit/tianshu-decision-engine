import test from 'node:test'
import assert from 'node:assert/strict'
import { mkdtempSync, mkdirSync, writeFileSync, rmSync } from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { spawnSync } from 'node:child_process'
import { composeInvocation, releaseSourceFiles, testDescription } from '../../package-deployment.mjs'

for (const platform of ['win32', 'darwin', 'linux']) test(`${platform} 使用 docker-compose 并把含空格路径作为独立参数传递`, () => {
  const result = composeInvocation('work space/docker/build.yml', 'work space/docker/build.env', ['run', '--rm', 'builder'], platform)
  assert.equal(result.command, platform === 'win32' ? 'docker-compose.exe' : 'docker-compose')
  assert.deepEqual(result.args, ['--project-name', 'tianshu-build', '--env-file', 'work space/docker/build.env', '-f', 'work space/docker/build.yml', 'run', '--rm', 'builder'])
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
