<template>
  <section class="execution-impact" aria-label="执行模式与影响清单">
    <div class="impact-heading"><strong>执行模式：真实联调</strong><el-tag size="small" type="warning">会实际调用或写入</el-tag></div>
    <p class="impact-summary">外部调用、名单写入及计费按真实配置发生。版本来源决定执行哪份配置，选择草稿不会自动变为离线试算。</p>
    <details :open="loaded && !!(impact.calls.length || impact.writes.length)">
      <summary>{{ loaded ? `调用与写入清单：${impact.calls.length + impact.writes.length} 项静态识别结果` : '依赖清单尚未加载完整' }}（动态调用仍需核对）</summary>
      <template v-if="loaded">
      <strong>可能发生的调用</strong>
      <ul v-if="impact.calls.length"><li v-for="call in impact.calls" :key="`${call.type}:${call.id}`">{{ call.label }}</li></ul>
      <p v-else>当前结构化配置中未识别到外部取值，仍需核对脚本、函数及子规则。</p>
      <strong>名单写入</strong>
      <ul v-if="impact.writes.length"><li v-for="write in impact.writes" :key="`${write.id}:${write.operation}`">名单 #{{ write.id }} · {{ operations[write.operation] || write.operation }}</li></ul>
      <p v-else>当前结构化动作中未识别到名单写入。</p>
      <p v-if="impact.dynamic">含脚本、函数或子规则时，静态清单不能穷尽动态调用及写入，请同时核对其配置。</p>
      </template>
      <p v-else>依赖清单尚未加载完整，不能据此判断本次执行没有副作用。</p>
      <p>可能计费：引擎执行及外数、数据库等调用按真实计费配置处理；缓存、重试及分支会影响实际发生次数。金额以账单为准。</p>
    </details>
  </section>
</template>
<script>
import { executionImpact } from '@/utils/executionImpact'
export default {
  props: { runtimeNodes: { type: Array, default: () => [] }, model: { type: Object, default: null }, modelType: { type: String, default: '' }, loaded: Boolean },
  data: () => ({ operations: { MERGE: '合并更新', OVERWRITE: '覆盖', ADD_ONLY: '仅新增', REMOVE: '移除' } }),
  computed: { impact() { return executionImpact(this.runtimeNodes, this.model, this.modelType) } },
}
</script>
<style scoped>
.execution-impact { display: flex; flex-wrap: wrap; gap: 4px 16px; flex-shrink: 0; padding: 8px 12px; margin: 0 0 12px; border-left: 3px solid var(--tianshu-primary-text); background: var(--tianshu-primary-soft-bg); color: var(--tianshu-text-primary); font-size: 12px; line-height: 1.7; overflow-wrap: anywhere; }
.execution-impact .impact-summary { order: 1; flex-basis: 100%; margin: 0; }
.execution-impact details[open] { order: 2; flex-basis: 100%; }
.execution-impact summary { cursor: pointer; color: var(--tianshu-primary-text); }
.execution-impact > div { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.execution-impact p { margin: 6px 0; color: var(--tianshu-text-secondary); }
.execution-impact ul { margin: 6px 0; padding-left: 20px; }
</style>
