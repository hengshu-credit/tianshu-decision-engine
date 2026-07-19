<template>
  <span class="graph-tools">
    <el-select
      :value="target"
      filterable
      remote
      clearable
      size="mini"
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
        <small>{{ item.kind === 'REFERENCE' ? item.refType + '#' + item.refId : item.kind === 'NODE' ? '节点' : '连线' }}</small>
      </el-option>
    </el-select>
    <el-button v-if="showMiniMap" size="mini" icon="el-icon-picture-outline" @click="$emit('toggle-minimap')">{{ miniMapVisible ? '关闭缩略图' : '缩略图' }}</el-button>
    <el-popover placement="bottom-end" width="360" trigger="click">
      <div v-if="issues.length === 0" class="issue-empty">未发现未配置项</div>
      <div v-else class="issue-list">
        <button v-for="(issue, index) in issues" :key="index" type="button" @click="$emit('locate-issue', issue)">
          <i class="el-icon-warning-outline" /> {{ issue.message }}
        </button>
      </div>
      <el-button slot="reference" size="mini" icon="el-icon-warning-outline" @click="$emit('check')">
        未配置项<span v-if="issues.length">（{{ issues.length }}）</span>
      </el-button>
    </el-popover>
  </span>
</template>

<script>
export default {
  name: 'GraphDesignerNavigator',
  data() {
    return {
      searchKeyword: ''
    }
  },
  props: {
    target: { type: String, default: '' },
    options: { type: Array, default: () => [] },
    issues: { type: Array, default: () => [] },
    miniMapVisible: { type: Boolean, default: false },
    showMiniMap: { type: Boolean, default: true }
  },
  methods: {
    onRemoteSearch(keyword) {
      this.searchKeyword = keyword || ''
      this.$emit('search', this.searchKeyword)
    },
    onVisibleChange(visible) {
      if (visible) this.$emit('search', this.searchKeyword)
    },
    onChange(value) {
      this.$emit('update:target', value)
      this.$emit('locate', value)
    }
  }
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
.graph-tools small {
  float: right;
  margin-left: 12px;
  color: #909399;
}
.issue-empty {
  padding: 16px;
  color: #909399;
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
