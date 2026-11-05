<template>
  <div class="uiue-list-page ledger-page ledger-reader" :aria-busy="loading">
    <div class="uiue-detail-header"><div class="uiue-detail-header__title"><el-button class="uiue-detail-back" size="small" aria-label="返回台账记录" @click="$router.push('/ledger')"><app-icon name="Back" /></el-button><h2>{{ article.title || '文章详情' }}</h2></div><div class="uiue-detail-header__actions"><ledger-versions v-if="article.articleId" :article-id="article.articleId" /><el-button v-if="canEdit" size="small" type="primary" @click="$router.push(`/ledger/articles/${article.articleId}/edit`)">编辑文章</el-button></div></div>
    <el-alert v-if="error" :title="error" type="error" :closable="false"><el-button link @click="load">重试</el-button></el-alert>
    <template v-else-if="article.id"><header class="ledger-reader-meta"><div><el-tag>v{{ article.versionNo }}</el-tag><el-tag>{{ categoryLabel(article.category) }}</el-tag><span>{{ article.scope === 'GLOBAL' ? '全局' : article.projectName || `项目 #${article.projectId}` }}</span><span>{{ article.actorName }}</span></div><p>发生时间 {{ time(article.occurredAt) }} · 发布时间 {{ time(article.publishTime) }}</p><div><el-button v-for="event in article.events" :key="event.id" link type="primary" @click="openEvent(event)">{{ event.title }}</el-button></div><p v-if="article.changeSummary">变更说明：{{ article.changeSummary }}</p></header><ledger-markdown :value="article.body" /></template>
  </div>
</template>
<script>
import LedgerMarkdown from '@/components/ledger/LedgerMarkdown.vue'
import ledgerTypesMixin from '@/mixins/ledgerTypesMixin'
import LedgerVersions from '@/components/ledger/LedgerVersions.vue'
import { getLedgerRevision, getLedgerVersions } from '@/api/ledger'
import { ledgerTime } from '@/utils/ledger'
import '@/styles/ledger.scss'
export default {
  mixins: [ledgerTypesMixin],
  name: 'LedgerReader', components: { LedgerMarkdown, LedgerVersions },
  data: () => ({ article: {}, loading: false, error: '', generation: 0, canEdit: false }),
  watch: { '$route.fullPath': { immediate: true, handler() { this.load() } } },
  mounted() { window.addEventListener('tianshu:identity-change', this.clearIdentity) },
  beforeUnmount() { this.generation++; window.removeEventListener('tianshu:identity-change', this.clearIdentity) },
  methods: {
    time: ledgerTime,
    clearIdentity() { this.generation++; this.article = {}; this.canEdit = false; this.error = '' },
    async load() { const generation = ++this.generation; this.loading = true; this.error = ''; this.canEdit = false; try { let revisionId = this.$route.query.revisionId; const workspace = await getLedgerVersions(this.$route.params.id); if (generation !== this.generation) return; this.canEdit = workspace.data?.canEdit === true; if (!revisionId) revisionId = workspace.data?.latestVersionId; if (!revisionId) throw new Error('文章尚无已发布版本'); const response = await getLedgerRevision(revisionId); if (generation === this.generation) { if (String(response.data.articleId) !== String(this.$route.params.id)) throw new Error('版本不属于当前文章'); this.article = response.data } } catch (error) { if (generation === this.generation) this.error = error.message || '文章读取失败' } finally { if (generation === this.generation) this.loading = false } },
    openEvent(event) { this.$router.push({ path: '/ledger', query: { tab: 'timeline', eventIds: event.id } }) },
  },
}
</script>
