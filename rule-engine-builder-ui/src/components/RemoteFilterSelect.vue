<template>
  <el-select
    ref="select"
    :value="value"
    clearable
    filterable
    remote
    reserve-keyword
    :allow-create="allowFreeInput"
    :default-first-option="allowFreeInput"
    :placeholder="placeholder"
    :loading="loading"
    v-bind="$attrs"
    @input="$emit('input', $event)"
    @change="$emit('change', $event)"
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
    allowFreeInput: { type: Boolean, default: false }
  },
  data() {
    return {
      options: [],
      loading: false,
      query: '',
      pageNum: 1,
      total: 0,
      hasMore: true,
      dropdownWrap: null
    }
  },
  beforeDestroy() {
    this.unbindDropdownScroll()
  },
  methods: {
    handleRemote(query) {
      this.query = query || ''
      if (this.allowFreeInput) {
        this.$emit('input', this.query)
      }
      this.loadOptions(true)
    },
    handleVisibleChange(visible) {
      if (visible) {
        this.query = this.allowFreeInput ? (this.value || '') : this.query
        this.loadOptions(true)
        this.$nextTick(this.bindDropdownScroll)
      } else {
        this.unbindDropdownScroll()
      }
    },
    async loadOptions(reset) {
      if (this.loading) return
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
          pageSize: this.pageSize
        })
        const page = this.normalizeResult(result)
        this.appendOptions(page.records)
        this.total = page.total
        this.hasMore = page.records.length >= this.pageSize &&
          (this.total <= 0 || this.options.length < this.total)
        this.pageNum += 1
      } finally {
        this.loading = false
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
      const seen = new Set(this.options.map(item => String(this.optionValue(item))))
      ;(records || []).forEach(item => {
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
      const wrap = select && select.$refs && select.$refs.scrollbar && select.$refs.scrollbar.$refs
        ? select.$refs.scrollbar.$refs.wrap
        : null
      if (!wrap) return
      this.dropdownWrap = wrap
      wrap.addEventListener('scroll', this.handleDropdownScroll)
    },
    unbindDropdownScroll() {
      if (this.dropdownWrap) {
        this.dropdownWrap.removeEventListener('scroll', this.handleDropdownScroll)
        this.dropdownWrap = null
      }
    },
    handleDropdownScroll(event) {
      const el = event.target
      if (!el || this.loading || !this.hasMore) return
      if (el.scrollTop + el.clientHeight >= el.scrollHeight - 32) {
        this.loadOptions(false)
      }
    }
  }
}
</script>
