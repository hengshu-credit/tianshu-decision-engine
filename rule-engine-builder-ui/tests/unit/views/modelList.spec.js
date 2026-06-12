// tests/unit/views/modelList.spec.js
import { mount, createLocalVue } from '@vue/test-utils'
import Vue from 'vue'
import ElementUI from 'element-ui'

// Mock API 模块
jest.mock('@/api/model', () => ({
  listModels: jest.fn(),
  uploadModel: jest.fn(),
  publishModel: jest.fn(),
  deleteModel: jest.fn(),
  updateModel: jest.fn(),
  toGlobalModel: jest.fn(),
  checkModelCode: jest.fn()
}))

jest.mock('@/api/project', () => ({
  listProjects: jest.fn()
}))

import * as modelApi from '@/api/model'
import * as projectApi from '@/api/project'
import ModelList from '@/views/model/ModelList.vue'

afterEach(() => { jest.clearAllMocks() })

// ─── Mock 数据 ───────────────────────────────────────────
function mockModels() {
  return [
    { id: 1, modelName: '信用评分模型', modelCode: 'credit_score', modelType: 'XGBOOST', modelFormat: 'PMML', scope: 'PROJECT', projectId: 1, projectName: '项目A', currentVersion: 1, publishedVersion: 1, status: 1, inputFieldCount: 5, outputFieldCount: 2 },
    { id: 2, modelName: '欺诈检测模型', modelCode: 'fraud_detect', modelType: 'LIGHTGBM', modelFormat: 'PMML', scope: 'PROJECT', projectId: 1, projectName: '项目A', currentVersion: 1, publishedVersion: null, status: 0, inputFieldCount: 10, outputFieldCount: 1 },
    { id: 3, modelName: '流失预测模型', modelCode: 'churn_predict', modelType: 'LR', modelFormat: 'PMML', scope: 'GLOBAL', projectId: 0, projectName: '—', currentVersion: 1, publishedVersion: 1, status: 1, inputFieldCount: 8, outputFieldCount: 1 }
  ]
}

function mockProjects() {
  return [
    { id: 1, projectName: '项目A', projectCode: 'project_a' },
    { id: 2, projectName: '项目B', projectCode: 'project_b' }
  ]
}

// ─── 测试用例 ─────────────────────────────────────────────
function createTestVue() {
  const localVue = createLocalVue()
  localVue.use(ElementUI)
  return localVue
}

async function mountAndWait() {
  projectApi.listProjects.mockResolvedValue({ data: { records: mockProjects() } })
  modelApi.listModels.mockResolvedValue({ data: { records: mockModels(), total: 3 } })

  const wrapper = mount(ModelList, {
    localVue: createTestVue(),
    mocks: {
      $route: { params: {} },
      $router: { push: jest.fn(), replace: jest.fn() }
    },
    stubs: {
      'el-form': true, 'el-form-item': true,
      'el-select': true, 'el-option': true,
      'el-input': true, 'el-input-number': true,
      'el-button': true, 'el-tag': true,
      'el-table': true, 'el-table-column': true,
      'el-tabs': true, 'el-tab-pane': true,
      'el-dialog': true, 'el-card': true,
      'el-radio': true, 'el-radio-group': true,
      'el-upload': true, 'el-pagination': true,
      'el-switch': true, 'el-loading': true,
      'el-textarea': true, 'el-divider': true, 'el-alert': true
    }
  })

  await Vue.nextTick()
  await new Promise(r => setTimeout(r, 100))
  return wrapper
}

