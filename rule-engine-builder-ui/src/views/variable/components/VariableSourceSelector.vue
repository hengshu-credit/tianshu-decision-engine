<template>
  <div
    class="source-selector"
    role="radiogroup"
    aria-label="字段取值方式"
  >
    <button
      v-for="item in items"
      :key="item.value"
      type="button"
      class="source-option"
      :class="{ 'is-active': modelValue === item.value }"
      :disabled="disabled && modelValue !== item.value"
      :aria-pressed="modelValue === item.value"
      @click="select(item.value)"
    >
      <span class="source-option__title">
        {{ item.label }}
        <small v-if="countFor(item.value) !== null">
          {{ countFor(item.value) }} 个可用
        </small>
      </span>
      <span class="source-option__help">{{ item.help }}</span>
    </button>
  </div>
</template>

<script>
const ITEMS = [
  {
    value: 'INPUT',
    label: '请求输入',
    help: '由调用方在执行规则时传入',
  },
  {
    value: 'API',
    label: '外数接口',
    help: '执行时调用已经审批生效的 API',
  },
  {
    value: 'DB',
    label: '数据库查询',
    help: '通过只读 SQL 获取业务数据',
  },
  {
    value: 'LIST',
    label: '名单匹配',
    help: '判断输入是否命中一个或多个名单',
  },
  {
    value: 'COMPUTED',
    label: '规则计算结果',
    help: '由规则动作或表达式在运行中写入',
  },
  {
    value: 'DERIVED',
    label: '衍生变量',
    help: '上游字段计算、内部进件聚合与多级关联',
  },
  {
    value: 'CONSTANT',
    label: '固定常量',
    help: '始终使用配置好的固定值',
  },
]

export default {
  name: 'VariableSourceSelector',
  props: {
    modelValue: {
      type: String,
      default: 'INPUT',
    },
    disabled: {
      type: Boolean,
      default: false,
    },
    counts: {
      type: Object,
      default: () => ({}),
    },
  },
  emits: ['update:modelValue', 'change'],
  data() {
    return { items: ITEMS }
  },
  methods: {
    countFor(source) {
      if (!['API', 'DB', 'LIST'].includes(source)) return null
      const value = Number(this.counts[source])
      return Number.isFinite(value) ? value : 0
    },
    select(value) {
      if (this.disabled && value !== this.modelValue) return
      this.$emit('update:modelValue', value)
      this.$emit('change', value)
    },
  },
}
</script>

<style scoped lang="scss">
.source-selector {
  display: grid;
  width: 100%;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.source-option {
  display: flex;
  min-width: 0;
  min-height: 76px;
  flex-direction: column;
  align-items: flex-start;
  padding: 11px 12px;
  border: 1px solid var(--tianshu-border-subtle);
  border-radius: 8px;
  background: var(--tianshu-bg-surface);
  color: var(--tianshu-text-tertiary);
  cursor: pointer;
  text-align: left;
}

.source-option:hover,
.source-option.is-active {
  border-color: var(--tianshu-info-border);
  background: var(--tianshu-info-bg);
  box-shadow: 0 5px 14px var(--tianshu-primary-shadow);
}

.source-option:disabled {
  cursor: not-allowed;
  opacity: 0.48;
}

.source-option__title {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  color: var(--tianshu-text-primary);
  font-size: 13px;
  font-weight: 700;
}

.source-option__title small {
  color: var(--tianshu-info-text);
  font-size: 10px;
  font-weight: 600;
}

.source-option__help {
  margin-top: 6px;
  color: var(--tianshu-text-tertiary);
  font-size: 11px;
  line-height: 1.45;
}

@media (max-width: 760px) {
  .source-selector {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
