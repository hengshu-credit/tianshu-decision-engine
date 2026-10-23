import fs from 'node:fs'
import { fileURLToPath } from 'node:url'

const source = new URL('../rule-engine-model/src/main/java/com/hengshucredit/rule/model/PlatformCode.java', import.meta.url)
const target = new URL('../rule-engine-web/src/constants/platformResponseCodes.json', import.meta.url)
const entries = [...fs.readFileSync(source, 'utf8').matchAll(/^    ([A-Z_]+)\("(\d{6})", "([^"]+)", (\d+)\)/gm)]
  .map(([, name, code, message, status]) => ({ name, code, message, httpStatus: Number(status) }))
if (!entries.length || new Set(entries.map(entry => entry.code)).size !== entries.length) throw new Error('平台结果码字典为空或重复')
const output = JSON.stringify(entries, null, 2) + '\n'
if (process.argv.includes('--check')) {
  if (fs.readFileSync(target, 'utf8') !== output) throw new Error('平台结果码已变化，请执行 node scripts/generate-platform-contract.mjs')
} else {
  fs.writeFileSync(target, output)
  console.log(`Generated ${entries.length} platform codes: ${fileURLToPath(target)}`)
}
