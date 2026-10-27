<template>
  <div class="uiue-list-page system-settings-page" :aria-busy="loading">
    <el-alert title="此处为全局配置，统一管理系统品牌、通知邮箱、账户访问与对外结果码。" type="info" :closable="false" show-icon />
    <el-tabs v-model="tab" :before-leave="beforeTabLeave" class="system-settings-tabs">
      <el-tab-pane label="基本信息" name="brand">
        <div v-if="form.brand" class="brand-columns">
          <el-form label-position="top" :disabled="!canEdit || saving || loading">
            <el-form-item label="系统名称" required><el-input v-model="form.brand.systemName" maxlength="40" show-word-limit /></el-form-item>
            <el-form-item label="系统副标题"><el-input v-model="form.brand.subtitle" maxlength="100" /><small>留空时隐藏副标题。</small></el-form-item>
            <el-form-item v-for="field in brandImageFields" :key="field.id" :label="field.label">
              <div class="brand-image-editor">
                <div class="brand-image-input">
                  <el-input :model-value="form.brand[field.url]" :aria-label="field.label + '图片地址'" clearable placeholder="输入图片地址，或上传图片" @update:model-value="value => setAssetUrl(field, value)" />
                  <el-upload :auto-upload="false" :show-file-list="false" :accept="imageAccept" :on-change="file => selectAsset(file, field.id)" :disabled="!canEdit || saving || loading"><el-button :disabled="!canEdit || saving || loading">上传图片</el-button></el-upload>
                </div>
                <div class="brand-upload"><img :src="field.id === 'logoId' ? logoPreview : faviconPreview" :alt="field.label" /><span v-if="form.brand[field.id]">已上传图片</span><el-button link :disabled="!canEdit || saving || loading" @click="restoreAsset(field.id)">{{ field.id === 'logoId' ? '恢复默认' : '使用系统 Logo' }}</el-button></div>
                <small>{{ field.id === 'faviconId' ? '未单独设置时自动使用系统 Logo。' : '支持 JPG、PNG、SVG、GIF、WebP、BMP、ICO；上传最大 1 MB，宽高不超过 2048 像素。' }}</small>
              </div>
            </el-form-item>
          </el-form>
          <div class="brand-preview">
            <small>导航标题与副标题 · 保存后生效</small>
            <div class="brand-nav-preview"><navigation-brand :brand="previewBrand" /></div>
            <div class="brand-browser-preview"><img :src="faviconPreview" alt="浏览器图标预览" /><span>系统配置 · {{ form.brand.systemName }}</span></div>
            <small>登录页 · 使用实际页面样式</small>
            <login-page preview :preview-brand="previewBrand" />
          </div>
        </div>
        <watermark-settings v-if="form.brand" v-model="form.brand" :disabled="!canEdit || saving || loading" />
      </el-tab-pane>
      <el-tab-pane label="通知邮箱" name="mail">
        <el-alert v-if="managed.mail" title="通知邮箱由部署环境覆盖，请先移除环境覆盖再通过页面维护。" type="info" :closable="false" />
        <div v-if="form.mail" class="mail-columns">
          <el-form label-position="top" :disabled="!writable">
            <el-form-item label="通知邮箱" required><el-input v-model="form.mail.email" type="email" /><small>所有通知统一由此邮箱发送，同时作为 SMTP 登录账号。</small></el-form-item>
            <el-form-item label="SMTP 服务器" required><el-input v-model="form.mail.host" /></el-form-item>
            <div class="settings-pair"><el-form-item label="端口" required><el-input-number v-model="form.mail.port" :min="1" :max="65535" controls-position="right" /></el-form-item><el-form-item label="连接加密"><el-select v-model="form.mail.encryption"><el-option label="STARTTLS" value="STARTTLS" /><el-option label="SSL / TLS" value="TLS" /></el-select></el-form-item></div>
            <el-form-item label="邮箱授权码 / SMTP 密码"><el-input v-model="mailPassword" type="password" show-password autocomplete="new-password" :placeholder="mailPasswordConfigured ? '已设置，留空保留' : '请输入邮箱授权码'" /><small>填写替换，留空保留；授权码不在浏览器持久保存。</small></el-form-item>
            <el-button :disabled="!canTestMail" @click="openMailTest">发送测试邮件</el-button><small v-if="mailResult" role="status">{{ mailResult }}</small>
          </el-form>
          <div class="mail-preview"><div class="mail-preview-heading"><small>邮件效果预览</small><el-select v-model="mailTemplate" size="small"><el-option label="密码重置" value="reset" /><el-option label="注册验证" value="verify" /><el-option label="系统通知" value="notice" /></el-select></div><article class="mail-letter"><header><strong>{{ mailPreview.title }}</strong><div class="mail-brand"><img :src="brand.logoUrl" :alt="brand.systemName" /><div><b>{{ brand.systemName }}</b><small>{{ brand.subtitle }}</small></div></div></header><p>尊敬的{{ mailTemplate === 'notice' ? '管理员' : '用户' }}：</p><p>{{ mailPreview.text }}</p><div v-if="mailPreview.highlight" class="mail-highlight">{{ mailPreview.highlight }}</div><span v-if="mailPreview.action" class="preview-button">{{ mailPreview.action }}</span><p v-if="mailPreview.action" class="mail-note">若非您本人操作，请忽略此邮件。请勿转发验证或重置链接。</p><footer><span>上海衡枢真信科技有限公司</span><a href="https://www.hengshucredit.com" target="_blank" rel="noopener noreferrer">www.hengshucredit.com</a></footer></article></div>
        </div>
      </el-tab-pane>
      <el-tab-pane label="登录与安全" name="access">
        <el-alert v-if="managed.access" title="访问策略由部署环境覆盖，请先移除环境覆盖再通过页面维护。" type="info" :closable="false" />
        <el-form v-if="form.access" label-position="top" class="access-form" :disabled="!writable">
          <el-form-item label="允许新用户注册"><el-switch v-model="form.access.registrationEnabled" /><small>验证邮箱后激活，由管理员分配业务权限。</small></el-form-item>
          <el-form-item label="允许通过邮箱找回密码"><el-switch v-model="form.access.passwordRecoveryEnabled" /></el-form-item>
          <el-form-item label="控制台访问地址"><el-input v-model="form.access.baseUrl" placeholder="https://engine.example.com" /><small>用于注册验证和密码重置邮件中的链接。</small></el-form-item>
          <div class="settings-pair"><el-form-item label="注册验证有效期（分钟）"><el-input-number v-model="form.access.verificationTtlMinutes" :min="5" :max="1440" /></el-form-item><el-form-item label="密码重置有效期（分钟）"><el-input-number v-model="form.access.passwordResetTtlMinutes" :min="5" :max="1440" /></el-form-item></div>
          <el-alert title="超级管理员的服务器一次性恢复码不受邮箱设置影响。" type="info" :closable="false" show-icon />
        </el-form>
      </el-tab-pane>
      <el-tab-pane label="对外结果码" name="codes">
        <template v-if="form.codes">
          <el-alert title="一个对外 code 可对应多个系统码。只转换对外 code 和描述，内部成功/失败、HTTP 状态与重试语义保持不变。" type="info" :closable="false" show-icon />
          <div class="uiue-search-container uiue-filter-toolbar code-toolbar"><div class="code-filters"><el-input v-model="codeSearch" clearable placeholder="搜索系统码、对外 code 或描述" @input="codePage = 1" /><el-select v-model="codeGroup" clearable placeholder="全部分类" @change="codePage = 1"><el-option v-for="item in codeGroups" :key="item.value" :label="item.label" :value="item.value" /></el-select></div><div class="code-actions"><span>启用对外映射</span><el-switch v-model="form.codes.enabled" :disabled="!writable" /></div></div>
          <el-table :data="visibleCodes" border size="small"><el-table-column prop="code" label="系统 code" width="110" /><el-table-column prop="message" label="系统描述" min-width="160" /><el-table-column label="对外 code" min-width="150"><template #default="{ row }"><el-input :model-value="mappingValue(row.code, 'code')" :placeholder="row.code" maxlength="64" :disabled="!writable" @update:model-value="value => changeMapping(row.code, 'code', value)" /></template></el-table-column><el-table-column label="对外描述" min-width="200"><template #default="{ row }"><el-input :model-value="mappingValue(row.code, 'message')" :placeholder="row.message" maxlength="120" :disabled="!writable" @update:model-value="value => changeMapping(row.code, 'message', value)" /></template></el-table-column><el-table-column label="操作" width="74"><template #default="{ row }"><el-button link :disabled="!writable || !form.codes.mappings[row.code]" @click="restoreMapping(row.code)">恢复</el-button></template></el-table-column></el-table>
          <el-pagination v-model:current-page="codePage" :page-size="10" :total="filteredCodes.length" layout="total, prev, pager, next" />
          <small>留空沿用系统值；code 按字符串保存，保留前导零，允许多项系统码映射为同一个对外 code。</small>
          <div class="mapping-preview"><div><strong>返回效果预览</strong><el-select v-model="previewCode"><el-option v-for="row in catalog" :key="row.code" :label="`${row.code} · ${row.message}`" :value="row.code" /></el-select></div><div class="settings-pair"><section><small>系统默认</small><pre>{{ mappingPreview.original }}</pre></section><section><small>对外返回 · HTTP {{ mappingPreview.httpStatus }}</small><pre>{{ mappingPreview.external }}</pre></section></div></div>
        </template>
      </el-tab-pane>
      <el-tab-pane v-if="canAudit" label="变更记录" name="history"><el-table :data="history" border><el-table-column label="时间" min-width="160"><template #default="{ row }">{{ row.createTime ? String(row.createTime).replace('T', ' ').slice(0, 19) : '—' }}</template></el-table-column><el-table-column prop="username" label="操作人" min-width="120" /><el-table-column prop="changeSummary" label="变更内容" min-width="260" /><el-table-column label="结果" width="100"><template #default="{ row }">{{ { SUCCESS: '成功', FAILED: '失败', DENIED: '拒绝' }[row.outcome] || row.outcome }}</template></el-table-column></el-table><el-pagination v-model:current-page="historyPage" :page-size="20" :total="historyTotal" layout="total, prev, pager, next" @current-change="loadHistory" /></el-tab-pane>
    </el-tabs>
    <div v-if="tab !== 'history'" class="settings-footer"><small>{{ draft ? draft.status === 'EDITING' ? '草稿已保存，尚未提交审批' : '审批处理中，已生效配置保持不变' : dirty ? '有未保存的修改' : '当前为已生效配置' }}</small><div><el-button :disabled="!dirty || saving" @click="cancelChanges">取消修改</el-button><el-button v-if="tab !== 'brand'" :disabled="!writable || !dirty" :loading="saving" @click="saveDraft">保存草稿</el-button><el-button v-if="tab === 'brand'" type="primary" :disabled="!writable || !dirty" :loading="saving" @click="saveBrand">保存配置</el-button><el-button v-else type="primary" :disabled="!canSubmit || (!dirty && !draft) || (draft && draft.status !== 'EDITING')" :loading="saving" @click="submit">提交审批</el-button><el-button v-if="draft && hasApprovalView" @click="$router.push('/approval/' + draft.id)">查看审批</el-button></div></div>
    <el-dialog v-model="testDialog" title="发送测试邮件" width="460px" :close-on-click-modal="false"><el-form label-position="top"><el-form-item label="测试收件人" required><el-input v-model="testRecipient" type="email" placeholder="仅用于本次测试" /></el-form-item></el-form><p>将使用当前表单配置发送真实测试邮件。</p><template #footer><el-button :disabled="testing" @click="testDialog = false">取消</el-button><el-button type="primary" :loading="testing" @click="sendTest">发送测试邮件</el-button></template></el-dialog>
  </div>
