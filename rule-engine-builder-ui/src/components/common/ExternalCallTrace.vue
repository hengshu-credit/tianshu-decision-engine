<template>
  <section class="external-call-trace" :class="{ 'is-compact': compact }" :aria-label="title">
    <div class="external-call-trace-header">
      <div>
        <div class="external-call-trace-title">{{ title }}</div>
        <div v-if="subtitle" class="external-call-trace-subtitle">{{ subtitle }}</div>
      </div>
      <button
        v-if="collapsible && hasSteps"
        type="button"
        class="external-call-trace-toggle"
        :aria-expanded="String(open)"
        @click="open = !open"
      >
        {{ open ? '收起' : '展开' }}
      </button>
    </div>

    <div v-if="!hasSteps" class="external-call-trace-empty" role="status">
      {{ emptyText }}
    </div>
    <div v-else-if="open" class="external-call-trace-steps">
      <article
        v-for="(step, index) in normalizedSteps"
        :key="step.key"
        class="external-call-trace-step"
      >
        <span class="external-call-trace-index">{{ index + 1 }}</span>
        <div class="external-call-trace-main">
          <div class="external-call-trace-step-header">
            <div class="external-call-trace-step-title">
              <span>{{ step.label || step.type || '过程步骤' }}</span>
              <code v-if="step.type && step.label">{{ step.type }}</code>
            </div>
            <span class="external-call-trace-status" :class="statusClass(step.status)">
              {{ statusLabel(step.status) }}
            </span>
          </div>
          <dl v-if="stepMeta(step).length" class="external-call-trace-meta">
            <template v-for="field in stepMeta(step)" :key="field.key">
              <dt>{{ field.label }}</dt>
              <dd>{{ displayValue(field.value) }}</dd>
            </template>
          </dl>
          <div v-if="stepValues(step).length" class="external-call-trace-values">
            <div v-for="field in stepValues(step)" :key="field.key" class="external-call-trace-value">
              <div class="external-call-trace-value-label">{{ field.label }}</div>
              <details v-if="isLong(field.value)" class="external-call-trace-value-details">
                <summary>查看报文（{{ displayValue(field.value).length }} 字符）</summary>
                <pre>{{ displayValue(field.value) }}</pre>
              </details>
              <pre v-else>{{ displayValue(field.value) }}</pre>
            </div>
          </div>
        </div>
      </article>
    </div>
  </section>
</template>

<script>
const VALUE_FIELDS = [
  ['input', '输入'],
  ['output', '输出'],
  ['value', '赋值'],
]

const META_FIELDS = [
  ['attempt', '尝试'],
  ['attemptNo', '尝试'],
  ['refType', '引用类型'],
  ['refId', '引用 ID'],
  ['targetPath', '写入路径'],
  ['resultPath', '取值路径'],
]

export default {
  name: 'ExternalCallTrace',
  props: {
    steps: { type: Array, default: () => [] },
    title: { type: String, default: '外数调用链' },
    subtitle: { type: String, default: '' },
    emptyText: {
      type: String,
      default: '历史记录未保存外数阶段链，仅保留本次调用的摘要。',
    },
    collapsible: { type: Boolean, default: true },
    initialOpen: { type: Boolean, default: true },
    compact: { type: Boolean, default: false },
  },
  data() {
    return { open: this.initialOpen }
  },
  computed: {
    normalizedSteps() {
      return (Array.isArray(this.steps) ? this.steps : [])
        .filter(step => step && typeof step === 'object')
        .map((step, index) => ({
          ...step,
          key: `${step.traceId || step.callId || 'step'}-${index}`,
        }))
    },
    hasSteps() {
      return this.normalizedSteps.length > 0
    },
  },
  methods: {
    hasOwn(step, key) {
      return Object.prototype.hasOwnProperty.call(step, key) && step[key] !== undefined
    },
    stepMeta(step) {
      const seen = new Set()
      return META_FIELDS.filter(([key]) => {
        const normalizedKey = key === 'attemptNo' ? 'attempt' : key
        if (!this.hasOwn(step, key) || seen.has(normalizedKey)) return false
        seen.add(normalizedKey)
        return true
      }).map(([key, label]) => ({ key: label, label, value: step[key] }))
    },
    stepValues(step) {
      return VALUE_FIELDS
        .filter(([key]) => this.hasOwn(step, key))
        .map(([key, label]) => ({ key, label, value: step[key] }))
    },
    displayValue(value) {
      if (value === null) return 'null'
      if (value === undefined) return '-'
      if (typeof value === 'string') return value
      try {
        return JSON.stringify(value, null, 2)
      } catch (error) {
        return String(value)
      }
    },
    isLong(value) {
      return this.displayValue(value).length > 360
    },
    statusLabel(status) {
      return {
        SUCCESS: '成功',
        FAILED: '失败',
        READY: '已准备',
        SENT: '已发送',
        SKIPPED: '已跳过',
        RUNNING: '执行中',
      }[status] || status || '处理中'
    },
    statusClass(status) {
      return `is-${String(status || 'running').toLowerCase()}`
    },
  },
}
</script>

