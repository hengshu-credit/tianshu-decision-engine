<template>
  <div
    class="rule-diff-lane"
    :class="['is-' + lane.status, 'is-depth-' + depth]"
    :data-diff-key="lane.key"
  >
    <div class="rule-diff-head-lane">
      <div
        class="rule-diff-side rule-diff-side--left"
        :class="sideClass('left')"
      >
        <template v-if="lane.left">
          <div class="rule-diff-title-row">
            <strong class="rule-diff-title">{{ lane.left.title }}</strong>
            <span v-if="markerText('left')" class="rule-diff-marker">{{
              markerText('left')
            }}</span>
          </div>
          <div v-if="lane.left.subtitle" class="rule-diff-subtitle">
            {{ lane.left.subtitle }}
          </div>
        </template>
        <div v-else class="rule-diff-placeholder">此版本无对应内容</div>
      </div>
      <div
        class="rule-diff-side rule-diff-side--right"
        :class="sideClass('right')"
      >
        <template v-if="lane.right">
          <div class="rule-diff-title-row">
            <strong class="rule-diff-title">{{ lane.right.title }}</strong>
            <span v-if="markerText('right')" class="rule-diff-marker">{{
              markerText('right')
            }}</span>
          </div>
          <div v-if="lane.right.subtitle" class="rule-diff-subtitle">
            {{ lane.right.subtitle }}
          </div>
        </template>
        <div v-else class="rule-diff-placeholder">此版本无对应内容</div>
      </div>
    </div>

    <div
      v-for="item in visibleFields"
      :key="item.key"
      class="rule-diff-field-lane"
    >
      <div
        class="rule-diff-field rule-diff-field--left"
        :class="fieldClass(item, 'left')"
      >
        <span class="rule-diff-field-label">{{ item.label }}</span>
        <span class="rule-diff-field-value">{{ fieldText(item, 'left') }}</span>
        <span v-if="item.status === 'modified'" class="rule-diff-field-change"
          >~ 修改</span
        >
        <span
          v-else-if="item.status === 'removed'"
          class="rule-diff-field-change"
          >- 删除</span
        >
      </div>
      <div
        class="rule-diff-field rule-diff-field--right"
        :class="fieldClass(item, 'right')"
      >
        <span class="rule-diff-field-label">{{ item.label }}</span>
        <span class="rule-diff-field-value">{{
          fieldText(item, 'right')
        }}</span>
        <span v-if="item.status === 'modified'" class="rule-diff-field-change"
          >~ 修改</span
        >
        <span v-else-if="item.status === 'added'" class="rule-diff-field-change"
          >+ 新增</span
        >
      </div>
    </div>

    <button
      v-if="hasChildren"
      type="button"
      class="rule-diff-expand"
      :aria-expanded="String(expanded)"
      @click="expanded = !expanded"
    >
      <app-icon :name="expanded ? 'ArrowDown' : 'ArrowRight'" />
      {{ expanded ? '收起明细' : '展开明细' }}（{{ lane.children.length }}）
    </button>
    <div v-if="hasChildren && expanded" class="rule-diff-children">
      <slot name="children" :children="lane.children" />
    </div>
  </div>
</template>