</template>

<script>
import { getSystemSettings, saveSystemBrand, uploadSystemAsset, testSystemMail, getSystemSettingsHistory } from '@/api/systemSettings'
import { createResourceDraft, saveGovernanceDraft, submitGovernanceRequest, getWorkspaceDraft } from '@/api/governance'
import { hasPermission } from '@/security/permissionState'
import NavigationBrand from '@/components/common/NavigationBrand.vue'
import LoginPage from '@/views/Login.vue'
import WatermarkSettings from '@/views/system/WatermarkSettings.vue'
import { BRAND_IMAGE_ACCEPT, prepareBrandImage } from '@/system/brandImageUpload'
import { systemBrand, resolveBrandImages, applySystemBrand, loadSystemBrand } from '@/system/systemBrand'
import { setWatermarkPreview } from '@/system/watermarkPreview'
import { parseMetadataResponse } from '@/utils/metadataResponse'

const clone = value => JSON.parse(JSON.stringify(value))
const groups = { brand: 1, mail: 2, access: 3, codes: 4 }

export default {
  name: 'SystemSettings',
  components: { NavigationBrand, LoginPage, WatermarkSettings },
  data: () => ({ imageAccept: BRAND_IMAGE_ACCEPT, brandImageFields: [{ id: 'logoId', url: 'logoUrl', label: '系统 Logo' }, { id: 'faviconId', url: 'faviconUrl', label: '浏览器图标' }], tab: 'brand', form: {}, saved: {}, drafts: {}, effectiveAccess: {}, catalog: [], managed: {}, history: [], historyPage: 1, historyTotal: 0, brand: systemBrand, loading: false, saving: false, mailPassword: '', assetPreviews: {}, codeSearch: '', codeGroup: '', codePage: 1, previewCode: '000000', testDialog: false, testRecipient: '', testing: false, mailResult: '', mailTemplate: 'reset', codeGroups: [{ value: '0', label: '成功' }, { value: '1', label: '请求参数' }, { value: '2', label: '规则与模型' }, { value: '3', label: '身份与权限' }, { value: '4', label: '执行与限流' }, { value: '5', label: '资源与额度' }, { value: '6', label: '外部调用' }, { value: '7', label: '治理与发布' }, { value: '9', label: '系统异常' }] }),
  computed: {
    canEdit() { return hasPermission('system:edit') },
    canAudit() { return hasPermission('system:audit') },
    canTestMail() { return this.writable && hasPermission('system:mail-test') && !this.managed.mail },
    canSubmit() { return this.writable && hasPermission('system:submit') && hasPermission('approval:submit') },
    hasApprovalView() { return hasPermission('approval:view') },
    draft() { return this.drafts[this.tab] },
    writable() { return !this.loading && this.canEdit && !this.managed[this.tab] && (!this.draft || this.draft.status === 'EDITING') },
    dirty() { return this.tab !== 'history' && (JSON.stringify(this.form[this.tab]) !== JSON.stringify(this.saved[this.tab]) || (this.tab === 'mail' && Boolean(this.mailPassword))) },
    watermarkPreviewConfig() { return this.tab === 'brand' && this.canEdit && this.dirty ? this.form.brand : null },
    logoPreview() { return this.assetPreviews.logoId || resolveBrandImages(this.form.brand).logoUrl },
    faviconPreview() { return this.assetPreviews.faviconId || (this.form.brand?.faviconId || this.form.brand?.faviconUrl ? resolveBrandImages(this.form.brand).faviconUrl : this.logoPreview) },
    previewBrand() { return { ...this.form.brand, logoUrl: this.logoPreview, faviconUrl: this.faviconPreview } },
    mailPasswordConfigured() { return Boolean(this.form.mail?._secretConfigured?.['/password']) },
    filteredCodes() { const q = this.codeSearch.trim().toLowerCase(); return this.catalog.filter(row => (!this.codeGroup || row.code.startsWith(this.codeGroup)) && (!q || [row.code, row.message, this.mappingValue(row.code, 'code'), this.mappingValue(row.code, 'message')].some(value => String(value).toLowerCase().includes(q)))) },
    visibleCodes() { return this.filteredCodes.slice((this.codePage - 1) * 10, this.codePage * 10) },
    mappingPreview() { const row = this.catalog.find(item => item.code === this.previewCode) || { code: '000000', message: '成功', httpStatus: 200 }; const original = { success: row.code === '000000', code: row.code, message: row.message }; const external = this.form.codes?.enabled ? { ...original, code: this.mappingValue(row.code, 'code') || row.code, message: this.mappingValue(row.code, 'message') || row.message } : original; return { original: JSON.stringify(original, null, 2), external: JSON.stringify(external, null, 2), httpStatus: row.httpStatus } },
    mailPreview() { return { reset: { title: '密码重置通知', text: '我们收到了您的密码重置请求，请点击下方按钮设置新密码。', highlight: `链接有效期：${this.effectiveAccess.passwordResetTtlMinutes || 30} 分钟`, action: '设置新密码' }, verify: { title: '账户邮箱验证', text: '请验证您的邮箱以完成账户激活，之后由管理员为您分配业务权限。', highlight: `链接有效期：${this.effectiveAccess.verificationTtlMinutes || 30} 分钟`, action: '验证邮箱并激活' }, notice: { title: '系统配置变更通知', text: '系统配置已更新并生效，请登录控制台查看变更记录。' } }[this.mailTemplate] },
  },
  watch: {
    watermarkPreviewConfig: { deep: true, immediate: true, handler(value) { setWatermarkPreview(value) } },
  },
  created() { this.load() },
  beforeUnmount() { setWatermarkPreview(null); this.mailPassword = ''; Object.values(this.assetPreviews).forEach(URL.revokeObjectURL) },
  async beforeRouteLeave() { return this.confirmLeave() },
  methods: {
    async load() { this.loading = true; try { const response = await getSystemSettings(); const data = response.data; this.effectiveAccess = clone(data.access); for (const group of Object.keys(groups)) this.form[group] = data[group]; this.saved = clone(this.form); this.catalog = data.codeCatalog; this.managed = data.deploymentManaged || {}; await this.loadDraft(this.tab) } catch (error) { this.showError(error) } finally { this.loading = false } },
    async loadDraft(tab) {
      if (tab === 'brand') return
      if (tab === 'history') { if (this.canAudit) await this.loadHistory(); return }
      const effective = (await getSystemSettings()).data
      this.effectiveAccess = clone(effective.access)
      this.form[tab] = effective[tab]; this.saved[tab] = clone(this.form[tab])
      const state = (effective.states || []).find(item => Number(item.resourceId) === groups[tab])
      this.drafts[tab] = state && ['EDITING', 'PENDING'].includes(state.requestStatus) ? { id: state.requestId, status: state.requestStatus === 'EDITING' && !state.owned ? 'LOCKED' : state.requestStatus } : null
      if (!this.canEdit) return
      const draft = (await getWorkspaceDraft('SYSTEM_SETTING', groups[tab])).data
      if (draft?.draftSnapshotJson) { this.drafts[tab] = draft; this.form[tab] = parseMetadataResponse(draft.draftSnapshotJson); this.saved[tab] = clone(this.form[tab]) }
    },
    async confirmLeave() { if (this.saving || this.testing) return false; if (!this.dirty) return true; try { await this.$confirm('当前配置尚未保存，是否放弃修改？', '未保存的修改'); this.cancelChanges(); return true } catch { return false } },
    async beforeTabLeave(next) { if (!await this.confirmLeave()) return false; try { await this.loadDraft(next); return true } catch (error) { this.showError(error); return false } },
    cancelChanges() { this.form[this.tab] = clone(this.saved[this.tab]); this.mailPassword = ''; if (this.tab === 'brand') { Object.values(this.assetPreviews).forEach(URL.revokeObjectURL); this.assetPreviews = {} } },
    async loadHistory() { const page = this.historyPage; try { const response = await getSystemSettingsHistory({ pageNum: page, pageSize: 20 }); if (page === this.historyPage) { this.history = response.data?.records || []; this.historyTotal = response.data?.total || 0 } } catch (error) { this.showError(error) } },
    async selectAsset(file, field) {
      if (!this.canEdit || this.saving) return
      this.saving = true
      try {
        const image = await prepareBrandImage(file.raw)
        const response = await uploadSystemAsset(image)
        this.restoreAsset(field)
        this.assetPreviews[field] = URL.createObjectURL(image)
        this.form.brand[field] = response.data.id
      } catch (error) { this.showError(error) } finally { this.saving = false }
    },
    restoreAsset(field) { if (this.assetPreviews[field]) URL.revokeObjectURL(this.assetPreviews[field]); delete this.assetPreviews[field]; this.form.brand[field] = ''; this.form.brand[field.replace('Id', 'Url')] = '' },
    setAssetUrl(field, value) { this.restoreAsset(field.id); this.form.brand[field.url] = value },
    async saveBrand() { this.saving = true; try { const response = await saveSystemBrand(this.form.brand); applySystemBrand(response.data); Object.values(this.assetPreviews).forEach(URL.revokeObjectURL); this.assetPreviews = {}; await this.load(); this.$message.success('基本信息已保存并生效') } catch (error) { this.showError(error) } finally { this.saving = false } },
    draftPayload() { const value = clone(this.form[this.tab]); if (this.tab === 'mail' && this.mailPassword) value.password = this.mailPassword; return value },
    async persistDraft() { const value = this.draftPayload(); const response = this.draft?.status === 'EDITING' ? await saveGovernanceDraft(this.draft.id, { snapshotJson: JSON.stringify(value), expectedSnapshotDigest: this.draft.snapshotDigest, changeSummary: '修改系统配置' }) : await createResourceDraft('SYSTEM_SETTING', value, 'UPDATE', { changeSummary: '修改系统配置' }); this.drafts[this.tab] = response.data; this.mailPassword = ''; await this.loadDraft(this.tab); return this.drafts[this.tab] },
    async saveDraft() { this.saving = true; try { await this.persistDraft(); this.$message.success('草稿已保存，已生效配置保持不变') } catch (error) { this.showError(error) } finally { this.saving = false } },
    async submit() { this.saving = true; try { const draft = this.dirty || !this.draft ? await this.persistDraft() : this.draft; const response = await submitGovernanceRequest(draft.id, '提交系统配置审批', draft.snapshotDigest); this.drafts[this.tab] = response.data; this.$message.success('已提交审批，通过后生效'); loadSystemBrand(true) } catch (error) { this.showError(error) } finally { this.saving = false } },
    mappingValue(code, key) { return this.form.codes?.mappings?.[code]?.[key] || '' },
    changeMapping(code, key, value) { const mapping = { code: '', message: '', ...this.form.codes.mappings[code], [key]: value }; if (mapping.code || mapping.message) this.form.codes.mappings[code] = mapping; else delete this.form.codes.mappings[code]; this.previewCode = code },
    restoreMapping(code) { delete this.form.codes.mappings[code]; this.previewCode = code },
    openMailTest() { this.testRecipient = ''; this.testDialog = true },
    async sendTest() { if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(this.testRecipient)) { this.$message.warning('请输入有效的测试收件邮箱'); return } this.testing = true; try { const config = clone(this.form.mail); if (this.mailPassword) config.password = this.mailPassword; await testSystemMail({ recipient: this.testRecipient, config, draftId: this.drafts.mail?.id, snapshotDigest: this.drafts.mail?.snapshotDigest }); this.mailResult = 'SMTP 服务已接受测试邮件，实际送达请查收邮箱。'; this.testDialog = false; this.$message.success(this.mailResult) } catch (error) { this.showError(error) } finally { this.testing = false } },
    showError(error) { if (!error?.requestErrorNotified) this.$message.error(error?.message || '系统配置操作失败') },
  },
}
</script>

