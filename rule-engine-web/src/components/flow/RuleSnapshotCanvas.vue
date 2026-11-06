<template>
  <div class="snapshot-graph">
    <div ref="container" class="snapshot-graph__canvas" role="img" aria-label="规则节点与连线" />
    <el-alert v-if="error" type="warning" :closable="false" title="此版本的画布无法显示，可查看节点明细或原始 JSON。" />
  </div>
</template>
<script setup>
import { onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { DynamicGroup } from '@logicflow/extension'
import { createGraphCanvas } from './graphCanvas'
import { convertLegacyModel } from './flowDesignerGraph'
import { prepareLogicFlowDataForRender } from './edgeLineType'

const props = defineProps({ graph: { type: Object, required: true } })
const container = ref(null)
const error = ref(false)
let canvas
let observer

function render() {
  if (!container.value?.clientWidth) return
  try {
    canvas ??= createGraphCanvas({
      container: container.value, plugins: [DynamicGroup], isSilentMode: true, keyboard: { enabled: false },
      stopScrollGraph: true, stopZoomGraph: true, adjustNodePosition: false,
    })
    // LogicFlow normalizes its input in place. Both preview panes own a separate copy.
    const model = JSON.parse(JSON.stringify(props.graph))
    const data = model.logicflow || convertLegacyModel(model)
    canvas.render(prepareLogicFlowDataForRender(data, model.defaultEdgeLineType))
    if (data.nodes.length) canvas.fitView(20, 20)
    error.value = false
  } catch {
    canvas?.destroy()
    canvas = null
    error.value = true
  }
}
onMounted(() => {
  observer = new ResizeObserver(render)
  observer.observe(container.value)
})
watch(() => props.graph, render, { flush: 'post' })
onBeforeUnmount(() => { observer?.disconnect(); canvas?.destroy() })
</script>
<style scoped>
.snapshot-graph__canvas { width: 100%; height: 280px; overflow: hidden; border: 1px solid var(--tianshu-border-subtle); border-radius: 6px; box-sizing: border-box; }
</style>
