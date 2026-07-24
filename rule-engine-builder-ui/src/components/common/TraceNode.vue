<template>
  <div class="nd" :class="{ 'is-dim': !node.evaluated && !isSkipType }">
    <template v-if="isInlineCompare">
      <div class="nd-box">
        <span class="nd-txt">
          <span class="c-name">{{ leftText }}</span>
          <span class="c-op">{{ opText }}</span>
          <span class="c-name">{{ rightText }}</span>
        </span>
        <span class="nd-res" :class="resCls" v-if="node.evaluated">{{
          resText
        }}</span>
        <span class="nd-res is-skip" v-else>未执行</span>
      </div>
    </template>

    <template v-else>
      <div class="nd-box" v-if="showSelf">
        <span class="nd-txt">{{ nodeLabel }}</span>
        <span class="nd-res" :class="resCls" v-if="showRes">{{ resText }}</span>
        <span class="nd-res is-skip" v-else-if="!node.evaluated && !isSkipType"
          >未执行</span
        >
      </div>
      <div
        class="nd-kids"
        v-if="kids.length > 0"
        :class="{ 'no-stem': !showSelf }"
      >
        <trace-node
          v-for="(c, i) in kids"
          :key="i"
          :node="c"
          :var-map="varMap"
        />
      </div>
    </template>
  </div>
</template>

<script>
var OP_CN = {
  '||': '或',
  '&&': '且',
  '!': '非',
  '==': '等于',
  '!=': '不等于',
  '>': '大于',
  '>=': '大于等于',
  '<': '小于',
  '<=': '小于等于',
  '+': '加',
  '-': '减',
  '*': '乘',
  '/': '除',
  '%': '取余',
  '=': '赋值',
}
var CMP = ['==', '!=', '>', '>=', '<', '<=']

function leaf(n) {
  return (
    n && (n.type === 'VARIABLE' || n.type === 'VALUE' || n.type === 'PRIMARY')
  )
}
function fv(v) {
  if (v === true) return '真'
  if (v === false) return '假'
  if (v === null || v === undefined) return '空'
  return String(v)
}

export default {
  name: 'TraceNode',
  props: {
    node: { type: Object, required: true },
    varMap: {
      type: Object,
      default: function () {
        return {}
      },
    },
  },
  computed: {
    isSkipType: function () {
      return this.node.type === 'BLOCK' || this.node.type === 'STATEMENT'
    },
    isInlineCompare: function () {
      if (this.node.type !== 'OPERATOR') return false
      if (CMP.indexOf(this.node.token) === -1) return false
      var ch = this.node.children || []
      return ch.length === 2 && leaf(ch[0]) && leaf(ch[1])
    },
    leftText: function () {
      var c = this.node.children[0]
      if (c.type === 'VARIABLE') {
        var l = this.varMap[c.token] || c.token
        return l + '(' + fv(c.value) + ')'
      }
      return fv(c.value !== undefined ? c.value : c.token)
    },
    rightText: function () {
      var c = this.node.children[1]
      if (c.type === 'VARIABLE') {
        var l = this.varMap[c.token] || c.token
        return l + '(' + fv(c.value) + ')'
      }
      return fv(c.value !== undefined ? c.value : c.token)
    },
    opText: function () {
      return OP_CN[this.node.token] || this.node.token
    },
    nodeLabel: function () {
      var type = this.node.type,
        token = this.node.token
      if (type === 'OPERATOR') {
        var cn = OP_CN[token]
        return cn ? cn + '(' + token + ')' : token
      }
      if (type === 'FUNCTION' || type === 'METHOD') return token + '()'
      if (type === 'VARIABLE') {
        var lbl = this.varMap[token] || token
        return this.node.evaluated ? lbl + ' = ' + fv(this.node.value) : lbl
      }
      if (type === 'VALUE' || type === 'PRIMARY')
        return fv(this.node.value !== undefined ? this.node.value : token)
      if (type === 'IF') return '条件判断'
      if (this.isSkipType) return ''
      return token || type
    },
    showSelf: function () {
      return !this.isSkipType
    },
    showRes: function () {
      if (!this.node.evaluated) return false
      if (this.isSkipType || this.node.type === 'VARIABLE') return false
      return true
    },
    resCls: function () {
      var v = this.node.value
      if (v === true) return 'is-true'
      if (v === false) return 'is-false'
      return 'is-val'
    },
    resText: function () {
      return '→ ' + fv(this.node.value)
    },
    kids: function () {
      if (!this.node.children) return []
      var r = []
      for (var i = 0; i < this.node.children.length; i++) {
        var c = this.node.children[i]
        if (!c || typeof c !== 'object') continue
        if (
          (c.type === 'BLOCK' || c.type === 'STATEMENT') &&
          c.children &&
          c.children.length > 0
        ) {
          for (var j = 0; j < c.children.length; j++) {
            if (c.children[j] && typeof c.children[j] === 'object')
              r.push(c.children[j])
          }
        } else {
          r.push(c)
        }
      }
      return r
    },
  },
}
</script>

