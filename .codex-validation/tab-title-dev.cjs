const { spawn, spawnSync } = require('node:child_process')
const path = require('node:path')
const server = spawn('npm.cmd', ['run', 'dev', '--', '--host', '127.0.0.1', '--port', '19091'], {
  cwd: path.resolve(__dirname, '../rule-engine-builder-ui'),
  stdio: 'inherit', shell: true, windowsHide: true,
})
const stop = () => spawnSync('taskkill', ['/PID', String(server.pid), '/T', '/F'], { windowsHide: true })
const timeout = setTimeout(stop, 1800000)
server.on('exit', code => { clearTimeout(timeout); process.exit(code || 0) })
process.on('SIGINT', stop)
process.on('SIGTERM', stop)
