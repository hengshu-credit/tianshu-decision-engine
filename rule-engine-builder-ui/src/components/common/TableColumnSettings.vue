<template>
  <el-popover
    v-model:visible="open"
    class="table-column-settings-popover"
    placement="bottom-end"
    :width="312"
    trigger="click"
    :teleported="true"
    @show="beginEdit"
    @hide="discardEdit"
  >
    <div class="table-column-settings__panel">
      <div class="table-column-settings__heading">
        <div>
          <strong>显示字段</strong>
          <span>调整顺序后会保存在当前浏览器</span>
        </div>
        <button
          type="button"
          class="table-column-settings__reset"
          @click="resetDraft"
        >
          恢复默认
        </button>
      </div>

      <ol class="table-column-settings__list">
        <li
          v-for="(column, index) in draftColumns"
          :key="column.key"
          class="table-column-settings__item"
          :class="{ 'is-hidden': !column.visible }"
        >
          <button
            type="button"
            class="table-column-settings__visibility"
            :aria-pressed="column.visible"
            :aria-label="`${column.visible ? '隐藏' : '显示'}${column.label}`"
            :disabled="column.required"
            @click="toggleColumn(column.key)"
          >
            <span class="table-column-settings__check" aria-hidden="true">
              {{ column.visible ? '✓' : '' }}
            </span>
            <span>{{ column.label }}</span>
            <em v-if="column.required">必选</em>
          </button>
          <div class="table-column-settings__moves">
            <button
              type="button"
              aria-label="上移字段"
              :disabled="index === 0"
              @click="moveColumn(index, -1)"
            >
              ↑
            </button>
            <button
              type="button"
              aria-label="下移字段"
              :disabled="index === draftColumns.length - 1"
              @click="moveColumn(index, 1)"
            >
              ↓
            </button>
          </div>
        </li>
      </ol>

      <div class="table-column-settings__footer">
        <span>{{ visibleCount }} / {{ columns.length }} 个字段显示</span>
        <div>
          <button
            type="button"
            class="table-column-settings__button"
            @click="open = false"
          >
            取消
          </button>
          <button
            type="button"
            class="table-column-settings__button is-primary"
            @click="apply"
          >
            应用
          </button>
        </div>
      </div>
    </div>

    <template #reference>
      <el-button
        class="table-column-settings__trigger"
        size="small"
        plain
        :aria-expanded="open"
        aria-label="设置表格显示字段"
        title="设置表格显示字段和顺序"
      >
        <span aria-hidden="true">☷</span>
        字段
      </el-button>
    </template>
  </el-popover>
</template>

<script>
export default {
  name: 'TableColumnSettings',
  props: {
    columns: { type: Array, required: true },
    modelValue: { type: Array, default: () => [] },
    storageKey: { type: String, required: true },
  },
  emits: ['update:modelValue', 'change'],
  data() {
    return {
      open: false,
      draftKeys: [],
      draftVisibleKeys: [],
    }
  },
  computed: {
    availableKeys() {
      return this.columns.map(column => column.key)
    },
    draftColumns() {
      const keys = this.normalizeKeys(this.draftKeys)
      return keys.map(key => {
        const column = this.columns.find(item => item.key === key)
        return {
          ...column,
          visible: this.draftVisibleKeys.includes(key),
        }
      })
    },
    visibleCount() {
      return this.draftColumns.filter(column => column.visible).length
    },
  },
  mounted() {
    const saved = this.readSavedKeys()
    if (saved.length > 0) {
      const normalized = this.normalizeVisibleKeys(saved)
      this.$emit('update:modelValue', normalized)
      this.$emit('change', normalized)
    }
  },
  methods: {
    normalizeKeys(keys) {
      const source = Array.isArray(keys) ? keys : []
      const known = source.filter(key => this.availableKeys.includes(key))
      const missing = this.availableKeys.filter(key => !known.includes(key))
      return [...known, ...missing]
    },
    visibleKeys(keys) {
      const normalized = this.normalizeKeys(keys)
      return normalized.filter(key => {
        const column = this.columns.find(item => item.key === key)
        return column && (column.required || this.draftVisibleKeys.includes(key))
      })
    },
    readSavedKeys() {
      if (typeof window === 'undefined' || !window.localStorage) return []
      try {
        const value = JSON.parse(window.localStorage.getItem(this.storageKey) || 'null')
        return Array.isArray(value) ? value : []
      } catch (error) {
        return []
      }
    },
    beginEdit() {
      const visibleKeys = this.modelValue.length > 0
        ? this.normalizeVisibleKeys(this.modelValue)
        : this.availableKeys
      this.draftKeys = this.normalizeKeys(visibleKeys)
      this.draftVisibleKeys = visibleKeys.filter(key => this.availableKeys.includes(key))
    },
    discardEdit() {
      const visibleKeys = this.modelValue.length > 0
        ? this.normalizeVisibleKeys(this.modelValue)
        : this.availableKeys
      this.draftKeys = this.normalizeKeys(visibleKeys)
      this.draftVisibleKeys = visibleKeys.filter(key => this.availableKeys.includes(key))
    },
    toggleColumn(key) {
      const column = this.columns.find(item => item.key === key)
      if (!column || column.required) return
      this.draftVisibleKeys = this.draftVisibleKeys.includes(key)
        ? this.draftVisibleKeys.filter(item => item !== key)
        : [...this.draftVisibleKeys, key]
    },
    moveColumn(index, offset) {
      const nextIndex = index + offset
      if (nextIndex < 0 || nextIndex >= this.draftKeys.length) return
      const keys = this.draftKeys.slice()
      const [key] = keys.splice(index, 1)
      keys.splice(nextIndex, 0, key)
      this.draftKeys = keys
    },
    resetDraft() {
      this.draftKeys = this.availableKeys.slice()
      this.draftVisibleKeys = this.availableKeys.slice()
    },
    normalizeVisibleKeys(keys) {
      const known = (Array.isArray(keys) ? keys : [])
        .filter(key => this.availableKeys.includes(key))
      const required = this.columns
        .filter(column => column.required)
        .map(column => column.key)
      return [...required.filter(key => !known.includes(key)), ...known]
    },
    apply() {
      const keys = this.visibleKeys(this.draftKeys)
      this.$emit('update:modelValue', keys)
      this.$emit('change', keys)
      this.persist(keys)
      this.open = false
    },
    persist(keys) {
      if (typeof window === 'undefined' || !window.localStorage) return
      try {
        window.localStorage.setItem(this.storageKey, JSON.stringify(keys))
      } catch (error) {
        // 存储不可用时仍保留当前页面的字段设置。
      }
    },
  },
}
</script>

