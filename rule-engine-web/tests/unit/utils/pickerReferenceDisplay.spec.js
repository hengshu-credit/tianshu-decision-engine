import { pickerReferenceDisplay } from '@/utils/pickerReferenceDisplay'
import { arrayLiteralDraft } from '@/utils/literalType'

test('字段显示包含名称、编码、版本和对象后续取值路径', () => {
  const option = { _varId: 7, _refType: 'DATA_OBJECT', varCode: 'report.items', varLabelText: '征信报告/明细', varObj: { version: 3 } }
  const value = { kind: 'PATH', refId: 7, refType: 'DATA_OBJECT', code: 'report.items[0].score', value: 'report.items[0].score', label: '征信报告/明细', relativePath: '[0].score' }
  expect(pickerReferenceDisplay(value, [option])).toContain('征信报告/明细 report.items v3 [0].score')
})

test('当前 API 上下文不虚构版本信息', () => {
  expect(pickerReferenceDisplay({ kind: 'PATH', refType: 'API_CONTEXT', value: 'response.body.vendor_code', code: 'response.body.vendor_code', label: '供应商编码', protocolPath: true })).toBe('供应商编码 response.body.vendor_code')
})

test('同一 API 的不同字段按后续路径显示，不串到第一个字段', () => {
  const options = ['score', 'state'].map(key => ({ _varId: 12, _refType: 'EXTERNAL_API', varCode: `vendor.body.${key}`, varLabelText: key === 'score' ? '评分' : '状态', relativePath: `body.${key}` }))
  expect(pickerReferenceDisplay({ kind: 'REFERENCE', refType: 'EXTERNAL_API', refId: 12, code: 'vendor.body.state', relativePath: 'body.state' }, options)).toBe('状态 vendor.body.state 最新')
})

test('数据对象版本来自所属对象，历史版本仍保留快照名称', () => {
  const option = { _varId: 7, _refType: 'DATA_OBJECT', varCode: 'input.mobile', varLabelText: '客户/手机', _ref: { objectVersion: 4 } }
  expect(pickerReferenceDisplay({ kind: 'REFERENCE', refId: 7, refType: 'DATA_OBJECT', code: 'input.mobile' }, [option])).toBe('客户/手机 input.mobile v4')
  expect(pickerReferenceDisplay({ kind: 'REFERENCE', refId: 7, refType: 'DATA_OBJECT', code: 'old.mobile', label: '旧手机', versionMode: 'FIXED', version: 2 }, [option])).toBe('旧手机 old.mobile v2')
})

test('新建数组空槽位进入输入阈值草稿', () => {
  expect(arrayLiteralDraft({ kind: 'ARRAY', valueType: 'LIST', items: [null] })).toEqual({ kind: 'LITERAL', valueType: 'LIST', value: '[]' })
})
