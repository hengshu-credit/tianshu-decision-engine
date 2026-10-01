<template>
  <remote-filter-select
    v-bind="$attrs"
    :value="value"
    :fetch-options="fetchOptions"
    :option-label-key="field"
    :option-value-key="field"
    allow-free-input
    @update:value="updateValue"
    @change="$emit('change', $event)"
  />
</template>

<script>
import RemoteFilterSelect from '@/components/RemoteFilterSelect.vue'
import { listProjects } from '@/api/project'

export default {
  name: 'ProjectFilterSelect',
  components: { RemoteFilterSelect },
  inheritAttrs: false,
  props: {
    value: { type: [String, Number], default: '' },
    field: {
      type: String,
      required: true,
      validator(value) {
        return value === 'projectCode' || value === 'projectName'
      },
    },
  },
  methods: {
    updateValue(value) {
      this.$emit('update:value', value)
      this.$emit('input', value)
    },
    fetchOptions({ query, pageNum, pageSize }) {
      return listProjects({
        pageNum,
        pageSize,
        [this.field]: query || '',
      })
    },
  },
  emits: ['input', 'update:value', 'change'],
}
</script>
