import { reactive } from 'vue'
import request from './request'
import { metadataResponse } from '@/utils/metadataResponse'
import { newApiId } from '@/utils/apiExecution'

const base = '/rule/transfer'
const MiB = 1024 * 1024
const wait = ms => new Promise(resolve => setTimeout(resolve, ms))
const temporary = error => ['ERR_NETWORK', 'ECONNABORTED', 'ETIMEDOUT'].includes(error.code)
  || [408, 429, 502, 503, 504].includes(error.response?.status)
let uploads = new WeakMap()
window.addEventListener('tianshu:identity-change', () => { uploads = new WeakMap() })

export function startActivity(title, preview = false, previous = null) {
  const entries = previous ? previous.entries.slice() : []
  return reactive({ title, preview, status: 'running', percent: 0, entries, controller: new AbortController() })
}

export function recordAction(activity, percent, message, time = Date.now(), stage = null) {
  activity.controller.signal.throwIfAborted()
  activity.percent = Math.max(activity.percent, Math.min(100, Math.floor(percent)))
  const last = activity.entries.at(-1)
  if (stage && last?.stage === stage) Object.assign(last, { time, message })
  else activity.entries.push(stage ? { time, message, stage } : { time, message })
}

export function endActivity(activity, status, message) {
  if (!activity || activity.controller.signal.aborted) return
  recordAction(activity, status === 'failed' ? activity.percent : 100, message)
  activity.status = status
}

async function retryRequest(send, activity, attempts, message) {
  let retries = 0
  for (;;) {
    activity.controller.signal.throwIfAborted()
    try { return await send() } catch (error) {
      if (!temporary(error) || activity.controller.signal.aborted || ++retries >= attempts) throw error
      if (retries === 1) recordAction(activity, activity.percent, message)
      await wait(Math.min(500 * 2 ** Math.min(4, retries - 1), 5000))
    }
  }
}

async function stageFile(file, activity, config) {
  if (!file.size || file.size > 512 * MiB) throw new Error('配置包为空或超过 512 MiB')
  let upload = uploads.get(file)
  if (!upload || Date.now() - upload.touched >= 23 * 3600000) {
    recordAction(activity, 0, '准备上传配置包 · ' + file.name)
    const { data } = await request({ ...config, url: base + '/uploads', method: 'post', params: { size: file.size } })
    upload = { ...data, next: 0, touched: Date.now() }
    uploads.set(file, upload)
  }
  const count = Math.ceil(file.size / upload.partSize)
  if (upload.next === count) {
    recordAction(activity, 20, '复用已上传的配置包 · ' + file.name)
    return upload.uploadId
  }
  recordAction(activity, 0, '上传配置包 · ' + file.name)
  for (let i = upload.next; i < count; i++) {
    const chunk = file.slice(i * upload.partSize, (i + 1) * upload.partSize)
    try {
      await retryRequest(() => request({ ...config, url: base + '/uploads/' + upload.uploadId + '/' + i,
        method: 'put', data: chunk, headers: { 'Content-Type': 'application/octet-stream' }, timeout: 120000 }),
      activity, 4, '上传连接中断，正在重传当前分块')
    } catch (error) {
      if (!temporary(error)) throw error
      throw new Error(`第 ${i + 1}/${count} 个分块上传中断，再次预检可从此处继续`, { cause: error })
    }
    upload.next = i + 1
    upload.touched = Date.now()
    recordAction(activity, 20 * upload.next / count, `已上传 ${upload.next}/${count} 个分块 · ${file.name}`, Date.now(), 'upload')
  }
  return upload.uploadId
}

// 同一 taskId 重试提交只会接回原任务。状态查询只按已读游标取得新增动作。
export async function executeTransferTask(payload, file, activity, serverEnd = 98) {
  const config = { signal: activity.controller.signal, inlineError: true }
  let uploadId
  if (file) uploadId = await stageFile(file, activity, config)
  const taskId = newApiId()
  recordAction(activity, activity.percent, '提交后台任务 · ' + activity.title)
  await retryRequest(() => request({ ...config, url: base + '/tasks', method: 'post', data: { ...payload, taskId, uploadId } }),
    activity, Infinity, '连接中断，正在确认同一后台任务的提交状态')
  let after = '0'
  const start = activity.percent
  for (;;) {
    const { data } = await retryRequest(() => request({ ...config, ...metadataResponse, url: base + '/tasks/' + taskId, params: { after } }),
      activity, Infinity, '暂时无法读取后台进度，正在重新连接')
    for (const entry of data.actions) {
      recordAction(activity, start + (serverEnd - start) * entry.progress / 100, entry.message, entry.time)
      after = entry.id
    }
    if (data.actions.length >= 256) continue
    if (data.status === 'FAILED') throw new Error(data.result?.message || '后台迁移失败')
    if (data.status === 'SUCCESS') {
      if (payload.operation !== 'EXPORT') return { data: data.result }
      recordAction(activity, 98, '正在下载已生成的配置包')
      return retryRequest(() => request({ ...config, url: base + '/tasks/' + taskId + '/download',
        responseType: 'blob', timeout: 30 * 60000 }), activity, 3, '正在重新下载同一份配置包')
    }
    await wait(750)
  }
}
