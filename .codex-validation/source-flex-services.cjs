const { spawn } = require('node:child_process')
const fs = require('node:fs')
const path = require('node:path')
const http = require('node:http')
const crypto = require('node:crypto')
const root = path.resolve(__dirname, '..')
const mode = process.argv[2]
const timeout = 1800000
if (mode === 'mock') {
  let tokens = 0
  let submissions = 0
  let polls = 0
  let callbacks = 0
  const server = http.createServer(async (req, res) => {
    let raw = ''
    for await (const chunk of req) raw += chunk
    const body = raw ? JSON.parse(raw) : {}
    const send = (value, status = 200) => { res.writeHead(status, { 'Content-Type': 'application/json' }); res.end(JSON.stringify(value)) }
    if (req.url === '/token' || req.url === '/tokent') return send({ token: 'validation-token-' + ++tokens, expires_in: 3600 })
    if (req.url === '/score') return send(req.headers.authorization === 'Bearer validation-token-1' ? { code: 'TOKEN_EXPIRED' } : { score: 720, code: 'OK' })
    if (req.url === '/submit') { submissions++; return send({ taskId: 'job-' + submissions }, 202) }
    if (req.url.startsWith('/result')) { polls++; return send(polls % 2 ? { status: 'PENDING' } : { status: 'SUCCESS', data: { score: 730 } }) }
    if (req.url === '/callback-submit') {
      submissions++
      const taskId = 'callback-' + submissions
      const notify = JSON.stringify({ job: taskId, status: 'SUCCESS', data: { score: 740 } })
      const signature = crypto.createHmac('sha256', 'local-validation-signature').update(notify).digest('hex')
      if (body.callback) {
        const target = new URL(body.callback)
        if (target.hostname === 'localhost' && target.port === '28080' && target.pathname.startsWith('/api/external-callback/')) {
          try { const response = await fetch(target, { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-Signature': signature }, body: notify }); if (response.ok) callbacks++ } catch (e) { console.log(e.message) }
        }
      }
      return send({ taskId }, 202)
    }
    if (req.url === '/stats') return send({ tokens, submissions, polls, callbacks })
    send({ message: 'not found' }, 404)
  })
  server.listen(28081, '127.0.0.1', () => console.log('mock ready 28081'))
  setTimeout(() => server.close(), timeout)
} else {
  const env = { ...process.env }
  for (const line of fs.readFileSync(path.join(root, '.env'), 'utf8').split(/\r?\n/)) {
    const match = line.match(/^([A-Za-z_][A-Za-z_0-9]*)=(.*)$/)
    if (match) env[match[1]] = match[2].trim().replace(/^(['"])(.*)\1$/, '$2')
  }
  env.SERVER_PORT = '28080'
  env.REDIS_HOST = '192.168.71.106'
  env.JDK_JAVA_OPTIONS = (env.JDK_JAVA_OPTIONS || '') + ' -Djdk.net.unixdomain.tmpdir=E:/workspace'
  env.VITE_PORT = '29090'
  env.VITE_DEV_PROXY = 'http://localhost:28080'
  const cwd = path.join(root, mode === 'backend' ? 'rule-engine-server' : 'rule-engine-builder-ui')
  const command = mode === 'backend' ? 'mvn spring-boot:run' : 'npm run dev -- --host 127.0.0.1'
  const child = spawn(process.env.ComSpec, ['/d', '/s', '/c', command], { cwd, env, windowsHide: true, timeout, stdio: 'inherit' })
  fs.writeFileSync(path.join(__dirname, 'source-flex-' + mode + '.pid'), String(child.pid))
  child.on('exit', code => process.exit(code ?? 1))
}
