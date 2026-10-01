<template>
  <span
    class="field-reference-display"
    :class="{ 'field-reference-display--compact': compact }"
  >
    <span
      class="field-reference-display__type"
      :class="'field-reference-display__type--' + typeChar"
      :title="typeLabel"
      >{{ typeChar }}</span
    >
    <span class="field-reference-display__code" :title="fieldCode">{{
      fieldCode
    }}</span>
    <span class="field-reference-display__name" :title="fieldName">{{
      fieldName
    }}</span>
  </span>
</template>

<script>
const TYPE_CHARS = {
  STRING: 's',
  NUMBER: 'i',
  INTEGER: 'i',
  INT: 'i',
  LONG: 'i',
  DOUBLE: 'i',
  DECIMAL: 'i',
  PROBABILITY: 'i',
  BOOLEAN: 'b',
  BOOL: 'b',
  DATE: 'd',
  DATETIME: 'd',
  ENUM: 'e',
  OBJECT: 'o',
  LIST: 'l',
  ARRAY: 'l',
  MAP: 'm',
  MODEL: 'M',
}

const TYPE_LABELS = {
  STRING: '字符串',
  NUMBER: '数值',
  INTEGER: '整数',
  INT: '整数',
  LONG: '长整数',
  DOUBLE: '双精度数值',
  DECIMAL: '高精度数值',
  PROBABILITY: '概率',
  BOOLEAN: '布尔',
  BOOL: '布尔',
  DATE: '日期',
  DATETIME: '日期时间',
  ENUM: '枚举',
  OBJECT: '对象',
  LIST: '列表',
  ARRAY: '数组',
  MAP: '映射',
  MODEL: '模型',
}

export default {
  name: 'FieldReferenceDisplay',
  props: {
    field: {
      type: Object,
      required: true,
    },
    compact: {
      type: Boolean,
      default: false,
    },
  },
  computed: {
    fieldType() {
      return String(this.field.fieldType || this.field.varType || 'OBJECT')
        .trim()
        .toUpperCase()
    },
    fieldCode() {
      return (
        this.field.fieldCode ||
        this.field.varCode ||
        this.field.scriptName ||
        '—'
      )
    },
    fieldName() {
      return (
        this.field.fieldName ||
        this.field.varLabelText ||
        this.field.varLabel ||
        this.fieldCode
      )
    },
    typeChar() {
      return TYPE_CHARS[this.fieldType] || '?'
    },
    typeLabel() {
      return TYPE_LABELS[this.fieldType] || this.fieldType
    },
  },
}
</script>

<style scoped>
.field-reference-display {
  display: grid;
  width: 100%;
  min-width: 0;
  grid-template-columns: 20px minmax(68px, 0.8fr) minmax(72px, 1fr);
  gap: 8px;
  align-items: center;
  color: #26364d;
  font-size: 12px;
  line-height: 20px;
}

.field-reference-display--compact {
  grid-template-columns: 20px minmax(56px, 0.8fr) minmax(56px, 1fr);
  gap: 6px;
}

.field-reference-display__type {
  display: inline-flex;
  width: 20px;
  height: 20px;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  background: #64748b;
  color: #fff;
  flex-shrink: 0;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 11px;
  font-weight: 600;
}

.field-reference-display__code,
.field-reference-display__name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.field-reference-display__code {
  color: #6f7f92;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 11px;
}

.field-reference-display__name {
  color: #26364d;
  font-weight: 500;
}

.field-reference-display__type--s {
  background: var(--el-color-primary);
}

.field-reference-display__type--i {
  background: #e6a23c;
}

.field-reference-display__type--b {
  background: #67c23a;
}

.field-reference-display__type--d {
  background: #9c27b0;
}

.field-reference-display__type--e {
  background: #f56c6c;
}

.field-reference-display__type--o {
  background: #00bcd4;
}

.field-reference-display__type--l {
  background: #ff9800;
}

.field-reference-display__type--m,
.field-reference-display__type--\? {
  background: #64748b;
}

.field-reference-display__type--M {
  background: #13c2c2;
}
</style>