<style scoped lang="scss">
.system-settings-page { gap: 16px; }
.system-settings-tabs { padding: 16px 24px; background: var(--tianshu-bg-surface); border: 1px solid var(--tianshu-border-subtle); border-radius: 10px; }
.system-settings-page small { display: block; color: var(--tianshu-text-tertiary); font-size: 12px; line-height: 1.6; margin-top: 6px; }
.brand-columns, .mail-columns { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 32px; padding: 12px 0; }
.brand-upload { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; width: 100%; }
.brand-upload img { width: 56px; height: 56px; object-fit: contain; }
.preview-button { display: block; padding: 10px 16px; border-radius: 6px; background: var(--tianshu-brand-background); color: var(--tianshu-brand-foreground); text-align: center; margin-top: 14px; }
.brand-columns { grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); align-items: start; }
.brand-image-editor { width: 100%; }
.brand-image-input { display: flex; gap: 12px; width: 100%; margin-bottom: 12px; }
.brand-image-input .el-input { flex: 1; min-width: 0; }
.brand-preview { display: flex; flex-direction: column; gap: 12px; min-width: 0; }
.brand-nav-preview { background: var(--tianshu-sidebar-bg); color: var(--tianshu-sidebar-text); border-radius: 8px; overflow: hidden; }
.brand-browser-preview { display: flex; align-items: center; gap: 8px; padding: 10px 16px; background: var(--tianshu-bg-soft); border: 1px solid var(--tianshu-border-subtle); border-radius: 8px; font-size: 12px; }
.brand-browser-preview img { width: 16px; height: 16px; object-fit: contain; }
.brand-browser-preview span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.mail-brand img { width: 32px; height: 32px; object-fit: contain; }
.access-form { max-width: 720px; padding-top: 12px; }
.settings-pair { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.settings-pair > * { min-width: 0; }
.mail-preview-heading { display: flex; justify-content: space-between; align-items: center; gap: 12px; margin-bottom: 12px; }
.mail-preview-heading .el-select { width: 140px; }
.mail-letter { border: 1px solid var(--tianshu-border-subtle); border-radius: 8px; padding: 20px; font-size: 13px; line-height: 1.8; }
.mail-letter header { display: flex; justify-content: space-between; gap: 18px; align-items: center; border-bottom: 2px solid var(--el-color-primary); padding-bottom: 16px; }
.mail-brand { display: flex; gap: 8px; align-items: center; font-size: 12px; }
.mail-brand small { font-size: 11px; margin: 0; }
.mail-highlight { padding: 10px; border-left: 3px solid var(--el-color-primary); background: var(--tianshu-primary-soft-bg); }
.mail-note { color: var(--tianshu-text-tertiary); font-size: 12px; }
.mail-letter footer { display: flex; justify-content: space-between; flex-wrap: wrap; gap: 8px; border-top: 1px solid var(--tianshu-border-subtle); padding-top: 12px; font-size: 11px; color: var(--tianshu-text-tertiary); }
.mail-letter a { color: var(--el-color-primary); }
.code-toolbar { margin-top: 14px; gap: 16px; }
.code-filters, .code-actions { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.code-actions { margin-left: auto; }
.code-filters .el-input { width: 290px; }.code-filters .el-select { width: 150px; }
.system-settings-page .el-pagination { margin: 16px 0; }
.mapping-preview { border-top: 1px solid var(--tianshu-border-subtle); margin-top: 20px; padding-top: 18px; }
.mapping-preview > div:first-child { display: flex; justify-content: space-between; gap: 16px; align-items: center; flex-wrap: wrap; }
.mapping-preview .el-select { width: 260px; }.mapping-preview pre { padding: 14px; background: var(--tianshu-bg-soft); border-radius: 6px; white-space: pre-wrap; overflow-wrap: anywhere; }
.settings-footer { display: flex; justify-content: space-between; gap: 16px; align-items: center; flex-wrap: wrap; padding: 12px 0; }
.settings-footer > div { display: flex; gap: 8px; flex-wrap: wrap; }.settings-footer .el-button + .el-button { margin-left: 0; }
@media (max-width: 820px) { .brand-columns, .mail-columns, .settings-pair { grid-template-columns: 1fr; }.system-settings-tabs { padding: 12px; }.mail-letter header { flex-wrap: wrap; }.code-filters, .code-filters .el-input, .code-filters .el-select { width: 100%; }.settings-footer > div { margin-left: auto; } }
</style>
