import test from 'node:test'
import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'

const root = fileURLToPath(new URL('../../../', import.meta.url))
const topologies = ['docker-compose.server.yml', 'docker-compose.full.yml', 'docker-compose.yml']

test('三个 web 入口都渲染客户端地址配置，并保留 Nginx 运行时变量', () => {
  for (const topology of topologies) {
    for (const proxy of ['', '192.0.2.10']) {
      const header = proxy ? 'X-Forwarded-For' : ''
      const json = execFileSync('docker', ['compose', '--project-name', 'tianshu-web-proxy-config',
        '--env-file', '.env.client.example', '--env-file', '.env.server.example',
        '-f', 'docker/' + topology, 'config', '--format', 'json'], {
        cwd: root, encoding: 'utf8', env: { ...process.env, WEB_TRUSTED_PROXY: proxy, WEB_REAL_IP_HEADER: header },
      })
      const { environment, volumes } = JSON.parse(json).services.web
      assert.equal(environment.WEB_TRUSTED_PROXY, proxy || 'unix:', topology)
      assert.equal(environment.WEB_REAL_IP_HEADER, header || 'X-Real-IP', topology)
      const filter = new RegExp(environment.NGINX_ENVSUBST_FILTER)
      assert.ok(filter.test('WEB_TRUSTED_PROXY') && filter.test('WEB_REAL_IP_HEADER'), topology)
      assert.ok(!filter.test('remote_addr') && !filter.test('scheme'), topology)
      const config = volumes.find(volume => volume.target === '/etc/nginx/templates/default.conf.template')
      assert.match(config?.source ?? '', /[/\\]nginx[/\\]default\.conf$/, topology)
    }
  }
})
