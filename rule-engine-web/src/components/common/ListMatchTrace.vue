<template>
  <section class="list-match-trace" aria-label="名单匹配过程">
    <header class="match-root">
      <div>
        <h3>名单匹配条件树</h3>
        <p>{{ combination.label }}<template v-if="combination.description"> · {{ combination.description }}</template></p>
      </div>
      <span class="match-count">{{ values.length }} 项内容 · {{ listIds.length }} 个名单 · {{ values.length * listIds.length }} 条条件</span>
    </header>
    <div class="match-context">
      <span>内容类型：{{ itemTypes }}</span>
      <span v-if="request.matchTime">匹配时间：{{ request.matchTime }}</span>
      <span>名单名称与编码按当前可见配置展示</span>
    </div>
    <div class="match-tree">
      <div class="match-group-head match-tree-head">
        <span class="match-logic">{{ logic ? logic.fields : '未知' }}</span>
        <strong>{{ logic ? (logic.fields === '且' ? '全部规则组满足' : '任一规则组满足') : '未识别组合关系' }}</strong>
      </div>
      <ol v-if="values.length" class="match-branches">
        <li v-for="(value, valueIndex) in values" :key="valueIndex" class="match-branch">
          <div class="match-group-head">
            <span class="match-logic">{{ logic ? logic.lists : '未知' }}</span>
            <h4>内容 {{ valueIndex + 1 }}</h4>
            <span>{{ logic ? (logic.lists === '且' ? '以下名单条件全部满足' : '以下名单条件任一满足') : '组合方式未记录或不支持' }}</span>
            <span v-if="groupResult(valueIndex) !== null" class="match-group-result" :class="{ 'is-hit': groupResult(valueIndex) }">{{ groupResult(valueIndex) ? '条件组满足' : '条件组不满足' }}</span>
          </div>
          <ol v-if="libraries.length" class="match-conditions">
            <li v-for="(library, listIndex) in libraries" :key="listIndex" class="match-condition" :class="{ 'is-hit': cellResult(valueIndex, listIndex) === true }">
              <span class="match-rule-index">{{ valueIndex + 1 }}.{{ listIndex + 1 }}</span>
              <list-match-content :operand="operands[valueIndex]" :actual-value="value" />
              <span class="match-operator">{{ matchMode }}</span>
              <span class="match-library" :title="`名单 ID：${library.id}`">
                <content-reference v-if="library.listName" :value="{ ...library, refType: 'LIST_LIBRARY', valueType: 'LIST' }" /><strong v-else>{{ namesLoading ? '正在加载名单名称…' : '名单名称不可用' }}</strong>
                
                <small v-if="!library.listCode && !namesLoading">{{ library.unavailable ? '已删除、无权查看或加载失败' : '未记录名单编码' }}</small>
              </span>
              <span class="match-condition-result" :title="cellResult(valueIndex, listIndex) === null ? '本条条件的判断结果未记录' : '本次匹配实际判断结果'">{{ cellLabel(valueIndex, listIndex) }}</span>
            </li>
          </ol>
          <template v-else>
            <list-match-content :operand="operands[valueIndex]" :actual-value="value" />
            <p>未记录名单范围</p>
          </template>
        </li>
      </ol>
      <p v-else>未记录待匹配内容，无法展开条件树。</p>
    </div>
    <footer class="match-final" :class="{ 'is-success': outcome.tone === 'success', 'is-error': outcome.tone === 'danger' }">
      <div class="match-group-head">
        <h4>最终结果</h4>
        <el-tag size="small" :type="outcome.tone === 'success' ? 'primary' : outcome.tone">{{ outcome.label }}</el-tag>
        <span>{{ outcome.description }}</span>
      </div>
      <p v-if="errorMessage || response.error" class="match-error">{{ errorMessage || response.error }}</p>
      <p v-if="!hasCompleteMatrix">日志未记录逐项匹配明细，条件树展示本次组合关系，最终结果以日志为准。</p>
    </footer>
  </section>
</template>

<script>
import ContentReference from './ContentReference.vue'
import { LIST_COMBINATION_MODES, LIST_ITEM_TYPES, LIST_MATCH_MODES } from '@/constants/listMatchModes'
import { listMatchOutcome, listMatchPayload } from '@/utils/listMatchTrace'
import { getLibrary } from '@/api/ruleList'
import ListMatchContent from './ListMatchContent.vue'

