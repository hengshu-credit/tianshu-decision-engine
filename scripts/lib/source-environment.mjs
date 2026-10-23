import { existsSync, readFileSync } from 'node:fs'
import path from 'node:path'
import { parseEnv } from 'node:util'

export function loadSourceEnvironment(root, service, inherited = process.env) {
  const values = {}
  for (const name of [`.env.${service}`]) {
    const file = path.join(root, name)
    if (existsSync(file)) Object.assign(values, parseEnv(readFileSync(file, 'utf8')))
  }
  return { ...values, ...inherited }
}
