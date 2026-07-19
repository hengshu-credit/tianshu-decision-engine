<template>
  <remote-filter-select
    :value="value"
    :fetch-options="fetchOptions"
    :option-label-key="field"
    :option-value-key="field"
    allow-free-input
    v-bind="$attrs"
    @input="$emit('input', $event)"
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
      }
    }
  },
  methods: {
    fetchOptions({ query, pageNum, pageSize }) {
      return listProjects({
        pageNum,
        pageSize,
        [this.field]: query || ''
      })
    }
  }
}
</script>
