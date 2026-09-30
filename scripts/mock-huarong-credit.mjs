import http from 'node:http'
import { readFileSync, appendFileSync, mkdirSync } from 'node:fs'
import { dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

// 仅在本机提供合成征信样例，不连接华融或模型服务。
const sample = JSON.parse(readFileSync(new URL('../docs/examples/huarong-credit/response-company.sample.json', import.meta.url), 'utf8'))
const logPath = fileURLToPath(new URL('../.codex-run-logs/huarong-mock-calls.jsonl', import.meta.url))
mkdirSync(dirname(logPath), { recursive: true })
const tasks = new Map()
const pdf = Buffer.from('%PDF-1.4\n% LOCAL SYNTHETIC TEST FILE ONLY\n1 0 obj<</Type/Catalog>>endobj\n%%EOF')
const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a8c8AAAAASUVORK5CYII=', 'base64')
const record = data => appendFileSync(logPath, JSON.stringify({ time: new Date().toISOString(), ...data }) + '\n')
const server = http.createServer(async (req, res) => {
  res.setHeader('Content-Type', 'application/json')
  const send = data => res.end(JSON.stringify(data))
  if (req.url.startsWith('/files/')) {
    record({ type: 'FILE', path: req.url })
    res.setHeader('Content-Type', req.url.endsWith('.pdf') ? 'application/pdf' : 'image/png')
    return res.end(req.url.endsWith('.pdf') ? pdf : png)
  }
  let body
  try {
    const chunks = []; for await (const chunk of req) chunks.push(chunk)
    body = JSON.parse(Buffer.concat(chunks).toString() || '{}')
  } catch { res.statusCode = 400; return send({ code: '0003', message: 'JSON 格式错误' }) }
  const type = req.url.split('/').at(-1)
  record({ type, requestNo: body.requestNo, serialNumber: body.serialNumber, fields: Object.keys(body) })
  if (type === 'C9004') {
    if (req.headers.merchantid !== 'HUARONG_LOCAL_ONLY' || body.signature !== 'LOCAL_MOCK_SIGNATURE') return send({ code: 'B0001', message: '仅接受本地模拟商户' })
    for (const name of ['caFile', 'idCardFile', 'otherAuthFile', 'otherFile']) {
      if (!body[name]) return send({ code: '0003', message: `缺少 ${name}` })
      const bytes = Buffer.from(body[name], 'base64')
      if (bytes.length > 307200 || (name === 'caFile' && !bytes.subarray(0, 5).equals(Buffer.from('%PDF-')))
          || (name === 'otherFile' && !bytes.subarray(0, 2).equals(Buffer.from('PK')))) return send({ code: '0003', message: `${name} 文件编码不正确` })
    }
    const serial = 'LOCAL_' + body.requestNo
    tasks.set(serial, { requestNo: body.requestNo, polls: 0 })
    return send({ code: '0000', message: '模拟授权已受理', data: { serialNumber: serial } })
  }
  const task = tasks.get(body.serialNumber)
  if (!task) return send({ code: '0003', message: '模拟授权流水不存在' })
  if (type === 'C9005') {
    task.polls++
    if (task.requestNo.includes('FAIL')) return send({ code: 'B0040', message: '模拟授权失败，不能进入报告查询' })
    if (task.polls < 3) return send({ code: task.polls === 1 ? 'B0013' : 'B0015', message: '模拟报告处理中' })
    return send({ code: '0000', message: '模拟报告已就绪', data: { serialNumber: body.serialNumber, reportStatus: task.requestNo.includes('WHITE') ? '02' : '01' } })
  }
  if (type === 'R9005') {
    if (task.polls < 3) return send({ code: 'B0013', message: '模拟报告未就绪' })
    const result = structuredClone(sample); result.data.requestNo = body.requestNo
    return send(result)
  }
  res.statusCode = 404; send({ code: '0003', message: '未定义的本地模拟接口' })
})
server.listen(28082, '127.0.0.1', () => console.log(`Huarong mock PID=${process.pid}, port=28082, timeout=1800000ms`))
const timer = setTimeout(() => server.close(), 1800000)
process.on('SIGINT', () => { clearTimeout(timer); server.close() })
process.on('SIGTERM', () => { clearTimeout(timer); server.close() })
