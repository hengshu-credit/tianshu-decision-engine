<template>
  <div v-if="cases.length || error" class="resource-test-cases">
    <el-alert v-if="error" title="测试用例加载失败" type="warning" :closable="false">
      <el-button link @click="load">重试</el-button>
    </el-alert>
    <template v-else>
      <el-select v-model="selectedId" clearable filterable placeholder="选择固定测试用例" aria-label="选择固定测试用例" @change="apply">
        <el-option v-for="item in cases" :key="item.id || item.code" :label="item.name" :value="item.id || item.code" />
      </el-select>
      <el-button v-if="selected?.id && editable" link type="primary" :loading="saving" @click="saveInput">保存用例输入</el-button>
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
import { saveResourceCaseInput } from '@/api/testData'
import { hasPermission } from '@/security/permissionState'
import { parseExactJson, stringifyExactJson, isExactNumber } from '@/utils/exactJson'
import request from '@/api/request'
import { compareResourceCase } from '@/utils/resourceTestCases'
export default {
  props: {
    kind: { type: String, required: true },
    owner: { type: Object, required: true },
    endpoint: { type: String, required: true },
    params: { type: String, required: true },
    outcome: { default: null, type: [Object, String] }
  },
  emits: ['apply', 'context'],
  data() { return { cases: [], selectedId: null, saving: false, error: false, loadId: 0, suspended: false } },
  computed: {
    editable() { return hasPermission(({ VARIABLE: 'field', MODEL: 'model', API: 'datasource' })[this.kind]+':edit',this.owner) },
    selected() { return this.cases.find(item => (item.id || item.code) === this.selectedId) },
    verdict() {
      if (!this.selected || this.outcome == null || this.outcome === '') return ''
      try {
        if (stringifyExactJson(parseExactJson(this.params)) !== stringifyExactJson(parseExactJson(this.selected.paramsJson))) return ''
        const result = typeof this.outcome === 'string' ? JSON.parse(this.outcome) : this.outcome
        return compareResourceCase(result, this.selected.expected) ? '通过' : '未通过'
      } catch { return '' }
    }
  },
  watch: { endpoint: { immediate: true, handler: 'load' } },
  deactivated() { this.suspended = true; this.loadId++ },
  activated() {
    if (this.suspended) { this.suspended = false; this.load(true) }
  },
  methods: {
    async saveInput() {
      const row = this.selected
      try {
        const params = parseExactJson(this.params)
        if (!params || typeof params !== 'object' || Array.isArray(params) || isExactNumber(params)) throw new Error('输入参数必须是 JSON 对象')
        this.saving = true
        const submitted = this.params
        await saveResourceCaseInput(this.kind,this.owner.id,row.id,submitted,row.paramsJson)
        row.paramsJson = submitted
        this.$message.success('用例输入已保存')
      } catch (error) { this.$message.error(error.message || '保存用例失败') }
      finally { this.saving = false }
    },
    expectationLabel(check) {
      if (check.op === 'reportPath') return '应为 /reports 下的征信报告路径'
      if (check.op === 'between') return '应在 ' + check.value.join(' 至 ') + ' 之间（含边界）'
      return '应等于 ' + JSON.stringify(check.value)
    },
    async load(resume = false) {
      const id = ++this.loadId
      const previous = resume === true ? this.selected : null
      this.error = false
      if (!previous) { this.cases = []; this.selectedId = null }
      if (!this.endpoint) return
      try {
        const res = await request.get(this.endpoint)
        if (id !== this.loadId) return
        this.cases = res.data.map(row => ({ ...row, expected: JSON.parse(row.expectedJson) }))
        if (!previous || previous.id !== this.selectedId) return
        if (!this.selected) {
          this.selectedId = null
          this.$emit('context', null)
        } else if (this.selected.paramsJson !== previous.paramsJson || this.selected.contextDefinitionId !== previous.contextDefinitionId) {
          if (this.params === stringifyExactJson(parseExactJson(previous.paramsJson), 2)) this.apply()
          else {
            this.selectedId = null
            this.$emit('context', null)
            this.$message.warning('固定用例已更新，当前输入已保留。重新选择用例可载入最新值。')
          }
        }
      } catch { if (id === this.loadId) this.error = true }
    },
    apply() { this.$emit('context', this.selected?.contextDefinitionId ?? null); if (this.selected) this.$emit('apply', stringifyExactJson(parseExactJson(this.selected.paramsJson),2)) }
  }
}
</script>