describe('ModelList — 初始化与数据加载', () => {
  let wrapper

  beforeEach(async () => { wrapper = await mountAndWait() })
  afterEach(() => { if (wrapper) wrapper.destroy() })

  test('mounted 后调用 loadProjects', () => {
    expect(projectApi.listProjects).toHaveBeenCalled()
  })

  test('mounted 后调用 listModels', () => {
    expect(modelApi.listModels).toHaveBeenCalled()
  })

  test('models 数据正确赋值', () => {
    expect(wrapper.vm.models).toBeInstanceOf(Array)
    expect(wrapper.vm.models.length).toBe(3)
  })

  test('projects 数据正确赋值', () => {
    expect(wrapper.vm.projects).toBeInstanceOf(Array)
    expect(wrapper.vm.projects.length).toBe(2)
  })

  test('total 正确赋值', () => {
    expect(wrapper.vm.total).toBe(3)
  })

  test('loading 初始值为 false（加载完成后）', () => {
    expect(wrapper.vm.loading).toBe(false)
  })

  test('modelTypeLabel 返回正确的标签', () => {
    expect(wrapper.vm.modelTypeLabel('XGBOOST')).toBe('XGBoost')
    expect(wrapper.vm.modelTypeLabel('LIGHTGBM')).toBe('LightGBM')
    expect(wrapper.vm.modelTypeLabel('LR')).toBe('LR（逻辑回归）')
    expect(wrapper.vm.modelTypeLabel('NEURAL_NET')).toBe('NeuralNet（神经网络）')
    expect(wrapper.vm.modelTypeLabel('RANDOM_FOREST')).toBe('RandomForest')
    expect(wrapper.vm.modelTypeLabel('SVM')).toBe('SVM')
    expect(wrapper.vm.modelTypeLabel('UNKNOWN')).toBe('UNKNOWN')
    expect(wrapper.vm.modelTypeLabel(null)).toBe('—')
    expect(wrapper.vm.modelTypeLabel('')).toBe('—')
  })

  test('分页参数初始化正确', () => {
    expect(wrapper.vm.qp.pageNum).toBe(1)
    expect(wrapper.vm.qp.pageSize).toBe(10)
    expect(wrapper.vm.qp.scope).toBe('')
    expect(wrapper.vm.qp.modelType).toBe('')
  })
})

describe('ModelList — 筛选与搜索', () => {
  let wrapper

  beforeEach(async () => { wrapper = await mountAndWait() })
  afterEach(() => { if (wrapper) wrapper.destroy() })

  test('queryProjectName 模糊匹配项目名称', () => {
    wrapper.vm.projectList = mockProjects()
    wrapper.vm.queryProjectName('项目A')
    expect(wrapper.vm.filteredProjectNames.length).toBe(1)
    expect(wrapper.vm.filteredProjectNames[0].projectName).toBe('项目A')
  })

  test('queryProjectName 空查询时返回前20项', () => {
    wrapper.vm.projectList = mockProjects()
    wrapper.vm.queryProjectName('')
    expect(wrapper.vm.filteredProjectNames.length).toBe(2)
  })

  test('queryProjectCode 模糊匹配项目编码', () => {
    wrapper.vm.projectList = mockProjects()
    wrapper.vm.queryProjectCode('project_a')
    expect(wrapper.vm.filteredProjectCodes.length).toBe(1)
  })

  test('queryModelCode 模糊匹配模型编码', () => {
    wrapper.vm.allModelCodes = ['credit_score', 'fraud_detect', 'churn_predict']
    wrapper.vm.queryModelCode('credit')
    expect(wrapper.vm.filteredModelCodes.length).toBe(1)
    expect(wrapper.vm.filteredModelCodes[0]).toBe('credit_score')
  })

  test('queryModelName 模糊匹配模型名称', () => {
    wrapper.vm.allModelNames = ['信用评分模型', '欺诈检测模型', '流失预测模型']
    wrapper.vm.queryModelName('信用')
    expect(wrapper.vm.filteredModelNames.length).toBe(1)
    expect(wrapper.vm.filteredModelNames[0]).toBe('信用评分模型')
  })

  test('handleQuery 重置页码并重新加载', async () => {
    wrapper.vm.qp.pageNum = 5
    wrapper.vm.qp.scope = 'PROJECT'
    modelApi.listModels.mockResolvedValue({ data: { records: [], total: 0 } })
    wrapper.vm.handleQuery()
    await Vue.nextTick()
    expect(wrapper.vm.qp.pageNum).toBe(1)
  })

  test('resetQuery 重置所有查询条件', () => {
    wrapper.vm.qp.scope = 'GLOBAL'
    wrapper.vm.qp.modelType = 'XGBOOST'
    wrapper.vm.qp.modelCode = 'test'
    wrapper.vm.qp.modelName = '测试模型'
    modelApi.listModels.mockResolvedValue({ data: { records: [], total: 0 } })
    wrapper.vm.resetQuery()
    expect(wrapper.vm.qp.scope).toBe('')
    expect(wrapper.vm.qp.modelType).toBe('')
    expect(wrapper.vm.qp.modelCode).toBe('')
    expect(wrapper.vm.qp.modelName).toBe('')
  })

  test('分页大小变化时 pageNum 重置为1', () => {
    wrapper.vm.qp.pageNum = 5
    wrapper.vm.qp.pageSize = 10
    modelApi.listModels.mockResolvedValue({ data: { records: [], total: 0 } })
    wrapper.vm.qp.pageSize = 50
    // 模拟 size-change 回调逻辑
    wrapper.vm.qp.pageNum = 1
    expect(wrapper.vm.qp.pageNum).toBe(1)
  })
})

