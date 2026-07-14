<template>
  <aside class="expression-inspector">
    <template v-if="node">
      <div class="inspector-heading">
        <div>
          <h3>{{ kindName }}</h3>
          <p>{{ kindTip }}</p>
        </div>
        <el-button type="text" class="danger-button" @click="$emit('remove')">删除节点</el-button>
      </div>

      <template v-if="node.kind === 'LITERAL'">
        <label>阈值类型</label>
        <el-select :value="node.valueType" size="small" @input="patch({ valueType: $event })">
          <el-option v-for="type in valueTypes" :key="type.value" :label="type.label" :value="type.value" />
        </el-select>
        <label>阈值</label>
        <el-input :value="node.value" size="small" placeholder="请输入阈值" @input="patch({ value: $event })" />
      </template>

      <template v-else-if="node.kind === 'OPERATION'">
        <label>运算符</label>
        <el-select :value="node.operator" size="small" filterable @input="patch({ operator: $event })">
          <el-option v-for="operator in operators" :key="operator" :label="operator" :value="operator" />
        </el-select>
        <div class="inline-heading"><label>运算项</label><el-button type="text" @click="addChild('operands')">增加一项</el-button></div>
        <div v-for="(item, index) in node.operands || []" :key="index" class="argument-row">
          <span>第 {{ index + 1 }} 项</span>
          <el-button type="text" :disabled="(node.operands || []).length <= 1" @click="removeChild('operands', index)">删除</el-button>
        </div>
      </template>

      <template v-else-if="node.kind === 'FUNCTION'">
        <label>函数 / 方法</label>
        <el-input :value="node.functionCode" size="small" disabled />
        <div class="inline-heading"><label>参数</label><el-button type="text" @click="addArgument">增加参数</el-button></div>
        <div v-for="(arg, index) in node.args || []" :key="index" class="argument-row">
          <span>参数 {{ index + 1 }}{{ arg ? ' · 已配置' : ' · 待配置' }}</span>
          <el-button type="text" @click="removeArgument(index)">删除</el-button>
        </div>
        <p class="field-tip">点击中间画布中的参数位置，再从左侧添加字段、阈值、方法或运算。</p>
      </template>

      <template v-else-if="node.kind === 'ACCESS'">
        <label>取值方式</label>
        <el-radio-group :value="node.accessType" size="small" @input="patch({ accessType: $event })">
          <el-radio-button label="KEY">字典 Key</el-radio-button>
          <el-radio-button label="INDEX">数组 Index</el-radio-button>
        </el-radio-group>
        <p class="field-tip">目标和值位置都在中间画布中配置，可继续嵌套方法或表达式。</p>
      </template>

      <template v-else-if="node.kind === 'CAST'">
        <label>目标类型</label>
        <el-select :value="node.targetType" size="small" @input="patch({ targetType: $event, valueType: $event })">
          <el-option v-for="type in valueTypes" :key="type.value" :label="type.label" :value="type.value" />
        </el-select>
      </template>

      <template v-else-if="node.kind === 'ARRAY'">
        <div class="inline-heading"><label>数组元素</label><el-button type="text" @click="addChild('items')">增加元素</el-button></div>
        <div v-for="(item, index) in node.items || []" :key="index" class="argument-row">
          <span>元素 {{ index + 1 }}{{ item ? ' · 已配置' : ' · 待配置' }}</span>
          <el-button type="text" @click="removeChild('items', index)">删除</el-button>
        </div>
      </template>

      <template v-else-if="node.kind === 'LIST_QUERY'">
        <label>名单（可多选）</label>
        <el-select :value="node.listIds" size="small" multiple filterable @input="patch({ listIds: $event })">
          <el-option v-for="item in listOptions" :key="item.id" :label="item.listName || item.name || item.listCode" :value="item.id" />
        </el-select>
        <label>字段与名单组合</label>
        <el-select :value="node.combinationMode" size="small" @input="patch({ combinationMode: $event })">
          <el-option v-for="item in listCombinationModes" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <p class="field-tip">{{ listCombinationDescription }}</p>
        <label>名单内容匹配</label>
        <el-radio-group :value="node.matchMode" size="small" @input="patch({ matchMode: $event })">
          <el-radio-button v-for="item in listMatchModes" :key="item.value" :label="item.value">{{ item.label }}</el-radio-button>
        </el-radio-group>
        <p class="field-tip">“不在名单内”由条件操作符控制，这里只配置名单内容如何匹配，避免重复取反。</p>
        <label>内容类型</label>
        <el-select :value="node.itemTypes" size="small" multiple clearable collapse-tags placeholder="不选表示任意类型" @input="patch({ itemTypes: $event })">
          <el-option v-for="item in listItemTypes" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </template>

      <template v-else>
        <label>数据类型</label>
        <el-input :value="node.valueType || '由字段定义'" size="small" disabled />
        <p class="field-tip">字段引用通过 ID 保存，字段编码变更后仍可正确解析。</p>
      </template>
    </template>
    <div v-else class="empty-inspector">
      <i class="el-icon-position" />
      <h3>选择要配置的位置</h3>
      <p>先点中间画布的空位置，再点左侧字段、方法或运算符。</p>
    </div>
  </aside>
