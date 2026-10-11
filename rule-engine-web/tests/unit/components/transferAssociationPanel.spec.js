import { mount } from '@test-utils'
import TransferAssociationPanel from '@/components/transfer/TransferAssociationPanel.vue'

describe('TransferAssociationPanel', () => {
  test('相邻大数字字符串ID不被合并，推荐、资源和字段关联保留原类型', async () => {
    const first = '9007199254740992', second = '9007199254740993', fieldId = '9007199254740995'
    const association = { referenceKey: 'object-field', targetKey: 'DATA_OBJECT:7', targetResourceType: 'DATA_OBJECT', childPath: '/fields/0/id', suggestedTargetId: first,
      candidates: [{ id: first, name: '同名对象', fields: [{ id: 501, name: '旧字段' }] }, { id: second, name: '同名对象', fields: [{ id: fieldId, name: '目标字段' }] }] }
    const wrapper = mount(TransferAssociationPanel, { props: { associations: [association], resourceBindings: { 'DATA_OBJECT:7': second }, fieldBindings: {} } })
    try {
      expect.soft(wrapper.vm.candidateLabel(association.candidates[0], association)).toContain('推荐')
      expect.soft(wrapper.vm.candidateLabel(association.candidates[1], association)).not.toContain('推荐')
      expect.soft(wrapper.vm.fieldsFor(association)).toEqual([{ id: fieldId, name: '目标字段' }])
      const selects = wrapper.findAllComponents({ name: 'ElSelect' })
      selects[0].vm.$emit('change', second)
      selects[1].vm.$emit('change', fieldId)
      await wrapper.vm.$nextTick()
      expect.soft(wrapper.emitted('update:resourceBindings').at(-1)[0]).toEqual({ 'DATA_OBJECT:7': second })
      expect.soft(wrapper.emitted('update:fieldBindings').at(-1)[0]).toEqual({ 'object-field': fieldId })
      expect(wrapper.emitted('search').at(-1)[0].targetResourceId).toBe(second)
    } finally { wrapper.unmount() }
  })

  test('同编码候选和数据对象字段选择都会写入稳定 ID', async () => {
    const wrapper = mount(TransferAssociationPanel, {
      props: {
        associations: [{
          referenceKey: 'RULE:1|/content/@json/field|DATA_OBJECT:7|/fields/0/id',
          targetKey: 'DATA_OBJECT:7',
          targetResourceType: 'DATA_OBJECT',
          targetCode: 'customer',
          targetName: '客户对象',
          path: '/content/@json/field',
          childPath: '/fields/0/id',
          suggestedTargetId: 101,
          candidates: [{ id: 101, code: 'customer', name: '客户对象', fields: [{ id: 501, varCode: 'age', varType: 'NUMBER' }] }],
        }],
        resourceBindings: {},
        fieldBindings: {},
      },
    })

    expect(wrapper.text()).toContain('同编码推荐')
    const selects = wrapper.findAllComponents({ name: 'ElSelect' })
    selects[0].vm.$emit('change', 101)
    await wrapper.vm.$nextTick()
    selects[1].vm.$emit('change', 501)

    expect(wrapper.emitted('update:resourceBindings').at(-1)[0]).toEqual({ 'DATA_OBJECT:7': 101 })
    expect(wrapper.emitted('update:fieldBindings').at(-1)[0]).toEqual({
      'RULE:1|/content/@json/field|DATA_OBJECT:7|/fields/0/id': 501,
    })
  })
})
