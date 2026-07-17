import { ONNX_TASKS, createOnnxConfig } from '@/constants/onnxTasks'

describe('ONNX 任务配置', () => {
  test('提供全部八类 ONNX 推理任务', () => {
    expect(ONNX_TASKS.map(item => item.value)).toEqual([
      'YUNET_FACE_DETECTION', 'FACENOX_ANTISPOOF', 'MN3_ANTISPOOF',
      'SCRFD_FACE_DETECTION', 'ARCFACE_RECOGNITION', 'LANDMARK_2D106',
      'LANDMARK_3D68', 'GENDER_AGE'
    ])
  })

  test('检测模型生成可编辑的上游默认参数', () => {
    expect(createOnnxConfig('YUNET_FACE_DETECTION')).toMatchObject({
      confidenceThreshold: 0.8, nmsThreshold: 0.3, topK: 5000, minFaceSize: 60
    })
    expect(createOnnxConfig('SCRFD_FACE_DETECTION')).toMatchObject({
      confidenceThreshold: 0.5, nmsThreshold: 0.4, inputWidth: 640, inputHeight: 640
    })
  })
})
