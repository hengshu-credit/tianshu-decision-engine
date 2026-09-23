<template>
  <div class="json-version-diff">
    <div v-if="showHeader" class="json-version-diff__head">
      <strong>{{ originalLabel }}</strong>
      <el-tag size="small" :type="changed ? 'warning' : 'success'">
        {{ changed ? '内容有差异' : '内容一致' }}
      </el-tag>
      <strong>{{ modifiedLabel }}</strong>
    </div>
    <monaco-diff-editor
      :original="originalText"
      :modified="modifiedText"
      language="json"
      :height="height"
    />
  </div>
</template>

<script>
import MonacoDiffEditor from '@/components/rule/versionDiff/MonacoDiffEditor.vue'

function prettyJson(value) {
  if (value === null || value === undefined || value === '') return '{}'
  if (typeof value !== 'string') return JSON.stringify(value, null, 2)
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch (error) {
    return value
  }
}

export default {
  name: 'JsonVersionDiff',
  components: { MonacoDiffEditor },
  props: {
    original: { type: [String, Object, Array], default: '' },
    modified: { type: [String, Object, Array], default: '' },
    originalLabel: { type: String, default: '左侧版本' },
    modifiedLabel: { type: String, default: '右侧版本' },
    showHeader: { type: Boolean, default: true },
    height: { type: String, default: '420px' },
  },
  computed: {
    originalText() {
      return prettyJson(this.original)
    },
    modifiedText() {
      return prettyJson(this.modified)
    },
    changed() {
      return this.originalText !== this.modifiedText
    },
  },
}
</script>

<style scoped>
.json-version-diff__head {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
  color: #334155;
  font-size: 13px;
}
.json-version-diff__head strong:last-child {
  text-align: right;
}
</style>
