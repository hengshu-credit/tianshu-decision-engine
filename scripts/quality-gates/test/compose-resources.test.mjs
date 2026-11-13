import test from 'node:test'
import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

const root = fileURLToPath(new URL('../../../', import.meta.url))
const topologies = {
  'docker-compose.yml': ['server', 'web'],
  'docker-compose.server.yml': ['server', 'web'],
  'docker-compose.full.yml': ['server', 'mysql', 'redis', 'web', 'kafka'],
  'docker-compose.mysql.yml': ['mysql'],
  'docker-compose.redis.yml': ['redis'],
  'docker-compose.kafka.yml': ['kafka'],
}

test('各部署拓扑将每个服务的 CPU 和内存覆盖传递给 Docker', () => {
  const env = { ...process.env }
  for (const file of Object.keys(topologies)) {
    const yaml = readFileSync(new URL(`../../../docker/${file}`, import.meta.url), 'utf8')
    for (const match of yaml.matchAll(/\$\{([A-Z][A-Z0-9_]*)(?=[:}])/g)) delete env[match[1]]
  }
  const template = readFileSync(new URL('../../../.env.server.example', import.meta.url), 'utf8')
  const expected = new Map()
  // 各服务用不同且区别于示例的值，避免变量串用或仍硬编码时通过。
  for (const [index, name] of ['server', 'mysql', 'redis', 'web', 'kafka'].entries()) {
    const prefix = name.toUpperCase()
    const cpus = index + 1.25
    const mib = 2560 + 64 * index
    for (const key of [`${prefix}_CPU_LIMIT`, `${prefix}_MEMORY_LIMIT`]) {
      assert.match(template, new RegExp(`^${key}=.+$`, 'm'))
    }
    env[`${prefix}_CPU_LIMIT`] = String(cpus)
    env[`${prefix}_MEMORY_LIMIT`] = `${mib}m`
    expected.set(name, { cpus, bytes: mib * 1024 * 1024 })
  }
  for (const [file, names] of Object.entries(topologies)) {
    const json = execFileSync('docker', ['compose', '--env-file', '.env.client.example', '--env-file', '.env.server.example',
      '-p', 'tianshu-resource-contract', '-f', `docker/${file}`, 'config', '--format', 'json'], { cwd: root, env, encoding: 'utf8' })
    const { services } = JSON.parse(json)
    for (const name of names) {
      const service = services[name]
      const { cpus, bytes } = expected.get(name)
      const context = `${file} / ${name}`
      assert.equal(Number(service.cpus), cpus, context)
      assert.equal(Number(service.mem_limit), bytes, context)
      if (name === 'kafka') {
        assert.equal(Number(service.memswap_limit), bytes, context)
      } else {
        assert.equal(Number(service.deploy.resources.limits.cpus), cpus, context)
        assert.equal(Number(service.deploy.resources.limits.memory), bytes, context)
      }
    }
  }
})
