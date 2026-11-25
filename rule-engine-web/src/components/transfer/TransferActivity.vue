<template>
  <section v-if="activity" class="transfer-activity" :aria-label="activity.title + '进度'">
    <div class="progress-line">
      <el-progress :percentage="activity.percent" :status="barStatus" :show-text="false" />
      <span>{{ activity.percent }}%</span>
    </div>
    <button type="button" class="current-action" :aria-label="'查看' + activity.title + '执行日志'" @click="visible = true">
      {{ currentMessage }}
    </button>
    <el-dialog v-if="active" v-model="visible" :title="activity.title + '执行日志'" width="720px" append-to-body>
      <ol class="action-log">
        <li v-for="(entry, index) in activity.entries" :key="index">
          <time>{{ formatTime(entry.time) }}</time><span>{{ entry.message }}</span>
        </li>
      </ol>
      <template v-if="hasResult" #footer>
        <el-button @click="openResult">查看导入结果</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, ref, watch } from 'vue'

const props = defineProps({ activity: Object, active: Boolean, hasResult: Boolean })
const emit = defineEmits(['show-result'])
const visible = ref(false)
function openResult() {
  visible.value = false
  emit('show-result')
}
const currentMessage = computed(() => props.activity?.entries.at(-1)?.message || props.activity?.title)
const barStatus = computed(() => ({ success: 'success', failed: 'exception', warning: 'warning' })[props.activity?.status])
const timeFormatter = new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false })
const formatTime = value => timeFormatter.format(value)
watch(() => [props.active, props.activity], () => { visible.value = false })
</script>

<style scoped>
.transfer-activity { display: grid; gap: 4px; min-width: 0; margin-top: 12px; }
.progress-line { display: grid; grid-template-columns: minmax(0, 1fr) auto; align-items: center; gap: 8px; font-size: 12px; font-variant-numeric: tabular-nums; }
.current-action { width: fit-content; max-width: 100%; min-width: 0; padding: 2px 0; border: 0; background: transparent; color: var(--el-color-primary); font: inherit; font-size: 13px; line-height: 1.5; text-align: left; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; cursor: pointer; }
.current-action:hover, .current-action:focus-visible { text-decoration: underline; }
.action-log { margin: 0; padding: 0; list-style: none; }
.action-log li { display: grid; grid-template-columns: auto minmax(0, 1fr); align-items: baseline; gap: 12px; padding: 6px 0; }
.action-log time { color: var(--el-text-color-secondary); font-variant-numeric: tabular-nums; }
.action-log span { overflow-wrap: anywhere; }
</style>
