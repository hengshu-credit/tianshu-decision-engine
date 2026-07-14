import { shallowMount } from '@vue/test-utils'
import ExpressionPalette from '@/components/expression/ExpressionPalette.vue'

function variable(id, code, category = 'standalone', refType = 'VARIABLE') {
  return {
    id,
    _varId: id,
    _refType: refType,
    varCode: code,
    varLabel: code,
    varType: 'NUMBER',
    _ref: { category, refType }
  }
}

function mountPalette(propsData = {}) {
  return shallowMount(ExpressionPalette, {
    propsData: {
      allowedKinds: ['LITERAL', 'PATH', 'REFERENCE', 'FUNCTION', 'OPERATION', 'ACCESS', 'CAST', 'ARRAY', 'LIST_QUERY'],
      vars: [],
      functions: [],
      ...propsData
    },
    stubs: {
      'el-input': true,
      'el-pagination': true,
      'el-button': true
    }
  })
}

describe('ExpressionPalette', () => {
  test('名单上下文仅展示名单查询并插入完整默认配置', async () => {
    const wrapper = shallowMount(ExpressionPalette, {
      propsData: { allowedKinds: ['LIST_QUERY'] },
      stubs: { 'el-input': true }
    })

    const button = wrapper.find('.palette-list-query')
    expect(button.exists()).toBe(true)
    await button.trigger('click')

    expect(wrapper.emitted().insert[0][0]).toEqual({
      kind: 'LIST_QUERY',
      listIds: [],
      itemTypes: [],
      combinationMode: 'ANY_FIELD_ANY_LIST',
      matchMode: 'IN_LIST',
      valueType: 'BOOLEAN'
    })
    expect(wrapper.find('.palette-grid').exists()).toBe(false)
  })

  test('双栏分类数量独立于搜索且默认定位普通变量', async () => {
    const wrapper = mountPalette({
      vars: [
        variable(1, 'age'),
        variable(2, 'ONE', 'constant', 'CONSTANT')
      ],
      functions: [{ id: 7, funcCode: 'numMax', funcName: '最大值' }]
    })

    expect(wrapper.findAll('.palette-category')).toHaveLength(9)
    expect(wrapper.find('.palette-category--active').text()).toContain('普通变量')
    expect(wrapper.vm.categories.find(item => item.key === 'standalone').count).toBe(1)

    wrapper.vm.keyword = 'not-found'
    await wrapper.vm.$nextTick()

    expect(wrapper.vm.categories.find(item => item.key === 'standalone').count).toBe(1)
    expect(wrapper.vm.filteredItems).toEqual([])
  })

  test('大量字段分页但分类总数保持完整', () => {
    const vars = Array.from({ length: 120 }, (_, index) => variable(index + 1, `field_${index}`))
    const wrapper = mountPalette({ vars })

    expect(wrapper.vm.activeItems).toHaveLength(120)
    expect(wrapper.vm.pagedItems).toHaveLength(50)
    expect(wrapper.vm.categories.find(item => item.key === 'standalone').count).toBe(120)
  })

  test('切换分类会清空搜索并回到第一页', () => {
    const wrapper = mountPalette({ vars: [variable(1, 'age')] })
    wrapper.vm.keyword = 'age'
    wrapper.vm.page = 3

    wrapper.vm.selectCategory('function')

    expect(wrapper.vm.activeCategory).toBe('function')
    expect(wrapper.vm.keyword).toBe('')
    expect(wrapper.vm.page).toBe(1)
  })

  test('手输唯一路径反解为带稳定ID的PATH', () => {
    const wrapper = mountPalette({
      vars: [variable(12, 'request.customer.age', 'object', 'DATA_OBJECT')]
    })
    wrapper.vm.manualKind = 'PATH'
    wrapper.vm.manualValue = 'request.customer.age'

    wrapper.vm.confirmManual()

    expect(wrapper.emitted().insert[0][0]).toMatchObject({
      kind: 'PATH',
      refId: 12,
      refType: 'DATA_OBJECT',
      resolved: true
    })
  })

  test('同路径多候选必须明确选择', () => {
    const wrapper = mountPalette({
      vars: [
        variable(1, 'score'),
        variable(2, 'score', 'model', 'MODEL_OUTPUT')
      ]
    })
    wrapper.vm.manualKind = 'PATH'
    wrapper.vm.manualValue = 'score'

    wrapper.vm.confirmManual()

    expect(wrapper.emitted().insert).toBeUndefined()
    expect(wrapper.vm.pathCandidates).toHaveLength(2)

    wrapper.vm.confirmPathCandidate(wrapper.vm.pathCandidates[1])
    expect(wrapper.emitted().insert[0][0]).toMatchObject({
      kind: 'PATH',
      refId: 2,
      refType: 'MODEL_OUTPUT',
      resolved: true
    })
  })

  test('无法反解的手输路径原样保留', () => {
    const wrapper = mountPalette()
    wrapper.vm.manualKind = 'PATH'
    wrapper.vm.manualValue = 'payload.external_score'

    wrapper.vm.confirmManual()

    expect(wrapper.emitted().insert[0][0]).toMatchObject({
      kind: 'PATH',
      value: 'payload.external_score',
      resolved: false
    })
  })
})