const COMBINATION_LOGIC = {
  ANY_FIELD_ANY_LIST: { fields: '或', lists: '或' },
  ALL_FIELDS_ANY_LIST: { fields: '且', lists: '或' },
  ANY_FIELD_ALL_LISTS: { fields: '或', lists: '且' },
  ALL_FIELDS_ALL_LISTS: { fields: '且', lists: '且' },
}

const EXCLUSION_COMBINATIONS = {
  ANY_FIELD_ANY_LIST: '任一内容在任一名单中满足排除条件即可。',
  ALL_FIELDS_ANY_LIST: '每项内容都要在至少一个名单中满足排除条件，可以是不同名单。',
  ANY_FIELD_ALL_LISTS: '至少一项内容要在全部所选名单中满足排除条件。',
  ALL_FIELDS_ALL_LISTS: '每项内容都要在全部所选名单中满足排除条件。',
}

export default {
  name: 'ListMatchTrace',
  components: { ListMatchContent, ContentReference },
  data() {
    return { libraryById: {}, namesLoading: false, nameRequestSeq: 0 }
  },
  props: {
    requestBody: { type: [String, Object], default: '' },
    responseBody: { type: [String, Object], default: '' },
    success: { type: Number, default: null },
    errorMessage: { type: String, default: '' },
  },
  computed: {
    request() { return listMatchPayload(this.requestBody) },
    response() { return listMatchPayload(this.responseBody) },
    outcome() { return listMatchOutcome(this.request, this.response, this.success, this.errorMessage) },
    values() {
      const values = this.request.queryValues ?? this.request.queryValue
      return Array.isArray(values) ? values : values === undefined ? [] : [values]
    },
    operands() {
      return Array.isArray(this.request.queryOperands) ? this.request.queryOperands : []
    },
    listIds() {
      const ids = this.request.listIds ?? this.request.listId
      return Array.isArray(ids) ? ids : ids == null ? [] : [ids]
    },
    libraries() {
      return this.listIds.map(id => this.libraryById[String(id)] || { id })
    },
    logic() { return COMBINATION_LOGIC[this.request.combinationMode] || null },
    matchMatrix() {
      if (this.success === 0 || this.errorMessage || this.response.error) return []
      const matrix = this.response.matchMatrix
      if (!Array.isArray(matrix) || matrix.length !== this.values.length
          || matrix.some(row => !Array.isArray(row) || row.length !== this.listIds.length)) return []
      return matrix
    },
    hasCompleteMatrix() {
      return this.values.length > 0 && this.listIds.length > 0 && this.matchMatrix.length > 0
        && this.matchMatrix.every(row => row.every(cell => typeof cell === 'boolean'))
    },
    itemTypes() {
      const types = this.request.itemTypes
      if (!Array.isArray(types)) return '未记录'
      return types.length ? types.map(type => LIST_ITEM_TYPES.find(item => item.value === type)?.label || type).join('、') : '全部内容类型'
    },
    matchMode() {
      return LIST_MATCH_MODES.find(item => item.value === this.request.matchMode)?.label || this.request.matchMode || '未记录'
    },
    combination() {
      const mode = LIST_COMBINATION_MODES.find(item => item.value === this.request.combinationMode)
      if (!mode) return { label: this.request.combinationMode || '未记录组合方式', description: '' }
      if (['NOT_IN_LIST', 'NOT_CONTAINED_IN_LIST'].includes(this.request.matchMode)) {
        return { label: '按排除条件组合判断', description: EXCLUSION_COMBINATIONS[mode.value] }
      }
      return mode
    },
  },
  watch: {
    listIds: { immediate: true, handler: 'loadLibraryNames' },
  },
  beforeUnmount() { this.nameRequestSeq++ },
  methods: {
    cellResult(valueIndex, listIndex) {
      const result = this.matchMatrix[valueIndex]?.[listIndex]
      return typeof result === 'boolean' ? result : null
    },
    cellLabel(valueIndex, listIndex) {
      const hit = this.cellResult(valueIndex, listIndex)
      return hit === null ? '未记录逐项结果' : listMatchOutcome(this.request, { hit }, 1, '').label
    },
    groupResult(valueIndex) {
      const row = this.matchMatrix[valueIndex]
      if (!this.logic || !row?.length || row.some(cell => typeof cell !== 'boolean')) return null
      return this.logic.lists === '且' ? row.every(Boolean) : row.some(Boolean)
    },
    async loadLibraryNames(ids) {
      const seq = ++this.nameRequestSeq
      this.libraryById = {}
      this.namesLoading = ids.length > 0
      const uniqueIds = [...new Set(ids.map(String))]
      const results = await Promise.allSettled(uniqueIds.map(id => getLibrary(id)))
      if (seq !== this.nameRequestSeq) return
      this.libraryById = Object.fromEntries(uniqueIds.map((id, index) => {
        const result = results[index]
        const library = result.status === 'fulfilled' ? result.value?.data : null
        // 名称只按稳定 ID 关联，不使用数组位置、名称或编码猜测对应关系。
        return [id, library && String(library.id) === id
          ? { id, listName: library.listName, listCode: library.listCode }
          : { id, unavailable: true }]
      }))
      this.namesLoading = false
    },
  },
}
</script>

