import { matchPathContent } from '@/utils/pathContentMatch'
import { pickerReferenceDisplay } from '@/utils/pickerReferenceDisplay'

const root = { _varId: '9007199254740993', _refType: 'DATA_OBJECT_ROOT', varCode: 'report', varLabelText: '报告', varType: 'OBJECT', _ref: { objectId: '9007199254740993', objectVersion: 3 } }
const fields = [root, { _varId: '8', _refType: 'DATA_OBJECT', varCode: 'report.customer', varLabelText: '客户', varType: 'OBJECT', _ref: root._ref },
  { _varId: '9', _refType: 'DATA_OBJECT', varCode: 'report.customer.score', varLabelText: '信用分', varType: 'NUMBER', _ref: root._ref }]
const value = { kind: 'PATH', refId: root._varId, refType: 'DATA_OBJECT_ROOT', anchorPath: 'report', code: 'report.customer.score.detail', relativePath: '.customer.score.detail', versionMode: 'LATEST' }

test('常量根路径仍展示常量值，不虚构版本', () => {
  expect(pickerReferenceDisplay({ kind: 'PATH', refId: '1', refType: 'CONSTANT', code: 'ZERO', anchorPath: 'ZERO', label: '零', constantValue: 0 })).toBe('零 ZERO = 0')
})

test('最长字段前缀显示名称编码版本与剩余路径，不改写来源 ID', () => {
  const before = JSON.stringify(value)
  expect(matchPathContent(value, fields)).toMatchObject({ label: '信用分', code: 'report.customer.score', suffix: '.detail', version: '最新·v3' })
  expect(pickerReferenceDisplay(value, fields)).toBe('信用分 report.customer.score 最新·v3 [.detail]')
  expect(JSON.stringify(value)).toBe(before)
})
test('同名其他对象、模型和较长字符串前缀不会串引用', () => {
  const other = { ...fields[2], _ref: { objectId: 'other', objectVersion: 99 } }
  expect(matchPathContent(value, [root, fields[1], other]).code).toBe('report.customer')
  expect(matchPathContent({ ...value, code: 'report.customers[0]' }, fields).code).toBe('report')
  expect(matchPathContent(value, [root, { ...fields[2], _refType: 'MODEL_OUTPUT', _ref: { modelId: root._varId } }]).code).toBe('report')
})
test('双引号键与点路径按真实键比较，不拆开包含点号的键名', () => {
  expect(matchPathContent({ ...value, code: 'report["customer"].score[0].label' }, fields)).toMatchObject({ code: 'report.customer.score', suffix: '[0].label' })
  expect(matchPathContent({ ...value, code: 'report["customer.score"].label' }, fields).code).toBe('report')
})
test('固定版本只使用同一版本目录，绝不套用最新版本名称', () => {
  const fixed = { ...value, versionMode: 'FIXED', versionId: '31', version: 2, label: '旧报告' }
  expect(matchPathContent(fixed, fields)).toMatchObject({ label: '旧报告', code: 'report', version: 'v2' })
  const historical = fields.map(item => ({ ...item, versionMode: 'FIXED', versionId: '31', version: 2 }))
  expect(matchPathContent(fixed, historical)).toMatchObject({ label: '信用分', code: 'report.customer.score', version: 'v2' })
})
test('固定版本匹配摘要在重新打开时保留，编辑到其他分支时不沿用旧匹配', () => {
  const fixed = { ...value, versionMode: 'FIXED', versionId: '31', version: 2, label: '旧报告' }
  const historical = fields.map(item => ({ ...item, versionMode: 'FIXED', versionId: '31', version: 2 }))
  const shown = matchPathContent(fixed, historical)
  expect(matchPathContent({ ...fixed, pathDisplay: shown }, fields)).toMatchObject({ code: 'report.customer.score', version: 'v2' })
  expect(matchPathContent({ ...fixed, code: 'report.other', pathDisplay: shown }, fields).code).toBe('report')
})
test('固定业务版本显示同版本目录返回的当前修订号', () => {
  const fixed = { ...value, versionMode: 'FIXED', versionId: '31', versionSource: 'BUSINESS_VERSION', version: 4, displayVersion: 2 }
  const historical = fields.map(item => ({ ...item, versionMode: 'FIXED', versionId: '31', versionSource: 'BUSINESS_VERSION', version: 7, displayVersion: 2 }))
  expect(matchPathContent(fixed, historical).version).toBe('v2·记录7')
})
test('API 与模型按所属 ID 匹配，完全匹配不留下空后缀', () => {
  for (const type of ['MODEL', 'EXTERNAL_API']) {
    const path = { ...value, refType: type, refId: '20', code: 'result.score', anchorPath: 'result' }
    const options = [{ _varId: '20', _refType: type, varCode: 'result', varLabelText: '结果' },
      { _varId: type === 'MODEL' ? '21' : '20', _refType: type === 'MODEL' ? 'MODEL_OUTPUT' : type, varCode: 'result.score', varLabelText: '评分', relativePath: 'score', _ref: { modelId: '20', modelVersion: 5 } }]
    expect(matchPathContent(path, options)).toMatchObject({ code: 'result.score', label: '评分', suffix: '' })
  }
})
