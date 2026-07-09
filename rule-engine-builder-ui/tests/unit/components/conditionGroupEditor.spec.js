import { shallowMount } from '@vue/test-utils'
import Vue from 'vue'
import ConditionGroupEditor from '@/components/decision/ConditionGroupEditor.vue'

function makeStub(tag) {
  return {
    template: '<' + tag + '><slot /><slot name="reference" /></' + tag + '>',
    props: ['value', 'disabled']
  }
}

const openPopoverMock = jest.fn()

const VarPickerStub = {
  name: 'VarPicker',
  template: '<div class="var-picker-stub" />',
  props: ['value', 'allowCustom'],
  methods: {
    openPopover() {
      openPopoverMock()
    }
  }
}

function mountEditor(leafOverrides = {}) {
  openPopoverMock.mockClear()
  return shallowMount(ConditionGroupEditor, {
    propsData: {
      group: {
        type: 'group',
        op: 'AND',
        children: [{
          type: 'leaf',
          varCode: 'amount',
          varLabel: '申请金额',
          varType: 'NUMBER',
          operator: '>',
          valueKind: 'CONST',
          value: '1000',
          ...leafOverrides
        }]
      },
      allowCustomVar: true,
      vars: [
        { varCode: 'amount', varLabel: '申请金额', varType: 'NUMBER', _varId: 1, _refType: 'VARIABLE' },
        { varCode: 'CONST_NEG_INF', varLabel: '负无穷', varType: 'NUMBER', _varId: 2, _refType: 'CONSTANT' }
      ]
    },
    stubs: {
      'condition-group-editor': true,
      'var-picker': VarPickerStub,
      'el-button': makeStub('button'),
      'el-button-group': makeStub('div'),
      'el-select': makeStub('div'),
      'el-option': true,
      'el-input': makeStub('div'),
      'el-tooltip': makeStub('span')
    }
  })
}

describe('ConditionGroupEditor', () => {
  test('左侧字段选择器允许在规则设计页切换手工输入', () => {
    const wrapper = mountEditor()

    const leftPicker = wrapper.findAllComponents(VarPickerStub).at(0)

    expect(leftPicker.props('allowCustom')).toBe(true)
  })

  test('点击右侧字段按钮后切换为字段选择并自动弹出选择器', async () => {
    const wrapper = mountEditor()
    const leaf = wrapper.props('group').children[0]

    wrapper.vm.switchRightToVar(leaf, 0)
    await Vue.nextTick()

    const pickers = wrapper.findAllComponents(VarPickerStub)
    expect(leaf.valueKind).toBe('VAR')
    expect(pickers).toHaveLength(2)
    expect(openPopoverMock).toHaveBeenCalledTimes(1)
  })

  test('右侧字段选择常量后按变量引用持久化 ID 和 refType', () => {
    const wrapper = mountEditor({ valueKind: 'VAR', value: '' })
    const leaf = wrapper.props('group').children[0]

    wrapper.vm.onLeafRightSelect(leaf, {
      varCode: 'CONST_NEG_INF',
      varLabel: '负无穷',
      varType: 'NUMBER',
      _varId: 2,
      _refType: 'CONSTANT'
    })

    expect(leaf.valueKind).toBe('VAR')
    expect(leaf.value).toBe('CONST_NEG_INF')
    expect(leaf.rightVarLabel).toBe('负无穷')
    expect(leaf.rightVarType).toBe('NUMBER')
    expect(leaf._rightVarId).toBe(2)
    expect(leaf._rightRefType).toBe('CONSTANT')
  })
})
