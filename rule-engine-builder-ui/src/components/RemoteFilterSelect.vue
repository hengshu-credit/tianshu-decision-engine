<template>
  <el-autocomplete
    v-if="allowFreeInput"
    v-bind="$attrs"
    ref="autocomplete"
    class="remote-filter-input"
    popper-class="remote-filter-suggestions"
    :model-value="value"
    :fetch-suggestions="fetchSuggestions"
    :debounce="200"
    :highlight-first-item="false"
    :teleported="true"
    :placeholder="placeholder"
    clearable
    fit-input-width
    @update:model-value="updateValue"
    @change="$emit('change', $event)"
    @select="handleSelect"
    @blur="closeSuggestions"
    @clear="closeSuggestions"
    @click="openSuggestions"
    @keydown.enter.capture="handleEnterStart"
    @keyup.enter="handleEnter"
  >
    <template #default="{ item }">{{ item.label }}</template>
  </el-autocomplete>
  <el-select
    v-else
    v-bind="$attrs"
    ref="select"
    :model-value="value"
    clearable
    filterable
    remote
    :teleported="true"
    :placeholder="placeholder"
    :loading="loading"
    :remote-method="handleRemote"
    @update:model-value="updateValue"
    @change="$emit('change', $event)"
    @visible-change="handleVisibleChange"
    @popup-scroll="handleDropdownScroll"
  >
    <el-option
      v-for="option in options"
      :key="optionValue(option)"
      :label="optionLabel(option)"
      :value="optionValue(option)"
    />
  </el-select>
</template>

<script>
import { $emit } from '../utils/gogocodeTransfer'