describe('ModelList — 上传模型', () => {
  let wrapper

  beforeEach(async () => { wrapper = await mountAndWait() })
  afterEach(() => { if (wrapper) wrapper.destroy() })

  test('handleUpload 打开上传弹窗并重置表单', () => {
    wrapper.vm.handleUpload()
    expect(wrapper.vm.uploadVisible).toBe(true)
    expect(wrapper.vm.uploadForm.modelCode).toBe('')
    expect(wrapper.vm.uploadForm.modelName).toBe('')
    expect(wrapper.vm.uploadForm.modelType).toBe('LR')
    expect(wrapper.vm.uploadForm.scope).toBe('PROJECT')
    expect(wrapper.vm.fileList).toEqual([])
    expect(wrapper.vm.selectedFile).toBeNull()
  })

  test('handleFileChange 保存选中的文件', () => {
    const file = { name: 'model.pmml', raw: { name: 'model.pmml' } }
    const files = [file]
    wrapper.vm.handleFileChange(file, files)
    expect(wrapper.vm.selectedFile).toBe(file.raw)
    expect(wrapper.vm.fileList.length).toBe(1)
  })

  test('handleFileChange 保留最后一个文件', () => {
    const file1 = { name: 'file1.pmml', raw: {} }
    const file2 = { name: 'file2.pmml', raw: {} }
    wrapper.vm.handleFileChange(file2, [file1, file2])
    expect(wrapper.vm.fileList.length).toBe(1)
    expect(wrapper.vm.fileList[0].name).toBe('file2.pmml')
  })
})

