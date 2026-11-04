<template>
  <resource-version-manager resource-type="LEDGER_ARTICLE" :resource-id="articleId" :provider="provider" @changed="$emit('changed')">
    <template #preview="{ snapshot, version, draft }">
      <div class="ledger-version-preview"><h3>{{ content(snapshot).title || '未命名草稿' }} <el-tag size="small">{{ draft ? '草稿' : `v${version?.versionNo}` }}</el-tag></h3><ledger-markdown :value="content(snapshot).body || ''" /></div>
    </template>
    <template #comparison="{ original, modified }"><el-tabs><el-tab-pane label="正文差异"><monaco-diff-editor :original="content(original).body || ''" :modified="content(modified).body || ''" language="markdown" height="500px" /></el-tab-pane><el-tab-pane label="配置差异"><json-version-diff :original="metadata(original)" :modified="metadata(modified)" /></el-tab-pane></el-tabs></template>
  </resource-version-manager>
</template>
<script>
import ResourceVersionManager from '@/components/common/ResourceVersionManager.vue'
import MonacoDiffEditor from '@/components/rule/versionDiff/MonacoDiffEditor.vue'
import LedgerMarkdown from './LedgerMarkdown.vue'
import JsonVersionDiff from '@/components/common/JsonVersionDiff.vue'
import { getLedgerVersions, getLedgerVersionEvents, restoreLedgerVersion } from '@/api/ledger'
import { snapshotContent } from '@/utils/ledger'
export default {
  name: 'LedgerVersions',
  components: { ResourceVersionManager, MonacoDiffEditor, LedgerMarkdown, JsonVersionDiff },
  props: { articleId: { type: [Number, String], required: true } },
  emits: ['changed'],
  computed: {
    provider() { return { load: getLedgerVersions, events: getLedgerVersionEvents, className: 'ledger-version-drawer', help: '草稿独立保存；每次发布形成不可变版本，并进入时间线。基于历史版本编辑会生成草稿，当前发布版本保持不变。', openEditor: this.openEditor } },
  },
  methods: {
    content: snapshotContent,
    metadata(snapshot) { const value = { ...snapshotContent(snapshot) }; delete value.body; return value },
    async openEditor({ resourceId, versionId, draftId, articleVersion, isCurrent }) {
      if (!draftId) { await this.$confirm('基于所选版本生成编辑草稿？本人已有的未发布草稿将被替换，已发布版本保持不变。', '基于版本编辑'); if (!isCurrent()) return; await restoreLedgerVersion(resourceId, versionId, articleVersion) }
      if (!isCurrent()) return
      this.$router.push(`/ledger/articles/${resourceId}/edit`)
    },
  },
}
</script>
