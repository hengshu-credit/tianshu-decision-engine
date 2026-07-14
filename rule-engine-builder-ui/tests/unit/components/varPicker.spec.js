import { shallowMount } from '@vue/test-utils'
import Vue from 'vue'

const VarPicker = jest.requireActual('../../../src/components/common/VarPicker.vue').default

function mountPicker(propsData = {}) {
  return shallowMount(VarPicker, {
    propsData: {
      vars: [],
      ...propsData
    },
    stubs: {
      'el-popover': {
        template: '<div><slot /><slot name="reference" /></div>'
      },
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
    }
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
    await Vue.nextTick()

    expect(wrapper.vm.categoryList.map(c => c.key)).toEqual(['object'])
    expect(wrapper.vm.activeCategory).toBe('object')
    expect(wrapper.vm.rightItems).toHaveLength(1)
    expect(wrapper.vm.rightItems[0]._objectGroup).toBe(true)
  })

  test('点击对象分组只展开，点击子字段才选择字段', async () => {
    const wrapper = mountPicker({ vars: objectFieldOptions() })
    await Vue.nextTick()

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
    await Vue.nextTick()

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
    await Vue.nextTick()

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
    await Vue.nextTick()

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
    await Vue.nextTick()

    expect(wrapper.vm.selectedItems.map(v => v.varCode)).toEqual(['v1'])
    expect(wrapper.vm.categoryList[0]).toMatchObject({ key: 'selected', count: 1 })
  })

  test('点击输入框后弹层保持打开，点击弹层内部不关闭', async () => {
    const wrapper = mountPicker({ vars: objectFieldOptions() })
    const popper = document.createElement('div')
    const inside = document.createElement('button')
    popper.appendChild(inside)
    document.body.appendChild(popper)
    wrapper.vm.$refs.popover = { popperElm: popper, doClose: jest.fn() }

    wrapper.vm.openPopover()
    await Vue.nextTick()
    expect(wrapper.vm.popoverVisible).toBe(true)

    wrapper.vm.onDocumentMouseDown({ target: inside })
    expect(wrapper.vm.popoverVisible).toBe(true)

    document.body.removeChild(popper)
    wrapper.destroy()
  })

  test('字段选择器打开后点击组件外部才关闭', async () => {
    const wrapper = mountPicker({ vars: objectFieldOptions() })
    const outside = document.createElement('div')
    document.body.appendChild(outside)
    wrapper.vm.$refs.popover = { doClose: jest.fn() }

    wrapper.vm.openPopover()
    await Vue.nextTick()
    wrapper.vm.onDocumentMouseDown({ target: outside })

    expect(wrapper.vm.popoverVisible).toBe(false)
    expect(wrapper.vm.$refs.popover.doClose).toHaveBeenCalled()

    document.body.removeChild(outside)
    wrapper.destroy()
  })

  test('打开已有字段时自动定位到字段所在分页', async () => {
    const wrapper = mountPicker({
      vars: standaloneOptions(150),
      value: 'v99'
    })
    wrapper.vm.$refs.popover = { popperElm: document.createElement('div') }

    wrapper.vm.openPopover()
    await Vue.nextTick()
    await Vue.nextTick()

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
    await Vue.nextTick()

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
    await Vue.nextTick()

    expect(wrapper.vm.activeCategory).toBe('standalone')

    wrapper.vm.onReferenceInput('credit')
    await Vue.nextTick()
    await Vue.nextTick()

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
    await Vue.nextTick()

    wrapper.vm.onCategoryClick('object')
    wrapper.vm.onReferenceInput('am')
    await Vue.nextTick()

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
    await Vue.nextTick()
    const width = wrapper.vm.panelStyle.width

    expect(wrapper.vm.categoryList.find(item => item.key === 'standalone').count).toBe(200)
    wrapper.vm.onReferenceInput('v199')
    await Vue.nextTick()

    expect(wrapper.vm.categoryList.find(item => item.key === 'standalone').count).toBe(200)
    expect(wrapper.vm.panelStyle.width).toBe(width)
  })

  test('popover resize supports larger panel bounds', () => {
    const wrapper = mountPicker({ vars: standaloneOptions(1) })
    wrapper.vm.$refs.popover = {
      popperElm: {
        getBoundingClientRect: () => ({ left: 0, top: 0 })
      }
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
    wrapper.vm.$refs.popover = { popperElm: document.createElement('div') }

    wrapper.vm.openPopover()
    await Vue.nextTick()
    await Vue.nextTick()
    await Vue.nextTick()

    expect(wrapper.vm.activeCategory).toBe('standalone')
    expect(wrapper.vm.currentValue).toBe('v99')
    expect(wrapper.vm.rightPage).toBe(2)
  })

  test('空操作数打开后默认聚焦手输阈值', async () => {
    const wrapper = mountPicker({ operandMode: true, allowedKinds: ['LITERAL', 'REFERENCE'], vars: standaloneOptions(1) })
    const focusManualInput = jest.spyOn(wrapper.vm, 'focusManualInput')
    wrapper.vm.$refs.popover = { popperElm: document.createElement('div') }

    wrapper.vm.openPopover()
    await Vue.nextTick()
    await Vue.nextTick()

    expect(wrapper.vm.activeCategory).toBe('manual')
    expect(wrapper.vm.manualKind).toBe('LITERAL')
    expect(focusManualInput).toHaveBeenCalled()
  })

  test('关闭面板前移走弹层内焦点并仅处理当前弹层', () => {
    const wrapper = mountPicker({ operandMode: true, allowedKinds: ['LITERAL'], vars: standaloneOptions(1) })
    const popper = document.createElement('div')
    const input = document.createElement('input')
    const reference = document.createElement('button')
    popper.appendChild(input)
    document.body.appendChild(popper)
    document.body.appendChild(reference)
    wrapper.vm.$refs.popover = { popperElm: popper, doClose: jest.fn() }
    wrapper.vm.$refs.reference = reference
    input.focus()

    wrapper.vm.closePopover()

    expect(document.activeElement).toBe(reference)
    expect(popper.hasAttribute('inert')).toBe(true)
    document.body.removeChild(popper)
    document.body.removeChild(reference)
  })

  test('object field short code value matches full object option without duplicate display code', async () => {
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
    await Vue.nextTick()

    expect(wrapper.vm.customMode).toBe(false)
    expect(wrapper.vm.currentValue).toBe('taxContext.taxpayerType')
    expect(wrapper.vm.displayValue).toBe('fengkong/taxpayerType taxContext.taxpayerType')
  })

  test('autoSwitchCustom=false keeps unmatched values in picker mode', async () => {
    const wrapper = mountPicker({
      value: 'paramName',
      autoSwitchCustom: false,
      vars: standaloneOptions(1)
    })
    await Vue.nextTick()

    expect(wrapper.vm.customMode).toBe(false)
    wrapper.vm.openPopover()
    expect(wrapper.vm.popoverVisible).toBe(true)
  })
})
