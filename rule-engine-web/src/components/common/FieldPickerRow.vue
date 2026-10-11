<template>
  <div class="fp-row" :class="{ 'fp-row--selected': selected, 'fp-row--parent': node.children.length, 'fp-row--disabled': !node.selectable && node.item && !node.item._versionGroup }" :style="{ '--field-depth': depth }" role="row" :aria-selected="selected" :aria-level="depth + 1" :aria-expanded="node.children.length ? expanded : undefined" tabindex="0" @click="$emit('preview', $event)" @dblclick.stop="$emit('confirm')" @keydown.enter.prevent.stop="$emit('confirm')" @keydown.space.prevent.stop="$emit('preview', $event)">
    <span role="gridcell"><span class="fp-type" :class="'fp-type--' + typeFamily" :title="typeName" :aria-label="typeName">{{ icon }}</span></span>
    <span class="fp-name" role="gridcell" :title="node.name" :style="{ paddingLeft: Math.min(depth, 6) * 12 + 'px' }">{{ node.children.length ? (expanded ? '▾ ' : '▸ ') : '' }}{{ node.name }}</span>
    <code class="fp-code" role="gridcell" :title="node.code">{{ node.code }}</code>
    <span class="fp-version" role="gridcell" @click.stop @dblclick.stop @keydown.stop><slot name="version" /></span>
  </div>
</template>
<script>
import { fieldTypeIcon, pickerTypeMatches } from '@/utils/fieldPicker'
import { varTypeLabel } from '@/constants/varTypes'
export default {
  name: 'FieldPickerRow',
  props: { node: { type: Object, required: true }, depth: { type: Number, default: 0 }, selected: Boolean, expanded: Boolean },
  emits: ['preview', 'confirm'],
  computed: {
    icon() { return fieldTypeIcon(this.node.type) },
    typeName() { return varTypeLabel(this.node.type) || this.node.type },
    typeFamily() { return pickerTypeMatches(this.node.type, 'NUMBER') ? 'number' : ['LIST', 'ARRAY', 'MAP'].includes(this.node.type) ? 'collection' : this.node.type === 'BOOLEAN' ? 'boolean' : 'text' },
  },
}
</script>
<style scoped>
.fp-row { display:grid; grid-template-columns:48px minmax(70px,1fr) minmax(70px,1.15fr) 130px; align-items:center; gap:0; height:36px; box-sizing:border-box; padding:0 8px; border-bottom:1px solid var(--tianshu-border-subtle); color:var(--tianshu-text-primary); cursor:pointer; background:color-mix(in srgb,var(--el-color-primary) calc(min(var(--field-depth),5) * 3%),var(--tianshu-bg-surface)); }
.fp-row:hover,.fp-row:focus-visible { background:var(--tianshu-bg-hover); outline:1px solid var(--el-color-primary); outline-offset:-1px; }
.fp-row--selected { background:var(--tianshu-info-bg); box-shadow:inset 3px 0 var(--el-color-primary); }
.fp-row--parent .fp-name { color:var(--tianshu-primary-text, var(--el-color-primary)); font-weight:600; }
.fp-row--disabled { opacity:.55; }
.fp-row > span,.fp-code { min-width:0; }
.fp-name,.fp-code { display:block; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; padding-right:8px; }
.fp-code { color:var(--tianshu-text-secondary); font-size:11px; }
.fp-type { display:inline-flex; width:28px; height:28px; align-items:center; justify-content:center; font-size:11px; font-weight:600; color:var(--tianshu-primary-text, var(--el-color-primary)); background:var(--tianshu-info-bg); }
.fp-type--number { color:var(--tianshu-success-text); background:var(--tianshu-success-bg); }
.fp-type--collection { color:var(--tianshu-warning-text); background:var(--tianshu-warning-bg); }
.fp-type--boolean { color:var(--tianshu-info-text); background:var(--tianshu-info-bg); }
.fp-version { overflow:hidden; }
@media(max-width:640px) { .fp-row { grid-template-columns:40px minmax(45px,1fr) minmax(45px,1fr) 96px; } }
</style>
