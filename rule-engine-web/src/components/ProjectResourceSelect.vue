<template>
  <remote-filter-select
    v-bind="$attrs"
    :value="value"
    :fetch-options="fetchOptions"
    option-label-key="projectName"
    option-value-key="id"
    :selected-option="selectedProject"
    :allow-free-input="false"
    :disabled="disabled"
    placeholder="输入项目名称搜索"
    @update:value="updateValue"
  @change="$emit('change', $event)"
  />
</template>

<script>
import RemoteFilterSelect from '@/components/RemoteFilterSelect.vue'
import { getProject, listProjects } from '@/api/project'

export default {
  name: 'ProjectResourceSelect',
  components: { RemoteFilterSelect },
  inheritAttrs: false,
  props: {
    value: { type: [String, Number], default: null },
    disabled: { type: Boolean, default: false },
  },
  emits: ['update:value', 'change', 'select'],
  data() {
    return { selectedProject: null, selectedRequestId: 0 }
  },
  watch: {
    value: {
      immediate: true,
      async handler(value) {
        const requestId = ++this.selectedRequestId
        if (value === null || value === undefined || value === '') {
          this.selectedProject = null
          return
        }
        try {
          const response = await getProject(value)
          if (requestId !== this.selectedRequestId) return
          this.selectedProject = response?.data || null
          this.$emit('select', this.selectedProject)
        } catch {
          if (requestId === this.selectedRequestId) this.selectedProject = null
        }
      },
    },
  },
  methods: {
    async updateValue(value) {
      const normalized = value === '' ? null : value
      this.$emit('update:value', normalized)
      if (normalized === null) {
        this.selectedProject = null
        this.$emit('select', null)
        return
      }
      try {
        const response = await getProject(normalized)
        this.selectedProject = response?.data || null
        this.$emit('select', this.selectedProject)
      } catch {
        this.$emit('select', null)
      }
    },
    fetchOptions({ query, pageNum, pageSize }) {
      return listProjects({
        pageNum,
        pageSize,
        status: 1,
        projectName: query || '',
      })
    },
  },
}
</script>
