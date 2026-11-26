import test from 'node:test'
import assert from 'node:assert/strict'
import { mkdtempSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { spawnSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'

const script = fileURLToPath(new URL('../../compose-down.sh', import.meta.url))

// 用可控的 Linux CLI 模拟旧 Docker 将 Compose 参数误交给顶层解析器的情况。
for (const mode of ['plugin', 'standalone', 'early-v2', 'legacy-only']) {
  test('停机入口和执行范围：' + mode, { skip: process.platform === 'win32' }, () => {
    const cwd = mkdtempSync(path.join(os.tmpdir(), 'compose down '))
    try {
      const file = path.join(cwd, 'docker/docker-compose.full.yml')
      mkdirSync(path.dirname(file))
      const bin = path.join(cwd, 'bin')
      mkdirSync(bin)
      for (const name of [file, '.env.client', '.env.server', 'actions']) writeFileSync(path.resolve(cwd, name), '')
      const labels = ['server', 'mysql'].map(service => JSON.stringify({
        'com.docker.compose.project': 'installed',
        'com.docker.compose.service': service,
        'com.docker.compose.project.config_files': file,
      })).join('\n')
      writeFileSync(path.join(cwd, 'labels'), labels + '\n')
      writeFileSync(path.join(bin, 'docker'), `#!/bin/sh
case "$1" in
  ps) echo 'app db'; exit 0;;
  inspect) cat labels; exit 0;;
  compose) [ "$MODE" = plugin ] || { echo 'See docker --help' >&2; exit 1; }; shift;;
  *) exit 55;;
esac
exec "$PWD/driver" plugin "$@"
`, { mode: 0o755 })
      writeFileSync(path.join(bin, 'docker-compose'), '#!/bin/sh\nexec "$PWD/driver" standalone "$@"\n', { mode: 0o755 })
      writeFileSync(path.join(cwd, 'driver'), `#!/bin/sh
driver=$1; shift
case "$1" in
  version) if [ "$MODE" = legacy-only ]; then echo 1.29.2; else echo v2.29.2; fi;;
  down) if [ "$MODE" = early-v2 ]; then echo 'Usage: down [OPTIONS]'; else echo 'Usage: down [OPTIONS] [SERVICES]'; fi;;
  --project-name)
    [ "$2" = installed ] || exit 56
    while [ "$1" != '*' ] && [ "$#" -gt 0 ]; do shift; done
    shift
    if [ "$1" = config ]; then printf 'server\\nmysql\\n'; else echo "$driver $*" >> actions; fi;;
  *) exit 57;;
esac
`, { mode: 0o755 })
      const run = (...args) => spawnSync('bash', [script, ...args], {
        cwd, encoding: 'utf8', env: { ...process.env, MODE: mode, PATH: bin + ':' + process.env.PATH },
      })
      const preview = run('--dry-run')
      assert.equal(readFileSync(path.join(cwd, 'actions'), 'utf8'), '')
      if (mode === 'legacy-only') {
        assert.notEqual(preview.status, 0)
        assert.match(preview.stderr, /Compose v2\+/)
        return
      }
      assert.equal(preview.status, 0, preview.stderr)
      assert.match(preview.stdout, mode === 'plugin' ? /^docker compose / : /^docker-compose /)
      const actual = run()
      assert.equal(actual.status, 0, actual.stderr)
      const driver = mode === 'plugin' ? 'plugin' : 'standalone'
      assert.equal(readFileSync(path.join(cwd, 'actions'), 'utf8'), mode === 'early-v2'
        ? 'standalone stop server\nstandalone rm --force server\n' : driver + ' down --remove-orphans=false server\n')
    } finally {
      rmSync(cwd, { recursive: true, force: true })
    }
  })
}