</template>

<script>
import { cloneOperand } from '@/utils/operand'
import { LIST_COMBINATION_MODES, LIST_ITEM_TYPES, POSITIVE_LIST_MATCH_MODES, listCombinationMode } from '@/constants/listMatchModes'

export default {
  name: 'ExpressionNodeInspector',
  props: {
    node: { type: Object, default: null },
    listOptions: { type: Array, default: () => [] }
  },
  data() {
    return {
      operators: ['+', '-', '*', '/', '%', '==', '!=', '>', '>=', '<', '<=', '&&', '||'],
      valueTypes: [
        { label: '文本', value: 'STRING' },
        { label: '数字', value: 'NUMBER' },
        { label: '布尔', value: 'BOOLEAN' },
        { label: '日期', value: 'DATE' },
        { label: '日期时间', value: 'DATETIME' },
        { label: '数组', value: 'LIST' },
        { label: '字典', value: 'MAP' }
      ],
      listCombinationModes: LIST_COMBINATION_MODES,
      listMatchModes: POSITIVE_LIST_MATCH_MODES,
      listItemTypes: LIST_ITEM_TYPES
    }
  },
  computed: {
    kindName() {
      return { LITERAL: '输入阈值', PATH: '自由路径', REFERENCE: '字段引用', FUNCTION: '函数参数', OPERATION: '运算配置', ACCESS: '取值配置', CAST: '类型转换', ARRAY: '数组配置', LIST_QUERY: '名单查询' }[this.node.kind] || '节点配置'
    },
    kindTip() {
      return { FUNCTION: '参数可增减，也可继续嵌套函数与运算。', OPERATION: '按画布顺序计算，可继续增加运算项。', ACCESS: '支持字典 Key 与数组 Index 取值。', CAST: '先配置目标类型，再配置待转换值。' }[this.node.kind] || '修改会立即反映到中间公式结构。'
    },
    listCombinationDescription() {
      return listCombinationMode(this.node && this.node.combinationMode).description
    }
  },
  methods: {
    patch(fields) {
      this.$emit('input', { ...cloneOperand(this.node), ...fields })
    },
    addArgument() {
      this.patch({ args: (this.node.args || []).concat([null]) })
    },
    removeArgument(index) {
      const args = (this.node.args || []).slice()
      args.splice(index, 1)
      this.patch({ args })
    },
    addChild(key) {
      this.patch({ [key]: (this.node[key] || []).concat([null]) })
    },
    removeChild(key, index) {
      const values = (this.node[key] || []).slice()
      values.splice(index, 1)
      this.patch({ [key]: values })
    }
  }
}
</script>

<style scoped>
.expression-inspector { padding: 18px; overflow: auto; border-left: 1px solid #e8edf3; background: #fbfcfe; }
.inspector-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 10px; margin-bottom: 20px; }
.inspector-heading h3, .empty-inspector h3 { margin: 0; color: #26364d; font-size: 16px; }
.inspector-heading p, .empty-inspector p { margin: 5px 0 0; color: #8290a3; font-size: 12px; line-height: 1.6; }
label { display: block; margin: 16px 0 7px; color: #45556c; font-size: 12px; font-weight: 600; }
.expression-inspector .el-select, .expression-inspector .el-input { width: 100%; }
.inline-heading { display: flex; align-items: center; justify-content: space-between; margin-top: 14px; }
.inline-heading label { margin: 0; }
.argument-row { display: flex; min-height: 35px; align-items: center; justify-content: space-between; margin-top: 6px; padding: 0 9px; border: 1px solid #e4e9f0; border-radius: 5px; background: #fff; color: #617087; font-size: 12px; }
.field-tip { margin: 9px 0 0; color: #8794a7; font-size: 12px; line-height: 1.65; }
.danger-button { color: #e35d6a; }
.empty-inspector { padding: 80px 12px; color: #8794a7; text-align: center; }
.empty-inspector i { margin-bottom: 12px; font-size: 26px; }
</style>
