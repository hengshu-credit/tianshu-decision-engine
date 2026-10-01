<template>
  <div class="response-condition-tree" :class="{ nested: depth > 0 }">
    <div class="tree-head">
      <el-button-group>
        <el-button
          size="small"
          :type="group.operator === 'AND' ? 'primary' : 'default'"
          @click="setOperator('AND')"
          >且</el-button
        >
        <el-button
          size="small"
          :type="group.operator === 'OR' ? 'primary' : 'default'"
          @click="setOperator('OR')"
          >或</el-button
        >
      </el-button-group>
      <el-button
        v-if="depth > 0"
        link
        size="small"
        class="danger"
        @click="$emit('remove')"
        >删除条件组</el-button
      >
    </div>

    <div
      v-for="(child, index) in group.children"
      :key="depth + '-' + index"
      class="tree-row"
    >
      <response-condition-tree-editor
        v-if="child.type === 'group'"
        :group="child"
        :path-options="pathOptions"
        :depth="depth + 1"
        :operand-mode="operandMode"
        :vars="vars"
        :functions="functions"
        @remove="removeChild(index)"
      />
      <div v-else class="condition-row">
        <api-value-editor v-if="operandMode" :value="child.left || pathOperand(child.path)" :vars="vars" :functions="functions" :modules="pathOptions" @update:value="child.left = $event" />
        <el-select
          v-else
          v-model="child.path"
          filterable
          allow-create
          default-first-option
          size="small"
          placeholder="响应字段路径"
        >
          <el-option
            v-for="item in pathOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <el-select
          v-model="child.operator"
          size="small"
          @change="onOperatorChange(child)"
        >
          <el-option
            v-for="item in operatorOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <api-value-editor v-if="operandMode && !operatorHasNoValue(child.operator)" :value="child.right || literalOperand(child.value)" :vars="vars" :functions="functions" :modules="pathOptions" @update:value="child.right = $event" />
        <el-select
          v-else-if="!operatorHasNoValue(child.operator) && isMultiValue(child.operator)"
          v-model="child.values"
          multiple
          filterable
          allow-create
          default-first-option
          size="small"
          placeholder="输入一个或多个值"
        />
        <el-input
          v-else-if="!operatorHasNoValue(child.operator)"
          v-model="child.value"
          size="small"
          :placeholder="valuePlaceholder(child.operator)"
        />
        <el-button
          link
          size="small"
          class="danger"
          @click="removeChild(index)"
          >删除</el-button
        >
      </div>
    </div>

    <div class="tree-actions">
      <el-button size="small" round @click="addCondition">加条件</el-button>
      <el-button size="small" round @click="addGroup">加条件组</el-button>
    </div>
  </div>
</template>

<script>
import ApiValueEditor from './ApiValueEditor.vue'
export default {
  name: 'ResponseConditionTreeEditor',
  components: { ApiValueEditor },
  props: {
    group: { type: Object, required: true },
    pathOptions: { type: Array, default: () => [] },
    depth: { type: Number, default: 0 },
    operandMode: Boolean,
    vars: { type: Array, default: () => [] },
    functions: { type: Array, default: () => [] },
  },
  data() {
    return {
      operatorOptions: [
        { label: '字符串等于', value: '==' },
        { label: '字符串不等于', value: '!=' },
        { label: '大于', value: '>' },
        { label: '大于等于', value: '>=' },
        { label: '小于', value: '<' },
        { label: '小于等于', value: '<=' },
        { label: '为空', value: 'is_null' },
        { label: '路径缺失', value: 'missing', noValue: true },
        { label: '存在', value: 'exists' },
        { label: '为空字符串/空集合', value: 'is_empty', noValue: true },
        { label: '非空', value: 'not_empty', noValue: true },
        { label: '类型等于', value: 'type_is' },
        { label: '类型变化/不等于', value: 'type_changed' },
        { label: '以…开头', value: 'starts_with' },
        { label: '不以…开头', value: 'not_starts_with' },
        { label: '在列表内', value: 'in' },
        { label: '不在列表内', value: 'not_in' },
        { label: '正则匹配', value: 'regex' },
        { label: '正则不匹配', value: 'not_regex' },
        { label: '包含', value: 'contains' },
        { label: '不包含', value: 'not_contains' },
        { label: '以…结尾', value: 'ends_with' },
        { label: '不以…结尾', value: 'not_ends_with' },
      ],
    }
  },
  methods: {
    pathOperand(path) { return path ? { kind: 'PATH', value: path, protocolPath: true, resolved: true } : null },
    literalOperand(value) { return value == null ? null : { kind: 'LITERAL', valueType: 'STRING', value: String(value) } },
    setOperator(operator) {
      this.group['operator'] = operator
    },
    addCondition() {
      if (!Array.isArray(this.group.children)) this.group['children'] = []
      this.group.children.push({
        type: 'condition',
        path: '',
        operator: '==',
        value: '',
      })
    },
    addGroup() {
      if (!Array.isArray(this.group.children)) this.group['children'] = []
      this.group.children.push({
        type: 'group',
        operator: 'AND',
        children: [{ type: 'condition', path: '', operator: '==', value: '' }],
      })
    },
    removeChild(index) {
      this.group.children.splice(index, 1)
    },
    isMultiValue(operator) {
      return operator === 'in' || operator === 'not_in'
    },
    onOperatorChange(condition) {
      if (this.operatorHasNoValue(condition.operator)) {
        delete condition.value
        delete condition.values
        delete condition.right
      } else if (this.isMultiValue(condition.operator)) {
        if (!Array.isArray(condition.values)) condition['values'] = []
        delete condition.value
      } else {
        if (condition.value == null) condition['value'] = ''
        delete condition.values
      }
    },
    operatorHasNoValue(operator) {
      return ['missing', 'exists', 'is_null', 'is_empty', 'not_empty'].includes(operator)
    },
    valuePlaceholder(operator) {
      return operator === 'regex' || operator === 'not_regex'
        ? '输入正则表达式'
        : '输入判断值'
    },
  },
  emits: ['remove'],
}
</script>

<style scoped>
.response-condition-tree {
  border-left: 2px solid var(--el-border-color);
  padding-left: 12px;
}
.response-condition-tree.nested {
  margin-top: 4px;
}
.tree-head,
.tree-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.tree-row {
  margin-bottom: 8px;
}
.condition-row {
  display: grid;
  grid-template-columns: minmax(180px, 1.2fr) 150px minmax(180px, 1fr) auto;
  align-items: center;
  gap: 8px;
}
.danger {
  color: var(--el-color-danger);
}
@media (max-width: 900px) {
  .condition-row {
    grid-template-columns: 1fr;
  }
}
</style>
