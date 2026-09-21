import { shallowMount } from '@test-utils'
import { nextTick } from 'vue'

const VarPicker = (await vi.importActual('../../../src/components/common/VarPicker.vue')).default

const PopoverStub = {
  name: 'ElPopover',
  data() {
    return { popperElm: null }
  },
  methods: {
    doClose() {},
  },
  template: '<div><slot /><slot name="reference" /></div>',
}

function mountPicker(propsData = {}, options = {}) {
  return shallowMount(VarPicker, {
    props: {
      vars: [],
      ...propsData
    },
    stubs: {
      'el-popover': PopoverStub,
      'el-input': {
        template: '<input />',
        props: ['value']
      },
      'el-tooltip': {
        template: '<span><slot /></span>'
      },
      'el-button': {
        template: '<button><slot /></button>'
      },
      'el-pagination': {
        template: '<div />'
      }
    },
    ...options,
  })
}

function objectFieldOptions() {
  return [
    {
      varCode: 'amount',
      varLabel: '申请金额',
      varType: 'NUMBER',
      varObj: { id: 101, varCode: 'amount' },
      _ref: { category: 'object', objectCode: 'LoanApply', objectLabel: '贷款申请' }
    },
    {
      varCode: 'age',
      varLabel: '年龄',
      varType: 'INTEGER',
      varObj: { id: 102, varCode: 'age' },
      _ref: { category: 'object', objectCode: 'LoanApply', objectLabel: '贷款申请' }
    }
  ]
}

function modelFieldOptions() {
  return [
    {
      varCode: 'creditModel.score',
      varLabel: '信用模型/评分 creditModel.score',
      varType: 'NUMBER',
      _ref: { category: 'model', refType: 'MODEL_OUTPUT', modelCode: 'creditModel', modelLabel: '信用模型' }
    },
    {
      varCode: 'creditModel.level',
      varLabel: '信用模型/等级 creditModel.level',
      varType: 'STRING',
      _ref: { category: 'model', refType: 'MODEL_OUTPUT', modelCode: 'creditModel', modelLabel: '信用模型' }
    },
    {
      varCode: 'fraudModel.hit',
      varLabel: '反欺诈模型/命中 fraudModel.hit',
      varType: 'BOOLEAN',
      _ref: { category: 'model', refType: 'MODEL_OUTPUT', modelCode: 'fraudModel', modelLabel: '反欺诈模型' }
    }
  ]
}

function standaloneOptions(count = 3) {
  const list = []
  for (let i = 1; i <= count; i++) {
    list.push({
      id: i,
      varCode: 'v' + i,
      varLabel: '变量' + i,
      varType: 'STRING',
      varObj: { id: i, refType: 'VARIABLE' },
      _varId: i,
      _refType: 'VARIABLE',
      _ref: { category: 'standalone', refType: 'VARIABLE' }
    })
  }
  return list
}

