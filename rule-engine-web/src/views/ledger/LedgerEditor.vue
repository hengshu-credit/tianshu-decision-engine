<template>
  <div class="uiue-list-page ledger-page ledger-editor-page" :aria-busy="loading">
    <div class="uiue-detail-header"><div class="uiue-detail-header__title"><el-button class="uiue-detail-back" size="small" aria-label="返回文章管理" @click="back"><app-icon name="Back" /></el-button><h2>{{ articleId ? '编辑文章' : '新建文章' }}</h2><el-tag v-if="publishedVersion" size="small">当前发布 v{{ publishedVersion }}</el-tag></div><div class="uiue-detail-header__actions"><ledger-versions v-if="articleId" :article-id="articleId" /><el-button size="small" :disabled="loading || busy" @click="saveDraft">保存草稿</el-button><el-button type="primary" size="small" :disabled="loading" :loading="busy" @click="configure">保存并发布</el-button></div></div>
    <el-alert v-if="loadError" :title="loadError" type="error" :closable="false"><el-button link @click="load">重试</el-button></el-alert>
    <template v-else>
      <el-input v-model="content.title" class="ledger-title-input" maxlength="200" placeholder="输入文章标题" aria-label="文章标题" :disabled="busy || loading" @input="dirty = true" />
      <div class="ledger-editor-toolbar"><el-button size="small" :disabled="busy" @click="assetPicker = true">素材库 / 插入图片</el-button><el-select v-model="snippet" size="small" placeholder="插入组件" style="width:145px" :disabled="busy" @change="insertSnippet"><el-option label="表格" value="table" /><el-option label="任务清单" value="checklist" /><el-option label="提示说明" value="quote" /><el-option label="验收记录" value="acceptance" /></el-select><span>支持直接粘贴截图；检测通过后自动插入正文</span></div>
      <ledger-markdown-editor ref="markdown" :model-value="content.body" :inert="busy || loading" @update:model-value="updateBody" @dirty="dirty = true" @asset-uploaded="assetUploaded" />
      <p class="ledger-help">{{ dirty ? '有尚未保存的修改' : '草稿已保存或内容未修改' }}。发布版本与草稿独立保存，历史版本保持不变。</p>
    </template>
    <el-dialog v-model="publishDialog" title="文章发布配置" width="620px" class="ledger-dialog" :close-on-click-modal="false" :close-on-press-escape="!busy" :show-close="!busy">
      <el-form label-width="95px" size="small" :disabled="busy">
        <el-form-item label="内容类型" required><el-select v-model="content.category"><el-option v-for="item in categories" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
        <scope-project-fields v-model:scope="content.scope" v-model:project-id="content.projectId" scope-label="内容归属" @scope-change="dirty = true" @project-change="dirty = true" />
        <el-form-item label="发生时间" required><el-date-picker v-model="content.occurredAt" type="datetime" format="YYYY-MM-DD HH:mm:ss" value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%" /></el-form-item>
        <el-form-item label="关联事件"><el-select v-model="content.eventIds" multiple filterable clearable remote :remote-method="searchEvents" placeholder="可选，可关联多个事件" style="width:100%"><el-option v-for="event in events" :key="event.id" :label="event.title" :value="event.id" /></el-select><el-button link type="primary" @click="newEvent">新建事件</el-button></el-form-item>
        <el-form-item label="摘要"><el-input v-model="content.summary" type="textarea" :rows="2" maxlength="400" placeholder="未填写时从正文自动提取" @input="content.summaryAuto = !content.summary.trim()" /></el-form-item>
        <el-form-item label="变更说明"><el-input v-model="content.changeSummary" type="textarea" :rows="2" maxlength="400" placeholder="说明本次发布或修改内容" /></el-form-item>
      </el-form>
      <p class="ledger-help">本人尚未共享的已检测素材，将按本次文章归属发布。待检、隔离或跨项目素材会阻止发布。</p>
      <template #footer><el-button :disabled="busy" @click="publishDialog = false">取消</el-button><el-button type="primary" :loading="busy" @click="publish">{{ publishedVersion ? `发布为 v${Number(publishedVersion) + 1}` : '发布文章' }}</el-button></template>
    </el-dialog>
    <el-dialog v-model="assetPicker" title="素材库" width="min(1150px, 96vw)" class="ledger-dialog" destroy-on-close><ledger-assets picker @select="insertAsset" /></el-dialog>
    <ledger-event-dialog v-model="eventDialog" :scope="content.scope" :project-id="content.projectId" @saved="eventSaved" />
  </div>
