<template>
  <el-autocomplete
    v-if="allowFreeInput"
    v-bind="$attrs"
    ref="autocomplete"
    class="remote-filter-input"
    popper-class="remote-filter-suggestions"
    :model-value="value"
    :fetch-suggestions="scheduleSuggestions"
    :debounce="0"
    fit-input-width
    :placement="suggestionPlacement"
    :popper-options="suggestionPopperOptions"
    :highlight-first-item="false"
    :teleported="true"
    :placeholder="placeholder"
    :aria-label="$attrs['aria-label']"
    clearable
    @update:model-value="updateValue"
    @change="$emit('change', $event)"
    @select="handleSelect"
    @focus="resumeSuggestions"
    @blur="closeSuggestions"
    @keydown.esc.capture="handleEscape"
    @clear="closeSuggestions"
    @click="openSuggestions"
    @keydown.enter.capture="handleEnterStart"
    @keyup.enter="handleEnter"
  >
    <template #loading><app-loading-icon size="48px" /></template>
    <template #default="{ item }"><span class="remote-filter-suggestion" :title="item.label">{{ item.label }}</span></template>
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
    :aria-label="$attrs['aria-label']"
    popper-class="remote-filter-options"
    fit-input-width
    :loading="loading && pageNum === 1"
    :remote-method="handleRemote"
    @update:model-value="updateValue"
    @change="$emit('change', $event)"
    @popup-scroll="handleDropdownScroll"
  >
    <template #loading><app-loading-icon size="48px" /></template>
    <template #empty>暂无匹配选项</template>
    <template v-if="contentType" #label><content-reference :show-value-type="showValueType" v-if="selectedContent" :value="{ ...selectedContent, refType: contentType }" /></template>
    <el-option v-if="selectedOption && String(optionValue(selectedOption)) === String(value) && !options.some(option => String(optionValue(option)) === String(value))"
      :key="'selected:' + value" :label="optionLabel(selectedOption)" :value="value"><content-reference :show-value-type="showValueType" v-if="contentType" :value="{ ...selectedOption, refType: contentType }" /></el-option>
    <el-option
      v-for="option in options"
      :key="optionValue(option)"
      :label="optionLabel(option)"
      :value="optionValue(option)"
    ><content-reference :show-value-type="showValueType" v-if="contentType" :value="{ ...option, refType: contentType }" /></el-option>
  </el-select>
</template>

<script>
import { autoUpdate } from '@floating-ui/dom'
import AppLoadingIcon from '@/components/common/AppLoadingIcon.vue'
import ContentReference from '@/components/common/ContentReference.vue'
import { $emit } from '../utils/gogocodeTransfer'

const suggestionGap = 8

