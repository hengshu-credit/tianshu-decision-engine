<template>
  <div
    class="rule-field-hierarchy"
    :class="{ 'rule-field-hierarchy--object': field.isDataObject }"
  >
    <template v-if="field.isDataObject">
      <div class="rule-field-hierarchy__object-line">
        <span class="rule-field-hierarchy__object-tag">数据对象</span>
        <strong
          class="rule-field-hierarchy__object-name"
          :title="field.objectLabel"
          >{{ field.objectLabel }}</strong
        >
        <code
          class="rule-field-hierarchy__object-code"
          :title="field.objectCode"
          >{{ field.objectCode }}</code
        >
      </div>
      <div class="rule-field-hierarchy__field-line">
        <span class="rule-field-hierarchy__branch" aria-hidden="true">└</span>
        <span
          class="rule-field-hierarchy__field-name"
          :title="field.fieldLabel"
          >{{ field.fieldLabel }}</span
        >
        <code
          class="rule-field-hierarchy__field-path"
          :title="field.fieldPath"
          >{{ field.fieldPath }}</code
        >
      </div>
    </template>
    <div v-else class="rule-field-hierarchy__single-line">
      <span
        class="rule-field-hierarchy__field-name"
        :title="field.fieldLabel"
        >{{ field.fieldLabel }}</span
      >
      <code
        class="rule-field-hierarchy__field-path"
        :title="field.fieldPath"
        >{{ field.fieldPath }}</code
      >
    </div>
  </div>
</template>

<script>
export default {
  name: 'RuleFieldHierarchyDisplay',
  props: {
    field: {
      type: Object,
      required: true,
    },
  },
}
</script>

<style scoped>
.rule-field-hierarchy {
  display: grid;
  min-width: 0;
  gap: 4px;
  padding: 2px 0;
  color: var(--tianshu-text-primary);
}

.rule-field-hierarchy__object-line,
.rule-field-hierarchy__field-line,
.rule-field-hierarchy__single-line {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 8px;
  line-height: 20px;
  white-space: nowrap;
}

.rule-field-hierarchy__object-tag {
  display: inline-flex;
  height: 20px;
  flex: none;
  align-items: center;
  padding: 0 6px;
  border: 1px solid var(--el-color-primary-light-5);
  border-radius: 4px;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  font-size: 11px;
  font-weight: 600;
}

.rule-field-hierarchy__object-name,
.rule-field-hierarchy__field-name {
  overflow: hidden;
  text-overflow: ellipsis;
}

.rule-field-hierarchy__object-name {
  flex: none;
  font-weight: 600;
}

.rule-field-hierarchy__object-code,
.rule-field-hierarchy__field-path {
  min-width: 0;
  overflow: hidden;
  color: var(--tianshu-text-secondary);
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 12px;
  text-overflow: ellipsis;
}

.rule-field-hierarchy__field-line {
  padding-left: 20px;
}

.rule-field-hierarchy__branch {
  width: 12px;
  flex: none;
  color: var(--tianshu-border-strong);
  font-family: 'Consolas', 'Monaco', monospace;
  text-align: center;
}

.rule-field-hierarchy__field-name {
  flex: none;
  font-weight: 500;
}

.rule-field-hierarchy__field-path {
  flex: 1;
}
</style>
