<template>
  <trace-branch :dimmed="!node.evaluated && !isSkipType" :show-heading="showSelf" :has-children="!isInlineCompare && kids.length > 0">
    <template #heading>
      <template v-if="isInlineCompare">
        <span class="nd-txt">
          <content-reference mode="trace" :value="traceContent(node.children[0], varMap)" /><span v-if="node.children[0].type === 'VARIABLE'">({{ valueFor(node.children[0]) }})</span>
          <span class="c-op">{{ opText }}</span>
          <content-reference mode="trace" :value="traceContent(node.children[1], varMap)" /><span v-if="node.children[1].type === 'VARIABLE'">({{ valueFor(node.children[1]) }})</span>
        </span>
        <span class="nd-res" :class="resCls" v-if="node.evaluated">{{
          resText
        }}</span>
        <span class="nd-res is-skip" v-else>未执行</span>
      </template>
      <template v-else>
        <span class="nd-txt"><content-reference mode="trace" v-if="['VARIABLE', 'FUNCTION', 'METHOD', 'VALUE', 'PRIMARY'].includes(node.type)" :value="traceContent(node, varMap)" /><template v-else>{{ nodeLabel }}</template><span v-if="node.type === 'VARIABLE' && node.evaluated"> = {{ valueFor(node) }}</span></span>
        <span class="nd-res" :class="resCls" v-if="showRes">{{ resText }}</span>
        <span class="nd-res is-skip" v-else-if="!node.evaluated && !isSkipType"
          >未执行</span
        >
      </template>
    </template>
    <trace-node v-for="(child, index) in kids" :key="index" :node="child" :var-map="varMap" :input-params="inputParams" />
  </trace-branch>
</template>

<script>
import { traceContentValue } from '@/utils/contentReference'
import ContentReference from './ContentReference.vue'
import TraceBranch from './TraceBranch.vue'
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
  components: { ContentReference, TraceBranch },
  name: 'TraceNode',
  props: {
    node: { type: Object, required: true },
    varMap: {
      type: Object,
      default: function () {
        return {}
      },
    },
    inputParams: {
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
        return l + '(' + fv(this.valueFor(c)) + ')'
      }
      return fv(c.value !== undefined ? c.value : c.token)
    },
    rightText: function () {
      var c = this.node.children[1]
      if (c.type === 'VARIABLE') {
        var l = this.varMap[c.token] || c.token
        return l + '(' + fv(this.valueFor(c)) + ')'
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
        return this.node.evaluated ? lbl + ' = ' + fv(this.valueFor(this.node)) : lbl
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
  methods: {
    traceContent: traceContentValue,
    inputValue: function (key) {
      if (!key || !this.inputParams) return undefined
      if (Object.prototype.hasOwnProperty.call(this.inputParams, key))
        return this.inputParams[key]
      if (key.indexOf('.') < 0) return undefined
      var current = this.inputParams
      var parts = key.split('.').filter(Boolean)
      for (var i = 0; i < parts.length; i++) {
        if (current === null || current === undefined || typeof current !== 'object')
          return undefined
        if (!Object.prototype.hasOwnProperty.call(current, parts[i])) return undefined
        current = current[parts[i]]
      }
      return current
    },
    valueFor: function (node) {
      if (!node) return undefined
      return node.value !== undefined ? node.value : this.inputValue(node.token)
    },
  },
}
</script>

<style scoped>
.nd-txt {
  font-weight: 600;
}
.c-name {
  color: var(--tianshu-text-primary);
}
.c-op {
  color: var(--tianshu-text-secondary);
  margin: 0 2px;
  font-weight: 600;
}
.nd-res {
  font-size: 11px;
  font-weight: 700;
  flex-shrink: 0;
}
.nd-res.is-true {
  color: var(--tianshu-success-text);
}
.nd-res.is-false {
  color: var(--tianshu-danger-text);
}
.nd-res.is-val {
  color: var(--tianshu-info-text);
}
.nd-res.is-skip {
  color: var(--tianshu-text-tertiary);
  font-weight: 400;
}
</style>
