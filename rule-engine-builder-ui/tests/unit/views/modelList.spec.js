// tests/unit/views/modelList.spec.js
import { mount } from '@test-utils'
import { h, nextTick } from 'vue'

// 使用真实 Element Plus（setup.js 的 element-ui mock 没有挂载到 Vue.prototype）
// Mock API 模块
jest.mock('@/api/model', () => ({
  listModels: jest.fn(),
  uploadModel: jest.fn(),
  publishModel: jest.fn(),
  deleteModel: jest.fn(),
  updateModel: jest.fn(),
  toGlobalModel: jest.fn(),
  checkModelCode: jest.fn(),
  getRuntimeCapabilities: jest.fn()
}))

jest.mock('@/api/project', () => ({
  listProjects: jest.fn()
}))

import * as modelApi from '@/api/model'
import * as projectApi from '@/api/project'
import ModelList from '@/views/model/ModelList.vue'
import ProjectFilterSelect from '@/components/ProjectFilterSelect.vue'
import fs from 'fs'
import path from 'path'

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

// ─── 带方法的 el-form stub（jsdom 中 el-form 的 ref 方法不可用）─────────────
const makeFormStub = (name) => ({
  name,
  render: () => h('form'),
  methods: { clearValidate: jest.fn(), validate: jest.fn(cb => cb && cb(true)), validateField: jest.fn(), resetFields: jest.fn() }
})

describe('ModelList 项目筛选交互', () => {
  test('顶部注册并绑定项目编码和项目名称筛选组件', () => {
    const source = fs.readFileSync(path.resolve(__dirname, '../../../src/views/model/ModelList.vue'), 'utf8')

    expect(ModelList.components.ProjectFilterSelect).toBe(ProjectFilterSelect)
    expect(source).toContain('field="projectCode"')
    expect(source).toContain('field="projectName"')
  })
})

// ─── 测试用例 ─────────────────────────────────────────────


async function mountAndWait() {
  projectApi.listProjects.mockResolvedValue({ data: { records: mockProjects() } })
  modelApi.listModels.mockResolvedValue({ data: { records: mockModels(), total: 3 } })
  modelApi.getRuntimeCapabilities.mockResolvedValue({
    data: { onnxRuntimeVersion: '1.26.0', availableProviders: ['CPU', 'CUDA'], cudaAvailable: true, cudaError: null }
  })

  const wrapper = mount(ModelList, {
    mocks: {
      $route: { params: {} },
      $router: { push: jest.fn(), replace: jest.fn() }
    },
    stubs: {
      'el-form': makeFormStub('ElForm'), 'el-form-item': true,
      'el-select': true, 'el-option': true,
      'el-input': true, 'el-input-number': true,
      'el-button': true, 'el-tag': true,
      'el-table': true, 'el-table-column': true,
      'el-tabs': true, 'el-tab-pane': true,
      'el-dialog': true, 'el-card': true,
      'el-radio': true, 'el-radio-group': true,
      'el-upload': true, 'el-pagination': true,
      'el-switch': true, 'el-loading': true,
      'el-textarea': true, 'el-divider': true, 'el-alert': true,
      'el-link': true, 'el-tooltip': true, 'el-col': true,
      'el-message': true
    }
  })

  await nextTick()
  await new Promise(r => setTimeout(r, 100))
  return wrapper
}

