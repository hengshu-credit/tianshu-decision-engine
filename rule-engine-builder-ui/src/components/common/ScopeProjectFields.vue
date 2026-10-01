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
        <el-option label="🌐 全局（所有项目可用）" value="GLOBAL" />
        <el-option label="📁 项目级" value="PROJECT" />
      </el-select>
    </el-form-item>
    <el-form-item v-if="scope === 'PROJECT'" :label="projectLabel" :required="required">
      <el-select
        :model-value="projectId"
        filterable
        clearable
        :disabled="disabled || projectDisabled"
        :placeholder="projectPlaceholder"
        style="width: 100%"
        @update:model-value="$emit('update:projectId', $event)"
        @change="$emit('project-change', $event)"
      >
        <el-option
          v-for="project in projects"
          :key="project.id"
          :label="project.projectName || project.projectCode || String(project.id)"
          :value="project.id"
        />
      </el-select>
    </el-form-item>
  </div>
</template>

<script>
export default {
  name: 'ScopeProjectFields',
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
    scopePlaceholder: { type: String, default: '选择作用范围' },
    projectPlaceholder: { type: String, default: '请选择项目' },
  },
  emits: ['update:scope', 'update:projectId', 'scope-change', 'project-change'],
}
</script>
