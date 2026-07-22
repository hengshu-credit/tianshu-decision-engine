<template>
  <div v-if="node.kind === 'group'" class="rs-trace-group" :class="resultClass">
    <div class="rs-trace-group-head">
      <span class="rs-trace-group-op">{{
        node.operator === 'OR' ? '或' : '且'
      }}</span>
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
    <span class="rs-trace-leaf-part"
      >实际值 <strong>{{ node.actualText }}</strong></span
    >
    <span class="rs-trace-leaf-op">{{ node.operatorText }}</span>
    <span class="rs-trace-leaf-part"
      >阈值 <strong>{{ node.thresholdText }}</strong></span
    >
    <span class="rs-trace-leaf-result">{{ resultText }}</span>
  </div>
</template>

<script>
export default {
  name: 'RuleSetConditionTraceNode',
  props: {
    node: { type: Object, required: true },
  },
  computed: {
    resultClass: function () {
      return this.node.result === true
        ? 'is-pass'
        : this.node.result === false
        ? 'is-fail'
        : 'is-skip'
    },
    resultText: function () {
      if (this.node.result === true)
        return this.node.kind === 'group' ? '条件组满足' : '满足'
      if (this.node.result === false)
        return this.node.kind === 'group' ? '条件组不满足' : '不满足'
      return '未执行'
    },
  },
}
</script>

<style lang="scss" scoped>
.rs-trace-group {
  border-left: 2px solid #dcdfe6;
  padding-left: 10px;
}
.rs-trace-group + .rs-trace-group,
.rs-trace-group + .rs-trace-leaf,
.rs-trace-leaf + .rs-trace-group,
.rs-trace-leaf + .rs-trace-leaf {
  margin-top: 8px;
}
.rs-trace-group.is-pass {
  border-left-color: #67c23a;
}
.rs-trace-group.is-fail {
  border-left-color: #f56c6c;
}
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
  background: #ecf5ff;
  color: #1677d2;
  font-size: 11px;
  font-weight: 700;
}
.rs-trace-group-result {
  color: #909399;
  font-size: 11px;
}
.rs-trace-group.is-pass > .rs-trace-group-head .rs-trace-group-result {
  color: #529b2e;
}
.rs-trace-group.is-fail > .rs-trace-group-head .rs-trace-group-result {
  color: #c45656;
}
.rs-trace-group-body {
  min-width: 0;
}
.rs-trace-leaf {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  padding: 7px 8px;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  background: #fff;
}
.rs-trace-leaf.is-pass {
  border-color: #d9f2ce;
  background: #fbfff8;
}
.rs-trace-leaf.is-fail {
  border-color: #fde2e2;
  background: #fff9f9;
}
.rs-trace-leaf-var {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 180px;
}
.rs-trace-leaf-var code {
  padding: 1px 6px;
  border-radius: 4px;
  background: #f2f6fc;
  color: #1f6fbf;
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
}
.rs-trace-leaf-var span,
.rs-trace-leaf-part {
  color: #606266;
  font-size: 12px;
}
.rs-trace-leaf-part strong,
.rs-trace-leaf-op {
  color: #303133;
  font-size: 12px;
  font-weight: 700;
}
.rs-trace-leaf-result {
  margin-left: auto;
  padding: 1px 8px;
  border-radius: 10px;
  background: #f4f4f5;
  color: #909399;
  font-size: 11px;
  font-weight: 700;
}
.rs-trace-leaf.is-pass .rs-trace-leaf-result {
  background: #f0f9eb;
  color: #529b2e;
}
.rs-trace-leaf.is-fail .rs-trace-leaf-result {
  background: #fef0f0;
  color: #c45656;
}
</style>
