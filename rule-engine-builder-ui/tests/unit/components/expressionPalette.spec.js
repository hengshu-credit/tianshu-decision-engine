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

function objectField(id, objectCode, objectLabel, fieldCode, fieldLabel) {
  return {
    id,
    _varId: id,
    _refType: 'DATA_OBJECT',
    varCode: `${objectCode}.${fieldCode}`,
    varLabel: `${objectLabel}/${fieldLabel}`,
    varLabelText: `${objectLabel}/${fieldLabel}`,
    varType: 'NUMBER',
    _ref: { category: 'object', refType: 'DATA_OBJECT', objectCode, objectLabel }
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

  test('切换分类保留同一个搜索词并回到第一页', () => {
    const wrapper = mountPalette({ vars: [variable(1, 'age')], functions: [{ id: 2, funcCode: 'ageOf' }] })
    wrapper.vm.keyword = 'age'
    wrapper.vm.page = 3

    wrapper.vm.selectCategory('function')

    expect(wrapper.vm.activeCategory).toBe('function')
    expect(wrapper.vm.keyword).toBe('age')
    expect(wrapper.vm.page).toBe(1)
  })

  test('函数带描述时仍可按函数编码搜索', () => {
    const wrapper = mountPalette({
      functions: [{
        id: 171,
        funcCode: 'imageToBase64',
        funcName: '图片转 Base64',
        description: '自动识别 Base64 或 URL'
      }]
    })

    wrapper.vm.selectCategory('function')
    wrapper.vm.keyword = 'imageToBase64'

    expect(wrapper.vm.filteredItems).toHaveLength(1)
    expect(wrapper.vm.filteredItems[0].funcCode).toBe('imageToBase64')
  })

  test('所有资源分类都展示搜索框且静态资源也参与过滤', async() => {
    const wrapper = mountPalette()

    for (const category of ['manual', 'list', 'operation', 'transform']) {
      wrapper.vm.selectCategory(category)
      await wrapper.vm.$nextTick()
      expect(wrapper.find('.palette-search').exists()).toBe(true)
    }

    wrapper.vm.selectCategory('operation')
    wrapper.vm.keyword = '&&'
    expect(wrapper.vm.filteredItems.map(item => item.value)).toEqual(['&&'])

    wrapper.vm.selectCategory('manual')
    wrapper.vm.keyword = '路径'
    expect(wrapper.vm.filteredItems.map(item => item.key)).toEqual(['PATH'])
  })

  test('数据对象先展示对象行，展开后点击子字段才插入稳定引用', async() => {
    const wrapper = mountPalette({
      vars: [
        objectField(11, 'LoanApply', '借款申请', 'amount', '申请金额'),
        objectField(12, 'LoanApply', '借款申请', 'term', '期限'),
        objectField(13, 'Profile', '客户画像', 'age', '年龄')
      ]
    })
    wrapper.vm.selectCategory('object')
    await wrapper.vm.$nextTick()

    expect(wrapper.findAll('.palette-reference-group')).toHaveLength(2)
    await wrapper.find('.palette-reference-group').trigger('click')
    expect(wrapper.emitted().insert).toBeUndefined()
    expect(wrapper.findAll('.palette-reference-child')).toHaveLength(2)

    await wrapper.find('.palette-reference-child').trigger('click')
    expect(wrapper.emitted().insert[0][0]).toMatchObject({
      kind: 'REFERENCE',
      refId: 11,
      refType: 'DATA_OBJECT'
    })
  })

  test('搜索只命中一个对象时自动展开匹配字段', async() => {
    const wrapper = mountPalette({
      vars: [
        objectField(11, 'LoanApply', '借款申请', 'amount', '申请金额'),
        objectField(13, 'Profile', '客户画像', 'age', '年龄')
      ]
    })
    wrapper.vm.selectCategory('object')
    wrapper.vm.keyword = 'amount'
    await wrapper.vm.$nextTick()

    expect(wrapper.vm.filteredItems).toHaveLength(1)
    expect(wrapper.vm.expandedGroupKey).toBe('object:LoanApply')
    expect(wrapper.findAll('.palette-reference-child')).toHaveLength(1)
  })

  test('手输资源只创建空节点且不显示资源区输入框', async () => {
    const wrapper = mountPalette({ expectedType: 'NUMBER' })
    wrapper.vm.selectCategory('manual')
    await wrapper.vm.$nextTick()

    expect(wrapper.find('.palette-manual-editor').exists()).toBe(false)

    wrapper.vm.insertManual('LITERAL')
    wrapper.vm.insertManual('PATH')
    expect(wrapper.emitted().insert[0][0]).toEqual({ kind: 'LITERAL', value: '', valueType: 'NUMBER' })
    expect(wrapper.emitted().insert[1][0]).toMatchObject({ kind: 'PATH', value: '', resolved: false })
  })

  test('字段结果使用类型编码名称三列展示', () => {
    const wrapper = mountPalette({ vars: [variable(1, 'customer.extremely_long_score')] })

    expect(wrapper.findAll('.palette-reference-table th')).toHaveLength(3)
    expect(wrapper.find('.palette-reference-code').text()).toBe('customer.extremely_long_score')
  })

  test('分类栏和内容栏宽度分别受到上下限约束', () => {
    const wrapper = mountPalette()
    wrapper.vm.resizeColumn('category', -1000)
    expect(wrapper.vm.categoryWidth).toBe(128)
    wrapper.vm.resizeColumn('category', 1000)
    expect(wrapper.vm.categoryWidth).toBe(240)
    wrapper.vm.resizeColumn('content', -1000)
    expect(wrapper.vm.contentWidth).toBe(280)
    wrapper.vm.resizeColumn('content', 1000)
    expect(wrapper.vm.contentWidth).toBeLessThanOrEqual(640)
  })
})
