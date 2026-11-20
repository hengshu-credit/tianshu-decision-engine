<template>
  <div v-if="cases.length || error" class="resource-test-cases">
    <el-alert v-if="error" title="测试用例加载失败" type="warning" :closable="false">
      <el-button link @click="load">重试</el-button>
    </el-alert>
    <template v-else>
      <el-select v-model="code" clearable filterable placeholder="选择固定测试用例" aria-label="选择固定测试用例" @change="apply">
        <el-option v-for="item in cases" :key="item.code" :label="item.name" :value="item.code" />
      </el-select>
      <ul v-if="selected" class="field-help">
        <li v-for="check in selected.expected" :key="check.path.join('.')">
          {{ check.path.join('.') }} {{ expectationLabel(check) }}
        </li>
      </ul>
      <el-tag v-if="verdict" :type="verdict === '通过' ? 'success' : 'danger'">{{ verdict }}</el-tag>
    </template>
  </div>
</template>
<script>
import request from '@/api/request'
import { compareResourceCase } from '@/utils/resourceTestCases'
export default {
  props: {
    endpoint: { type: String, required: true },
    params: { type: String, required: true },
    outcome: { default: null, type: [Object, String] }
  },
  emits: ['apply', 'context'],
  data() { return { cases: [], code: '', error: false, loadId: 0 } },
  computed: {
    selected() { return this.cases.find(item => item.code === this.code) },
    verdict() {
      if (!this.selected || this.outcome == null || this.outcome === '') return ''
      try {
        if (JSON.stringify(JSON.parse(this.params)) !== JSON.stringify(this.selected.params)) return ''
        const result = typeof this.outcome === 'string' ? JSON.parse(this.outcome) : this.outcome
        return compareResourceCase(result, this.selected.expected) ? '通过' : '未通过'
      } catch { return '' }
    }
  },
  watch: { endpoint: { immediate: true, handler: 'load' } },
  methods: {
    expectationLabel(check) {
      if (check.op === 'reportPath') return '应为 /reports 下的征信报告路径'
      if (check.op === 'between') return '应在 ' + check.value.join(' 至 ') + ' 之间（含边界）'
      return '应等于 ' + JSON.stringify(check.value)
    },
    async load() {
      const id = ++this.loadId
      this.cases = []; this.code = ''; this.error = false
      if (!this.endpoint) return
      try {
        const res = await request.get(this.endpoint)
        if (id === this.loadId) this.cases = res.data.map(row => ({ ...row, params: JSON.parse(row.paramsJson), expected: JSON.parse(row.expectedJson) }))
      } catch { if (id === this.loadId) this.error = true }
    },
    apply() { this.$emit('context', this.selected?.contextDefinitionId ?? null); if (this.selected) this.$emit('apply', JSON.stringify(this.selected.params, null, 2)) }
  }
}
</script>
