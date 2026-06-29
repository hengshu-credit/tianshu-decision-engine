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
      vars: [
        {
          varCode: 'creditModel',
          varLabel: '信用模型 creditModel',
          varType: 'MODEL',
          _ref: { category: 'model', refType: 'MODEL' }
        }
      ]
    })

    wrapper.vm.onCategoryClick('model')
    expect(wrapper.vm.typeChar('MODEL')).toBe('M')
    expect(wrapper.vm.typeLabel('MODEL')).toBe('模型')
    expect(wrapper.vm.codeColumnLabel()).toBe('模型编码')
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
})
