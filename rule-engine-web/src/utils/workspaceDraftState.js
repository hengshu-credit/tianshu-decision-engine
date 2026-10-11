import request from '@/api/request'
import { metadataResponse } from '@/utils/metadataResponse'

const subscriptions = new Map()
const pending = new Set()
let queued = false
let generation = 0

function schedule() {
  if (queued) return
  queued = true
  queueMicrotask(async () => {
    queued = false
    const version = generation
    const keys = [...pending]; pending.clear()
    const groups = new Map()
    for (const key of keys) {
      const entry = subscriptions.get(key)
      if (!entry) continue
      if (!groups.has(entry.type)) groups.set(entry.type, [])
      groups.get(entry.type).push(entry.id)
    }
    await Promise.all([...groups].map(async ([type, ids]) => {
      for (let start = 0; start < ids.length; start += 100) {
        const batch = ids.slice(start, start + 100)
        const expected = new Map(batch.map(id => [id, subscriptions.get(`${type}:${id}`)]))
        try {
          const response = await request.get('/rule/governance/workspaces/states', { ...metadataResponse, params: { type, ids: batch.join(',') } })
          if (version !== generation) return
          if (!Array.isArray(response?.data)) throw new Error('状态响应无效')
          const states = new Map(response.data.map(item => [String(item.resourceId), item]))
          for (const id of batch) {
            const entry = subscriptions.get(`${type}:${id}`)
            if (entry && entry === expected.get(id)) {
              entry.value = states.has(id) ? { ...states.get(id), ready: true } : { ready: false, error: true }
              entry.listeners.forEach(listener => listener(entry.value))
            }
          }
        } catch {
          if (version !== generation) return
          for (const id of batch) {
            const entry = subscriptions.get(`${type}:${id}`)
            if (entry && entry === expected.get(id)) {
              entry.value = { ready: false, error: true }; entry.listeners.forEach(listener => listener(entry.value))
            }
          }
        }
      }
    }))
  })
}

export function watchResourceState(type, resourceId, listener) {
  const id = String(resourceId), key = `${type}:${id}`
  if (!/^[1-9]\d*$/.test(id)) { listener({ ready: false }); return () => {} }
  let entry = subscriptions.get(key)
  if (!entry) { entry = { type, id, value: { ready: false }, listeners: new Set() }; subscriptions.set(key, entry); pending.add(key); schedule() }
  entry.listeners.add(listener); listener(entry.value)
  return () => { entry.listeners.delete(listener); if (!entry.listeners.size) subscriptions.delete(key) }
}

window.addEventListener('tianshu:resource-draft-change', () => {
  generation++
  subscriptions.forEach((_, key) => pending.add(key))
  schedule()
})