</template>
<script>
import ScopeProjectFields from '@/components/common/ScopeProjectFields.vue'
import LedgerMarkdownEditor from '@/components/ledger/LedgerMarkdownEditor.vue'
import LedgerAssets from '@/components/ledger/LedgerAssets.vue'
import LedgerVersions from '@/components/ledger/LedgerVersions.vue'
import LedgerEventDialog from '@/components/ledger/LedgerEventDialog.vue'
import { getLedgerArticleDraft, saveLedgerDraft, publishLedgerArticle, listLedgerEvents, getLedgerEvent } from '@/api/ledger'
import { newLedgerContent, LEDGER_CATEGORIES } from '@/utils/ledger'
import { routeProjectId } from '@/utils/projectContext'
import { getCurrentUser } from '@/security/permissionState'
import '@/styles/ledger.scss'
const snippets = { table: '\n| 检查项 | 调整前 | 调整后 |\n| --- | --- | --- |\n| 指标 | 待填写 | 待填写 |\n', checklist: '\n- [ ] 核查实际效果\n- [ ] 补充验收结论\n', quote: '\n> 请填写需要团队关注的事项。\n', acceptance: '\n## 验收记录\n\n### 验收标准\n\n待填写\n\n### 实际结果\n\n待填写\n\n### 后续安排\n\n- [ ] 待跟进事项\n' }
export default {
  name: 'LedgerEditor', components: { ScopeProjectFields, LedgerMarkdownEditor, LedgerAssets, LedgerVersions, LedgerEventDialog },
  data() { return { articleId: this.$route.params.id || null, version: null, publishedVersion: 0, content: newLedgerContent(routeProjectId(this.$route, this.$store.state.currentProject)), loading: false, loadError: '', busy: false, dirty: false, navigating: false, generation: 0, eventGeneration: 0, events: [], publishDialog: false, assetPicker: false, eventDialog: false, snippet: '', categories: LEDGER_CATEGORIES } },
  mounted() { this.load(); window.addEventListener('tianshu:identity-change', this.clearIdentity) },
  watch: { content: { deep: true, handler() { if (!this.loading && getCurrentUser()) this.dirty = true } } },
  beforeUnmount() { this.generation++; this.eventGeneration++; window.removeEventListener('tianshu:identity-change', this.clearIdentity) },
  async beforeRouteLeave() { if (this.navigating || !getCurrentUser()) return true; if (this.busy) return false; if (!this.dirty) return true; try { await this.$confirm('有未保存的修改，离开后将丢失。可以先保存草稿。', '离开编辑页面'); return true } catch { return false } },
  methods: {
    clearIdentity() { this.dirty = false; this.publishDialog = false; this.assetPicker = false; this.eventDialog = false; this.content = newLedgerContent(null); this.$refs.markdown?.editor?.setValue('') },
    async load() {
      const generation = ++this.generation; this.loading = true; this.loadError = ''
      try {
        if (this.articleId) { const response = await getLedgerArticleDraft(this.articleId); if (generation !== this.generation) return; this.version = response.data.version; this.publishedVersion = response.data.publishedVersion; this.content = { ...newLedgerContent(null), ...response.data.content }; if (this.content.summaryAuto === true) this.content.summary = '' }
        else { const ids = String(this.$route.query.eventIds || '').split(',').filter(value => /^[1-9]\d*$/.test(value)); this.content.eventIds = ids }
        await this.searchEvents(''); if (generation !== this.generation) return; this.dirty = false
        if (this.$route.query.publish === '1') this.publishDialog = true
      } catch (error) { if (generation === this.generation) this.loadError = error.message || '文章读取失败' }
      finally { if (generation === this.generation) this.loading = false }
    },
    updateBody(value) { if (value !== this.content.body) { this.content.body = value; if (!this.loading) this.dirty = true } },
    syncBody() { const body = this.$refs.markdown?.getValue?.(); if (typeof body === 'string') this.content.body = body },
    async persistDraft() { this.syncBody(); const response = await saveLedgerDraft(this.articleId, { version: this.version, content: { ...this.content, projectId: this.content.scope === 'PROJECT' ? this.content.projectId : null } }); this.articleId = response.data.id; this.version = response.data.version; this.publishedVersion = response.data.publishedVersion; this.dirty = false; return response.data },
    async saveDraft() { if (this.busy) return; this.busy = true; const fresh = !this.articleId; try { await this.persistDraft(); this.$message.success('草稿已保存，可在文章管理中继续编辑'); if (fresh) { this.navigating = true; await this.$router.replace(`/ledger/articles/${this.articleId}/edit`) } } catch { /* request 已提示，保留当前正文。 */ } finally { this.busy = false } },
    configure() { this.syncBody(); if (!this.content.title?.trim() || !this.content.body?.trim()) { this.$message.warning('请先填写标题和正文'); return } this.publishDialog = true },
    async publish() {
      if (this.busy) return
      if (!this.content.scope || (this.content.scope === 'PROJECT' && !this.content.projectId) || !this.content.occurredAt) { this.$message.warning('请选择归属范围和发生时间'); return }
      this.busy = true
      try { await this.persistDraft(); const response = await publishLedgerArticle(this.articleId, this.version); this.publishDialog = false; this.dirty = false; this.navigating = true; this.$message.success(`文章 v${response.data.versionNo} 已发布`); await this.$router.push({ path: `/ledger/articles/${this.articleId}`, query: { revisionId: response.data.id } }) }
      catch { /* 发布失败时已保存的草稿仍保留，弹窗不关闭。 */ } finally { this.busy = false }
    },
    back() { this.$router.push({ path: '/ledger', query: { tab: 'articles' } }) },
    insertSnippet(value) { if (snippets[value]) this.$refs.markdown.insert(snippets[value]); this.snippet = '' },
    insertAsset(asset) { this.$refs.markdown.insertAsset(asset); this.assetPicker = false },
    assetUploaded(asset) { if (asset.scanStatus !== 'CLEAN') this.$message.info('可在素材库中查看检测状态并重试检测') },
    async searchEvents(keyword) { const generation = ++this.eventGeneration; const response = await listLedgerEvents({ keyword }); if (generation !== this.eventGeneration) return; const keep = this.events.filter(item => this.content.eventIds.some(id => String(id) === String(item.id))); const values = [...keep, ...(response.data || [])]; const missing = this.content.eventIds.filter(id => !values.some(item => String(item.id) === String(id))); const selected = await Promise.all(missing.map(id => getLedgerEvent(id))); if (generation !== this.eventGeneration) return; this.events = [...new Map([...values, ...selected.map(item => item.data)].map(item => [String(item.id), item])).values()] },
    newEvent() { if (!this.content.scope || (this.content.scope === 'PROJECT' && !this.content.projectId)) { this.$message.warning('请先选择内容归属，新事件将使用该范围'); return } this.eventDialog = true },
    eventSaved(event) { this.events = [...this.events.filter(item => String(item.id) !== String(event.id)), event]; this.content.eventIds = [...new Set([...this.content.eventIds, event.id])]; this.dirty = true },
  },
}
</script>
