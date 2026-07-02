<template>
  <div class="block-editor">
    <!-- 块列表 -->
    <div v-for="(block, bi) in blocks" :key="bi" class="block-item" :class="'block-' + block.type">
      <div class="block-header">
        <span class="block-type-tag" :style="{background: typeColor(block.type)}">{{ typeLabel(block.type) }}</span>
        <div class="block-header-actions">
          <el-button type="text" size="mini" icon="el-icon-top" v-if="bi > 0" @click="moveBlock(bi, -1)" />
          <el-button type="text" size="mini" icon="el-icon-bottom" v-if="bi < blocks.length - 1" @click="moveBlock(bi, 1)" />
          <el-button type="text" size="mini" icon="el-icon-delete" style="color:#F76E6C" @click="removeBlock(bi)" />
        </div>
      </div>
      <div class="block-body">

        <!-- ===== 赋值 ===== -->
        <template v-if="block.type === 'assign'">
          <div class="inline-row">
            <var-picker :vars="vars" :selected-vars="selectedVars" :value="block.target" placeholder="目标变量" size="mini" @select="v => selectVar(block, 'target', v)" />
            <span class="eq">=</span>
            <el-input v-model="block.value" size="mini" placeholder="值/表达式" @input="sync" />
          </div>
          <div class="rounding-row">
            <el-switch v-model="block.enableRounding" size="mini" active-text="精度" @change="sync" />
            <template v-if="block.enableRounding">
              <span class="mini-label">小数位</span>
              <el-input-number v-model="block.decimalPlaces" :min="0" :max="10" size="mini" style="width:90px;" placeholder="位数" @change="sync" />
              <span class="mini-label">进位</span>
              <el-select v-model="block.roundingMode" size="mini" style="width:100px;" placeholder="进位规则" @change="sync">
                <el-option label="四舍五入" value="HALF_UP" />
                <el-option label="向上取整" value="UP" />
                <el-option label="向下截断" value="DOWN" />
                <el-option label="正无穷方向" value="CEILING" />
                <el-option label="负无穷方向" value="FLOOR" />
              </el-select>
            </template>
          </div>
        </template>

        <!-- ===== 条件分支 ===== -->
        <template v-if="block.type === 'if-block'">
          <div v-for="(br, bri) in block.branches" :key="bri" class="branch-card" :class="'branch-' + br.type">
            <div class="branch-head">
              <span class="branch-tag" :class="'tag-' + br.type">{{ br.type === 'if' ? 'IF' : br.type === 'elseif' ? 'ELSE IF' : 'ELSE' }}</span>
              <el-button type="text" size="mini" icon="el-icon-delete" style="color:#F76E6C" @click="removeBranch(block, bri)" />
            </div>
            <div v-if="br.type !== 'else'" class="cond-area">
              <var-picker :vars="vars" :selected-vars="selectedVars" :value="br.condVar" placeholder="条件变量" size="mini" @select="v => selectVar(br, 'condVar', v)" />
              <el-select v-model="br.condOp" size="mini" style="width:68px" @change="sync">
                <el-option label="==" value="==" /><el-option label="!=" value="!=" />
                <el-option label=">" value=">" /><el-option label=">=" value=">=" />
                <el-option label="<" value="<" /><el-option label="<=" value="<=" />
              </el-select>
              <el-input v-model="br.condValue" size="mini" placeholder="值" @input="sync" style="width:90px" />
            </div>
            <div class="branch-body">
              <div v-for="(a, ai) in br.actions" :key="ai" class="inline-row">
                <var-picker :vars="vars" :selected-vars="selectedVars" :value="a.target" placeholder="变量" size="mini" @select="v => selectVar(a, 'target', v)" />
                <span class="eq">=</span>
                <el-input v-model="a.value" size="mini" placeholder="值" @input="sync" />
                <el-button v-if="br.actions.length > 1" type="text" size="mini" icon="el-icon-delete" style="color:#F76E6C" @click="br.actions.splice(ai,1); sync()" />
              </div>
              <el-button size="mini" icon="el-icon-plus" @click="br.actions.push({type:'assign',target:'',value:''})" style="width:100%;margin-top:2px">添加赋值</el-button>
            </div>
          </div>
          <div class="branch-add-row">
            <el-button size="mini" @click="addBranch(block, 'elseif')" v-if="!hasElse(block)">+ ELSE IF</el-button>
            <el-button size="mini" @click="addBranch(block, 'else')" v-if="!hasElse(block)">+ ELSE</el-button>
          </div>
        </template>

        <!-- ===== Switch ===== -->
        <template v-if="block.type === 'switch-block'">
          <div class="inline-row" style="margin-bottom:6px">
            <span class="mini-label">匹配变量</span>
            <var-picker :vars="vars" :selected-vars="selectedVars" :value="block.matchVar" placeholder="变量" size="mini" @select="v => selectVar(block, 'matchVar', v)" />
          </div>
          <div v-for="(c, ci) in block.cases" :key="ci" class="case-card">
            <div class="case-head">
              <span class="case-tag">CASE</span>
              <el-input v-model="c.value" size="mini" placeholder="匹配值" @input="sync" style="flex:1" />
              <el-button type="text" size="mini" icon="el-icon-delete" style="color:#F76E6C" @click="block.cases.splice(ci,1); sync()" />
            </div>
            <div class="case-body">
              <div v-for="(a, ai) in c.actions" :key="ai" class="inline-row">
                <var-picker :vars="vars" :selected-vars="selectedVars" :value="a.target" placeholder="变量" size="mini" @select="v => selectVar(a, 'target', v)" />
                <span class="eq">=</span>
                <el-input v-model="a.value" size="mini" placeholder="值" @input="sync" />
              </div>
            </div>
          </div>
          <div class="case-card default-case" v-if="block.defaultActions">
            <div class="case-head"><span class="case-tag default-tag">DEFAULT</span></div>
            <div class="case-body">
              <div v-for="(a, ai) in block.defaultActions" :key="ai" class="inline-row">
                <var-picker :vars="vars" :selected-vars="selectedVars" :value="a.target" placeholder="变量" size="mini" @select="v => selectVar(a, 'target', v)" />
                <span class="eq">=</span>
                <el-input v-model="a.value" size="mini" placeholder="值" @input="sync" />
              </div>
            </div>
          </div>
          <el-button size="mini" icon="el-icon-plus" @click="block.cases.push({value:'',actions:[{type:'assign',target:'',value:''}]}); sync()" style="width:100%;margin-top:4px">添加 Case</el-button>
        </template>

        <!-- ===== 函数调用 ===== -->
        <template v-if="block.type === 'func-call'">
          <div class="inline-row" style="margin-bottom:4px">
            <span class="mini-label">结果</span>
            <var-picker :vars="vars" :selected-vars="selectedVars" :value="block.target" placeholder="结果变量（可空）" size="mini" @select="v => selectVar(block, 'target', v)" />
          </div>
          <div class="inline-row" style="margin-bottom:4px">
            <span class="mini-label">函数</span>
            <el-select v-if="functions.length" v-model="block.funcName" size="mini" filterable allow-create placeholder="选择或输入函数" style="flex:1" @change="onFuncSelect(block, $event)">
              <el-option v-for="f in functions" :key="f.funcCode" :label="f.funcName + ' (' + f.funcCode + ')'" :value="f.funcCode" />
            </el-select>
            <el-input v-else v-model="block.funcName" size="mini" placeholder="函数名" @input="sync" />
          </div>
          <div class="inline-row" v-for="(arg, ai) in block.args" :key="ai" style="margin-bottom:2px">
            <span class="mini-label">参数{{ai+1}}</span>
            <var-picker
              :vars="vars"
              :selected-vars="selectedVars"
              :value="arg"
              placeholder="参数表达式/字段"
              size="mini"
              :auto-switch-custom="false"
              @input="value => setArgValue(block, ai, value)"
              @select="v => selectArgVar(block, ai, v)"
            />
            <el-button v-if="block.args.length > 1" type="text" size="mini" icon="el-icon-delete" style="color:#F76E6C" @click="removeArg(block, ai)" />
          </div>
          <el-button size="mini" icon="el-icon-plus" @click="addArg(block)" style="width:100%;margin-top:2px">添加参数</el-button>
        </template>

        <!-- ===== Rule Call ===== -->
        <template v-if="block.type === 'rule-call'">
          <div class="inline-row" style="margin-bottom:4px">
            <span class="mini-label">结果</span>
            <var-picker :vars="vars" :selected-vars="selectedVars" :value="block.target" placeholder="结果变量（可空）" size="mini" @select="v => selectVar(block, 'target', v)" />
          </div>
          <div class="inline-row" style="margin-bottom:4px">
            <span class="mini-label">规则</span>
            <el-select
              v-model="block.ruleCode"
              size="mini"
              filterable
              placeholder="选择要执行的规则"
              style="flex:1"
              @visible-change="visible => rememberRuleCallSnapshot(block, visible)"
              @change="onRuleSelect(block, $event)"
            >
              <el-option
                v-for="rule in rules"
                :key="rule.id || rule.ruleCode"
                :label="ruleLabel(rule)"
                :value="rule.ruleCode"
              />
            </el-select>
          </div>
          <div class="inline-row">
            <span class="mini-label">输出字段</span>
            <el-select
              v-if="ruleOutputFields(block).length"
              v-model="block.outputField"
              size="mini"
              filterable
              clearable
              placeholder="选择具体输出字段（可空）"
              style="flex:1"
              @change="sync"
            >
              <el-option
                v-for="field in ruleOutputFields(block)"
                :key="field.id || field.scriptName || field.fieldName"
                :label="fieldLabel(field)"
                :value="field.scriptName || field.fieldName"
              />
            </el-select>
            <el-input
              v-else
              v-model="block.outputField"
              size="mini"
              placeholder="输出字段名（可空，空则返回整条规则结果）"
              @input="sync"
            />
          </div>
        </template>

        <!-- ===== ForEach ===== -->
        <template v-if="block.type === 'foreach'">
          <div class="inline-row" style="margin-bottom:4px">
            <span class="mini-label">循环变量</span>
            <el-input v-model="block.itemVar" size="mini" placeholder="item" @input="sync" style="width:80px" />
            <span class="mini-label" style="margin-left:6px">列表</span>
            <el-input v-model="block.listExpr" size="mini" placeholder="列表变量/表达式" @input="sync" />
          </div>
          <div class="loop-body">
            <div v-for="(a, ai) in block.actions" :key="ai" class="inline-row">
              <var-picker :vars="vars" :selected-vars="selectedVars" :value="a.target" placeholder="变量" size="mini" @select="v => selectVar(a, 'target', v)" />
              <span class="eq">=</span>
              <el-input v-model="a.value" size="mini" placeholder="值/表达式" @input="sync" />
            </div>
            <el-button size="mini" icon="el-icon-plus" @click="block.actions.push({type:'assign',target:'',value:''})" style="width:100%;margin-top:2px">添加赋值</el-button>
          </div>
        </template>

        <!-- ===== 三元表达式 ===== -->
        <template v-if="block.type === 'ternary'">
          <div class="inline-row" style="margin-bottom:4px">
            <span class="mini-label">结果</span>
            <var-picker :vars="vars" :selected-vars="selectedVars" :value="block.target" placeholder="变量" size="mini" @select="v => selectVar(block, 'target', v)" />
          </div>
          <div class="cond-area" style="margin-bottom:4px">
            <var-picker :vars="vars" :selected-vars="selectedVars" :value="block.condVar" placeholder="条件变量" size="mini" @select="v => selectVar(block, 'condVar', v)" />
            <el-select v-model="block.condOp" size="mini" style="width:60px" @change="sync">
              <el-option label="==" value="==" /><el-option label="!=" value="!=" /><el-option label=">" value=">" /><el-option label="<" value="<" />
            </el-select>
            <el-input v-model="block.condValue" size="mini" placeholder="值" @input="sync" style="width:80px" />
          </div>
          <div class="inline-row">
            <span class="mini-label" style="color:#52c41a">真</span>
            <el-input v-model="block.trueValue" size="mini" placeholder="真值" @input="sync" />
            <span class="mini-label" style="color:#F76E6C">假</span>
            <el-input v-model="block.falseValue" size="mini" placeholder="假值" @input="sync" />
          </div>
        </template>

        <!-- ===== IN 判断 ===== -->
        <template v-if="block.type === 'in-check'">
          <div class="inline-row" style="margin-bottom:4px">
            <span class="mini-label">结果</span>
            <var-picker :vars="vars" :selected-vars="selectedVars" :value="block.target" placeholder="变量" size="mini" @select="v => selectVar(block, 'target', v)" />
            <span class="mini-label">检测</span>
            <var-picker :vars="vars" :selected-vars="selectedVars" :value="block.checkVar" placeholder="变量" size="mini" @select="v => selectVar(block, 'checkVar', v)" />
          </div>
          <div class="inline-row" style="flex-wrap:wrap;gap:4px;margin-bottom:4px">
            <span class="mini-label">值列表</span>
            <el-tag v-for="(v, vi) in (block.inValues || [])" :key="vi" closable size="mini" @close="removeInValue(block, vi)">{{ v }}</el-tag>
            <el-input v-model="newInValue" size="mini" placeholder="输入后回车或失焦添加" style="width:120px" @keyup.enter.native="addInValue(block)" @blur="addInValue(block)" />
          </div>
          <div class="inline-row">
            <span class="mini-label" style="color:#52c41a">匹配</span>
            <el-input v-model="block.trueValue" size="mini" placeholder="true" @input="sync" />
            <span class="mini-label" style="color:#F76E6C">不匹配</span>
            <el-input v-model="block.falseValue" size="mini" placeholder="false" @input="sync" />
          </div>
        </template>

        <!-- ===== 动态字符串 ===== -->
        <template v-if="block.type === 'template-str'">
          <div class="inline-row" style="margin-bottom:4px">
            <span class="mini-label">结果</span>
            <var-picker :vars="vars" :selected-vars="selectedVars" :value="block.target" placeholder="变量" size="mini" @select="v => selectVar(block, 'target', v)" />
          </div>
          <div v-for="(p, pi) in block.parts" :key="pi" class="inline-row" style="margin-bottom:2px">
            <el-select v-model="p.type" size="mini" style="width:65px" @change="sync">
              <el-option label="文本" value="text" /><el-option label="表达式" value="expr" />
            </el-select>
            <el-input v-model="p.content" size="mini" :placeholder="p.type === 'expr' ? '变量/表达式' : '文本内容'" @input="sync" />
            <el-button v-if="block.parts.length > 1" type="text" size="mini" icon="el-icon-delete" style="color:#F76E6C" @click="block.parts.splice(pi,1); sync()" />
          </div>
          <el-button size="mini" icon="el-icon-plus" @click="block.parts.push({type:'text',content:''}); sync()" style="width:100%;margin-top:2px">添加片段</el-button>
        </template>

      </div>
    </div>

    <!-- 添加块按钮 -->
    <el-dropdown trigger="click" @command="addBlock" style="width:100%;margin-top:6px">
      <el-button size="mini" icon="el-icon-plus" style="width:100%">添加动作块</el-button>
      <el-dropdown-menu slot="dropdown">
        <el-dropdown-item v-for="bt in blockTypes" :key="bt.type" :command="bt.type">
          <i :class="bt.icon" :style="{color: bt.color}" /> {{ bt.label }}
        </el-dropdown-item>
      </el-dropdown-menu>
    </el-dropdown>
  </div>
