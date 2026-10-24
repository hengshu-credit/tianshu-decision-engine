import ScriptEditor from '@/views/designer/ScriptEditor.vue'
import { collectQlScriptReferences } from '@/utils/qlScriptReferences'
import { listFieldVersions } from '@/api/fieldPickerVersion'

vi.mock('@/api/fieldPickerVersion', () => ({ listFieldVersions: vi.fn() }))

function context() {
  const options = ['customer.name', 'customer.age'].map((varCode, index) => ({
    varCode, varLabelText: index ? '年龄' : '姓名', _varId: String(index + 1),
    _refType: 'DATA_OBJECT', objectId: '10',
  }))
  return {
    showEngineReferences: true, referenceCatalogMode: 'LIVE', referenceVersions: {}, referenceVersionRequest: 0,
    scriptReferences: collectQlScriptReferences('customer.name + customer.age', options),
  }
}

beforeEach(() => vi.clearAllMocks())

test('同一对象多个字段只读取一次版本，重复编辑复用结果', async () => {
  listFieldVersions.mockResolvedValue({ data: [{ version: 5 }] })
  const view = context()
  await ScriptEditor.methods.loadReferenceVersions.call(view)
  await ScriptEditor.methods.loadReferenceVersions.call(view)
  expect(listFieldVersions).toHaveBeenCalledTimes(1)
  expect(listFieldVersions).toHaveBeenCalledWith(expect.objectContaining({ ownerId: '10', module: 'dataobject' }))
  expect(view.referenceVersions).toEqual({ 'dataobject:10': { version: 5 } })
})

test('冻结历史目录绝不读取当前资源版本', async () => {
  const view = { ...context(), referenceCatalogMode: 'FROZEN' }
  await ScriptEditor.methods.loadReferenceVersions.call(view)
  expect(listFieldVersions).not.toHaveBeenCalled()
})

test('切换版本后的旧请求不覆盖新目录，失败可显示明确状态', async () => {
  let resolve
  listFieldVersions.mockImplementation(() => new Promise(done => { resolve = done }))
  const view = context()
  const pending = ScriptEditor.methods.loadReferenceVersions.call(view)
  view.referenceVersionRequest++
  view.referenceVersions = {}
  resolve({ data: [{ version: 99 }] })
  await pending
  expect(view.referenceVersions).toEqual({})
  listFieldVersions.mockRejectedValue(new Error('offline'))
  await ScriptEditor.methods.loadReferenceVersions.call(view)
  expect(view.referenceVersions).toEqual({ 'dataobject:10': { error: true } })
})

test('关闭展示仅移除装饰，不替换源码、不触发编辑或保存', () => {
  const view = context()
  view.monacoEditor = {
    getModel: () => ({ getPositionAt: offset => ({ lineNumber: 1, column: offset + 1 }) }),
    deltaDecorations: vi.fn().mockReturnValue(['decoration']),
    executeEdits: vi.fn(), setValue: vi.fn(),
  }
  view.referenceDecorationIds = []
  ScriptEditor.methods.refreshReferenceDecorations.call(view)
  expect(view.monacoEditor.deltaDecorations.mock.calls[0][1]).toHaveLength(10)
  view.showEngineReferences = false
  ScriptEditor.methods.refreshReferenceDecorations.call(view)
  expect(view.monacoEditor.deltaDecorations).toHaveBeenLastCalledWith(['decoration'], [])
  expect(view.monacoEditor.executeEdits).not.toHaveBeenCalled()
  expect(view.monacoEditor.setValue).not.toHaveBeenCalled()
})