describe('VarPicker', () => {
  test('操作数触发器直接使用 Element 输入框并交给前缀流式布局', () => {
    const wrapper = mountPicker({
      operandMode: true,
      value: {
        kind: 'REFERENCE', code: 'amount', valueType: 'NUMBER', refId: 1,
        refType: 'VARIABLE', resolved: true
      },
      vars: standaloneOptions(1)
    })

    expect(wrapper.vm.operandKindMetaValue.label).toBe('变量')
    expect(wrapper.get('.vp-reference').element.tagName).toBe('INPUT')
    expect(wrapper.find('div.vp-reference').exists()).toBe(false)
    expect(wrapper.vm.referenceStyle).toBeUndefined()
  })

  test('同编码但不同 ID 的字段使用独立行 key', async () => {
    const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
    const vars = standaloneOptions(2).map((item, index) => ({
      ...item,
      varCode: 'amount',
      varLabel: index === 0 ? '全局金额' : '项目计算额度'
    }))

    const wrapper = mountPicker({ vars })
    await nextTick()

    expect(wrapper.findAll('.vp-row')).toHaveLength(2)
    expect(errorSpy.mock.calls.some(args => args.join(' ').includes("Duplicate keys detected: 'amount'"))).toBe(false)
    errorSpy.mockRestore()
  })

  test('可写操作数只展示变量和数据对象字段', () => {
    const wrapper = mountPicker({
      operandMode: true,
      writableOnly: true,
      allowedKinds: ['PATH', 'REFERENCE'],
      vars: [
        ...standaloneOptions(1),
        ...objectFieldOptions(),
        ...modelFieldOptions(),
        { varCode: 'LIMIT', varLabel: '阈值', _refType: 'CONSTANT', _ref: { category: 'constant', refType: 'CONSTANT' } }
      ]
    })

    expect(wrapper.vm.categoryList.map(item => item.key)).toEqual(['manual', 'standalone', 'object'])
  })

  test('只有对象字段时自动切换到对象分类', async () => {
    const wrapper = mountPicker({ vars: objectFieldOptions() })
    await nextTick()

    expect(wrapper.vm.categoryList.map(c => c.key)).toEqual(['object'])
    expect(wrapper.vm.activeCategory).toBe('object')
    expect(wrapper.vm.rightItems).toHaveLength(1)
    expect(wrapper.vm.rightItems[0]._objectGroup).toBe(true)
  })

  test('点击对象分组只展开，点击子字段才选择字段', async () => {
    const wrapper = mountPicker({ vars: objectFieldOptions() })
    await nextTick()

    const group = wrapper.vm.rightItems[0]
    wrapper.vm.onItemClick(group)
    expect(wrapper.emitted().input).toBeUndefined()
    expect(wrapper.vm.expandedObject).toBe('LoanApply')

    const child = group.children[0]
    wrapper.vm.onItemClick(child)

    expect(wrapper.emitted().input[0]).toEqual([child.varCode])
    expect(wrapper.emitted().select[0]).toEqual([child])
    expect(wrapper.vm.popoverVisible).toBe(false)
  })

  test('字段选择器表头返回文本而不是函数对象', () => {
    const wrapper = mountPicker({ columnLabels: 'dataObject' })

    expect(typeof wrapper.vm.codeColumnLabel()).toBe('string')
    expect(typeof wrapper.vm.nameColumnLabel()).toBe('string')
  })

  test('数据对象子字段展示对象内相对路径和简短名称', async () => {
    const wrapper = mountPicker({
      vars: [
        {
          varCode: 'request.params.taxpayerType',
          varLabel: 'request/纳税人类型 request.params.taxpayerType',
          varLabelText: 'request/纳税人类型',
          varType: 'STRING',
          _ref: {
            category: 'object',
            objectCode: 'request',
            objectScriptName: 'request',
            objectLabel: 'request'
          }
        }
      ]
    })
    await nextTick()

    const child = wrapper.vm.rightItems[0].children[0]
    expect(wrapper.vm.objectFieldPath(child)).toBe('request.params.taxpayerType')
    expect(wrapper.vm.objectFieldRelativePath(child)).toBe('params.taxpayerType')
    expect(wrapper.vm.objectFieldDisplayName(child)).toBe('纳税人类型')
    wrapper.vm.onCategoryClick('object')
    expect(wrapper.vm.codeColumnLabel()).toBe('字段编码')
  })

  test('模型字段显示明确的数据类型', () => {
    const wrapper = mountPicker({
      vars: modelFieldOptions().slice(0, 1)
    })

    wrapper.vm.onCategoryClick('model')
    expect(wrapper.vm.typeChar('NUMBER')).toBe('i')
    expect(wrapper.vm.typeLabel('NUMBER')).toBe('数值')
    expect(wrapper.vm.codeColumnLabel()).toBe('模型输出字段')
    expect(wrapper.vm.nameColumnLabel()).toBe('输出字段名称')
  })

  test('点击模型分组只展开，点击输出字段才选择字段', async () => {
    const wrapper = mountPicker({ vars: modelFieldOptions() })
    await nextTick()

    wrapper.vm.onCategoryClick('model')
    const group = wrapper.vm.rightItems.find(item => item._modelGroupKey === 'creditModel')
    expect(wrapper.vm.rightItems).toHaveLength(2)
    expect(group._modelGroup).toBe(true)
    expect(group.children.map(child => child.varCode)).toEqual(['creditModel.level', 'creditModel.score'])

    wrapper.vm.onItemClick(group)
    expect(wrapper.emitted().input).toBeUndefined()
    expect(wrapper.vm.expandedObject).toBe('model:creditModel')

    const child = group.children[1]
    wrapper.vm.onItemClick(child)

    expect(wrapper.emitted().input[0]).toEqual([child.varCode])
    expect(wrapper.emitted().select[0]).toEqual([child])
    expect(wrapper.vm.popoverVisible).toBe(false)
  })

  test('传入 selectedVars 后显示已选字段分类并去重', async () => {
    const vars = standaloneOptions(3)
    const wrapper = mountPicker({
      vars,
      selectedVars: [vars[1], 'v2', vars[2]]
    })
    await nextTick()

    expect(wrapper.vm.categoryList[0]).toMatchObject({ key: 'selected', label: '已选字段', count: 2 })

    wrapper.vm.onCategoryClick('selected')
    expect(wrapper.vm.rightItems.map(v => v.varCode)).toEqual(['v2', 'v3'])
  })

  test('已选字段不展示当前作用域不可选的旧引用', async () => {
    const vars = standaloneOptions(1)
    const wrapper = mountPicker({
      vars,
      selectedVars: [vars[0], { varCode: 'projectOnlyVar', varLabel: '项目变量' }]
    })
    await nextTick()

    expect(wrapper.vm.selectedItems.map(v => v.varCode)).toEqual(['v1'])
    expect(wrapper.vm.categoryList[0]).toMatchObject({ key: 'selected', count: 1 })
  })

  test('点击输入框后弹层保持打开，点击弹层内部不关闭', async () => {
    const wrapper = mountPicker({ vars: objectFieldOptions() })
    const popper = document.createElement('div')
    const inside = document.createElement('button')
    popper.appendChild(inside)
    document.body.appendChild(popper)
    wrapper.vm.$refs.popover.popperElm = popper

    wrapper.vm.openPopover()
    await nextTick()
    expect(wrapper.vm.popoverVisible).toBe(true)

    wrapper.vm.onDocumentMouseDown({ target: inside })
    expect(wrapper.vm.popoverVisible).toBe(true)

    document.body.removeChild(popper)
    wrapper.unmount()
  })

  test('字段选择器打开后点击组件外部才关闭', async () => {
    const wrapper = mountPicker({ vars: objectFieldOptions() })
    const outside = document.createElement('div')
    document.body.appendChild(outside)
    const closeSpy = vi.spyOn(wrapper.vm.$refs.popover, 'doClose')

    wrapper.vm.openPopover()
    await nextTick()
    wrapper.vm.onDocumentMouseDown({ target: outside })

    expect(wrapper.vm.popoverVisible).toBe(false)
    expect(closeSpy).toHaveBeenCalled()

    document.body.removeChild(outside)
    wrapper.unmount()
  })

  test('打开已有字段时自动定位到字段所在分页', async () => {
    const wrapper = mountPicker({
      vars: standaloneOptions(150),
      operandMode: true,
      allowedKinds: ['REFERENCE'],
      value: { kind: 'REFERENCE', refId: 99, refType: 'VARIABLE', code: 'v99', value: 'v99' }
    })
    wrapper.vm.$refs.popover.popperElm = document.createElement('div')

    wrapper.vm.openPopover()
    await nextTick()
    await nextTick()

    expect(wrapper.vm.popoverVisible).toBe(true)
    expect(wrapper.vm.activeCategory).toBe('standalone')
    expect(wrapper.vm.rightPage).toBe(2)
  })

  test('输入框输入关键字后按编码和名称筛选字段，前缀命中优先', async () => {
    const wrapper = mountPicker({
      vars: [
        { varCode: 'riskScore', varLabel: '风险分', varType: 'NUMBER', _ref: { category: 'standalone' } },
        { varCode: 'creditScore', varLabel: '信用评分', varType: 'NUMBER', _ref: { category: 'standalone' } },
        { varCode: 'scoreLevel', varLabel: '评分等级', varType: 'STRING', _ref: { category: 'standalone' } }
      ]
    })
    await nextTick()

    wrapper.vm.onReferenceInput('score')

    expect(wrapper.vm.popoverVisible).toBe(true)
    expect(wrapper.vm.filteredRightItems.map(v => v.varCode)).toEqual(['scoreLevel', 'creditScore', 'riskScore'])
  })

  test('搜索切换到命中分类但左侧分类和计数保持稳定', async () => {
    const wrapper = mountPicker({
      vars: [
        { varCode: 'age', varLabel: '年龄', varType: 'NUMBER', _ref: { category: 'standalone' } },
        ...modelFieldOptions().slice(0, 1)
      ]
    })
    await nextTick()

    expect(wrapper.vm.activeCategory).toBe('standalone')

    wrapper.vm.onReferenceInput('credit')
    await nextTick()
    await nextTick()

    expect(wrapper.vm.categoryList.map(c => c.key)).toEqual(['standalone', 'model'])
    expect(wrapper.vm.categoryList.map(c => c.count)).toEqual([1, 1])
    expect(wrapper.vm.activeCategory).toBe('model')
    expect(wrapper.vm.filteredRightItems).toHaveLength(1)
    expect(wrapper.vm.filteredRightItems[0]._modelGroup).toBe(true)
    expect(wrapper.vm.expandedObject).toBe('model:creditModel')
    expect(wrapper.vm.pagedObjectChildren(wrapper.vm.filteredRightItems[0]).map(v => v.varCode)).toEqual(['creditModel.score'])
  })

  test('数据对象分类搜索时只展示命中的子字段并自动展开单个对象组', async () => {
    const wrapper = mountPicker({
      vars: [
        ...objectFieldOptions(),
        {
          varCode: 'homeAddress',
          varLabel: '家庭地址',
          varType: 'STRING',
          varObj: { id: 103, varCode: 'homeAddress' },
          _ref: { category: 'object', objectCode: 'Profile', objectLabel: '客户档案' }
        }
      ]
    })
    await nextTick()

    wrapper.vm.onCategoryClick('object')
    wrapper.vm.onReferenceInput('am')
    await nextTick()
    await nextTick()

    expect(wrapper.vm.filteredRightItems).toHaveLength(1)
    expect(wrapper.vm.expandedObject).toBe('LoanApply')
    expect(wrapper.vm.pagedObjectChildren(wrapper.vm.filteredRightItems[0]).map(v => v.varCode)).toEqual(['amount'])
  })

  test('弹层宽度和面板宽度一致，拖拽宽度不会留下右侧空白', () => {
    const wrapper = mountPicker({ vars: standaloneOptions(1) })

    wrapper.vm.panelWidth = 720

    expect(wrapper.vm.popoverWidth).toBe(720)
    expect(wrapper.vm.panelStyle.width).toBe('720px')
  })

  test('200 个字段搜索前后左侧计数和面板宽度不变化', async () => {
    const wrapper = mountPicker({ vars: standaloneOptions(200) })
    await nextTick()
    const width = wrapper.vm.panelStyle.width

    expect(wrapper.vm.categoryList.find(item => item.key === 'standalone').count).toBe(200)
    wrapper.vm.onReferenceInput('v199')
    await nextTick()

    expect(wrapper.vm.categoryList.find(item => item.key === 'standalone').count).toBe(200)
    expect(wrapper.vm.panelStyle.width).toBe(width)
  })

  test('popover resize supports larger panel bounds', () => {
    const wrapper = mountPicker({ vars: standaloneOptions(1) })
    wrapper.vm.$refs.popover.popperElm = {
      getBoundingClientRect: () => ({ left: 0, top: 0 })
    }

    wrapper.vm.updatePanelSize(2000, 1200)

    expect(wrapper.vm.panelWidth).toBe(1440)
    expect(wrapper.vm.panelHeight).toBe(960)
  })

  test('操作数模式按引用 ID 和类型定位已有字段', async () => {
    const vars = standaloneOptions(150)
    const wrapper = mountPicker({
      operandMode: true,
      allowedKinds: ['LITERAL', 'REFERENCE'],
      vars,
      value: { kind: 'REFERENCE', code: 'oldCode', refId: 99, refType: 'VARIABLE' }
    })
    wrapper.vm.$refs.popover.popperElm = document.createElement('div')

    wrapper.vm.openPopover()
    await nextTick()
    await nextTick()
    await nextTick()

    expect(wrapper.vm.activeCategory).toBe('standalone')
    expect(wrapper.vm.currentValue).toBe('v99')
    expect(wrapper.vm.rightPage).toBe(2)
  })

  test('打开时同步定位，浏览期间不跳回；重开仍定位已配置字段', async () => {
    const wrapper = mountPicker({
      operandMode: true,
      allowedKinds: ['LITERAL', 'REFERENCE'],
      vars: [...standaloneOptions(1), ...objectFieldOptions()],
      value: {
        kind: 'REFERENCE',
        code: 'v1',
        refId: 1,
        refType: 'VARIABLE',
      },
    })
    wrapper.vm.$refs.popover.popperElm = document.createElement('div')
    wrapper.vm.activeCategory = 'object'

    wrapper.vm.openPopover()

    expect(wrapper.vm.activeCategory).toBe('standalone')

    wrapper.vm.onCategoryClick('object')
    const group = wrapper.vm.filteredRightItems[0]
    wrapper.vm.onItemClick(group)
    const groupKey = wrapper.vm.fieldGroupKey(group)
    await nextTick()
    await nextTick()
    await nextTick()

    expect(wrapper.vm.activeCategory).toBe('object')
    expect(wrapper.vm.expandedObject).toBe(groupKey)

    await wrapper.setProps({
      value: {
        kind: 'REFERENCE',
        code: 'v1-renamed',
        label: '同一变量的新展示文本',
        refId: 1,
        refType: 'VARIABLE',
      },
    })
    wrapper.vm.popoverVisible = false
    await nextTick()
    wrapper.vm.openPopover()
    await nextTick()

    expect(wrapper.vm.activeCategory).toBe('standalone')
    expect(wrapper.vm.expandedObject).toBeNull()
  })

  test('同编码字段按 ID 唯一高亮，已配置字段每次打开恢复分页', async () => {
    const vars = standaloneOptions(150).map(v => ({ ...v, varCode: 'sameCode' }))
    const wrapper = mountPicker({ operandMode: true, vars, value: { kind: 'REFERENCE', refId: 149, refType: 'VARIABLE', code: 'sameCode' } })
    wrapper.vm.openPopover()
    await nextTick()
    expect(wrapper.vm.rightPage).toBe(2)
    expect(wrapper.findAll('.vp-row--selected')).toHaveLength(1)
    expect(wrapper.get('.vp-row--selected').text()).toContain('变量149')
    wrapper.vm.onRightPageChange(1)
    wrapper.vm.closePopover()
    await nextTick()
    wrapper.vm.openPopover()
    await nextTick()
    expect(wrapper.vm.rightPage).toBe(2)
    wrapper.unmount()
  })

  test('函数按 ID 定位分页和高亮，不按旧编码误选同名方法，也不重置参数', async () => {
    const functions = Array.from({ length: 130 }, (_, i) => ({ id: i + 1, functionCode: 'same', functionName: `函数${i + 1}` }))
    const value = { kind: 'FUNCTION', functionId: 129, functionCode: 'old', args: [{ kind: 'LITERAL', value: '8' }] }
    const wrapper = mountPicker({ operandMode: true, allowedKinds: ['FUNCTION'], functions, value })
    wrapper.vm.openPopover()
    await nextTick()
    expect(wrapper.vm.activeCategory).toBe('function')
    expect(wrapper.vm.rightPage).toBe(2)
    expect(wrapper.findAll('.vp-row--selected')).toHaveLength(1)
    expect(wrapper.get('.vp-row--selected').text()).toContain('函数129')
    expect(wrapper.emitted().input).toBeUndefined()
    expect(value.args[0].value).toBe('8')
    wrapper.unmount()
  })

  test.each(['object', 'model'])('%s 字段展开所属分组和子字段分页', async category => {
    const refType = category === 'model' ? 'MODEL_OUTPUT' : 'DATA_OBJECT'
    const vars = Array.from({ length: 125 }, (_, i) => ({
      _varId: i + 1, _refType: refType, varCode: `current.f${String(i + 1).padStart(3, '0')}`, varLabel: `字段${i + 1}`,
      _ref: { category, objectCode: 'current', modelCode: 'current', refType },
    }))
    const wrapper = mountPicker({ operandMode: true, vars, value: { kind: 'REFERENCE', refId: 122, refType, code: 'old.name' } })
    wrapper.vm.openPopover()
    await nextTick()
    expect(wrapper.vm.activeCategory).toBe(category)
    expect(wrapper.vm.expandedObject).toBe(category === 'model' ? 'model:current' : 'current')
    expect(wrapper.vm.objectChildPage(wrapper.vm.rightItems[0])).toBe(2)
    expect(wrapper.get('.vp-child-item--selected').text()).toContain('字段122')
    wrapper.unmount()
  })

  test('字段目录延迟加载后补定位，但用户开始浏览后不抢回分类', async () => {
    const value = { kind: 'REFERENCE', refId: 99, refType: 'VARIABLE', code: 'v99' }
    const wrapper = mountPicker({ operandMode: true, vars: [], value })
    wrapper.vm.openPopover()
    await wrapper.setProps({ vars: standaloneOptions(150) })
    expect(wrapper.vm.activeCategory).toBe('standalone')
    expect(wrapper.vm.rightPage).toBe(2)
    wrapper.unmount()
    const browsing = mountPicker({ operandMode: true, vars: standaloneOptions(1), value })
    browsing.vm.openPopover()
    browsing.vm.onCategoryClick('manual')
    await browsing.setProps({ vars: standaloneOptions(150) })
    expect(browsing.vm.activeCategory).toBe('manual')
    browsing.unmount()
  })

  test('失效的引用 ID 不按旧编码定位到其他变量', async () => {
    const wrapper = mountPicker({ operandMode: true, vars: standaloneOptions(1), value: { kind: 'REFERENCE', refId: 999, refType: 'VARIABLE', code: 'v1' } })
    wrapper.vm.openPopover()
    await nextTick()
    expect(wrapper.find('.vp-row--selected').exists()).toBe(false)
    expect(wrapper.emitted().input).toBeUndefined()
    wrapper.unmount()
  })

  test('绑定值语义变化后下一次打开重新定位新字段', async () => {
    const objectOption = {
      ...objectFieldOptions()[0],
      _varId: 101,
      _refType: 'DATA_OBJECT_FIELD',
      _ref: {
        ...objectFieldOptions()[0]._ref,
        refType: 'DATA_OBJECT_FIELD',
      },
    }
    const wrapper = mountPicker({
      operandMode: true,
      allowedKinds: ['LITERAL', 'REFERENCE'],
      vars: [...standaloneOptions(1), objectOption],
      value: {
        kind: 'REFERENCE',
        code: 'v1',
        refId: 1,
        refType: 'VARIABLE',
      },
    })
    wrapper.vm.$refs.popover.popperElm = document.createElement('div')
    wrapper.vm.openPopover()

    await wrapper.setProps({
      value: {
        kind: 'REFERENCE',
        code: 'amount',
        refId: 101,
        refType: 'DATA_OBJECT_FIELD',
      },
    })
    wrapper.vm.popoverVisible = false
    await nextTick()
    wrapper.vm.openPopover()

    expect(wrapper.vm.activeCategory).toBe('object')
    expect(wrapper.vm.expandedObject).toBe('LoanApply')
  })

  test.each([
    [
      'LITERAL',
      { kind: 'LITERAL', value: '18', valueType: 'NUMBER' },
      'manual',
    ],
    [
      'PATH',
      { kind: 'PATH', value: 'request.age', resolved: false },
      'manual',
    ],
    [
      '已解析 PATH',
      {
        kind: 'PATH',
        value: 'v1',
        code: 'v1',
        refId: 1,
        refType: 'VARIABLE',
        resolved: true,
      },
      'manual',
    ],
    [
      'FUNCTION',
      { kind: 'FUNCTION', functionId: 9, functionCode: 'max', args: [] },
      'function',
    ],
  ])('绑定值切换为 %s 后重开定位对应分类', async (_, value, expectedCategory) => {
    const wrapper = mountPicker({
      operandMode: true,
      allowedKinds: ['LITERAL', 'PATH', 'REFERENCE', 'FUNCTION'],
      vars: [...standaloneOptions(1), ...objectFieldOptions()],
      functions: [{ id: 9, functionCode: 'max', functionName: '最大值' }],
      value: {
        kind: 'REFERENCE',
        code: 'v1',
        refId: 1,
        refType: 'VARIABLE',
      },
    })
    wrapper.vm.$refs.popover.popperElm = document.createElement('div')
    wrapper.vm.openPopover()
    wrapper.vm.onCategoryClick('object')
    wrapper.vm.onItemClick(wrapper.vm.filteredRightItems[0])

    wrapper.vm.closePopover()
    await wrapper.setProps({ value })
    wrapper.vm.openPopover()

    expect(wrapper.vm.activeCategory).toBe(expectedCategory)
    expect(wrapper.vm.rightPage).toBe(1)
    expect(wrapper.vm.expandedObject).toBeNull()
    wrapper.unmount()
  })

  test.each([
    ['LITERAL', { kind: 'LITERAL', value: '18', valueType: 'NUMBER' }],
    [
      'FUNCTION',
      { kind: 'FUNCTION', functionId: 9, functionCode: 'max', args: [] },
    ],
  ])('%s 对应分类不可用时保留当前可用分类', (_, value) => {
    const wrapper = mountPicker({
      operandMode: true,
      allowedKinds: ['REFERENCE'],
      vars: [...standaloneOptions(1), ...objectFieldOptions()],
      value,
    })
    wrapper.vm.$refs.popover.popperElm = document.createElement('div')
    wrapper.vm.activeCategory = 'object'
    wrapper.vm.rightPage = 3
    wrapper.vm.expandedObject = 'LoanApply'

    wrapper.vm.openPopover()

    expect(wrapper.vm.activeCategory).toBe('object')
    expect(wrapper.vm.rightPage).toBe(1)
    expect(wrapper.vm.expandedObject).toBeNull()
  })

  test('空操作数打开后默认定位手输分类但不在弹层输入', async () => {
    const wrapper = mountPicker({ operandMode: true, allowedKinds: ['LITERAL', 'REFERENCE'], vars: standaloneOptions(1) })
    wrapper.vm.$refs.popover.popperElm = document.createElement('div')

    wrapper.vm.openPopover()
    await nextTick()
    await nextTick()

    expect(wrapper.vm.activeCategory).toBe('manual')
    expect(wrapper.find('.vp-manual-editor').exists()).toBe(false)
  })

  test('空操作数打开后立即选择普通变量不会被延迟默认分类覆盖', async () => {
    const wrapper = mountPicker({
      operandMode: true,
      allowedKinds: ['LITERAL', 'REFERENCE'],
      vars: standaloneOptions(1),
    })
    wrapper.vm.$refs.popover.popperElm = document.createElement('div')

    wrapper.vm.openPopover()
    wrapper.vm.onCategoryClick('standalone')
    await nextTick()

    expect(wrapper.vm.activeCategory).toBe('standalone')
    expect(wrapper.vm.filteredRightItems.map((item) => item.varCode)).toEqual([
      'v1',
    ])
  })

  test('已有字段打开后立即选择其他分类不会被延迟定位覆盖', async () => {
    const vars = [...standaloneOptions(1), ...objectFieldOptions()]
    const wrapper = mountPicker({
      operandMode: true,
      allowedKinds: ['LITERAL', 'REFERENCE'],
      vars,
      value: {
        kind: 'REFERENCE',
        code: 'v1',
        refId: 1,
        refType: 'VARIABLE',
      },
    })
    wrapper.vm.$refs.popover.popperElm = document.createElement('div')

    wrapper.vm.openPopover()
    wrapper.vm.onCategoryClick('object')
    await nextTick()

    expect(wrapper.vm.activeCategory).toBe('object')
    expect(wrapper.vm.filteredRightItems[0]._objectGroup).toBe(true)
  })

  test('已有对象字段打开后点击当前普通变量分类不会被延迟定位覆盖', async () => {
    const objectOption = {
      ...objectFieldOptions()[0],
      _varId: 101,
      _refType: 'DATA_OBJECT_FIELD',
      _ref: {
        ...objectFieldOptions()[0]._ref,
        refType: 'DATA_OBJECT_FIELD',
      },
    }
    const wrapper = mountPicker({
      operandMode: true,
      allowedKinds: ['LITERAL', 'REFERENCE'],
      vars: [...standaloneOptions(1), objectOption],
      value: {
        kind: 'REFERENCE',
        code: 'amount',
        refId: 101,
        refType: 'DATA_OBJECT_FIELD',
      },
    })
    wrapper.vm.$refs.popover.popperElm = document.createElement('div')
    wrapper.vm.activeCategory = 'standalone'

    wrapper.vm.openPopover()
    wrapper.vm.onCategoryClick('standalone')
    await nextTick()

    expect(wrapper.vm.activeCategory).toBe('standalone')
  })

  test('已有普通变量打开后点击当前数据对象分类不会被延迟定位覆盖', async () => {
    const wrapper = mountPicker({
      operandMode: true,
      allowedKinds: ['LITERAL', 'REFERENCE'],
      vars: [...standaloneOptions(1), ...objectFieldOptions()],
      value: {
        kind: 'REFERENCE',
        code: 'v1',
        refId: 1,
        refType: 'VARIABLE',
      },
    })
    wrapper.vm.$refs.popover.popperElm = document.createElement('div')
    wrapper.vm.activeCategory = 'object'

    wrapper.vm.openPopover()
    wrapper.vm.onCategoryClick('object')
    await nextTick()

    expect(wrapper.vm.activeCategory).toBe('object')
  })

  test('空操作数面板重新打开时保留用户已选择的字段分类', () => {
    const wrapper = mountPicker({
      operandMode: true,
      allowedKinds: ['LITERAL', 'REFERENCE'],
      vars: standaloneOptions(1),
    })
    wrapper.vm.$refs.popover.popperElm = document.createElement('div')

    wrapper.vm.openPopover()
    wrapper.vm.onCategoryClick('standalone')
    wrapper.vm.closePopover()
    wrapper.vm.openPopover()

    expect(wrapper.vm.activeCategory).toBe('standalone')
    expect(wrapper.vm.filteredRightItems.map((item) => item.varCode)).toEqual([
      'v1',
    ])
    wrapper.unmount()
  })

  test('操作数模式点击手输类型只发出当前输入位编辑请求', () => {
    const wrapper = mountPicker({ operandMode: true, allowedKinds: ['LITERAL', 'PATH'], vars: standaloneOptions(1) })

    wrapper.vm.requestManualEdit('LITERAL')

    expect(wrapper.emitted()['manual-edit'][0]).toEqual(['LITERAL'])
    expect(wrapper.emitted().input).toBeUndefined()
  })

  test('操作数选择器支持键盘聚焦并通过输入框焦点打开', async () => {
    const wrapper = mountPicker({ operandMode: true, allowedKinds: ['LITERAL', 'REFERENCE'], vars: standaloneOptions(1) })
    wrapper.vm.$refs.popover = { popperElm: document.createElement('div') }

    const reference = wrapper.find('.vp-reference')
    expect(reference.element.tagName).toBe('INPUT')
    expect(reference.attributes('role')).toBeUndefined()

    wrapper.vm.onInputFocus()
    await nextTick()
    expect(wrapper.vm.popoverVisible).toBe(true)
  })

  test.each([
    { kind: 'LITERAL', value: '18', valueType: 'NUMBER' },
    { kind: 'PATH', value: 'request.age', refId: 99, refType: 'DATA_OBJECT', resolved: true },
  ])('已配置 $kind 打开时直接进入输入位，而不要求再选类型', value => {
    const wrapper = mountPicker({ operandMode: true, value, allowedKinds: ['LITERAL', 'PATH', 'REFERENCE'] })
    wrapper.vm.openPopover()
    expect(wrapper.emitted()['manual-edit']).toEqual([[value.kind]])
    expect(wrapper.vm.popoverVisible).toBe(false)
    expect(wrapper.emitted().input).toBeUndefined()
    wrapper.unmount()
  })

  test('鼠标单击只在 click 阶段打开一次避免 focus 与 click 重入', () => {
    const wrapper = mountPicker({
      operandMode: true,
      allowedKinds: ['LITERAL', 'REFERENCE'],
      vars: standaloneOptions(1),
    })
    wrapper.vm.$refs.popover.popperElm = document.createElement('div')

    wrapper.vm.onReferenceMouseDown()
    wrapper.vm.onInputFocus()

    expect(wrapper.vm.popoverVisible).toBe(false)

    wrapper.vm.onInputClick()

    expect(wrapper.vm.popoverVisible).toBe(true)
    expect(wrapper.vm.pointerFocusPending).toBe(false)
  })

  test('关闭面板前移走弹层内焦点并仅处理当前弹层', () => {
    const wrapper = mountPicker(
      { operandMode: true, allowedKinds: ['LITERAL'], vars: standaloneOptions(1) },
      { attachTo: document.body }
    )
    const popper = document.createElement('div')
    const input = document.createElement('input')
    popper.appendChild(input)
    document.body.appendChild(popper)
    const reference = wrapper.get('.vp-reference').element
    wrapper.vm.$refs.popover.popperElm = popper
    input.focus()

    wrapper.vm.closePopover()

    expect(document.activeElement).toBe(reference)
    expect(popper.hasAttribute('inert')).toBe(true)
    document.body.removeChild(popper)
    wrapper.unmount()
  })

  test('Escape 只关闭字段选择器，不继续传播给父弹框', async () => {
    const wrapper = mountPicker(
      { vars: standaloneOptions(1) },
      { attachTo: document.body }
    )
    const parentEscapeHandler = vi.fn()
    document.addEventListener('keydown', parentEscapeHandler)

    wrapper.vm.openPopover()
    await nextTick()
    wrapper.element.dispatchEvent(
      new KeyboardEvent('keydown', {
        key: 'Escape',
        bubbles: true,
        cancelable: true,
      })
    )

    expect(wrapper.vm.popoverVisible).toBe(false)
    expect(parentEscapeHandler).not.toHaveBeenCalled()

    document.removeEventListener('keydown', parentEscapeHandler)
    wrapper.unmount()
  })

  test('object field short code does not reverse-link to a full object option', async () => {
    const wrapper = mountPicker({
      value: 'taxpayerType',
      allowCustom: false,
      vars: [
        {
          varCode: 'taxContext.taxpayerType',
          varLabel: 'fengkong/taxpayerType taxpayerType',
          varLabelText: 'fengkong/taxpayerType',
          varType: 'STRING',
          _ref: {
            category: 'object',
            objectCode: 'taxContext',
            objectScriptName: 'taxContext',
            objectLabel: 'fengkong'
          }
        }
      ]
    })
    await nextTick()

    expect(wrapper.vm.customMode).toBe(false)
    expect(wrapper.vm.currentValue).toBe('taxpayerType')
    expect(wrapper.vm.displayValue).toBe('taxpayerType')
  })

  test('autoSwitchCustom=false keeps unmatched values in picker mode', async () => {
    const wrapper = mountPicker({
      value: 'paramName',
      autoSwitchCustom: false,
      vars: standaloneOptions(1)
    })
    await nextTick()

    expect(wrapper.vm.customMode).toBe(false)
    wrapper.vm.openPopover()
    expect(wrapper.vm.popoverVisible).toBe(true)
  })
})
