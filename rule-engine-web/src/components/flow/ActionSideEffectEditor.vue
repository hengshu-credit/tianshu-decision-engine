<template>
  <div class="side-effect-editor">
    <template v-if="block.type === 'api-call'">
      <label>外数 API</label>
      <resource-picker v-model:value="block.apiId" :resources="editor.apiOptions" aria-label="外数 API" placeholder="选择要调用的 API 外数" @update:value="editor.sync()" />
      <label>完整结果赋值（可选）</label>
      <operand-picker :value="block.targetOperand" :vars="objectTargets" :selected-vars="selectedTargets" :allowed-kinds="editor.writeKinds" writable-only expected-type="OBJECT" placeholder="选择接收完整结果的变量或对象" @input="value => editor.setOperand(block, 'targetOperand', value)" />
      <p>执行到此动作时调用 API。完整结果包含请求、响应、组装结果、调用状态等内容；后续规则可从内容选择器的外数字段继续取值。同一请求与隐式调用共用结果。</p>
    </template>
    <template v-else>
      <label>目标名单</label>
      <resource-picker v-model:value="block.listIds" :resources="editor.listOptions" category="list" aria-label="目标名单" multiple placeholder="选择一个或多个名单" @update:value="editor.sync()" />
      <label>执行操作</label>
      <el-select v-model="block.operation" aria-label="名单执行操作" @change="editor.sync()">
        <el-option label="加入并合并有效期" value="MERGE" />
        <el-option label="加入并覆盖有效期" value="OVERWRITE" />
        <el-option label="仅新增（有效内容保持不变）" value="ADD_ONLY" />
        <el-option label="从名单剔除" value="REMOVE" />
      </el-select>
      <template v-if="block.operation !== 'REMOVE'">
        <label>有效期</label>
        <el-select v-model="block.expiryMode" aria-label="名单有效期" @change="editor.sync()">
          <el-option label="执行后指定时长" value="DURATION" />
          <el-option label="指定失效时间" value="UNTIL" />
          <el-option label="永久有效" value="PERMANENT" />
        </el-select>
        <div v-if="block.expiryMode === 'DURATION'" class="duration-row">
          <el-input-number v-model="block.duration" aria-label="有效时长" :min="1" :precision="0" @change="editor.sync()" />
          <el-select v-model="block.durationUnit" aria-label="时长单位" @change="editor.sync()"><el-option label="天" value="DAYS" /><el-option label="小时" value="HOURS" /><el-option label="分钟" value="MINUTES" /></el-select>
        </div>
        <el-date-picker v-if="block.expiryMode === 'UNTIL'" v-model="block.expireTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="选择失效时间" @change="editor.sync()" />
      </template>
      <label>名单内容</label>
      <div v-for="(entry, index) in block.entries" :key="index" class="content-row">
        <el-select v-model="entry.itemType" :aria-label="`内容类型 ${index + 1}`" @change="editor.sync()"><el-option v-for="type in itemTypes" :key="type.value" :label="type.label" :value="type.value" /></el-select>
        <operand-picker :value="entry.valueOperand" :vars="contentFields" :selected-vars="editor.selectedVars" :functions="editor.functions" :allowed-kinds="['REFERENCE', 'PATH', 'FUNCTION', 'OPERATION', 'ACCESS', 'CAST']" placeholder="从内容选择器选择内容或表达式" @input="value => editor.setOperand(entry, 'valueOperand', value)" />
        <el-button link type="danger" :aria-label="`删除内容 ${index + 1}`" @click="removeEntry(index)">删除</el-button>
      </div>
      <el-button @click="addEntry">添加内容字段</el-button>
      <p>合并保留更早生效时间和更晚失效时间，永久有效优先；空值不写入。任务持久化后异步落库，失败自动重试，不阻塞后续规则；后续查询不保证立即读到本次写入。</p>
    </template>
  </div>
</template>
<script>
import OperandPicker from '@/components/common/OperandPicker.vue'
import ResourcePicker from '@/components/common/ResourcePicker.vue'
import { LIST_ITEM_TYPES } from '@/constants/listMatchModes'
export default {
  name: 'ActionSideEffectEditor',
  components: { OperandPicker, ResourcePicker },
  inject: { editor: { from: 'actionEditor' } },
  props: { block: { type: Object, required: true } },
  data: () => ({ itemTypes: LIST_ITEM_TYPES }),
  computed: {
    objectTargets() { return this.editor.vars.filter(item => ['OBJECT', 'MAP'].includes(item.varType || item.valueType || item.type)) },
    selectedTargets() { return this.editor.selectedVars.filter(item => ['OBJECT', 'MAP'].includes(item.varType || item.valueType || item.type)) },
    contentFields() { return this.editor.vars.filter(item => !['OBJECT', 'MAP', 'LIST', 'ARRAY', 'COLLECTION', 'SET'].includes(item.varType || item.valueType || item.type)) },
  },
  methods: {
    addEntry() { this.block.entries.push({ itemType: 'MOBILE', valueOperand: null }); this.editor.sync() },
    removeEntry(index) { this.block.entries.splice(index, 1); this.editor.sync() },
  },
}
</script>
<style scoped>
.side-effect-editor { display: grid; gap: 8px; min-width: 0; }
.side-effect-editor label { font-size: 12px; color: var(--tianshu-text-secondary); }
.side-effect-editor p { margin: 2px 0; font-size: 12px; line-height: 1.6; color: var(--tianshu-text-secondary); }
.duration-row, .content-row { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
.duration-row > *, .content-row > .el-select { flex: 0 1 130px; }
.content-row > .operand-picker { flex: 1 1 180px; min-width: 0; }
.side-effect-editor :deep(.el-date-editor) { max-width: 100%; }
</style>
