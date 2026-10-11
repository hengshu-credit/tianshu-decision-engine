<template>
  <div class="condition-collection-input">
    <div v-if="legacy" class="collection-legacy" data-testid="collection-legacy">
      <strong>原有配置保持不变</strong>
      <pre>{{ legacyText }}</pre>
      <span>继续使用原有比较规则；改为逐项填写前不会转换或重写。</span>
      <el-button size="small" data-testid="collection-replace-legacy" @click="replaceTarget = 'ALL'">用逐项配置替换</el-button>
    </div>
    <template v-else>
      <div v-for="(item, index) in items" :key="index" class="collection-member">
        <span class="collection-index">{{ index + 1 }}</span>
        <template v-if="memberType(item) !== 'ORIGINAL'">
          <el-select :model-value="memberType(item)" size="small" :aria-label="`集合第 ${index + 1} 项类型`" @update:model-value="changeType(index, $event)">
            <el-option label="文本" value="STRING" />
            <el-option label="精确数字" value="NUMBER" />
            <el-option label="布尔值" value="BOOLEAN" />
          </el-select>
          <el-select v-if="memberType(item) === 'BOOLEAN'" :model-value="memberValue(item)" size="small" :aria-label="`集合第 ${index + 1} 项内容`" :data-testid="`collection-value-${index}`" @update:model-value="updateValue(index, $event)">
            <el-option label="是（true）" :value="true" />
            <el-option label="否（false）" :value="false" />
          </el-select>
          <el-input v-else :model-value="memberValue(item)" size="small" :placeholder="memberType(item) === 'NUMBER' ? '填写数字，保留完整精度' : '填写文本；留空表示空字符串'" :aria-label="`集合第 ${index + 1} 项内容`" :data-testid="`collection-value-${index}`" @update:model-value="updateValue(index, $event)" />
        </template>
        <div v-else class="collection-original">
          <span>{{ originalLabel(item) }}</span>
          <el-button link size="small" :data-testid="`collection-replace-${index}`" @click="replaceTarget = index">替换此项</el-button>
        </div>
        <el-button link size="small" :aria-label="`删除集合第 ${index + 1} 项`" @click="remove(index)">删除</el-button>
      </div>
      <el-button size="small" data-testid="collection-add-member" @click="add">添加成员</el-button>
      <span v-if="error" class="collection-error" role="alert">{{ error }}</span>
      <p class="collection-help">{{ helpText }}</p>
    </template>
    <div v-if="replaceTarget !== null" class="collection-replace-impact" data-testid="collection-replace-impact">
      <p v-if="replaceTarget === 'ALL'">替换会移除当前旧格式集合，需要逐项重新填写；不会自动拆分或推断原内容。</p>
      <p v-else>替换第 {{ replaceTarget + 1 }} 项会移除原字段或计算引用，或改变已有数字的计算方式。其他成员保持不变，请填写新值并重新测试。</p>
      <div class="collection-replace-actions">
        <el-button size="small" @click="replaceTarget = null">取消</el-button>
        <el-button size="small" data-testid="collection-confirm-replace" @click="confirmReplace">确认替换</el-button>
      </div>
    </div>
  </div>
</template>

<script>
import { cloneOperand } from '@/utils/operand'
import { collectionMemberType, newCollectionMember, requireCollectionItems } from '@/utils/conditionCollection'

