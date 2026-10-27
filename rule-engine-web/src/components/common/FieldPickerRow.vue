<template>
  <div class="fp-row" :class="{ 'fp-row--selected': selected, 'fp-row--parent': node.children.length, 'fp-row--disabled': !node.selectable && node.item && !node.item._versionGroup }" :style="{ '--field-depth': depth }" role="row" :aria-selected="selected" :aria-level="depth + 1" :aria-expanded="node.children.length ? expanded : undefined" tabindex="0" @click.stop="$emit('preview', $event)" @dblclick.stop="$emit('confirm')" @keydown.enter.prevent.stop="$emit('confirm')" @keydown.space.prevent.stop="$emit('preview', $event)">
    <div class="fp-content">
      <div v-for="(part, index) in columns" :key="part.key" class="fp-cell" :class="'fp-cell--' + part.key" :style="{ gridColumn: index + 1 }" role="gridcell" :aria-label="part.title" :title="part.title">
        <span v-if="part.key === 'version'" class="fp-version-cell" @click.stop @dblclick.stop @keydown.stop><slot name="version"><span :class="partClass(part)">{{ part.text }}</span></slot></span>
        <span v-else :class="partClass(part)" :data-content-part="part.key" :style="part.key === 'name' ? { marginLeft: Math.min(depth, 3) * 8 + 'px' } : {}"><span v-if="part.key === 'name' && node.children.length" class="fp-branch" aria-hidden="true">{{ expanded ? '▾' : '▸' }}</span>{{ part.text }}</span>
        <button v-if="part.key === 'name' && node.item && node.selectable && node.children.length" type="button" class="fp-select-parent" title="选择整个字段" @click.stop="$emit('confirm')">选</button>
      </div>
    </div>
  </div>
</template>
<script>
import { contentReferenceParts } from '@/utils/contentReference'
import { FIELD_PICKER_COLUMNS } from '@/utils/fieldPickerPreferences'
export default {
  name: 'FieldPickerRow',
  props: { node: { type: Object, required: true }, depth: { type: Number, default: 0 }, selected: Boolean, expanded: Boolean },
  emits: ['preview', 'confirm'],
  computed: {
    content() { return { ...this.node.item, varLabelText: this.node.name, varCode: this.node.code, varType: this.node.type,
      _refType: this.node.item?._refType || ({ object: 'DATA_OBJECT_ROOT', model: 'MODEL', function: 'FUNCTION', external: 'EXTERNAL_API', constant: 'CONSTANT' })[this.node.category] || 'VARIABLE', version: this.node.item?.version ?? this.node.version } },
    columns() {
      const parts = contentReferenceParts(this.content)
      return FIELD_PICKER_COLUMNS.map(({ key }) => {
        const part = parts.filter(part => part.key === key)
        return { key, text: part.map(item => item.text).join(' · ') || '—', title: part.map(item => item.title).join(' · ') || '—', family: part[0]?.family }
      })
    },
  },
  methods: { partClass(part) { return `fp-cell-label content-ref__${part.key}` } },
}
</script>
<style scoped>
.fp-row { display:grid; grid-template-columns:var(--fp-table-columns); width:100%; min-width:var(--fp-table-min-width); align-items:center; gap:0; height:36px; box-sizing:border-box; padding:0 8px; border-bottom:1px solid var(--tianshu-border-subtle); color:var(--tianshu-text-primary); cursor:pointer; background:var(--tianshu-bg-surface); }
.fp-row:hover,.fp-row:focus-visible { background:var(--tianshu-bg-hover); outline:1px solid var(--el-color-primary); outline-offset:-1px; }
.fp-row--selected { background:var(--tianshu-info-bg); box-shadow:inset 3px 0 var(--el-color-primary); }
.fp-row--parent .fp-name { color:var(--tianshu-primary-text, var(--el-color-primary)); font-weight:600; }
.fp-row--disabled { opacity:.55; }
.fp-content { display:contents; }
.fp-cell { display:flex; align-items:center; min-width:0; height:100%; padding:0 4px; border-right:1px solid var(--tianshu-border-subtle); overflow:hidden; }
.fp-cell .fp-cell-label { flex:0 1 auto; width:auto; min-width:0; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; padding:0; color:var(--tianshu-text-primary); background:none; font:inherit; text-align:left; }
.fp-cell .content-ref__code { font-family:Consolas,monospace; }
.fp-row--parent .content-ref__name { font-weight:600; }
.fp-version-cell { width:100%; min-width:0; }
.fp-branch { flex:none; width:12px; }
.fp-select-parent { flex:none; margin-left:auto; width:22px; height:22px; padding:0; border:1px solid var(--tianshu-border); color:var(--tianshu-primary-text,var(--el-color-primary)); background:var(--tianshu-bg-surface); cursor:pointer; opacity:0; }
.fp-row:hover .fp-select-parent,.fp-row:focus-within .fp-select-parent { opacity:1; }
</style>
