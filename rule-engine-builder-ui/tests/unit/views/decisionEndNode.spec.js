import DecisionFlow from '@/views/designer/DecisionFlow.vue'
import DecisionTree from '@/views/designer/DecisionTree.vue'
import { END_SCOPE_CURRENT_RULE, END_SCOPE_ALL_RULES } from '@/utils/endNodeScope'

const DESIGNERS = [
  ['决策流', DecisionFlow],
  ['决策树', DecisionTree]
]

describe.each(DESIGNERS)('%s结束节点', (name, designer) => {
  test('点击添加结束节点时先打开范围确认弹窗', () => {
    const context = {
      endNodeScopeVisible: false,
      addNodeToCanvas: jest.fn()
    }

    designer.methods.addNode.call(context, 'end-event')

    expect(context.endNodeScopeVisible).toBe(true)
    expect(context.addNodeToCanvas).not.toHaveBeenCalled()
  })

  test('确认后按所选范围创建结束节点', () => {
    const addNodeToCanvas = jest.fn()
    const context = {
      endNodeScopeVisible: true,
      addNodeToCanvas
    }

    designer.methods.confirmEndNode.call(context, END_SCOPE_ALL_RULES)

    expect(context.endNodeScopeVisible).toBe(false)
    expect(addNodeToCanvas).toHaveBeenCalledWith('end-event', END_SCOPE_ALL_RULES)
  })

  test.each([
    [END_SCOPE_CURRENT_RULE, '跳出当前规则'],
    [END_SCOPE_ALL_RULES, '跳出整体规则']
  ])('创建时持久化%s并设置对应节点名称', (scope, expectedName) => {
    const addNode = jest.fn()
    const context = { lf: { addNode } }

    designer.methods.addNodeToCanvas.call(context, 'end-event', scope)

    expect(addNode).toHaveBeenCalledTimes(1)
    expect(addNode.mock.calls[0][0].properties).toMatchObject({
      nodeName: expectedName,
      terminationScope: scope
    })
  })
})
