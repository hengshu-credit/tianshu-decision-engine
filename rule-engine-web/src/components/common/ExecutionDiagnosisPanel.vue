<template>
  <section class="execution-diagnosis" aria-label="执行排障指引">
    <h3>执行排障 · {{ diagnosis.mode }}</h3>
    <p>修订 {{ diagnosis.revisionId || '未记录' }} · 制品 {{ diagnosis.artifactDigest || '未记录' }}</p>
    <strong>失败阶段</strong>
    <ul v-if="diagnosis.failures.length"><li v-for="(failure, i) in diagnosis.failures" :key="i">{{ failure.stageLabel }}：{{ failure.message }} <el-button v-if="resource(failure)" link type="primary" @click="$router.push(resource(failure).path)">查看对应配置</el-button></li></ul>
    <p v-else>{{ Number(detail.success) === 1 ? '本条记录未报告失败阶段。' : '历史记录未提供结构化失败阶段，请结合错误信息及原始追踪核对。' }}</p>
    <p>{{ diagnosis.snapshot ? '已保留执行时设计快照，可从追踪核对当时的条件和动作。' : '未保留完整设计快照，不使用当前配置推测当时执行情况。' }} <el-button v-if="detail.traceInfo" link type="primary" @click="$emit('view-trace')">查看执行时追踪</el-button></p>
    <strong>已记录的副作用</strong>
    <ul v-if="diagnosis.calls.length"><li v-for="(call, i) in diagnosis.calls" :key="i">{{ call.stageLabel }} · {{ statusName(call.status) }}<span v-if="call.cacheStatus"> · 缓存 {{ call.cacheStatus }}</span><code v-if="call.traceId"> · {{ call.traceId }}</code> <el-button v-if="resource(call)" link type="primary" @click="$router.push(resource(call).path)">查看对应配置</el-button></li></ul>
    <p v-if="diagnosis.calls.length">模块成功不等于已向供应商发起新请求或已计费，请结合缓存状态与对应调用日志核对。</p>
    <ul v-if="diagnosis.effects.length"><li v-for="(effect, i) in diagnosis.effects" :key="i">名单动作：{{ effect.message }}<code v-if="effect.taskId"> · {{ effect.taskId }}</code></li></ul>
    <p v-else>未记录名单动作证据，不等于执行期间没有外部调用、计费或写入。</p>
    <strong>重试条件</strong><p>{{ diagnosis.retry }}</p>
  </section>
</template>
<script>
import { executionDiagnosis, diagnosticResource } from '@/utils/executionDiagnosis'
import { hasPermission } from '@/security/permissionState'
export default {
  props: { detail: { type: Object, required: true } }, emits: ['view-trace'],
  computed: { diagnosis() { return executionDiagnosis(this.detail) } },
  methods: {
    statusName(value) { return ({ SUCCESS: '模块执行成功', FAILED: '失败', RUNNING: '运行中', WAITING: '等待中', TIMEOUT: '超时' })[value] || value || '未记录状态' },
    resource(event) { const target = diagnosticResource(event); return target && hasPermission(target.permission, { projectId: this.detail.projectId }) ? target : null },
  },
}
</script>
<style scoped>
.execution-diagnosis { margin-bottom: 16px; padding: 14px 16px; background: var(--tianshu-primary-soft-bg); border-left: 3px solid var(--tianshu-primary-text); color: var(--tianshu-text-primary); font-size: 13px; line-height: 1.7; overflow-wrap: anywhere; }
.execution-diagnosis h3 { margin: 0 0 10px; font-size: 15px; }
.execution-diagnosis p { margin: 8px 0; color: var(--tianshu-text-secondary); }
.execution-diagnosis ul { padding-left: 20px; }
</style>