</template>

<script>
import { blocksToActionData, actionDataToBlocks, newBlock, generateScript, BLOCK_TYPES } from '@/utils/actionDataCodegen'
import VarPicker from '@/components/common/VarPicker.vue'

export default {
  name: 'ActionBlockEditor',
  components: { VarPicker },
  props: {
    actionData: { type: Array, default: () => [] },
    vars: { type: Array, default: () => [] },
    selectedVars: { type: Array, default: () => [] },
    functions: { type: Array, default: () => [] },
    rules: { type: Array, default: () => [] },
    currentRuleId: { type: [String, Number], default: null },
    currentRuleCode: { type: String, default: '' },
    validateRuleCallCycle: { type: Function, default: null }
  },
  data() {
    return {
      blocks: [],
      blockTypes: BLOCK_TYPES,
      newInValue: ''
    }
  },
  watch: {
    actionData: {
      handler(val) {
        this.blocks = actionDataToBlocks(val || [])
      },
      immediate: true,
      deep: false
    }
  },
  beforeDestroy() {
    // 切换至脚本预览时组件会被销毁，先提交未按回车添加的值
    if (this.newInValue.trim()) {
      const inCheckBlock = this.blocks.find(b => b.type === 'in-check')
      if (inCheckBlock) {
        const vals = [...(inCheckBlock.inValues || []).filter(v => v != null && String(v).trim() !== ''), this.newInValue.trim()]
        this.$set(inCheckBlock, 'inValues', vals)
        this.$emit('update', blocksToActionData(this.blocks))
      }
    }
  },
  computed: {
    scriptPreview() {
      return generateScript(blocksToActionData(this.blocks))
    }
  },
  methods: {
    sync() {
      this.$emit('update', blocksToActionData(this.blocks))
    },
    selectVar(holder, field, v) {
      if (!holder || !field) return
      const value = v && v.varCode ? v.varCode : ''
      this.$set(holder, field, value)
      this.setFieldRef(holder, field, v)
      if (this.shouldMirrorLegacyRef(holder, field)) {
        this.setLegacyRef(holder, v)
      }
      this.sync()
    },
    setFieldRef(holder, field, v) {
      const keys = this.fieldRefKeys(field)
      if (!keys) return
      const id = this.refIdOf(v)
      const refType = this.refTypeOf(v)
      if (id != null && id !== '') {
        this.$set(holder, keys.id, id)
      } else {
        this.$delete(holder, keys.id)
      }
      if (refType) {
        this.$set(holder, keys.refType, refType)
      } else {
        this.$delete(holder, keys.refType)
      }
    },
    setLegacyRef(holder, v) {
      const id = this.refIdOf(v)
      const refType = this.refTypeOf(v)
      if (id != null && id !== '') {
        this.$set(holder, '_varId', id)
      } else {
        this.$delete(holder, '_varId')
      }
      if (refType) {
        this.$set(holder, '_refType', refType)
      } else {
        this.$delete(holder, '_refType')
      }
    },
    shouldMirrorLegacyRef(holder, field) {
      if (field === 'target') {
        return !holder || !['ternary', 'in-check'].includes(holder.type)
      }
      if (field === 'condVar') {
        return !holder || !holder.type || holder.type === 'if' || holder.type === 'elseif'
      }
      return field === 'matchVar'
    },
    fieldRefKeys(field) {
      const map = {
        target: { id: '_targetVarId', refType: '_targetRefType' },
        condVar: { id: '_condVarId', refType: '_condVarRefType' },
        matchVar: { id: '_matchVarId', refType: '_matchVarRefType' },
        checkVar: { id: '_checkVarId', refType: '_checkVarRefType' }
      }
      return map[field] || null
    },
    refIdOf(v) {
      if (!v) return null
      if (v._varId != null) return v._varId
      if (v.id != null) return v.id
      if (v.varObj && v.varObj.id != null) return v.varObj.id
      if (v._ref && v._ref.id != null) return v._ref.id
      return null
    },
    refTypeOf(v) {
      if (!v) return ''
      return v._refType || v.refType || (v.varObj && v.varObj.refType) || (v._ref && v._ref.refType) || ''
    },
    addBlock(type) {
      this.blocks.push(newBlock(type))
      this.sync()
    },
    setArgValue(block, index, value) {
      if (!block || !Array.isArray(block.args)) return
      this.$set(block.args, index, value || '')
      this.clearArgRef(block, index)
      this.sync()
    },
    selectArgVar(block, index, variable) {
      if (!variable) return
      if (!block || !Array.isArray(block.args)) return
      this.$set(block.args, index, variable.varCode || '')
      this.setArgRef(block, index, variable)
      this.sync()
    },
    setArgRef(block, index, variable) {
      if (!block) return
      if (!Array.isArray(block._argRefs)) this.$set(block, '_argRefs', [])
      const id = this.refIdOf(variable)
      const refType = this.refTypeOf(variable)
      if (id != null && id !== '') {
        this.$set(block._argRefs, index, { _varId: id, _refType: refType || undefined })
      } else {
        this.$set(block._argRefs, index, null)
      }
    },
    clearArgRef(block, index) {
      if (!block || !Array.isArray(block._argRefs)) return
      this.$set(block._argRefs, index, null)
    },
    addArg(block) {
      if (!block || !Array.isArray(block.args)) return
      block.args.push('')
      if (Array.isArray(block._argRefs)) block._argRefs.push(null)
      this.sync()
    },
    removeArg(block, index) {
      if (!block || !Array.isArray(block.args)) return
      block.args.splice(index, 1)
      if (Array.isArray(block._argRefs)) block._argRefs.splice(index, 1)
      this.sync()
    },
    removeBlock(bi) {
      this.blocks.splice(bi, 1)
      this.sync()
    },
    moveBlock(bi, dir) {
      const target = bi + dir
      if (target < 0 || target >= this.blocks.length) return
      const temp = this.blocks[bi]
      this.$set(this.blocks, bi, this.blocks[target])
      this.$set(this.blocks, target, temp)
      this.sync()
    },
    hasElse(block) {
      return (block.branches || []).some(b => b.type === 'else')
    },
    addBranch(block, type) {
      block.branches.push({ type, condVar: '', condOp: '==', condValue: '', actions: [{ type: 'assign', target: '', value: '' }] })
      this.sync()
    },
    removeBranch(block, bri) {
      block.branches.splice(bri, 1)
      if (block.branches.length > 0 && block.branches[0].type !== 'if') block.branches[0].type = 'if'
      this.sync()
    },
    addInValue(block) {
      if (this.newInValue.trim()) {
        const vals = [...(block.inValues || []).filter(v => v != null && String(v).trim() !== ''), this.newInValue.trim()]
        this.$set(block, 'inValues', vals)
        this.newInValue = ''
        this.sync()
      }
    },
    removeInValue(block, index) {
      const vals = [...(block.inValues || [])]
      vals.splice(index, 1)
      this.$set(block, 'inValues', vals)
      this.sync()
    },
    onFuncSelect(block, funcCode) {
      const func = this.functions.find(f => f.funcCode === funcCode)
      if (func && func.paramsJson) {
        try {
          const params = JSON.parse(func.paramsJson)
          block.args = params.map(p => p.name || '')
        } catch (e) { /* ignore */ }
      }
      this.sync()
    },
    rememberRuleCallSnapshot(block, visible) {
      if (!visible || !block) return
      Object.defineProperty(block, '__ruleCallSnapshot', {
        value: {
          ruleId: block.ruleId == null ? null : block.ruleId,
          ruleCode: block.ruleCode || '',
          ruleName: block.ruleName || '',
          modelType: block.modelType || '',
          outputField: block.outputField || ''
        },
        enumerable: false,
        configurable: true,
        writable: true
      })
    },
    restoreRuleCallSnapshot(block) {
      if (!block) return
      const snapshot = block.__ruleCallSnapshot || {
        ruleId: null,
        ruleCode: '',
        ruleName: '',
        modelType: '',
        outputField: ''
      }
      this.$set(block, 'ruleId', snapshot.ruleId == null ? null : snapshot.ruleId)
      this.$set(block, 'ruleCode', snapshot.ruleCode || '')
      this.$set(block, 'ruleName', snapshot.ruleName || '')
      this.$set(block, 'modelType', snapshot.modelType || '')
      this.$set(block, 'outputField', snapshot.outputField || '')
      this.clearRuleCallSnapshot(block)
    },
    clearRuleCallSnapshot(block) {
      if (block && Object.prototype.hasOwnProperty.call(block, '__ruleCallSnapshot')) {
        delete block.__ruleCallSnapshot
      }
    },
    async validateSelectedRuleCall(block) {
      if (typeof this.validateRuleCallCycle !== 'function') return true
      try {
        const result = await this.validateRuleCallCycle(block)
        return result === false ? '规则调用存在环路' : (result || true)
      } catch (e) {
        return (e && e.message) || '规则调用环校验失败'
      }
    },
    async onRuleSelect(block, ruleCode) {
      if (!block) return
      const rule = (this.rules || []).find(item => String(item.ruleCode) === String(ruleCode))
      if (!rule) {
        this.$set(block, 'ruleId', null)
        this.$set(block, 'ruleName', '')
        this.$set(block, 'modelType', '')
        this.clearRuleCallSnapshot(block)
        this.sync()
        return
      }
      if (this.isCurrentRule(rule)) {
        if (this.$message) this.$message.warning('不能调用当前规则自身，会形成规则调用环')
        this.restoreRuleCallSnapshot(block)
        this.sync()
        return
      }
      this.$set(block, 'ruleId', rule.id || null)
      this.$set(block, 'ruleName', rule.ruleName || '')
      this.$set(block, 'modelType', rule.modelType || '')
      if (!this.ruleOutputFields(block).some(field => (field.scriptName || field.fieldName) === block.outputField)) {
        this.$set(block, 'outputField', '')
      }
      this.sync()
      const validation = await this.validateSelectedRuleCall(block)
      if (validation !== true) {
        if (this.$message) this.$message.warning(validation)
        this.restoreRuleCallSnapshot(block)
        this.sync()
        return
      }
      this.clearRuleCallSnapshot(block)
    },
    isCurrentRule(rule) {
      if (!rule) return false
      if (this.currentRuleId != null && String(rule.id) === String(this.currentRuleId)) return true
      return !!this.currentRuleCode && String(rule.ruleCode) === String(this.currentRuleCode)
    },
    ruleLabel(rule) {
      if (!rule) return ''
      const name = rule.ruleName || rule.ruleCode || ''
      const code = rule.ruleCode && rule.ruleCode !== name ? ' (' + rule.ruleCode + ')' : ''
      const type = rule.modelType ? ' - ' + rule.modelType : ''
      return name + code + type
    },
    ruleOutputFields(block) {
      if (!block || !block.ruleCode) return []
      const rule = (this.rules || []).find(item => String(item.ruleCode) === String(block.ruleCode))
      return rule ? (rule.outputFieldsJson || rule.outputFields || []) : []
    },
    fieldLabel(field) {
      if (!field) return ''
      const name = field.fieldLabel || field.fieldName || field.scriptName || ''
      const code = field.scriptName || field.fieldName || ''
      return code && code !== name ? name + ' (' + code + ')' : name
    },
    typeLabel(type) {
      const t = BLOCK_TYPES.find(b => b.type === type)
      return t ? t.label : type
    },
    typeColor(type) {
      const t = BLOCK_TYPES.find(b => b.type === type)
      return t ? t.color : '#999'
    }
  }
}
</script>