describe('ModelList — 初始化与数据加载', () => {
  let wrapper

  beforeEach(async () => { wrapper = await mountAndWait() })
  afterEach(() => { if (wrapper) wrapper.unmount() })

  test('mounted 后调用 loadProjects', () => {
    expect(projectApi.listProjects).toHaveBeenCalled()
  })

  test('mounted 后调用 listModels', () => {
    expect(modelApi.listModels).toHaveBeenCalled()
  })

  test('mounted 后加载 ONNX Runtime GPU 能力', () => {
    expect(modelApi.getRuntimeCapabilities).toHaveBeenCalled()
    expect(wrapper.vm.runtimeCapabilities.cudaAvailable).toBe(true)
  })

  test('CUDA 可用时仍提示推理失败会自动回退 CPU', () => {
    expect(wrapper.vm.cudaRuntimeHint).toContain('自动回退 CPU')
    expect(wrapper.vm.cudaRuntimeHint).toContain('重启服务')
  })

  test('CUDA 环境不可用时提示模型将自动回退 CPU', async () => {
    wrapper.vm.runtimeCapabilities = {
      ...wrapper.vm.runtimeCapabilities,
      cudaAvailable: false,
      cudaError: '缺少 cublasLt64_12.dll'
    }
    await nextTick()

    expect(wrapper.vm.cudaRuntimeHint).toContain('缺少 cublasLt64_12.dll')
    expect(wrapper.vm.cudaRuntimeHint).toContain('自动回退 CPU')
    expect(wrapper.vm.cudaRuntimeHint).toContain('重启服务')
  })

  test('已有模型触发回退时提示数量和当前 CPU 执行状态', async () => {
    wrapper.vm.runtimeCapabilities = {
      ...wrapper.vm.runtimeCapabilities,
      activeCpuFallbackCount: 3
    }
    await nextTick()

    expect(wrapper.vm.cudaRuntimeHint).toContain('已有 3 个')
    expect(wrapper.vm.cudaRuntimeHint).toContain('正在通过 CPU 执行')
    expect(wrapper.vm.cudaRuntimeHint).toContain('自动回退 CPU')
    expect(wrapper.vm.cudaRuntimeHint).toContain('首次回退可能增加耗时')
    expect(wrapper.vm.cudaRuntimeHint).toContain('重启服务')
  })

  test('models 数据正确赋值', () => {
    expect(wrapper.vm.models).toBeInstanceOf(Array)
    expect(wrapper.vm.models.length).toBe(3)
    expect(wrapper.vm.activeTab).toBe('list')
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

  test('页面只承诺 ONNX 和 PMML 文件格式', () => {
    const html = wrapper.html()

    expect(wrapper.vm.supportedModelAccept).toBe('.onnx,.pmml')
    expect(html).toContain('ONNX')
    expect(html).toContain('PMML')
    expect(html).not.toContain('PICKLE')
    expect(html).not.toContain('DILL')
    expect(html).not.toContain('.pkl')
    expect(html).not.toContain('.pickle')
    expect(html).not.toContain('.dill')
    expect(html).not.toContain('.pb')
    expect(html).not.toContain('.xml')
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
  afterEach(() => { if (wrapper) wrapper.unmount() })

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
    await nextTick()
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
  afterEach(() => { if (wrapper) wrapper.unmount() })

  test('handleUpload 打开上传弹窗并重置表单', () => {
    wrapper.vm.handleUpload()
    expect(wrapper.vm.uploadVisible).toBe(true)
    expect(wrapper.vm.uploadForm.modelCode).toBe('')
    expect(wrapper.vm.uploadForm.modelName).toBe('')
    expect(wrapper.vm.uploadForm.modelType).toBe('LR')
    expect(wrapper.vm.uploadForm.scope).toBe('PROJECT')
    expect(wrapper.vm.uploadForm.executionProvider).toBe('CPU')
    expect(wrapper.vm.uploadForm.cudaDeviceId).toBe(0)
    expect(wrapper.vm.fileList).toEqual([])
    expect(wrapper.vm.selectedFile).toBeNull()
  })

  test('handleFileChange 保存选中的文件', () => {
    const file = { name: 'model.pmml', raw: { name: 'model.pmml' } }
    const files = [file]
    wrapper.vm.handleFileChange(file, files)
    expect(wrapper.vm.selectedFile).toEqual(file.raw)
    expect(wrapper.vm.fileList.length).toBe(1)
  })

  test('handleFileChange 保留最后一个文件', () => {
    const file1 = { name: 'file1.pmml', raw: {} }
    const file2 = { name: 'file2.pmml', raw: {} }
    wrapper.vm.handleFileChange(file2, [file1, file2])
    expect(wrapper.vm.fileList.length).toBe(1)
    expect(wrapper.vm.fileList[0].name).toBe('file2.pmml')
  })

  test('handleFileChange 拒绝所有非 ONNX 和 PMML 文件', () => {
    const file = { name: 'model.pkl', raw: { name: 'model.pkl' } }
    const errorMessage = jest.spyOn(wrapper.vm.$message, 'error').mockImplementation(() => {})

    wrapper.vm.handleFileChange(file, [file])

    expect(wrapper.vm.selectedFile).toBeNull()
    expect(wrapper.vm.fileList).toEqual([])
    expect(errorMessage).toHaveBeenCalledWith('仅支持 ONNX、PMML 格式的模型文件')
    errorMessage.mockRestore()
  })

  test('选择 ONNX 文件后显示任务配置并切换到神经网络类型', () => {
    const file = { name: 'det_10g.onnx', raw: { name: 'det_10g.onnx' } }
    wrapper.vm.handleFileChange(file, [file])
    expect(wrapper.vm.isOnnxFile).toBe(true)
    expect(wrapper.vm.uploadForm.modelType).toBe('NEURAL_NET')
    wrapper.vm.onOnnxTaskChange('SCRFD_FACE_DETECTION')
    expect(wrapper.vm.uploadForm.onnxConfig).toMatchObject({ inputWidth: 640, inputHeight: 640 })
    expect(wrapper.vm.uploadSupportsGpu).toBe(true)
  })

  test('上传 ONNX 时提交任务类型、配置并更新进度', async () => {
    wrapper.vm.handleUpload()
    wrapper.vm.uploadForm.scope = 'GLOBAL'
    wrapper.vm.uploadForm.modelCode = 'buffalo_face_detector'
    wrapper.vm.uploadForm.modelName = 'Buffalo 人脸检测'
    const raw = new Blob(['onnx'])
    const file = { name: 'det_10g.onnx', raw }
    wrapper.vm.handleFileChange(file, [file])
    wrapper.vm.onOnnxTaskChange('SCRFD_FACE_DETECTION')
    wrapper.vm.uploadForm.executionProvider = 'CUDA'
    wrapper.vm.uploadForm.cudaDeviceId = 0
    wrapper.vm.uploadForm.cudaGpuMemLimitMb = 8192
    wrapper.vm.uploadForm.cudaArenaExtendStrategy = 'kSameAsRequested'
    wrapper.vm.uploadForm.cudaCudnnConvAlgoSearch = 'HEURISTIC'
    wrapper.vm.uploadForm.cudaDoCopyInDefaultStream = true
    wrapper.vm.uploadForm.preloadOnStartup = 1
    wrapper.vm.uploadForm.executionTimeoutMs = 90000
    modelApi.uploadModel.mockImplementation((formData, onProgress) => {
      onProgress({ loaded: 50, total: 100 })
      expect(formData.get('onnxTaskType')).toBe('SCRFD_FACE_DETECTION')
      expect(JSON.parse(formData.get('onnxConfig'))).toMatchObject({
        inputWidth: 640,
        inputHeight: 640,
        executionProvider: 'CUDA',
        cudaDeviceId: 0,
        cudaGpuMemLimitMb: 8192,
        cudaArenaExtendStrategy: 'kSameAsRequested',
        cudaCudnnConvAlgoSearch: 'HEURISTIC',
        cudaDoCopyInDefaultStream: true
      })
      expect(formData.get('preloadOnStartup')).toBe('1')
      expect(formData.get('executionTimeoutMs')).toBe('90000')
      return Promise.resolve({ data: { id: 10 } })
    })

    wrapper.vm.handleDoUpload()
    await new Promise(resolve => setTimeout(resolve, 20))

    expect(modelApi.uploadModel).toHaveBeenCalled()
    expect(wrapper.vm.uploadProgress).toBe(50)
    expect(wrapper.vm.uploading).toBe(false)
  })
})

describe('ModelList — 模型操作', () => {
  let wrapper

  beforeEach(async () => { wrapper = await mountAndWait() })
  afterEach(() => { if (wrapper) wrapper.unmount() })

  test('handleEdit 填充编辑表单', () => {
    const row = {
      id: 1, modelName: '测试模型', modelCode: 'test_code', modelType: 'NEURAL_NET', modelFormat: 'ONNX',
      scope: 'PROJECT', status: 1, preloadOnStartup: 1, executionTimeoutMs: 90000,
      modelConfig: JSON.stringify({ onnxTaskType: 'MN3_ANTISPOOF', executionProvider: 'CUDA', cudaDeviceId: 1, cudaGpuMemLimitMb: 4096 })
    }
    wrapper.vm.handleEdit(row)
    expect(wrapper.vm.editVisible).toBe(true)
    expect(wrapper.vm.editForm.id).toBe(1)
    expect(wrapper.vm.editForm.modelName).toBe('测试模型')
    expect(wrapper.vm.editForm.modelCode).toBe('test_code')
    expect(wrapper.vm.editForm.status).toBe(1)
    expect(wrapper.vm.editForm.preloadOnStartup).toBe(1)
    expect(wrapper.vm.editForm.executionTimeoutMs).toBe(90000)
    expect(wrapper.vm.editForm.executionProvider).toBe('CUDA')
    expect(wrapper.vm.editForm.cudaDeviceId).toBe(1)
    expect(wrapper.vm.editForm.cudaGpuMemLimitMb).toBe(4096)
    expect(wrapper.vm.editSupportsGpu).toBe(true)
  })

  test('编辑 ONNX GPU 配置时保留已有任务配置', async () => {
    const row = {
      id: 1, modelName: '测试模型', modelCode: 'test_code', modelType: 'NEURAL_NET', modelFormat: 'ONNX',
      scope: 'PROJECT', status: 1, preloadOnStartup: 1, executionTimeoutMs: 90000,
      modelConfig: JSON.stringify({ onnxTaskType: 'MN3_ANTISPOOF', nodeMetadata: { inputs: {} }, testParams: '{}', executionProvider: 'CPU' })
    }
    wrapper.vm.handleEdit(row)
    wrapper.vm.editForm.executionProvider = 'CUDA'
    wrapper.vm.editForm.cudaDeviceId = 0
    wrapper.vm.editForm.cudaGpuMemLimitMb = 6144
    modelApi.updateModel.mockResolvedValue({ data: true })

    wrapper.vm.handleDoEdit()
    await new Promise(resolve => setTimeout(resolve, 20))

    const payload = modelApi.updateModel.mock.calls[0][0]
    const config = JSON.parse(payload.modelConfig)
    expect(config).toMatchObject({
      onnxTaskType: 'MN3_ANTISPOOF',
      nodeMetadata: { inputs: {} },
      testParams: '{}',
      executionProvider: 'CUDA',
      cudaDeviceId: 0,
      cudaGpuMemLimitMb: 6144
    })
  })

  test('handleToGlobal 填充转换表单', () => {
    const row = { id: 1, modelName: '项目模型', modelCode: 'project_model' }
    wrapper.vm.handleToGlobal(row)
    expect(wrapper.vm.toGlobalVisible).toBe(true)
    expect(wrapper.vm.toGlobalModelInfo.id).toBe(1)
    expect(wrapper.vm.toGlobalModelInfo.modelName).toBe('项目模型')
    expect(wrapper.vm.toGlobalForm.modelCode).toBe('')
  })

  test('全局模型项目列忽略残留项目名称', () => {
    expect(ModelList.methods.modelProjectName).toEqual(expect.any(Function))
    expect(ModelList.methods.modelProjectName({ scope: 'GLOBAL', projectName: '项目A' })).toBe('—')
    expect(ModelList.methods.modelProjectName({ scope: 'PROJECT', projectName: '项目A' })).toBe('项目A')
  })

  test('handleQuery 按 scope 筛选', async () => {
    modelApi.listModels.mockResolvedValue({ data: { records: mockModels().filter(m => m.scope === 'GLOBAL'), total: 1 } })
    wrapper.vm.qp.scope = 'GLOBAL'
    wrapper.vm.handleQuery()
    await nextTick()
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
      mocks: {
        $route: { params: {} },
        $router: { push: jest.fn(), replace: jest.fn() }
      },
      stubs: {
        'el-form': true, 'el-form-item': true, 'el-select': true, 'el-option': true,
        'el-input': true, 'el-input-number': true, 'el-button': true, 'el-tag': true,
        'el-table': true, 'el-table-column': true,
        'el-dialog': true, 'el-radio': true, 'el-radio-group': true,
        'el-upload': true, 'el-pagination': true, 'el-switch': true, 'el-loading': true,
        'el-textarea': true
      }
    })
    await nextTick()
    await new Promise(r => setTimeout(r, 100))
    expect(wrapper.vm.models).toEqual([])
    wrapper.unmount()
  })

  test('projects 加载失败时设为空数组', async () => {
    projectApi.listProjects.mockRejectedValue(new Error('加载失败'))
    modelApi.listModels.mockResolvedValue({ data: { records: [], total: 0 } })
    const wrapper = mount(ModelList, {
      mocks: {
        $route: { params: {} },
        $router: { push: jest.fn(), replace: jest.fn() }
      },
      stubs: {
        'el-form': true, 'el-form-item': true, 'el-select': true, 'el-option': true,
        'el-input': true, 'el-input-number': true, 'el-button': true, 'el-tag': true,
        'el-table': true, 'el-table-column': true,
        'el-dialog': true, 'el-radio': true, 'el-radio-group': true,
        'el-upload': true, 'el-pagination': true, 'el-switch': true, 'el-loading': true,
        'el-textarea': true
      }
    })
    await nextTick()
    await new Promise(r => setTimeout(r, 100))
    expect(wrapper.vm.projects).toEqual([])
    wrapper.unmount()
  })
})
