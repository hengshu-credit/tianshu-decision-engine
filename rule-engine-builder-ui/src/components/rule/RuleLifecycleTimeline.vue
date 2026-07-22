<template>
  <section class="lifecycle-timeline" aria-label="规则审计时间线">
    <div v-for="event in events" :key="event.id || `${event.action}-${event.createTime}`" class="timeline-row">
      <span class="timeline-dot" />
      <div>
        <div class="timeline-heading">
          <strong>{{ actionLabel(event.action) }}</strong>
          <el-tag size="small" type="info">{{ event.fromState || '—' }} → {{ event.toState }}</el-tag>
        </div>
        <div class="timeline-meta">{{ event.actor || 'SYSTEM_CONSOLE' }} · {{ event.createTime || '—' }}</div>
        <div v-if="event.comment" class="timeline-comment">{{ event.comment }}</div>
        <code v-if="event.artifactDigest" class="timeline-digest">{{ event.artifactDigest }}</code>
      </div>
    </div>
    <el-empty v-if="!events.length" description="暂无生命周期事件" />
  </section>
</template>

<script>
export default {
  name: 'RuleLifecycleTimeline',
  props: { events: { type: Array, default: () => [] } },
  methods: {
    actionLabel(action) {
      return ({ CREATE_DRAFT: '创建草稿', SUBMIT: '提交评审', RETURN_TO_DRAFT: '退回草稿', APPROVE: '批准', PUBLISH: '发布', OFFLINE: '下线', AUTO_OFFLINE: '自动下线', IMPORT_APPROVED_ARTIFACT: '导入制品' })[action] || action
    }
  }
}
</script>

<style scoped>
.lifecycle-timeline { display: grid; gap: 16px; }
.timeline-row { display: grid; grid-template-columns: 12px minmax(0, 1fr); gap: 12px; }
.timeline-dot { width: 10px; height: 10px; margin-top: 5px; border-radius: 50%; background: #315ca8; box-shadow: 0 0 0 4px #eaf0fb; }
.timeline-heading { display: flex; align-items: center; gap: 8px; }
.timeline-meta, .timeline-comment { margin-top: 4px; color: #6b7280; font-size: 13px; }
.timeline-digest { display: block; margin-top: 8px; color: #475569; overflow-wrap: anywhere; }
</style>
