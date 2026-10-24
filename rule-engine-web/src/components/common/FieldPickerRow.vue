<template>
  <div class="fp-row" :class="{ 'fp-row--selected': selected, 'fp-row--parent': node.children.length, 'fp-row--disabled': !node.selectable && node.item && !node.item._versionGroup }" :style="{ '--field-depth': depth }" role="row" :aria-selected="selected" :aria-level="depth + 1" :aria-expanded="node.children.length ? expanded : undefined" tabindex="0" @click.stop="$emit('preview', $event)" @dblclick.stop="$emit('confirm')" @keydown.enter.prevent.stop="$emit('confirm')" @keydown.space.prevent.stop="$emit('preview', $event)">
    <div class="fp-content" :style="{ paddingLeft: Math.min(depth, 6) * 12 + 'px' }"><span v-if="node.children.length" class="fp-branch">{{ expanded ? '▾' : '▸' }}</span><content-reference :value="content" cell-role="gridcell"><template #version><span @click.stop @dblclick.stop @keydown.stop><slot name="version" /></span></template></content-reference></div>
    <button v-if="node.item && node.selectable && node.children.length" type="button" class="fp-select-parent" title="选择整个字段" @click.stop="$emit('confirm')">选</button>
  </div>
</template>
<script>
import ContentReference from './ContentReference.vue'
export default {
  name: 'FieldPickerRow',
  components: { ContentReference },
  props: { node: { type: Object, required: true }, depth: { type: Number, default: 0 }, selected: Boolean, expanded: Boolean },
  emits: ['preview', 'confirm'],
  computed: {
    content() { return { ...this.node.item, varLabelText: this.node.name, varCode: this.node.code, varType: this.node.type,
      _refType: this.node.item?._refType || ({ object: 'DATA_OBJECT_ROOT', model: 'MODEL', function: 'FUNCTION', external: 'EXTERNAL_API', constant: 'CONSTANT' })[this.node.category] || 'VARIABLE', version: this.node.item?.version ?? this.node.version } },
  },
}
</script>
<style scoped>
.fp-row { display:grid; grid-template-columns:minmax(0,1fr) 28px; align-items:center; gap:0; height:36px; box-sizing:border-box; padding:0 8px; border-bottom:1px solid var(--tianshu-border-subtle); color:var(--tianshu-text-primary); cursor:pointer; background:color-mix(in srgb,var(--el-color-primary) calc(min(var(--field-depth),5) * 3%),var(--tianshu-bg-surface)); }
.fp-row:hover,.fp-row:focus-visible { background:var(--tianshu-bg-hover); outline:1px solid var(--el-color-primary); outline-offset:-1px; }
.fp-row--selected { background:var(--tianshu-info-bg); box-shadow:inset 3px 0 var(--el-color-primary); }
.fp-row--parent .fp-name { color:var(--tianshu-primary-text, var(--el-color-primary)); font-weight:600; }
.fp-row--disabled { opacity:.55; }
.fp-content { display:flex; min-width:0; align-items:center; }
.fp-content :deep(.content-ref) { width:100%; }
.fp-content :deep(.content-ref__name), .fp-content :deep(.content-ref__code) { flex:1; }
.fp-content :deep(.content-ref__version) { flex:0 0 130px; padding:0; }
.fp-branch { flex:none; width:12px; }
.fp-select-parent { width:22px; height:22px; padding:0; border:1px solid var(--tianshu-border); color:var(--tianshu-primary-text,var(--el-color-primary)); background:var(--tianshu-bg-surface); cursor:pointer; opacity:0; }
.fp-row:hover .fp-select-parent,.fp-row:focus-within .fp-select-parent { opacity:1; }
@media(max-width:640px) { .fp-content :deep(.content-ref__version) { flex-basis:96px; } }
</style>
