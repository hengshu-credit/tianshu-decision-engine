import { shallowMount } from '@test-utils'
import ActionSideEffectEditor from '@/components/flow/ActionSideEffectEditor.vue'
import { newBlock } from '@/utils/actionDataCodegen'

describe('外数与名单动作面板', () => {
  const field = (id, varType) => ({ id, refType: 'VARIABLE', varType, varCode: 'value' + id })
  function mount(type) {
    const editor = { vars: [field(1, 'STRING'), field(2, 'OBJECT'), field(3, 'MAP'), field(4, 'LIST')], selectedVars: [field(2, 'OBJECT')], writeKinds: ['REFERENCE', 'PATH'], apiOptions: [], listOptions: [], functions: [], sync: vi.fn(), setOperand: vi.fn() }
    const block = newBlock(type)
    return { wrapper: shallowMount(ActionSideEffectEditor, { props: { block }, global: { provide: { actionEditor: editor } } }), block, editor }
  }
  test('完整外数赋值仅提供对象目标，已选内容不扩展为所有字段', () => {
    const { wrapper } = mount('api-call')
    expect(wrapper.vm.objectTargets.map(item => item.id)).toEqual([2, 3])
    expect(wrapper.vm.selectedTargets.map(item => item.id)).toEqual([2])
    wrapper.unmount()
  })
  test('名单内容通过内容选择器增加并可分别删除，不把对象或集合作为内容', () => {
    const { wrapper, block, editor } = mount('list-write')
    expect(wrapper.vm.contentFields.map(item => item.id)).toEqual([1])
    wrapper.vm.addEntry()
    block.entries[0] = { itemType: 'EMAIL', valueOperand: { kind: 'REFERENCE', refId: 11, refType: 'VARIABLE' } }
    wrapper.vm.removeEntry(1)
    expect(block.entries).toEqual([{ itemType: 'EMAIL', valueOperand: { kind: 'REFERENCE', refId: 11, refType: 'VARIABLE' } }])
    expect(editor.sync).toHaveBeenCalledTimes(2)
    wrapper.unmount()
  })
})