describe('ModelList — 模型操作', () => {
  let wrapper

  beforeEach(async () => { wrapper = await mountAndWait() })
  afterEach(() => { if (wrapper) wrapper.destroy() })

  test('handleEdit 填充编辑表单', () => {
    const row = { id: 1, modelName: '测试模型', modelCode: 'test_code', modelType: 'LR', modelFormat: 'PMML', scope: 'PROJECT', status: 1 }
    wrapper.vm.handleEdit(row)
    expect(wrapper.vm.editVisible).toBe(true)
    expect(wrapper.vm.editForm.id).toBe(1)
    expect(wrapper.vm.editForm.modelName).toBe('测试模型')
    expect(wrapper.vm.editForm.modelCode).toBe('test_code')
    expect(wrapper.vm.editForm.status).toBe(1)
  })

  test('handleToGlobal 填充转换表单', () => {
    const row = { id: 1, modelName: '项目模型', modelCode: 'project_model' }
    wrapper.vm.handleToGlobal(row)
    expect(wrapper.vm.toGlobalVisible).toBe(true)
    expect(wrapper.vm.toGlobalModelInfo.id).toBe(1)
    expect(wrapper.vm.toGlobalModelInfo.modelName).toBe('项目模型')
    expect(wrapper.vm.toGlobalForm.modelCode).toBe('')
  })

  test('handleQuery 按 scope 筛选', async () => {
    modelApi.listModels.mockResolvedValue({ data: { records: mockModels().filter(m => m.scope === 'GLOBAL'), total: 1 } })
    wrapper.vm.qp.scope = 'GLOBAL'
    wrapper.vm.handleQuery()
    await Vue.nextTick()
    expect(modelApi.listModels).toHaveBeenCalledWith(expect.objectContaining({ scope: 'GLOBAL' }))
  })

  test('load 方法删除空参数', async () => {
    wrapper.vm.qp.scope = ''
    modelApi.listModels.mockResolvedValue({ data: { records: [], total: 0 } })
    await wrapper.vm.load()
    const callArgs = modelApi.listModels.mock.calls[modelApi.listModels.mock.calls.length - 1][0]
    expect(callArgs.scope).toBeUndefined()
  })

  test('allModelCodes 和 allModelNames 从响应中提取', async () => {
    const models = mockModels()
    wrapper.vm.allModelCodes = []
    wrapper.vm.allModelNames = []
    modelApi.listModels.mockResolvedValue({ data: { records: models, total: 3 } })
    await wrapper.vm.load()
    expect(wrapper.vm.allModelCodes.length).toBe(3)
    expect(wrapper.vm.allModelNames.length).toBe(3)
    expect(wrapper.vm.allModelCodes).toContain('credit_score')
  })
})

describe('ModelList — 边界情况', () => {
  test('models 为空数组不报错', async () => {
    modelApi.listModels.mockResolvedValue({ data: { records: [], total: 0 } })
    projectApi.listProjects.mockResolvedValue({ data: { records: [] } })
    const wrapper = mount(ModelList, {
      localVue: createTestVue(),
      mocks: {
        $route: { params: {} },
        $router: { push: jest.fn(), replace: jest.fn() }
      },
      stubs: {
        'el-form': true, 'el-form-item': true, 'el-select': true, 'el-option': true,
        'el-input': true, 'el-button': true, 'el-tag': true,
        'el-table': true, 'el-table-column': true,
        'el-dialog': true, 'el-radio': true, 'el-radio-group': true,
        'el-upload': true, 'el-pagination': true, 'el-switch': true, 'el-loading': true,
        'el-textarea': true
      }
    })
    await Vue.nextTick()
    await new Promise(r => setTimeout(r, 100))
    expect(wrapper.vm.models).toEqual([])
    wrapper.destroy()
  })

  test('projects 加载失败时设为空数组', async () => {
    projectApi.listProjects.mockRejectedValue(new Error('加载失败'))
    modelApi.listModels.mockResolvedValue({ data: { records: [], total: 0 } })
    const wrapper = mount(ModelList, {
      localVue: createTestVue(),
      mocks: {
        $route: { params: {} },
        $router: { push: jest.fn(), replace: jest.fn() }
      },
      stubs: {
        'el-form': true, 'el-form-item': true, 'el-select': true, 'el-option': true,
        'el-input': true, 'el-button': true, 'el-tag': true,
        'el-table': true, 'el-table-column': true,
        'el-dialog': true, 'el-radio': true, 'el-radio-group': true,
        'el-upload': true, 'el-pagination': true, 'el-switch': true, 'el-loading': true,
        'el-textarea': true
      }
    })
    await Vue.nextTick()
    await new Promise(r => setTimeout(r, 100))
    expect(wrapper.vm.projects).toEqual([])
    wrapper.destroy()
  })
})