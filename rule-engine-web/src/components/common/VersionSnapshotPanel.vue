<template>
  <div class="version-snapshot-panel">
    <slot>
      <resource-version-preview :resource-type="resourceType" :snapshot="businessRight"
        :comparing="comparing" :original="businessLeft" :original-label="originalLabel" :modified-label="modifiedLabel"
        :original-version="comparing && historical ? original : originalVersion" :modified-version="historical ? snapshot : modifiedVersion" />
    </slot>
    <el-collapse v-model="rawPanels" class="version-raw-collapse">
      <el-collapse-item v-if="$slots.detail" title="逐项变更明细" name="detail">
        <div v-if="rawPanels.includes('detail')" class="version-detail-scroll"><slot name="detail" /></div>
      </el-collapse-item>
      <el-collapse-item title="查看原始 JSON（完整配置）" name="raw">
        <template v-if="rawPanels.includes('raw')">
          <json-version-diff v-if="comparing" :original="rawLeft" :modified="rawRight"
            :original-label="originalLabel" :modified-label="modifiedLabel" />
          <pre v-else class="version-preview">{{ formatSnapshotJson(rawRight) }}</pre>
          <slot name="technical" />
        </template>
      </el-collapse-item>
    </el-collapse>
  </div>
</template>
<script setup>
import { computed, ref, watch } from 'vue'
import ResourceVersionPreview from './ResourceVersionPreview.vue'
import JsonVersionDiff from './JsonVersionDiff.vue'
import { historicalSnapshot, formatSnapshotJson } from '@/utils/versionSnapshot'

const props = defineProps({
  resourceType: { type: String, required: true },
  snapshot: { type: [String, Object], required: true },
  original: { type: [String, Object], default: '' },
  originalLabel: { type: String, default: '基准版本' }, modifiedLabel: { type: String, default: '当前版本' },
  comparing: Boolean, historical: Boolean, modelType: { type: String, default: '' },
  originalVersion: { type: Object, default: null }, modifiedVersion: { type: Object, default: null },
})
const business = value => props.historical ? historicalSnapshot(props.resourceType, value, props.modelType) : value
const businessRight = computed(() => business(props.snapshot))
const businessLeft = computed(() => props.comparing ? business(props.original) : '')
const rawRight = computed(() => props.historical && props.resourceType === 'FUNCTION' ? props.snapshot.functionJson : props.snapshot)
const rawLeft = computed(() => props.historical && props.resourceType === 'FUNCTION' ? props.original?.functionJson : props.original)
const rawPanels = ref([])
watch(() => [props.resourceType, props.snapshot, props.original, props.comparing, props.modifiedLabel, props.originalLabel], () => { rawPanels.value = [] })
</script>
<style scoped>
.version-snapshot-panel { min-width: 0; }
.version-raw-collapse { margin: 16px 0; }
.version-detail-scroll { max-width: 100%; overflow-x: auto; }
.version-preview { max-height: 420px; overflow: auto; margin: 0; padding: 12px; white-space: pre-wrap; overflow-wrap: anywhere; color: var(--tianshu-text-primary); background: var(--tianshu-bg-soft); }
</style>
