import fs from 'node:fs'
import path from 'node:path'
import { analyzeSqlQuery, validateReadOnlyQuery } from '@/utils/sqlQuery'

const cases = JSON.parse(fs.readFileSync(path.resolve(__dirname, '../../../../rule-engine-server/src/test/resources/sql/read-only-cases.json'), 'utf8'))
test.each(cases)('前后端同一 SQL 契约：$sql', ({ sql, valid, params }) => {
  expect(validateReadOnlyQuery(sql) === '').toBe(valid)
  expect(analyzeSqlQuery(sql).placeholderCount).toBe(params)
})
