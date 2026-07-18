export const ONNX_TASKS = [
  { value: 'YUNET_FACE_DETECTION', label: 'YuNet 人脸检测', description: '输出完整人脸列表与 5 个关键点', supportsGpu: true },
  { value: 'FACENOX_ANTISPOOF', label: 'facenox 活体检测', description: '按人脸输出原始 logits', supportsGpu: true },
  { value: 'MN3_ANTISPOOF', label: 'MN3 活体检测', description: '按人脸输出模型原始概率', supportsGpu: true },
  { value: 'SCRFD_FACE_DETECTION', label: 'SCRFD 人脸检测', description: '输出完整人脸列表、5 个关键点及原始节点', supportsGpu: true },
  { value: 'ARCFACE_RECOGNITION', label: 'ArcFace 人脸识别', description: '输出原始及 L2 归一化 embedding', supportsGpu: true },
  { value: 'LANDMARK_2D106', label: '2D 106 点关键点', description: '按人脸输出 106 个二维关键点', supportsGpu: true },
  { value: 'LANDMARK_3D68', label: '3D 68 点关键点', description: '按人脸输出 68 个三维关键点', supportsGpu: true },
  { value: 'GENDER_AGE', label: '性别年龄', description: '按人脸输出性别、年龄及原始预测值', supportsGpu: true }
]

export const ONNX_RUNTIME_DEFAULTS = Object.freeze({
  executionProvider: 'CPU',
  cudaDeviceId: 0,
  cudaGpuMemLimitMb: 0,
  cudaArenaExtendStrategy: 'kNextPowerOfTwo',
  cudaCudnnConvAlgoSearch: 'EXHAUSTIVE',
  cudaDoCopyInDefaultStream: true
})

export function parseOnnxModelConfig(value) {
  if (!value) return {}
  if (typeof value === 'object') return { ...value }
  try { return JSON.parse(value) } catch (e) { return {} }
}

export function createOnnxRuntimeConfig(value) {
  const source = parseOnnxModelConfig(value)
  return {
    executionProvider: String(source.executionProvider || 'CPU').toUpperCase() === 'CUDA' ? 'CUDA' : 'CPU',
    cudaDeviceId: Number.isInteger(Number(source.cudaDeviceId)) && Number(source.cudaDeviceId) >= 0 ? Number(source.cudaDeviceId) : 0,
    cudaGpuMemLimitMb: Number.isFinite(Number(source.cudaGpuMemLimitMb)) && Number(source.cudaGpuMemLimitMb) >= 0 ? Number(source.cudaGpuMemLimitMb) : 0,
    cudaArenaExtendStrategy: source.cudaArenaExtendStrategy === 'kSameAsRequested' ? 'kSameAsRequested' : 'kNextPowerOfTwo',
    cudaCudnnConvAlgoSearch: ['EXHAUSTIVE', 'HEURISTIC', 'DEFAULT'].includes(String(source.cudaCudnnConvAlgoSearch || '').toUpperCase())
      ? String(source.cudaCudnnConvAlgoSearch).toUpperCase()
      : 'EXHAUSTIVE',
    cudaDoCopyInDefaultStream: source.cudaDoCopyInDefaultStream !== false
  }
}

export function onnxRuntimePayload(value) {
  return createOnnxRuntimeConfig(value)
}

export function createOnnxConfig(taskType) {
  if (taskType === 'YUNET_FACE_DETECTION') {
    return { confidenceThreshold: 0.8, nmsThreshold: 0.3, topK: 5000, minFaceSize: 60 }
  }
  if (taskType === 'SCRFD_FACE_DETECTION') {
    return { confidenceThreshold: 0.5, nmsThreshold: 0.4, inputWidth: 640, inputHeight: 640 }
  }
  return {}
}

export function getOnnxTaskLabel(taskType) {
  const task = ONNX_TASKS.find(item => item.value === taskType)
  return task ? task.label : (taskType || '-')
}
