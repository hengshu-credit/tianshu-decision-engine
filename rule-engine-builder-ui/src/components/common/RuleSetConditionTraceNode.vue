<template>
  <div v-if="node.kind === 'group'" class="rs-trace-group" :class="resultClass">
    <div class="rs-trace-group-head">
      <span class="rs-trace-group-op">{{ node.operator === 'OR' ? '或' : '且' }}</span>
      <span class="rs-trace-group-result">{{ resultText }}</span>
    </div>
    <div class="rs-trace-group-body">
      <rule-set-condition-trace-node
        v-for="(child, index) in node.children"
        :key="index"
        :node="child"
      />
    </div>
  </div>
  <div v-else class="rs-trace-leaf" :class="resultClass">
    <div class="rs-trace-leaf-var">
      <code>{{ node.varCode }}</code>
      <span>{{ node.varName }}</span>
    </div>
    <span class="rs-trace-leaf-part">实际值 <strong>{{ node.actualText }}</strong></span>
    <span class="rs-trace-leaf-op">{{ node.operatorText }}</span>
    <span class="rs-trace-leaf-part">阈值 <strong>{{ node.thresholdText }}</strong></span>
    <span class="rs-trace-leaf-result">{{ resultText }}</span>
  </div>
</template>

<script>
export default {
  name: 'RuleSetConditionTraceNode',
  props: {
    node: { type: Object, required: true }
  },
  computed: {
    resultClass: function () {
      return this.node.result === true ? 'is-pass' : this.node.result === false ? 'is-fail' : 'is-skip'
    },
    resultText: function () {
      if (this.node.result === true) return this.node.kind === 'group' ? '条件组满足' : '满足'
      if (this.node.result === false) return this.node.kind === 'group' ? '条件组不满足' : '不满足'
      return '未执行'
    }
  }
}
</script>

<style lang="scss" scoped>
.rs-trace-group {
  border-left: 2px solid #DCDFE6;
  padding-left: 10px;
}
.rs-trace-group + .rs-trace-group,
.rs-trace-group + .rs-trace-leaf,
.rs-trace-leaf + .rs-trace-group,
.rs-trace-leaf + .rs-trace-leaf { margin-top: 8px; }
.rs-trace-group.is-pass { border-left-color: #67C23A; }
.rs-trace-group.is-fail { border-left-color: #F56C6C; }
.rs-trace-group-head {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 24px;
  margin-bottom: 8px;
}
.rs-trace-group-op {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 20px;
  border-radius: 10px;
  background: #ECF5FF;
  color: #1677D2;
  font-size: 11px;
  font-weight: 700;
}
.rs-trace-group-result { color: #909399; font-size: 11px; }
.rs-trace-group.is-pass > .rs-trace-group-head .rs-trace-group-result { color: #529B2E; }
.rs-trace-group.is-fail > .rs-trace-group-head .rs-trace-group-result { color: #C45656; }
.rs-trace-group-body { min-width: 0; }
.rs-trace-leaf {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  padding: 7px 8px;
  border: 1px solid #E4E7ED;
  border-radius: 6px;
  background: #fff;
}
.rs-trace-leaf.is-pass { border-color: #D9F2CE; background: #FBFFF8; }
.rs-trace-leaf.is-fail { border-color: #FDE2E2; background: #FFF9F9; }
.rs-trace-leaf-var { display: inline-flex; align-items: center; gap: 6px; min-width: 180px; }
.rs-trace-leaf-var code {
  padding: 1px 6px;
  border-radius: 4px;
  background: #F2F6FC;
  color: #1F6FBF;
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
}
.rs-trace-leaf-var span,
.rs-trace-leaf-part { color: #606266; font-size: 12px; }
.rs-trace-leaf-part strong,
.rs-trace-leaf-op { color: #303133; font-size: 12px; font-weight: 700; }
.rs-trace-leaf-result {
  margin-left: auto;
  padding: 1px 8px;
  border-radius: 10px;
  background: #F4F4F5;
  color: #909399;
  font-size: 11px;
  font-weight: 700;
}
.rs-trace-leaf.is-pass .rs-trace-leaf-result { background: #F0F9EB; color: #529B2E; }
.rs-trace-leaf.is-fail .rs-trace-leaf-result { background: #FEF0F0; color: #C45656; }
</style>
