<template>
  <remote-filter-select
    v-bind="$attrs"
    :value="modelValue"
    :fetch-options="fetchProjects"
    :selected-option="selectedProject"
    option-label-key="displayName"
    option-value-key="id"
    placeholder="搜索项目名称或编码"
    @update:value="$emit('update:modelValue', $event)"
    @change="$emit('change', $event)"
    @load-error="loadError = '项目读取失败，请重新打开或搜索'"
  />
  <span v-if="loadError" role="alert" class="project-select-error">{{ loadError }}</span>
</template>

<script>
import RemoteFilterSelect from '@/components/RemoteFilterSelect.vue'
import { listProjectOptions as listProjects, getProjectOption as getProject } from '@/api/project'

function labeled(project) {
  return { ...project, displayName: [project.projectName, project.projectCode].filter(Boolean).join(' · ') || String(project.id) }
}

export default {
  name: 'ProjectResourceSelect',
  components: { RemoteFilterSelect },
  inheritAttrs: false,
  props: {
    modelValue: { type: [String, Number], default: null },
    projects: { type: Array, default: () => [] },
    status: { type: Number, default: 1 },
  },
  emits: ['update:modelValue', 'change'],
  data() { return { selectedProject: null, knownProjects: {}, labelRequestId: 0, loadError: '' } },
  watch: {
    modelValue: { immediate: true, handler() { this.resolveSelectedProject() } },
    projects() { this.resolveSelectedProject() },
  },
  beforeUnmount() { this.labelRequestId++ },
  methods: {
    async fetchProjects({ query, pageNum, pageSize }) {
      this.loadError = ''
      const response = await listProjects({ keyword: query, pageNum, pageSize, status: this.status })
      const data = response.data || {}
      const records = (Array.isArray(data) ? data : data.records || []).map(labeled)
      for (const project of records) this.knownProjects[String(project.id)] = project
      return { data: { records, total: Array.isArray(data) ? data.length : data.total } }
    },
    async resolveSelectedProject() {
      const requestId = ++this.labelRequestId
      const id = this.modelValue
      this.selectedProject = null
      this.loadError = ''
      if (id == null || id === '' || String(id) === '0') return
      const known = this.knownProjects[String(id)] || this.projects.find(item => String(item.id) === String(id))
      if (known) { this.selectedProject = labeled(known); return }
      try {
        const response = await getProject(id)
        if (requestId !== this.labelRequestId) return
        if (response.data?.id != null) this.selectedProject = labeled(response.data)
      } catch {
        if (requestId === this.labelRequestId) this.loadError = '已选项目读取失败，请重新选择'
      }
    },
  },
}
</script>

<style scoped>
.project-select-error { color: var(--el-color-danger); font-size: 12px; }
</style>
