<template>
  <resource-version-manager resource-type="LEDGER_ARTICLE" :resource-id="articleId" :provider="provider" @changed="$emit('changed')">
    <template #preview="{ snapshot, version, draft }">
      <div class="ledger-version-preview"><h3>{{ content(snapshot).title || '未命名草稿' }} <el-tag size="small">{{ draft ? '草稿' : `v${version?.versionNo}` }}</el-tag></h3><ledger-markdown :value="content(snapshot).body || ''" /></div>
    </template>
    <template #comparison="{ original, modified, originalVersion, modifiedVersion }">
      <div class="ledger-version-columns">
        <section v-for="(item, index) in [{ snapshot: original, version: originalVersion }, { snapshot: modified, version: modifiedVersion }]" :key="index" class="ledger-version-preview">
          <p>{{ index === 0 ? '左侧 · 基准版本' : '右侧 · 当前所选版本' }} · {{ item.version ? `v${item.version.versionNo} · 修订 ${item.version.revisionNo}` : '草稿' }}</p>
          <h3>{{ content(item.snapshot).title || '未命名草稿' }}</h3>
          <ledger-markdown :value="content(item.snapshot).body || ''" />
        </section>
      </div>
      <resource-version-preview resource-type="LEDGER_ARTICLE" :original="metadata(original)" :snapshot="metadata(modified)" comparing original-label="左侧文章配置" modified-label="右侧文章配置" />
    </template>
  </resource-version-manager>
</template>
<script>
import ResourceVersionManager from '@/components/common/ResourceVersionManager.vue'
import LedgerMarkdown from './LedgerMarkdown.vue'
import ResourceVersionPreview from '@/components/common/ResourceVersionPreview.vue'
import { getLedgerVersions, getLedgerVersionEvents, restoreLedgerVersion } from '@/api/ledger'
import { snapshotContent } from '@/utils/ledger'
export default {
  name: 'LedgerVersions',
  components: { ResourceVersionManager, LedgerMarkdown, ResourceVersionPreview },
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
<style scoped>
.ledger-version-columns { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; margin-top: 16px; }
.ledger-version-preview { min-width: 0; overflow-wrap: anywhere; }
.ledger-version-columns > section { padding: 16px; border: 1px solid var(--tianshu-border); border-radius: 8px; }
.ledger-version-preview > p { font-size: 13px; color: var(--tianshu-text-secondary); }
@media (max-width: 640px) { .ledger-version-columns { grid-template-columns: minmax(0, 1fr); } }
</style>