// 文本筛选使用受控输入；候选查询只更新建议，不改写筛选条件。
// 页面通过 fetchOptions、optionLabelKey、optionValueKey 配置数据源和字段。
export default {
  components: { AppLoadingIcon, ContentReference },
  name: 'RemoteFilterSelect',
  inheritAttrs: false,
  props: {
    value: { type: [String, Number], default: '' },
    fetchOptions: { type: Function, required: true },
    placeholder: { type: String, default: '输入筛选' },
    optionLabelKey: { type: String, default: 'label' },
    optionValueKey: { type: String, default: 'value' },
    optionFields: { type: Array, default: () => [] },
    selectedOption: { type: Object, default: null },
    showValueType: { type: Boolean, default: true },
    contentType: { type: String, default: '' },
    pageSize: { type: Number, default: 20 },
    allowFreeInput: { type: Boolean, default: false },
  },
  computed: { selectedContent() { return this.options.find(option => String(this.optionValue(option)) === String(this.value)) || this.selectedOption } },
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
      suggestionTimer: null,
      suggestionPlacement: 'bottom-start',
      suggestionPopperOptions: {
        modifiers: [
          { name: 'offset', options: { offset: [0, suggestionGap] } },
          {
            name: 'filterSuggestions', enabled: true, phase: 'beforeRead', fn: this.fitSuggestionPanel,
            effect({ state, instance }) {
              // Popper 负责滚动和窗口缩放，另跟踪已打开候选框的输入锚点尺寸与位置变化。
              if (!state.orderedModifiers.some(modifier => modifier.name === 'eventListeners')) return
              return autoUpdate(state.elements.reference, state.elements.popper, instance.update, {
                ancestorResize: false, ancestorScroll: false,
              })
            },
          },
        ],
      },
      dropdownWrap: null,
      lastInputValue: this.value,
      composingEnter: false,
      suggestionsClosed: false,
    }
  },
  watch: {
    value(value) {
      // 外部重置取消旧请求；普通输入（包括删空）不重建组件或丢失焦点。
      if (value !== this.lastInputValue) this.closeSuggestions()
      this.lastInputValue = value
    },
  },
  mounted() {
    // Element Plus 的 visible-change 也受 loading/空候选影响，不能用作用户开关下拉的信号。
    this.$watch(() => this.$refs.select?.expanded, this.handleSelectExpanded)
  },
  beforeUnmount() {
    this.closeSuggestions()
  },
  methods: {
    updateValue(value) {
      this.resumeSuggestions()
      this.lastInputValue = value
      // 输入一变化就作废旧请求，不等候选查询的防抖结束。
      this.cancelPendingLoad()
      $emit(this, 'update:value', value)
    },
    handleSelect() {
      this.$emit('change', this.lastInputValue)
      this.closeSuggestions()
    },
    openSuggestions() {
      this.resumeSuggestions()
      const autocomplete = this.$refs.autocomplete
      if (autocomplete && !this.suggestionsCallback && !this.loading) {
        autocomplete.getData(String(this.value ?? ''))
      }
    },
    handleEscape(event) {
      const autocomplete = this.$refs.autocomplete
      if (autocomplete?.activated && (autocomplete.loading || autocomplete.suggestions?.length)) {
        event.preventDefault()
        event.stopPropagation()
        this.closeSuggestions()
      }
    },
    handleEnterStart(event) {
      this.composingEnter = event.isComposing || event.keyCode === 229
      if (this.composingEnter) return
      // 未主动用方向键选中候选时，回车只查询输入，不执行自动补全的重新加载逻辑。
      if (this.suggestionTimer !== null || this.loading || !(this.$refs.autocomplete?.highlightedIndex >= 0)) {
        event.preventDefault()
        event.stopPropagation()
        this.closeSuggestions()
      }
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
    scheduleSuggestions(query, callback) {
      // Element 立即进入加载态，只对网络查询防抖，避免连续输入时下拉反复开关。
      if (this.suggestionsClosed || String(query ?? '') !== String(this.lastInputValue ?? '')) {
        callback([])
        return
      }
      if (!this.suggestionsCallback && String(query ?? '') === String(this.value ?? '')) {
        this.fetchSuggestions(query, callback)
        return
      }
      if (this.loading && this.query === query) {
        this.suggestionsCallback = callback
        return
      }
      clearTimeout(this.suggestionTimer)
      this.suggestionsCallback = callback
      this.suggestionTimer = setTimeout(() => {
        this.suggestionTimer = null
        this.fetchSuggestions(query, callback)
      }, 200)
    },
    fetchSuggestions(query, callback) {
      // 回车、失焦或重置后，排队的防抖任务不能重新打开候选或再发起查询。
      if (this.suggestionsClosed || String(query ?? '') !== String(this.value ?? '')) {
        callback([])
        return
      }
      this.query = query || ''
      this.suggestionsCallback = callback
      this.loadOptions(true)
    },
    handleRemote(query) {
      const nextQuery = query || ''
      if (this.loading && this.query === nextQuery) return
      this.query = nextQuery
      this.loadOptions(true)
    },
    handleSelectExpanded(expanded) {
      if (expanded) {
        // 打开时 remote-method 可能已开始加载，同一请求不再重置候选。
        if (!this.loading) {
          this.query = ''
          this.loadOptions(true)
        }
      } else if (expanded === false) {
        this.cancelPendingLoad()
      }
    },
    fitSuggestionPanel({ state }) {
      const { reference, popper } = state.elements
      const wrap = popper.querySelector('.el-autocomplete-suggestion__wrap')
      if (!wrap || !popper.offsetWidth || !reference.getClientRects().length) return
      const rect = reference.getBoundingClientRect()
      const panelHeight = parseFloat(getComputedStyle(wrap).maxHeight)
      const toolbar = reference.closest('.uiue-filter-toolbar, .tab-filter-row')
      const buttons = toolbar?.querySelectorAll('button.el-button') || []
      // 只检查候选层所在的水平范围。其他列换行不影响输入框与弹层之间的距离。
      const overlapsAction = Array.from(buttons).some(button => {
        const action = button.getBoundingClientRect()
        return action.width > 0 && action.right > rect.left && action.left < rect.right &&
          action.top >= rect.bottom && action.top < rect.bottom + suggestionGap + panelHeight
      })
      // 翻到上方时为箭头、边框和视口边缘留出空间，其余位置交给 Popper 自动避让。
      const above = Math.floor(rect.top) - suggestionGap - 8
      const openAbove = overlapsAction && above >= 48
      this.suggestionPlacement = openAbove ? 'top-start' : 'bottom-start'
      const room = openAbove ? above + 'px' : '100vh'
      const width = rect.width + 'px'
      if (popper.style.getPropertyValue('--filter-panel-room') === room && popper.style.getPropertyValue('--filter-anchor-width') === width) return
      popper.style.setProperty('--filter-panel-room', room)
      popper.style.setProperty('--filter-anchor-width', width)
      state.rects.popper.height = popper.offsetHeight
      state.rects.popper.width = popper.offsetWidth
    },
    resumeSuggestions() {
      this.suggestionsClosed = false
    },
    closeSuggestions() {
      this.suggestionsClosed = true
      this.cancelPendingLoad()
      this.suggestionsCallback?.([])
      this.suggestionsCallback = null
      this.$refs.autocomplete?.close()
      this.unbindDropdownScroll()
    },
    cancelPendingLoad() {
      clearTimeout(this.suggestionTimer)
      this.suggestionTimer = null
      this.optionsRequestId += 1
      this.loading = false
    },
    async loadOptions(reset) {
      if (!reset && (this.loading || this.suggestionTimer !== null || !this.hasMore)) return
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
  min-width: 160px;
}

:global(.remote-filter-suggestions[aria-hidden='true']) {
  pointer-events: none;
}

/* 加载、空结果与可滚动列表共用同一个视口，换内容时保持弹层定位。 */
:global(.remote-filter-suggestions),
:global(.remote-filter-options) {
  --filter-panel-height: min(176px, 35vh);
}
:global(.remote-filter-suggestions.el-popper) {
  box-sizing: border-box;
  width: var(--filter-anchor-width);
  max-width: calc(100vw - 8px);
}
:global(.remote-filter-suggestions .el-autocomplete-suggestion) {
  min-width: 100%;
  max-width: 100%;
}
:global(.remote-filter-suggestions .el-autocomplete-suggestion__wrap) {
  height: min(var(--filter-panel-height), var(--filter-panel-room, 100vh));
  max-height: var(--filter-panel-height);
}

:global(.remote-filter-options .el-select-dropdown__wrap),
:global(.remote-filter-options .el-select-dropdown__loading),
:global(.remote-filter-options .el-select-dropdown__empty) {
  box-sizing: border-box;
  height: var(--filter-panel-height);
}
:global(.remote-filter-options .el-select-dropdown__loading),
:global(.remote-filter-options .el-select-dropdown__empty) {
  display: grid;
  place-items: center;
  margin: 0;
}
:global(.remote-filter-suggestions .el-autocomplete-suggestion.is-loading .el-autocomplete-suggestion__wrap) {
  display: grid;
  align-content: center;
}

:global(.remote-filter-suggestions .el-autocomplete-suggestion__list li) {
  min-height: 32px;
  height: auto;
  padding: 6px 12px;
  line-height: 20px;
  white-space: normal;
  overflow-wrap: anywhere;
}

.remote-filter-suggestion {
  display: block;
  overflow-wrap: anywhere;
}

@media (max-width: 560px) {
  .remote-filter-input {
    min-width: 0;
  }
}
</style>
