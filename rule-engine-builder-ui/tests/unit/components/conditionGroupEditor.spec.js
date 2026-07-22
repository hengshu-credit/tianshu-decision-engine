import { shallowMount } from '@test-utils'
import ConditionGroupEditor from '@/components/decision/ConditionGroupEditor.vue'

function makeStub(tag) {
  return {
    template: '<' + tag + '><slot /><slot name="reference" /></' + tag + '>',
    props: ['value', 'disabled']
  }
}

const OperandPickerStub = {
  name: 'OperandPicker',
  template: '<div class="operand-picker-stub" />',
  props: ['value', 'allowedKinds', 'expectedType', 'writableOnly', 'context', 'listOptions']
}

function mountEditor(leafOverrides = {}) {
  return shallowMount(ConditionGroupEditor, {
    props: {
      group: {
        type: 'group',
        op: 'AND',
        children: [{
          type: 'leaf',
          leftOperand: {
            kind: 'REFERENCE',
            value: 'amount',
            code: 'amount',
            label: '申请金额',
            valueType: 'NUMBER',
            refId: 1,
            refType: 'VARIABLE',
            varSource: 'INPUT',
            resolved: true
          },
          operator: '>',
          rightOperand: { kind: 'LITERAL', value: '1000', valueType: 'NUMBER' },
          ...leafOverrides
        }]
      },
      vars: [
        { varCode: 'amount', varLabel: '申请金额', varType: 'NUMBER', _varId: 1, _refType: 'VARIABLE' },
        { varCode: 'CONST_NEG_INF', varLabel: '负无穷', varType: 'NUMBER', _varId: 2, _refType: 'CONSTANT' }
      ],
      listOptions: [{ id: 9, listCode: 'mobile_black', listName: '手机号黑名单' }]
    },
    stubs: {
      'condition-group-editor': true,
      'operand-picker': OperandPickerStub,
      'el-button': makeStub('button'),
      'el-button-group': makeStub('div'),
      'el-select': makeStub('div'),
      'el-option-group': makeStub('div'),
      'el-option': true
    }
  })
}

describe('ConditionGroupEditor', () => {
  test('条件叶节点只渲染左右操作数选择器', () => {
    const wrapper = mountEditor()
    const pickers = wrapper.findAllComponents(OperandPickerStub)

    expect(pickers).toHaveLength(2)
    expect(pickers.at(0).props('allowedKinds')).toEqual(expect.arrayContaining(['REFERENCE', 'FUNCTION', 'OPERATION', 'ACCESS', 'CAST']))
    expect(pickers.at(1).props('allowedKinds')).toEqual(expect.arrayContaining(['LITERAL', 'REFERENCE', 'FUNCTION', 'OPERATION', 'ACCESS', 'CAST']))
    expect(wrapper.find('.cg-field--kind').exists()).toBe(false)
    expect(wrapper.find('.cg-manual-value').exists()).toBe(false)
  })

  test('无右值运算符清空 rightOperand 并隐藏右侧选择器', async () => {
    const wrapper = mountEditor()
    const leaf = wrapper.props('group').children[0]
    leaf.operator = 'is_null'

    wrapper.vm.onOpChange(leaf)
    await wrapper.vm.$nextTick()

    expect(leaf.rightOperand).toBeNull()
    expect(wrapper.findAllComponents(OperandPickerStub)).toHaveLength(1)
  })

  test('选择左操作数后按其类型规范化运算符', () => {
    const wrapper = mountEditor({ operator: 'contains' })
    const leaf = wrapper.props('group').children[0]

    wrapper.vm.onLeftOperandChange(leaf, {
      kind: 'REFERENCE', value: 'approved', code: 'approved', valueType: 'BOOLEAN',
      refId: 3, refType: 'VARIABLE', resolved: true
    })

    expect(leaf.leftOperand.valueType).toBe('BOOLEAN')
    expect(leaf.operator).toBe('==')
  })

  test('列表型运算符只允许阈值右值', () => {
    const wrapper = mountEditor({ operator: 'between' })
    const leaf = wrapper.props('group').children[0]

    expect(wrapper.vm.rightAllowedKinds(leaf)).toEqual(['LITERAL'])
  })

  test('在名单内只允许名单配置并透传名单选项', async () => {
    const wrapper = mountEditor({ operator: 'in_list', rightOperand: null })
    const leaf = wrapper.props('group').children[0]
    await wrapper.vm.$nextTick()
    const right = wrapper.findAllComponents(OperandPickerStub).at(1)

    expect(wrapper.vm.rightAllowedKinds(leaf)).toEqual(['LIST_QUERY'])
    expect(right.props('context')).toBe('LIST_QUERY_CONFIG')
    expect(right.props('listOptions')).toEqual([{ id: 9, listCode: 'mobile_black', listName: '手机号黑名单' }])
  })

  test('复合数值表达式使用递归类型推断展示数值操作符', () => {
    const wrapper = mountEditor({
      leftOperand: {
        kind: 'OPERATION',
        terms: [
          { operand: { kind: 'REFERENCE', code: 'amount', valueType: 'NUMBER', refId: 1, refType: 'VARIABLE' } },
          { operator: '+', operand: { kind: 'LITERAL', value: '100', valueType: 'NUMBER' } }
        ]
      }
    })
    const leaf = wrapper.props('group').children[0]

    expect(wrapper.vm.leftOperandType(leaf)).toBe('NUMBER')
    expect(wrapper.vm.operatorOptions(leaf).map(item => item.value)).toEqual(expect.arrayContaining(['>', '>=', '<', '<=', 'between']))
    expect(wrapper.vm.operatorOptions(leaf).map(item => item.value)).not.toContain('starts_with')
  })

  test('API 引用展示来源状态组，选择缓存状态后清空并隐藏右值', async () => {
    const wrapper = mountEditor({
      leftOperand: {
        kind: 'REFERENCE', code: 'creditScore', valueType: 'DOUBLE', refId: 8,
        refType: 'VARIABLE', varSource: 'API', sourceType: 'variable', resolved: true
      },
      operator: 'source_cache_hit'
    })
    const leaf = wrapper.props('group').children[0]

    expect(wrapper.vm.operatorGroups(leaf).map(group => group.label)).toEqual(['值判断', '来源状态'])
    expect(wrapper.vm.operatorOptions(leaf).map(item => item.value)).toContain('source_cache_hit')
    wrapper.vm.onOpChange(leaf)
    await wrapper.vm.$nextTick()

    expect(leaf.rightOperand).toBeNull()
    expect(wrapper.findAllComponents(OperandPickerStub)).toHaveLength(1)
  })
})