// 文本筛选使用受控输入；候选查询只更新建议，不改写筛选条件。
// 页面通过 fetchOptions、optionLabelKey、optionValueKey 配置数据源和字段。
export default {
  name: 'RemoteFilterSelect',
  inheritAttrs: false,
  props: {
    value: { type: [String, Number], default: '' },
    fetchOptions: { type: Function, required: true },
    placeholder: { type: String, default: '输入筛选' },
    optionLabelKey: { type: String, default: 'label' },
    optionValueKey: { type: String, default: 'value' },
    optionFields: { type: Array, default: () => [] },
    pageSize: { type: Number, default: 20 },
    allowFreeInput: { type: Boolean, default: false },
  },
  data() {
    return {
      options: [],
      loading: false,
      query: '',
      pageNum: 1,
      hasMore: true,
      loadedCount: 0,
      optionsRequestId: 0,
      suggestionsCallback: null,
      dropdownWrap: null,
      lastInputValue: this.value,
      composingEnter: false,
    }
  },
  watch: {
    value(value) {
      // 外部重置取消旧请求；普通输入（包括删空）不重建组件或丢失焦点。
      if (value !== this.lastInputValue) this.closeSuggestions()
      this.lastInputValue = value
    },
  },
  beforeUnmount() {
    this.closeSuggestions()
  },
  methods: {
    updateValue(value) {
      this.lastInputValue = value
      // 输入一变化就作废旧请求，不等候选查询的防抖结束。
      this.cancelPendingLoad()
      this.suggestionsCallback?.([])
      $emit(this, 'update:value', value)
    },
    handleSelect() {
      this.$emit('change', this.lastInputValue)
      this.closeSuggestions()
    },
    openSuggestions() {
      const autocomplete = this.$refs.autocomplete
      if (autocomplete && !this.suggestionsCallback && !this.loading) {
        autocomplete.getData(String(this.value ?? ''))
      }
    },
    handleEnterStart(event) {
      this.composingEnter = event.isComposing || event.keyCode === 229
    },
    handleEnter(event) {
      if (this.composingEnter || event.isComposing || event.keyCode === 229) {
        event.stopPropagation()
        this.composingEnter = false
        return
      }
      this.closeSuggestions()
      // 值已经由 update:model-value 同步，keyup 冒泡到页面只执行一次查询。
    },
    fetchSuggestions(query, callback) {
      // 自动补全组件可能还有重置前排队的防抖任务。
      if (String(query ?? '') !== String(this.value ?? '')) {
        callback([])
        return
      }
      this.query = query || ''
      this.suggestionsCallback = callback
      this.loadOptions(true)
    },
    handleRemote(query) {
      this.query = query || ''
      this.loadOptions(true)
    },
    handleVisibleChange(visible) {
      if (visible) {
        this.query = ''
        this.loadOptions(true)
      } else {
        this.cancelPendingLoad()
      }
    },
    closeSuggestions() {
      this.cancelPendingLoad()
      this.suggestionsCallback?.([])
      this.suggestionsCallback = null
      this.$refs.autocomplete?.close()
      this.unbindDropdownScroll()
    },
    cancelPendingLoad() {
      this.optionsRequestId += 1
      this.loading = false
    },
    async loadOptions(reset) {
      if (!reset && (this.loading || !this.hasMore)) return
      const requestId = ++this.optionsRequestId
      if (reset) {
        this.pageNum = 1
        this.loadedCount = 0
        this.hasMore = true
        this.options = []
      }
      this.loading = true
      try {
        const result = await this.fetchOptions({
          query: this.query,
          pageNum: this.pageNum,
          pageSize: this.pageSize,
        })
        if (requestId !== this.optionsRequestId) return
        const page = this.normalizeResult(result)
        this.appendOptions(this.optionFields.length
          ? page.records.flatMap(record => this.optionFields.map(field => record[field]))
            .filter(value => value != null && String(value).toLowerCase().includes(this.query.toLowerCase()))
          : page.records)
        this.loadedCount += page.records.length
        this.hasMore = page.records.length >= this.pageSize &&
          (page.total <= 0 || this.loadedCount < page.total)
        this.pageNum += 1
        this.suggestionsCallback?.(this.options.map(option => ({
          value: this.optionValue(option),
          label: this.optionLabel(option),
        })))
        this.$nextTick(this.bindDropdownScroll)
      } catch (error) {
        if (requestId !== this.optionsRequestId) return
        this.hasMore = false
        this.suggestionsCallback?.([])
        // 请求层负责错误提示；候选加载失败不阻止用户使用已输入的条件。
        this.$emit('load-error', error)
      } finally {
        if (requestId === this.optionsRequestId) this.loading = false
      }
    },
    normalizeResult(result) {
      const data = result && result.data ? result.data : result
      if (Array.isArray(data)) return { records: data, total: data.length }
      if (data && Array.isArray(data.records)) {
        return { records: data.records, total: Number(data.total || 0) }
      }
      return { records: [], total: 0 }
    },
    appendOptions(records) {
      const seen = new Set(this.options.map(item => String(this.optionValue(item))))
      records.forEach(item => {
        const value = this.optionValue(item)
        if (value === undefined || value === null || value === '') return
        if (!seen.has(String(value))) {
          this.options.push(item)
          seen.add(String(value))
        }
      })
    },
    optionLabel(option) {
      if (option == null) return ''
      return typeof option === 'object' ? option[this.optionLabelKey] : option
    },
    optionValue(option) {
      if (option == null) return ''
      return typeof option === 'object' ? option[this.optionValueKey] : option
    },
    bindDropdownScroll() {
      this.unbindDropdownScroll()
      const dropdown = this.$refs.autocomplete?.popperRef?.popperRef?.contentRef
      const wrap = dropdown?.querySelector('.el-scrollbar__wrap')
      if (!wrap) return
      this.dropdownWrap = wrap
      wrap.addEventListener('scroll', this.handleDropdownScroll, { passive: true })
    },
    unbindDropdownScroll() {
      if (!this.dropdownWrap) return
      this.dropdownWrap.removeEventListener('scroll', this.handleDropdownScroll)
      this.dropdownWrap = null
    },
    handleDropdownScroll(event) {
      const el = event.target || this.$refs.select?.scrollbarRef?.wrapRef
      if (el && el.scrollTop + el.clientHeight >= el.scrollHeight - 32) {
        this.loadOptions(false)
      }
    },
  },
  emits: ['input', 'update:value', 'change', 'load-error'],
}
</script>

<style scoped>
.remote-filter-input {
  width: 100%;
}

:global(.remote-filter-suggestions[aria-hidden='true']) {
  pointer-events: none;
}
</style>
