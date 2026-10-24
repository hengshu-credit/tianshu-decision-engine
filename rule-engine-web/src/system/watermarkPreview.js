import { shallowRef } from 'vue'
import { getCurrentUser } from '@/security/permissionState'
import { resolveWatermarkSettings } from '@/system/watermark'

const preview = shallowRef(null)
const identity = user => user ? JSON.stringify([user.userId, user.username, user.permissionVersion, user.loginTime]) : null

// 仅当前页面内存中的草稿，不能写入共享配置、浏览器存储或跨窗口消息。
export function setWatermarkPreview(config) {
  const owner = identity(getCurrentUser())
  preview.value = config && owner ? {
    owner,
    config: { systemName: config.systemName, ...resolveWatermarkSettings(config) },
  } : null
}

export function getWatermarkPreview(user) {
  return preview.value?.owner === identity(user) ? preview.value.config : null
}
