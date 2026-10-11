import { parseDesignerModelJson } from '@/utils/designerModelJson'

const big = '9007199254740993'

test('引用、旧字段、函数、规则版本、名单和脚本绑定保留原始大ID', () => {
  const model = parseDesignerModelJson(`{"conditions":[{"kind":"REFERENCE","refId":${big}},{"kind":"PATH","refId":${big}},{"_varId":${big},"leftVarId":${big},"rightVarId":${big}}],"actions":[{"type":"rule-call","ruleId":${big},"versionBindingId":${big}},{"type":"func-call","functionId":${big}}],"operand":{"kind":"FUNCTION","functionId":${big},"args":[{"kind":"LIST_QUERY","listIds":[7,${big}]}]},"scriptVarRefs":[{"varId":${big},"refType":"VARIABLE"}]}`)
  expect(model.conditions.map(item => item.refId || item._varId)).toEqual([big, big, big])
  expect(model.conditions[2]).toEqual({ _varId: big, leftVarId: big, rightVarId: big })
  expect(model.actions[0]).toMatchObject({ ruleId: big, versionBindingId: big })
  expect(model.actions[1].functionId).toBe(big)
  expect(model.operand.functionId).toBe(big)
  expect(model.operand.args[0].listIds).toEqual([7, big])
  expect(model.scriptVarRefs[0].varId).toBe(big)
})

test('固定对象、普通业务id、图节点id和脚本不误认为资源引用', () => {
  const json = `{"id":${big},"script":"ruleId = ${big}","literal":{"kind":"LITERAL","value":{"kind":"REFERENCE","refId":${big},"_varId":${big}}},"defaultValue":{"_varId":${big}},"node":{"id":${big}},"business":{"refId":${big},"customerId":${big}},"safe":{"kind":"REFERENCE","refId":7}}`
  expect(parseDesignerModelJson(json)).toEqual(JSON.parse(json))
})

test.each(['{"kind":"REFERENCE","refId":9223372036854775808}', '{"kind":"REFERENCE","refId":9.007199254740993e15}', '{"kind":"REFERENCE","refId":1,}'])('拒绝不可验证引用或畸形JSON %s', json => {
  expect(() => parseDesignerModelJson(json)).toThrow()
})