<style scoped>
.list-match-trace { margin-top: 16px; min-width: 0; color: var(--tianshu-text-primary); }
.match-root { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 12px; padding: 16px; border: 1px solid var(--tianshu-border-subtle); border-radius: 8px; background: var(--tianshu-bg-muted); }
.match-root > div { flex: 1; min-width: 0; }
.match-root h3 { margin: 0; font-size: 15px; }
.match-count { color: var(--tianshu-text-secondary); font-size: 12px; }
.list-match-trace p { margin: 8px 0 0; color: var(--tianshu-text-secondary); line-height: 1.6; font-size: 13px; overflow-wrap: anywhere; }
.match-context { display: flex; flex-wrap: wrap; gap: 8px 24px; margin: 12px 0 16px; color: var(--tianshu-text-secondary); font-size: 12px; overflow-wrap: anywhere; }
.match-group-head { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; font-size: 13px; }
.match-group-head h4 { margin: 0; font-size: 13px; }
.match-group-head > span:not(.match-logic, .el-tag) { color: var(--tianshu-text-secondary); }
.match-logic { display: inline-flex; align-items: center; justify-content: center; min-width: 28px; padding: 4px 8px; border-radius: 4px; background: var(--tianshu-info-bg); color: var(--tianshu-info-text); font-size: 12px; font-weight: 700; }
.match-branches { margin: 8px 0 0 14px; padding: 0 0 0 20px; list-style: none; border-left: 2px solid var(--tianshu-border); }
.match-branch { position: relative; margin-top: 12px; min-width: 0; }
.match-branch::before { position: absolute; top: 14px; left: -22px; width: 16px; border-top: 2px solid var(--tianshu-border); content: ''; }
.match-conditions { display: grid; gap: 8px; margin: 8px 0 0; padding: 0 0 4px; list-style: none; overflow-x: auto; }
.match-condition { display: flex; align-items: center; gap: 12px; width: max-content; min-width: 100%; box-sizing: border-box; padding: 12px; border: 1px solid var(--tianshu-border-subtle); border-radius: 6px; background: var(--tianshu-bg-surface); font-size: 13px; white-space: nowrap; }
.match-condition.is-hit { border-color: var(--el-color-primary); background: color-mix(in srgb, var(--el-color-primary) 8%, var(--tianshu-bg-surface)); box-shadow: inset 3px 0 var(--el-color-primary); }
.match-group-head .match-group-result.is-hit, .match-condition.is-hit .match-condition-result { color: var(--tianshu-primary-text, var(--el-color-primary)); font-weight: 600; }
.match-rule-index { color: var(--tianshu-text-tertiary); font-size: 12px; }
.match-operator { color: var(--tianshu-text-primary); font-weight: 600; }
.match-library { display: inline-flex; align-items: baseline; gap: 8px; }
.match-library strong { font-weight: 600; }
.match-library code, .match-library small { min-width: 0; color: var(--tianshu-text-secondary); font-size: 12px; }
.match-condition-result { margin-left: auto; padding-left: 16px; color: var(--tianshu-text-tertiary); font-size: 12px; }
.match-final { margin-top: 16px; padding: 16px; border: 1px solid var(--tianshu-border); border-radius: 8px; background: var(--tianshu-bg-muted); }
.match-final.is-success { border-color: var(--el-color-primary); background: color-mix(in srgb, var(--el-color-primary) 8%, var(--tianshu-bg-surface)); }
.match-final.is-error { border-color: var(--tianshu-danger-border); }
.list-match-trace .match-error { color: var(--el-color-danger); }
@media (max-width: 600px) {
  .match-root { padding: 12px; }
  .match-branches { margin-left: 8px; padding-left: 12px; }
  .match-branch::before { left: -14px; width: 8px; }
}
</style>
