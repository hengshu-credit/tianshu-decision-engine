<template>
  <span class="graph-tools">
    <el-select
      :model-value="target"
      filterable
      remote
      clearable
      size="small"
      placeholder="搜索节点或引用"
      :remote-method="onRemoteSearch"
      @visible-change="onVisibleChange"
      @change="onChange"
    >
      <el-option
        v-for="item in options"
        :key="item.key"
        :label="item.label"
        :value="item.key"
      >
        <span>{{ item.label }}</span>
        <small>{{
          item.kind === 'REFERENCE'
            ? item.refType + '#' + item.refId
            : item.kind === 'NODE'
            ? '节点'
            : '连线'
        }}</small>
      </el-option>
    </el-select>
    <el-button
      v-if="showMiniMap"
      class="toolbar-action"
      size="small"
      :icon="ElIconPictureOutline"
      @click="$emit('toggle-minimap')"
      >{{ miniMapVisible ? '关闭缩略图' : '缩略图' }}</el-button
    >
    <el-popover placement="bottom-end" width="360" trigger="click">
      <div v-if="issues.length === 0" class="issue-empty">未发现未配置项</div>
      <div v-else class="issue-list">
        <button
          v-for="(issue, index) in issues"
          :key="index"
          type="button"
          @click="$emit('locate-issue', issue)"
        >
          <el-icon><el-icon-warning-outline /></el-icon> {{ issue.message }}
        </button>
      </div>
      <template v-slot:reference>
        <el-button
          class="toolbar-action"
          size="small"
          :icon="ElIconWarningOutline"
          @click="$emit('check')"
        >
          未配置项<span v-if="issues.length">（{{ issues.length }}）</span>
        </el-button>
      </template>
    </el-popover>
  </span>
</template>

<script>
import { markRaw } from 'vue'
import {
  Warning as ElIconWarningOutline,
  Picture as ElIconPictureOutline,
} from '@element-plus/icons-vue'
import { $emit } from '../../utils/gogocodeTransfer'
export default {
  data() {
    return {
      searchKeyword: '',
      ElIconPictureOutline: markRaw(ElIconPictureOutline),
      ElIconWarningOutline: markRaw(ElIconWarningOutline),
    }
  },
  components: {
    ElIconWarningOutline,
  },
  name: 'GraphDesignerNavigator',
  props: {
    target: { type: String, default: '' },
    options: { type: Array, default: () => [] },
    issues: { type: Array, default: () => [] },
    miniMapVisible: { type: Boolean, default: false },
    showMiniMap: { type: Boolean, default: true },
  },
  methods: {
    onRemoteSearch(keyword) {
      this.searchKeyword = keyword || ''
      $emit(this, 'search', this.searchKeyword)
    },
    onVisibleChange(visible) {
      if (visible) $emit(this, 'search', this.searchKeyword)
    },
    onChange(value) {
      $emit(this, 'update:target', value)
      $emit(this, 'locate', value)
    },
  },
  emits: [
    'toggle-minimap',
    'locate-issue',
    'check',
    'search',
    'update:target',
    'locate',
  ],
}
</script>

<style scoped>
.graph-tools {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.graph-tools > .el-select {
  width: 190px;
}
.toolbar-action {
  border-color: rgba(255, 255, 255, 0.4);
  background: rgba(255, 255, 255, 0.1);
  color: #ffffff;
  transition: background-color 0.15s ease, border-color 0.15s ease,
    color 0.15s ease;
}
.toolbar-action:hover,
.toolbar-action:focus {
  border-color: #ffffff;
  background: #ffffff;
  color: #1d39c4;
  box-shadow: 0 0 0 2px rgba(255, 255, 255, 0.22);
}
.toolbar-action:active {
  border-color: #d6deff;
  background: #eef2ff;
  color: #1428a0;
  box-shadow: none;
}
.graph-tools small {
  float: right;
  margin-left: 12px;
  color: #64748b;
}
.issue-empty {
  padding: 16px;
  color: #64748b;
  text-align: center;
}
.issue-list {
  max-height: 260px;
  overflow: auto;
}
.issue-list button {
  display: block;
  width: 100%;
  padding: 8px;
  border: 0;
  border-bottom: 1px solid #f2f3f5;
  background: transparent;
  color: #606266;
  text-align: left;
  cursor: pointer;
}
.issue-list button:hover {
  color: #2639e9;
  background: #f5f7ff;
}
</style>
