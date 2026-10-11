import DecisionFlow from '@/views/designer/DecisionFlow.vue'
import DecisionTree from '@/views/designer/DecisionTree.vue'
import GatewayBranchEditor from '@/components/flow/GatewayBranchEditor.vue'

function incomplete() {
  return { type: 'group', op: 'AND', children: [{ type: 'leaf', operator: 'between',
    leftOperand: { kind: 'REFERENCE', refId: 19, refType: 'VARIABLE', code: 'age', valueType: 'NUMBER' },
    rightOperand: { kind: 'LITERAL', valueType: 'NUMBER', value: '18,' } }] }
}

describe('未完成区间可继续编辑但不能伪装成有效脚本', () => {
  test.each([['决策流', DecisionFlow], ['决策树', DecisionTree]])('%s自动同步保留区间草稿且清除旧条件文本', (_, component) => {
    const vm = {
      activeElement: { baseType: 'edge' }, edgeCondMode: 'visual', edgeConditionRoot: incomplete(),
      edgeProps: { conditionExpr: 'true' }, onEdgeChange: vi.fn(),
    }
    expect(() => component.methods.persistEdgeConditionDraft.call(vm, true)).not.toThrow()
    expect(vm.edgeProps.conditionExpr).toBe('')
    expect(vm.edgeProps.conditionConfig).toEqual(vm.edgeConditionRoot)
    expect(vm.edgeProps.conditionConfig.children[0].leftOperand.refId).toBe(19)
    expect(vm.onEdgeChange).toHaveBeenCalledOnce()
  })

  test('网关分支编辑同样保留未完成区间，显式生成时显示中文错误', () => {
    const vm = { conditionRoot: incomplete(), form: { conditionExpr: 'true' }, updateForm: vi.fn(), $message: { warning: vi.fn() } }
    expect(() => GatewayBranchEditor.methods.persistCondition.call(vm, true)).not.toThrow()
    expect(vm.updateForm).toHaveBeenCalledWith(expect.objectContaining({ conditionExpr: '', conditionConfig: vm.conditionRoot }))
    expect(vm.$message.warning).toHaveBeenCalledWith(expect.stringContaining('区间'))
  })

  test.each([['决策流', DecisionFlow], ['决策树', DecisionTree]])('%s动作预览显示区间错误而不破坏编辑', (_, component) => {
    const leaf = incomplete().children[0]
    const vm = { currentActionData: [{ type: 'if-block', branches: [{ ...leaf, type: 'if', actions: [] }] }] }
    expect(() => component.computed.scriptPreview.call(vm)).not.toThrow()
    expect(component.computed.scriptPreview.call(vm)).toContain('区间')
    expect(component.computed.scriptPreview.call(vm)).not.toContain('if (true)')
  })
})
