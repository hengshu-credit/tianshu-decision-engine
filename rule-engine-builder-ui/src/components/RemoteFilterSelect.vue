<template>
  <el-select
    v-bind="$attrs"
    ref="select"
    :model-value="value"
    clearable
    filterable
    remote
    reserve-keyword
    :allow-create="allowFreeInput"
    :default-first-option="allowFreeInput"
    :teleported="true"
    :placeholder="placeholder"
    :loading="loading"
    @update:model-value="updateValue"
    @change="$emit('change', $event)"
    @keyup.enter="handleEnter"
    @visible-change="handleVisibleChange"
    :remote-method="handleRemote"
  >
    <el-option
      v-for="option in options"
      :key="optionKey(option)"
      :label="optionLabel(option)"
      :value="optionValue(option)"
    />
  </el-select>
</template>

<script>
import { $emit } from '../utils/gogocodeTransfer'
export default {
  name: 'RemoteFilterSelect',
  inheritAttrs: false,
  props: {
    value: { type: [String, Number], default: '' },
    fetchOptions: { type: Function, required: true },
    placeholder: { type: String, default: '输入筛选' },
    optionLabelKey: { type: String, default: 'label' },
    optionValueKey: { type: String, default: 'value' },
    pageSize: { type: Number, default: 20 },
    allowFreeInput: { type: Boolean, default: false },
  },
  data() {
    return {
      options: [],
      loading: false,
      query: '',
      pageNum: 1,
      total: 0,
      hasMore: true,
      dropdownWrap: null,
      editableInput: null,
      lastEmittedFreeInput: null,
      inputObserver: null,
      optionsRequestId: 0,
    }
  },
  watch: {
    value(value) {
      if (!this.allowFreeInput) return
      const nextValue = value == null ? '' : String(value)
      this.query = nextValue
      this.lastEmittedFreeInput = nextValue
      this.$nextTick(() => {
        const input = this.nativeInput()
        if (input && input.value !== nextValue) input.value = nextValue
      })
    },
  },
  beforeUnmount() {
    this.unbindEditableInput()
    this.unbindDropdownScroll()
  },
  methods: {
    updateValue(value) {
      if (this.allowFreeInput) {
        this.lastEmittedFreeInput = value == null ? '' : String(value)
      }
      $emit(this, 'update:value', value)
    },
    emitFreeInput(value) {
      const nextValue = value == null ? '' : String(value)
      if (this.lastEmittedFreeInput === nextValue) return
      this.lastEmittedFreeInput = nextValue
      $emit(this, 'update:value', nextValue)
    },
    handleEnter(event) {
      if (!this.allowFreeInput) return
      this.query = event && event.target ? event.target.value : this.query
      this.emitFreeInput(this.query || '')
    },
    handleNativeInput(event) {
      if (!this.allowFreeInput) return
      this.query = event && event.target ? event.target.value : ''
      this.emitFreeInput(this.query)
    },
    handleRemote(query) {
      this.query = query || ''
      if (this.allowFreeInput) {
        this.emitFreeInput(this.query)
      }
      this.loadOptions(true)
    },
    handleVisibleChange(visible) {
      if (visible) {
        this.setDropdownAccessible(true)
        this.query = this.allowFreeInput ? this.value || '' : this.query
        this.loadOptions(true)
        this.$nextTick(this.bindDropdownScroll)
      } else {
        this.cancelPendingLoad()
        this.setDropdownAccessible(false)
        this.retainSelectedOption()
        this.unbindDropdownScroll()
      }
    },
    nativeInput() {
      const select = this.$refs.select
      const reference = select && select.$refs ? select.$refs.reference : null
      return (
        (reference && reference.$refs && reference.$refs.input) ||
        (select && select.$el ? select.$el.querySelector('input') : null)
      )
    },
    dropdownElement() {
      const select = this.$refs.select
      if (!select) return null
      return (
        (select.popperRef && select.popperRef.contentRef) ||
        select.popperElm ||
        (select.$refs && select.$refs.popper ? select.$refs.popper.$el : null) ||
        (select.$el
          ? select.$el.querySelector('.el-select__popper, .el-select-dropdown')
          : null)
      )
    },
    bindEditableInput() {
      this.unbindEditableInput()
      const input = this.nativeInput()
      if (!input) return
      input.removeAttribute('readonly')
      this.editableInput = input
      input.addEventListener('input', this.handleNativeInput)
      this.inputObserver = new MutationObserver(() => {
        if (input.hasAttribute('readonly')) input.removeAttribute('readonly')
      })
      this.inputObserver.observe(input, {
        attributes: true,
        attributeFilter: ['readonly'],
      })
    },
    unbindEditableInput() {
      if (this.editableInput) {
        this.editableInput.removeEventListener('input', this.handleNativeInput)
        this.editableInput = null
      }
      if (this.inputObserver) {
        this.inputObserver.disconnect()
        this.inputObserver = null
      }
    },
    setDropdownAccessible(visible) {
      const dropdown = this.dropdownElement()
      if (!dropdown) return
      if (visible) {
        dropdown.removeAttribute('inert')
        dropdown.removeAttribute('aria-hidden')
        return
      }
      if (dropdown.contains(document.activeElement)) {
        const input = this.nativeInput()
        if (input) input.focus()
      }
      dropdown.setAttribute('inert', '')
      dropdown.setAttribute('aria-hidden', 'true')
    },
    retainSelectedOption() {
      if (
        this.value === undefined ||
        this.value === null ||
        this.value === ''
      ) {
        this.options = []
        return
      }
      const selectedValue = String(this.value)
      this.options = this.options.filter(
        (option) => String(this.optionValue(option)) === selectedValue
      )
    },
    cancelPendingLoad() {
      this.optionsRequestId += 1
      this.loading = false
    },
    async loadOptions(reset) {
      if (this.loading) {
        if (!reset) return
        this.cancelPendingLoad()
      }
      const requestId = ++this.optionsRequestId
      if (reset) {
        this.pageNum = 1
        this.total = 0
        this.hasMore = true
        this.options = []
      }
      if (!this.hasMore) return
      this.loading = true
      try {
        const result = await this.fetchOptions({
          query: this.query,
          pageNum: this.pageNum,
          pageSize: this.pageSize,
        })
        if (requestId !== this.optionsRequestId) return
        const page = this.normalizeResult(result)
        this.appendOptions(page.records)
        this.total = page.total
        this.hasMore =
          page.records.length >= this.pageSize &&
          (this.total <= 0 || this.options.length < this.total)
        this.pageNum += 1
      } finally {
        if (requestId === this.optionsRequestId) this.loading = false
      }
    },
    normalizeResult(result) {
      const data = result && result.data ? result.data : result
      if (Array.isArray(data)) {
        return { records: data, total: data.length }
      }
      if (data && Array.isArray(data.records)) {
        return { records: data.records, total: Number(data.total || 0) }
      }
      return { records: [], total: 0 }
    },
    appendOptions(records) {
      const seen = new Set(
        this.options.map((item) => String(this.optionValue(item)))
      )
      ;(records || []).forEach((item) => {
        const value = this.optionValue(item)
        if (value === undefined || value === null || value === '') return
        const key = String(value)
        if (!seen.has(key)) {
          this.options.push(item)
          seen.add(key)
        }
      })
    },
    optionLabel(option) {
      if (option == null) return ''
      if (typeof option !== 'object') return option
      return option[this.optionLabelKey]
    },
    optionValue(option) {
      if (option == null) return ''
      if (typeof option !== 'object') return option
      return option[this.optionValueKey]
    },
    optionKey(option) {
      return this.optionValue(option)
    },
    bindDropdownScroll() {
      this.unbindDropdownScroll()
      const select = this.$refs.select
      const dropdown = this.dropdownElement()
      const wrap =
        (dropdown && dropdown.querySelector('.el-scrollbar__wrap')) ||
        (select &&
        select.$refs &&
        select.$refs.scrollbar &&
        select.$refs.scrollbar.$refs
          ? select.$refs.scrollbar.$refs.wrap
          : null)
      if (!wrap) return
      this.dropdownWrap = wrap
      wrap.addEventListener('scroll', this.handleDropdownScroll)
    },
    unbindDropdownScroll() {
      if (this.dropdownWrap) {
        this.dropdownWrap.removeEventListener(
          'scroll',
          this.handleDropdownScroll
        )
        this.dropdownWrap = null
      }
    },
    handleDropdownScroll(event) {
      const el = event.target
      if (!el || this.loading || !this.hasMore) return
      if (el.scrollTop + el.clientHeight >= el.scrollHeight - 32) {
        this.loadOptions(false)
      }
    },
  },
  mounted() {
    this.$nextTick(() => {
      this.bindEditableInput()
      this.setDropdownAccessible(false)
    })
  },
  emits: ['input', 'update:value', 'change'],
}
</script>
