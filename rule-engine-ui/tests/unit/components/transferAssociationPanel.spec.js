import { mount } from '@test-utils'
import TransferAssociationPanel from '@/components/transfer/TransferAssociationPanel.vue'

describe('TransferAssociationPanel', () => {
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
