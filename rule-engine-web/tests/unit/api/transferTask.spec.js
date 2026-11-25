import { afterEach, expect, it, vi } from 'vitest'
import request from '@/api/request'
import { executeTransferTask, startActivity } from '@/api/transferTask'

vi.mock('@/api/request', () => ({ default: vi.fn() }))

afterEach(() => { vi.useRealTimers(); vi.resetAllMocks() })

it('分块精确覆盖文件，确认导入复用已预检的上传，切换身份后重新上传', async () => {
  let uploads = 0
  const chunks = []
  const tasks = []
  request.mockImplementation(async config => {
    if (config.url.endsWith('/uploads')) return { data: { uploadId: String(++uploads), partSize: 4 } }
    if (config.method === 'put') {
      chunks.push([config.url, config.data.size])
      return {}
    }
    if (config.method === 'post') {
      tasks.push(config.data)
      return { data: config.data.taskId }
    }
    return { data: { status: 'SUCCESS', actions: [], result: { applied: true } } }
  })
  const file = new File(['0123456789'], 'sample.project-set.zip')
  await executeTransferTask({ operation: 'SET_PREVIEW' }, file, startActivity('预检'))
  await executeTransferTask({ operation: 'SET_IMPORT', digest: 'digest' }, file, startActivity('导入'))
  expect(chunks).toEqual([[0, 4], [1, 4], [2, 2]].map(([i, size]) => ['/rule/transfer/uploads/1/' + i, size]))
  expect(tasks.map(t => t.uploadId)).toEqual(['1', '1'])
  expect(tasks[1].taskId).not.toBe(tasks[0].taskId)
  window.dispatchEvent(new Event('tianshu:identity-change'))
  await executeTransferTask({ operation: 'SET_PREVIEW' }, file, startActivity('预检'))
  expect(uploads).toBe(2)
  expect(tasks[2].uploadId).toBe('2')
})

it('提交回包丢失时接回同一任务，按游标追加真实动作并保留失败进度', async () => {
  vi.useFakeTimers()
  const submissions = []
  const cursors = []
  request.mockImplementation(async config => {
    if (config.method === 'post') {
      submissions.push(config.data.taskId)
      if (submissions.length === 1) throw Object.assign(new Error('连接中断'), { code: 'ERR_NETWORK' })
      return { data: config.data.taskId }
    }
    cursors.push(config.params.after)
    return { data: cursors.length === 1
      ? { status: 'RUNNING', actions: [{ id: '15', time: 1234, progress: 50, message: '读取配置' }] }
      : { status: 'FAILED', actions: [{ id: '18', time: 2345, progress: 70, message: '校验摘要' }], result: { message: '摘要不符' } } }
  })
  const activity = startActivity('导出配置')
  const failure = expect(executeTransferTask({ operation: 'EXPORT' }, null, activity)).rejects.toThrow('摘要不符')
  await vi.runAllTimersAsync()
  await failure
  expect(submissions).toHaveLength(2)
  expect(submissions[0]).toBe(submissions[1])
  expect(cursors).toEqual(['0', '15'])
  expect(activity.entries.slice(-2)).toEqual([{ time: 1234, message: '读取配置' }, { time: 2345, message: '校验摘要' }])
  expect(activity.percent).toBe(68)
})
