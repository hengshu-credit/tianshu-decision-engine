<template>
  <aside class="expression-palette">
    <el-input v-model="keyword" size="small" clearable prefix-icon="el-icon-search" placeholder="搜索字段或方法" />
    <section v-if="allows('LITERAL')">
      <h4>基础</h4>
      <button class="palette-item" type="button" @click="insertLiteral"><i class="el-icon-edit-outline" /> 输入阈值</button>
    </section>
    <section v-if="allows('REFERENCE') && filteredVars.length">
      <h4>字段</h4>
      <button v-for="item in filteredVars" :key="referenceKey(item)" class="palette-item" type="button" @click="$emit('insert', referenceTemplate(item))">
        <span>{{ item.varLabel || item.label || item.varCode }}</span><code>{{ item.varCode || item.refCode }}</code>
      </button>
    </section>
    <section v-if="allows('FUNCTION') && filteredFunctions.length">
      <h4>函数 / 方法</h4>
      <button v-for="fn in filteredFunctions" :key="functionCode(fn)" class="palette-item" type="button" @click="$emit('insert', functionTemplate(fn))">
        <span>{{ fn.funcName || fn.functionLabel || functionCode(fn) }}</span><code>{{ functionCode(fn) }}()</code>
      </button>
    </section>
    <section v-if="allows('OPERATION')">
      <h4>运算符</h4>
      <div class="palette-grid">
        <button v-for="operator in operators" :key="operator" type="button" @click="$emit('insert', operationTemplate(operator))">{{ operator }}</button>
      </div>
    </section>
    <section v-if="allows('ACCESS') || allows('CAST') || allows('ARRAY')">
      <h4>取值与转换</h4>
      <button v-if="allows('ACCESS')" class="palette-item" type="button" @click="$emit('insert', accessTemplate('KEY'))">取字典 Key</button>
      <button v-if="allows('ACCESS')" class="palette-item" type="button" @click="$emit('insert', accessTemplate('INDEX'))">取数组 Index</button>
      <button v-if="allows('CAST')" class="palette-item" type="button" @click="$emit('insert', castTemplate('NUMBER'))">类型转换</button>
      <button v-if="allows('ARRAY')" class="palette-item" type="button" @click="$emit('insert', arrayTemplate())">数组</button>
    </section>
  </aside>
</template>

<script>
import {
  createAccessOperand,
  createArrayOperand,
  createCastOperand,
  createLiteralOperand,
  createOperationOperand,
  createReferenceOperand
} from '@/utils/operand'
import { createFunctionTemplate } from './expressionTree'

export default {
  name: 'ExpressionPalette',
  props: {
    vars: { type: Array, default: () => [] },
    functions: { type: Array, default: () => [] },
    allowedKinds: { type: Array, default: () => [] },
    expectedType: { type: String, default: '' }
  },
  data() {
    return {
      keyword: '',
      operators: ['+', '-', '*', '/', '%', '==', '!=', '>', '>=', '<', '<=', '&&', '||']
    }
  },
  computed: {
    filteredVars() {
      const key = this.keyword.trim().toLowerCase()
      if (!key) return this.vars
      return this.vars.filter(item => [item.varCode, item.varLabel, item.refCode, item.label].some(value => String(value || '').toLowerCase().includes(key)))
    },
    filteredFunctions() {
      const key = this.keyword.trim().toLowerCase()
      if (!key) return this.functions
      return this.functions.filter(fn => [this.functionCode(fn), fn.funcName, fn.functionLabel].some(value => String(value || '').toLowerCase().includes(key)))
    }
  },
  methods: {
    allows(kind) { return !this.allowedKinds.length || this.allowedKinds.includes(kind) },
    insertLiteral() { this.$emit('insert', createLiteralOperand('', this.expectedType || 'STRING')) },
    referenceTemplate(item) { return createReferenceOperand(item) },
    functionTemplate(fn) { return createFunctionTemplate(fn) },
    operationTemplate(operator) { return createOperationOperand(operator, [null, null]) },
    accessTemplate(type) { return createAccessOperand(null, type, createLiteralOperand('', type === 'INDEX' ? 'NUMBER' : 'STRING')) },
    castTemplate(type) { return createCastOperand(type, null) },
    arrayTemplate() { return createArrayOperand([null]) },
    functionCode(fn) { return fn.functionCode || fn.funcCode || fn.functionName || fn.funcName || fn.name || '' },
    referenceKey(item) { return (item._refType || item.refType || '') + ':' + (item._varId || item.refId || item.varCode) }
  }
}
</script>

<style scoped>
.expression-palette { padding: 16px; overflow: auto; border-right: 1px solid #e8edf3; background: #fbfcfe; }
h4 { margin: 18px 0 8px; color: #6b778c; font-size: 12px; text-transform: uppercase; }
.palette-item { display: flex; width: 100%; min-height: 34px; align-items: center; justify-content: space-between; gap: 8px; margin: 4px 0; padding: 7px 9px; border: 1px solid transparent; border-radius: 6px; background: transparent; color: #26364d; cursor: pointer; text-align: left; }
.palette-item:hover { border-color: #b9d3ff; background: #edf5ff; }
.palette-item code { overflow: hidden; color: #7a8799; font-size: 11px; text-overflow: ellipsis; }
.palette-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 6px; }
.palette-grid button { height: 30px; border: 1px solid #dce3ec; border-radius: 5px; background: #fff; cursor: pointer; }
</style>
