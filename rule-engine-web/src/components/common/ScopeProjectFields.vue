<template>
  <div class="scope-project-fields">
    <el-form-item :label="scopeLabel" :required="required">
      <el-select
        :model-value="scope"
        :disabled="disabled || scopeDisabled"
        :placeholder="scopePlaceholder"
        style="width: 100%"
        @update:model-value="$emit('update:scope', $event)"
        @change="$emit('scope-change', $event)"
      >
        <el-option label="🌐 全局（所有项目可用）" value="GLOBAL" :disabled="!canEditGlobal" />
        <el-option label="📁 项目级" value="PROJECT" />
      </el-select>
    </el-form-item>
    <el-form-item v-if="scope === 'PROJECT'" :label="projectLabel" :required="required" :prop="projectProp">
      <project-resource-select
        :model-value="projectId"
        :projects="projects"
        :disabled="disabled || projectDisabled"
        :placeholder="projectPlaceholder"
        style="width: 100%"
        @update:model-value="$emit('update:projectId', $event)"
        @change="$emit('project-change', $event)"
      />
    </el-form-item>
  </div>
</template>

<script>
import ProjectResourceSelect from '@/components/ProjectResourceSelect.vue'
import { getCurrentUser, hasPermission } from '@/security/permissionState'
import { getActiveMenuIndex, SIDEBAR_MENUS } from '@/layout/layoutState'
export default {
  name: 'ScopeProjectFields',
  components: { ProjectResourceSelect },
  props: {
    scope: { type: String, default: '' },
    projectId: { type: [String, Number], default: '' },
    projects: { type: Array, default: () => [] },
    disabled: { type: Boolean, default: false },
    scopeDisabled: { type: Boolean, default: false },
    projectDisabled: { type: Boolean, default: false },
    required: { type: Boolean, default: true },
    scopeLabel: { type: String, default: '作用范围' },
    projectLabel: { type: String, default: '所属项目' },
    projectProp: { type: String, default: '' },
    scopePlaceholder: { type: String, default: '选择作用范围' },
    projectPlaceholder: { type: String, default: '请选择项目' },
  },
  emits: ['update:scope', 'update:projectId', 'scope-change', 'project-change'],
  computed: {
    canEditGlobal() {
      if (getCurrentUser()?.globalEditable === false) return false
      const menu = SIDEBAR_MENUS.find(item => item.index === getActiveMenuIndex(this.$route?.path))
      const permission = typeof menu?.permission === 'string' && menu.permission.endsWith(':view') ? menu.permission.replace(':view', ':edit') : null
      return !permission || hasPermission(permission, { scope: 'GLOBAL' })
    },
  },
}
</script>