<style lang="scss" scoped>
.block-editor { padding: 0; }
.block-item {
  border: 1px solid #e0e0e0;
  border-radius: 4px;
  margin-bottom: 8px;
  background: #fafafa;
  overflow: hidden;
}
.block-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 3px 6px;
  background: #f0f0f0;
  border-bottom: 1px solid #e0e0e0;
}
.block-header-actions { display: flex; gap: 0; }
.block-type-tag {
  font-size: 10px;
  font-weight: bold;
  color: #fff;
  padding: 1px 6px;
  border-radius: 3px;
}
.block-body { padding: 6px 8px; }

.inline-row {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 3px;
}
.eq { font-weight: bold; color: #999; flex-shrink: 0; }
.mini-label { font-size: 11px; color: #888; flex-shrink: 0; white-space: nowrap; }
.rounding-row {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 3px;
  padding: 3px 0;
}

.branch-card {
  border: 1px solid #d9d9d9;
  border-radius: 3px;
  margin-bottom: 6px;
  background: #fff;
  overflow: hidden;
}
.branch-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 2px 6px;
  background: #f5f5f5;
}
.branch-tag {
  font-size: 10px;
  font-weight: bold;
  color: #fff;
  padding: 1px 6px;
  border-radius: 2px;
  &.tag-if { background: #1890ff; }
  &.tag-elseif { background: #fa8c16; }
  &.tag-else { background: #8c8c8c; }
}
.cond-area {
  display: flex;
  align-items: center;
  gap: 3px;
  padding: 4px 6px;
  border-bottom: 1px dashed #e8e8e8;
}
.branch-body { padding: 4px 6px; }
.branch-add-row { display: flex; gap: 4px; margin-top: 4px; }

.case-card {
  border: 1px solid #e0e0e0;
  border-radius: 3px;
  margin-bottom: 4px;
  background: #fff;
  &.default-case { background: #f9f9f9; }
}
.case-head {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 3px 6px;
  background: #f5f5f5;
}
.case-tag {
  font-size: 10px;
  font-weight: bold;
  color: #fff;
  background: #722ed1;
  padding: 1px 6px;
  border-radius: 2px;
  flex-shrink: 0;
  &.default-tag { background: #8c8c8c; }
}
.case-body { padding: 4px 6px; }
.loop-body {
  padding: 4px 6px;
  border: 1px dashed #d9d9d9;
  border-radius: 3px;
  background: #fff;
}
</style>