export default {
  name: 'ConditionCollectionInput',
  props: {
    value: { type: Object, default: null },
    valueType: { type: String, default: 'STRING' },
    helpText: { type: String, default: '每行是一个成员，文本中的逗号和空格原样保留。空字符串也会保存；空白输入仍按引擎缺失值规则处理，不会因此命中集合。' },
  },
  emits: ['input'],
  data() { return { replaceTarget: null } },
  computed: {
    legacy() { return Boolean(this.value && this.value.kind !== 'ARRAY' && !(this.value.kind === 'LITERAL' && this.value.value === '')) },
    legacyText() { return this.value?.kind === 'LITERAL' ? String(this.value.value ?? '') : this.originalLabel(this.value) },
    items() { return this.value?.kind === 'ARRAY' && Array.isArray(this.value.items) ? this.value.items : [] },
    defaultType() { return ['NUMBER', 'BYTE', 'SHORT', 'INT', 'INTEGER', 'LONG', 'FLOAT', 'DOUBLE', 'DECIMAL', 'BIGDECIMAL', 'PROBABILITY'].includes(this.valueType.toUpperCase()) ? 'NUMBER' : 'STRING' },
    error() {
      try { requireCollectionItems({ items: this.items }); return '' } catch (error) { return error.message }
    },
  },
  watch: { value() { this.replaceTarget = null } },
  methods: {
    memberType: collectionMemberType,
    memberValue(item) {
      const type = this.memberType(item)
      if (type === 'NUMBER') return item.operand.value
      if (type === 'BOOLEAN') return item.value === true || item.value === 'true'
      return item.value == null ? '' : String(item.value)
    },
    originalLabel(item) {
      if (item?.kind === 'LITERAL') return `已有数字（保留原值和计算方式）：${item.value ?? ''}`
      return `原字段或计算引用（保持不变）：${item?.label || item?.code || item?.functionCode || item?.kind || '未完成项'}`
    },
    updateItems(items) {
      this.$emit('input', { ...(this.value?.kind === 'ARRAY' ? cloneOperand(this.value) : {}), kind: 'ARRAY', valueType: 'LIST', items: cloneOperand(items) })
    },
    updateValue(index, value) {
      const items = this.items.slice()
      const item = items[index]
      items[index] = this.memberType(item) === 'NUMBER' ? { ...item, operand: { ...item.operand, value } } : { ...item, value }
      this.updateItems(items)
    },
    changeType(index, type) {
      const items = this.items.slice()
      items[index] = newCollectionMember(type, this.memberValue(items[index]))
      this.updateItems(items)
    },
    add() { this.updateItems([...this.items, newCollectionMember(this.defaultType)]) },
    remove(index) { this.updateItems(this.items.filter((_, i) => i !== index)) },
    confirmReplace() {
      if (this.replaceTarget === 'ALL') this.updateItems([])
      else {
        const items = this.items.slice()
        items[this.replaceTarget] = newCollectionMember(this.defaultType)
        this.updateItems(items)
      }
      this.replaceTarget = null
    },
  },
}
</script>

<style scoped>
.condition-collection-input { min-width: 0; width: 100%; container-type: inline-size; }
.collection-member { display: grid; grid-template-columns: 18px 100px minmax(0, 1fr) auto; align-items: center; gap: 6px; margin-bottom: 6px; }
.collection-index, .collection-help, .collection-legacy > span { color: var(--tianshu-text-secondary); font-size: 12px; }
.collection-original { grid-column: 2 / 4; display: flex; align-items: center; justify-content: space-between; gap: 8px; min-width: 0; overflow-wrap: anywhere; }
.collection-legacy pre { margin: 6px 0; white-space: pre-wrap; overflow-wrap: anywhere; }
.collection-legacy > span { display: block; margin-bottom: 8px; }
.collection-help { margin: 6px 0 0; line-height: 1.5; }
.collection-error { display: block; margin-top: 6px; color: var(--tianshu-danger-text); font-size: 12px; }
.collection-replace-impact { margin-top: 8px; padding: 8px; color: var(--tianshu-warning-text); background: var(--tianshu-warning-bg); border: 1px solid var(--tianshu-warning-border); border-radius: 4px; font-size: 12px; }
.collection-replace-impact p { margin: 0 0 8px; line-height: 1.5; }
.collection-replace-actions { display: flex; gap: 8px; }
@container (max-width: 340px) { .collection-member { grid-template-columns: 18px minmax(0, 1fr) auto; } .collection-member > .el-input, .collection-member > .el-select:nth-of-type(2) { grid-column: 2; } .collection-original { grid-column: 2; } }
</style>
