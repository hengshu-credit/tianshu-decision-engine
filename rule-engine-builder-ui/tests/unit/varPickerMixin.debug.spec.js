/**
 * 调试：检查 mock 是否正确共享
 */
import Vue from 'vue'
import varPickerMixin from '@/mixins/varPickerMixin'

const createMock = () => jest.fn()

jest.mock('@/api/definition', () => ({
  getDefinition: createMock()
}))
jest.mock('@/api/variable', () => ({
  listVariablesByProject: createMock()
}))
jest.mock('@/api/dataObject', () => ({
  getVariableTree: createMock()
}))
jest.mock('@/api/function', () => ({
  listAllFunctionsByProject: createMock()
}))
jest.mock('@/api/model', () => ({
  listModelInputs: createMock(),
  listModelOutputs: createMock()
}))

import * as definitionApi from '@/api/definition'
import * as variableApi from '@/api/variable'
import * as dataObjectApi from '@/api/dataObject'
import * as functionApi from '@/api/function'
import * as modelApi from '@/api/model'

describe('Debug: mock reference identity', () => {
  test('检查 mixin 中的 API 和测试文件中的 API 是否是同一个引用', async () => {
    // 直接在测试中调用，然后检查 mixin 是否使用同一个 mock
    definitionApi.getDefinition.mockResolvedValue({ data: { id: 1 } })

    const ComponentDef = {
      mixins: [varPickerMixin],
      data() {
        return {
          definitionId: 1,
          projectId: 1,
          varsLoading: false,
          varsLoadError: false,
          projectVars: [],
          projectRefs: [],
          variableTree: [],
          projectFunctions: [],
          modelInputVars: [],
          modelOutputVars: []
        }
      }
    }
    const vm = new (Vue.extend(ComponentDef))()

    // 手动调用定义 API，验证 mixin 是否使用同一个 mock
    variableApi.listVariablesByProject.mockResolvedValue({
      data: [{ id: 1, varCode: 'test', varLabel: '测试', varType: 'Integer', varSource: 'INPUT', scriptName: 'test' }]
    })
    dataObjectApi.getVariableTree.mockResolvedValue({ data: [] })
    functionApi.listAllFunctionsByProject.mockResolvedValue({ data: [] })
    modelApi.listModelInputs.mockResolvedValue({ data: [] })
    modelApi.listModelOutputs.mockResolvedValue({ data: [] })

    await vm.loadProjectVars(1)

    console.log('definitionApi.getDefinition calls:', definitionApi.getDefinition.mock.calls.length)
    console.log('variableApi.listVariablesByProject calls:', variableApi.listVariablesByProject.mock.calls.length)
    console.log('projectVars:', vm.projectVars)

    expect(definitionApi.getDefinition.mock.calls.length).toBeGreaterThan(0)
    expect(variableApi.listVariablesByProject.mock.calls.length).toBeGreaterThan(0)
  })
})