<style lang="scss" scoped>
.table-column-settings__trigger {
  gap: 5px;
}

.table-column-settings__panel {
  color: var(--tianshu-text-primary);
}

.table-column-settings__heading,
.table-column-settings__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.table-column-settings__heading {
  padding-bottom: 10px;
  border-bottom: 1px solid var(--tianshu-border-subtle);

  strong,
  span {
    display: block;
  }

  strong {
    font-size: 14px;
  }

  span,
  .table-column-settings__reset {
    margin-top: 3px;
    color: var(--tianshu-text-tertiary);
    font-size: 12px;
  }
}

.table-column-settings__reset,
.table-column-settings__visibility,
.table-column-settings__moves button,
.table-column-settings__button {
  appearance: none;
  border: 0;
  font: inherit;
  cursor: pointer;
}

.table-column-settings__reset {
  padding: 2px 0;
  background: transparent;

  &:hover,
  &:focus-visible {
    color: var(--el-color-primary);
  }
}

.table-column-settings__list {
  max-height: 300px;
  margin: 8px 0;
  padding: 0;
  overflow: auto;
  list-style: none;
}

.table-column-settings__item {
  display: flex;
  align-items: center;
  min-height: 34px;
  border-radius: 5px;

  &:hover {
    background: var(--tianshu-bg-hover);
  }

  &.is-hidden {
    .table-column-settings__visibility {
      color: var(--tianshu-text-tertiary);
    }

    .table-column-settings__check {
      color: transparent;
      border-color: var(--tianshu-border);
      background: transparent;
    }
  }
}

.table-column-settings__visibility {
  display: inline-flex;
  min-width: 0;
  padding: 4px 6px;
  flex: 1;
  align-items: center;
  gap: 8px;
  color: var(--tianshu-text-primary);
  text-align: left;
  background: transparent;

  &:disabled {
    cursor: default;
  }

  em {
    margin-left: auto;
    color: var(--tianshu-text-tertiary);
    font-size: 11px;
    font-style: normal;
  }
}

.table-column-settings__check {
  display: inline-flex;
  width: 16px;
  height: 16px;
  align-items: center;
  justify-content: center;
  color: var(--tianshu-brand-foreground);
  font-size: 11px;
  background: var(--el-color-primary);
  border: 1px solid var(--el-color-primary);
  border-radius: 3px;
}

.table-column-settings__moves {
  display: inline-flex;
  padding-right: 5px;
  gap: 2px;

  button {
    width: 24px;
    height: 24px;
    color: var(--tianshu-text-tertiary);
    background: transparent;
    border-radius: 4px;

    &:hover:not(:disabled),
    &:focus-visible:not(:disabled) {
      color: var(--el-color-primary);
      background: var(--tianshu-bg-active);
    }

    &:disabled {
      color: var(--tianshu-text-disabled);
      cursor: not-allowed;
    }
  }
}

.table-column-settings__footer {
  padding-top: 8px;
  border-top: 1px solid var(--tianshu-border-subtle);

  > span {
    color: var(--tianshu-text-tertiary);
    font-size: 12px;
  }

  > div {
    display: flex;
    gap: 6px;
  }
}

.table-column-settings__button {
  min-width: 52px;
  padding: 5px 10px;
  color: var(--tianshu-text-secondary);
  background: var(--tianshu-bg-soft);
  border: 1px solid var(--tianshu-border);
  border-radius: 4px;

  &.is-primary {
    color: var(--tianshu-brand-foreground);
    background: var(--el-color-primary);
    border-color: var(--el-color-primary);
  }
}
</style>