<style scoped>
.nd {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 0 10px;
}
.nd.is-dim {
  opacity: 0.7;
}
.nd.is-dim > .nd-box {
  border-style: dashed;
}
.nd-box {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 34px;
  padding: 6px 12px;
  background: #fff;
  border: 1px solid #d8dee9;
  border-radius: 6px;
  font-size: 12px;
  white-space: nowrap;
  color: #303133;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
}
.nd-box:hover {
  border-color: #a8c8f0;
  box-shadow: 0 2px 6px var(--tianshu-primary-shadow);
}
.nd-txt {
  font-weight: 600;
}
.c-name {
  color: #303133;
}
.c-op {
  color: #6b7280;
  margin: 0 2px;
  font-weight: 600;
}
.nd-res {
  font-size: 11px;
  font-weight: 700;
  flex-shrink: 0;
}
.nd-res.is-true {
  color: #529b2e;
}
.nd-res.is-false {
  color: #c45656;
}
.nd-res.is-val {
  color: var(--el-color-primary);
}
.nd-res.is-skip {
  color: #64748b;
  font-weight: 400;
}
.nd-kids {
  display: flex;
  justify-content: center;
  position: relative;
  padding-top: 28px;
}
.nd-kids:not(.no-stem)::before {
  content: '';
  position: absolute;
  top: 0;
  left: 50%;
  width: 1px;
  height: 14px;
  background: #cbd5e1;
}
.nd-kids > .nd {
  position: relative;
}
.nd-kids > .nd::before {
  content: '';
  position: absolute;
  top: -14px;
  left: 50%;
  width: 1px;
  height: 14px;
  background: #cbd5e1;
}
.nd-kids > .nd:first-child:not(:last-child)::after {
  content: '';
  position: absolute;
  top: -14px;
  left: 50%;
  right: 0;
  height: 1px;
  background: #cbd5e1;
}
.nd-kids > .nd:last-child:not(:first-child)::after {
  content: '';
  position: absolute;
  top: -14px;
  left: 0;
  right: 50%;
  height: 1px;
  background: #cbd5e1;
}
.nd-kids > .nd:not(:first-child):not(:last-child)::after {
  content: '';
  position: absolute;
  top: -14px;
  left: 0;
  right: 0;
  height: 1px;
  background: #cbd5e1;
}
.nd:not(.is-dim) > .nd-kids:not(.no-stem)::before {
  width: 2px;
  margin-left: -0.5px;
  background: var(--el-color-primary);
}
.nd-kids > .nd:not(.is-dim)::before {
  width: 2px;
  margin-left: -0.5px;
  background: var(--el-color-primary);
}
.nd-kids > .nd:not(.is-dim):first-child:not(:last-child)::after {
  height: 2px;
  background: var(--el-color-primary);
}
.nd-kids > .nd:not(.is-dim):last-child:not(:first-child)::after {
  height: 2px;
  background: var(--el-color-primary);
}
.nd-kids > .nd:not(.is-dim):not(:first-child):not(:last-child)::after {
  height: 2px;
  background: var(--el-color-primary);
}
</style>
