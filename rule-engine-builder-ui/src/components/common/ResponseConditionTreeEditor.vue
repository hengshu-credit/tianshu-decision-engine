<template>
  <div class="response-condition-tree" :class="{ nested: depth > 0 }">
    <div class="tree-head">
      <el-button-group>
        <el-button size="mini" :type="group.operator === 'AND' ? 'primary' : 'default'" @click="setOperator('AND')">且</el-button>
        <el-button size="mini" :type="group.operator === 'OR' ? 'primary' : 'default'" @click="setOperator('OR')">或</el-button>
      </el-button-group>
      <el-button v-if="depth > 0" type="text" size="mini" class="danger" @click="$emit('remove')">删除条件组</el-button>
    </div>

    <div v-for="(child, index) in group.children" :key="depth + '-' + index" class="tree-row">
      <response-condition-tree-editor
        v-if="child.type === 'group'"
        :group="child"
        :path-options="pathOptions"
        :depth="depth + 1"
        @remove="removeChild(index)"
      />
      <div v-else class="condition-row">
        <el-select v-model="child.path" filterable allow-create default-first-option size="mini" placeholder="响应字段路径">
          <el-option v-for="item in pathOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-select v-model="child.operator" size="mini" @change="onOperatorChange(child)">
          <el-option v-for="item in operatorOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-select
          v-if="isMultiValue(child.operator)"
          v-model="child.values"
          multiple
          filterable
          allow-create
          default-first-option
          size="mini"
          placeholder="输入一个或多个值"
        />
        <el-input v-else v-model="child.value" size="mini" :placeholder="valuePlaceholder(child.operator)" />
        <el-button type="text" size="mini" class="danger" @click="removeChild(index)">删除</el-button>
      </div>
    </div>

    <div class="tree-actions">
      <el-button size="mini" round @click="addCondition">加条件</el-button>
      <el-button size="mini" round @click="addGroup">加条件组</el-button>
    </div>
  </div>
</template>

<script>
export default {
  name: 'ResponseConditionTreeEditor',
  props: {
    group: { type: Object, required: true },
    pathOptions: { type: Array, default: () => [] },
    depth: { type: Number, default: 0 }
  },
  data() {
    return {
      operatorOptions: [
        { label: '字符串等于', value: '==' },
        { label: '字符串不等于', value: '!=' },
        { label: '以…开头', value: 'starts_with' },
        { label: '不以…开头', value: 'not_starts_with' },
        { label: '在列表内', value: 'in' },
        { label: '不在列表内', value: 'not_in' },
        { label: '正则匹配', value: 'regex' },
        { label: '正则不匹配', value: 'not_regex' },
        { label: '包含', value: 'contains' },
        { label: '不包含', value: 'not_contains' },
        { label: '以…结尾', value: 'ends_with' },
        { label: '不以…结尾', value: 'not_ends_with' }
      ]
    }
  },
  methods: {
    setOperator(operator) {
      this.$set(this.group, 'operator', operator)
    },
    addCondition() {
      if (!Array.isArray(this.group.children)) this.$set(this.group, 'children', [])
      this.group.children.push({ type: 'condition', path: '', operator: '==', value: '' })
    },
    addGroup() {
      if (!Array.isArray(this.group.children)) this.$set(this.group, 'children', [])
      this.group.children.push({
        type: 'group',
        operator: 'AND',
        children: [{ type: 'condition', path: '', operator: '==', value: '' }]
      })
    },
    removeChild(index) {
      this.group.children.splice(index, 1)
    },
    isMultiValue(operator) {
      return operator === 'in' || operator === 'not_in'
    },
    onOperatorChange(condition) {
      if (this.isMultiValue(condition.operator)) {
        if (!Array.isArray(condition.values)) this.$set(condition, 'values', [])
        this.$delete(condition, 'value')
      } else {
        if (condition.value == null) this.$set(condition, 'value', '')
        this.$delete(condition, 'values')
      }
    },
    valuePlaceholder(operator) {
      return operator === 'regex' || operator === 'not_regex' ? '输入正则表达式' : '输入判断值'
    }
  }
}
</script>

<style scoped>
.response-condition-tree {
  border-left: 2px solid #dbe3ef;
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
  color: #dc2626;
}
@media (max-width: 900px) {
  .condition-row {
    grid-template-columns: 1fr;
  }
}
</style>