<script>
export default {
  name: 'RuleDiffLane',
  props: {
    lane: {
      type: Object,
      required: true,
    },
    depth: {
      type: Number,
      default: 0,
    },
    showUnchangedFields: {
      type: Boolean,
      default: true,
    },
  },
  data() {
    return {
      expanded: true,
    }
  },
  computed: {
    hasChildren() {
      return Array.isArray(this.lane.children) && this.lane.children.length > 0
    },
    visibleFields() {
      const fields = Array.isArray(this.lane.fields) ? this.lane.fields : []
      return this.showUnchangedFields
        ? fields
        : fields.filter((item) => item.status !== 'unchanged')
    },
  },
  methods: {
    markerText(side) {
      if (this.lane.status === 'added' && side === 'right') return '+ 新增'
      if (this.lane.status === 'removed' && side === 'left') return '- 删除'
      if (this.lane.status === 'modified') return '~ 修改'
      return ''
    },
    sideClass(side) {
      if (this.lane.status === 'added')
        return side === 'right' ? 'is-added' : 'is-empty'
      if (this.lane.status === 'removed')
        return side === 'left' ? 'is-removed' : 'is-empty'
      return this.lane.status === 'modified' ? 'is-modified' : 'is-unchanged'
    },
    fieldClass(item, side) {
      if (item.status === 'added')
        return side === 'right' ? 'is-added' : 'is-empty'
      if (item.status === 'removed')
        return side === 'left' ? 'is-removed' : 'is-empty'
      return item.status === 'modified' ? 'is-modified' : 'is-unchanged'
    },
    fieldText(item, side) {
      if (item.status === 'added' && side === 'left') return '—'
      if (item.status === 'removed' && side === 'right') return '—'
      return side === 'left' ? item.leftText : item.rightText
    },
  },
}
</script>

<style scoped>
.rule-diff-lane {
  min-width: 860px;
}
.rule-diff-lane + .rule-diff-lane {
  margin-top: 12px;
}
.rule-diff-head-lane,
.rule-diff-field-lane {
  display: grid;
  grid-template-columns: minmax(420px, 1fr) minmax(420px, 1fr);
  gap: 12px;
}
.rule-diff-side {
  min-width: 0;
  padding: 12px 16px;
  border: 1px solid #ebeef5;
  border-radius: 4px 4px 0 0;
  background: #fff;
}
.rule-diff-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 20px;
}
.rule-diff-title {
  color: #303133;
  font-size: 14px;
  line-height: 20px;
}
.rule-diff-subtitle {
  margin-top: 4px;
  color: #64748b;
  font-size: 12px;
}
.rule-diff-marker,
.rule-diff-field-change {
  flex-shrink: 0;
  font-size: 12px;
  font-weight: 600;
}
.rule-diff-field-lane + .rule-diff-field-lane {
  margin-top: -1px;
}
.rule-diff-field {
  display: grid;
  grid-template-columns: minmax(96px, 128px) minmax(0, 1fr) auto;
  align-items: start;
  gap: 8px;
  min-width: 0;
  padding: 8px 16px;
  border: 1px solid #ebeef5;
  color: #303133;
  font-size: 13px;
  line-height: 20px;
}
.rule-diff-field-label {
  color: #64748b;
}
.rule-diff-field-value {
  min-width: 0;
  overflow-wrap: anywhere;
  font-family: Consolas, Monaco, 'Courier New', monospace;
}
.rule-diff-placeholder {
  display: flex;
  align-items: center;
  min-height: 20px;
  color: #64748b;
  font-size: 12px;
}
.is-modified {
  border-color: #f5dab1;
  background: #fdf6ec;
}
.is-modified .rule-diff-marker,
.is-modified .rule-diff-field-change {
  color: #b88230;
}
.is-added {
  border-color: #c2e7b0;
  background: #f0f9eb;
}
.is-added .rule-diff-marker,
.is-added .rule-diff-field-change {
  color: #529b2e;
}
.is-removed {
  border-color: #fbc4c4;
  background: #fef0f0;
}
.is-removed .rule-diff-marker,
.is-removed .rule-diff-field-change {
  color: #c45656;
}
.is-empty {
  border-style: dashed;
  background: #fafafa;
}
.rule-diff-expand {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin: 8px 0 0 16px;
  padding: 0;
  border: 0;
  background: transparent;
  color: #606266;
  cursor: pointer;
  font-size: 12px;
}
.rule-diff-expand:hover {
  color: #2639e9;
}
.rule-diff-children {
  margin-top: 8px;
  padding: 12px 0 0 24px;
  border-left: 2px solid #e4e7ed;
}
</style>