<style scoped>
.external-call-trace {
  display: grid;
  gap: 8px;
  margin-top: 12px;
  padding: 12px;
  border: 1px solid var(--tianshu-border-subtle);
  border-radius: 6px;
  background: var(--tianshu-bg-muted);
}
.external-call-trace-header,
.external-call-trace-step-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.external-call-trace-title { color: var(--tianshu-text-primary); font-weight: 700; }
.external-call-trace-subtitle,
.external-call-trace-empty { color: var(--tianshu-text-tertiary); font-size: 12px; }
.external-call-trace-toggle {
  border: 0;
  background: transparent;
  color: var(--el-color-primary);
  cursor: pointer;
  font-size: 12px;
}
.external-call-trace-steps { display: grid; gap: 6px; }
.external-call-trace-step {
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr);
  gap: 8px;
  align-items: start;
}
.external-call-trace-index {
  display: inline-flex;
  width: 24px;
  height: 24px;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-size: 11px;
  font-weight: 700;
}
.external-call-trace-main {
  min-width: 0;
  padding: 8px 10px;
  border: 1px solid var(--tianshu-border-subtle);
  border-radius: 5px;
  background: var(--tianshu-bg-surface);
}
.external-call-trace-step-title { color: var(--tianshu-text-primary); font-size: 12px; font-weight: 700; }
.external-call-trace-step-title code { margin-left: 5px; color: var(--tianshu-text-tertiary); font-size: 10px; font-weight: 400; }
.external-call-trace-status { color: var(--el-color-primary); font-size: 11px; font-weight: 600; }
.external-call-trace-status.is-failed { color: var(--el-color-danger); }
.external-call-trace-status.is-skipped { color: var(--tianshu-text-tertiary); }
.external-call-trace-meta { display: flex; flex-wrap: wrap; gap: 3px 10px; margin: 6px 0 0; font-size: 11px; }
.external-call-trace-meta dt { color: var(--tianshu-text-tertiary); }
.external-call-trace-meta dd { margin: 0; color: var(--tianshu-text-secondary); overflow-wrap: anywhere; }
.external-call-trace-values { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; margin-top: 7px; }
.external-call-trace-value { min-width: 0; }
.external-call-trace-value-label { margin-bottom: 3px; color: var(--tianshu-text-tertiary); font-size: 10px; }
.external-call-trace-value pre {
  max-height: 160px;
  margin: 0;
  padding: 6px;
  overflow: auto;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  border: 1px solid var(--tianshu-border-subtle);
  border-radius: 3px;
  background: var(--tianshu-bg-soft);
  color: var(--tianshu-text-secondary);
  font: 10px/1.45 Consolas, Monaco, monospace;
}
.external-call-trace-value-details summary { cursor: pointer; color: var(--el-color-primary); font-size: 11px; }
.external-call-trace-value-details pre { margin-top: 4px; }
@media (max-width: 760px) { .external-call-trace-values { grid-template-columns: 1fr; } }
</style>
