import { h, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import VersionRestoreOptions from '@/components/common/VersionRestoreOptions.vue'

export function versionDisplayLabel(version) {
  const number = version.displayVersion ?? version.businessVersion ?? version.versionNo ?? version.version
  const record = version.revisionNo ?? version.version
  return `v${number}${record != null && String(record) !== String(number) ? ` · 记录 ${record}` : ''}`
}

export async function chooseVersionRestore(version) {
  const mode = ref('NEW')
  try {
    await ElMessageBox({
      title: '恢复历史版本',
      message: () => h(VersionRestoreOptions, { version, modelValue: mode.value, 'onUpdate:modelValue': value => { mode.value = value } }),
      showCancelButton: true,
      confirmButtonText: '恢复并编辑',
      cancelButtonText: '取消',
      closeOnClickModal: false,
    })
    return mode.value
  } catch (error) {
    if (error === 'cancel' || error === 'close') return null
    throw error
  }
}
