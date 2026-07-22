<template>
  <el-dialog :model-value="modelValue" title="模型引用影响确认" width="680px" @update:model-value="$emit('update:modelValue', $event)">
    <div v-loading="loading" class="impact-body">
      <el-alert title="旧的已发布决策制品包含模型固化副本，不会被此次操作改写。" type="info" :closable="false" show-icon />
      <div v-if="analysis" class="impact-summary">
        <span>影响摘要</span>
        <code>{{ analysis.impactDigest }}</code>
        <pre>{{ prettyReport }}</pre>
      </div>
      <el-checkbox v-if="analysis" v-model="confirmed">我已核对当前引用关系，并确认继续</el-checkbox>
    </div>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button data-testid="confirm-impact" type="primary" :disabled="!analysis || !confirmed" @click="confirm">使用此影响分析继续</el-button>
    </template>
  </el-dialog>
</template>

<script>
import { analyzeModelImpact } from '@/api/model'

export default {
  name: 'ModelImpactDialog',
  props: {
    modelValue: { type: Boolean, default: false },
    modelId: { type: Number, required: true },
    action: { type: String, required: true }
  },
  emits: ['update:modelValue', 'confirmed'],
  data() { return { loading: false, analysis: null, confirmed: false } },
  computed: {
    prettyReport() {
      if (!this.analysis || !this.analysis.reportJson) return ''
      try { return JSON.stringify(JSON.parse(this.analysis.reportJson), null, 2) } catch { return this.analysis.reportJson }
    }
  },
  watch: {
    modelValue: { immediate: true, handler(value) { if (value) this.loadImpact() } },
    action() { if (this.modelValue) this.loadImpact() }
  },
  methods: {
    async loadImpact() {
      this.loading = true
      this.confirmed = false
      try {
        const response = await analyzeModelImpact(this.modelId, this.action)
        this.analysis = response.data
      } finally { this.loading = false }
    },
    confirm() { this.$emit('confirmed', { action: this.action, impactToken: this.analysis.analysisToken, analysis: this.analysis }) }
  }
}
</script>

<style scoped>
.impact-body { display: grid; gap: 16px; }
.impact-summary { display: grid; gap: 8px; }
.impact-summary > span { color: #6b7280; font-size: 12px; font-weight: 600; }
.impact-summary code { overflow-wrap: anywhere; }
.impact-summary pre { max-height: 280px; margin: 0; padding: 12px; overflow: auto; border-radius: 4px; background: #f6f8fb; color: #334155; font-size: 12px; line-height: 1.5; }
</style>
